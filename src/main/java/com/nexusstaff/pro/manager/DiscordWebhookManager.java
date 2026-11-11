package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class DiscordWebhookManager {
    private final NexusStaffPlugin plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public DiscordWebhookManager(NexusStaffPlugin plugin) { this.plugin = plugin; }

    public void alert(String title, String description, int color) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) return;
        String webhook = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhook.isBlank()) return;
        String json = "{\"embeds\":[{\"title\":\"" + escape(title) + "\",\"description\":\""
                + escape(description) + "\",\"color\":" + color + "}]}";
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(webhook)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Discord webhook URL is invalid.");
            return;
        }
        CompletableFuture<HttpResponse<Void>> response = client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        response.thenAccept(result -> {
            if (result.statusCode() < 200 || result.statusCode() >= 300) {
                plugin.getLogger().warning("Discord webhook returned HTTP " + result.statusCode());
            }
        }).exceptionally(error -> {
            plugin.getLogger().log(Level.FINE, "Discord webhook delivery failed", error);
            return null;
        });
    }

    private String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> { if (character >= 0x20) escaped.append(character); }
            }
        }
        return escaped.toString();
    }
}