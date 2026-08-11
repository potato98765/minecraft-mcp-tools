package com.mrpotato.minecraftmcptools.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import com.mrpotato.minecraftmcptools.config.McpConfig;
import com.mrpotato.minecraftmcptools.server.McpServer;
import com.mrpotato.minecraftmcptools.util.TextFormatter;

import java.net.URI;

public class McpCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, McpServer server) {
        dispatcher.register(
                Commands.literal("mcp")
                        .executes(ctx -> showStatus(ctx, server))
                        .then(Commands.literal("status").executes(ctx -> showStatus(ctx, server)))
                        .then(Commands.literal("start").executes(ctx -> startServer(ctx, server)))
                        .then(Commands.literal("stop").executes(ctx -> stopServer(ctx, server)))
                        .then(Commands.literal("restart").executes(ctx -> restartServer(ctx, server)))
                        .then(Commands.literal("token")
                                .executes(ctx -> showToken(ctx, server))
                                .then(Commands.literal("show").executes(ctx -> showToken(ctx, server)))
                                .then(Commands.literal("generate").executes(ctx -> generateToken(ctx, server)))
                        )
                        .then(Commands.literal("config")
                                .then(Commands.literal("port")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1024, 65535))
                                                .executes(ctx -> setPort(ctx, server, IntegerArgumentType.getInteger(ctx, "value")))))
                                .then(Commands.literal("requireAuth")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setRequireAuth(ctx, server, BoolArgumentType.getBool(ctx, "value")))))
                                .then(Commands.literal("broadcast")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setBroadcast(ctx, server, BoolArgumentType.getBool(ctx, "value")))))
                                .then(Commands.literal("readOnly")
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setReadOnly(ctx, server, BoolArgumentType.getBool(ctx, "value")))))
                        )
                        .then(Commands.literal("help").executes(McpCommand::showHelp))
        );
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx, McpServer server) {
        McpConfig config = server.getConfig();
        boolean running = server.isRunning();

        Component header = Component.literal("=== Minecraft MCP Server Status ===").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        ctx.getSource().sendSuccess(() -> header, false);

        ctx.getSource().sendSuccess(() -> Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(running ? "ONLINE" : "OFFLINE")
                        .withStyle(running ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD)), false);

        ctx.getSource().sendSuccess(() -> Component.literal("Port: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(config.getPort())).withStyle(ChatFormatting.YELLOW)), false);

        ctx.getSource().sendSuccess(() -> Component.literal("Auth Required: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(config.isRequireAuth())).withStyle(config.isRequireAuth() ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)), false);

        ctx.getSource().sendSuccess(() -> Component.literal("Read-Only Mode: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(config.isReadOnlyMode())).withStyle(config.isReadOnlyMode() ? ChatFormatting.RED : ChatFormatting.GREEN)), false);

        ctx.getSource().sendSuccess(() -> Component.literal("Tools: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(server.getToolRegistry().getAllTools().size())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | Resources: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(server.getResourceRegistry().getAllResources().size())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | Prompts: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(server.getPromptRegistry().getAllPrompts().size())).withStyle(ChatFormatting.WHITE)), false);

        String mcpUrl = "http://" + config.getHost() + ":" + config.getPort() + "/mcp";
        ctx.getSource().sendSuccess(() -> Component.literal("MCP Endpoint: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(mcpUrl).withStyle(ChatFormatting.GREEN)), false);

        return 1;
    }

    private static int startServer(CommandContext<CommandSourceStack> ctx, McpServer server) {
        if (server.isRunning()) {
            ctx.getSource().sendFailure(TextFormatter.warning("MCP Server is already running on port " + server.getConfig().getPort()));
            return 0;
        }
        server.start();
        ctx.getSource().sendSuccess(() -> TextFormatter.success("Started MCP Server on port " + server.getConfig().getPort()), true);
        return 1;
    }

    private static int stopServer(CommandContext<CommandSourceStack> ctx, McpServer server) {
        if (!server.isRunning()) {
            ctx.getSource().sendFailure(TextFormatter.warning("MCP Server is not running."));
            return 0;
        }
        server.stop();
        ctx.getSource().sendSuccess(() -> TextFormatter.warning("Stopped MCP Server."), true);
        return 1;
    }

    private static int restartServer(CommandContext<CommandSourceStack> ctx, McpServer server) {
        server.restart();
        ctx.getSource().sendSuccess(() -> TextFormatter.success("Restarted MCP Server on port " + server.getConfig().getPort()), true);
        return 1;
    }

    private static int showToken(CommandContext<CommandSourceStack> ctx, McpServer server) {
        String token = server.getConfig().getAuthToken();
        if (token == null || token.isBlank()) {
            ctx.getSource().sendSuccess(() -> TextFormatter.info("No auth token configured (auth is disabled). Use '/mcp token generate' to create one."), false);
        } else {
            ctx.getSource().sendSuccess(() -> TextFormatter.info("Current MCP Token: " + token), false);
        }
        return 1;
    }

    private static int generateToken(CommandContext<CommandSourceStack> ctx, McpServer server) {
        String newToken = server.getConfigManager().generateNewToken();
        ctx.getSource().sendSuccess(() -> TextFormatter.success("Generated new MCP Auth Token: " + newToken), true);
        return 1;
    }

    private static int setPort(CommandContext<CommandSourceStack> ctx, McpServer server, int port) {
        server.getConfig().setPort(port);
        server.getConfigManager().save();
        ctx.getSource().sendSuccess(() -> TextFormatter.success("Set MCP port to " + port + ". Restart server with '/mcp restart' to apply."), true);
        return 1;
    }

    private static int setRequireAuth(CommandContext<CommandSourceStack> ctx, McpServer server, boolean requireAuth) {
        server.getConfig().setRequireAuth(requireAuth);
        server.getConfigManager().save();
        ctx.getSource().sendSuccess(() -> TextFormatter.success("Set requireAuth to " + requireAuth), true);
        return 1;
    }

    private static int setBroadcast(CommandContext<CommandSourceStack> ctx, McpServer server, boolean broadcast) {
        server.getConfig().setBroadcastToChat(broadcast);
        server.getConfigManager().save();
        ctx.getSource().sendSuccess(() -> TextFormatter.success("Set broadcastToChat to " + broadcast), true);
        return 1;
    }

    private static int setReadOnly(CommandContext<CommandSourceStack> ctx, McpServer server, boolean readOnly) {
        server.getConfig().setReadOnlyMode(readOnly);
        server.getConfigManager().save();
        ctx.getSource().sendSuccess(() -> TextFormatter.success("Set readOnlyMode to " + readOnly), true);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("=== Minecraft MCP Commands ===").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/mcp status - View server status and port").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/mcp start - Start MCP server").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/mcp stop - Stop MCP server").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/mcp restart - Restart MCP server").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/mcp token [show|generate] - Manage auth tokens").withStyle(ChatFormatting.GRAY), false);
        ctx.getSource().sendSuccess(() -> Component.literal("/mcp config [key] [value] - Adjust settings").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
