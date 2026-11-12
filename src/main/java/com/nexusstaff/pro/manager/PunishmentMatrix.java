package com.nexusstaff.pro.manager;

import com.nexusstaff.pro.NexusStaffPlugin;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PunishmentMatrix {
    private final YamlConfiguration config;

    public PunishmentMatrix(NexusStaffPlugin plugin) {
        plugin.saveResource("punishment-matrix.yml", false);
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "punishment-matrix.yml"));
    }

    public List<Category> categories() {
        List<Category> categories = new ArrayList<>();
        for (String section : List.of("chat", "gameplay")) {
            var values = config.getConfigurationSection(section);
            if (values == null) continue;
            for (String key : values.getKeys(false)) {
                List<String> tiers = config.getStringList(section + "." + key);
                if (!tiers.isEmpty()) categories.add(new Category(section, key, tiers));
            }
        }
        return List.copyOf(categories);
    }

    public Category find(String input) {
        String normalized = normalize(input);
        return categories().stream().filter(category -> category.key().equals(normalized)
            || normalize(category.id()).equals(normalized)).findFirst().orElse(null);
    }

    public Decision decision(Category category, int offense) {
        List<String> tiers = category.tiers();
        String outcome = tiers.get(Math.min(Math.max(1, offense), tiers.size()) - 1).toLowerCase(Locale.ROOT);
        if (outcome.equals("blacklist")) return new Decision(outcome, "BLACKLIST", null, true);
        int separator = outcome.indexOf('-');
        if (separator <= 0) throw new IllegalArgumentException("Invalid matrix outcome: " + outcome);
        String durationText = outcome.substring(0, separator);
        String type = outcome.substring(separator + 1).toUpperCase(Locale.ROOT);
        boolean permanent = durationText.equals("perm");
        Duration duration = permanent ? null : parseDuration(durationText);
        return new Decision(outcome, type, duration, permanent);
    }

    public static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private Duration parseDuration(String input) {
        int split = 0;
        while (split < input.length() && Character.isDigit(input.charAt(split))) split++;
        if (split == 0) throw new IllegalArgumentException("Invalid duration: " + input);
        long amount = Long.parseLong(input.substring(0, split));
        return switch (input.substring(split)) {
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Invalid duration unit: " + input);
        };
    }

    public record Category(String section, String key, List<String> tiers) {
        public String id() { return section + "." + key; }
        public String displayName() { return key.replace('-', ' '); }
    }

    public record Decision(String configuredOutcome, String type, Duration duration, boolean permanent) { }
}