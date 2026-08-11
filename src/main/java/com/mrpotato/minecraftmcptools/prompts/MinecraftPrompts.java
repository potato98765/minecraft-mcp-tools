package com.mrpotato.minecraftmcptools.prompts;

import com.google.gson.JsonObject;
import com.mrpotato.minecraftmcptools.protocol.McpPrompt;
import com.mrpotato.minecraftmcptools.protocol.MinecraftContext;
import com.mrpotato.minecraftmcptools.resources.MinecraftResources;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class MinecraftPrompts {

    private MinecraftPrompts() {}

    public static List<McpPrompt> registerAll() {
        return List.of(
                new SurvivalAdvisorPrompt(),
                new ArchitectBuilderPrompt(),
                new ThreatAnalyzerPrompt()
        );
    }

    public static class SurvivalAdvisorPrompt implements McpPrompt {
        @Override
        public String name() {
            return "survival-advisor";
        }

        @Override
        public String description() {
            return "Analyzes current player vitals, inventory, time of day, and surroundings to suggest immediate survival priorities.";
        }

        @Override
        public List<PromptArgument> arguments() {
            return List.of(
                    new PromptArgument("focus", "Specific focus: 'food', 'shelter', 'mining', 'combat'", false)
            );
        }

        @Override
        public CompletableFuture<List<PromptMessage>> generate(JsonObject args, MinecraftContext context) {
            MinecraftResources.PlayerStatusResource statusRes = new MinecraftResources.PlayerStatusResource();
            MinecraftResources.WorldOverviewResource worldRes = new MinecraftResources.WorldOverviewResource();
            MinecraftResources.SurroundingsResource surrRes = new MinecraftResources.SurroundingsResource();

            return CompletableFuture.allOf(
                    statusRes.read(context),
                    worldRes.read(context),
                    surrRes.read(context)
            ).thenApply(v -> {
                String statusJson = statusRes.read(context).join();
                String worldJson = worldRes.read(context).join();
                String surrJson = surrRes.read(context).join();

                String promptText = """
                        You are an expert Minecraft survival advisor connected live to the player's world via MCP.

                        Current Player Status:
                        %s

                        World Overview:
                        %s

                        Surroundings:
                        %s

                        Provide a clear, prioritized list of recommendations for what the player should do next.
                        """.formatted(statusJson, worldJson, surrJson);

                return List.of(new PromptMessage("user", promptText));
            });
        }
    }

    public static class ArchitectBuilderPrompt implements McpPrompt {
        @Override
        public String name() {
            return "architect-builder";
        }

        @Override
        public String description() {
            return "Assists in designing and placing complex architectural builds using MCP construction and block tools.";
        }

        @Override
        public List<PromptArgument> arguments() {
            return List.of(
                    new PromptArgument("style", "Architectural style (e.g. 'medieval', 'modern', 'steampunk', 'nether')", false)
            );
        }

        @Override
        public CompletableFuture<List<PromptMessage>> generate(JsonObject args, MinecraftContext context) {
            MinecraftResources.PlayerStatusResource statusRes = new MinecraftResources.PlayerStatusResource();

            return statusRes.read(context).thenApply(statusJson -> {
                String promptText = """
                        You are a master Minecraft architect and builder connected via MCP.
                        You have access to tools like `set_block`, `fill_blocks`, `build_schematic`, `build_sphere`, and `build_cylinder`.

                        Current Player Location:
                        %s

                        Help the player design and construct an impressive structure. Use tool calls to preview or place blocks.
                        """.formatted(statusJson);

                return List.of(new PromptMessage("user", promptText));
            });
        }
    }

    public static class ThreatAnalyzerPrompt implements McpPrompt {
        @Override
        public String name() {
            return "threat-analyzer";
        }

        @Override
        public String description() {
            return "Scans the immediate area for hostile entities, low light levels, and environmental hazards.";
        }

        @Override
        public List<PromptArgument> arguments() {
            return List.of();
        }

        @Override
        public CompletableFuture<List<PromptMessage>> generate(JsonObject args, MinecraftContext context) {
            MinecraftResources.SurroundingsResource surrRes = new MinecraftResources.SurroundingsResource();

            return surrRes.read(context).thenApply(surrJson -> {
                String promptText = """
                        You are a tactical combat assistant for Minecraft.
                        Evaluate the following scan of the player's surroundings for hostile mobs, traps, lava hazards, and ambush risks:

                        %s

                        Alert the player to immediate dangers and suggest evasive or offensive tactics.
                        """.formatted(surrJson);

                return List.of(new PromptMessage("user", promptText));
            });
        }
    }
}
