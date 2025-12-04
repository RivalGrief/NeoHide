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

    // Основные настройки
    private boolean hideCommandsEnabled = true;
    private boolean hideFromTabComplete = true;
    private boolean hideOpCommands = true;
    private boolean permissionProtectionEnabled = true;
    private boolean opProtectionEnabled = true;
    private boolean autoProtectionEnabled = true;
    private boolean logToConsole = true;
    private boolean logToFile = true;

    // Настройки базы данных
    private String databaseType = "SQLITE";
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "neohide";
    private String mysqlUsername = "root";
    private String mysqlPassword = "";

    // Настройки веб-сервера
    private boolean webEnabled = false;
    private int webPort = 8080;
    private String webAuthToken = "neohide-secret-token-change-me";

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

        // Загрузка основных значений
        hideCommandsEnabled = config.getBoolean("hide-commands.enabled", true);
        hideFromTabComplete = config.getBoolean("hide-commands.hide-from-tab", true);
        hideOpCommands = config.getBoolean("hide-commands.hide-op-commands", true);
        permissionProtectionEnabled = config.getBoolean("permission-protection.enabled", true);
        opProtectionEnabled = config.getBoolean("permission-protection.protect-op", true);
        autoProtectionEnabled = config.getBoolean("permission-protection.auto-fix", true);
        logToConsole = config.getBoolean("logging.console", true);
        logToFile = config.getBoolean("logging.file", true);

        // Загрузка настроек БД
        databaseType = config.getString("database.type", "SQLITE");
        mysqlHost = config.getString("database.mysql.host", "localhost");
        mysqlPort = config.getInt("database.mysql.port", 3306);
        mysqlDatabase = config.getString("database.mysql.database", "neohide");
        mysqlUsername = config.getString("database.mysql.username", "root");
        mysqlPassword = config.getString("database.mysql.password", "");

        // Загрузка настроек веб-сервера
        webEnabled = config.getBoolean("web.enabled", false);
        webPort = config.getInt("web.port", 8080);
        webAuthToken = config.getString("web.auth-token", "neohide-secret-token-change-me");

        // Списки
        hiddenCommands = config.getStringList("hidden-commands");
        protectedPermissions = config.getStringList("protected-permissions");

        // Псевдонимы команд
        if (config.contains("fake-aliases")) {
            for (String key : config.getConfigurationSection("fake-aliases").getKeys(false)) {
                fakeAliases.put(key, config.getString("fake-aliases." + key));
            }
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

    // ========== ГЕТТЕРЫ ==========

    // Основные настройки
    public boolean isHideCommandsEnabled() { return hideCommandsEnabled; }
    public boolean isHideFromTabComplete() { return hideFromTabComplete; }
    public boolean isHideOpCommands() { return hideOpCommands; }
    public boolean isPermissionProtectionEnabled() { return permissionProtectionEnabled; }
    public boolean isOpProtectionEnabled() { return opProtectionEnabled; }
    public boolean isAutoProtectionEnabled() { return autoProtectionEnabled; }
    public boolean isLogToConsole() { return logToConsole; }
    public boolean isLogToFile() { return logToFile; }

    // Настройки БД
    public String getDatabaseType() { return databaseType; }
    public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUsername() { return mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }

    // Настройки веб-сервера
    public boolean isWebEnabled() { return webEnabled; }
    public int getWebPort() { return webPort; }
    public String getWebAuthToken() { return webAuthToken; }

    // Списки
    public List<String> getHiddenCommands() { return new ArrayList<>(hiddenCommands); }
    public List<String> getProtectedPermissions() { return new ArrayList<>(protectedPermissions); }
    public Map<String, String> getFakeAliases() { return new HashMap<>(fakeAliases); }

    // ========== СЕТТЕРЫ ==========

    // Основные настройки
    public void setHideCommandsEnabled(boolean value) {
        hideCommandsEnabled = value;
        config.set("hide-commands.enabled", value);
        plugin.saveConfig();
    }

    public void setHideFromTabComplete(boolean value) {
        hideFromTabComplete = value;
        config.set("hide-commands.hide-from-tab", value);
        plugin.saveConfig();
    }

    public void setHideOpCommands(boolean value) {
        hideOpCommands = value;
        config.set("hide-commands.hide-op-commands", value);
        plugin.saveConfig();
    }

    public void setPermissionProtectionEnabled(boolean value) {
        permissionProtectionEnabled = value;
        config.set("permission-protection.enabled", value);
        plugin.saveConfig();
    }

    public void setOpProtectionEnabled(boolean value) {
        opProtectionEnabled = value;
        config.set("permission-protection.protect-op", value);
        plugin.saveConfig();
    }

    public void setAutoProtectionEnabled(boolean value) {
        autoProtectionEnabled = value;
        config.set("permission-protection.auto-fix", value);
        plugin.saveConfig();
    }

    public void setLogToConsole(boolean value) {
        logToConsole = value;
        config.set("logging.console", value);
        plugin.saveConfig();
    }

    public void setLogToFile(boolean value) {
        logToFile = value;
        config.set("logging.file", value);
        plugin.saveConfig();
    }

    // Настройки веб-сервера
    public void setWebEnabled(boolean value) {
        webEnabled = value;
        config.set("web.enabled", value);
        plugin.saveConfig();
    }

    public void setWebPort(int value) {
        webPort = value;
        config.set("web.port", value);
        plugin.saveConfig();
    }

    public void setWebAuthToken(String value) {
        webAuthToken = value;
        config.set("web.auth-token", value);
        plugin.saveConfig();
    }

    // Управление скрытыми командами
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

    // Управление защищенными правами
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

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

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

    public boolean isPermissionProtected(String permission) {
        String perm = permission.toLowerCase();

        // Проверка точного совпадения
        if (protectedPermissions.contains(perm)) {
            return true;
        }

        // Проверка по wildcard (например, "neohide.*" защищает "neohide.reload")
        for (String protectedPerm : protectedPermissions) {
            if (protectedPerm.endsWith(".*")) {
                String prefix = protectedPerm.substring(0, protectedPerm.length() - 2);
                if (perm.startsWith(prefix)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void saveConfig() {
        config.set("hidden-commands", hiddenCommands);
        config.set("protected-permissions", protectedPermissions);
        plugin.saveConfig();
    }

    // ========== МЕТОДЫ ДЛЯ КОМАНД ==========

    public String getDatabaseStatus() {
        return String.format("Тип: %s, Подключен: %s",
                databaseType,
                databaseType.equals("MYSQL") ?
                        String.format("%s:%d/%s", mysqlHost, mysqlPort, mysqlDatabase) :
                        "SQLite (локальная БД)");
    }

    public String getWebStatus() {
        // Проверяем, был ли токен изменен с дефолтного
        boolean isDefaultToken = webAuthToken.equals("neohide-secret-token-change-me");

        return String.format("Веб-интерфейс: %s, Порт: %d, Токен: %s",
                webEnabled ? "Включен" : "Выключен",
                webPort,
                isDefaultToken ? "НЕ ИЗМЕНЕН (опасно!)" : "Установлен (смотрите в консоли)");
    }

    public Map<String, Object> getConfigSummary() {
        Map<String, Object> summary = new HashMap<>();

        summary.put("hide_commands_enabled", hideCommandsEnabled);
        summary.put("permission_protection_enabled", permissionProtectionEnabled);
        summary.put("op_protection_enabled", opProtectionEnabled);
        summary.put("web_enabled", webEnabled);
        summary.put("hidden_commands_count", hiddenCommands.size());
        summary.put("protected_permissions_count", protectedPermissions.size());
        summary.put("database_type", databaseType);
        summary.put("web_token_changed", !webAuthToken.equals("neohide-secret-token-change-me"));

        return summary;
    }

    // ========== МЕТОДЫ ДЛЯ ВЕБ-ИНТЕРФЕЙСА ==========

    /**
     * Получить информацию о конфигурации для веб-интерфейса
     */
    public Map<String, Object> getWebConfigInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("enabled", webEnabled);
        info.put("port", webPort);
        info.put("token_set", !webAuthToken.equals("neohide-secret-token-change-me"));
        info.put("token_length", webAuthToken.length());
        info.put("last_modified", new File(plugin.getDataFolder(), "config.yml").lastModified());
        return info;
    }

    /**
     * Проверить валидность токена
     */
    public boolean validateWebToken(String token) {
        return webAuthToken.equals(token);
    }

    /**
     * Получить текущую конфигурацию в виде Map
     */
    public Map<String, Object> getConfigMap() {
        Map<String, Object> configMap = new HashMap<>();

        // Основные настройки
        configMap.put("hide_commands_enabled", hideCommandsEnabled);
        configMap.put("permission_protection_enabled", permissionProtectionEnabled);
        configMap.put("op_protection_enabled", opProtectionEnabled);
        configMap.put("web_enabled", webEnabled);
        configMap.put("web_port", webPort);

        // Списки
        configMap.put("hidden_commands", new ArrayList<>(hiddenCommands));
        configMap.put("protected_permissions", new ArrayList<>(protectedPermissions));
        configMap.put("fake_aliases", new HashMap<>(fakeAliases));

        // Информация о токене (без самого токена из соображений безопасности)
        configMap.put("has_token", !webAuthToken.equals("neohide-secret-token-change-me"));

        return configMap;
    }
}