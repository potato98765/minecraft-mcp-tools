package com.mrpotato.minecraftmcptools.prompts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mrpotato.minecraftmcptools.protocol.McpPrompt;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class McpPromptRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftMCP/Prompts");
    private final Map<String, McpPrompt> prompts = new ConcurrentHashMap<>();

    public McpPromptRegistry() {
        registerBuiltInPrompts();
    }

    private void registerBuiltInPrompts() {
        MinecraftPrompts.registerAll().forEach(this::register);
        LOGGER.info("Registered {} MCP prompts for Minecraft", prompts.size());
    }

    public void register(McpPrompt prompt) {
        if (prompt == null) return;
        prompts.put(prompt.name(), prompt);
    }

    public Optional<McpPrompt> getPrompt(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(prompts.get(name));
    }

    public Collection<McpPrompt> getAllPrompts() {
        return Collections.unmodifiableCollection(prompts.values());
    }

    public JsonArray getPromptsListJson() {
        JsonArray arr = new JsonArray();
        prompts.values().stream()
                .sorted(Comparator.comparing(McpPrompt::name))
                .forEach(prompt -> arr.add(prompt.toDefinitionJson()));
        return arr;
    }

    public CompletableFuture<List<McpPrompt.PromptMessage>> generate(String name, JsonObject args, MinecraftContext context) {
        Optional<McpPrompt> promptOpt = getPrompt(name);
        if (promptOpt.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Prompt not found: " + name));
        }
        return promptOpt.get().generate(args != null ? args : new JsonObject(), context);
    }
}
