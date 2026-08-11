package com.mrpotato.minecraftmcptools.protocol;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public interface McpTool {

    String name();

    String description();

    JsonObject inputSchema();

    CompletableFuture<McpToolResult> execute(JsonObject arguments, MinecraftContext context);

    default JsonObject toDefinitionJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name());
        obj.addProperty("description", description());
        obj.add("inputSchema", inputSchema());
        return obj;
    }
}
