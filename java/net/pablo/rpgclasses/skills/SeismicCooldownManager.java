package net.pablo.rpgclasses.skills;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cooldown manager storing expiration timestamps (ms).
 * Usage:
 *  - setCooldown(player, seconds)
 *  - isOnCooldown(player)
 *  - getRemainingSeconds(player)
 *  - refundCooldownPercent(player, percent) // percent: 0.0..1.0
 */
public final class SeismicCooldownManager {
    private static final Map<UUID, Long> expirations = new ConcurrentHashMap<>();

    private SeismicCooldownManager() {}

    public static boolean isOnCooldown(Player player) {
        return getRemainingSeconds(player) > 0.0;
    }

    public static double getRemainingSeconds(Player player) {
        Long exp = expirations.get(player.getUUID());
        if (exp == null) return 0.0;
        long now = System.currentTimeMillis();
        long remainingMs = exp - now;
        return Math.max(0.0, remainingMs / 1000.0);
    }

    public static void setCooldown(Player player, double seconds) {
        long exp = System.currentTimeMillis() + Math.round(seconds * 1000.0);
        expirations.put(player.getUUID(), exp);
    }

    /**
     * Refunds a percentage of remaining cooldown.
     * percent: fraction to refund (0.0 -> no refund, 1.0 -> clear cooldown)
     */
    public static void refundCooldownPercent(Player player, double percent) {
        if (percent <= 0.0) return;
        UUID id = player.getUUID();
        Long exp = expirations.get(id);
        if (exp == null) return;
        long now = System.currentTimeMillis();
        if (exp <= now) {
            expirations.remove(id);
            return;
        }
        long remaining = exp - now;
        long reduced = (long) Math.max(0L, remaining - Math.round(remaining * percent));
        expirations.put(id, now + reduced);
    }

    public static void clearCooldown(Player player) {
        expirations.remove(player.getUUID());
    }
}
