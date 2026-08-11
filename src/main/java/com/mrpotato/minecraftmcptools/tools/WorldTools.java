package com.mrpotato.minecraftmcptools.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import com.mrpotato.minecraftmcptools.protocol.McpTool;
import com.mrpotato.minecraftmcptools.protocol.McpToolResult;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import com.mrpotato.minecraftmcptools.util.JsonUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class WorldTools {

    private WorldTools() {}

    public static List<McpTool> registerAll() {
        return List.of(
                new GetBlockTool(),
                new GetBlocksAreaTool(),
                new SetBlockTool(),
                new FillBlocksTool(),
                new BreakBlockTool(),
                new GetBiomeTool(),
                new GetHeightmapTool(),
                new GetLightLevelTool()
        );
    }

    public static class GetBlockTool implements McpTool {
        @Override
        public String name() {
            return "get_block";
        }

        @Override
        public String description() {
            return "Inspect a Minecraft block at the given coordinates (x, y, z). Returns block ID, name, state properties, light levels, and hardness.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("x", new JsonUtils.PropertyDefinition("integer", "X coordinate in world space"));
            props.put("y", new JsonUtils.PropertyDefinition("integer", "Y coordinate in world space"));
            props.put("z", new JsonUtils.PropertyDefinition("integer", "Z coordinate in world space"));
            props.put("dimension", new JsonUtils.PropertyDefinition("string", "Optional dimension ID (e.g. 'minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end')"));
            return JsonUtils.buildSchema(props, "x", "y", "z");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int x = JsonUtils.getInt(args, "x", 0);
            int y = JsonUtils.getInt(args, "y", 0);
            int z = JsonUtils.getInt(args, "z", 0);

            return context.runOnServer(() -> {
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());

                JsonObject result = new JsonObject();
                result.addProperty("x", x);
                result.addProperty("y", y);
                result.addProperty("z", z);
                result.addProperty("block_id", id != null ? id.toString() : "unknown");
                result.addProperty("block_name", state.getBlock().getName().getString());
                result.addProperty("is_air", state.isAir());
                result.addProperty("is_liquid", state.liquid());
                result.addProperty("is_solid", state.isSolid());
                result.addProperty("block_light", level.getBrightness(LightLayer.BLOCK, pos));
                result.addProperty("sky_light", level.getBrightness(LightLayer.SKY, pos));

                JsonObject stateProps = new JsonObject();
                for (Property<?> prop : state.getProperties()) {
                    stateProps.addProperty(prop.getName(), state.getValue(prop).toString());
                }
                result.add("properties", stateProps);

                return McpToolResult.json(result);
            });
        }
    }

    public static class GetBlocksAreaTool implements McpTool {
        @Override
        public String name() {
            return "get_blocks_area";
        }

        @Override
        public String description() {
            return "Inspect a 3D bounding box of blocks between (x1, y1, z1) and (x2, y2, z2). Returns block counts summary and coordinate matrix (max volume 32,768 blocks).";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("x1", new JsonUtils.PropertyDefinition("integer", "Start X coordinate"));
            props.put("y1", new JsonUtils.PropertyDefinition("integer", "Start Y coordinate"));
            props.put("z1", new JsonUtils.PropertyDefinition("integer", "Start Z coordinate"));
            props.put("x2", new JsonUtils.PropertyDefinition("integer", "End X coordinate"));
            props.put("y2", new JsonUtils.PropertyDefinition("integer", "End Y coordinate"));
            props.put("z2", new JsonUtils.PropertyDefinition("integer", "End Z coordinate"));
            props.put("include_block_list", new JsonUtils.PropertyDefinition("boolean", "Whether to return full list of non-air blocks (default true)"));
            return JsonUtils.buildSchema(props, "x1", "y1", "z1", "x2", "y2", "z2");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int x1 = JsonUtils.getInt(args, "x1", 0);
            int y1 = JsonUtils.getInt(args, "y1", 0);
            int z1 = JsonUtils.getInt(args, "z1", 0);
            int x2 = JsonUtils.getInt(args, "x2", 0);
            int y2 = JsonUtils.getInt(args, "y2", 0);
            int z2 = JsonUtils.getInt(args, "z2", 0);
            boolean includeList = JsonUtils.getBoolean(args, "include_block_list", true);

            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

            long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (volume > 32768) {
                return CompletableFuture.completedFuture(McpToolResult.error("Area volume exceeds maximum limit of 32,768 blocks (requested: " + volume + ")"));
            }

            return context.runOnServer(() -> {
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                Map<String, Integer> counts = new HashMap<>();
                JsonArray blockList = new JsonArray();

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                            String id = key != null ? key.toString() : "unknown";

                            counts.put(id, counts.getOrDefault(id, 0) + 1);

                            if (includeList && !state.isAir()) {
                                JsonObject b = new JsonObject();
                                b.addProperty("x", x);
                                b.addProperty("y", y);
                                b.addProperty("z", z);
                                b.addProperty("id", id);
                                blockList.add(b);
                            }
                        }
                    }
                }

                JsonObject result = new JsonObject();
                result.addProperty("volume", volume);
                result.addProperty("dimensions", (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1));

                JsonObject countObj = new JsonObject();
                counts.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .forEach(e -> countObj.addProperty(e.getKey(), e.getValue()));
                result.add("block_counts", countObj);

                if (includeList) {
                    result.add("non_air_blocks", blockList);
                }

                return McpToolResult.json(result);
            });
        }
    }

    public static class SetBlockTool implements McpTool {
        @Override
        public String name() {
            return "set_block";
        }

        @Override
        public String description() {
            return "Place or replace a block at coordinates (x, y, z) with specified block ID (e.g. 'minecraft:stone', 'minecraft:oak_log', 'minecraft:air').";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("x", new JsonUtils.PropertyDefinition("integer", "X coordinate"));
            props.put("y", new JsonUtils.PropertyDefinition("integer", "Y coordinate"));
            props.put("z", new JsonUtils.PropertyDefinition("integer", "Z coordinate"));
            props.put("block_id", new JsonUtils.PropertyDefinition("string", "Block registry ID (e.g. 'minecraft:diamond_block')"));
            return JsonUtils.buildSchema(props, "x", "y", "z", "block_id");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int x = JsonUtils.getInt(args, "x", 0);
            int y = JsonUtils.getInt(args, "y", 0);
            int z = JsonUtils.getInt(args, "z", 0);
            String blockId = JsonUtils.getString(args, "block_id", "minecraft:stone");

            return context.runOnServer(() -> {
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                Identifier rl = Identifier.parse(blockId.contains(":") ? blockId : "minecraft:" + blockId);
                Optional<Block> blockOpt = BuiltInRegistries.BLOCK.getOptional(rl);
                if (blockOpt.isEmpty()) {
                    return McpToolResult.error("Unknown block ID: " + blockId);
                }

                BlockPos pos = new BlockPos(x, y, z);
                BlockState newState = blockOpt.get().defaultBlockState();
                level.setBlockAndUpdate(pos, newState);

                return McpToolResult.text("Successfully placed " + rl + " at (" + x + ", " + y + ", " + z + ")");
            });
        }
    }

    public static class FillBlocksTool implements McpTool {
        @Override
        public String name() {
            return "fill_blocks";
        }

        @Override
        public String description() {
            return "Fill a box from (x1, y1, z1) to (x2, y2, z2) with a block. Supports modes: 'replace' (all blocks), 'outline' (outer shell only), 'hollow' (hollow shell with air inside).";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("x1", new JsonUtils.PropertyDefinition("integer", "Start X"));
            props.put("y1", new JsonUtils.PropertyDefinition("integer", "Start Y"));
            props.put("z1", new JsonUtils.PropertyDefinition("integer", "Start Z"));
            props.put("x2", new JsonUtils.PropertyDefinition("integer", "End X"));
            props.put("y2", new JsonUtils.PropertyDefinition("integer", "End Y"));
            props.put("z2", new JsonUtils.PropertyDefinition("integer", "End Z"));
            props.put("block_id", new JsonUtils.PropertyDefinition("string", "Block registry ID (e.g. 'minecraft:stone_bricks')"));
            props.put("mode", new JsonUtils.PropertyDefinition("string", "Fill mode: replace, outline, or hollow", new String[]{"replace", "outline", "hollow"}));
            return JsonUtils.buildSchema(props, "x1", "y1", "z1", "x2", "y2", "z2", "block_id");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int x1 = JsonUtils.getInt(args, "x1", 0);
            int y1 = JsonUtils.getInt(args, "y1", 0);
            int z1 = JsonUtils.getInt(args, "z1", 0);
            int x2 = JsonUtils.getInt(args, "x2", 0);
            int y2 = JsonUtils.getInt(args, "y2", 0);
            int z2 = JsonUtils.getInt(args, "z2", 0);
            String blockId = JsonUtils.getString(args, "block_id", "minecraft:stone");
            String mode = JsonUtils.getString(args, "mode", "replace").toLowerCase(Locale.ROOT);

            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

            long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (volume > 32768) {
                return CompletableFuture.completedFuture(McpToolResult.error("Fill area volume exceeds limit of 32,768 blocks (requested: " + volume + ")"));
            }

            return context.runOnServer(() -> {
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                Identifier rl = Identifier.parse(blockId.contains(":") ? blockId : "minecraft:" + blockId);
                Optional<Block> blockOpt = BuiltInRegistries.BLOCK.getOptional(rl);
                if (blockOpt.isEmpty()) {
                    return McpToolResult.error("Unknown block ID: " + blockId);
                }

                BlockState fillState = blockOpt.get().defaultBlockState();
                BlockState airState = Blocks.AIR.defaultBlockState();
                int changedCount = 0;

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            boolean isEdge = (x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ);
                            BlockPos pos = new BlockPos(x, y, z);

                            if ("outline".equals(mode)) {
                                if (isEdge) {
                                    level.setBlock(pos, fillState, 2);
                                    changedCount++;
                                }
                            } else if ("hollow".equals(mode)) {
                                if (isEdge) {
                                    level.setBlock(pos, fillState, 2);
                                } else {
                                    level.setBlock(pos, airState, 2);
                                }
                                changedCount++;
                            } else {
                                level.setBlock(pos, fillState, 2);
                                changedCount++;
                            }
                        }
                    }
                }

                return McpToolResult.text("Successfully filled " + changedCount + " blocks with " + rl + " (mode: " + mode + ")");
            });
        }
    }

    public static class BreakBlockTool implements McpTool {
        @Override
        public String name() {
            return "break_block";
        }

        @Override
        public String description() {
            return "Destroys a block at (x, y, z), optionally dropping items.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("x", new JsonUtils.PropertyDefinition("integer", "X coordinate"));
            props.put("y", new JsonUtils.PropertyDefinition("integer", "Y coordinate"));
            props.put("z", new JsonUtils.PropertyDefinition("integer", "Z coordinate"));
            props.put("drop_items", new JsonUtils.PropertyDefinition("boolean", "Whether to drop item entities (default true)"));
            return JsonUtils.buildSchema(props, "x", "y", "z");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int x = JsonUtils.getInt(args, "x", 0);
            int y = JsonUtils.getInt(args, "y", 0);
            int z = JsonUtils.getInt(args, "z", 0);
            boolean dropItems = JsonUtils.getBoolean(args, "drop_items", true);

            return context.runOnServer(() -> {
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                BlockPos pos = new BlockPos(x, y, z);
                BlockState oldState = level.getBlockState(pos);
                Identifier id = BuiltInRegistries.BLOCK.getKey(oldState.getBlock());

                if (oldState.isAir()) {
                    return McpToolResult.text("Block at (" + x + ", " + y + ", " + z + ") is already air.");
                }

                level.destroyBlock(pos, dropItems);
                return McpToolResult.text("Broke " + id + " at (" + x + ", " + y + ", " + z + ")");
            });
        }
    }

    public static class GetBiomeTool implements McpTool {
        @Override
        public String name() {
            return "get_biome";
        }

        @Override
        public String description() {
            return "Get the biome and climate properties at specified coordinates.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("x", new JsonUtils.PropertyDefinition("integer", "X coordinate"));
            props.put("y", new JsonUtils.PropertyDefinition("integer", "Y coordinate"));
            props.put("z", new JsonUtils.PropertyDefinition("integer", "Z coordinate"));
            return JsonUtils.buildSchema(props, "x", "y", "z");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int x = JsonUtils.getInt(args, "x", 0);
            int y = JsonUtils.getInt(args, "y", 0);
            int z = JsonUtils.getInt(args, "z", 0);

            return context.runOnServer(() -> {
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                BlockPos pos = new BlockPos(x, y, z);
                var biomeHolder = level.getBiome(pos);
                String biomeName = biomeHolder.unwrapKey()
                        .map(k -> k.identifier().toString())
                        .orElse("unknown");

                JsonObject res = new JsonObject();
                res.addProperty("biome", biomeName);
                res.addProperty("x", x);
                res.addProperty("y", y);
                res.addProperty("z", z);
                res.addProperty("has_precipitation", biomeHolder.value().hasPrecipitation());
                res.addProperty("temperature", biomeHolder.value().getBaseTemperature());

                return McpToolResult.json(res);
            });
        }
    }

    public static class GetHeightmapTool implements McpTool {
        @Override
        public String name() {
            return "get_heightmap";
        }

        @Override
        public String description() {
            return "Find the highest surface block Y coordinate at (x, z).";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("x", new JsonUtils.PropertyDefinition("integer", "X coordinate"));
            props.put("z", new JsonUtils.PropertyDefinition("integer", "Z coordinate"));
            props.put("type", new JsonUtils.PropertyDefinition("string", "Heightmap type: WORLD_SURFACE, MOTION_BLOCKING, OCEAN_FLOOR", new String[]{"WORLD_SURFACE", "MOTION_BLOCKING", "OCEAN_FLOOR"}));
            return JsonUtils.buildSchema(props, "x", "z");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int x = JsonUtils.getInt(args, "x", 0);
            int z = JsonUtils.getInt(args, "z", 0);
            String typeStr = JsonUtils.getString(args, "type", "WORLD_SURFACE").toUpperCase(Locale.ROOT);

            return context.runOnServer(() -> {
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                Heightmap.Types type;
                try {
                    type = Heightmap.Types.valueOf(typeStr);
                } catch (Exception e) {
                    type = Heightmap.Types.WORLD_SURFACE;
                }

                int height = level.getHeight(type, x, z);
                BlockPos surfacePos = new BlockPos(x, height - 1, z);
                BlockState surfaceState = level.getBlockState(surfacePos);
                Identifier surfaceId = BuiltInRegistries.BLOCK.getKey(surfaceState.getBlock());

                JsonObject result = new JsonObject();
                result.addProperty("x", x);
                result.addProperty("z", z);
                result.addProperty("surface_y", height);
                result.addProperty("surface_block", surfaceId != null ? surfaceId.toString() : "air");

                return McpToolResult.json(result);
            });
        }
    }

    public static class GetLightLevelTool implements McpTool {
        @Override
        public String name() {
            return "get_light_level";
        }

        @Override
        public String description() {
            return "Get block light, sky light, and total light levels at coordinates (x, y, z).";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("x", new JsonUtils.PropertyDefinition("integer", "X coordinate"));
            props.put("y", new JsonUtils.PropertyDefinition("integer", "Y coordinate"));
            props.put("z", new JsonUtils.PropertyDefinition("integer", "Z coordinate"));
            return JsonUtils.buildSchema(props, "x", "y", "z");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int x = JsonUtils.getInt(args, "x", 0);
            int y = JsonUtils.getInt(args, "y", 0);
            int z = JsonUtils.getInt(args, "z", 0);

            return context.runOnServer(() -> {
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                BlockPos pos = new BlockPos(x, y, z);
                int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
                int skyLight = level.getBrightness(LightLayer.SKY, pos);
                int rawLight = level.getMaxLocalRawBrightness(pos);

                JsonObject result = new JsonObject();
                result.addProperty("x", x);
                result.addProperty("y", y);
                result.addProperty("z", z);
                result.addProperty("block_light", blockLight);
                result.addProperty("sky_light", skyLight);
                result.addProperty("total_brightness", rawLight);

                return McpToolResult.json(result);
            });
        }
    }
}
