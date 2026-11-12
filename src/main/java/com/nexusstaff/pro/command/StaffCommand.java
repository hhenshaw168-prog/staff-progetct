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
        if (name.equals("dupeip")) {
            if (!sender.hasPermission(command.getPermission())) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            if (args.length != 1) return false;
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found");
                return true;
            }
            sender.sendMessage(plugin.messages().raw("<yellow>Searching account history for <player>...</yellow>", Map.of("player", target.getName())));
            plugin.altAccounts().findAlts(target, matches -> {
                if (matches.isEmpty()) {
                    sender.sendMessage(plugin.messages().raw("<gray>No matching account history found.</gray>", Map.of()));
                    return;
                }
                var ranks = matches.stream().map(uniqueId -> plugin.luckPerms().rankAsync(uniqueId)).toList();
                java.util.concurrent.CompletableFuture.allOf(ranks.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .whenComplete((ignored, rankError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(plugin.messages().raw("<yellow>Related accounts for <player> (<count>):</yellow>",
                            Map.of("player", target.getName(), "count", Integer.toString(matches.size()))));
                        for (int index = 0; index < matches.size(); index++) {
                        var uniqueId = matches.get(index);
                        String accountName = Bukkit.getOfflinePlayer(uniqueId).getName();
                        if (accountName == null) accountName = uniqueId.toString();
                        String group = rankError == null ? ranks.get(index).join().group() : "unknown";
                        sender.sendMessage(net.kyori.adventure.text.Component.text("- " + accountName + " [" + group + "]")
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(
                                "/ipban " + target.getName() + " gameplay.duping Duping network blacklist")));
                        }
                        sender.sendMessage(net.kyori.adventure.text.Component.text("IP-ban the shared address through the Duping escalation.")
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(
                                "/ipban " + target.getName() + " gameplay.duping Duping network blacklist")));
                    }));
            });
            return true;
        }
        if (isMatrixCommand(name) && !(name.equals("punish") && args.length == 1)) {
            if (!sender.hasPermission(command.getPermission())) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            if (args.length < 3) return false;
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found");
                return true;
            }
            var category = plugin.punishments().matrix().find(args[1]);
            int categoryTokens = 1;
            if (category == null && args.length > 3) {
                category = plugin.punishments().matrix().find(args[1] + " " + args[2]);
                categoryTokens = 2;
            }
            int reasonIndex = 1 + categoryTokens;
            if (category == null || reasonIndex >= args.length) {
                sender.sendMessage(plugin.messages().raw("<red>Unknown category. Use /punish <player> <category> <reason>.</red>", Map.of()));
                return true;
            }
            if (category.key().equals("lying-to-staff") && !sender.hasPermission("nexusstaff.admin")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            String reason = String.join(" ", java.util.Arrays.copyOfRange(args, reasonIndex, args.length));
            plugin.punishments().applyMatrix(sender, target, category, reason, name.equals("ipban"));
            return true;
        }
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

    private boolean isMatrixCommand(String command) {
        return switch (command) {
            case "punish", "ban", "tempban", "ipban", "tempmute", "mute" -> true;
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(java.util.Locale.ROOT);
        if (isMatrixCommand(commandName) && args.length == 2) {
            String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
            return plugin.punishments().matrix().categories().stream()
                    .map(category -> category.id())
                    .filter(category -> category.startsWith(prefix)).toList();
        }
        if (commandName.equals("dupeip") && args.length == 1) {
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(playerName -> playerName.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)).toList();
        }
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