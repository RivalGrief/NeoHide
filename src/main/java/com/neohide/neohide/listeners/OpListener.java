package com.neohide.neohide.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import com.neohide.neohide.NeoHide;

public class OpListener implements Listener {

    private final NeoHide plugin;

    public OpListener(NeoHide plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOpCommand(PlayerCommandPreprocessEvent event) {
        if (!event.getPlayer().isOp()) {
            return;
        }

        String command = event.getMessage().toLowerCase();

        // Блокируем опасные команды выдачи OP
        String[] dangerousCommands = {
                "op ", "minecraft:op ",
                "deop ", "minecraft:deop ",
                "permission ", "perm ",
                "lp ", "luckperms ",
                "pex ", "permissionsex "
        };

        for (String dangerous : dangerousCommands) {
            if (command.startsWith(dangerous)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cЭта команда заблокирована системой защиты NeoHide!");

                plugin.getLogger().warning(String.format(
                        "[OP PROTECTION] %s пытался выполнить опасную команду: %s",
                        event.getPlayer().getName(), event.getMessage()
                ));
                break;
            }
        }
    }
}