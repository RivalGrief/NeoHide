package com.neohide.neohide.tabcompleters;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.*;

public class NeoHideTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("neohide.use")) {
            return completions;
        }

        if (args.length == 1) {
            // Основные команды
            String[] subCommands = {
                    "help", "reload", "status",
                    "hide", "unhide", "list",
                    "protect", "token", "web"
            };

            for (String sub : subCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }

        } else if (args.length == 2) {
            // Команда hide/unhide - предлагаем популярные команды
            if (args[0].equalsIgnoreCase("hide") || args[0].equalsIgnoreCase("unhide")) {
                String[] popularCommands = {
                        "plugin", "pl", "plugins", "ver", "version",
                        "help", "?", "bukkit", "spigot", "minecraft",
                        "op", "deop", "gamemode", "gm", "gmc", "gms", "gma", "gmsp",
                        "give", "i", "item", "enchant", "effect", "potion",
                        "ban", "kick", "pardon", "unban", "ban-ip", "pardon-ip",
                        "whitelist", "wl", "whitelist", "xp", "experience", "exp",
                        "weather", "time", "difficulty", "gamerule", "seed",
                        "tp", "teleport", "spawnpoint", "setworldspawn",
                        "say", "tell", "msg", "w", "me", "teammsg", "tm",
                        "stop", "restart", "reload", "rl", "save", "save-all",
                        "list", "who", "players", "online"
                };

                for (String command : popularCommands) {
                    if (command.startsWith(args[1].toLowerCase())) {
                        completions.add(command);
                    }
                }
            }

            // Команда protect - предлагаем популярные права
            else if (args[0].equalsIgnoreCase("protect")) {
                String[] popularPermissions = {
                        "bukkit.command", "minecraft.command", "spigot.command",
                        "op", "minecraft.op", "bukkit.op",
                        "neohide.", "neohide.reload", "neohide.manage", "neohide.admin",
                        "worldedit.", "worldguard.", "essentials.",
                        "luckperms.", "permissionsex.", "permissions.",
                        "*", "bukkit.*", "minecraft.*"
                };

                for (String perm : popularPermissions) {
                    if (perm.startsWith(args[1].toLowerCase())) {
                        completions.add(perm);
                    }
                }
            }

            // Команда token - подкоманды
            else if (args[0].equalsIgnoreCase("token")) {
                String[] tokenSubCommands = {"generate", "show"};

                for (String sub : tokenSubCommands) {
                    if (sub.startsWith(args[1].toLowerCase())) {
                        // Проверка прав для show (только консоль)
                        if (sub.equals("show") && sender instanceof org.bukkit.entity.Player) {
                            continue;
                        }
                        completions.add(sub);
                    }
                }
            }
        } else if (args.length == 3) {
            // Для команды hide/unhide предлагаем дополнительные аргументы
            if (args[0].equalsIgnoreCase("hide") && args.length == 3) {
                // Можно добавить предложения для псевдонимов команд
                String[] suggestions = {
                        "true", "false",
                        "message:Команда не найдена",
                        "alias:другая_команда"
                };

                for (String suggestion : suggestions) {
                    if (suggestion.startsWith(args[2].toLowerCase())) {
                        completions.add(suggestion);
                    }
                }
            }
        }

        // Сортируем результаты
        Collections.sort(completions);
        return completions;
    }
}