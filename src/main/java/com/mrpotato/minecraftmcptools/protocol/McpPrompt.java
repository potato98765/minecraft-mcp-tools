package com.mrpotato.minecraftmcptools.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface McpPrompt {
    String name();

    String description();

    List<PromptArgument> arguments();

    CompletableFuture<List<PromptMessage>> generate(JsonObject args, MinecraftContext context);

    default JsonObject toDefinitionJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name());
        obj.addProperty("description", description());
        JsonArray argsArr = new JsonArray();
        for (PromptArgument arg : arguments()) {
            JsonObject argObj = new JsonObject();
            argObj.addProperty("name", arg.name());
            argObj.addProperty("description", arg.description());
            argObj.addProperty("required", arg.required());
            argsArr.add(argObj);
        }
        obj.add("arguments", argsArr);
        return obj;
    }

    record PromptArgument(String name, String description, boolean required) {}

    record PromptMessage(String role, String content) {
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("role", role);
            JsonObject contentObj = new JsonObject();
            contentObj.addProperty("type", "text");
            contentObj.addProperty("text", content);
            obj.add("content", contentObj);
            return obj;
        }
    }
}
