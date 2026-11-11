package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Stable intake boundary for optional GrimAC/Vulcan adapters. */
public final class AntiCheatHooks {
    private final NexusStaffPlugin plugin;
    private final Map<UUID, Double> threatLevels = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.concurrent.atomic.LongAdder> flagCounts = new ConcurrentHashMap<>();
    private final boolean grimPresent;
    private final boolean vulcanPresent;

    public AntiCheatHooks(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        grimPresent = Bukkit.getPluginManager().getPlugin("GrimAC") != null;
        vulcanPresent = Bukkit.getPluginManager().getPlugin("Vulcan") != null;
    }

    public boolean isGrimPresent() { return grimPresent; }
    public boolean isVulcanPresent() { return vulcanPresent; }
    public double threat(UUID playerId) { return threatLevels.getOrDefault(playerId, 0.0); }
    public long flagCount(UUID playerId) {
        var count = flagCounts.get(playerId);
        return count == null ? 0 : count.sum();
    }
    public long trackedThreatCount() { return threatLevels.values().stream().filter(level -> level > 0).count(); }

    /** Called by a version-specific adapter when a supported anti-cheat flag event arrives. */
    public void publishFlag(UUID playerId, String source, String check, int count, double violationLevel, String detail) {
        threatLevels.merge(playerId, violationLevel, Math::max);
        flagCounts.computeIfAbsent(playerId, ignored -> new java.util.concurrent.atomic.LongAdder()).increment();
        plugin.storage().executeAsync(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO ac_flags (player_uuid, source, check_name, vl) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, playerId.toString());
                statement.setString(2, source);
                statement.setString(3, check);
                statement.setDouble(4, violationLevel);
                statement.executeUpdate();
            }
        });
        if (!plugin.getConfig().getBoolean("anti-cheat.alerts-enabled", true)) return;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        String template = plugin.messages().text("ac-alert", "<red>AC Alert: <player> <check> <vl></red>");
        var alert = plugin.messages().raw(template, Map.of(
                "player", player.getName(), "check", source + "/" + check,
                "count", Integer.toString(count), "vl", Double.toString(violationLevel),
                "detail", detail == null ? "" : detail));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("nexusstaff.alerts")) staff.sendMessage(alert);
        }
        double notifyAt = plugin.getConfig().getDouble("anti-cheat.auto-notify-vl", 50);
        double freezeAt = plugin.getConfig().getDouble("anti-cheat.auto-freeze-vl", 0);
        if (violationLevel >= freezeAt && freezeAt > 0 && !plugin.freeze().isFrozen(player)) plugin.freeze().toggle(player);
        if (violationLevel >= notifyAt) plugin.getLogger().warning("High anti-cheat VL: " + player.getName() + " " + source + "/" + check + " VL=" + violationLevel);
        if (violationLevel >= notifyAt) plugin.discord().alert("High anti-cheat violation",
            player.getName() + " flagged " + source + "/" + check + " at VL " + violationLevel, 16711680);
    }
}