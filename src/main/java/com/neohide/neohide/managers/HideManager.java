package com.neohide.neohide.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.neohide.neohide.NeoHide;

import java.util.*;

public class HideManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private Set<UUID> hiddenPlayers; // Игроки, которые скрыты от других
    private Set<UUID> vanishedPlayers; // Игроки в ванише
    private Map<UUID, Set<UUID>> spyTargets; // Кто за кем следит (шпион)
    private Set<UUID> commandSpyEnabled; // Игроки с включенным шпионажем команд

    public HideManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = ((NeoHide) plugin).getConfigManager();
        this.hiddenPlayers = new HashSet<>();
        this.vanishedPlayers = new HashSet<>();
        this.spyTargets = new HashMap<>();
        this.commandSpyEnabled = new HashSet<>();

        loadData();
    }

    private void loadData() {
        // Загрузка данных из файла
        // В реальной реализации можно добавить загрузку из data.yml
    }

    // ========== Скрытие игроков ==========

    /**
     * Скрыть игрока от других игроков
     */
    public void hidePlayer(Player player) {
        hiddenPlayers.add(player.getUniqueId());

        // Скрываем игрока от всех
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player) && !canSee(online, player)) {
                online.hidePlayer(plugin, player);
            }
        }

        logAction(player.getName() + " скрылся от других игроков");
    }

    /**
     * Показать игрока другим игрокам
     */
    public void showPlayer(Player player) {
        hiddenPlayers.remove(player.getUniqueId());

        // Показываем игрока всем
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.showPlayer(plugin, player);
            }
        }

        logAction(player.getName() + " теперь виден другим игрокам");
    }

    /**
     * Проверка, скрыт ли игрок
     */
    public boolean isPlayerHidden(Player player) {
        return hiddenPlayers.contains(player.getUniqueId());
    }

    // ========== Ванш (Vanished) ==========

    /**
     * Включить ванш для игрока
     */
    public void vanishPlayer(Player player) {
        vanishedPlayers.add(player.getUniqueId());
        hidePlayer(player); // Также скрываем от других

        // Убираем из таба
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player) && !canSee(online, player)) {
                online.hidePlayer(plugin, player);
            }
        }

        // Убираем из списка игроков
        player.setPlayerListName(null);

        player.sendMessage("§aВы теперь невидимы для других игроков!");
        logAction(player.getName() + " активировал ванш");
    }

    /**
     * Выключить ванш для игрока
     */
    public void unvanishPlayer(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
        showPlayer(player); // Показываем игрока

        // Возвращаем в список игроков
        updatePlayerListName(player);

        player.sendMessage("§aВы теперь видимы для других игроков!");
        logAction(player.getName() + " выключил ванш");
    }

    /**
     * Проверка, в ванше ли игрок
     */
    public boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    // ========== Шпионаж (Spy) ==========

    /**
     * Включить шпионаж за игроком
     */
    public void addSpy(Player spy, Player target) {
        spyTargets.computeIfAbsent(spy.getUniqueId(), k -> new HashSet<>())
                .add(target.getUniqueId());

        spy.sendMessage("§aТеперь вы следите за игроком " + target.getName());
        logAction(spy.getName() + " начал следить за " + target.getName());
    }

    /**
     * Выключить шпионаж за игроком
     */
    public void removeSpy(Player spy, Player target) {
        Set<UUID> targets = spyTargets.get(spy.getUniqueId());
        if (targets != null) {
            targets.remove(target.getUniqueId());
            if (targets.isEmpty()) {
                spyTargets.remove(spy.getUniqueId());
            }
        }

        spy.sendMessage("§aВы больше не следите за игроком " + target.getName());
    }

    /**
     * Проверка, следит ли игрок за целью
     */
    public boolean isSpyingOn(Player spy, Player target) {
        Set<UUID> targets = spyTargets.get(spy.getUniqueId());
        return targets != null && targets.contains(target.getUniqueId());
    }

    /**
     * Получить всех, за кем следит игрок
     */
    public Set<UUID> getSpyTargets(Player spy) {
        return spyTargets.getOrDefault(spy.getUniqueId(), new HashSet<>());
    }

    /**
     * Получить всех, кто следит за игроком
     */
    public List<Player> getSpiesWatching(Player target) {
        List<Player> spies = new ArrayList<>();
        for (Map.Entry<UUID, Set<UUID>> entry : spyTargets.entrySet()) {
            if (entry.getValue().contains(target.getUniqueId())) {
                Player spy = Bukkit.getPlayer(entry.getKey());
                if (spy != null) {
                    spies.add(spy);
                }
            }
        }
        return spies;
    }

    // ========== Шпионаж команд ==========

    /**
     * Включить шпионаж команд
     */
    public void enableCommandSpy(Player player) {
        commandSpyEnabled.add(player.getUniqueId());
        player.sendMessage("§aШпионаж команд включен!");
    }

    /**
     * Выключить шпионаж команд
     */
    public void disableCommandSpy(Player player) {
        commandSpyEnabled.remove(player.getUniqueId());
        player.sendMessage("§cШпионаж команд выключен!");
    }

    /**
     * Проверка, включен ли шпионаж команд у игрока
     */
    public boolean hasCommandSpyEnabled(Player player) {
        return commandSpyEnabled.contains(player.getUniqueId());
    }

    /**
     * Отправить сообщение о команде шпионам
     */
    public void sendCommandToSpies(Player sender, String command) {
        String message = String.format("§7[CMD] §e%s §7-> §f%s",
                sender.getName(), command);

        for (UUID spyId : commandSpyEnabled) {
            Player spy = Bukkit.getPlayer(spyId);
            if (spy != null && !spy.equals(sender) && spy.hasPermission("neohide.spy.commands")) {
                spy.sendMessage(message);
            }
        }
    }

    // ========== Вспомогательные методы ==========

    /**
     * Может ли игрок видеть другого игрока
     */
    public boolean canSee(Player viewer, Player target) {
        // Если зритель имеет право обхода
        if (viewer.hasPermission("neohide.bypass.view")) {
            return true;
        }

        // Если цель в ванше
        if (isVanished(target)) {
            return viewer.hasPermission("neohide.vanish.see");
        }

        // Если цель скрыта
        if (isPlayerHidden(target)) {
            return viewer.hasPermission("neohide.hide.see");
        }

        // Проверка шпионажа
        if (isSpyingOn(viewer, target)) {
            return true;
        }

        return true; // По умолчанию видит
    }

    /**
     * Обновить список игроков для всех
     */
    public void updateAllPlayerLists() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            updatePlayerListName(online);
        }
    }

    /**
     * Обновить имя игрока в списке
     */
    private void updatePlayerListName(Player player) {
        if (isVanished(player)) {
            player.setPlayerListName("§7[V] §8" + player.getName());
        } else if (isPlayerHidden(player)) {
            player.setPlayerListName("§7[H] §8" + player.getName());
        } else {
            player.setPlayerListName(player.getDisplayName());
        }
    }

    /**
     * Обработка входа игрока
     */
    public void onPlayerJoin(Player player) {
        // Скрываем от игрока других скрытых игроков
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player) && !canSee(player, online)) {
                player.hidePlayer(plugin, online);
            }
        }

        // Скрываем игрока от других если он скрыт
        if (isPlayerHidden(player)) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player) && !canSee(online, player)) {
                    online.hidePlayer(plugin, player);
                }
            }
        }

        // Обновляем список
        updatePlayerListName(player);
    }

    /**
     * Обработка выхода игрока
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        hiddenPlayers.remove(uuid);
        vanishedPlayers.remove(uuid);
        commandSpyEnabled.remove(uuid);

        // Убираем из шпионажа
        spyTargets.remove(uuid);
        for (Set<UUID> targets : spyTargets.values()) {
            targets.remove(uuid);
        }
    }

    /**
     * Получить всех скрытых игроков
     */
    public List<Player> getHiddenPlayers() {
        List<Player> hidden = new ArrayList<>();
        for (UUID uuid : hiddenPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                hidden.add(player);
            }
        }
        return hidden;
    }

    /**
     * Получить всех игроков в ванше
     */
    public List<Player> getVanishedPlayers() {
        List<Player> vanished = new ArrayList<>();
        for (UUID uuid : vanishedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                vanished.add(player);
            }
        }
        return vanished;
    }

    /**
     * Получить всех с включенным шпионажем команд
     */
    public List<Player> getCommandSpies() {
        List<Player> spies = new ArrayList<>();
        for (UUID uuid : commandSpyEnabled) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                spies.add(player);
            }
        }
        return spies;
    }

    /**
     * Очистить все данные
     */
    public void clearAll() {
        hiddenPlayers.clear();
        vanishedPlayers.clear();
        spyTargets.clear();
        commandSpyEnabled.clear();

        // Показать всех игроков
        for (Player online : Bukkit.getOnlinePlayers()) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                online.showPlayer(plugin, other);
            }
            online.setPlayerListName(online.getDisplayName());
        }
    }

    /**
     * Логирование действий
     */
    private void logAction(String message) {
        if (configManager.isLogToConsole()) {
            plugin.getLogger().info("[Hide] " + message);
        }
    }

    /**
     * Сохранить данные
     */
    public void saveData() {
        // В реальной реализации можно добавить сохранение в data.yml
    }
}