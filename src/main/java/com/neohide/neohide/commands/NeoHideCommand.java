package com.neohide.neohide.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import com.neohide.neohide.NeoHide;
import java.util.List;

public class NeoHideCommand implements CommandExecutor {

    private final NeoHide plugin;

    public NeoHideCommand(NeoHide plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                sendHelp(sender);
                break;

            case "reload":
                if (!sender.hasPermission("neohide.reload")) {
                    sender.sendMessage(ChatColor.RED + "У вас нет прав!");
                    return true;
                }
                plugin.getConfigManager().reloadConfig();
                plugin.getCommandManager().restoreCommands();
                plugin.getCommandManager().setupCommands();
                sender.sendMessage(ChatColor.GREEN + "Конфигурация NeoHide перезагружена!");
                break;

            case "hide":
                if (!sender.hasPermission("neohide.manage")) {
                    sender.sendMessage(ChatColor.RED + "У вас нет прав!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /neohide hide <команда>");
                    return true;
                }
                plugin.getCommandManager().addHiddenCommand(args[1]);
                sender.sendMessage(ChatColor.GREEN + "Команда " + args[1] + " теперь скрыта!");
                break;

            case "unhide":
                if (!sender.hasPermission("neohide.manage")) {
                    sender.sendMessage(ChatColor.RED + "У вас нет прав!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /neohide unhide <команда>");
                    return true;
                }
                plugin.getCommandManager().removeHiddenCommand(args[1]);
                sender.sendMessage(ChatColor.GREEN + "Команда " + args[1] + " больше не скрыта!");
                break;

            case "list":
                if (!sender.hasPermission("neohide.view")) {
                    sender.sendMessage(ChatColor.RED + "У вас нет прав!");
                    return true;
                }
                List<String> hidden = plugin.getCommandManager().getHiddenCommands();
                sender.sendMessage(ChatColor.GOLD + "Скрытые команды (" + hidden.size() + "):");
                for (String command : hidden) {
                    sender.sendMessage(ChatColor.YELLOW + " - " + ChatColor.WHITE + command);
                }
                break;

            case "status":
                sendStatus(sender);
                break;

            case "protect":
                if (!sender.hasPermission("neohide.manage")) {
                    sender.sendMessage(ChatColor.RED + "У вас нет прав!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /neohide protect <право>");
                    return true;
                }
                plugin.getConfigManager().addProtectedPermission(args[1]);
                sender.sendMessage(ChatColor.GREEN + "Право " + args[1] + " теперь защищено!");
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Неизвестная подкоманда. Используйте /neohide help");
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "==========[ NeoHide Help ]==========");
        sender.sendMessage(ChatColor.YELLOW + "/neohide help" + ChatColor.WHITE + " - Показать это сообщение");
        sender.sendMessage(ChatColor.YELLOW + "/neohide reload" + ChatColor.WHITE + " - Перезагрузить конфиг");
        sender.sendMessage(ChatColor.YELLOW + "/neohide status" + ChatColor.WHITE + " - Статус защиты");
        sender.sendMessage(ChatColor.YELLOW + "/neohide hide <cmd>" + ChatColor.WHITE + " - Скрыть команду");
        sender.sendMessage(ChatColor.YELLOW + "/neohide unhide <cmd>" + ChatColor.WHITE + " - Показать команду");
        sender.sendMessage(ChatColor.YELLOW + "/neohide list" + ChatColor.WHITE + " - Список скрытых команд");
        sender.sendMessage(ChatColor.YELLOW + "/neohide protect <perm>" + ChatColor.WHITE + " - Защитить право");
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "==========[ NeoHide Status ]==========");
        sender.sendMessage(ChatColor.YELLOW + "Скрытие команд: " +
                (plugin.getConfigManager().isHideCommandsEnabled() ?
                        ChatColor.GREEN + "Включено" : ChatColor.RED + "Выключено"));
        sender.sendMessage(ChatColor.YELLOW + "Защита прав: " +
                (plugin.getConfigManager().isPermissionProtectionEnabled() ?
                        ChatColor.GREEN + "Включена" : ChatColor.RED + "Выключена"));
        sender.sendMessage(ChatColor.YELLOW + "Защита OP: " +
                (plugin.getConfigManager().isOpProtectionEnabled() ?
                        ChatColor.GREEN + "Включена" : ChatColor.RED + "Выключена"));
        sender.sendMessage(ChatColor.YELLOW + "Скрыто команд: " +
                ChatColor.GREEN + plugin.getCommandManager().getHiddenCommands().size());
        sender.sendMessage(ChatColor.YELLOW + "Защищено прав: " +
                ChatColor.GREEN + plugin.getConfigManager().getProtectedPermissions().size());
    }
}