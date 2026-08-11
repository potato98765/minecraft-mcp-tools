package com.mrpotato.minecraftmcptools.tools;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.mrpotato.minecraftmcptools.protocol.McpTool;
import com.mrpotato.minecraftmcptools.protocol.McpToolResult;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import com.mrpotato.minecraftmcptools.util.JsonUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ConstructionTools {

    private ConstructionTools() {}

    public static List<McpTool> registerAll() {
        return List.of(
                new BuildSchematicTool(),
                new BuildSphereTool(),
                new BuildCylinderTool()
        );
    }

    public static class BuildSchematicTool implements McpTool {
        @Override
        public String name() {
            return "build_schematic";
        }

        @Override
        public String description() {
            return "Builds a procedural structure at target coordinates. Supported templates: 'house', 'tower', 'portal', 'pyramid', 'beacon_base', 'glass_dome'.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("template", new JsonUtils.PropertyDefinition("string", "Structure template name: house, tower, portal, pyramid, beacon_base, glass_dome",
                    new String[]{"house", "tower", "portal", "pyramid", "beacon_base", "glass_dome"}));
            props.put("x", new JsonUtils.PropertyDefinition("integer", "Origin X (optional, defaults to player position)"));
            props.put("y", new JsonUtils.PropertyDefinition("integer", "Origin Y (optional, defaults to player position)"));
            props.put("z", new JsonUtils.PropertyDefinition("integer", "Origin Z (optional, defaults to player position)"));
            return JsonUtils.buildSchema(props, "template");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            String template = JsonUtils.getString(args, "template", "house").toLowerCase(Locale.ROOT);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer();
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                int ox = args.has("x") ? JsonUtils.getInt(args, "x", 0) : (player != null ? (int) Math.floor(player.getX()) : 0);
                int oy = args.has("y") ? JsonUtils.getInt(args, "y", 0) : (player != null ? (int) Math.floor(player.getY()) : 64);
                int oz = args.has("z") ? JsonUtils.getInt(args, "z", 0) : (player != null ? (int) Math.floor(player.getZ()) : 0);

                int blocksPlaced = 0;

                switch (template) {
                    case "house" -> {

                        int width = 7, height = 5, length = 7;
                        for (int x = 0; x < width; x++) {
                            for (int y = 0; y < height; y++) {
                                for (int z = 0; z < length; z++) {
                                    BlockPos pos = new BlockPos(ox + x, oy + y, oz + z);
                                    boolean isCorner = (x == 0 || x == width - 1) && (z == 0 || z == length - 1);
                                    boolean isWall = (x == 0 || x == width - 1 || z == 0 || z == length - 1);

                                    if (y == 0) {
                                        level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 2);
                                    } else if (y == height - 1) {
                                        level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 2);
                                    } else if (isCorner) {
                                        level.setBlock(pos, Blocks.OAK_LOG.defaultBlockState(), 2);
                                    } else if (isWall) {
                                        if (y == 2 && ((x == 3 && (z == 0 || z == length - 1)) || (z == 3 && (x == 0 || x == width - 1)))) {
                                            level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 2);
                                        } else if (y == 1 && x == 3 && z == 0) {
                                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                                        } else {
                                            level.setBlock(pos, Blocks.OAK_PLANKS.defaultBlockState(), 2);
                                        }
                                    } else {
                                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                                    }
                                    blocksPlaced++;
                                }
                            }
                        }
                    }
                    case "tower" -> {

                        int size = 5, height = 12;
                        for (int y = 0; y < height; y++) {
                            for (int x = 0; x < size; x++) {
                                for (int z = 0; z < size; z++) {
                                    BlockPos pos = new BlockPos(ox + x, oy + y, oz + z);
                                    boolean isWall = (x == 0 || x == size - 1 || z == 0 || z == size - 1);
                                    boolean isRoof = (y == height - 1);

                                    if (isRoof) {
                                        if (isWall && (x % 2 == 0 || z % 2 == 0)) {
                                            level.setBlock(pos, Blocks.STONE_BRICK_WALL.defaultBlockState(), 2);
                                        }
                                    } else if (y == 0 || y == height - 2) {
                                        level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 2);
                                    } else if (isWall) {
                                        level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 2);
                                    } else {
                                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                                    }
                                    blocksPlaced++;
                                }
                            }
                        }
                    }
                    case "portal" -> {
                        for (int x = 0; x < 4; x++) {
                            for (int y = 0; y < 5; y++) {
                                BlockPos pos = new BlockPos(ox + x, oy + y, oz);
                                boolean isFrame = (x == 0 || x == 3 || y == 0 || y == 4);
                                if (isFrame) {
                                    level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 2);
                                } else {
                                    level.setBlock(pos, Blocks.NETHER_PORTAL.defaultBlockState(), 2);
                                }
                                blocksPlaced++;
                            }
                        }
                    }
                    case "beacon_base" -> {
                        for (int x = -2; x <= 2; x++) {
                            for (int z = -2; z <= 2; z++) {
                                level.setBlock(new BlockPos(ox + x, oy, oz + z), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                                blocksPlaced++;
                            }
                        }
                        for (int x = -1; x <= 1; x++) {
                            for (int z = -1; z <= 1; z++) {
                                level.setBlock(new BlockPos(ox + x, oy + 1, oz + z), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                                blocksPlaced++;
                            }
                        }
                        level.setBlock(new BlockPos(ox, oy + 2, oz), Blocks.BEACON.defaultBlockState(), 2);
                        blocksPlaced++;
                    }
                    case "pyramid" -> {
                        int baseRadius = 6;
                        for (int r = baseRadius; r >= 0; r--) {
                            int currentY = oy + (baseRadius - r);
                            for (int x = -r; x <= r; x++) {
                                for (int z = -r; z <= r; z++) {
                                    level.setBlock(new BlockPos(ox + x, currentY, oz + z), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), 2);
                                    blocksPlaced++;
                                }
                            }
                        }
                    }
                    case "glass_dome" -> {
                        int radius = 6;
                        for (int x = -radius; x <= radius; x++) {
                            for (int y = 0; y <= radius; y++) {
                                for (int z = -radius; z <= radius; z++) {
                                    double dist = Math.sqrt(x * x + y * y + z * z);
                                    BlockPos pos = new BlockPos(ox + x, oy + y, oz + z);
                                    if (dist >= radius - 0.7 && dist <= radius + 0.5) {
                                        level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 2);
                                        blocksPlaced++;
                                    } else if (dist < radius - 0.7) {
                                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                                    }
                                }
                            }
                        }
                    }
                    default -> {
                        return McpToolResult.error("Unknown schematic template: " + template + " (available: house, tower, portal, beacon_base, pyramid, glass_dome)");
                    }
                }

                return McpToolResult.text("Successfully built schematic '" + template + "' at (" + ox + ", " + oy + ", " + oz + ") with " + blocksPlaced + " blocks placed.");
            });
        }
    }

    public static class BuildSphereTool implements McpTool {
        @Override
        public String name() {
            return "build_sphere";
        }

        @Override
        public String description() {
            return "Creates a solid or hollow sphere of specified blocks around coordinates.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("radius", new JsonUtils.PropertyDefinition("integer", "Sphere radius in blocks (1-16)"));
            props.put("block_id", new JsonUtils.PropertyDefinition("string", "Block ID (e.g. 'minecraft:glass', 'minecraft:glowstone')"));
            props.put("hollow", new JsonUtils.PropertyDefinition("boolean", "Whether sphere is hollow (default false)"));
            props.put("x", new JsonUtils.PropertyDefinition("integer", "Center X (optional, defaults to player position)"));
            props.put("y", new JsonUtils.PropertyDefinition("integer", "Center Y (optional, defaults to player position)"));
            props.put("z", new JsonUtils.PropertyDefinition("integer", "Center Z (optional, defaults to player position)"));
            return JsonUtils.buildSchema(props, "radius", "block_id");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int radius = Math.clamp(JsonUtils.getInt(args, "radius", 4), 1, 16);
            String blockId = JsonUtils.getString(args, "block_id", "minecraft:glass");
            boolean hollow = JsonUtils.getBoolean(args, "hollow", false);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer();
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                Identifier rl = Identifier.parse(blockId.contains(":") ? blockId : "minecraft:" + blockId);
                Optional<Block> blockOpt = BuiltInRegistries.BLOCK.getOptional(rl);
                if (blockOpt.isEmpty()) return McpToolResult.error("Unknown block ID: " + blockId);

                BlockState state = blockOpt.get().defaultBlockState();
                int cx = args.has("x") ? JsonUtils.getInt(args, "x", 0) : (player != null ? (int) Math.floor(player.getX()) : 0);
                int cy = args.has("y") ? JsonUtils.getInt(args, "y", 0) : (player != null ? (int) Math.floor(player.getY()) : 64);
                int cz = args.has("z") ? JsonUtils.getInt(args, "z", 0) : (player != null ? (int) Math.floor(player.getZ()) : 0);

                int placed = 0;
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            double dist = Math.sqrt(x * x + y * y + z * z);
                            BlockPos pos = new BlockPos(cx + x, cy + y, cz + z);
                            if (hollow) {
                                if (dist >= radius - 0.8 && dist <= radius + 0.4) {
                                    level.setBlock(pos, state, 2);
                                    placed++;
                                }
                            } else {
                                if (dist <= radius + 0.4) {
                                    level.setBlock(pos, state, 2);
                                    placed++;
                                }
                            }
                        }
                    }
                }

                return McpToolResult.text("Built " + (hollow ? "hollow" : "solid") + " sphere of " + rl + " (radius " + radius + ") with " + placed + " blocks.");
            });
        }
    }

    public static class BuildCylinderTool implements McpTool {
        @Override
        public String name() {
            return "build_cylinder";
        }

        @Override
        public String description() {
            return "Creates a vertical cylinder of specified blocks.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("radius", new JsonUtils.PropertyDefinition("integer", "Cylinder radius in blocks (1-20)"));
            props.put("height", new JsonUtils.PropertyDefinition("integer", "Height in blocks (1-30)"));
            props.put("block_id", new JsonUtils.PropertyDefinition("string", "Block ID (e.g. 'minecraft:stone_bricks')"));
            props.put("hollow", new JsonUtils.PropertyDefinition("boolean", "Whether cylinder is hollow (default false)"));
            props.put("x", new JsonUtils.PropertyDefinition("integer", "Center X"));
            props.put("y", new JsonUtils.PropertyDefinition("integer", "Base Y"));
            props.put("z", new JsonUtils.PropertyDefinition("integer", "Center Z"));
            return JsonUtils.buildSchema(props, "radius", "height", "block_id");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int radius = Math.clamp(JsonUtils.getInt(args, "radius", 5), 1, 20);
            int height = Math.clamp(JsonUtils.getInt(args, "height", 5), 1, 30);
            String blockId = JsonUtils.getString(args, "block_id", "minecraft:stone_bricks");
            boolean hollow = JsonUtils.getBoolean(args, "hollow", false);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer();
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                Identifier rl = Identifier.parse(blockId.contains(":") ? blockId : "minecraft:" + blockId);
                Optional<Block> blockOpt = BuiltInRegistries.BLOCK.getOptional(rl);
                if (blockOpt.isEmpty()) return McpToolResult.error("Unknown block ID: " + blockId);

                BlockState state = blockOpt.get().defaultBlockState();
                int cx = args.has("x") ? JsonUtils.getInt(args, "x", 0) : (player != null ? (int) Math.floor(player.getX()) : 0);
                int cy = args.has("y") ? JsonUtils.getInt(args, "y", 0) : (player != null ? (int) Math.floor(player.getY()) : 64);
                int cz = args.has("z") ? JsonUtils.getInt(args, "z", 0) : (player != null ? (int) Math.floor(player.getZ()) : 0);

                int placed = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = -radius; x <= radius; x++) {
                        for (int z = -radius; z <= radius; z++) {
                            double dist = Math.sqrt(x * x + z * z);
                            BlockPos pos = new BlockPos(cx + x, cy + y, cz + z);
                            if (hollow) {
                                if (dist >= radius - 0.8 && dist <= radius + 0.4) {
                                    level.setBlock(pos, state, 2);
                                    placed++;
                                }
                            } else {
                                if (dist <= radius + 0.4) {
                                    level.setBlock(pos, state, 2);
                                    placed++;
                                }
                            }
                        }
                    }
                }

                return McpToolResult.text("Built " + (hollow ? "hollow" : "solid") + " cylinder of " + rl + " (radius " + radius + ", height " + height + ") with " + placed + " blocks.");
            });
        }
    }
}
