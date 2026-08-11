package com.mrpotato.minecraftmcptools.util;

import com.google.gson.*;

import java.util.Map;

public final class JsonUtils {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final Gson COMPACT_GSON = new GsonBuilder().disableHtmlEscaping().create();

    private JsonUtils() {}

    public static String getString(JsonObject obj, String key, String defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static int getInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static double getDouble(JsonObject obj, String key, double defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonObject()) {
            return null;
        }
        return obj.getAsJsonObject(key);
    }

    public static JsonArray getArray(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) {
            return null;
        }
        return obj.getAsJsonArray(key);
    }

    public static JsonObject buildSchema(Map<String, PropertyDefinition> properties, String... requiredFields) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();
        for (Map.Entry<String, PropertyDefinition> entry : properties.entrySet()) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", entry.getValue().type());
            prop.addProperty("description", entry.getValue().description());
            if (entry.getValue().enumValues() != null && entry.getValue().enumValues().length > 0) {
                JsonArray enumArr = new JsonArray();
                for (String val : entry.getValue().enumValues()) {
                    enumArr.add(val);
                }
                prop.add("enum", enumArr);
            }
            props.add(entry.getKey(), prop);
        }
        schema.add("properties", props);

        if (requiredFields.length > 0) {
            JsonArray required = new JsonArray();
            for (String field : requiredFields) {
                required.add(field);
            }
            schema.add("required", required);
        }

        return schema;
    }

    public record PropertyDefinition(String type, String description, String[] enumValues) {
        public PropertyDefinition(String type, String description) {
            this(type, description, null);
        }
    }
}
