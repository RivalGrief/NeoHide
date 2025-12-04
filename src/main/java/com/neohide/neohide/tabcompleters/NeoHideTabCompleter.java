package com.neohide.neohide.tabcompleters;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.*;

public class NeoHideTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String[] subCommands = {"help", "reload", "status", "hide", "unhide", "list", "protect"};
            for (String sub : subCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("hide") || args[0].equalsIgnoreCase("unhide")) {
                // Предлагаем популярные команды для скрытия
                String[] popularCommands = {
                        "plugin", "pl", "plugins", "ver", "version",
                        "help", "?", "bukkit", "spigot",
                        "op", "deop", "gamemode", "gm",
                        "give", "i", "item", "enchant",
                        "ban", "kick", "pardon", "unban",
                        "whitelist", "wl", "xp", "experience",
                        "weather", "time", "difficulty", "gamerule"
                };

                for (String command : popularCommands) {
                    if (command.startsWith(args[1].toLowerCase())) {
                        completions.add(command);
                    }
                }
            }
        }

        return completions;
    }
}