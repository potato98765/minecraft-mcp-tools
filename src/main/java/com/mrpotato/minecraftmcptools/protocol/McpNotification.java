package com.mrpotato.minecraftmcptools.protocol;

import com.google.gson.JsonObject;

public class McpNotification extends McpMessage {
    private final String method;
    private final JsonObject params;

    public McpNotification(String method, JsonObject params) {
        this.method = method;
        this.params = params != null ? params : new JsonObject();
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
        obj.addProperty("method", method);
        if (params != null) {
            obj.add("params", params);
        }
        return obj;
    }

    public static McpNotification fromJson(JsonObject obj) {
        String method = obj.get("method").getAsString();
        JsonObject params = obj.has("params") && obj.get("params").isJsonObject()
                ? obj.getAsJsonObject("params")
                : new JsonObject();
        return new McpNotification(method, params);
    }
}
