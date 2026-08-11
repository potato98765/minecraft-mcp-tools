package com.mrpotato.minecraftmcptools.resources;

import com.google.gson.JsonArray;
import com.mrpotato.minecraftmcptools.protocol.McpResource;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class McpResourceRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftMCP/Resources");
    private final Map<String, McpResource> resources = new ConcurrentHashMap<>();

    public McpResourceRegistry() {
        registerBuiltInResources();
    }

    private void registerBuiltInResources() {
        MinecraftResources.registerAll().forEach(this::register);
        LOGGER.info("Registered {} MCP resources for Minecraft", resources.size());
    }

    public void register(McpResource resource) {
        if (resource == null) return;
        resources.put(resource.uri(), resource);
    }

    public Optional<McpResource> getResource(String uri) {
        if (uri == null) return Optional.empty();
        return Optional.ofNullable(resources.get(uri));
    }

    public Collection<McpResource> getAllResources() {
        return Collections.unmodifiableCollection(resources.values());
    }

    public JsonArray getResourcesListJson() {
        JsonArray arr = new JsonArray();
        resources.values().stream()
                .sorted(Comparator.comparing(McpResource::uri))
                .forEach(res -> arr.add(res.toDefinitionJson()));
        return arr;
    }

    public CompletableFuture<String> read(String uri, MinecraftContext context) {
        Optional<McpResource> resOpt = getResource(uri);
        if (resOpt.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Resource not found: " + uri));
        }
        return resOpt.get().read(context);
    }
}
