package com.neohide.neohide.managers;

import com.neohide.neohide.NeoHide;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Field;
import java.util.*;

public class CommandManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private Map<String, Command> originalCommands;
    private Map<String, String> fakeCommandMap;

    public CommandManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = ((NeoHide) plugin).getConfigManager();
        this.originalCommands = new HashMap<>();
        this.fakeCommandMap = new HashMap<>();
    }

    public void setupCommands() {
        // Сохраняем оригинальные команды
        saveOriginalCommands();

        // Скрываем команды
        hideCommands();

        // Настраиваем псевдонимы
        setupFakeAliases();
    }

    private void saveOriginalCommands() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            // Сохраняем копию оригинальных команд
            for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                originalCommands.put(entry.getKey().toLowerCase(), entry.getValue());
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось сохранить оригинальные команды: " + e.getMessage());
        }
    }

    public void hideCommands() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            // Скрываем команды
            List<String> toRemove = new ArrayList<>();
            for (String command : knownCommands.keySet()) {
                if (configManager.isCommandHidden(command)) {
                    toRemove.add(command);
                }
            }

            for (String command : toRemove) {
                knownCommands.remove(command);
            }

            plugin.getLogger().info("Скрыто " + toRemove.size() + " команд");

        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось скрыть команды: " + e.getMessage());
        }
    }

    private void setupFakeAliases() {
        Map<String, String> aliases = configManager.getFakeAliases();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            fakeCommandMap.put(entry.getKey().toLowerCase(), entry.getValue());
        }
    }

    public String getFakeResponse(String command) {
        return fakeCommandMap.get(command.toLowerCase());
    }

    public boolean hasFakeResponse(String command) {
        return fakeCommandMap.containsKey(command.toLowerCase());
    }

    public void restoreCommands() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            // Восстанавливаем оригинальные команды
            for (Map.Entry<String, Command> entry : originalCommands.entrySet()) {
                knownCommands.put(entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось восстановить команды: " + e.getMessage());
        }
    }

    public List<String> getHiddenCommands() {
        return configManager.getHiddenCommands();
    }

    public void addHiddenCommand(String command) {
        configManager.addHiddenCommand(command);
        hideCommands(); // Применяем изменения
    }

    public void removeHiddenCommand(String command) {
        configManager.removeHiddenCommand(command);
        restoreCommands();
        hideCommands();
    }
}