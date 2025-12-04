package com.neohide.neohide;

import org.bukkit.plugin.java.JavaPlugin;
import com.neohide.neohide.managers.*;
import com.neohide.neohide.listeners.*;
import com.neohide.neohide.commands.*;
import com.neohide.neohide.tabcompleters.*;
import com.neohide.neohide.protection.*;

public class NeoHide extends JavaPlugin {

    private static NeoHide instance;
    private ConfigManager configManager;
    private CommandManager commandManager;
    private PermissionProtection permissionProtection;
    private HideManager hideManager;

    @Override
    public void onEnable() {
        instance = this;

        // Инициализация менеджеров
        this.configManager = new ConfigManager(this);
        this.commandManager = new CommandManager(this);
        this.permissionProtection = new PermissionProtection(this);
        this.hideManager = new HideManager(this);

        // Загрузка конфигурации
        configManager.loadConfig();

        // Настройка команд
        commandManager.setupCommands();

        // Регистрация событий
        registerEvents();

        // Регистрация команд плагина
        registerPluginCommands();

        // Запуск таймеров
        startTasks();

        // Защита разрешений
        permissionProtection.setupProtection();

        getLogger().info("NeoHide v" + getDescription().getVersion() + " успешно запущен!");
        getLogger().info("Скрыто команд: " + commandManager.getHiddenCommands().size());
        getLogger().info("Защита прав: " + (configManager.isPermissionProtectionEnabled() ? "Включена" : "Выключена"));
    }

    @Override
    public void onDisable() {
        // Восстановление оригинальных команд
        commandManager.restoreCommands();

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
}