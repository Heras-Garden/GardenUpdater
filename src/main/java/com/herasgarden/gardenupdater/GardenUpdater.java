package com.herasgarden.gardenupdater;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class GardenUpdater extends JavaPlugin {
    private UpdateService updates;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String configuredToken = getConfig().getString("updates.github-token", "");
        String environmentToken = System.getenv("GARDEN_GITHUB_TOKEN");
        String token = environmentToken == null || environmentToken.isBlank()
                ? configuredToken
                : environmentToken;

        List<ManagedPlugin> managed = ManagedPlugin.load(getConfig().getConfigurationSection("plugins"));
        updates = new UpdateService(
                new GithubActionsClient(token),
                new UpdateStateStore(getDataFolder().toPath().resolve("state.yml")),
                managed,
                Bukkit.getUpdateFolderFile().toPath(),
                getDataFolder().toPath().resolve("downloads")
        );

        GardenUpdateCommand command = new GardenUpdateCommand(this);
        Objects.requireNonNull(getCommand("gardenupdate")).setExecutor(command);
        Objects.requireNonNull(getCommand("gardenupdate")).setTabCompleter(command);

        scheduleChecks();
        getLogger().info(
                "GardenUpdater enabled. Successful main-branch builds will be staged for the next restart."
        );
    }

    public void showStatus(CommandSender sender) {
        Map<String, String> installed = installedVersions();
        send(sender, "Managed Garden plugins:");
        for (ManagedPlugin plugin : updates.managed()) {
            String version = installed.getOrDefault(plugin.name(), "not installed");
            boolean staged = Files.exists(updates.stagedPath(plugin));
            String sha = updates.lastStagedSha(plugin);
            String source = sha.isBlank() ? "" : " | last main " + shortSha(sha);
            send(sender, plugin.name() + " " + version + source + (staged ? " | update staged" : ""));
        }
    }

    public void check(CommandSender sender, boolean stage, String requested) {
        Map<String, String> installed = installedVersions();
        send(
                sender,
                stage
                        ? "Checking successful main builds and staging updates..."
                        : "Checking successful main builds..."
        );

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            List<UpdateStatus> statuses = updates.checkAll(installed);
            int available = 0;
            int staged = 0;

            for (UpdateStatus status : statuses) {
                if (!matchesRequested(status.plugin(), requested)) {
                    continue;
                }

                if (status.updateAvailable()) {
                    available++;
                    if (stage) {
                        try {
                            Path path = updates.stage(status);
                            staged++;
                            send(
                                    sender,
                                    status.plugin().name() + " main " + shortSha(status.mainSha())
                                            + " staged as " + path.getFileName() + "."
                            );
                        } catch (Exception exception) {
                            send(
                                    sender,
                                    status.plugin().name() + " could not be staged: "
                                            + exception.getMessage()
                            );
                        }
                    } else {
                        send(sender, status.plugin().name() + ": " + status.detail());
                    }
                } else if (requested != null && !requested.equalsIgnoreCase("all")) {
                    send(sender, status.plugin().name() + ": " + status.detail());
                }
            }

            if (stage && staged > 0) {
                send(
                        sender,
                        staged + " update" + (staged == 1 ? "" : "s")
                                + " staged. Restart the server to apply them."
                );
            } else if (!stage && available == 0) {
                send(sender, "No newer successful main builds are available.");
            } else if (stage && available == 0) {
                send(sender, "No newer successful main builds are available to stage.");
            }
        });
    }

    public void clear(CommandSender sender, String requested) {
        int cleared = 0;
        for (ManagedPlugin plugin : updates.managed()) {
            if (!matchesRequested(plugin, requested)) {
                continue;
            }
            try {
                if (updates.clear(plugin)) {
                    cleared++;
                }
            } catch (Exception exception) {
                send(sender, plugin.name() + " could not be cleared: " + exception.getMessage());
            }
        }
        send(sender, cleared + " staged update" + (cleared == 1 ? "" : "s") + " cleared.");
    }

    public List<String> managedNames() {
        List<String> names = new ArrayList<>();
        names.add("all");
        updates.managed().forEach(plugin -> names.add(plugin.name()));
        return List.copyOf(names);
    }

    public void send(CommandSender sender, String message) {
        if (Bukkit.isPrimaryThread()) {
            sender.sendMessage("[GardenUpdater] " + message);
        } else {
            Bukkit.getScheduler().runTask(
                    this,
                    () -> sender.sendMessage("[GardenUpdater] " + message)
            );
        }
    }

    private void scheduleChecks() {
        long hours = Math.max(1L, getConfig().getLong("updates.check-interval-hours", 6L));
        long interval = hours * 60L * 60L * 20L;

        if (getConfig().getBoolean("updates.check-on-startup", true)) {
            Bukkit.getScheduler().runTaskLater(this, this::backgroundCheck, 100L);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::backgroundCheck, interval, interval);
    }

    private void backgroundCheck() {
        Map<String, String> installed = installedVersions();
        boolean autoStage = getConfig().getBoolean("updates.auto-stage", true);

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            List<UpdateStatus> statuses = updates.checkAll(installed);
            int available = 0;
            int staged = 0;

            for (UpdateStatus status : statuses) {
                if (!status.updateAvailable()) {
                    continue;
                }
                available++;
                if (!autoStage) {
                    continue;
                }

                try {
                    updates.stage(status);
                    staged++;
                    getLogger().info(
                            status.plugin().name() + " main " + shortSha(status.mainSha())
                                    + " was staged for the next restart."
                    );
                } catch (Exception exception) {
                    getLogger().warning(
                            "Could not stage " + status.plugin().name() + ": " + exception.getMessage()
                    );
                }
            }

            if (available > 0 && !autoStage) {
                getLogger().info(
                        available + " Garden update" + (available == 1 ? " is" : "s are")
                                + " available. Use /gardenupdate stage all."
                );
            }
            if (staged > 0) {
                getLogger().info(
                        staged + " Garden update" + (staged == 1 ? " is" : "s are")
                                + " ready. Restart the server when convenient."
                );
            }
        });
    }

    private Map<String, String> installedVersions() {
        Map<String, String> versions = new LinkedHashMap<>();
        for (ManagedPlugin managed : updates.managed()) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(managed.name());
            if (plugin != null) {
                versions.put(managed.name(), plugin.getPluginMeta().getVersion());
            }
        }
        return Map.copyOf(versions);
    }

    private boolean matchesRequested(ManagedPlugin plugin, String requested) {
        return requested == null
                || requested.equalsIgnoreCase("all")
                || plugin.name().toLowerCase(Locale.ROOT).equals(requested.toLowerCase(Locale.ROOT));
    }

    private String shortSha(String sha) {
        return sha == null || sha.length() < 8 ? sha : sha.substring(0, 8);
    }
}
