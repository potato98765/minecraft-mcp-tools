package com.mrpotato.minecraftmcptools;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mrpotato.minecraftmcptools.config.McpConfigManager;
import com.mrpotato.minecraftmcptools.protocol.McpRequest;
import com.mrpotato.minecraftmcptools.protocol.McpResponse;
import com.mrpotato.minecraftmcptools.server.McpServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

public class McpServerTest {
    private McpServer server;

    @BeforeEach
    public void setup() {
        McpConfigManager configManager = new McpConfigManager();
        server = new McpServer(configManager);
    }

    @Test
    public void testInitialize() throws ExecutionException, InterruptedException {
        JsonObject params = new JsonObject();
        params.addProperty("protocolVersion", "2024-11-05");
        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "TestClient");
        clientInfo.addProperty("version", "1.0.0");
        params.add("clientInfo", clientInfo);

        McpRequest req = new McpRequest(new JsonPrimitive(1), "initialize", params);
        McpResponse resp = server.handleRequest(req, null).get();

        Assertions.assertTrue(resp.isSuccess());
        JsonObject result = resp.getResult().getAsJsonObject();
        Assertions.assertEquals("2024-11-05", result.get("protocolVersion").getAsString());
        Assertions.assertEquals("minecraft-mcp", result.getAsJsonObject("serverInfo").get("name").getAsString());
        Assertions.assertTrue(result.getAsJsonObject("capabilities").has("tools"));
        Assertions.assertTrue(result.getAsJsonObject("capabilities").has("resources"));
        Assertions.assertTrue(result.getAsJsonObject("capabilities").has("prompts"));
    }

    @Test
    public void testToolsList() throws ExecutionException, InterruptedException {
        McpRequest req = new McpRequest(new JsonPrimitive(2), "tools/list", new JsonObject());
        McpResponse resp = server.handleRequest(req, null).get();

        Assertions.assertTrue(resp.isSuccess());
        JsonObject result = resp.getResult().getAsJsonObject();
        Assertions.assertTrue(result.getAsJsonArray("tools").size() >= 20);
    }

    @Test
    public void testResourcesList() throws ExecutionException, InterruptedException {
        McpRequest req = new McpRequest(new JsonPrimitive(3), "resources/list", new JsonObject());
        McpResponse resp = server.handleRequest(req, null).get();

        Assertions.assertTrue(resp.isSuccess());
        JsonObject result = resp.getResult().getAsJsonObject();
        Assertions.assertTrue(result.getAsJsonArray("resources").size() >= 6);
    }

    @Test
    public void testPromptsList() throws ExecutionException, InterruptedException {
        McpRequest req = new McpRequest(new JsonPrimitive(4), "prompts/list", new JsonObject());
        McpResponse resp = server.handleRequest(req, null).get();

        Assertions.assertTrue(resp.isSuccess());
        JsonObject result = resp.getResult().getAsJsonObject();
        Assertions.assertTrue(result.getAsJsonArray("prompts").size() >= 3);
    }

    @Test
    public void testMethodNotFound() throws ExecutionException, InterruptedException {
        McpRequest req = new McpRequest(new JsonPrimitive(5), "unknown/method", new JsonObject());
        McpResponse resp = server.handleRequest(req, null).get();

        Assertions.assertFalse(resp.isSuccess());
        Assertions.assertEquals(-32601, resp.getError().code());
    }
}
