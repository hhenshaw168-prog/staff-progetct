package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class NetworkMessenger implements PluginMessageListener {
    private static final String STAFF_CHAT = "STAFF_CHAT";
    private final NexusStaffPlugin plugin;
    private final String channel;

    public NetworkMessenger(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        channel = plugin.getConfig().getString("network.channel", "nexusstaff:main");
        if (plugin.getConfig().getBoolean("network.enabled", false)) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, this);
        }
    }

    public void sendStaffChat(String sender, String message) {
        if (!plugin.getConfig().getBoolean("network.enabled", false)) return;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(STAFF_CHAT);
            output.writeUTF(Bukkit.getServer().getName());
            output.writeUTF(sender);
            output.writeUTF(message);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not encode network staff chat message.");
            return;
        }
        for (Player carrier : Bukkit.getOnlinePlayers()) {
            carrier.sendPluginMessage(plugin, channel, bytes.toByteArray());
            break;
        }
    }

    @Override
    public void onPluginMessageReceived(String incomingChannel, Player carrier, byte[] payload) {
        if (!channel.equals(incomingChannel)) return;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (!STAFF_CHAT.equals(input.readUTF())) return;
            String origin = input.readUTF();
            String sender = input.readUTF();
            String message = input.readUTF();
            if (Bukkit.getServer().getName().equals(origin)) return;
            String formatted = plugin.messages().text("staffchat-format", "<aqua>[Staff]</aqua> <player>: <message>")
                    .replace("<player>", sender).replace("<message>", message);
            var component = plugin.messages().raw(formatted, java.util.Map.of());
            for (Player staff : Bukkit.getOnlinePlayers()) if (staff.hasPermission("nexusstaff.staff")) staff.sendMessage(component);
        } catch (IOException exception) {
            plugin.getLogger().warning("Discarded malformed network staff chat payload.");
        }
    }
}