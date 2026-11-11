package com.nexusstaff.pro.gui.menus;

import com.nexusstaff.pro.NexusStaffPlugin;
import org.bukkit.Bukkit;

public final class DashboardMetrics {
    private final NexusStaffPlugin plugin;

    public DashboardMetrics(NexusStaffPlugin plugin) { this.plugin = plugin; }

    public Snapshot sample() {
        double[] tps = Bukkit.getTPS();
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long maximum = runtime.maxMemory();
        int chunks = Bukkit.getWorlds().stream().mapToInt(world -> world.getLoadedChunks().length).sum();
        int staff = 0;
        int staffMode = 0;
        int vanished = 0;
        for (var player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("nexusstaff.staff")) continue;
            staff++;
            if (plugin.staffMode().isEnabled(player)) staffMode++;
            if (plugin.vanish().isVanished(player)) vanished++;
        }
        return new Snapshot(Math.min(20.0, tps[0]), Bukkit.getAverageTickTime(),
                maximum == 0 ? 0 : (int) Math.min(100, used * 100 / maximum),
                Long.toString(used / 1_048_576), Long.toString(maximum / 1_048_576), chunks,
                staff, staffMode, vanished, plugin.reports().openReports().size());
    }

    public record Snapshot(double tps, double mspt, int ramPercent, String ramUsedMb,
                           String ramMaxMb, int loadedChunks, int onlineStaff, int staffMode, int vanished,
                           int openReports) { }
}