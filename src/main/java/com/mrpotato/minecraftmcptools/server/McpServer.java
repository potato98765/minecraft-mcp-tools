package com.mrpotato.minecraftmcptools.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import com.mrpotato.minecraftmcptools.config.McpConfig;
import com.mrpotato.minecraftmcptools.config.McpConfigManager;
import com.mrpotato.minecraftmcptools.protocol.*;
import com.mrpotato.minecraftmcptools.prompts.McpPromptRegistry;
import com.mrpotato.minecraftmcptools.resources.McpResourceRegistry;
import com.mrpotato.minecraftmcptools.tools.ChatCommandTools;
import com.mrpotato.minecraftmcptools.tools.McpToolRegistry;
import com.mrpotato.minecraftmcptools.util.JsonUtils;
import com.mrpotato.minecraftmcptools.util.TextFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

public class McpServer {
    public static final String PROTOCOL_VERSION = "2024-11-05";
    public static final String SERVER_NAME = "minecraft-mcp";
    public static final String SERVER_VERSION = "1.0.0";

    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftMCP/Server");

    private final McpConfigManager configManager;
    private final McpToolRegistry toolRegistry;
    private final McpResourceRegistry resourceRegistry;
    private final McpPromptRegistry promptRegistry;
    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();

    private MinecraftContext context;
    private HttpServer httpServer;
    private ExecutorService httpExecutor;
    private volatile boolean running = false;

    public McpServer(McpConfigManager configManager) {
        this.configManager = configManager;
        this.toolRegistry = new McpToolRegistry();
        this.resourceRegistry = new McpResourceRegistry();
        this.promptRegistry = new McpPromptRegistry();
    }

    public void setContext(MinecraftContext context) {
        this.context = context;
    }

    public MinecraftContext getContext() {
        return context;
    }

    public McpConfig getConfig() {
        return configManager.getConfig();
    }

    public McpConfigManager getConfigManager() {
        return configManager;
    }

    public McpToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public McpResourceRegistry getResourceRegistry() {
        return resourceRegistry;
    }

    public McpPromptRegistry getPromptRegistry() {
        return promptRegistry;
    }

    public boolean isRunning() {
        return running;
    }

    public Collection<McpSession> getSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public McpSession getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        return sessions.computeIfAbsent(sessionId, McpSession::new);
    }

    public void removeSession(String sessionId) {
        McpSession session = sessions.remove(sessionId);
        if (session != null) {
            session.close();
        }
    }

    public synchronized void start() {
        if (running) {
            LOGGER.info("MCP server is already running on port {}", getConfig().getPort());
            return;
        }

        int port = getConfig().getPort();
        String host = getConfig().getHost();

        try {
            httpExecutor = Executors.newVirtualThreadPerTaskExecutor();
            httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
            httpServer.setExecutor(httpExecutor);

            McpHttpHandler handler = new McpHttpHandler(this);
            httpServer.createContext("/", handler);
            httpServer.start();

            running = true;
            LOGGER.info("=================================================");
            LOGGER.info(" Minecraft MCP Server running on http://{}:{}", host, port);
            LOGGER.info(" MCP Endpoint:   http://{}:{}/mcp", host, port);
            LOGGER.info(" SSE Endpoint:   http://{}:{}/sse", host, port);
            LOGGER.info("=================================================");

            if (context != null && getConfig().isBroadcastToChat()) {
                context.broadcastMessage(TextFormatter.success("Minecraft MCP server started on port " + port));
            }
        } catch (IOException e) {
            LOGGER.error("Failed to start Minecraft MCP server on port {}", port, e);
            running = false;
        }
    }

    public synchronized void stop() {
        if (!running) return;

        try {
            for (McpSession session : sessions.values()) {
                session.close();
            }
            sessions.clear();

            if (httpServer != null) {
                httpServer.stop(1);
                httpServer = null;
            }
            if (httpExecutor != null) {
                httpExecutor.shutdown();
                httpExecutor = null;
            }

            running = false;
            LOGGER.info("Minecraft MCP Server stopped.");
            if (context != null && getConfig().isBroadcastToChat()) {
                context.broadcastMessage(TextFormatter.warning("Minecraft MCP server stopped."));
            }
        } catch (Exception e) {
            LOGGER.error("Error stopping MCP server", e);
        }
    }

    public synchronized void restart() {
        stop();
        start();
    }

    public boolean checkAuth(String authHeader, String tokenQuery) {
        if (!getConfig().isRequireAuth()) return true;

        String expectedToken = getConfig().getAuthToken();
        if (expectedToken == null || expectedToken.isBlank()) return true;

        if (tokenQuery != null && tokenQuery.equals(expectedToken)) return true;

        if (authHeader != null) {
            if (authHeader.startsWith("Bearer ")) {
                authHeader = authHeader.substring(7).trim();
            }
            return authHeader.equals(expectedToken);
        }

        return false;
    }

    public CompletableFuture<McpResponse> handleRequest(McpRequest request, McpSession session) {
        if (session != null) {
            session.updateActivity();
        }

        String method = request.getMethod();
        JsonElement id = request.getId();
        JsonObject params = request.getParams();

        LOGGER.debug("Handling MCP request: {} (id: {})", method, id);

        switch (method) {
            case "initialize" -> {
                return handleInitialize(id, params, session);
            }
            case "ping" -> {
                return CompletableFuture.completedFuture(McpResponse.success(id, new JsonObject()));
            }
            case "tools/list" -> {
                return handleToolsList(id);
            }
            case "tools/call" -> {
                return handleToolCall(id, params, session);
            }
            case "resources/list" -> {
                return handleResourcesList(id);
            }
            case "resources/read" -> {
                return handleResourceRead(id, params);
            }
            case "prompts/list" -> {
                return handlePromptsList(id);
            }
            case "prompts/get" -> {
                return handlePromptGet(id, params);
            }
            case "logging/setLevel" -> {
                return CompletableFuture.completedFuture(McpResponse.success(id, new JsonObject()));
            }
            default -> {
                return CompletableFuture.completedFuture(McpResponse.error(id, McpError.methodNotFound(method)));
            }
        }
    }

    private CompletableFuture<McpResponse> handleInitialize(JsonElement id, JsonObject params, McpSession session) {
        if (session != null && params != null) {
            String clientName = "Unknown Client";
            String clientVersion = "1.0.0";
            if (params.has("clientInfo") && params.get("clientInfo").isJsonObject()) {
                JsonObject ci = params.getAsJsonObject("clientInfo");
                clientName = JsonUtils.getString(ci, "name", "Unknown Client");
                clientVersion = JsonUtils.getString(ci, "version", "1.0.0");
            }
            session.setClientInfo(clientName, clientVersion);
            session.setProtocolVersion(JsonUtils.getString(params, "protocolVersion", PROTOCOL_VERSION));

            if (context != null && getConfig().isBroadcastToChat()) {
                context.broadcastMessage(TextFormatter.info("MCP Client connected: " + clientName + " (v" + clientVersion + ")"));
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", PROTOCOL_VERSION);

        JsonObject capabilities = new JsonObject();

        JsonObject toolsCap = new JsonObject();
        toolsCap.addProperty("listChanged", true);
        capabilities.add("tools", toolsCap);

        JsonObject resourcesCap = new JsonObject();
        resourcesCap.addProperty("subscribe", false);
        resourcesCap.addProperty("listChanged", true);
        capabilities.add("resources", resourcesCap);

        JsonObject promptsCap = new JsonObject();
        promptsCap.addProperty("listChanged", true);
        capabilities.add("prompts", promptsCap);

        JsonObject loggingCap = new JsonObject();
        capabilities.add("logging", loggingCap);

        result.add("capabilities", capabilities);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", SERVER_NAME);
        serverInfo.addProperty("version", SERVER_VERSION);
        result.add("serverInfo", serverInfo);

        return CompletableFuture.completedFuture(McpResponse.success(id, result));
    }

    private CompletableFuture<McpResponse> handleToolsList(JsonElement id) {
        JsonObject result = new JsonObject();
        result.add("tools", toolRegistry.getToolsListJson());
        return CompletableFuture.completedFuture(McpResponse.success(id, result));
    }

    private CompletableFuture<McpResponse> handleToolCall(JsonElement id, JsonObject params, McpSession session) {
        String toolName = JsonUtils.getString(params, "name", "");
        JsonObject arguments = JsonUtils.getObject(params, "arguments");

        if (toolName.isBlank()) {
            return CompletableFuture.completedFuture(McpResponse.error(id, McpError.invalidParams("Tool name cannot be empty")));
        }

        if (getConfig().isReadOnlyMode()) {
            if (toolName.startsWith("set_") || toolName.startsWith("fill_") || toolName.startsWith("break_") || toolName.startsWith("spawn_") || toolName.startsWith("build_") || toolName.equals("execute_command") || toolName.equals("give_item")) {
                return CompletableFuture.completedFuture(McpResponse.error(id, McpError.invalidRequest("Server is in read-only mode")));
            }
        }

        if (!getConfig().isAllowCommandExecution() && toolName.equals("execute_command")) {
            return CompletableFuture.completedFuture(McpResponse.error(id, McpError.invalidRequest("Command execution is disabled in MCP config")));
        }

        if (!getConfig().isAllowWorldModification() && (toolName.startsWith("set_") || toolName.startsWith("fill_") || toolName.startsWith("break_") || toolName.startsWith("build_"))) {
            return CompletableFuture.completedFuture(McpResponse.error(id, McpError.invalidRequest("World modification is disabled in MCP config")));
        }

        if (context != null && getConfig().isBroadcastToChat()) {
            String clientName = session != null ? session.getClientName() : "AI Assistant";
            context.broadcastMessage(TextFormatter.toolExecution(clientName, toolName));
        }

        return toolRegistry.execute(toolName, arguments, context)
                .thenApply(toolResult -> McpResponse.success(id, toolResult.toJson()))
                .exceptionally(t -> McpResponse.error(id, McpError.internalError(t.getMessage())));
    }

    private CompletableFuture<McpResponse> handleResourcesList(JsonElement id) {
        JsonObject result = new JsonObject();
        result.add("resources", resourceRegistry.getResourcesListJson());
        return CompletableFuture.completedFuture(McpResponse.success(id, result));
    }

    private CompletableFuture<McpResponse> handleResourceRead(JsonElement id, JsonObject params) {
        String uri = JsonUtils.getString(params, "uri", "");
        if (uri.isBlank()) {
            return CompletableFuture.completedFuture(McpResponse.error(id, McpError.invalidParams("URI cannot be empty")));
        }

        return resourceRegistry.read(uri, context)
                .thenApply(content -> {
                    JsonObject result = new JsonObject();
                    JsonArray contents = new JsonArray();
                    JsonObject item = new JsonObject();
                    item.addProperty("uri", uri);
                    item.addProperty("mimeType", "application/json");
                    item.addProperty("text", content);
                    contents.add(item);
                    result.add("contents", contents);
                    return McpResponse.success(id, result);
                })
                .exceptionally(t -> McpResponse.error(id, new McpError(McpError.RESOURCE_NOT_FOUND, t.getMessage())));
    }

    private CompletableFuture<McpResponse> handlePromptsList(JsonElement id) {
        JsonObject result = new JsonObject();
        result.add("prompts", promptRegistry.getPromptsListJson());
        return CompletableFuture.completedFuture(McpResponse.success(id, result));
    }

    private CompletableFuture<McpResponse> handlePromptGet(JsonElement id, JsonObject params) {
        String name = JsonUtils.getString(params, "name", "");
        JsonObject arguments = JsonUtils.getObject(params, "arguments");

        if (name.isBlank()) {
            return CompletableFuture.completedFuture(McpResponse.error(id, McpError.invalidParams("Prompt name cannot be empty")));
        }

        return promptRegistry.generate(name, arguments, context)
                .thenApply(messages -> {
                    JsonObject result = new JsonObject();
                    result.addProperty("description", "Generated prompt: " + name);
                    JsonArray messagesArr = new JsonArray();
                    for (McpPrompt.PromptMessage msg : messages) {
                        messagesArr.add(msg.toJson());
                    }
                    result.add("messages", messagesArr);
                    return McpResponse.success(id, result);
                })
                .exceptionally(t -> McpResponse.error(id, new McpError(McpError.PROMPT_NOT_FOUND, t.getMessage())));
    }
}
