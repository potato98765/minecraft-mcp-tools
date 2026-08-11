package com.mrpotato.minecraftmcptools.protocol;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

public interface McpResource {

    String uri();

    String name();

    String description();

    String mimeType();

    CompletableFuture<String> read(MinecraftContext context);

    default JsonObject toDefinitionJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("uri", uri());
        obj.addProperty("name", name());
        obj.addProperty("description", description());
        obj.addProperty("mimeType", mimeType());
        return obj;
    }
}
