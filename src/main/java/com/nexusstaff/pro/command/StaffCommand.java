package com.nexusstaff.pro.command;

import com.nexusstaff.pro.NexusStaffPlugin;
import com.nexusstaff.pro.gui.MenuFactory;
import com.nexusstaff.pro.gui.menus.StaffDashboardMenu;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class StaffCommand implements CommandExecutor, TabCompleter {
    private final NexusStaffPlugin plugin;
    private final MenuFactory menus;

    public StaffCommand(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        menus = new MenuFactory(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(java.util.Locale.ROOT);
        if (!name.equals("report") && !sender.hasPermission(command.getPermission())) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (name.equals("report")) {
            if (!(sender instanceof Player reporter)) {
                plugin.messages().send(sender, "player-only");
                return true;
            }
            if (args.length < 2) return false;
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                plugin.messages().send(reporter, "player-not-found");
                return true;
            }
            if (target.equals(reporter)) {
                reporter.sendMessage(plugin.messages().raw("<red>You cannot report yourself.</red>", Map.of()));
                return true;
            }
            plugin.reports().submit(reporter, target, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            reporter.sendMessage(plugin.messages().raw("<green>Report submitted.</green>", Map.of()));
            return true;
        }
        if (name.equals("mutechat")) {
            boolean muted = plugin.chatControl().toggle();
            Bukkit.broadcast(plugin.messages().raw(muted ? "<red>Global chat mute enabled.</red>" : "<green>Global chat mute disabled.</green>", Map.of()));
            return true;
        }
        if (name.equals("freezeall")) {
            boolean frozen = plugin.freeze().toggleAllNonStaff();
            Bukkit.broadcast(plugin.messages().raw(frozen ? "<red>Emergency freeze enabled.</red>" : "<green>Emergency freeze disabled.</green>", Map.of()));
            return true;
        }
        if (name.equals("staffchat")) {
            if (args.length == 0) return false;
            String message = String.join(" ", args);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("nexusstaff.staff")) {
                    staff.sendMessage(plugin.messages().raw(plugin.messages().text("staffchat-format", "<aqua>[Staff]</aqua> <player>: <message>"),
                            Map.of("player", sender.getName(), "message", message)));
                }
            }
            plugin.network().sendStaffChat(sender.getName(), message);
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        switch (name) {
            case "staff" -> plugin.dashboardMenu().open(player);
            case "staffmode" -> staffMode(player, args);
            case "threats" -> menus.openThreats(player);
            case "inspect" -> {
                Player target = target(player, args);
                if (target != null) menus.openInspect(player, target);
            }
            case "punish" -> {
                Player target = target(player, args);
                if (target != null) menus.openPunish(player, target);
            }
            default -> { return false; }
        }
        return true;
    }

    private void staffMode(Player player, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("spectate")) {
            Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : Bukkit.getOnlinePlayers().stream()
                    .filter(candidate -> !candidate.hasPermission("nexusstaff.staff") && !candidate.equals(player))
                    .max(java.util.Comparator.comparingDouble(candidate -> plugin.antiCheat().threat(candidate.getUniqueId())))
                    .orElse(null);
            if (target == null) {
                plugin.messages().send(player, "player-not-found");
                return;
            }
            if (!plugin.staffMode().isEnabled(player)) plugin.staffMode().toggle(player);
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.setSpectatorTarget(target);
            return;
        }
        boolean enabled = plugin.staffMode().toggle(player);
        plugin.messages().send(player, enabled ? "staffmode-enabled" : "staffmode-disabled");
    }

    private Player target(Player sender, String[] args) {
        if (args.length == 0) return null;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) plugin.messages().send(sender, "player-not-found");
        return target;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && command.getName().equalsIgnoreCase("staffmode")) {
            return List.of("spectate");
        }
        if (args.length == 1 || (args.length == 2 && args[0].equalsIgnoreCase("spectate"))) {
            String prefix = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}