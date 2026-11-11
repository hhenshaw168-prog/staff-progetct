package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class MessageService {
    private final NexusStaffPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final YamlConfiguration messages;

    public MessageService(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
    }

    public Component component(String key, Map<String, String> placeholders) {
        String text = messages.getString(key, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return miniMessage.deserialize(text);
    }

    public Component raw(String text, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return miniMessage.deserialize(text);
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(raw(messages.getString("prefix", "") + messages.getString(key, key), Map.of()));
    }

    public String text(String key, String fallback) { return messages.getString(key, fallback); }

    public MiniMessage miniMessage() { return miniMessage; }
}