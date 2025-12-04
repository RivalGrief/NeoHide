package com.neohide.neohide.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration dataConfig;
    private File dataFile;

    private boolean hideCommandsEnabled = true;
    private boolean hideFromTabComplete = true;
    private boolean hideOpCommands = true;
    private boolean permissionProtectionEnabled = true;
    private boolean opProtectionEnabled = true;
    private boolean autoProtectionEnabled = true;
    private boolean logToConsole = true;
    private boolean logToFile = true;

    private List<String> hiddenCommands = new ArrayList<>();
    private List<String> protectedPermissions = new ArrayList<>();
    private Map<String, String> fakeAliases = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        // Основной конфиг
        plugin.saveDefaultConfig();
        config = plugin.getConfig();

        // Файл данных
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Загрузка значений
        hideCommandsEnabled = config.getBoolean("hide-commands.enabled", true);
        hideFromTabComplete = config.getBoolean("hide-commands.hide-from-tab", true);
        hideOpCommands = config.getBoolean("hide-commands.hide-op-commands", true);
        permissionProtectionEnabled = config.getBoolean("permission-protection.enabled", true);
        opProtectionEnabled = config.getBoolean("permission-protection.protect-op", true);
        autoProtectionEnabled = config.getBoolean("permission-protection.auto-fix", true);
        logToConsole = config.getBoolean("logging.console", true);
        logToFile = config.getBoolean("logging.file", true);

        // Списки
        hiddenCommands = config.getStringList("hidden-commands");
        protectedPermissions = config.getStringList("protected-permissions");

        // Псевдонимы команд
        for (String key : config.getConfigurationSection("fake-aliases").getKeys(false)) {
            fakeAliases.put(key, config.getString("fake-aliases." + key));
        }

        // Загрузка из data.yml
        loadHiddenData();
    }

    private void loadHiddenData() {
        if (dataConfig.contains("manually-hidden")) {
            List<String> manual = dataConfig.getStringList("manually-hidden");
            hiddenCommands.addAll(manual);
        }
    }

    public void saveHiddenData() {
        dataConfig.set("manually-hidden", new ArrayList<>(new HashSet<>(hiddenCommands)));
        try {
            dataConfig.save(dataFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadConfig();
    }

    // Геттеры
    public boolean isHideCommandsEnabled() { return hideCommandsEnabled; }
    public boolean isHideFromTabComplete() { return hideFromTabComplete; }
    public boolean isHideOpCommands() { return hideOpCommands; }
    public boolean isPermissionProtectionEnabled() { return permissionProtectionEnabled; }
    public boolean isOpProtectionEnabled() { return opProtectionEnabled; }
    public boolean isAutoProtectionEnabled() { return autoProtectionEnabled; }
    public boolean isLogToConsole() { return logToConsole; }
    public boolean isLogToFile() { return logToFile; }
    public List<String> getHiddenCommands() { return new ArrayList<>(hiddenCommands); }
    public List<String> getProtectedPermissions() { return new ArrayList<>(protectedPermissions); }
    public Map<String, String> getFakeAliases() { return new HashMap<>(fakeAliases); }

    // Сеттеры
    public void addHiddenCommand(String command) {
        if (!hiddenCommands.contains(command.toLowerCase())) {
            hiddenCommands.add(command.toLowerCase());
            saveHiddenData();
        }
    }

    public void removeHiddenCommand(String command) {
        hiddenCommands.remove(command.toLowerCase());
        saveHiddenData();
    }

    public boolean isCommandHidden(String command) {
        String cmd = command.toLowerCase().replace("/", "");

        // Проверка точного совпадения
        if (hiddenCommands.contains(cmd)) {
            return true;
        }

        // Проверка по подстроке (например, "pl" должно скрывать "plugin")
        for (String hidden : hiddenCommands) {
            if (cmd.startsWith(hidden) || hidden.startsWith(cmd)) {
                return true;
            }
        }

        return false;
    }

    public void addProtectedPermission(String permission) {
        if (!protectedPermissions.contains(permission.toLowerCase())) {
            protectedPermissions.add(permission.toLowerCase());
            saveConfig();
        }
    }

    public void removeProtectedPermission(String permission) {
        protectedPermissions.remove(permission.toLowerCase());
        saveConfig();
    }

    private void saveConfig() {
        config.set("hidden-commands", hiddenCommands);
        config.set("protected-permissions", protectedPermissions);
        plugin.saveConfig();
    }
}