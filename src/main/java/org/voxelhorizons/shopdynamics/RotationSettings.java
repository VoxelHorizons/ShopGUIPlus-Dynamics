package org.voxelhorizons.shopdynamics;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable, validated runtime settings. Slot indexes are zero-based. */
public record RotationSettings(
    boolean enabled, Mode mode, String world, int intervalDays, long realTimeMillis,
    long checkTicks, boolean avoidPrevious, boolean closeOpenShops,
    boolean automatic, Set<String> include, Set<String> exclude, List<Integer> slots, int inventorySize
) {
    public enum Mode { MINECRAFT_DAY, REAL_TIME }

    public static RotationSettings from(FileConfiguration config) {
        String mode = config.getString("rotation.mode", "minecraft-day");
        Mode parsed = switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "minecraft-day" -> Mode.MINECRAFT_DAY;
            case "real-time" -> Mode.REAL_TIME;
            default -> throw new IllegalArgumentException("rotation.mode must be minecraft-day or real-time");
        };
        int interval = config.getInt("rotation.interval-days", 1);
        long realTime = config.getLong("rotation.real-time-minutes", 20);
        long ticks = config.getLong("rotation.check-interval-ticks", 100);
        if (interval < 1 || realTime < 1 || ticks < 1) {
            throw new IllegalArgumentException("rotation intervals must be positive");
        }
        // Overflow-safe, with a bound generous enough for practical administration.
        if (realTime > 5256000L) {
            throw new IllegalArgumentException("rotation.real-time-minutes is too large");
        }

        List<Integer> slots = new ArrayList<>(config.getIntegerList("shops.slots"));
        if (slots.isEmpty() || slots.size() > 54 || new HashSet<>(slots).size() != slots.size()) {
            throw new IllegalArgumentException("shops.slots requires 1 to 54 distinct inventory indexes");
        }
        int inventorySize = config.getInt("shops.inventory-size", -1);
        if (inventorySize != -1 && (inventorySize < 9 || inventorySize > 54 || inventorySize % 9 != 0)) {
            throw new IllegalArgumentException("shops.inventory-size must be -1 or a multiple of 9 from 9 to 54");
        }
        for (int slot : slots) {
            if (slot < 0 || slot >= (inventorySize == -1 ? 54 : inventorySize)) {
                throw new IllegalArgumentException("invalid shop item slot " + slot);
            }
        }
        return new RotationSettings(config.getBoolean("enabled", true), parsed,
            config.getString("rotation.world", "world"), interval, realTime * 60000L, ticks,
            config.getBoolean("rotation.avoid-previous", true),
            config.getBoolean("rotation.close-open-shops", true),
            config.getBoolean("shops.auto", true), Set.copyOf(config.getStringList("shops.include")),
            Set.copyOf(config.getStringList("shops.exclude")), List.copyOf(slots), inventorySize);
    }

    public boolean manages(String shopId, int count) {
        return enabled && !exclude.contains(shopId) &&
            (include.contains(shopId) || (automatic && count > slots.size()));
    }
}
