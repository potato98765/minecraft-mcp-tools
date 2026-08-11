package com.mrpotato.minecraftmcptools.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import com.mrpotato.minecraftmcptools.protocol.McpTool;
import com.mrpotato.minecraftmcptools.protocol.McpToolResult;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import com.mrpotato.minecraftmcptools.util.JsonUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class EntityTools {

    private EntityTools() {}

    public static List<McpTool> registerAll() {
        return List.of(
                new GetNearbyEntitiesTool(),
                new SpawnEntityTool(),
                new InteractWithEntityTool()
        );
    }

    public static class GetNearbyEntitiesTool implements McpTool {
        @Override
        public String name() {
            return "get_nearby_entities";
        }

        @Override
        public String description() {
            return "List all entities within a spherical or cuboid radius around the player (or specific coordinates). Returns distance, position, health, and entity type.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("radius", new JsonUtils.PropertyDefinition("number", "Search radius in blocks (default 32, max 128)"));
            props.put("x", new JsonUtils.PropertyDefinition("number", "Center X (optional, defaults to player position)"));
            props.put("y", new JsonUtils.PropertyDefinition("number", "Center Y (optional, defaults to player position)"));
            props.put("z", new JsonUtils.PropertyDefinition("number", "Center Z (optional, defaults to player position)"));
            props.put("type_filter", new JsonUtils.PropertyDefinition("string", "Optional filter: 'living', 'hostile', 'passive', 'player', 'item', or specific ID"));
            return JsonUtils.buildSchema(props);
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            double radius = Math.clamp(JsonUtils.getDouble(args, "radius", 32.0), 1.0, 128.0);
            String filter = JsonUtils.getString(args, "type_filter", "").toLowerCase(Locale.ROOT);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer();
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                double cx = args.has("x") ? JsonUtils.getDouble(args, "x", 0) : (player != null ? player.getX() : 0);
                double cy = args.has("y") ? JsonUtils.getDouble(args, "y", 0) : (player != null ? player.getY() : 64);
                double cz = args.has("z") ? JsonUtils.getDouble(args, "z", 0) : (player != null ? player.getZ() : 0);

                AABB box = new AABB(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius);
                List<Entity> entities = level.getEntities((Entity) null, box, entity -> {
                    double dist = Math.sqrt(entity.distanceToSqr(cx, cy, cz));
                    return dist <= radius;
                });

                JsonArray entityList = new JsonArray();
                for (Entity entity : entities) {
                    Identifier typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                    String typeId = typeKey != null ? typeKey.toString() : "unknown";

                    if (!filter.isBlank()) {
                        if ("living".equals(filter) && !(entity instanceof LivingEntity)) continue;
                        if ("player".equals(filter) && !(entity instanceof ServerPlayer)) continue;
                        if (!filter.equals("living") && !filter.equals("player") && !typeId.contains(filter)) continue;
                    }

                    JsonObject eObj = new JsonObject();
                    eObj.addProperty("uuid", entity.getStringUUID());
                    eObj.addProperty("type", typeId);
                    eObj.addProperty("name", entity.getName().getString());
                    eObj.addProperty("distance", Math.round(Math.sqrt(entity.distanceToSqr(cx, cy, cz)) * 100.0) / 100.0);

                    JsonObject pos = new JsonObject();
                    pos.addProperty("x", entity.getX());
                    pos.addProperty("y", entity.getY());
                    pos.addProperty("z", entity.getZ());
                    eObj.add("position", pos);

                    if (entity instanceof LivingEntity living) {
                        eObj.addProperty("health", living.getHealth());
                        eObj.addProperty("max_health", living.getMaxHealth());
                        eObj.addProperty("is_alive", living.isAlive());
                    }

                    entityList.add(eObj);
                }

                JsonObject result = new JsonObject();
                result.addProperty("count", entityList.size());
                result.addProperty("center", "(" + cx + ", " + cy + ", " + cz + ")");
                result.addProperty("radius", radius);
                result.add("entities", entityList);

                return McpToolResult.json(result);
            });
        }
    }

    public static class SpawnEntityTool implements McpTool {
        @Override
        public String name() {
            return "spawn_entity";
        }

        @Override
        public String description() {
            return "Summon / spawn a Minecraft entity (e.g. 'minecraft:zombie', 'minecraft:cow', 'minecraft:iron_golem') at coordinates.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("type", new JsonUtils.PropertyDefinition("string", "Entity registry ID (e.g. 'minecraft:creeper')"));
            props.put("x", new JsonUtils.PropertyDefinition("number", "Spawn X coordinate"));
            props.put("y", new JsonUtils.PropertyDefinition("number", "Spawn Y coordinate"));
            props.put("z", new JsonUtils.PropertyDefinition("number", "Spawn Z coordinate"));
            return JsonUtils.buildSchema(props, "type", "x", "y", "z");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            String typeStr = JsonUtils.getString(args, "type", "minecraft:cow");
            double x = JsonUtils.getDouble(args, "x", 0);
            double y = JsonUtils.getDouble(args, "y", 0);
            double z = JsonUtils.getDouble(args, "z", 0);

            return context.runOnServer(() -> {
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                Identifier rl = Identifier.parse(typeStr.contains(":") ? typeStr : "minecraft:" + typeStr);
                Optional<EntityType<?>> typeOpt = BuiltInRegistries.ENTITY_TYPE.getOptional(rl);
                if (typeOpt.isEmpty()) {
                    return McpToolResult.error("Unknown entity type: " + typeStr);
                }

                Entity entity = typeOpt.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                if (entity == null) {
                    return McpToolResult.error("Failed to spawn entity " + rl);
                }

                entity.setPos(x, y, z);
                level.addFreshEntity(entity);

                return McpToolResult.text("Spawned " + rl + " (UUID: " + entity.getStringUUID() + ") at (" + x + ", " + y + ", " + z + ")");
            });
        }
    }

    public static class InteractWithEntityTool implements McpTool {
        @Override
        public String name() {
            return "interact_with_entity";
        }

        @Override
        public String description() {
            return "Interact with or inspect an entity by its UUID (actions: 'inspect', 'remove', 'heal').";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("uuid", new JsonUtils.PropertyDefinition("string", "Entity UUID"));
            props.put("action", new JsonUtils.PropertyDefinition("string", "Action to perform: inspect, remove, heal", new String[]{"inspect", "remove", "heal"}));
            return JsonUtils.buildSchema(props, "uuid");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            String uuidStr = JsonUtils.getString(args, "uuid", "");
            String action = JsonUtils.getString(args, "action", "inspect").toLowerCase(Locale.ROOT);

            return context.runOnServer(() -> {
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                UUID uuid;
                try {
                    uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    return McpToolResult.error("Invalid UUID format: " + uuidStr);
                }

                Entity entity = level.getEntity(uuid);
                if (entity == null) {
                    return McpToolResult.error("Entity with UUID " + uuidStr + " not found");
                }

                Identifier typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

                if ("remove".equals(action)) {
                    entity.discard();
                    return McpToolResult.text("Removed entity " + typeKey + " (" + uuidStr + ")");
                } else if ("heal".equals(action) && entity instanceof LivingEntity living) {
                    living.setHealth(living.getMaxHealth());
                    return McpToolResult.text("Restored " + typeKey + " health to maximum (" + living.getMaxHealth() + ")");
                }

                JsonObject info = new JsonObject();
                info.addProperty("uuid", entity.getStringUUID());
                info.addProperty("type", typeKey != null ? typeKey.toString() : "unknown");
                info.addProperty("name", entity.getName().getString());
                info.addProperty("x", entity.getX());
                info.addProperty("y", entity.getY());
                info.addProperty("z", entity.getZ());
                if (entity instanceof LivingEntity living) {
                    info.addProperty("health", living.getHealth());
                    info.addProperty("max_health", living.getMaxHealth());
                }
                return McpToolResult.json(info);
            });
        }
    }
}
