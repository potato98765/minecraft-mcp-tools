package com.mrpotato.minecraftmcptools.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class McpStdioServer {

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 25560;

        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ignored) {}
            } else if ("--host".equals(args[i]) && i + 1 < args.length) {
                host = args[++i];
            }
        }

        String targetUrl = "http://" + host + ":" + port + "/mcp";
        HttpClient httpClient = HttpClient.newHttpClient();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(targetUrl))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(line, StandardCharsets.UTF_8))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() == 200) {
                        System.out.println(response.body());
                        System.out.flush();
                    } else {
                        JsonObject err = new JsonObject();
                        err.addProperty("jsonrpc", "2.0");
                        JsonObject errObj = new JsonObject();
                        errObj.addProperty("code", -32603);
                        errObj.addProperty("message", "Minecraft MCP HTTP bridge error: status " + response.statusCode());
                        err.add("error", errObj);
                        System.out.println(err);
                        System.out.flush();
                    }
                } catch (Exception e) {
                    JsonObject err = new JsonObject();
                    err.addProperty("jsonrpc", "2.0");
                    JsonObject errObj = new JsonObject();
                    errObj.addProperty("code", -32603);
                    errObj.addProperty("message", "Minecraft server is not reachable at " + targetUrl + ". Is Minecraft running with the MCP mod?");
                    err.add("error", errObj);
                    System.out.println(err);
                    System.out.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("McpStdioServer exited: " + e.getMessage());
        }
    }
}
