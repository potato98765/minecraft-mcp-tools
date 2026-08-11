package com.mrpotato.minecraftmcptools.protocol;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class MinecraftContext {
    private final MinecraftServer server;

    public MinecraftContext(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public boolean isAvailable() {
        return server != null && server.isRunning();
    }

    public ServerLevel getLevel() {
        if (server == null) return null;
        return server.overworld();
    }

    public ServerPlayer getPlayer() {
        if (server == null) return null;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.get(0);
    }

    public ServerPlayer getPlayer(String nameOrUuid) {
        if (server == null) return null;
        if (nameOrUuid == null || nameOrUuid.isBlank()) {
            return getPlayer();
        }

        try {
            UUID uuid = UUID.fromString(nameOrUuid);
            ServerPlayer byUuid = server.getPlayerList().getPlayer(uuid);
            if (byUuid != null) return byUuid;
        } catch (IllegalArgumentException ignored) {}

        return server.getPlayerList().getPlayerByName(nameOrUuid);
    }

    public List<ServerPlayer> getAllPlayers() {
        if (server == null) return List.of();
        return List.copyOf(server.getPlayerList().getPlayers());
    }

    public <T> CompletableFuture<T> runOnServer(Supplier<T> supplier) {
        if (server == null || !server.isRunning()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Minecraft server is not running"));
        }

        if (server.isSameThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        server.execute(() -> {
            try {
                T result = supplier.get();
                future.complete(result);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public CompletableFuture<Void> runOnServer(Runnable runnable) {
        return runOnServer(() -> {
            runnable.run();
            return null;
        });
    }

    public void broadcastMessage(Component message) {
        if (server == null) return;
        runOnServer(() -> {
            server.getPlayerList().broadcastSystemMessage(message, false);
        });
    }
}
