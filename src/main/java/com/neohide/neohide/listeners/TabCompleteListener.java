package com.neohide.neohide.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;
import com.neohide.neohide.NeoHide;
import java.util.*;

public class TabCompleteListener implements Listener {

    private final NeoHide plugin;

    public TabCompleteListener(NeoHide plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!plugin.getConfigManager().isHideFromTabComplete()) {
            return;
        }

        String buffer = event.getBuffer().toLowerCase();
        if (!buffer.startsWith("/")) {
            return;
        }

        List<String> completions = event.getCompletions();
        List<String> filtered = new ArrayList<>();

        for (String completion : completions) {
            String cmd = completion.toLowerCase().replace("/", "").split(" ")[0];

            if (!plugin.getConfigManager().isCommandHidden(cmd)) {
                filtered.add(completion);
            }
        }

        event.setCompletions(filtered);
    }
}