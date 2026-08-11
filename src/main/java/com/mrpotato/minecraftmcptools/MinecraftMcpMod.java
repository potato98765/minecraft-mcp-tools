package com.mrpotato.minecraftmcptools;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.mrpotato.minecraftmcptools.commands.McpCommand;
import com.mrpotato.minecraftmcptools.config.McpConfigManager;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import com.mrpotato.minecraftmcptools.server.McpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinecraftMcpMod implements ModInitializer {
    public static final String MOD_ID = "minecraft-mcp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftMcpMod instance;
    private McpConfigManager configManager;
    private McpServer mcpServer;

    public static MinecraftMcpMod getInstance() {
        return instance;
    }

    public McpServer getMcpServer() {
        return mcpServer;
    }

    public McpConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("Initializing Minecraft Model Context Protocol (MCP) mod for Minecraft 26.2 (Java 25)...");

        this.configManager = new McpConfigManager();
        this.mcpServer = new McpServer(configManager);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Minecraft Server started, attaching MCP context...");
            MinecraftContext ctx = new MinecraftContext(server);
            mcpServer.setContext(ctx);

            if (configManager.getConfig().isEnabled()) {
                mcpServer.start();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Minecraft Server stopping, shutting down MCP server...");
            if (mcpServer != null) {
                mcpServer.stop();
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            McpCommand.register(dispatcher, mcpServer);
        });

        LOGGER.info("Minecraft MCP mod successfully initialized!");
    }
}
