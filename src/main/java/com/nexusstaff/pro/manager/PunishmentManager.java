package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.Date;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PunishmentManager {
    private final NexusStaffPlugin plugin;
    private final Map<UUID, Instant> mutedUntil = new ConcurrentHashMap<>();
    private final Set<UUID> permanentlyMuted = ConcurrentHashMap.newKeySet();
    private final PunishmentMatrix matrix;

    public PunishmentManager(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        matrix = new PunishmentMatrix(plugin);
        restoreActiveMutes();
    }

    private void restoreActiveMutes() {
        plugin.storage().executeAsync(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT p.uuid, p.type, p.expires_at FROM punishments p WHERE p.type IN ('MUTE', 'PERM_MUTE') AND NOT EXISTS (SELECT 1 FROM punishments newer WHERE newer.uuid = p.uuid AND newer.id > p.id AND newer.type IN ('MUTE', 'PERM_MUTE'))");
                 var result = statement.executeQuery()) {
                while (result.next()) {
                    UUID playerId = UUID.fromString(result.getString("uuid"));
                    String type = result.getString("type");
                    Timestamp timestamp = result.getTimestamp("expires_at");
                    if (type.equals("PERM_MUTE")) permanentlyMuted.add(playerId);
                    else if (timestamp != null && timestamp.toInstant().isAfter(Instant.now())) mutedUntil.put(playerId, timestamp.toInstant());
                }
            }
        });
    }

    public PunishmentMatrix matrix() { return matrix; }

    public void applyMatrix(CommandSender staff, Player target, PunishmentMatrix.Category category, String reason) {
        applyMatrix(staff, target, category, reason, false);
    }

    public void applyMatrix(CommandSender staff, Player target, PunishmentMatrix.Category category, String reason, boolean ipBan) {
        UUID staffId = staff instanceof Player player ? player.getUniqueId() : new UUID(0, 0);
        plugin.storage().supplyAsync(connection -> applyMatrixTransaction(connection, staffId, target.getUniqueId(), category, reason))
                .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not apply escalation for " + category.id(), error);
                        staff.sendMessage(plugin.messages().raw("<red>Punishment failed. Check server logs.</red>", Map.of()));
                        return;
                    }
                    applyDecision(staff, target, category, reason, result, ipBan);
                }));
    }

    private MatrixResult applyMatrixTransaction(Connection connection, UUID staffId, UUID targetId,
                                               PunishmentMatrix.Category category, String reason) throws Exception {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String upsert = connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("sqlite")
                    ? "INSERT INTO offense_counters (uuid, category, offense_count) VALUES (?, ?, 1) ON CONFLICT(uuid, category) DO UPDATE SET offense_count = offense_count + 1"
                    : "INSERT INTO offense_counters (uuid, category, offense_count) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE offense_count = offense_count + 1";
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, targetId.toString());
                statement.setString(2, category.id());
                statement.executeUpdate();
            }
            int offense;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT offense_count FROM offense_counters WHERE uuid = ? AND category = ?")) {
                statement.setString(1, targetId.toString());
                statement.setString(2, category.id());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new SQLException("Offense counter row was not created");
                    offense = result.getInt(1);
                }
            }
            PunishmentMatrix.Decision decision = matrix.decision(category, offense);
            Instant expiry = decision.duration() == null ? null : Instant.now().plus(decision.duration());
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO punishments (uuid, staff_uuid, type, reason, created_at, expires_at, category, offense_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, targetId.toString());
                statement.setString(2, staffId.toString());
                statement.setString(3, decision.type());
                statement.setString(4, reason);
                statement.setTimestamp(5, Timestamp.from(Instant.now()));
                if (expiry == null) statement.setTimestamp(6, null); else statement.setTimestamp(6, Timestamp.from(expiry));
                statement.setString(7, category.id());
                statement.setInt(8, offense);
                statement.executeUpdate();
            }
            connection.commit();
            return new MatrixResult(offense, decision, expiry);
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private void applyDecision(CommandSender staff, Player target, PunishmentMatrix.Category category,
                               String reason, MatrixResult result, boolean ipBan) {
        if (!target.isOnline()) return;
        var decision = result.decision();
        String staffName = staff.getName();
        String fullReason = category.displayName() + " (offense " + result.offense() + "): " + reason;
        switch (decision.type()) {
            case "MUTE" -> {
                mutedUntil.put(target.getUniqueId(), result.expiresAt());
                target.sendMessage(plugin.messages().raw("<red>You have been muted for <duration>: <reason>", Map.of(
                        "duration", displayDuration(decision), "reason", fullReason)));
                plugin.discord().alert("Player muted", target.getName() + " muted by " + staffName + ": " + fullReason, 16753920);
            }
            case "PERM_MUTE" -> {
                permanentlyMuted.add(target.getUniqueId());
                target.sendMessage(plugin.messages().raw("<red>You have been permanently muted: <reason>", Map.of("reason", fullReason)));
                plugin.discord().alert("Permanent mute", target.getName() + " muted by " + staffName + ": " + fullReason, 16753920);
            }
            case "BAN", "PERM_BAN" -> {
                BanList.Type banType = ipBan ? BanList.Type.IP : BanList.Type.NAME;
                String banTarget = target.getName();
                if (ipBan) {
                    var address = target.getAddress();
                    if (address == null || address.getAddress() == null) {
                        staff.sendMessage(plugin.messages().raw("<red>Cannot IP-ban: player IP is unavailable.</red>", Map.of()));
                        return;
                    }
                    banTarget = address.getAddress().getHostAddress();
                }
                Bukkit.getBanList(banType).addBan(banTarget, fullReason,
                        result.expiresAt() == null ? null : Date.from(result.expiresAt()), staffName);
                target.kick(plugin.messages().raw("<red>Banned: <reason>", Map.of("reason", fullReason)));
                plugin.discord().alert("Player banned", target.getName() + " banned by " + staffName + ": " + fullReason, 10038562);
            }
            case "BLACKLIST" -> {
                var address = target.getAddress();
                if (address == null || address.getAddress() == null) {
                    staff.sendMessage(plugin.messages().raw("<red>Cannot blacklist: player IP is unavailable.</red>", Map.of()));
                    return;
                }
                String ip = address.getAddress().getHostAddress();
                Bukkit.getBanList(BanList.Type.IP).addBan(ip, fullReason, null, staffName);
                plugin.network().broadcastNetworkIpBan(ip, fullReason, staffName);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    var onlineAddress = online.getAddress();
                    if (onlineAddress != null && onlineAddress.getAddress() != null
                            && onlineAddress.getAddress().getHostAddress().equalsIgnoreCase(ip)) {
                        online.kick(plugin.messages().raw("<dark_red>Network blacklist: <reason>", Map.of("reason", fullReason)));
                    }
                }
                plugin.discord().alert("Network IP blacklist", target.getName() + " blacklisted by " + staffName + ": " + fullReason, 10038562);
            }
            default -> staff.sendMessage(plugin.messages().raw("<red>Unknown matrix outcome: <outcome></red>", Map.of("outcome", decision.configuredOutcome())));
        }
        staff.sendMessage(plugin.messages().raw("<green>Applied <outcome> for <category>, offense <count>.</green>", Map.of(
                "outcome", decision.configuredOutcome(), "category", category.displayName(), "count", Integer.toString(result.offense()))));
    }

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
        if (permanentlyMuted.contains(player.getUniqueId())) return true;
        Instant expiry = mutedUntil.get(player.getUniqueId());
        if (expiry == null) return false;
        if (expiry.isAfter(Instant.now())) return true;
        mutedUntil.remove(player.getUniqueId());
        return false;
    }

    private String displayDuration(PunishmentMatrix.Decision decision) {
        Duration duration = decision.duration();
        if (duration == null) return "permanent";
        if (duration.toDays() > 0) return duration.toDays() + " day(s)";
        if (duration.toHours() > 0) return duration.toHours() + " hour(s)";
        if (duration.toMinutes() > 0) return duration.toMinutes() + " minute(s)";
        return duration.toSeconds() + " second(s)";
    }

    private record MatrixResult(int offense, PunishmentMatrix.Decision decision, Instant expiresAt) { }

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