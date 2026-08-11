package com.mrpotato.minecraftmcptools.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class McpSession {
    private final String id;
    private String clientName = "Unknown Client";
    private String clientVersion = "1.0.0";
    private String protocolVersion = "2024-11-05";
    private final long createdAt = System.currentTimeMillis();
    private volatile long lastActivity = System.currentTimeMillis();
    private volatile boolean active = true;

    private OutputStream sseOutputStream;
    private final BlockingQueue<String> eventQueue = new LinkedBlockingQueue<>();

    public McpSession(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientInfo(String name, String version) {
        if (name != null && !name.isBlank()) this.clientName = name;
        if (version != null && !version.isBlank()) this.clientVersion = version;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public boolean isActive() {
        return active;
    }

    public void close() {
        this.active = false;
        if (sseOutputStream != null) {
            try {
                sseOutputStream.close();
            } catch (IOException ignored) {}
        }
    }

    public synchronized void attachSse(OutputStream out) {
        this.sseOutputStream = out;
        this.active = true;
    }

    public synchronized void sendSseEvent(String eventName, String data) {
        if (sseOutputStream == null || !active) return;
        try {
            StringBuilder sb = new StringBuilder();
            if (eventName != null && !eventName.isBlank()) {
                sb.append("event: ").append(eventName).append("\n");
            }

            String[] lines = data.split("\r?\n");
            for (String line : lines) {
                sb.append("data: ").append(line).append("\n");
            }
            sb.append("\n");

            sseOutputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            sseOutputStream.flush();
            updateActivity();
        } catch (IOException e) {
            close();
        }
    }
}
