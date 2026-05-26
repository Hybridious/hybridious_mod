package dev.hybridious;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public class CoordsPoster {
    private static final URI ENDPOINT = URI.create("https://leonetic.dev");
    private static final long POST_INTERVAL_MS = 1_000L;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private long lastPostAt;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastPostAt < POST_INTERVAL_MS) return;

        lastPostAt = now;

        String payload = String.format(
            Locale.ROOT,
            "{\"x\":%.3f,\"y\":%.3f,\"z\":%.3f}",
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ()
        );

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .exceptionally(error -> {
                Hybridious.LOG.debug("Failed to post coordinates.", error);
                return null;
            });
    }
}
