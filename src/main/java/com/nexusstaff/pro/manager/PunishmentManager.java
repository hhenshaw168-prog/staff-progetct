package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PunishmentManager {
    private final NexusStaffPlugin plugin;
    private final Map<UUID, Instant> mutedUntil = new ConcurrentHashMap<>();

    public PunishmentManager(NexusStaffPlugin plugin) { this.plugin = plugin; }

    public void warn(Player staff, Player target, String reason) {
        target.sendMessage(plugin.messages().raw("<red>You have been warned by <staff>: <reason>", Map.of(
                "staff", staff.getName(), "reason", reason)));
        record(staff, target, "WARN", reason, null);
        plugin.discord().alert("Player warned", target.getName() + " warned by " + staff.getName() + ": " + reason, 16763904);
    }

    public void mute(Player staff, Player target, long seconds, String reason) {
        Instant expiry = Instant.now().plusSeconds(seconds);
        mutedUntil.put(target.getUniqueId(), expiry);
        target.sendMessage(plugin.messages().raw("<red>You have been muted for <duration>: <reason>", Map.of(
                "duration", formatDuration(seconds), "reason", reason)));
        record(staff, target, "MUTE", reason, expiry);
        plugin.discord().alert("Player muted", target.getName() + " muted by " + staff.getName() + ": " + reason, 16753920);
    }

    public void kick(Player staff, Player target, String reason) {
        record(staff, target, "KICK", reason, null);
        plugin.discord().alert("Player kicked", target.getName() + " kicked by " + staff.getName() + ": " + reason, 15158332);
        target.kick(plugin.messages().raw("<red>Kicked by <staff>: <reason>", Map.of("staff", staff.getName(), "reason", reason)));
    }

    public void ban(Player staff, Player target, String reason) {
        record(staff, target, "BAN", reason, null);
        plugin.discord().alert("Player banned", target.getName() + " banned by " + staff.getName() + ": " + reason, 10038562);
        Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), reason, null, staff.getName());
        target.kick(plugin.messages().raw("<red>Banned: <reason>", Map.of("reason", reason)));
    }

    public boolean isMuted(Player player) {
        Instant expiry = mutedUntil.get(player.getUniqueId());
        if (expiry == null) return false;
        if (expiry.isAfter(Instant.now())) return true;
        mutedUntil.remove(player.getUniqueId());
        return false;
    }

    private void record(Player staff, Player target, String type, String reason, Instant expiry) {
        plugin.storage().executeAsync(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO punishments (uuid, staff_uuid, type, reason, expires_at) VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, target.getUniqueId().toString());
                statement.setString(2, staff.getUniqueId().toString());
                statement.setString(3, type);
                statement.setString(4, reason);
                if (expiry == null) statement.setTimestamp(5, null);
                else statement.setTimestamp(5, Timestamp.from(expiry));
                statement.executeUpdate();
            }
        });
    }

    private String formatDuration(long seconds) {
        if (seconds % 3600 == 0) return (seconds / 3600) + " hour(s)";
        return seconds + " second(s)";
    }
}