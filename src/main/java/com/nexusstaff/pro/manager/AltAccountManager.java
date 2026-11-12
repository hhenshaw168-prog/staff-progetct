package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Stores a keyed IP digest instead of a recoverable player IP address. */
public final class AltAccountManager {
    private final NexusStaffPlugin plugin;
    private final byte[] secret;

    public AltAccountManager(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        String encoded = plugin.getConfig().getString("privacy.ip-hash-secret", "");
        if (encoded.isBlank()) {
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            encoded = Base64.getEncoder().encodeToString(generated);
            plugin.getConfig().set("privacy.ip-hash-secret", encoded);
            plugin.saveConfig();
        }
        secret = Base64.getDecoder().decode(encoded);
    }

    public void remember(Player player) {
        var address = player.getAddress();
        if (address == null || address.getAddress() == null) return;
        String digest = digest(address.getAddress().getHostAddress());
        plugin.storage().executeAsync(connection -> {
            boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("sqlite");
            String sql = sqlite
                    ? "INSERT INTO player_ip_hashes (uuid, ip_hash, last_seen) VALUES (?, ?, ?) ON CONFLICT(uuid, ip_hash) DO UPDATE SET last_seen = excluded.last_seen"
                    : "INSERT INTO player_ip_hashes (uuid, ip_hash, last_seen) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE last_seen = VALUES(last_seen)";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, digest);
                statement.setTimestamp(3, Timestamp.from(Instant.now()));
                statement.executeUpdate();
            }
        });
    }

    public void findAlts(Player target, Consumer<List<UUID>> callback) {
        var address = target.getAddress();
        if (address == null || address.getAddress() == null) {
            callback.accept(List.of());
            return;
        }
        String hash = digest(address.getAddress().getHostAddress());
        UUID targetId = target.getUniqueId();
        plugin.storage().supplyAsync(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT uuid FROM player_ip_hashes WHERE ip_hash = ? AND uuid <> ? ORDER BY last_seen DESC")) {
                statement.setString(1, hash);
                statement.setString(2, targetId.toString());
                try (var result = statement.executeQuery()) {
                    java.util.ArrayList<UUID> matches = new java.util.ArrayList<>();
                    while (result.next()) matches.add(UUID.fromString(result.getString(1)));
                    return List.copyOf(matches);
                }
            }
        }).whenComplete((matches, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.getLogger().warning("Alt account lookup failed: " + error.getMessage());
                callback.accept(List.of());
            } else callback.accept(matches);
        }));
    }

    private String digest(String address) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(address.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }
}