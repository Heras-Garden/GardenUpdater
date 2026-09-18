package com.herasgarden.gardenupdater;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UpdateStateStore {
    private final Path path;
    private final YamlConfiguration yaml;

    public UpdateStateStore(Path path) {
        this.path = path;
        this.yaml = Files.exists(path)
                ? YamlConfiguration.loadConfiguration(path.toFile())
                : new YamlConfiguration();
    }

    public synchronized String lastStagedSha(String pluginName) {
        return yaml.getString("plugins." + pluginName + ".last-staged-sha", "");
    }

    public synchronized void markStaged(String pluginName, String sha) throws IOException {
        yaml.set("plugins." + pluginName + ".last-staged-sha", sha);
        save();
    }

    public synchronized void clear(String pluginName) throws IOException {
        yaml.set("plugins." + pluginName, null);
        save();
    }

    private void save() throws IOException {
        Files.createDirectories(path.getParent());
        yaml.save(path.toFile());
    }
}
