package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.EventExecutor;

/** Registers adapters only when the configured optional event type is available. */
public final class AntiCheatEventBridge implements Listener {
    private static final List<String> PLAYER_METHODS = List.of("getPlayer", "player", "getFlaggedPlayer");
    private static final List<String> CHECK_METHODS = List.of("getCheckName", "getCheck", "getCheckType", "getCheckIdentifier");
    private static final List<String> VL_METHODS = List.of("getViolationLevel", "getVl", "getVL", "getViolation");
    private static final List<String> COUNT_METHODS = List.of("getViolationCount", "getViolations", "getCount");
    private final NexusStaffPlugin plugin;

    public AntiCheatEventBridge(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        register("GrimAC", "anti-cheat.grim-event-class", List.of(
                "ac.grim.grimac.api.events.FlagEvent", "ac.grim.grimac.api.events.PlayerFlagEvent",
                "ac.grim.grimac.api.events.GrimFlagEvent"));
        register("Vulcan", "anti-cheat.vulcan-event-class", List.of(
                "me.frep.vulcan.api.event.VulcanFlagEvent", "me.frep.vulcan.api.events.VulcanFlagEvent",
                "me.frep.vulcan.api.event.PlayerFlagEvent"));
    }

    private void register(String pluginName, String configPath, List<String> defaultEventClasses) {
        Plugin dependency = Bukkit.getPluginManager().getPlugin(pluginName);
        if (dependency == null || !dependency.isEnabled()) return;
        String configured = plugin.getConfig().getString(configPath, "");
        List<String> candidates = configured.isBlank() ? defaultEventClasses : List.of(configured);
        for (String className : candidates) {
            try {
                Class<?> eventType = Class.forName(className, false, dependency.getClass().getClassLoader());
                if (!Event.class.isAssignableFrom(eventType)) continue;
                @SuppressWarnings("unchecked")
                Class<? extends Event> bukkitEvent = (Class<? extends Event>) eventType;
                EventExecutor executor = (listener, event) -> accept(pluginName, event);
                Bukkit.getPluginManager().registerEvent(bukkitEvent, this, EventPriority.NORMAL, executor, plugin, true);
                plugin.getLogger().info("Registered " + pluginName + " flag adapter for " + className);
                return;
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        }
        plugin.getLogger().fine(pluginName + " flag event adapter unavailable; configure " + configPath + " for this release.");
    }

    private void accept(String source, Event event) {
        Runnable publish = () -> {
            Object playerValue = invoke(event, PLAYER_METHODS);
            if (!(playerValue instanceof org.bukkit.entity.Player player)) return;
            Object checkValue = invoke(event, CHECK_METHODS);
            Object vlValue = invoke(event, VL_METHODS);
            Object countValue = invoke(event, COUNT_METHODS);
            String check = checkValue == null ? event.getClass().getSimpleName() : checkValue.toString();
            double vl = number(vlValue, 0.0);
            int count = (int) number(countValue, 1.0);
            plugin.antiCheat().publishFlag(player.getUniqueId(), source, check, count, vl, event.toString());
        };
        if (event.isAsynchronous()) Bukkit.getScheduler().runTask(plugin, publish);
        else publish.run();
    }

    private Object invoke(Object instance, List<String> names) {
        for (String name : names) {
            try {
                Method method = instance.getClass().getMethod(name);
                return method.invoke(instance);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}