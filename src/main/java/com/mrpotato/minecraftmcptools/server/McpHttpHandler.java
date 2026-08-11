package com.mrpotato.minecraftmcptools.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import net.minecraft.server.level.ServerPlayer;
import com.mrpotato.minecraftmcptools.protocol.*;
import com.mrpotato.minecraftmcptools.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class McpHttpHandler implements HttpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftMCP/Http");
    private final McpServer server;

    public McpHttpHandler(McpServer server) {
        this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();
        String method = exchange.getRequestMethod();

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        Map<String, String> queryParams = parseQueryParams(uri.getQuery());
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        String tokenQuery = queryParams.get("token");

        try {

            if ("/sse".equals(path)) {
                handleSse(exchange, queryParams, authHeader, tokenQuery);
                return;
            }

            if ("/message".equals(path) && "POST".equalsIgnoreCase(method)) {
                handleSseMessage(exchange, queryParams, authHeader, tokenQuery);
                return;
            }

            if (("/mcp".equals(path) || "/".equals(path)) && "POST".equalsIgnoreCase(method)) {
                handleMcpPost(exchange, authHeader, tokenQuery);
                return;
            }

            if ("/api/status".equals(path) || "/status".equals(path)) {
                handleApiStatus(exchange);
                return;
            }

            if ("/api/tools".equals(path)) {
                handleApiTools(exchange);
                return;
            }

            if ("/api/resources".equals(path)) {
                handleApiResources(exchange);
                return;
            }

            if ("/api/prompts".equals(path)) {
                handleApiPrompts(exchange);
                return;
            }

            if ("/".equals(path) && "GET".equalsIgnoreCase(method)) {
                handleApiStatus(exchange);
                return;
            }

            sendJsonResponse(exchange, 404, McpError.invalidRequest("Not Found: " + path).toJson());

        } catch (Exception e) {
            LOGGER.error("Error handling HTTP request to {}", path, e);
            sendJsonResponse(exchange, 500, McpError.internalError(e.getMessage()).toJson());
        }
    }

    private void handleSse(HttpExchange exchange, Map<String, String> queryParams, String authHeader, String tokenQuery) throws IOException {
        if (!server.checkAuth(authHeader, tokenQuery)) {
            sendJsonResponse(exchange, 401, McpError.unauthorized("Authentication required").toJson());
            return;
        }

        String sessionId = queryParams.getOrDefault("sessionId", java.util.UUID.randomUUID().toString());
        McpSession session = server.getOrCreateSession(sessionId);

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        OutputStream out = exchange.getResponseBody();
        session.attachSse(out);

        String endpointUrl = "/message?sessionId=" + session.getId();
        session.sendSseEvent("endpoint", endpointUrl);

        LOGGER.info("SSE client connected: session {}", session.getId());
    }

    private void handleSseMessage(HttpExchange exchange, Map<String, String> queryParams, String authHeader, String tokenQuery) throws IOException {
        if (!server.checkAuth(authHeader, tokenQuery)) {
            sendJsonResponse(exchange, 401, McpError.unauthorized("Authentication required").toJson());
            return;
        }

        String sessionId = queryParams.get("sessionId");
        McpSession session = server.getOrCreateSession(sessionId);

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        McpMessage msg = McpMessage.fromJson(body);

        if (msg instanceof McpRequest req) {
            server.handleRequest(req, session).thenAccept(resp -> {

                session.sendSseEvent("message", JsonUtils.COMPACT_GSON.toJson(resp.toJson()));
            });

            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        } else if (msg instanceof McpNotification notif) {
            LOGGER.debug("Received MCP notification: {}", notif.getMethod());
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        } else {
            sendJsonResponse(exchange, 400, McpError.invalidRequest("Expected valid JSON-RPC request").toJson());
        }
    }

    private void handleMcpPost(HttpExchange exchange, String authHeader, String tokenQuery) throws IOException {
        if (!server.checkAuth(authHeader, tokenQuery)) {
            sendJsonResponse(exchange, 401, McpError.unauthorized("Authentication required").toJson());
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        McpMessage msg = McpMessage.fromJson(body);

        if (msg instanceof McpRequest req) {
            McpSession session = server.getOrCreateSession("http-direct");
            server.handleRequest(req, session).thenAccept(resp -> {
                try {
                    sendJsonResponse(exchange, 200, resp.toJson());
                } catch (IOException e) {
                    LOGGER.error("Error sending MCP response", e);
                }
            });
        } else if (msg instanceof McpNotification notif) {
            LOGGER.debug("Received MCP notification: {}", notif.getMethod());
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        } else {
            sendJsonResponse(exchange, 400, McpError.invalidRequest("Invalid JSON-RPC request body").toJson());
        }
    }

    private void handleApiStatus(HttpExchange exchange) throws IOException {
        JsonObject status = new JsonObject();
        status.addProperty("status", server.isRunning() ? "online" : "offline");
        status.addProperty("version", McpServer.SERVER_VERSION);
        status.addProperty("protocol_version", McpServer.PROTOCOL_VERSION);
        status.addProperty("port", server.getConfig().getPort());
        status.addProperty("tools_count", server.getToolRegistry().getAllTools().size());
        status.addProperty("resources_count", server.getResourceRegistry().getAllResources().size());
        status.addProperty("prompts_count", server.getPromptRegistry().getAllPrompts().size());
        status.addProperty("active_sessions", server.getSessions().size());

        MinecraftContext ctx = server.getContext();
        if (ctx != null && ctx.isAvailable()) {
            JsonObject mc = new JsonObject();
            mc.addProperty("minecraft_version", ctx.getServer().getServerVersion());
            mc.addProperty("player_count", ctx.getAllPlayers().size());
            JsonArray playersArr = new JsonArray();
            for (ServerPlayer p : ctx.getAllPlayers()) {
                playersArr.add(p.getName().getString());
            }
            mc.add("players", playersArr);
            status.add("minecraft", mc);
        }

        sendJsonResponse(exchange, 200, status);
    }

    private void handleApiTools(HttpExchange exchange) throws IOException {
        JsonObject res = new JsonObject();
        res.add("tools", server.getToolRegistry().getToolsListJson());
        sendJsonResponse(exchange, 200, res);
    }

    private void handleApiResources(HttpExchange exchange) throws IOException {
        JsonObject res = new JsonObject();
        res.add("resources", server.getResourceRegistry().getResourcesListJson());
        sendJsonResponse(exchange, 200, res);
    }

    private void handleApiPrompts(HttpExchange exchange) throws IOException {
        JsonObject res = new JsonObject();
        res.add("prompts", server.getPromptRegistry().getPromptsListJson());
        sendJsonResponse(exchange, 200, res);
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, JsonObject json) throws IOException {
        byte[] bytes = JsonUtils.GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) return map;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else {
                map.put(pair, "");
            }
        }
        return map;
    }
}
