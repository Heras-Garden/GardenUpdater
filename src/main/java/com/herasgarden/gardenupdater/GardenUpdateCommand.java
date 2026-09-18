package com.herasgarden.gardenupdater;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class GardenUpdateCommand implements CommandExecutor, TabCompleter {
    private final GardenUpdater plugin;

    public GardenUpdateCommand(GardenUpdater plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gardenupdater.admin")) {
            plugin.send(sender, "You do not have permission to manage updates.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            plugin.showStatus(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "check" -> {
                plugin.check(sender, false, null);
                yield true;
            }
            case "stage" -> {
                String target = args.length >= 2 ? args[1] : "all";
                plugin.check(sender, true, target);
                yield true;
            }
            case "clear" -> {
                if (args.length < 2) {
                    plugin.send(sender, "Use /gardenupdate clear <plugin|all>.");
                } else {
                    plugin.clear(sender, args[1]);
                }
                yield true;
            }
            default -> {
                plugin.send(sender, "Use /gardenupdate <status|check|stage|clear>.");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("gardenupdater.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return match(args[0], List.of("status", "check", "stage", "clear"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("stage") || args[0].equalsIgnoreCase("clear"))) {
            return match(args[1], plugin.managedNames());
        }
        return List.of();
    }

    private List<String> match(String prefix, List<String> values) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}
