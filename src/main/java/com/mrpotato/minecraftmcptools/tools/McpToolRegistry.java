package com.mrpotato.minecraftmcptools.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mrpotato.minecraftmcptools.protocol.McpTool;
import com.mrpotato.minecraftmcptools.protocol.McpToolResult;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class McpToolRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftMCP/Tools");
    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();

    public McpToolRegistry() {
        registerBuiltInTools();
    }

    private void registerBuiltInTools() {
        WorldTools.registerAll().forEach(this::register);
        PlayerTools.registerAll().forEach(this::register);
        EntityTools.registerAll().forEach(this::register);
        ChatCommandTools.registerAll().forEach(this::register);
        EnvironmentTools.registerAll().forEach(this::register);
        ConstructionTools.registerAll().forEach(this::register);
        LOGGER.info("Registered {} MCP tools for Minecraft", tools.size());
    }

    public void register(McpTool tool) {
        if (tool == null) return;
        tools.put(tool.name(), tool);
    }

    public Optional<McpTool> getTool(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<McpTool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public JsonArray getToolsListJson() {
        JsonArray arr = new JsonArray();
        tools.values().stream()
                .sorted(Comparator.comparing(McpTool::name))
                .forEach(tool -> arr.add(tool.toDefinitionJson()));
        return arr;
    }

    public CompletableFuture<McpToolResult> execute(String name, JsonObject arguments, MinecraftContext context) {
        Optional<McpTool> toolOpt = getTool(name);
        if (toolOpt.isEmpty()) {
            return CompletableFuture.completedFuture(McpToolResult.error("Unknown tool: " + name));
        }

        try {
            return toolOpt.get().execute(arguments != null ? arguments : new JsonObject(), context);
        } catch (Exception e) {
            LOGGER.error("Error executing MCP tool: {}", name, e);
            return CompletableFuture.completedFuture(McpToolResult.error("Tool execution failed: " + e.getMessage()));
        }
    }
}
