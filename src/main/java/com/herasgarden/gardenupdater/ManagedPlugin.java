package com.herasgarden.gardenupdater;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public record ManagedPlugin(String name, String repository, String assetPattern) {
    public static List<ManagedPlugin> load(ConfigurationSection section) {
        List<ManagedPlugin> plugins = new ArrayList<>();
        if (section == null) {
            return List.of();
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection plugin = section.getConfigurationSection(name);
            if (plugin == null) {
                continue;
            }
            String repository = plugin.getString("repository", "").trim();
            String asset = plugin.getString("asset", name + "*.jar").trim();
            if (!repository.isBlank()) {
                plugins.add(new ManagedPlugin(name, repository, asset));
            }
        }
        return List.copyOf(plugins);
    }
}
