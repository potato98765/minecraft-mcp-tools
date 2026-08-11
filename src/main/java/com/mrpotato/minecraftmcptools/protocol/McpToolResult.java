package com.mrpotato.minecraftmcptools.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mrpotato.minecraftmcptools.util.JsonUtils;

import java.util.ArrayList;
import java.util.List;

public class McpToolResult {
    private final List<ContentItem> content = new ArrayList<>();
    private boolean isError = false;

    public McpToolResult() {}

    public static McpToolResult text(String text) {
        McpToolResult result = new McpToolResult();
        result.addText(text);
        return result;
    }

    public static McpToolResult json(JsonElement json) {
        McpToolResult result = new McpToolResult();
        result.addText(JsonUtils.GSON.toJson(json));
        return result;
    }

    public static McpToolResult error(String errorMessage) {
        McpToolResult result = new McpToolResult();
        result.addText(errorMessage);
        result.setError(true);
        return result;
    }

    public McpToolResult addText(String text) {
        content.add(new ContentItem("text", text, null, null));
        return this;
    }

    public McpToolResult addImage(String base64Data, String mimeType) {
        content.add(new ContentItem("image", null, base64Data, mimeType));
        return this;
    }

    public McpToolResult setError(boolean error) {
        isError = error;
        return this;
    }

    public boolean isError() {
        return isError;
    }

    public List<ContentItem> getContent() {
        return content;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        JsonArray contentArr = new JsonArray();
        for (ContentItem item : content) {
            JsonObject itemObj = new JsonObject();
            itemObj.addProperty("type", item.type());
            if ("text".equals(item.type())) {
                itemObj.addProperty("text", item.text() != null ? item.text() : "");
            } else if ("image".equals(item.type())) {
                itemObj.addProperty("data", item.data() != null ? item.data() : "");
                itemObj.addProperty("mimeType", item.mimeType() != null ? item.mimeType() : "image/png");
            }
            contentArr.add(itemObj);
        }
        obj.add("content", contentArr);
        if (isError) {
            obj.addProperty("isError", true);
        }
        return obj;
    }

    public record ContentItem(String type, String text, String data, String mimeType) {}
}
