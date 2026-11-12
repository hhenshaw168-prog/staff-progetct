package com.nexusstaff.pro.gui;

import com.nexusstaff.pro.NexusStaffPlugin;
import com.nexusstaff.pro.gui.holders.StaffDashboardHolder;
import com.nexusstaff.pro.gui.holders.PlayerDirectoryHolder;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.persistence.PersistentDataType;

public final class MenuListener implements Listener {
    private final NexusStaffPlugin plugin;
    private final MenuFactory menus;

    public MenuListener(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        menus = new MenuFactory(plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof StaffDashboardHolder) return;
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
        String action = event.getCurrentItem().getItemMeta().getPersistentDataContainer()
                .get(plugin.actionKey(), PersistentDataType.STRING);
        if (action == null) return;
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.25f);
        if (action.startsWith("inspect:")) {
            Player target = Bukkit.getPlayer(UUID.fromString(action.substring("inspect:".length())));
            if (target != null) menus.openInspect(viewer, target);
            return;
        }
        if (action.startsWith("report:")) {
            UUID reportId = UUID.fromString(action.substring("report:".length()));
            if (event.isRightClick()) {
                var report = plugin.reports().resolve(reportId);
                if (report != null) viewer.sendMessage(plugin.messages().raw("<green>Report resolved.</green>", java.util.Map.of()));
                new com.nexusstaff.pro.gui.menus.ReportsMenu(plugin).open(viewer);
            } else if (event.isLeftClick()) {
                Player target = Bukkit.getPlayer(plugin.reports().openReports().stream()
                        .filter(report -> report.id().equals(reportId)).map(com.nexusstaff.pro.manager.ReportManager.Report::targetId).findFirst().orElse(null));
                if (target != null) viewer.teleport(target.getLocation());
                else viewer.sendMessage(plugin.messages().raw("<red>That player is no longer online.</red>", java.util.Map.of()));
            }
            return;
        }
        if (action.startsWith("directory:")) {
            Player target = Bukkit.getPlayer(UUID.fromString(action.substring("directory:".length())));
            if (target == null) return;
            if (event.isShiftClick()) {
                boolean frozen = plugin.freeze().toggle(target);
                viewer.sendMessage(plugin.messages().raw(frozen ? "<red>Player frozen.</red>" : "<green>Player unfrozen.</green>", java.util.Map.of()));
            } else if (event.isRightClick()) {
                menus.openInspect(viewer, target);
            } else if (event.isLeftClick()) {
                viewer.teleport(target.getLocation());
            }
            return;
        }
        if (action.startsWith("directory-page:") && holder instanceof PlayerDirectoryHolder directory) {
            String pageValue = action.substring("directory-page:".length());
            if (!pageValue.equals("current")) menus.openPlayers(viewer, Integer.parseInt(pageValue));
            return;
        }
        switch (holder.type()) {
            case DASHBOARD -> dashboard(viewer, action);
            case INSPECT -> inspect(viewer, holder, action);
            case PUNISH -> punish(viewer, holder, action);
            default -> { }
        }
    }

    private void dashboard(Player viewer, String action) {
        switch (action) {
            case "players" -> menus.openPlayers(viewer);
            case "threats" -> menus.openThreats(viewer);
            case "staffmode" -> {
                boolean enabled = plugin.staffMode().toggle(viewer);
                plugin.messages().send(viewer, enabled ? "staffmode-enabled" : "staffmode-disabled");
                viewer.closeInventory();
            }
            case "vanish" -> {
                boolean hidden = plugin.vanish().toggle(viewer);
                viewer.sendMessage(plugin.messages().raw(hidden ? "<green>Vanish enabled.</green>" : "<yellow>Vanish disabled.</yellow>", java.util.Map.of()));
            }
            case "freeze-all" -> {
                for (Player target : Bukkit.getOnlinePlayers()) if (!target.hasPermission("nexusstaff.staff")) plugin.freeze().toggle(target);
                viewer.sendMessage(plugin.messages().raw("<yellow>Freeze state toggled for online non-staff players.</yellow>", java.util.Map.of()));
            }
            default -> { }
        }
    }

    private void inspect(Player viewer, MenuHolder holder, String action) {
        Player target = holder.target() == null ? null : Bukkit.getPlayer(holder.target());
        if (target == null) return;
        switch (action) {
            case "teleport" -> viewer.teleport(target.getLocation());
            case "freeze" -> {
                boolean frozen = plugin.freeze().toggle(target);
                viewer.sendMessage(plugin.messages().raw(frozen ? "<red>Player frozen.</red>" : "<green>Player unfrozen.</green>", java.util.Map.of()));
            }
            case "punish" -> menus.openPunish(viewer, target);
            default -> { }
        }
    }

    private void punish(Player staff, MenuHolder holder, String action) {
        Player target = holder.target() == null ? null : Bukkit.getPlayer(holder.target());
        if (target == null) return;
        if (action.startsWith("matrix:")) {
            String categoryId = action.substring("matrix:".length());
            var category = plugin.punishments().matrix().find(categoryId);
            if (category == null) return;
            if (category.key().equals("lying-to-staff") && !staff.hasPermission("nexusstaff.admin")) {
                plugin.messages().send(staff, "no-permission");
                return;
            }
            plugin.punishments().applyMatrix(staff, target, category, "Staff moderation");
            staff.closeInventory();
            return;
        }
        switch (action) {
            case "punish:warn" -> plugin.punishments().warn(staff, target, "Staff warning");
            case "punish:mute" -> plugin.punishments().mute(staff, target, 3600, "Staff moderation");
            case "punish:kick" -> plugin.punishments().kick(staff, target, "Staff moderation");
            case "punish:ban" -> plugin.punishments().ban(staff, target, "Staff moderation");
            default -> { return; }
        }
        staff.closeInventory();
    }
}