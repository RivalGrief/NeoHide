package com.neohide.neohide;

import org.bukkit.plugin.java.JavaPlugin;
import com.neohide.neohide.managers.*;
import com.neohide.neohide.listeners.*;
import com.neohide.neohide.commands.*;
import com.neohide.neohide.tabcompleters.*;
import com.neohide.neohide.protection.*;
import com.neohide.neohide.managers.WebServerManager;

import java.util.logging.Level;

public class NeoHide extends JavaPlugin {

    private static NeoHide instance;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private PermissionProtection permissionProtection;
    private HideManager hideManager;
    private DatabaseManager databaseManager;
    private WebServerManager webServerManager;

    @Override
    public void onEnable() {
        instance = this;

        // Инициализация менеджеров
        this.configManager = new ConfigManager(this);
        this.commandManager = new CommandManager(this);
        this.permissionProtection = new PermissionProtection(this);
        this.hideManager = new HideManager(this);
        this.databaseManager = new DatabaseManager(this);

        // Загрузка конфигурации
        configManager.loadConfig();

        // Подключение к БД
        databaseManager.connect();

        // Настройка команд
        commandManager.setupCommands();

        // Загрузка скрытых команд из БД
        loadHiddenCommandsFromDB();

        // Регистрация событий
        registerEvents();

        // Регистрация команд плагина
        registerPluginCommands();

        // Запуск таймеров
        startTasks();

        // Защита разрешений
        permissionProtection.setupProtection();

        // Запуск веб-сервера если включен
        if (configManager.isWebEnabled()) {
            this.webServerManager = new WebServerManager(this);
            webServerManager.start();
        }

        getLogger().info("NeoHide v" + getDescription().getVersion() + " успешно запущен!");
        getLogger().info("Скрыто команд: " + commandManager.getHiddenCommands().size());
        getLogger().info("Защита прав: " + (configManager.isPermissionProtectionEnabled() ? "Включена" : "Выключена"));
        getLogger().info("База данных: " + (databaseManager.isConnected() ? "Подключена" : "Ошибка"));
        if (configManager.isWebEnabled()) {
            getLogger().info("Веб-интерфейс: http://localhost:" + configManager.getWebPort());
        }
    }

    @Override
    public void onDisable() {
        // Восстановление оригинальных команд
        commandManager.restoreCommands();

        // Остановка веб-сервера
        if (webServerManager != null) {
            webServerManager.stop();
        }

        // Закрытие соединения с БД
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("NeoHide отключен!");
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(new CommandListener(this), this);
        getServer().getPluginManager().registerEvents(new TabCompleteListener(this), this);
        getServer().getPluginManager().registerEvents(new PermissionListener(this), this);

        if (configManager.isOpProtectionEnabled()) {
            getServer().getPluginManager().registerEvents(new OpListener(this), this);
        }
    }

    private void registerPluginCommands() {
        getCommand("neohide").setExecutor(new NeoHideCommand(this));
        getCommand("neohide").setTabCompleter(new NeoHideTabCompleter());
    }

    private void startTasks() {
        // Таймер для периодической проверки защиты
        if (configManager.isAutoProtectionEnabled()) {
            getServer().getScheduler().runTaskTimer(this, () -> {
                permissionProtection.checkAndFixPermissions();
            }, 6000L, 6000L); // Каждые 5 минут
        }

        // Таймер для сохранения данных в БД
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (databaseManager.isConnected()) {
                savePlayerDataToDB();
            }
        }, 6000L, 6000L); // Каждые 5 минут
    }

    private void loadHiddenCommandsFromDB() {
        if (databaseManager.isConnected()) {
            try {
                java.util.List<String> dbCommands = databaseManager.getHiddenCommandsFromDB();
                for (String command : dbCommands) {
                    configManager.addHiddenCommand(command);
                }
                getLogger().info("Загружено " + dbCommands.size() + " скрытых команд из БД");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Ошибка загрузки команд из БД", e);
            }
        }
    }

    private void savePlayerDataToDB() {
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            boolean isHidden = hideManager.isPlayerHidden(player);
            boolean isVanished = hideManager.isVanished(player);
            boolean commandSpy = hideManager.hasCommandSpyEnabled(player);

            databaseManager.savePlayerData(
                    player.getUniqueId(),
                    player.getName(),
                    isHidden,
                    isVanished,
                    commandSpy
            );
        }
    }

    public static NeoHide getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public PermissionProtection getPermissionProtection() {
        return permissionProtection;
    }

    public HideManager getHideManager() {
        return hideManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public WebServerManager getWebServerManager() {
        return webServerManager;
    }
}