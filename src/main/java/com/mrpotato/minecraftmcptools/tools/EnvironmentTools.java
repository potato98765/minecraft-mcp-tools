package com.mrpotato.minecraftmcptools.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import com.mrpotato.minecraftmcptools.protocol.McpTool;
import com.mrpotato.minecraftmcptools.protocol.McpToolResult;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import com.mrpotato.minecraftmcptools.util.JsonUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class EnvironmentTools {

    private EnvironmentTools() {}

    public static List<McpTool> registerAll() {
        return List.of(
                new GetWorldInfoTool(),
                new SetTimeTool(),
                new SetWeatherTool(),
                new ScanSurroundingsTool()
        );
    }

    public static class GetWorldInfoTool implements McpTool {
        @Override
        public String name() {
            return "get_world_info";
        }

        @Override
        public String description() {
            return "Get world information: game time, weather status, world spawn coordinates, difficulty, and player count.";
        }

        @Override
        public JsonObject inputSchema() {
            return JsonUtils.buildSchema(Map.of());
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            return context.runOnServer(() -> {
                ServerLevel level = context.getLevel();
                if (level == null) return McpToolResult.error("World/Server level not available");

                long gameTime = level.getLevelData().getGameTime();
                long timeInDay = gameTime % 24000L;
                long dayNumber = gameTime / 24000L;

                String timePhase;
                if (timeInDay < 1000) timePhase = "Early Morning";
                else if (timeInDay < 6000) timePhase = "Morning";
                else if (timeInDay < 12000) timePhase = "Afternoon / Evening";
                else if (timeInDay < 13000) timePhase = "Sunset";
                else if (timeInDay < 18000) timePhase = "Night";
                else if (timeInDay < 23000) timePhase = "Midnight / Late Night";
                else timePhase = "Dawn";

                LevelData.RespawnData respawnData = level.getRespawnData();

                JsonObject result = new JsonObject();
                result.addProperty("day_number", dayNumber);
                result.addProperty("time_ticks", timeInDay);
                result.addProperty("total_game_time_ticks", gameTime);
                result.addProperty("time_phase", timePhase);
                result.addProperty("is_raining", level.isRaining());
                result.addProperty("is_thundering", level.isThundering());
                result.addProperty("rain_level", level.getRainLevel(1.0f));
                result.addProperty("difficulty", level.getDifficulty().name());

                if (respawnData != null) {
                    JsonObject spawn = new JsonObject();
                    spawn.addProperty("dimension", respawnData.dimension().identifier().toString());
                    spawn.addProperty("x", respawnData.pos().getX());
                    spawn.addProperty("y", respawnData.pos().getY());
                    spawn.addProperty("z", respawnData.pos().getZ());
                    result.add("world_spawn", spawn);
                }

                result.addProperty("active_players", context.getAllPlayers().size());

                return McpToolResult.json(result);
            });
        }
    }

    public static class SetTimeTool implements McpTool {
        @Override
        public String name() {
            return "set_time";
        }

        @Override
        public String description() {
            return "Sets the world time of day. Accepted values: 'day', 'noon', 'night', 'midnight', or a specific tick number (0-24000).";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("time", new JsonUtils.PropertyDefinition("string", "Time target: 'day', 'noon', 'night', 'midnight', or a number (0-24000)"));
            return JsonUtils.buildSchema(props, "time");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            String timeArg = JsonUtils.getString(args, "time", "day").toLowerCase(Locale.ROOT);

            return context.runOnServer(() -> {
                if (!context.isAvailable()) return McpToolResult.error("World/Server level not available");

                CommandSourceStack source = context.getServer().createCommandSourceStack();
                context.getServer().getCommands().performPrefixedCommand(source, "time set " + timeArg);

                return McpToolResult.text("Set time to " + timeArg);
            });
        }
    }

    public static class SetWeatherTool implements McpTool {
        @Override
        public String name() {
            return "set_weather";
        }

        @Override
        public String description() {
            return "Sets the weather conditions: 'clear', 'rain', or 'thunder'.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("weather", new JsonUtils.PropertyDefinition("string", "Weather type: 'clear', 'rain', 'thunder'", new String[]{"clear", "rain", "thunder"}));
            props.put("duration_seconds", new JsonUtils.PropertyDefinition("integer", "Duration in seconds (default 300)"));
            return JsonUtils.buildSchema(props, "weather");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            String weather = JsonUtils.getString(args, "weather", "clear").toLowerCase(Locale.ROOT);
            int durationSec = Math.clamp(JsonUtils.getInt(args, "duration_seconds", 300), 1, 86400);

            return context.runOnServer(() -> {
                if (!context.isAvailable()) return McpToolResult.error("World/Server level not available");

                CommandSourceStack source = context.getServer().createCommandSourceStack();
                context.getServer().getCommands().performPrefixedCommand(source, "weather " + weather + " " + durationSec);

                return McpToolResult.text("Weather set to " + weather + " for " + durationSec + " seconds");
            });
        }
    }

    public static class ScanSurroundingsTool implements McpTool {
        @Override
        public String name() {
            return "scan_surroundings";
        }

        @Override
        public String description() {
            return "Performs an intelligent scan around the player, identifying nearby ores, hazards (lava, fire), water sources, chests, and interesting blocks.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("radius", new JsonUtils.PropertyDefinition("integer", "Scan radius (default 16, max 32)"));
            return JsonUtils.buildSchema(props);
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int radius = Math.clamp(JsonUtils.getInt(args, "radius", 16), 4, 32);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer();
                ServerLevel level = context.getLevel();
                if (level == null || player == null) return McpToolResult.error("Player or world level not available");

                int px = (int) Math.floor(player.getX());
                int py = (int) Math.floor(player.getY());
                int pz = (int) Math.floor(player.getZ());

                Map<String, List<JsonObject>> categories = new LinkedHashMap<>();
                categories.put("ores", new ArrayList<>());
                categories.put("hazards", new ArrayList<>());
                categories.put("containers", new ArrayList<>());
                categories.put("liquids", new ArrayList<>());

                for (int x = px - radius; x <= px + radius; x++) {
                    for (int y = Math.max(level.getMinY(), py - radius); y <= Math.min(level.getMaxY(), py + radius); y++) {
                        for (int z = pz - radius; z <= pz + radius; z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            if (state.isAir()) continue;

                            Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                            String name = key != null ? key.toString() : "";

                            String category = null;
                            if (name.contains("ore") || name.contains("ancient_debris")) {
                                category = "ores";
                            } else if (name.contains("lava") || name.contains("fire") || name.contains("crying_obsidian") || name.contains("magma")) {
                                category = "hazards";
                            } else if (name.contains("chest") || name.contains("barrel") || name.contains("shulker") || name.contains("furnace")) {
                                category = "containers";
                            } else if (name.contains("water")) {
                                category = "liquids";
                            }

                            if (category != null && categories.get(category).size() < 25) {
                                JsonObject item = new JsonObject();
                                item.addProperty("block", name);
                                item.addProperty("x", x);
                                item.addProperty("y", y);
                                item.addProperty("z", z);
                                double dist = Math.sqrt(pos.distSqr(new BlockPos(px, py, pz)));
                                item.addProperty("distance", Math.round(dist * 10.0) / 10.0);
                                categories.get(category).add(item);
                            }
                        }
                    }
                }

                JsonObject result = new JsonObject();
                result.addProperty("player_position", "(" + px + ", " + py + ", " + pz + ")");
                result.addProperty("scan_radius", radius);

                for (Map.Entry<String, List<JsonObject>> entry : categories.entrySet()) {
                    JsonArray arr = new JsonArray();
                    entry.getValue().forEach(arr::add);
                    result.add(entry.getKey(), arr);
                }

                return McpToolResult.json(result);
            });
        }
    }
}
