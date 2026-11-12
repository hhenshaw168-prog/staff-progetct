package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class NetworkMessenger implements PluginMessageListener {
    private static final String STAFF_CHAT = "STAFF_CHAT";
    private static final String NETWORK_IP_BAN = "NETWORK_IP_BAN";
    private final NexusStaffPlugin plugin;
    private final String channel;
    private final byte[] signingKey;

    public NetworkMessenger(NexusStaffPlugin plugin) {
        this.plugin = plugin;
        channel = plugin.getConfig().getString("network.channel", "nexusstaff:main");
        String encodedKey = plugin.getConfig().getString("network.shared-secret", "");
        if (encodedKey.isBlank()) {
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            encodedKey = Base64.getEncoder().encodeToString(generated);
            plugin.getConfig().set("network.shared-secret", encodedKey);
            plugin.saveConfig();
        }
        signingKey = Base64.getDecoder().decode(encodedKey);
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

    public void broadcastNetworkIpBan(String ip, String reason, String source) {
        if (!plugin.getConfig().getBoolean("network.enabled", false)) return;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(NETWORK_IP_BAN);
            output.writeUTF(Bukkit.getServer().getName());
            output.writeUTF(ip);
            output.writeUTF(reason);
            output.writeUTF(source);
            output.writeUTF(signature(Bukkit.getServer().getName(), ip, reason, source));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not encode network IP ban.");
            return;
        }
        sendToProxy(bytes.toByteArray());
    }

    private void sendToProxy(byte[] payload) {
        for (Player carrier : Bukkit.getOnlinePlayers()) {
            carrier.sendPluginMessage(plugin, channel, payload);
            return;
        }
        plugin.getLogger().warning("Could not forward network action: no online proxy carrier is available.");
    }

    @Override
    public void onPluginMessageReceived(String incomingChannel, Player carrier, byte[] payload) {
        if (!channel.equals(incomingChannel)) return;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            String subchannel = input.readUTF();
            String origin = input.readUTF();
            if (Bukkit.getServer().getName().equals(origin)) return;
            if (NETWORK_IP_BAN.equals(subchannel)) {
                String ip = input.readUTF();
                String reason = input.readUTF();
                String source = input.readUTF();
                String signature = input.readUTF();
                if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.US_ASCII),
                        signature(origin, ip, reason, source).getBytes(StandardCharsets.US_ASCII))) return;
                applyNetworkIpBan(ip, reason, source);
                return;
            }
            if (!STAFF_CHAT.equals(subchannel)) return;
            String sender = input.readUTF();
            String message = input.readUTF();
            String formatted = plugin.messages().text("staffchat-format", "<aqua>[Staff]</aqua> <player>: <message>")
                    .replace("<player>", sender).replace("<message>", message);
            var component = plugin.messages().raw(formatted, java.util.Map.of());
            for (Player staff : Bukkit.getOnlinePlayers()) if (staff.hasPermission("nexusstaff.staff")) staff.sendMessage(component);
        } catch (IOException exception) {
            plugin.getLogger().warning("Discarded malformed network staff chat payload.");
        }
    }

    private void applyNetworkIpBan(String ip, String reason, String source) {
        if (!ip.matches("[0-9a-fA-F:.]+")) return;
        Bukkit.getBanList(BanList.Type.IP).addBan(ip, reason, null, source);
        for (Player player : Bukkit.getOnlinePlayers()) {
            var address = player.getAddress();
            if (address != null && address.getAddress() != null
                    && address.getAddress().getHostAddress().equalsIgnoreCase(ip)) {
                player.kick(plugin.messages().raw("<dark_red>Network IP blacklist: <reason>", java.util.Map.of("reason", reason)));
            }
        }
    }

    private String signature(String origin, String ip, String reason, String source) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            String payload = origin + "\n" + ip + "\n" + reason + "\n" + source;
            return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }
}