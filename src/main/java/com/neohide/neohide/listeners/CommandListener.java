package com.neohide.neohide.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import com.neohide.neohide.NeoHide;

public class CommandListener implements Listener {

    private final NeoHide plugin;

    public CommandListener(NeoHide plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().isHideCommandsEnabled()) {
            return;
        }

        String command = event.getMessage().split(" ")[0].toLowerCase().replace("/", "");

        // Проверяем, скрыта ли команда
        if (plugin.getConfigManager().isCommandHidden(command)) {
            event.setCancelled(true);

            // Показываем фейковый ответ если настроено
            String fakeResponse = plugin.getCommandManager().getFakeResponse(command);
            if (fakeResponse != null) {
                event.getPlayer().sendMessage(fakeResponse);
            } else {
                event.getPlayer().sendMessage("§cНеизвестная команда. Напишите /help для помощи.");
            }

            // Логируем попытку
            logCommandAttempt(event.getPlayer().getName(), command, event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsoleCommand(ServerCommandEvent event) {
        // Пропускаем команды из консоли
        // (можно добавить фильтрацию для консоли если нужно)
    }

    private void logCommandAttempt(String player, String command, String fullCommand) {
        if (plugin.getConfigManager().isLogToConsole()) {
            plugin.getLogger().info(String.format(
                    "[BLOCKED] %s пытался выполнить: %s",
                    player, fullCommand
            ));
        }

        // Оповещение админов в игре
        String alert = String.format("§c[NeoHide] §fИгрок §e%s §fпытался выполнить скрытую команду: §c%s",
                player, command);

        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("neohide.alerts"))
                .forEach(p -> p.sendMessage(alert));
    }
}