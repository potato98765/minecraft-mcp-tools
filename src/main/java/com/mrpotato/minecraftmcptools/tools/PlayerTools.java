package com.mrpotato.minecraftmcptools.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.mrpotato.minecraftmcptools.protocol.McpTool;
import com.mrpotato.minecraftmcptools.protocol.McpToolResult;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import com.mrpotato.minecraftmcptools.util.JsonUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class PlayerTools {

    private PlayerTools() {}

    public static List<McpTool> registerAll() {
        return List.of(
                new GetPlayerInfoTool(),
                new TeleportPlayerTool(),
                new MovePlayerTool(),
                new LookAtTool(),
                new GetInventoryTool(),
                new SelectHotbarSlotTool(),
                new GiveItemTool(),
                new ClearInventoryTool()
        );
    }

    public static class GetPlayerInfoTool implements McpTool {
        @Override
        public String name() {
            return "get_player_info";
        }

        @Override
        public String description() {
            return "Get detailed status of a player: position, rotation, dimension, health, hunger, level, gamemode, active status effects, and flying status.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("player", new JsonUtils.PropertyDefinition("string", "Player username or UUID (optional, defaults to first active player)"));
            return JsonUtils.buildSchema(props);
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            String playerName = JsonUtils.getString(args, "player", null);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer(playerName);
                if (player == null) return McpToolResult.error("No active player found");

                JsonObject json = new JsonObject();
                json.addProperty("name", player.getName().getString());
                json.addProperty("uuid", player.getStringUUID());
                json.addProperty("dimension", player.level().dimension().identifier().toString());

                JsonObject pos = new JsonObject();
                pos.addProperty("x", player.getX());
                pos.addProperty("y", player.getY());
                pos.addProperty("z", player.getZ());
                pos.addProperty("yaw", player.getYRot());
                pos.addProperty("pitch", player.getXRot());
                json.add("position", pos);

                JsonObject stats = new JsonObject();
                stats.addProperty("health", player.getHealth());
                stats.addProperty("max_health", player.getMaxHealth());
                stats.addProperty("food_level", player.getFoodData().getFoodLevel());
                stats.addProperty("saturation", player.getFoodData().getSaturationLevel());
                stats.addProperty("experience_level", player.experienceLevel);
                stats.addProperty("experience_progress", player.experienceProgress);
                stats.addProperty("gamemode", player.gameMode.getGameModeForPlayer().getName());
                stats.addProperty("is_sneaking", player.isShiftKeyDown());
                stats.addProperty("is_sprinting", player.isSprinting());
                stats.addProperty("is_swimming", player.isSwimming());
                stats.addProperty("is_on_ground", player.onGround());
                stats.addProperty("is_flying", player.getAbilities().flying);
                json.add("stats", stats);

                JsonArray effectsArr = new JsonArray();
                for (MobEffectInstance effect : player.getActiveEffects()) {
                    JsonObject eff = new JsonObject();
                    Identifier effId = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
                    eff.addProperty("id", effId != null ? effId.toString() : "unknown");
                    eff.addProperty("amplifier", effect.getAmplifier());
                    eff.addProperty("duration_ticks", effect.getDuration());
                    effectsArr.add(eff);
                }
                json.add("active_effects", effectsArr);

                return McpToolResult.json(json);
            });
        }
    }

    public static class TeleportPlayerTool implements McpTool {
        @Override
        public String name() {
            return "teleport_player";
        }

        @Override
        public String description() {
            return "Teleports the player to specified world coordinates (x, y, z).";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("x", new JsonUtils.PropertyDefinition("number", "Target X coordinate"));
            props.put("y", new JsonUtils.PropertyDefinition("number", "Target Y coordinate"));
            props.put("z", new JsonUtils.PropertyDefinition("number", "Target Z coordinate"));
            props.put("player", new JsonUtils.PropertyDefinition("string", "Player username (optional)"));
            return JsonUtils.buildSchema(props, "x", "y", "z");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            double x = JsonUtils.getDouble(args, "x", 0);
            double y = JsonUtils.getDouble(args, "y", 0);
            double z = JsonUtils.getDouble(args, "z", 0);
            String playerName = JsonUtils.getString(args, "player", null);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer(playerName);
                if (player == null) return McpToolResult.error("No active player found");

                player.teleportTo(x, y, z);
                return McpToolResult.text("Teleported " + player.getName().getString() + " to (" + x + ", " + y + ", " + z + ")");
            });
        }
    }

    public static class MovePlayerTool implements McpTool {
        @Override
        public String name() {
            return "move_player";
        }

        @Override
        public String description() {
            return "Moves the player relative to their current position by (dx, dy, dz).";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("dx", new JsonUtils.PropertyDefinition("number", "Delta X"));
            props.put("dy", new JsonUtils.PropertyDefinition("number", "Delta Y"));
            props.put("dz", new JsonUtils.PropertyDefinition("number", "Delta Z"));
            props.put("player", new JsonUtils.PropertyDefinition("string", "Player username (optional)"));
            return JsonUtils.buildSchema(props, "dx", "dy", "dz");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            double dx = JsonUtils.getDouble(args, "dx", 0);
            double dy = JsonUtils.getDouble(args, "dy", 0);
            double dz = JsonUtils.getDouble(args, "dz", 0);
            String playerName = JsonUtils.getString(args, "player", null);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer(playerName);
                if (player == null) return McpToolResult.error("No active player found");

                player.teleportRelative(dx, dy, dz);
                return McpToolResult.text("Moved " + player.getName().getString() + " by (" + dx + ", " + dy + ", " + dz + ")");
            });
        }
    }

    public static class LookAtTool implements McpTool {
        @Override
        public String name() {
            return "look_at";
        }

        @Override
        public String description() {
            return "Orients the player view direction towards target world coordinates (x, y, z).";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("x", new JsonUtils.PropertyDefinition("number", "Target X"));
            props.put("y", new JsonUtils.PropertyDefinition("number", "Target Y"));
            props.put("z", new JsonUtils.PropertyDefinition("number", "Target Z"));
            props.put("player", new JsonUtils.PropertyDefinition("string", "Player username (optional)"));
            return JsonUtils.buildSchema(props, "x", "y", "z");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            double tx = JsonUtils.getDouble(args, "x", 0);
            double ty = JsonUtils.getDouble(args, "y", 0);
            double tz = JsonUtils.getDouble(args, "z", 0);
            String playerName = JsonUtils.getString(args, "player", null);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer(playerName);
                if (player == null) return McpToolResult.error("No active player found");

                Vec3 target = new Vec3(tx, ty, tz);
                player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target);
                return McpToolResult.text("Oriented " + player.getName().getString() + "'s gaze towards (" + tx + ", " + ty + ", " + tz + ")");
            });
        }
    }

    public static class GetInventoryTool implements McpTool {
        @Override
        public String name() {
            return "get_inventory";
        }

        @Override
        public String description() {
            return "Get full contents of the player's inventory, hotbar, armor, and offhand slots.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("player", new JsonUtils.PropertyDefinition("string", "Player username (optional)"));
            return JsonUtils.buildSchema(props);
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            String playerName = JsonUtils.getString(args, "player", null);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer(playerName);
                if (player == null) return McpToolResult.error("No active player found");

                Inventory inv = player.getInventory();
                JsonObject result = new JsonObject();
                result.addProperty("selected_slot", inv.getSelectedSlot());

                JsonArray itemsArr = new JsonArray();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack stack = inv.getItem(i);
                    if (!stack.isEmpty()) {
                        JsonObject itemObj = new JsonObject();
                        itemObj.addProperty("slot", i);
                        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        itemObj.addProperty("id", key != null ? key.toString() : "unknown");
                        itemObj.addProperty("name", stack.getHoverName().getString());
                        itemObj.addProperty("count", stack.getCount());
                        itemObj.addProperty("max_count", stack.getMaxStackSize());
                        itemObj.addProperty("is_damageable", stack.isDamageableItem());
                        if (stack.isDamageableItem()) {
                            itemObj.addProperty("damage", stack.getDamageValue());
                            itemObj.addProperty("max_damage", stack.getMaxDamage());
                        }
                        if (i < 9) {
                            itemObj.addProperty("location", "hotbar");
                        } else if (i < 36) {
                            itemObj.addProperty("location", "main");
                        } else if (i < 40) {
                            itemObj.addProperty("location", "armor");
                        } else {
                            itemObj.addProperty("location", "offhand");
                        }
                        itemsArr.add(itemObj);
                    }
                }
                result.add("items", itemsArr);

                return McpToolResult.json(result);
            });
        }
    }

    public static class SelectHotbarSlotTool implements McpTool {
        @Override
        public String name() {
            return "select_hotbar_slot";
        }

        @Override
        public String description() {
            return "Select active hotbar slot index (0 to 8).";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("slot", new JsonUtils.PropertyDefinition("integer", "Hotbar slot index (0-8)"));
            props.put("player", new JsonUtils.PropertyDefinition("string", "Player username (optional)"));
            return JsonUtils.buildSchema(props, "slot");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int slot = JsonUtils.getInt(args, "slot", 0);
            String playerName = JsonUtils.getString(args, "player", null);

            if (slot < 0 || slot > 8) {
                return CompletableFuture.completedFuture(McpToolResult.error("Slot must be between 0 and 8 (provided: " + slot + ")"));
            }

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer(playerName);
                if (player == null) return McpToolResult.error("No active player found");

                player.getInventory().setSelectedSlot(slot);
                return McpToolResult.text("Selected hotbar slot " + slot);
            });
        }
    }

    public static class GiveItemTool implements McpTool {
        @Override
        public String name() {
            return "give_item";
        }

        @Override
        public String description() {
            return "Gives items to the player's inventory.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("item_id", new JsonUtils.PropertyDefinition("string", "Item ID (e.g. 'minecraft:diamond', 'minecraft:golden_apple')"));
            props.put("count", new JsonUtils.PropertyDefinition("integer", "Item count (1-64, default 1)"));
            props.put("player", new JsonUtils.PropertyDefinition("string", "Player username (optional)"));
            return JsonUtils.buildSchema(props, "item_id");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            String itemId = JsonUtils.getString(args, "item_id", "minecraft:diamond");
            int count = Math.clamp(JsonUtils.getInt(args, "count", 1), 1, 64);
            String playerName = JsonUtils.getString(args, "player", null);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer(playerName);
                if (player == null) return McpToolResult.error("No active player found");

                Identifier rl = Identifier.parse(itemId.contains(":") ? itemId : "minecraft:" + itemId);
                Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(rl);
                if (itemOpt.isEmpty()) {
                    return McpToolResult.error("Unknown item ID: " + itemId);
                }

                ItemStack stack = new ItemStack(itemOpt.get(), count);
                boolean added = player.getInventory().add(stack);
                if (!added) {
                    player.drop(stack, false);
                }

                return McpToolResult.text("Gave " + count + "x " + rl + " to " + player.getName().getString());
            });
        }
    }

    public static class ClearInventoryTool implements McpTool {
        @Override
        public String name() {
            return "clear_inventory";
        }

        @Override
        public String description() {
            return "Clears the player's inventory completely.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("player", new JsonUtils.PropertyDefinition("string", "Player username (optional)"));
            return JsonUtils.buildSchema(props);
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            String playerName = JsonUtils.getString(args, "player", null);

            return context.runOnServer(() -> {
                ServerPlayer player = context.getPlayer(playerName);
                if (player == null) return McpToolResult.error("No active player found");

                player.getInventory().clearContent();
                return McpToolResult.text("Cleared inventory of " + player.getName().getString());
            });
        }
    }
}
