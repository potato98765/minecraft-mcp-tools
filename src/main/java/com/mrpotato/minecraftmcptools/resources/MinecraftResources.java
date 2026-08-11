package com.mrpotato.minecraftmcptools.resources;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import com.mrpotato.minecraftmcptools.protocol.McpResource;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import com.mrpotato.minecraftmcptools.tools.ChatCommandTools;
import com.mrpotato.minecraftmcptools.tools.EnvironmentTools;
import com.mrpotato.minecraftmcptools.tools.PlayerTools;
import com.mrpotato.minecraftmcptools.util.JsonUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MinecraftResources {

    private MinecraftResources() {}

    public static List<McpResource> registerAll() {
        return List.of(
                new PlayerStatusResource(),
                new PlayerInventoryResource(),
                new WorldOverviewResource(),
                new SurroundingsResource(),
                new ChatHistoryResource(),
                new ServerStatsResource()
        );
    }

    public static class PlayerStatusResource implements McpResource {
        @Override
        public String uri() {
            return "minecraft://player/status";
        }

        @Override
        public String name() {
            return "Player Status";
        }

        @Override
        public String description() {
            return "Live game status of the active player (coordinates, health, hunger, dimension, rotation).";
        }

        @Override
        public String mimeType() {
            return "application/json";
        }

        @Override
        public CompletableFuture<String> read(MinecraftContext context) {
            PlayerTools.GetPlayerInfoTool tool = new PlayerTools.GetPlayerInfoTool();
            return tool.execute(new JsonObject(), context).thenApply(res -> {
                if (res.getContent().isEmpty()) return "{}";
                return res.getContent().get(0).text();
            });
        }
    }

    public static class PlayerInventoryResource implements McpResource {
        @Override
        public String uri() {
            return "minecraft://player/inventory";
        }

        @Override
        public String name() {
            return "Player Inventory";
        }

        @Override
        public String description() {
            return "Current contents of the player inventory, hotbar, offhand, and armor slots.";
        }

        @Override
        public String mimeType() {
            return "application/json";
        }

        @Override
        public CompletableFuture<String> read(MinecraftContext context) {
            PlayerTools.GetInventoryTool tool = new PlayerTools.GetInventoryTool();
            return tool.execute(new JsonObject(), context).thenApply(res -> {
                if (res.getContent().isEmpty()) return "{}";
                return res.getContent().get(0).text();
            });
        }
    }

    public static class WorldOverviewResource implements McpResource {
        @Override
        public String uri() {
            return "minecraft://world/overview";
        }

        @Override
        public String name() {
            return "World Overview";
        }

        @Override
        public String description() {
            return "World environment summary including time of day, weather, day count, and difficulty.";
        }

        @Override
        public String mimeType() {
            return "application/json";
        }

        @Override
        public CompletableFuture<String> read(MinecraftContext context) {
            EnvironmentTools.GetWorldInfoTool tool = new EnvironmentTools.GetWorldInfoTool();
            return tool.execute(new JsonObject(), context).thenApply(res -> {
                if (res.getContent().isEmpty()) return "{}";
                return res.getContent().get(0).text();
            });
        }
    }

    public static class SurroundingsResource implements McpResource {
        @Override
        public String uri() {
            return "minecraft://world/surroundings";
        }

        @Override
        public String name() {
            return "Player Surroundings";
        }

        @Override
        public String description() {
            return "16-block radius intelligent scan of nearby blocks, ores, containers, liquids, and hazards.";
        }

        @Override
        public String mimeType() {
            return "application/json";
        }

        @Override
        public CompletableFuture<String> read(MinecraftContext context) {
            EnvironmentTools.ScanSurroundingsTool tool = new EnvironmentTools.ScanSurroundingsTool();
            return tool.execute(new JsonObject(), context).thenApply(res -> {
                if (res.getContent().isEmpty()) return "{}";
                return res.getContent().get(0).text();
            });
        }
    }

    public static class ChatHistoryResource implements McpResource {
        @Override
        public String uri() {
            return "minecraft://chat/recent";
        }

        @Override
        public String name() {
            return "Recent Chat Messages";
        }

        @Override
        public String description() {
            return "Recent in-game chat log messages.";
        }

        @Override
        public String mimeType() {
            return "application/json";
        }

        @Override
        public CompletableFuture<String> read(MinecraftContext context) {
            ChatCommandTools.GetChatHistoryTool tool = new ChatCommandTools.GetChatHistoryTool();
            return tool.execute(new JsonObject(), context).thenApply(res -> {
                if (res.getContent().isEmpty()) return "{}";
                return res.getContent().get(0).text();
            });
        }
    }

    public static class ServerStatsResource implements McpResource {
        @Override
        public String uri() {
            return "minecraft://server/stats";
        }

        @Override
        public String name() {
            return "Server Statistics";
        }

        @Override
        public String description() {
            return "Server operational statistics, connected players, tick rate, and loaded levels.";
        }

        @Override
        public String mimeType() {
            return "application/json";
        }

        @Override
        public CompletableFuture<String> read(MinecraftContext context) {
            return context.runOnServer(() -> {
                JsonObject stats = new JsonObject();
                if (!context.isAvailable()) {
                    stats.addProperty("status", "offline");
                    return JsonUtils.GSON.toJson(stats);
                }

                stats.addProperty("status", "online");
                stats.addProperty("player_count", context.getAllPlayers().size());
                stats.addProperty("max_players", context.getServer().getMaxPlayers());
                stats.addProperty("server_version", context.getServer().getServerVersion());
                stats.addProperty("is_dedicated", context.getServer().isDedicatedServer());

                JsonArray playersArr = new JsonArray();
                for (ServerPlayer p : context.getAllPlayers()) {
                    JsonObject pObj = new JsonObject();
                    pObj.addProperty("name", p.getName().getString());
                    pObj.addProperty("uuid", p.getStringUUID());
                    pObj.addProperty("dimension", p.level().dimension().identifier().toString());
                    playersArr.add(pObj);
                }
                stats.add("players", playersArr);

                return JsonUtils.GSON.toJson(stats);
            });
        }
    }
}
