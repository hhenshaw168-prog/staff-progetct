package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ReportManager {
    private final NexusStaffPlugin plugin;
    private final Map<UUID, Report> openReports = new ConcurrentHashMap<>();

    public ReportManager(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        plugin.storage().executeAsync(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT report_key, target_uuid, reporter_uuid, reason, created_at FROM reports WHERE status = 'OPEN' AND report_key IS NOT NULL");
                 var result = statement.executeQuery()) {
                var loaded = new java.util.ArrayList<Report>();
                while (result.next()) {
                    Timestamp timestamp = result.getTimestamp("created_at");
                    loaded.add(new Report(UUID.fromString(result.getString("report_key")),
                            UUID.fromString(result.getString("reporter_uuid")), UUID.fromString(result.getString("target_uuid")),
                            result.getString("reason"), timestamp == null ? Instant.EPOCH : timestamp.toInstant()));
                }
                Bukkit.getScheduler().runTask(plugin, () -> loaded.forEach(report -> openReports.put(report.id(), report)));
            }
        });
    }

    public Report submit(Player reporter, Player target, String reason) {
        Report report = new Report(UUID.randomUUID(), reporter.getUniqueId(), target.getUniqueId(), reason, Instant.now());
        openReports.put(report.id(), report);
        plugin.storage().executeAsync(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO reports (report_key, target_uuid, reporter_uuid, reason, status, created_at) VALUES (?, ?, ?, ?, 'OPEN', ?)");) {
                statement.setString(1, report.id().toString());
                statement.setString(2, report.targetId().toString());
                statement.setString(3, report.reporterId().toString());
                statement.setString(4, report.reason());
                statement.setTimestamp(5, Timestamp.from(report.createdAt()));
                statement.executeUpdate();
            }
        });
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("nexusstaff.dashboard")) {
                staff.sendMessage(plugin.messages().raw("<yellow>New report: <player> reported <target>: <reason>",
                        Map.of("player", reporter.getName(), "target", target.getName(), "reason", reason)));
            }
        }
        return report;
    }

    public List<Report> openReports() {
        return openReports.values().stream().sorted(Comparator.comparing(Report::createdAt)).toList();
    }

    public Report resolve(UUID reportId) {
        Report report = openReports.remove(reportId);
        if (report != null) {
            plugin.storage().executeAsync(connection -> {
                try (var statement = connection.prepareStatement("UPDATE reports SET status = 'RESOLVED' WHERE report_key = ?")) {
                    statement.setString(1, reportId.toString());
                    statement.executeUpdate();
                }
            });
        }
        return report;
    }

    public record Report(UUID id, UUID reporterId, UUID targetId, String reason, Instant createdAt) { }
}