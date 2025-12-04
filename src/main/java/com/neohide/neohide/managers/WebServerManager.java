package com.neohide.neohide.managers;

import com.neohide.neohide.NeoHide;
import com.neohide.neohide.managers.ConfigManager;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class WebServerManager {

    private final NeoHide plugin;
    private com.sun.net.httpserver.HttpServer server;
    private boolean running = false;

    public WebServerManager(NeoHide plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ConfigManager config = plugin.getConfigManager();

        if (!config.isWebEnabled()) {
            plugin.getLogger().info("Веб-интерфейс отключен в конфигурации");
            return;
        }

        int port = config.getWebPort();

        try {
            // Создаем HTTP сервер
            server = com.sun.net.httpserver.HttpServer.create(
                    new java.net.InetSocketAddress(port), 0
            );

            // API эндпоинты
            server.createContext("/api", new ApiHandler());

            // Статические файлы
            server.createContext("/", new StaticHandler());

            server.setExecutor(null);
            server.start();

            running = true;
            plugin.getLogger().info("Веб-интерфейс запущен на порту " + port);
            plugin.getLogger().info("Доступ: http://localhost:" + port);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось запустить веб-интерфейс: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        running = false;
        plugin.getLogger().info("Веб-интерфейс остановлен");
    }

    public boolean isRunning() {
        return running;
    }

    // ========== API Handler ==========
    private class ApiHandler implements com.sun.net.httpserver.HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            ConfigManager config = plugin.getConfigManager();
            String authToken = config.getWebAuthToken();

            // Проверка авторизации
            String token = exchange.getRequestHeaders().getFirst("X-Auth-Token");
            if (token == null || !token.equals(authToken)) {
                sendError(exchange, 401, "Неавторизованный доступ");
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            try {
                if ("GET".equals(method)) {
                    handleGet(exchange, path);
                } else if ("POST".equals(method)) {
                    handlePost(exchange, path);
                } else {
                    sendError(exchange, 405, "Метод не поддерживается");
                }
            } catch (Exception e) {
                sendError(exchange, 500, "Внутренняя ошибка сервера: " + e.getMessage());
                plugin.getLogger().log(Level.WARNING, "Ошибка API: " + e.getMessage(), e);
            }
        }

        private void handleGet(com.sun.net.httpserver.HttpExchange exchange, String path) throws IOException {
            if ("/api/stats".equals(path)) {
                // Статистика
                Map<String, Object> stats = new HashMap<>();
                stats.put("hidden_commands", plugin.getCommandManager().getHiddenCommands().size());
                stats.put("online_players", plugin.getServer().getOnlinePlayers().size());
                stats.put("vanished_players", plugin.getHideManager().getVanishedPlayers().size());

                sendJson(exchange, stats);

            } else if ("/api/commands".equals(path)) {
                // Скрытые команды
                List<String> commands = plugin.getCommandManager().getHiddenCommands();
                sendJson(exchange, commands);

            } else if ("/api/status".equals(path)) {
                // Статус плагина
                ConfigManager config = plugin.getConfigManager();
                Map<String, Object> status = new HashMap<>();
                status.put("plugin", "NeoHide");
                status.put("version", plugin.getDescription().getVersion());
                status.put("protection_enabled", config.isHideCommandsEnabled());
                status.put("web_enabled", config.isWebEnabled());
                status.put("database_connected", plugin.getDatabaseManager().isConnected());

                sendJson(exchange, status);

            } else {
                sendError(exchange, 404, "Эндпоинт не найден");
            }
        }

        private void handlePost(com.sun.net.httpserver.HttpExchange exchange, String path) throws IOException {
            // Читаем тело запроса
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseQuery(body);

            if ("/api/hide".equals(path)) {
                String command = params.get("command");
                if (command != null && !command.isEmpty()) {
                    plugin.getCommandManager().addHiddenCommand(command);
                    sendSuccess(exchange, "Команда скрыта: " + command);
                } else {
                    sendError(exchange, 400, "Требуется параметр command");
                }

            } else if ("/api/unhide".equals(path)) {
                String command = params.get("command");
                if (command != null && !command.isEmpty()) {
                    plugin.getCommandManager().removeHiddenCommand(command);
                    sendSuccess(exchange, "Команда показана: " + command);
                } else {
                    sendError(exchange, 400, "Требуется параметр command");
                }

            } else if ("/api/toggle".equals(path)) {
                String enabledStr = params.get("enabled");
                if (enabledStr != null) {
                    boolean enabled = "true".equalsIgnoreCase(enabledStr);
                    plugin.getConfigManager().setHideCommandsEnabled(enabled);
                    sendSuccess(exchange, "Защита " + (enabled ? "включена" : "выключена"));
                } else {
                    sendError(exchange, 400, "Требуется параметр enabled");
                }

            } else {
                sendError(exchange, 404, "Эндпоинт не найден");
            }
        }

        private Map<String, String> parseQuery(String query) {
            Map<String, String> result = new HashMap<>();
            if (query == null || query.isEmpty()) return result;

            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    result.put(key, value);
                }
            }
            return result;
        }

        private void sendJson(com.sun.net.httpserver.HttpExchange exchange, Object data) throws IOException {
            String json = "{}";
            try {
                // Простой JSON сериализатор
                if (data instanceof Map) {
                    json = mapToJson((Map<?, ?>) data);
                } else if (data instanceof List) {
                    json = listToJson((List<?>) data);
                }
            } catch (Exception e) {
                json = "{\"error\":\"Ошибка сериализации JSON\"}";
            }

            byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        private String mapToJson(Map<?, ?> map) {
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    json.append("\"").append(escapeJson(value.toString())).append("\"");
                } else if (value instanceof Number || value instanceof Boolean) {
                    json.append(value);
                } else {
                    json.append("\"").append(escapeJson(value.toString())).append("\"");
                }
                first = false;
            }
            json.append("}");
            return json.toString();
        }

        private String listToJson(List<?> list) {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) json.append(",");
                if (item instanceof String) {
                    json.append("\"").append(escapeJson(item.toString())).append("\"");
                } else {
                    json.append(item);
                }
                first = false;
            }
            json.append("]");
            return json.toString();
        }

        private String escapeJson(String str) {
            if (str == null) return "";
            return str.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }

        private void sendSuccess(com.sun.net.httpserver.HttpExchange exchange, String message) throws IOException {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", message);
            sendJson(exchange, response);
        }

        private void sendError(com.sun.net.httpserver.HttpExchange exchange, int code, String message) throws IOException {
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("code", code);
            error.put("message", message);

            String json = mapToJson(error);
            byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(code, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    // ========== Static Handler ==========
    private class StaticHandler implements com.sun.net.httpserver.HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if ("/".equals(path) || "/index.html".equals(path)) {
                serveIndex(exchange);
            } else if ("/style.css".equals(path)) {
                serveCss(exchange);
            } else {
                sendError(exchange, 404, "Файл не найден");
            }
        }

        private void serveIndex(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String html = "<!DOCTYPE html>\n" +
                    "<html lang='ru'>\n" +
                    "<head>\n" +
                    "    <meta charset='UTF-8'>\n" +
                    "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
                    "    <title>NeoHide - Панель управления</title>\n" +
                    "    <style>\n" +
                    "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; color: #333; }\n" +
                    "        .container { max-width: 1200px; margin: 0 auto; background: rgba(255, 255, 255, 0.95); border-radius: 15px; padding: 30px; box-shadow: 0 10px 30px rgba(0,0,0,0.2); }\n" +
                    "        .header { text-align: center; margin-bottom: 30px; padding-bottom: 20px; border-bottom: 2px solid #667eea; }\n" +
                    "        .header h1 { color: #667eea; margin: 0; font-size: 2.5em; }\n" +
                    "        .header p { color: #666; font-size: 1.1em; }\n" +
                    "        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr)); gap: 20px; margin-bottom: 30px; }\n" +
                    "        .stat-card { background: white; padding: 25px; border-radius: 10px; box-shadow: 0 5px 15px rgba(0,0,0,0.1); transition: transform 0.3s, box-shadow 0.3s; }\n" +
                    "        .stat-card:hover { transform: translateY(-5px); box-shadow: 0 10px 25px rgba(0,0,0,0.15); }\n" +
                    "        .stat-card h3 { margin-top: 0; color: #555; font-size: 1.2em; }\n" +
                    "        .stat-value { font-size: 2.5em; font-weight: bold; color: #667eea; margin: 10px 0; }\n" +
                    "        .control-panel { background: white; padding: 30px; border-radius: 10px; margin-bottom: 30px; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }\n" +
                    "        .control-panel h2 { margin-top: 0; color: #667eea; }\n" +
                    "        .toggle-container { display: flex; align-items: center; margin-bottom: 20px; }\n" +
                    "        .toggle-switch { position: relative; display: inline-block; width: 60px; height: 34px; margin-right: 15px; }\n" +
                    "        .toggle-switch input { opacity: 0; width: 0; height: 0; }\n" +
                    "        .toggle-slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #ccc; transition: .4s; border-radius: 34px; }\n" +
                    "        .toggle-slider:before { position: absolute; content: ''; height: 26px; width: 26px; left: 4px; bottom: 4px; background-color: white; transition: .4s; border-radius: 50%; }\n" +
                    "        input:checked + .toggle-slider { background-color: #667eea; }\n" +
                    "        input:checked + .toggle-slider:before { transform: translateX(26px); }\n" +
                    "        .command-section { background: white; padding: 30px; border-radius: 10px; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }\n" +
                    "        .command-input { display: flex; gap: 10px; margin-bottom: 20px; }\n" +
                    "        input[type='text'], input[type='password'] { flex: 1; padding: 12px 15px; border: 2px solid #ddd; border-radius: 8px; font-size: 16px; transition: border-color 0.3s; }\n" +
                    "        input:focus { outline: none; border-color: #667eea; }\n" +
                    "        button { padding: 12px 25px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border: none; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; transition: transform 0.2s, box-shadow 0.2s; }\n" +
                    "        button:hover { transform: translateY(-2px); box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4); }\n" +
                    "        button:active { transform: translateY(0); }\n" +
                    "        .button-group { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 20px; }\n" +
                    "        .command-list { margin-top: 20px; max-height: 300px; overflow-y: auto; }\n" +
                    "        .command-item { display: flex; justify-content: space-between; align-items: center; padding: 15px; border-bottom: 1px solid #eee; background: #f9f9f9; border-radius: 8px; margin-bottom: 10px; }\n" +
                    "        .command-item:last-child { border-bottom: none; }\n" +
                    "        .auth-panel { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); padding: 30px; border-radius: 10px; margin-bottom: 30px; box-shadow: 0 5px 15px rgba(0,0,0,0.1); color: white; }\n" +
                    "        .auth-panel h3 { margin-top: 0; }\n" +
                    "        .status-badge { display: inline-block; padding: 5px 15px; border-radius: 20px; font-size: 0.9em; font-weight: bold; margin-left: 10px; }\n" +
                    "        .status-connected { background: #4CAF50; color: white; }\n" +
                    "        .status-disconnected { background: #f44336; color: white; }\n" +
                    "        .log-entry { background: #f5f5f5; padding: 10px 15px; border-radius: 5px; margin-bottom: 5px; font-family: monospace; font-size: 0.9em; }\n" +
                    "        .error-message { background: #ffebee; color: #c62828; padding: 15px; border-radius: 8px; margin: 15px 0; }\n" +
                    "        .success-message { background: #e8f5e9; color: #2e7d32; padding: 15px; border-radius: 8px; margin: 15px 0; }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <div class='container'>\n" +
                    "        <div class='header'>\n" +
                    "            <h1>NeoHide Dashboard</h1>\n" +
                    "            <p>Панель управления защитой Minecraft сервера</p>\n" +
                    "            <div id='connectionStatus'>\n" +
                    "                <span id='statusText'>Не подключено</span>\n" +
                    "                <span id='statusBadge' class='status-badge status-disconnected'>Отключено</span>\n" +
                    "            </div>\n" +
                    "        </div>\n" +
                    "        \n" +
                    "        <div class='auth-panel' id='authPanel'>\n" +
                    "            <h3>🔐 Авторизация</h3>\n" +
                    "            <p>Для доступа к панели управления введите токен из config.yml</p>\n" +
                    "            <div class='command-input'>\n" +
                    "                <input type='password' id='authToken' placeholder='Введите секретный токен'>\n" +
                    "                <button onclick='connect()'>Подключиться</button>\n" +
                    "            </div>\n" +
                    "            <p><small>Токен можно найти в файле plugins/NeoHide/config.yml в разделе web.auth-token</small></p>\n" +
                    "        </div>\n" +
                    "        \n" +
                    "        <div id='dashboard' style='display:none;'>\n" +
                    "            <div class='stats-grid'>\n" +
                    "                <div class='stat-card'>\n" +
                    "                    <h3>📋 Скрытые команды</h3>\n" +
                    "                    <div class='stat-value' id='hiddenCount'>0</div>\n" +
                    "                    <p>Команд скрыто от игроков</p>\n" +
                    "                </div>\n" +
                    "                <div class='stat-card'>\n" +
                    "                    <h3>👥 Онлайн игроки</h3>\n" +
                    "                    <div class='stat-value' id='onlineCount'>0</div>\n" +
                    "                    <p>Игроков на сервере</p>\n" +
                    "                </div>\n" +
                    "                <div class='stat-card'>\n" +
                    "                    <h3>👻 Игроки в ванше</h3>\n" +
                    "                    <div class='stat-value' id='vanishedCount'>0</div>\n" +
                    "                    <p>Скрытых администраторов</p>\n" +
                    "                </div>\n" +
                    "                <div class='stat-card'>\n" +
                    "                    <h3>🛡️ Статус защиты</h3>\n" +
                    "                    <div class='stat-value' id='protectionStatus'>Нет</div>\n" +
                    "                    <p>Система защиты активна</p>\n" +
                    "                </div>\n" +
                    "            </div>\n" +
                    "            \n" +
                    "            <div class='control-panel'>\n" +
                    "                <h2>⚙️ Управление защитой</h2>\n" +
                    "                \n" +
                    "                <div class='toggle-container'>\n" +
                    "                    <label class='toggle-switch'>\n" +
                    "                        <input type='checkbox' id='protectionToggle' onchange='toggleProtection()'>\n" +
                    "                        <span class='toggle-slider'></span>\n" +
                    "                    </label>\n" +
                    "                    <label for='protectionToggle' style='font-size: 1.1em; font-weight: bold;'>Защита команд включена</label>\n" +
                    "                </div>\n" +
                    "                \n" +
                    "                <div class='command-section'>\n" +
                    "                    <h3>🎯 Управление командами</h3>\n" +
                    "                    <div class='command-input'>\n" +
                    "                        <input type='text' id='commandInput' placeholder='Введите команду для скрытия (например: plugin)'>\n" +
                    "                        <button onclick='hideCommand()'>Скрыть команду</button>\n" +
                    "                    </div>\n" +
                    "                    \n" +
                    "                    <div class='button-group'>\n" +
                    "                        <button onclick='refreshData()'>🔄 Обновить данные</button>\n" +
                    "                        <button onclick='showLogs()'>📜 Показать логи</button>\n" +
                    "                        <button onclick='reloadConfig()'>⚡ Перезагрузить конфиг</button>\n" +
                    "                    </div>\n" +
                    "                </div>\n" +
                    "            </div>\n" +
                    "            \n" +
                    "            <div class='command-section'>\n" +
                    "                <h3>📋 Список скрытых команд</h3>\n" +
                    "                <div class='command-list' id='commandsList'>\n" +
                    "                    <p style='text-align: center; color: #888;'>Загрузка списка команд...</p>\n" +
                    "                </div>\n" +
                    "            </div>\n" +
                    "            \n" +
                    "            <div id='logsSection' style='display:none;'>\n" +
                    "                <div class='command-section'>\n" +
                    "                    <h3>📜 Последние действия</h3>\n" +
                    "                    <div id='logsList'></div>\n" +
                    "                </div>\n" +
                    "            </div>\n" +
                    "            \n" +
                    "            <div id='messages' style='margin-top: 20px;'></div>\n" +
                    "        </div>\n" +
                    "    </div>\n" +
                    "    \n" +
                    "    <script>\n" +
                    "        let authToken = '';\n" +
                    "        let autoRefreshInterval = null;\n" +
                    "        \n" +
                    "        function connect() {\n" +
                    "            authToken = document.getElementById('authToken').value.trim();\n" +
                    "            if (!authToken) {\n" +
                    "                showMessage('Введите токен авторизации', 'error');\n" +
                    "                return;\n" +
                    "            }\n" +
                    "            \n" +
                    "            // Сохраняем токен в localStorage\n" +
                    "            localStorage.setItem('neohide_token', authToken);\n" +
                    "            \n" +
                    "            // Скрываем панель авторизации, показываем dashboard\n" +
                    "            document.getElementById('authPanel').style.display = 'none';\n" +
                    "            document.getElementById('dashboard').style.display = 'block';\n" +
                    "            \n" +
                    "            // Обновляем статус\n" +
                    "            updateStatus('Подключение...', 'disconnected');\n" +
                    "            \n" +
                    "            // Загружаем данные\n" +
                    "            refreshData();\n" +
                    "            \n" +
                    "            // Запускаем автообновление каждые 10 секунд\n" +
                    "            if (autoRefreshInterval) {\n" +
                    "                clearInterval(autoRefreshInterval);\n" +
                    "            }\n" +
                    "            autoRefreshInterval = setInterval(refreshData, 10000);\n" +
                    "        }\n" +
                    "        \n" +
                    "        function updateStatus(text, type) {\n" +
                    "            const statusText = document.getElementById('statusText');\n" +
                    "            const statusBadge = document.getElementById('statusBadge');\n" +
                    "            \n" +
                    "            statusText.textContent = text;\n" +
                    "            statusBadge.textContent = type === 'connected' ? 'Подключено' : 'Отключено';\n" +
                    "            statusBadge.className = 'status-badge ' + (type === 'connected' ? 'status-connected' : 'status-disconnected');\n" +
                    "        }\n" +
                    "        \n" +
                    "        function showMessage(message, type) {\n" +
                    "            const messagesDiv = document.getElementById('messages');\n" +
                    "            const messageDiv = document.createElement('div');\n" +
                    "            messageDiv.className = type === 'error' ? 'error-message' : 'success-message';\n" +
                    "            messageDiv.textContent = message;\n" +
                    "            \n" +
                    "            messagesDiv.appendChild(messageDiv);\n" +
                    "            \n" +
                    "            // Автоматически удаляем сообщение через 5 секунд\n" +
                    "            setTimeout(() => {\n" +
                    "                if (messageDiv.parentNode) {\n" +
                    "                    messageDiv.remove();\n" +
                    "                }\n" +
                    "            }, 5000);\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function apiCall(endpoint, method = 'GET', params = {}) {\n" +
                    "            const url = '/api' + endpoint;\n" +
                    "            let options = {\n" +
                    "                method: method,\n" +
                    "                headers: {\n" +
                    "                    'X-Auth-Token': authToken,\n" +
                    "                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'\n" +
                    "                }\n" +
                    "            };\n" +
                    "            \n" +
                    "            if (method === 'POST' && Object.keys(params).length > 0) {\n" +
                    "                const formData = new URLSearchParams();\n" +
                    "                for (const [key, value] of Object.entries(params)) {\n" +
                    "                    formData.append(key, value);\n" +
                    "                }\n" +
                    "                options.body = formData;\n" +
                    "            } else if (method === 'GET' && Object.keys(params).length > 0) {\n" +
                    "                const query = new URLSearchParams(params).toString();\n" +
                    "                options.url = url + '?' + query;\n" +
                    "            }\n" +
                    "            \n" +
                    "            try {\n" +
                    "                const response = await fetch(url, options);\n" +
                    "                \n" +
                    "                if (response.status === 401) {\n" +
                    "                    updateStatus('Неверный токен', 'disconnected');\n" +
                    "                    showMessage('Неверный токен авторизации. Проверьте config.yml', 'error');\n" +
                    "                    return null;\n" +
                    "                }\n" +
                    "                \n" +
                    "                if (!response.ok) {\n" +
                    "                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);\n" +
                    "                }\n" +
                    "                \n" +
                    "                const data = await response.json();\n" +
                    "                updateStatus('Подключено', 'connected');\n" +
                    "                return data;\n" +
                    "                \n" +
                    "            } catch (error) {\n" +
                    "                console.error('Ошибка API:', error);\n" +
                    "                updateStatus('Ошибка подключения', 'disconnected');\n" +
                    "                showMessage('Ошибка подключения к серверу: ' + error.message, 'error');\n" +
                    "                return null;\n" +
                    "            }\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function refreshData() {\n" +
                    "            try {\n" +
                    "                // Получаем статус\n" +
                    "                const status = await apiCall('/status');\n" +
                    "                if (!status) return;\n" +
                    "                \n" +
                    "                // Обновляем переключатель защиты\n" +
                    "                document.getElementById('protectionToggle').checked = status.protection_enabled;\n" +
                    "                document.getElementById('protectionStatus').textContent = status.protection_enabled ? 'Активна' : 'Отключена';\n" +
                    "                document.getElementById('protectionStatus').style.color = status.protection_enabled ? '#4CAF50' : '#f44336';\n" +
                    "                \n" +
                    "                // Получаем статистику\n" +
                    "                const stats = await apiCall('/stats');\n" +
                    "                if (!stats) return;\n" +
                    "                \n" +
                    "                document.getElementById('hiddenCount').textContent = stats.hidden_commands || 0;\n" +
                    "                document.getElementById('onlineCount').textContent = stats.online_players || 0;\n" +
                    "                document.getElementById('vanishedCount').textContent = stats.vanished_players || 0;\n" +
                    "                \n" +
                    "                // Получаем команды\n" +
                    "                const commands = await apiCall('/commands');\n" +
                    "                if (!commands) return;\n" +
                    "                \n" +
                    "                const commandsList = document.getElementById('commandsList');\n" +
                    "                commandsList.innerHTML = '';\n" +
                    "                \n" +
                    "                if (commands.length === 0) {\n" +
                    "                    commandsList.innerHTML = '<p style=\"text-align: center; color: #888;\">Нет скрытых команд</p>';\n" +
                    "                } else {\n" +
                    "                    commands.forEach(cmd => {\n" +
                    "                        const item = document.createElement('div');\n" +
                    "                        item.className = 'command-item';\n" +
                    "                        item.innerHTML = `\n" +
                    "                            <div>\n" +
                    "                                <strong style=\"color: #667eea;\">/${cmd}</strong>\n" +
                    "                            </div>\n" +
                    "                            <button onclick=\"unhideCommand('${cmd}')\">Показать команду</button>\n" +
                    "                        `;\n" +
                    "                        commandsList.appendChild(item);\n" +
                    "                    });\n" +
                    "                }\n" +
                    "                \n" +
                    "                showMessage('Данные успешно обновлены', 'success');\n" +
                    "                \n" +
                    "            } catch (error) {\n" +
                    "                console.error('Ошибка обновления данных:', error);\n" +
                    "                showMessage('Ошибка при обновлении данных', 'error');\n" +
                    "            }\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function hideCommand() {\n" +
                    "            const command = document.getElementById('commandInput').value.trim();\n" +
                    "            if (!command) {\n" +
                    "                showMessage('Введите команду для скрытия', 'error');\n" +
                    "                return;\n" +
                    "            }\n" +
                    "            \n" +
                    "            // Убираем слеш если есть\n" +
                    "            const cleanCommand = command.replace(/^\\//, '');\n" +
                    "            \n" +
                    "            const result = await apiCall('/hide', 'POST', { command: cleanCommand });\n" +
                    "            if (result && result.success) {\n" +
                    "                document.getElementById('commandInput').value = '';\n" +
                    "                showMessage(result.message, 'success');\n" +
                    "                refreshData();\n" +
                    "            }\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function unhideCommand(command) {\n" +
                    "            if (!confirm(`Вы действительно хотите показать команду /${command}?`)) return;\n" +
                    "            \n" +
                    "            const result = await apiCall('/unhide', 'POST', { command: command });\n" +
                    "            if (result && result.success) {\n" +
                    "                showMessage(result.message, 'success');\n" +
                    "                refreshData();\n" +
                    "            }\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function toggleProtection() {\n" +
                    "            const enabled = document.getElementById('protectionToggle').checked;\n" +
                    "            const result = await apiCall('/toggle', 'POST', { enabled: enabled });\n" +
                    "            if (result && result.success) {\n" +
                    "                showMessage(result.message, 'success');\n" +
                    "                refreshData();\n" +
                    "            } else {\n" +
                    "                // Откатываем переключатель если ошибка\n" +
                    "                document.getElementById('protectionToggle').checked = !enabled;\n" +
                    "            }\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function showLogs() {\n" +
                    "            const logsSection = document.getElementById('logsSection');\n" +
                    "            const logsList = document.getElementById('logsList');\n" +
                    "            \n" +
                    "            if (logsSection.style.display === 'none') {\n" +
                    "                logsList.innerHTML = '<p style=\"text-align: center; color: #888;\">Загрузка логов...</p>';\n" +
                    "                logsSection.style.display = 'block';\n" +
                    "                \n" +
                    "                // Здесь можно добавить загрузку логов когда будет API\n" +
                    "                setTimeout(() => {\n" +
                    "                    logsList.innerHTML = '<p style=\"text-align: center; color: #888;\">Функция логов в разработке</p>';\n" +
                    "                }, 1000);\n" +
                    "            } else {\n" +
                    "                logsSection.style.display = 'none';\n" +
                    "            }\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function reloadConfig() {\n" +
                    "            if (!confirm('Перезагрузить конфигурацию плагина?\\nИспользуйте команду /neohide reload в игре')) return;\n" +
                    "            showMessage('Для перезагрузки используйте /neohide reload в игре', 'error');\n" +
                    "        }\n" +
                    "        \n" +
                    "        function disconnect() {\n" +
                    "            if (autoRefreshInterval) {\n" +
                    "                clearInterval(autoRefreshInterval);\n" +
                    "                autoRefreshInterval = null;\n" +
                    "            }\n" +
                    "            \n" +
                    "            localStorage.removeItem('neohide_token');\n" +
                    "            document.getElementById('dashboard').style.display = 'none';\n" +
                    "            document.getElementById('authPanel').style.display = 'block';\n" +
                    "            document.getElementById('authToken').value = '';\n" +
                    "            updateStatus('Не подключено', 'disconnected');\n" +
                    "        }\n" +
                    "        \n" +
                    "        // Проверяем сохраненный токен при загрузке\n" +
                    "        window.onload = function() {\n" +
                    "            const savedToken = localStorage.getItem('neohide_token');\n" +
                    "            if (savedToken) {\n" +
                    "                document.getElementById('authToken').value = savedToken;\n" +
                    "                connect();\n" +
                    "            }\n" +
                    "            \n" +
                    "            // Добавляем кнопку отключения\n" +
                    "            const header = document.querySelector('.header');\n" +
                    "            const disconnectBtn = document.createElement('button');\n" +
                    "            disconnectBtn.textContent = 'Выйти';\n" +
                    "            disconnectBtn.style.marginLeft = '20px';\n" +
                    "            disconnectBtn.style.background = 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)';\n" +
                    "            disconnectBtn.onclick = disconnect;\n" +
                    "            header.appendChild(disconnectBtn);\n" +
                    "        };\n" +
                    "    </script>\n" +
                    "</body>\n" +
                    "</html>";

            byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        private void serveCss(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String css = "body { font-family: Arial, sans-serif; }";
            byte[] responseBytes = css.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        private void sendError(com.sun.net.httpserver.HttpExchange exchange, int code, String message) throws IOException {
            String response = "<html><head><meta charset='UTF-8'></head><body><h1>" + code + " " + message + "</h1></body></html>";
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(code, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}