package com.neohide.neohide.managers;

import com.neohide.neohide.NeoHide;
import com.neohide.neohide.managers.ConfigManager;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Level;

public class WebServerManager {

    private final NeoHide plugin;
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
            // Создаем простой HTTP сервер
            com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
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
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            try {
                if (method.equals("GET")) {
                    handleGet(exchange, path);
                } else if (method.equals("POST")) {
                    handlePost(exchange, path);
                } else {
                    sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        private void handleGet(com.sun.net.httpserver.HttpExchange exchange, String path) throws IOException {
            if (path.equals("/api/stats")) {
                // Статистика
                Map<String, Object> stats = new HashMap<>();
                stats.put("hidden_commands", plugin.getCommandManager().getHiddenCommands().size());
                stats.put("online_players", plugin.getServer().getOnlinePlayers().size());
                stats.put("vanished_players", plugin.getHideManager().getVanishedPlayers().size());

                sendJson(exchange, stats);

            } else if (path.equals("/api/commands")) {
                // Скрытые команды
                List<String> commands = plugin.getCommandManager().getHiddenCommands();
                sendJson(exchange, commands);

            } else if (path.equals("/api/status")) {
                // Статус плагина
                ConfigManager config = plugin.getConfigManager();
                Map<String, Object> status = new HashMap<>();
                status.put("plugin", "NeoHide");
                status.put("version", plugin.getDescription().getVersion());
                status.put("protection_enabled", config.isHideCommandsEnabled());
                status.put("web_enabled", config.isWebEnabled());

                sendJson(exchange, status);

            } else {
                sendError(exchange, 404, "Endpoint not found");
            }
        }

        private void handlePost(com.sun.net.httpserver.HttpExchange exchange, String path) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);

            if (path.equals("/api/hide")) {
                String command = params.get("command");
                if (command != null && !command.isEmpty()) {
                    plugin.getCommandManager().addHiddenCommand(command);
                    sendSuccess(exchange, "Command hidden: " + command);
                } else {
                    sendError(exchange, 400, "Command parameter required");
                }

            } else if (path.equals("/api/unhide")) {
                String command = params.get("command");
                if (command != null && !command.isEmpty()) {
                    plugin.getCommandManager().removeHiddenCommand(command);
                    sendSuccess(exchange, "Command unhidden: " + command);
                } else {
                    sendError(exchange, 400, "Command parameter required");
                }

            } else if (path.equals("/api/toggle")) {
                boolean enabled = "true".equals(params.get("enabled"));
                plugin.getConfigManager().setHideCommandsEnabled(enabled);
                sendSuccess(exchange, "Protection " + (enabled ? "enabled" : "disabled"));

            } else {
                sendError(exchange, 404, "Endpoint not found");
            }
        }

        private Map<String, String> parseQuery(String query) {
            Map<String, String> result = new HashMap<>();
            if (query == null) return result;

            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = java.net.URLDecoder.decode(pair.substring(6, idx), java.nio.charset.StandardCharsets.UTF_8);
                    String value = java.net.URLDecoder.decode(pair.substring(idx + 1), java.nio.charset.StandardCharsets.UTF_8);
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
                json = "{\"error\":\"JSON serialization error\"}";
            }

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, json.getBytes().length);

            try (PrintWriter out = new PrintWriter(exchange.getResponseBody())) {
                out.print(json);
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
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, json.getBytes().length);

            try (PrintWriter out = new PrintWriter(exchange.getResponseBody())) {
                out.print(json);
            }
        }
    }

    // ========== Static Handler ==========
    private class StaticHandler implements com.sun.net.httpserver.HttpHandler {
        @Override
        public void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/") || path.equals("/index.html")) {
                serveIndex(exchange);
            } else {
                sendError(exchange, 404, "File not found");
            }
        }

        private void serveIndex(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
            String html = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <title>NeoHide Dashboard</title>\n" +
                    "    <style>\n" +
                    "        body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }\n" +
                    "        .container { max-width: 1200px; margin: 0 auto; }\n" +
                    "        .header { background: #333; color: white; padding: 20px; border-radius: 5px; }\n" +
                    "        .stats { display: flex; gap: 20px; margin: 20px 0; }\n" +
                    "        .stat-card { flex: 1; background: white; padding: 20px; border-radius: 5px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }\n" +
                    "        .stat-card h3 { margin: 0 0 10px 0; color: #333; }\n" +
                    "        .stat-card .value { font-size: 24px; font-weight: bold; color: #4CAF50; }\n" +
                    "        .control-panel { background: white; padding: 20px; border-radius: 5px; margin: 20px 0; }\n" +
                    "        .command-input { display: flex; gap: 10px; margin: 10px 0; }\n" +
                    "        input { flex: 1; padding: 10px; border: 1px solid #ddd; border-radius: 3px; }\n" +
                    "        button { padding: 10px 20px; background: #4CAF50; color: white; border: none; border-radius: 3px; cursor: pointer; }\n" +
                    "        button:hover { background: #45a049; }\n" +
                    "        .command-list { background: white; padding: 20px; border-radius: 5px; }\n" +
                    "        .command-item { padding: 10px; border-bottom: 1px solid #eee; display: flex; justify-content: space-between; }\n" +
                    "        .auth-panel { background: #ffebee; padding: 20px; border-radius: 5px; margin: 20px 0; }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <div class='container'>\n" +
                    "        <div class='header'>\n" +
                    "            <h1>NeoHide Dashboard</h1>\n" +
                    "            <p>Управление защитой сервера</p>\n" +
                    "        </div>\n" +
                    "        \n" +
                    "        <div class='auth-panel' id='authPanel'>\n" +
                    "            <h3>Авторизация</h3>\n" +
                    "            <p>Введите токен из config.yml:</p>\n" +
                    "            <input type='password' id='authToken' placeholder='Auth token'>\n" +
                    "            <button onclick='connect()'>Подключиться</button>\n" +
                    "        </div>\n" +
                    "        \n" +
                    "        <div id='dashboard' style='display:none;'>\n" +
                    "            <div class='stats'>\n" +
                    "                <div class='stat-card'>\n" +
                    "                    <h3>Скрытые команды</h3>\n" +
                    "                    <div class='value' id='hiddenCount'>0</div>\n" +
                    "                </div>\n" +
                    "                <div class='stat-card'>\n" +
                    "                    <h3>Онлайн игроки</h3>\n" +
                    "                    <div class='value' id='onlineCount'>0</div>\n" +
                    "                </div>\n" +
                    "                <div class='stat-card'>\n" +
                    "                    <h3>Ванш игроки</h3>\n" +
                    "                    <div class='value' id='vanishedCount'>0</div>\n" +
                    "                </div>\n" +
                    "            </div>\n" +
                    "            \n" +
                    "            <div class='control-panel'>\n" +
                    "                <h3>Управление защитой</h3>\n" +
                    "                <label>\n" +
                    "                    <input type='checkbox' id='protectionToggle' onchange='toggleProtection()'>\n" +
                    "                    Защита включена\n" +
                    "                </label>\n" +
                    "                \n" +
                    "                <div class='command-input'>\n" +
                    "                    <input type='text' id='commandInput' placeholder='Введите команду для скрытия'>\n" +
                    "                    <button onclick='hideCommand()'>Скрыть команду</button>\n" +
                    "                </div>\n" +
                    "                \n" +
                    "                <button onclick='refreshData()' style='margin-right:10px;'>Обновить</button>\n" +
                    "                <button onclick='reloadPlugin()'>Перезагрузить плагин</button>\n" +
                    "            </div>\n" +
                    "            \n" +
                    "            <div class='command-list'>\n" +
                    "                <h3>Скрытые команды</h3>\n" +
                    "                <div id='commandsList'></div>\n" +
                    "            </div>\n" +
                    "        </div>\n" +
                    "    </div>\n" +
                    "    \n" +
                    "    <script>\n" +
                    "        let authToken = '';\n" +
                    "        \n" +
                    "        function connect() {\n" +
                    "            authToken = document.getElementById('authToken').value.trim();\n" +
                    "            if (!authToken) {\n" +
                    "                alert('Введите токен авторизации');\n" +
                    "                return;\n" +
                    "            }\n" +
                    "            \n" +
                    "            localStorage.setItem('neohide_token', authToken);\n" +
                    "            document.getElementById('authPanel').style.display = 'none';\n" +
                    "            document.getElementById('dashboard').style.display = 'block';\n" +
                    "            refreshData();\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function apiCall(endpoint, method = 'GET', params = {}) {\n" +
                    "            const url = '/api' + endpoint + (method === 'GET' && Object.keys(params).length > 0 \n" +
                    "                ? '?' + new URLSearchParams(params).toString() : '');\n" +
                    "            \n" +
                    "            const response = await fetch(url, {\n" +
                    "                method: method,\n" +
                    "                headers: {\n" +
                    "                    'X-Auth-Token': authToken\n" +
                    "                },\n" +
                    "                body: method === 'POST' ? new URLSearchParams(params) : null\n" +
                    "            });\n" +
                    "            \n" +
                    "            if (response.status === 401) {\n" +
                    "                alert('Неверный токен авторизации');\n" +
                    "                location.reload();\n" +
                    "                return null;\n" +
                    "            }\n" +
                    "            \n" +
                    "            return await response.json();\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function refreshData() {\n" +
                    "            try {\n" +
                    "                // Получаем статус\n" +
                    "                const status = await apiCall('/status');\n" +
                    "                if (!status) return;\n" +
                    "                \n" +
                    "                document.getElementById('protectionToggle').checked = status.protection_enabled;\n" +
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
                    "                commands.forEach(cmd => {\n" +
                    "                    const item = document.createElement('div');\n" +
                    "                    item.className = 'command-item';\n" +
                    "                    item.innerHTML = `\n" +
                    "                        <span>/${cmd}</span>\n" +
                    "                        <button onclick='unhideCommand(\"${cmd}\")'>Показать</button>\n" +
                    "                    `;\n" +
                    "                    commandsList.appendChild(item);\n" +
                    "                });\n" +
                    "                \n" +
                    "            } catch (error) {\n" +
                    "                console.error('Ошибка:', error);\n" +
                    "                alert('Ошибка загрузки данных');\n" +
                    "            }\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function hideCommand() {\n" +
                    "            const command = document.getElementById('commandInput').value.trim();\n" +
                    "            if (!command) {\n" +
                    "                alert('Введите команду');\n" +
                    "                return;\n" +
                    "            }\n" +
                    "            \n" +
                    "            const result = await apiCall('/hide', 'POST', { command });\n" +
                    "            if (result && result.success) {\n" +
                    "                document.getElementById('commandInput').value = '';\n" +
                    "                refreshData();\n" +
                    "            }\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function unhideCommand(command) {\n" +
                    "            if (!confirm(`Показать команду /${command}?`)) return;\n" +
                    "            \n" +
                    "            const result = await apiCall('/unhide', 'POST', { command });\n" +
                    "            if (result && result.success) {\n" +
                    "                refreshData();\n" +
                    "            }\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function toggleProtection() {\n" +
                    "            const enabled = document.getElementById('protectionToggle').checked;\n" +
                    "            await apiCall('/toggle', 'POST', { enabled: enabled });\n" +
                    "        }\n" +
                    "        \n" +
                    "        async function reloadPlugin() {\n" +
                    "            if (!confirm('Перезагрузить конфигурацию плагина?')) return;\n" +
                    "            alert('Для перезагрузки используйте команду /neohide reload в игре');\n" +
                    "        }\n" +
                    "        \n" +
                    "        // Проверяем сохраненный токен\n" +
                    "        window.onload = function() {\n" +
                    "            const savedToken = localStorage.getItem('neohide_token');\n" +
                    "            if (savedToken) {\n" +
                    "                document.getElementById('authToken').value = savedToken;\n" +
                    "                connect();\n" +
                    "            }\n" +
                    "        };\n" +
                    "    </script>\n" +
                    "</body>\n" +
                    "</html>";

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, html.getBytes().length);

            try (PrintWriter out = new PrintWriter(exchange.getResponseBody())) {
                out.print(html);
            }
        }

        private void sendError(com.sun.net.httpserver.HttpExchange exchange, int code, String message) throws IOException {
            String response = "<html><body><h1>" + code + " " + message + "</h1></body></html>";
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(code, response.getBytes().length);

            try (PrintWriter out = new PrintWriter(exchange.getResponseBody())) {
                out.print(response);
            }
        }
    }
}