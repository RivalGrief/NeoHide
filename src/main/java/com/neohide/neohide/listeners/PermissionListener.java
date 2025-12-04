package com.neohide.neohide.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import com.neohide.neohide.NeoHide;

public class PermissionListener implements Listener {

    private final NeoHide plugin;

    public PermissionListener(NeoHide plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getPermissionProtection().onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPermissionProtection().onPlayerQuit(event.getPlayer());
    }
}