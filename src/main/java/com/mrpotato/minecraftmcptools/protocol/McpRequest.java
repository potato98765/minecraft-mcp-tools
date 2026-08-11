package com.mrpotato.minecraftmcptools.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class McpRequest extends McpMessage {
    private final JsonElement id;
    private final String method;
    private final JsonObject params;

    public McpRequest(JsonElement id, String method, JsonObject params) {
        this.id = id != null ? id : new JsonPrimitive(0);
        this.method = method;
        this.params = params != null ? params : new JsonObject();
    }

    public McpRequest(String id, String method, JsonObject params) {
        this(new JsonPrimitive(id), method, params);
    }

    public McpRequest(long id, String method, JsonObject params) {
        this(new JsonPrimitive(id), method, params);
    }

    public JsonElement getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public JsonObject getParams() {
        return params;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("jsonrpc", JSONRPC_VERSION);
        obj.add("id", id);
        obj.addProperty("method", method);
        if (params != null) {
            obj.add("params", params);
        }
        return obj;
    }

    public static McpRequest fromJson(JsonObject obj) {
        JsonElement id = obj.get("id");
        String method = obj.get("method").getAsString();
        JsonObject params = obj.has("params") && obj.get("params").isJsonObject()
                ? obj.getAsJsonObject("params")
                : new JsonObject();
        return new McpRequest(id, method, params);
    }
}
