package com.neohide.neohide.protection;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;
import com.neohide.neohide.managers.ConfigManager;
import java.util.*;

public class PermissionProtection {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private Map<UUID, Set<String>> originalPermissions;
    private Map<UUID, PermissionAttachment> permissionAttachments;

    public PermissionProtection(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = ((com.neohide.neohide.NeoHide) plugin).getConfigManager();
        this.originalPermissions = new HashMap<>();
        this.permissionAttachments = new HashMap<>();
    }

    public void setupProtection() {
        if (!configManager.isPermissionProtectionEnabled()) {
            return;
        }

        // Сохраняем оригинальные права для всех онлайн игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            saveOriginalPermissions(player);
            applyProtection(player);
        }
    }

    private void saveOriginalPermissions(Player player) {
        Set<String> perms = new HashSet<>();
        for (PermissionAttachmentInfo entry : player.getEffectivePermissions()) {
            perms.add(entry.getPermission().toLowerCase());
        }
        originalPermissions.put(player.getUniqueId(), perms);
    }

    public void applyProtection(Player player) {
        if (!configManager.isPermissionProtectionEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // Удаляем старый PermissionAttachment если есть
        if (permissionAttachments.containsKey(uuid)) {
            player.removeAttachment(permissionAttachments.get(uuid));
        }

        // Создаем новый
        PermissionAttachment attachment = player.addAttachment(plugin);
        permissionAttachments.put(uuid, attachment);

        // Запрещаем защищенные права
        for (String permission : configManager.getProtectedPermissions()) {
            attachment.setPermission(permission, false);
        }

        // Если игрок не OP, запрещаем OP-права
        if (!player.isOp() && configManager.isHideOpCommands()) {
            attachment.setPermission("*", false);
            attachment.setPermission("bukkit.*", false);
            attachment.setPermission("minecraft.*", false);
        }

        player.recalculatePermissions();
    }

    public void removeProtection(Player player) {
        UUID uuid = player.getUniqueId();
        if (permissionAttachments.containsKey(uuid)) {
            player.removeAttachment(permissionAttachments.get(uuid));
            permissionAttachments.remove(uuid);
            player.recalculatePermissions();
        }
    }

    public void checkAndFixPermissions() {
        if (!configManager.isPermissionProtectionEnabled()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayerPermissions(player);
        }
    }

    private void checkPlayerPermissions(Player player) {
        // Проверяем, не получил ли игрок защищенные права
        Set<String> currentPerms = new HashSet<>();
        for (PermissionAttachmentInfo entry : player.getEffectivePermissions()) {
            currentPerms.add(entry.getPermission().toLowerCase());
        }

        // Получаем оригинальные права
        Set<String> original = originalPermissions.getOrDefault(player.getUniqueId(), new HashSet<>());

        // Находим новые права, которых не было в оригинале
        Set<String> newPerms = new HashSet<>(currentPerms);
        newPerms.removeAll(original);

        // Проверяем, есть ли среди них защищенные
        for (String protectedPerm : configManager.getProtectedPermissions()) {
            if (newPerms.contains(protectedPerm.toLowerCase())) {
                // Игрок получил защищенное право - забираем его
                logSecurityAlert(player, protectedPerm);
                applyProtection(player); // Применяем защиту заново
                break;
            }
        }
    }

    private void logSecurityAlert(Player player, String permission) {
        String message = String.format("[SECURITY] Игрок %s получил защищенное право: %s",
                player.getName(), permission);

        plugin.getLogger().warning(message);

        // Оповещение админов в игре
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("neohide.alerts")) {
                online.sendMessage("§c[NeoHide] §f" + message);
            }
        }
    }

    public void onPlayerJoin(Player player) {
        saveOriginalPermissions(player);
        applyProtection(player);
    }

    public void onPlayerQuit(Player player) {
        removeProtection(player);
        originalPermissions.remove(player.getUniqueId());
    }
}