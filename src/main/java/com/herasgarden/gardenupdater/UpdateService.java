package com.herasgarden.gardenupdater;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class UpdateService {
    private static final long MAX_ARTIFACT_SIZE = 200L * 1024L * 1024L;
    private static final long MAX_JAR_SIZE = 100L * 1024L * 1024L;

    private final GithubActionsClient github;
    private final UpdateStateStore state;
    private final List<ManagedPlugin> managed;
    private final Path updateFolder;
    private final Path tempFolder;

    public UpdateService(
            GithubActionsClient github,
            UpdateStateStore state,
            List<ManagedPlugin> managed,
            Path updateFolder,
            Path tempFolder
    ) {
        this.github = github;
        this.state = state;
        this.managed = managed;
        this.updateFolder = updateFolder;
        this.tempFolder = tempFolder;
    }

    public List<ManagedPlugin> managed() {
        return managed;
    }

    public void cleanupPartialDownloads() {
        if (!Files.isDirectory(tempFolder)) return;
        try (java.util.stream.Stream<Path> files = Files.list(tempFolder)) {
            files.filter(path -> path.getFileName().toString().endsWith(".part"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    public List<UpdateStatus> checkAll(Map<String, String> installedVersions) {
        List<UpdateStatus> statuses = new ArrayList<>();
        for (ManagedPlugin plugin : managed) {
            statuses.add(check(plugin, installedVersions.get(plugin.name())));
        }
        return List.copyOf(statuses);
    }

    public UpdateStatus check(ManagedPlugin plugin, String installedVersion) {
        Path stagedPath = stagedPath(plugin);
        boolean staged = Files.exists(stagedPath);

        if (installedVersion == null || installedVersion.isBlank()) {
            return new UpdateStatus(
                    plugin,
                    "not installed",
                    "",
                    null,
                    false,
                    staged,
                    "Plugin is not installed."
            );
        }

        try {
            Optional<GithubActionsClient.MainBuild> build = github.latestSuccessfulMainBuild(plugin);
            if (build.isEmpty()) {
                return new UpdateStatus(
                        plugin,
                        installedVersion,
                        "",
                        null,
                        false,
                        staged,
                        "No successful Actions build exists for the current " + plugin.branch() + " commit."
                );
            }

            GithubActionsClient.MainBuild latest = build.get();
            String lastStaged = state.lastStagedSha(plugin.name());
            if (!lastStaged.isBlank() && !Files.exists(stagedPath)) {
                try {
                    state.clear(plugin.name());
                    lastStaged = "";
                } catch (IOException exception) {
                    return new UpdateStatus(
                            plugin,
                            installedVersion,
                            latest.headSha(),
                            latest.artifact(),
                            false,
                            false,
                            "Staged-state reconciliation failed: " + exception.getMessage()
                    );
                }
            }
            boolean available = !latest.headSha().equals(lastStaged);
            String detail = available
                    ? "New " + plugin.branch() + " build available at " + shortSha(latest.headSha()) + "."
                    : "Already staged from " + plugin.branch() + " at " + shortSha(latest.headSha()) + ".";

            return new UpdateStatus(
                    plugin,
                    installedVersion,
                    latest.headSha(),
                    latest.artifact(),
                    available,
                    staged,
                    detail
            );
        } catch (Exception exception) {
            return new UpdateStatus(
                    plugin,
                    installedVersion,
                    "",
                    null,
                    false,
                    staged,
                    "Check failed: " + exception.getMessage()
            );
        }
    }

    public Path stage(UpdateStatus status) throws IOException, InterruptedException {
        if (!status.updateAvailable() || status.artifact() == null) {
            throw new IllegalArgumentException("No new main build is available for " + status.plugin().name() + ".");
        }
        if (status.artifact().size() <= 0 || status.artifact().size() > MAX_ARTIFACT_SIZE) {
            throw new IOException("Actions artifact size is outside the allowed range.");
        }

        Files.createDirectories(updateFolder);
        Files.createDirectories(tempFolder);

        Path archive = Files.createTempFile(tempFolder, status.plugin().name() + "-", ".zip.part");
        Path extractedJar = Files.createTempFile(tempFolder, status.plugin().name() + "-", ".jar.part");
        try {
            github.downloadArtifact(status.artifact(), archive);
            long downloaded = Files.size(archive);
            if (downloaded <= 0 || downloaded > MAX_ARTIFACT_SIZE) {
                throw new IOException("Downloaded Actions artifact size is outside the allowed range.");
            }

            extractJar(archive, extractedJar, status.plugin().jarPattern());
            long jarSize = Files.size(extractedJar);
            if (jarSize <= 0 || jarSize > MAX_JAR_SIZE) {
                throw new IOException("Extracted JAR size is outside the allowed range.");
            }

            verifyJar(extractedJar, status.plugin().name());

            Path target = stagedPath(status.plugin());
            try {
                Files.move(
                        extractedJar,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(extractedJar, target, StandardCopyOption.REPLACE_EXISTING);
            }

            state.markStaged(status.plugin().name(), status.mainSha());
            return target;
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(extractedJar);
        }
    }

    public boolean clear(ManagedPlugin plugin) throws IOException {
        boolean removed = Files.deleteIfExists(stagedPath(plugin));
        state.clear(plugin.name());
        return removed;
    }

    public Path stagedPath(ManagedPlugin plugin) {
        return updateFolder.resolve(plugin.name() + ".jar");
    }

    public String lastStagedSha(ManagedPlugin plugin) {
        return state.lastStagedSha(plugin.name());
    }

    private void extractJar(Path archive, Path destination, String jarPattern) throws IOException {
        Pattern pattern = glob(jarPattern);
        boolean found = false;

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String fileName = Path.of(entry.getName()).getFileName().toString();
                if (!pattern.matcher(fileName).matches() || !fileName.toLowerCase().endsWith(".jar")) {
                    continue;
                }

                if (found) {
                    throw new IOException("Actions artifact contains more than one matching JAR.");
                }

                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                found = true;
            }
        }

        if (!found) {
            throw new IOException("Actions artifact does not contain a JAR matching " + jarPattern + ".");
        }
    }

    private Pattern glob(String value) {
        String regex = Arrays.stream(value.split("\\*", -1))
                .map(Pattern::quote)
                .collect(Collectors.joining(".*"));
        return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE);
    }

    private void verifyJar(Path jarPath, String expectedPluginName) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry pluginYml = jar.getJarEntry("plugin.yml");
            if (pluginYml == null) {
                throw new IOException("Downloaded file does not contain plugin.yml.");
            }

            YamlConfiguration metadata;
            try (InputStreamReader reader = new InputStreamReader(
                    jar.getInputStream(pluginYml),
                    StandardCharsets.UTF_8
            )) {
                metadata = YamlConfiguration.loadConfiguration(reader);
            }

            String pluginName = metadata.getString("name", "");
            String jarVersion = metadata.getString("version", "");
            if (!expectedPluginName.equals(pluginName)) {
                throw new IOException(
                        "Downloaded JAR is " + pluginName + ", expected " + expectedPluginName + "."
                );
            }
            if (jarVersion.isBlank()) {
                throw new IOException("Downloaded JAR does not declare a version.");
            }
        }
    }

    private String shortSha(String sha) {
        return sha == null || sha.length() < 8 ? sha : sha.substring(0, 8);
    }
}
