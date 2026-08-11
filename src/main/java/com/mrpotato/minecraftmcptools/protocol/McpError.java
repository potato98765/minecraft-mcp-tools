package com.mrpotato.minecraftmcptools.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record McpError(int code, String message, JsonElement data) {
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    public static final int RESOURCE_NOT_FOUND = -32002;
    public static final int TOOL_NOT_FOUND = -32001;
    public static final int PROMPT_NOT_FOUND = -32003;
    public static final int UNAUTHORIZED = -32000;

    public McpError(int code, String message) {
        this(code, message, null);
    }

    public static McpError parseError(String detail) {
        return new McpError(PARSE_ERROR, "Parse error: " + detail);
    }

    public static McpError invalidRequest(String detail) {
        return new McpError(INVALID_REQUEST, "Invalid Request: " + detail);
    }

    public static McpError methodNotFound(String method) {
        return new McpError(METHOD_NOT_FOUND, "Method not found: " + method);
    }

    public static McpError invalidParams(String detail) {
        return new McpError(INVALID_PARAMS, "Invalid params: " + detail);
    }

    public static McpError internalError(String detail) {
        return new McpError(INTERNAL_ERROR, "Internal error: " + detail);
    }

    public static McpError unauthorized(String detail) {
        return new McpError(UNAUTHORIZED, "Unauthorized: " + detail);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("code", code);
        obj.addProperty("message", message);
        if (data != null) {
            obj.add("data", data);
        }
        return obj;
    }
}
