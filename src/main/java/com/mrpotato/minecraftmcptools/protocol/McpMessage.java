package com.mrpotato.minecraftmcptools.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public abstract class McpMessage {
    public static final String JSONRPC_VERSION = "2.0";

    public abstract JsonObject toJson();

    public static McpMessage fromJson(String jsonStr) {
        try {
            JsonElement element = JsonParser.parseString(jsonStr);
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("method")) {
                if (obj.has("id")) {
                    return McpRequest.fromJson(obj);
                } else {
                    return McpNotification.fromJson(obj);
                }
            } else if (obj.has("id") && (obj.has("result") || obj.has("error"))) {
                return McpResponse.fromJson(obj);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
