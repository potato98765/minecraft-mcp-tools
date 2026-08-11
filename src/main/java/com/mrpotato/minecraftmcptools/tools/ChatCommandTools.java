package com.mrpotato.minecraftmcptools.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.mrpotato.minecraftmcptools.protocol.McpTool;
import com.mrpotato.minecraftmcptools.protocol.McpToolResult;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import com.mrpotato.minecraftmcptools.util.JsonUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ChatCommandTools {
    private static final int MAX_CHAT_HISTORY = 100;
    private static final Deque<ChatLogEntry> CHAT_HISTORY = new ConcurrentLinkedDeque<>();

    private ChatCommandTools() {}

    public static void recordChat(String sender, String message) {
        CHAT_HISTORY.addLast(new ChatLogEntry(System.currentTimeMillis(), sender, message));
        while (CHAT_HISTORY.size() > MAX_CHAT_HISTORY) {
            CHAT_HISTORY.pollFirst();
        }
    }

    public static List<ChatLogEntry> getHistory() {
        return List.copyOf(CHAT_HISTORY);
    }

    public static List<McpTool> registerAll() {
        return List.of(
                new ExecuteCommandTool(),
                new SendChatTool(),
                new GetChatHistoryTool()
        );
    }

    public static class ExecuteCommandTool implements McpTool {
        @Override
        public String name() {
            return "execute_command";
        }

        @Override
        public String description() {
            return "Executes any Minecraft server slash command (e.g. 'time set day', 'weather clear', 'gamemode survival', 'locate structure fortress', 'give @p diamond 64'). Do not include the leading slash.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("command", new JsonUtils.PropertyDefinition("string", "The Minecraft command to execute (without leading '/')"));
            return JsonUtils.buildSchema(props, "command");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            String command = JsonUtils.getString(args, "command", "").trim();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }

            if (command.isBlank()) {
                return CompletableFuture.completedFuture(McpToolResult.error("Command cannot be empty"));
            }

            final String finalCmd = command;
            return context.runOnServer(() -> {
                if (!context.isAvailable()) return McpToolResult.error("Minecraft server not available");

                ServerPlayer player = context.getPlayer();
                CommandSourceStack source = player != null ? player.createCommandSourceStack() : context.getServer().createCommandSourceStack();

                try {
                    context.getServer().getCommands().performPrefixedCommand(source, finalCmd);
                    return McpToolResult.text("Executed command: /" + finalCmd);
                } catch (Exception e) {
                    return McpToolResult.error("Error executing command /" + finalCmd + ": " + e.getMessage());
                }
            });
        }
    }

    public static class SendChatTool implements McpTool {
        @Override
        public String name() {
            return "send_chat";
        }

        @Override
        public String description() {
            return "Sends a chat message to all players on the server, formatted with AI / MCP styling.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("message", new JsonUtils.PropertyDefinition("string", "Message text to broadcast to chat"));
            props.put("sender_name", new JsonUtils.PropertyDefinition("string", "Name of the AI assistant (default 'AI')"));
            return JsonUtils.buildSchema(props, "message");
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            String message = JsonUtils.getString(args, "message", "");
            String sender = JsonUtils.getString(args, "sender_name", "AI");

            if (message.isBlank()) {
                return CompletableFuture.completedFuture(McpToolResult.error("Message cannot be empty"));
            }

            return context.runOnServer(() -> {
                if (!context.isAvailable()) return McpToolResult.error("Minecraft server not available");

                Component formatted = Component.empty()
                        .append(Component.literal("[" + sender + "] ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                        .append(Component.literal(message).withStyle(ChatFormatting.WHITE));

                context.broadcastMessage(formatted);
                recordChat(sender, message);

                return McpToolResult.text("Chat broadcast: [" + sender + "] " + message);
            });
        }
    }

    public static class GetChatHistoryTool implements McpTool {
        @Override
        public String name() {
            return "get_chat_history";
        }

        @Override
        public String description() {
            return "Retrieves recent in-game chat messages.";
        }

        @Override
        public JsonObject inputSchema() {
            Map<String, JsonUtils.PropertyDefinition> props = new LinkedHashMap<>();
            props.put("limit", new JsonUtils.PropertyDefinition("integer", "Maximum number of messages to return (default 20, max 100)"));
            return JsonUtils.buildSchema(props);
        }

        @Override
        public CompletableFuture<McpToolResult> execute(JsonObject args, MinecraftContext context) {
            int limit = Math.clamp(JsonUtils.getInt(args, "limit", 20), 1, 100);

            List<ChatLogEntry> all = getHistory();
            int start = Math.max(0, all.size() - limit);
            List<ChatLogEntry> subList = all.subList(start, all.size());

            JsonArray arr = new JsonArray();
            for (ChatLogEntry entry : subList) {
                JsonObject o = new JsonObject();
                o.addProperty("timestamp", entry.timestamp());
                o.addProperty("sender", entry.sender());
                o.addProperty("message", entry.message());
                arr.add(o);
            }

            JsonObject result = new JsonObject();
            result.addProperty("count", arr.size());
            result.add("messages", arr);

            return CompletableFuture.completedFuture(McpToolResult.json(result));
        }
    }

    public record ChatLogEntry(long timestamp, String sender, String message) {}
}
