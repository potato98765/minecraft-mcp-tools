package com.mrpotato.minecraftmcptools.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class McpResponse extends McpMessage {
    private final JsonElement id;
    private final JsonElement result;
    private final McpError error;

    public McpResponse(JsonElement id, JsonElement result, McpError error) {
        this.id = id != null ? id : new JsonPrimitive(0);
        this.result = result;
        this.error = error;
    }

    public static McpResponse success(JsonElement id, JsonElement result) {
        return new McpResponse(id, result, null);
    }

    public static McpResponse error(JsonElement id, McpError error) {
        return new McpResponse(id, null, error);
    }

    public static McpResponse error(JsonElement id, int code, String message) {
        return new McpResponse(id, null, new McpError(code, message));
    }

    public JsonElement getId() {
        return id;
    }

    public JsonElement getResult() {
        return result;
    }

    public McpError getError() {
        return error;
    }

    public boolean isSuccess() {
        return error == null;
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("jsonrpc", JSONRPC_VERSION);
        obj.add("id", id);
        if (error != null) {
            obj.add("error", error.toJson());
        } else {
            obj.add("result", result != null ? result : new JsonObject());
        }
        return obj;
    }

    public static McpResponse fromJson(JsonObject obj) {
        JsonElement id = obj.get("id");
        JsonElement result = obj.get("result");
        McpError error = null;
        if (obj.has("error") && obj.get("error").isJsonObject()) {
            JsonObject errObj = obj.getAsJsonObject("error");
            int code = errObj.has("code") ? errObj.get("code").getAsInt() : -32603;
            String msg = errObj.has("message") ? errObj.get("message").getAsString() : "Unknown error";
            JsonElement data = errObj.get("data");
            error = new McpError(code, msg, data);
        }
        return new McpResponse(id, result, error);
    }
}
