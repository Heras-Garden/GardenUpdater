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

public final class UpdateService {
    private static final long MAX_JAR_SIZE = 100L * 1024L * 1024L;

    private final GithubReleaseClient github;
    private final List<ManagedPlugin> managed;
    private final Path updateFolder;
    private final Path tempFolder;
    private final boolean includePrereleases;

    public UpdateService(
            GithubReleaseClient github,
            List<ManagedPlugin> managed,
            Path updateFolder,
            Path tempFolder,
            boolean includePrereleases
    ) {
        this.github = github;
        this.managed = managed;
        this.updateFolder = updateFolder;
        this.tempFolder = tempFolder;
        this.includePrereleases = includePrereleases;
    }

    public List<ManagedPlugin> managed() {
        return managed;
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
            return new UpdateStatus(plugin, "not installed", "", null, false, staged, "Plugin is not installed.");
        }

        try {
            Optional<GithubReleaseClient.Release> release = github.latestRelease(plugin.repository(), includePrereleases);
            if (release.isEmpty()) {
                return new UpdateStatus(plugin, installedVersion, "", null, false, staged, "No GitHub Release exists yet.");
            }

            GithubReleaseClient.Release latest = release.get();
            GithubReleaseClient.Asset asset = selectAsset(plugin, latest)
                    .orElse(null);
            if (asset == null) {
                return new UpdateStatus(
                        plugin,
                        installedVersion,
                        latest.tag(),
                        null,
                        false,
                        staged,
                        "Release " + latest.tag() + " does not contain a matching JAR."
                );
            }

            boolean available = SemVer.parse(latest.tag()).compareTo(SemVer.parse(installedVersion)) > 0;
            String detail = available ? "Update available." : "Already current.";
            return new UpdateStatus(plugin, installedVersion, latest.tag(), asset, available, staged, detail);
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
        if (!status.updateAvailable() || status.asset() == null) {
            throw new IllegalArgumentException("No update is available for " + status.plugin().name() + ".");
        }
        if (status.asset().size() <= 0 || status.asset().size() > MAX_JAR_SIZE) {
            throw new IOException("Release asset size is outside the allowed range.");
        }

        Files.createDirectories(updateFolder);
        Files.createDirectories(tempFolder);

        Path temp = Files.createTempFile(tempFolder, status.plugin().name() + "-", ".jar.part");
        try {
            github.download(status.asset(), temp);
            long downloaded = Files.size(temp);
            if (downloaded <= 0 || downloaded > MAX_JAR_SIZE) {
                throw new IOException("Downloaded JAR size is outside the allowed range.");
            }
            verifyJar(temp, status.plugin().name(), status.releaseVersion());

            Path target = stagedPath(status.plugin());
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public boolean clear(ManagedPlugin plugin) throws IOException {
        return Files.deleteIfExists(stagedPath(plugin));
    }

    public Path stagedPath(ManagedPlugin plugin) {
        return updateFolder.resolve(plugin.name() + ".jar");
    }

    private Optional<GithubReleaseClient.Asset> selectAsset(
            ManagedPlugin plugin,
            GithubReleaseClient.Release release
    ) {
        Pattern pattern = glob(plugin.assetPattern());
        return release.assets().stream()
                .filter(asset -> pattern.matcher(asset.name()).matches())
                .filter(asset -> asset.name().toLowerCase().endsWith(".jar"))
                .findFirst();
    }

    private Pattern glob(String value) {
        String regex = Arrays.stream(value.split("\\*", -1))
                .map(Pattern::quote)
                .collect(Collectors.joining(".*"));
        return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE);
    }

    private void verifyJar(Path jarPath, String expectedPluginName, String releaseVersion) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry pluginYml = jar.getJarEntry("plugin.yml");
            if (pluginYml == null) {
                throw new IOException("Downloaded file does not contain plugin.yml.");
            }

            YamlConfiguration metadata;
            try (InputStreamReader reader = new InputStreamReader(
                    jar.getInputStream(pluginYml), StandardCharsets.UTF_8)) {
                metadata = YamlConfiguration.loadConfiguration(reader);
            }

            String pluginName = metadata.getString("name", "");
            String jarVersion = metadata.getString("version", "");
            if (!expectedPluginName.equals(pluginName)) {
                throw new IOException("Downloaded JAR is " + pluginName + ", expected " + expectedPluginName + ".");
            }
            try {
                if (SemVer.parse(jarVersion).compareTo(SemVer.parse(releaseVersion)) != 0) {
                    throw new IOException(
                            "Downloaded JAR version " + jarVersion + " does not match release " + releaseVersion + "."
                    );
                }
            } catch (IllegalArgumentException exception) {
                throw new IOException("Downloaded JAR has an invalid version.", exception);
            }
        }
    }
}
