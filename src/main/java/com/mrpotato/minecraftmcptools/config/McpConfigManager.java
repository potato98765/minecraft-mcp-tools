package com.mrpotato.minecraftmcptools.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;

public class McpConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MinecraftMCP/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "minecraft-mcp.json";

    private final Path configPath;
    private McpConfig config;

    public McpConfigManager() {
        this(resolveConfigDir().resolve(CONFIG_FILE_NAME));
    }

    public McpConfigManager(Path configPath) {
        this.configPath = configPath;
        this.config = load();
    }

    private static Path resolveConfigDir() {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            if (loader != null) {
                return loader.getConfigDir();
            }
        } catch (Throwable ignored) {}
        return Paths.get("config");
    }

    public McpConfig getConfig() {
        if (config == null) {
            config = load();
        }
        return config;
    }

    public synchronized McpConfig load() {
        File file = configPath.toFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                McpConfig loaded = GSON.fromJson(reader, McpConfig.class);
                if (loaded != null) {
                    this.config = loaded;
                    return loaded;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load minecraft-mcp config file, using defaults", e);
            }
        }

        this.config = new McpConfig();
        save();
        return config;
    }

    public synchronized void save() {
        try {
            File file = configPath.toFile();
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save minecraft-mcp config file", e);
        }
    }

    public String generateNewToken() {
        byte[] randomBytes = new byte[24];
        new SecureRandom().nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        config.setAuthToken(token);
        config.setRequireAuth(true);
        save();
        return token;
    }
}
