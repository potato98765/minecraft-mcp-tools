package com.mrpotato.minecraftmcptools.config;

public class McpConfig {
    private boolean enabled = true;
    private int port = 25560;
    private String host = "127.0.0.1";
    private boolean requireAuth = false;
    private String authToken = "";
    private boolean allowCommandExecution = true;
    private boolean allowWorldModification = true;
    private boolean broadcastToChat = true;
    private boolean readOnlyMode = false;
    private int maxBlocksPerOperation = 32768;

    public McpConfig() {}

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public boolean isRequireAuth() {
        return requireAuth;
    }

    public void setRequireAuth(boolean requireAuth) {
        this.requireAuth = requireAuth;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public boolean isAllowCommandExecution() {
        return allowCommandExecution && !readOnlyMode;
    }

    public void setAllowCommandExecution(boolean allowCommandExecution) {
        this.allowCommandExecution = allowCommandExecution;
    }

    public boolean isAllowWorldModification() {
        return allowWorldModification && !readOnlyMode;
    }

    public void setAllowWorldModification(boolean allowWorldModification) {
        this.allowWorldModification = allowWorldModification;
    }

    public boolean isBroadcastToChat() {
        return broadcastToChat;
    }

    public void setBroadcastToChat(boolean broadcastToChat) {
        this.broadcastToChat = broadcastToChat;
    }

    public boolean isReadOnlyMode() {
        return readOnlyMode;
    }

    public void setReadOnlyMode(boolean readOnlyMode) {
        this.readOnlyMode = readOnlyMode;
    }

    public int getMaxBlocksPerOperation() {
        return maxBlocksPerOperation;
    }

    public void setMaxBlocksPerOperation(int maxBlocksPerOperation) {
        this.maxBlocksPerOperation = maxBlocksPerOperation;
    }
}
