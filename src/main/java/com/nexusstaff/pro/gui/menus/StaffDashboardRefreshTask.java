package com.nexusstaff.pro.gui.menus;

import com.nexusstaff.pro.NexusStaffPlugin;
import com.nexusstaff.pro.gui.holders.StaffDashboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class StaffDashboardRefreshTask implements Runnable {
    private final NexusStaffPlugin plugin;
    private final DashboardMetrics metrics;
    private final StaffDashboardMenu menu;

    public StaffDashboardRefreshTask(NexusStaffPlugin plugin, DashboardMetrics metrics, StaffDashboardMenu menu) {
        this.plugin = plugin;
        this.metrics = metrics;
        this.menu = menu;
    }

    @Override
    public void run() {
        DashboardMetrics.Snapshot snapshot = metrics.sample();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof StaffDashboardHolder) {
                menu.refresh(player.getOpenInventory().getTopInventory(), snapshot);
            }
        }
    }
}