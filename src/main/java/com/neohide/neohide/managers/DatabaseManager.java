package com.neohide.neohide.managers;

import org.bukkit.plugin.java.JavaPlugin;
import com.neohide.neohide.NeoHide;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private Connection connection;
    private DatabaseType dbType;

    public enum DatabaseType {
        SQLITE, MYSQL, H2
    }

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        String type = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();

        try {
            Class.forName("org.sqlite.JDBC");

            switch (type) {
                case "MYSQL":
                    dbType = DatabaseType.MYSQL;
                    connectMySQL();
                    break;
                case "H2":
                    dbType = DatabaseType.H2;
                    connectH2();
                    break;
                default:
                    dbType = DatabaseType.SQLITE;
                    connectSQLite();
            }

            createTables();
            plugin.getLogger().info("База данных подключена: " + dbType);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка подключения к БД", e);
        }
    }

    private void connectSQLite() throws SQLException {
        String path = plugin.getDataFolder().getAbsolutePath() + "/neohide.db";
        String url = "jdbc:sqlite:" + path;

        connection = DriverManager.getConnection(url);

        // Включаем поддержку внешних ключей для SQLite
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
    }

    private void connectMySQL() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");

        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "neohide");
        String username = plugin.getConfig().getString("database.mysql.username", "root");
        String password = plugin.getConfig().getString("database.mysql.password", "");

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&characterEncoding=utf8&serverTimezone=UTC",
                host, port, database);

        connection = DriverManager.getConnection(url, username, password);
    }

    private void connectH2() throws SQLException, ClassNotFoundException {
        Class.forName("org.h2.Driver");

        String path = plugin.getDataFolder().getAbsolutePath() + "/neohide";
        String url = "jdbc:h2:" + path + ";MODE=MySQL";

        connection = DriverManager.getConnection(url);
    }

    private void createTables() throws SQLException {
        // Таблица игроков
        String playersTable;
        if (dbType == DatabaseType.MYSQL) {
            playersTable = "CREATE TABLE IF NOT EXISTS neohide_players (" +
                    "  id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  uuid VARCHAR(36) NOT NULL UNIQUE," +
                    "  username VARCHAR(16) NOT NULL," +
                    "  is_hidden BOOLEAN DEFAULT FALSE," +
                    "  is_vanished BOOLEAN DEFAULT FALSE," +
                    "  command_spy BOOLEAN DEFAULT FALSE," +
                    "  last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "  INDEX idx_uuid (uuid)," +
                    "  INDEX idx_username (username)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        } else {
            playersTable = "CREATE TABLE IF NOT EXISTS neohide_players (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  uuid VARCHAR(36) NOT NULL UNIQUE," +
                    "  username VARCHAR(16) NOT NULL," +
                    "  is_hidden BOOLEAN DEFAULT 0," +
                    "  is_vanished BOOLEAN DEFAULT 0," +
                    "  command_spy BOOLEAN DEFAULT 0," +
                    "  last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
        }

        // Таблица логов
        String logsTable = "CREATE TABLE IF NOT EXISTS neohide_logs (" +
                "  id " + (dbType == DatabaseType.MYSQL ? "INT AUTO_INCREMENT" : "INTEGER") + " PRIMARY KEY," +
                "  player_uuid VARCHAR(36)," +
                "  player_name VARCHAR(16)," +
                "  action_type VARCHAR(32) NOT NULL," +
                "  command VARCHAR(255)," +
                "  details TEXT," +
                "  server_name VARCHAR(64)," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_action_type (action_type)," +
                "  INDEX idx_created_at (created_at)" +
                ")";

        // Таблица скрытых команд
        String hiddenCommandsTable = "CREATE TABLE IF NOT EXISTS neohide_hidden_commands (" +
                "  id " + (dbType == DatabaseType.MYSQL ? "INT AUTO_INCREMENT" : "INTEGER") + " PRIMARY KEY," +
                "  command VARCHAR(64) NOT NULL UNIQUE," +
                "  hidden_by VARCHAR(36)," +
                "  hidden_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_command (command)" +
                ")";

        // Таблица нарушений
        String violationsTable = "CREATE TABLE IF NOT EXISTS neohide_violations (" +
                "  id " + (dbType == DatabaseType.MYSQL ? "INT AUTO_INCREMENT" : "INTEGER") + " PRIMARY KEY," +
                "  player_uuid VARCHAR(36)," +
                "  player_name VARCHAR(16)," +
                "  violation_type VARCHAR(32) NOT NULL," +
                "  severity INT DEFAULT 1," +
                "  details TEXT," +
                "  resolved BOOLEAN DEFAULT FALSE," +
                "  resolved_by VARCHAR(36)," +
                "  resolved_at TIMESTAMP," +
                "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_violation_type (violation_type)," +
                "  INDEX idx_resolved (resolved)" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(playersTable);
            stmt.execute(logsTable);
            stmt.execute(hiddenCommandsTable);
            stmt.execute(violationsTable);
        }
    }

    // === Player Data Methods ===
    public void savePlayerData(UUID uuid, String username, boolean isHidden,
                               boolean isVanished, boolean commandSpy) {
        String sql;
        if (dbType == DatabaseType.MYSQL) {
            sql = "INSERT INTO neohide_players (uuid, username, is_hidden, is_vanished, command_spy) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "username = VALUES(username), is_hidden = VALUES(is_hidden), " +
                    "is_vanished = VALUES(is_vanished), command_spy = VALUES(command_spy), " +
                    "last_seen = CURRENT_TIMESTAMP";
        } else {
            sql = "INSERT OR REPLACE INTO neohide_players (uuid, username, is_hidden, is_vanished, command_spy) " +
                    "VALUES (?, ?, ?, ?, ?)";
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.setBoolean(3, isHidden);
            stmt.setBoolean(4, isVanished);
            stmt.setBoolean(5, commandSpy);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка сохранения данных игрока", e);
        }
    }

    public Map<String, Object> getPlayerData(UUID uuid) {
        String sql = "SELECT is_hidden, is_vanished, command_spy FROM neohide_players WHERE uuid = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Map<String, Object> data = new HashMap<>();
                data.put("is_hidden", rs.getBoolean("is_hidden"));
                data.put("is_vanished", rs.getBoolean("is_vanished"));
                data.put("command_spy", rs.getBoolean("command_spy"));
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка получения данных игрока", e);
        }

        return null;
    }

    // === Logging Methods ===
    public void logAction(UUID playerUuid, String playerName, String actionType,
                          String command, String details) {
        String sql = "INSERT INTO neohide_logs (player_uuid, player_name, action_type, command, details, server_name) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid != null ? playerUuid.toString() : null);
            stmt.setString(2, playerName);
            stmt.setString(3, actionType);
            stmt.setString(4, command);
            stmt.setString(5, details);
            stmt.setString(6, plugin.getServer().getServerIcon().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка записи лога", e);
        }
    }

    public List<Map<String, Object>> getLogs(int limit, int offset) {
        List<Map<String, Object>> logs = new ArrayList<>();
        String sql = "SELECT * FROM neohide_logs ORDER BY created_at DESC LIMIT ? OFFSET ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> log = new HashMap<>();
                log.put("id", rs.getInt("id"));
                log.put("player_uuid", rs.getString("player_uuid"));
                log.put("player_name", rs.getString("player_name"));
                log.put("action_type", rs.getString("action_type"));
                log.put("command", rs.getString("command"));
                log.put("details", rs.getString("details"));
                log.put("server_name", rs.getString("server_name"));
                log.put("created_at", rs.getTimestamp("created_at"));
                logs.add(log);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка получения логов", e);
        }

        return logs;
    }

    public int getTotalLogs() {
        String sql = "SELECT COUNT(*) as total FROM neohide_logs";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка получения количества логов", e);
        }

        return 0;
    }

    // === Hidden Commands Methods ===
    public void saveHiddenCommand(String command, UUID hiddenBy) {
        String sql;
        if (dbType == DatabaseType.MYSQL) {
            sql = "INSERT IGNORE INTO neohide_hidden_commands (command, hidden_by) VALUES (?, ?)";
        } else {
            sql = "INSERT OR IGNORE INTO neohide_hidden_commands (command, hidden_by) VALUES (?, ?)";
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, command.toLowerCase());
            stmt.setString(2, hiddenBy != null ? hiddenBy.toString() : null);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка сохранения скрытой команды", e);
        }
    }

    public void removeHiddenCommand(String command) {
        String sql = "DELETE FROM neohide_hidden_commands WHERE command = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, command.toLowerCase());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка удаления скрытой команды", e);
        }
    }

    public List<String> getHiddenCommandsFromDB() {
        List<String> commands = new ArrayList<>();
        String sql = "SELECT command FROM neohide_hidden_commands";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                commands.add(rs.getString("command"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка получения скрытых команд", e);
        }

        return commands;
    }

    // === Violations Methods ===
    public void logViolation(UUID playerUuid, String playerName, String violationType,
                             int severity, String details) {
        String sql = "INSERT INTO neohide_violations (player_uuid, player_name, violation_type, severity, details) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid != null ? playerUuid.toString() : null);
            stmt.setString(2, playerName);
            stmt.setString(3, violationType);
            stmt.setInt(4, severity);
            stmt.setString(5, details);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка записи нарушения", e);
        }
    }

    public List<Map<String, Object>> getActiveViolations(int limit) {
        List<Map<String, Object>> violations = new ArrayList<>();
        String sql = "SELECT * FROM neohide_violations WHERE resolved = FALSE ORDER BY created_at DESC LIMIT ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> violation = new HashMap<>();
                violation.put("id", rs.getInt("id"));
                violation.put("player_name", rs.getString("player_name"));
                violation.put("violation_type", rs.getString("violation_type"));
                violation.put("severity", rs.getInt("severity"));
                violation.put("details", rs.getString("details"));
                violation.put("created_at", rs.getTimestamp("created_at"));
                violations.add(violation);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка получения нарушений", e);
        }

        return violations;
    }

    // === Statistics ===
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        String[] queries = {
                "SELECT COUNT(*) as total_logs FROM neohide_logs",
                "SELECT COUNT(*) as total_violations FROM neohide_violations WHERE resolved = 0",
                "SELECT COUNT(*) as hidden_commands FROM neohide_hidden_commands",
                "SELECT COUNT(DISTINCT player_uuid) as unique_players FROM neohide_logs WHERE player_uuid IS NOT NULL",
                "SELECT COUNT(*) as vanished_players FROM neohide_players WHERE is_vanished = 1",
                "SELECT COUNT(*) as hidden_players FROM neohide_players WHERE is_hidden = 1"
        };

        String[] keys = {"total_logs", "total_violations", "hidden_commands",
                "unique_players", "vanished_players", "hidden_players"};

        try (Statement stmt = connection.createStatement()) {
            for (int i = 0; i < queries.length; i++) {
                ResultSet rs = stmt.executeQuery(queries[i]);
                if (rs.next()) {
                    stats.put(keys[i], rs.getInt(1));
                }
            }

            // Получаем последние 5 логов
            List<Map<String, Object>> recentLogs = getLogs(5, 0);
            stats.put("recent_logs", recentLogs);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка получения статистики", e);
        }

        return stats;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Ошибка закрытия соединения с БД", e);
        }
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public DatabaseType getDatabaseType() {
        return dbType;
    }
}