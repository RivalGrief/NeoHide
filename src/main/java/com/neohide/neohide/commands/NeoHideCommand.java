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

            case "token":
                handleTokenCommand(sender, args);
                break;

            case "web":
                handleWebCommand(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Неизвестная подкоманда. Используйте /neohide help");
        }

        return true;
    }

    private void handleTokenCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("neohide.token")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав!");
            return;
        }

        if (args.length == 2 && args[1].equalsIgnoreCase("generate")) {
            // Генерация нового токена
            String newToken = plugin.generateNewToken();
            if (newToken != null) {
                sender.sendMessage(ChatColor.GREEN + "════════════════════════════════════════");
                sender.sendMessage(ChatColor.GREEN + "✅ Новый токен успешно сгенерирован!");
                sender.sendMessage(ChatColor.GOLD + "🔑 Токен: " + ChatColor.WHITE + newToken);
                sender.sendMessage(ChatColor.YELLOW + "📋 Скопируйте этот токен для доступа к веб-интерфейсу");
                sender.sendMessage(ChatColor.YELLOW + "🌐 Адрес: " + ChatColor.GREEN +
                        "http://localhost:" + plugin.getConfigManager().getWebPort());
                sender.sendMessage(ChatColor.GREEN + "════════════════════════════════════════");

                // Предупреждение для консоли
                if (!(sender instanceof Player)) {
                    plugin.getLogger().warning("════════════════════════════════════════");
                    plugin.getLogger().warning("⚠️  Был сгенерирован новый токен веб-интерфейса!");
                    plugin.getLogger().warning("🔑 Старый токен больше не действителен!");
                    plugin.getLogger().warning("════════════════════════════════════════");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "❌ Ошибка генерации токена!");
            }
        } else if (args.length == 2 && args[1].equalsIgnoreCase("show")) {
            // Показать токен (только для консоли)
            if (sender instanceof Player) {
                sender.sendMessage(ChatColor.RED + "❌ По соображениям безопасности эта команда доступна только из консоли!");
                sender.sendMessage(ChatColor.YELLOW + "Используйте: " + ChatColor.GREEN + "/neohide token generate");
                return;
            }

            String token = plugin.getConfigManager().getWebAuthToken();
            sender.sendMessage(ChatColor.GREEN + "════════════════════════════════════════");
            sender.sendMessage(ChatColor.GOLD + "🔑 Текущий токен веб-интерфейса:");
            sender.sendMessage(ChatColor.WHITE + token);
            sender.sendMessage(ChatColor.GREEN + "════════════════════════════════════════");
        } else {
            // Информация о токене
            String token = plugin.getConfigManager().getWebAuthToken();
            boolean isDefaultToken = token.equals("neohide-secret-token-change-me");

            sender.sendMessage(ChatColor.GREEN + "════════════════════════════════════════");
            sender.sendMessage(ChatColor.GOLD + "🔐 Управление токенами веб-интерфейса");
            sender.sendMessage(ChatColor.GREEN + "════════════════════════════════════════");

            if (isDefaultToken) {
                sender.sendMessage(ChatColor.RED + "⚠️  ВНИМАНИЕ: Используется дефолтный токен!");
                sender.sendMessage(ChatColor.YELLOW + "Это небезопасно! Сгенерируйте новый токен:");
                sender.sendMessage(ChatColor.GREEN + "/neohide token generate");
            } else {
                sender.sendMessage(ChatColor.GREEN + "✅ Токен установлен и активен");
                sender.sendMessage(ChatColor.YELLOW + "Длина токена: " + ChatColor.WHITE + token.length() + " символов");
            }

            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "Доступные команды:");
            sender.sendMessage(ChatColor.YELLOW + "/neohide token generate" + ChatColor.WHITE + " - Сгенерировать новый токен");
            sender.sendMessage(ChatColor.YELLOW + "/neohide web" + ChatColor.WHITE + " - Информация о веб-интерфейсе");

            // Только для консоли
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.YELLOW + "/neohide token show" + ChatColor.WHITE + " - Показать текущий токен (консоль)");
            }

            sender.sendMessage(ChatColor.GREEN + "════════════════════════════════════════");
        }
    }

    private void handleWebCommand(CommandSender sender) {
        if (!sender.hasPermission("neohide.web")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав!");
            return;
        }

        boolean webEnabled = plugin.getConfigManager().isWebEnabled();
        int webPort = plugin.getConfigManager().getWebPort();
        String token = plugin.getConfigManager().getWebAuthToken();
        boolean isDefaultToken = token.equals("neohide-secret-token-change-me");

        sender.sendMessage(ChatColor.GREEN + "════════════════════════════════════════");
        sender.sendMessage(ChatColor.GOLD + "🌐 Веб-интерфейс NeoHide");
        sender.sendMessage(ChatColor.GREEN + "════════════════════════════════════════");

        sender.sendMessage(ChatColor.YELLOW + "Статус: " +
                (webEnabled ? ChatColor.GREEN + "Включен" : ChatColor.RED + "Выключен"));

        if (webEnabled) {
            sender.sendMessage(ChatColor.YELLOW + "Порт: " + ChatColor.WHITE + webPort);
            sender.sendMessage(ChatColor.YELLOW + "Адрес: " + ChatColor.GREEN +
                    "http://localhost:" + webPort);

            if (isDefaultToken) {
                sender.sendMessage(ChatColor.RED + "⚠️  Токен: Дефолтный (небезопасно!)");
            } else {
                sender.sendMessage(ChatColor.GREEN + "✅ Токен: Установлен");
            }

            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "Управление:");
            sender.sendMessage(ChatColor.GREEN + "/neohide token" + ChatColor.WHITE + " - Управление токенами");
            sender.sendMessage(ChatColor.GREEN + "/neohide token generate" + ChatColor.WHITE + " - Новый токен");

            // Проверка статуса веб-сервера
            if (plugin.getWebServerManager() != null) {
                boolean isRunning = plugin.getWebServerManager().isRunning();
                sender.sendMessage(ChatColor.YELLOW + "Сервер: " +
                        (isRunning ? ChatColor.GREEN + "Запущен" : ChatColor.RED + "Остановлен"));
            }
        } else {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "Чтобы включить веб-интерфейс:");
            sender.sendMessage(ChatColor.WHITE + "1. Откройте plugins/NeoHide/config.yml");
            sender.sendMessage(ChatColor.WHITE + "2. Установите web.enabled: true");
            sender.sendMessage(ChatColor.WHITE + "3. Выполните " + ChatColor.GREEN + "/neohide reload");
        }

        sender.sendMessage(ChatColor.GREEN + "════════════════════════════════════════");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════════════");
        sender.sendMessage(ChatColor.GOLD + "🛡️  NeoHelp - Помощь по командам");
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════════════");

        sender.sendMessage(ChatColor.YELLOW + "📋 Основные команды:");
        sender.sendMessage(ChatColor.GREEN + "/neohide help" + ChatColor.WHITE + " - Показать это сообщение");
        sender.sendMessage(ChatColor.GREEN + "/neohide reload" + ChatColor.WHITE + " - Перезагрузить конфиг");
        sender.sendMessage(ChatColor.GREEN + "/neohide status" + ChatColor.WHITE + " - Статус защиты");

        sender.sendMessage(ChatColor.YELLOW + "🔧 Управление командами:");
        sender.sendMessage(ChatColor.GREEN + "/neohide hide <cmd>" + ChatColor.WHITE + " - Скрыть команду");
        sender.sendMessage(ChatColor.GREEN + "/neohide unhide <cmd>" + ChatColor.WHITE + " - Показать команду");
        sender.sendMessage(ChatColor.GREEN + "/neohide list" + ChatColor.WHITE + " - Список скрытых команд");

        sender.sendMessage(ChatColor.YELLOW + "🛡️  Управление правами:");
        sender.sendMessage(ChatColor.GREEN + "/neohide protect <perm>" + ChatColor.WHITE + " - Защитить право");

        sender.sendMessage(ChatColor.YELLOW + "🌐 Веб-интерфейс:");
        sender.sendMessage(ChatColor.GREEN + "/neohide token" + ChatColor.WHITE + " - Управление токенами");
        sender.sendMessage(ChatColor.GREEN + "/neohide web" + ChatColor.WHITE + " - Информация о веб-интерфейсе");

        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "📖 Подробнее: https://github.com/RivalGrief/NeoHide");
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════════════");
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════════════");
        sender.sendMessage(ChatColor.GOLD + "📊 NeoHide - Статус защиты");
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════════════");

        sender.sendMessage(ChatColor.YELLOW + "🛡️  Скрытие команд: " +
                (plugin.getConfigManager().isHideCommandsEnabled() ?
                        ChatColor.GREEN + "Включено" : ChatColor.RED + "Выключено"));

        sender.sendMessage(ChatColor.YELLOW + "🔒 Защита прав: " +
                (plugin.getConfigManager().isPermissionProtectionEnabled() ?
                        ChatColor.GREEN + "Включена" : ChatColor.RED + "Выключена"));

        sender.sendMessage(ChatColor.YELLOW + "👑 Защита OP: " +
                (plugin.getConfigManager().isOpProtectionEnabled() ?
                        ChatColor.GREEN + "Включена" : ChatColor.RED + "Выключена"));

        sender.sendMessage(ChatColor.YELLOW + "📋 Скрыто команд: " +
                ChatColor.WHITE + plugin.getCommandManager().getHiddenCommands().size());

        sender.sendMessage(ChatColor.YELLOW + "🔐 Защищено прав: " +
                ChatColor.WHITE + plugin.getConfigManager().getProtectedPermissions().size());

        // Информация о веб-интерфейсе
        boolean webEnabled = plugin.getConfigManager().isWebEnabled();
        String token = plugin.getConfigManager().getWebAuthToken();
        boolean isDefaultToken = token.equals("neohide-secret-token-change-me");

        sender.sendMessage(ChatColor.YELLOW + "🌐 Веб-интерфейс: " +
                (webEnabled ? ChatColor.GREEN + "Включен" : ChatColor.RED + "Выключен"));

        if (webEnabled) {
            if (isDefaultToken) {
                sender.sendMessage(ChatColor.RED + "⚠️  Используется дефолтный токен!");
            } else {
                sender.sendMessage(ChatColor.GREEN + "✅ Токен установлен");
            }
        }

        // Информация о БД
        boolean dbConnected = plugin.getDatabaseManager().isConnected();
        sender.sendMessage(ChatColor.YELLOW + "🗄️  База данных: " +
                (dbConnected ? ChatColor.GREEN + "Подключена" : ChatColor.RED + "Ошибка"));

        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════════════");
    }
}