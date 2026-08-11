package com.mrpotato.minecraftmcptools.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class TextFormatter {
    public static final String PREFIX_STR = "[MCP] ";

    private TextFormatter() {}

    public static Component prefix() {
        return Component.literal("[MCP] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    public static Component info(String message) {
        return Component.empty()
                .append(prefix())
                .append(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    public static Component success(String message) {
        return Component.empty()
                .append(prefix())
                .append(Component.literal(message).withStyle(ChatFormatting.GREEN));
    }

    public static Component warning(String message) {
        return Component.empty()
                .append(prefix())
                .append(Component.literal(message).withStyle(ChatFormatting.YELLOW));
    }

    public static Component error(String message) {
        return Component.empty()
                .append(prefix())
                .append(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    public static Component toolExecution(String clientName, String toolName) {
        return Component.empty()
                .append(prefix())
                .append(Component.literal(clientName).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" executed tool ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(toolName).withStyle(ChatFormatting.YELLOW));
    }
}
