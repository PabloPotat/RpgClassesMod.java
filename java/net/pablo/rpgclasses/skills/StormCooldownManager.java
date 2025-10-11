package net.pablo.rpgclasses.skills;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cooldown manager for Stormbound skill.
 * Tracks whether the player took damage to allow partial refund.
 */
public final class StormCooldownManager {
    private static final Map<UUID, Long> expirations = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> tookDamage = new ConcurrentHashMap<>();

    private static final double COOLDOWN_SECONDS = 40.0; // 40 second cooldown

    private StormCooldownManager() {}

    public static boolean isOnCooldown(Player player) {
        return getRemainingSeconds(player) > 0.0;
    }

    public static double getRemainingSeconds(Player player) {
        Long exp = expirations.get(player.getUUID());
        if (exp == null) return 0.0;
        long now = System.currentTimeMillis();
        return Math.max(0.0, (exp - now) / 1000.0);
    }

    public static void setCooldown(Player player, double seconds) {
        expirations.put(player.getUUID(), System.currentTimeMillis() + Math.round(seconds * 1000.0));
        tookDamage.put(player.getUUID(), false);
    }

    public static void markDamageTaken(Player player) {
        tookDamage.put(player.getUUID(), true);
    }

    public static void onStormSurgeEnd(Player player) {
        boolean damaged = tookDamage.getOrDefault(player.getUUID(), false);

        if (damaged) {
            // Full cooldown if damaged
            setCooldown(player, COOLDOWN_SECONDS);
        } else {
            // 60% refund if no damage (40% of cooldown = 16s instead of 40s)
            setCooldown(player, COOLDOWN_SECONDS * 0.4);
        }

        // Clean up damage tracking
        tookDamage.remove(player.getUUID());
    }

    public static void refundCooldownPercent(Player player, double percent) {
        if (percent <= 0.0) return;
        Long exp = expirations.get(player.getUUID());
        if (exp == null) return;

        long now = System.currentTimeMillis();
        long remaining = Math.max(0, exp - now);
        long reduced = (long) (remaining * (1.0 - percent));
        expirations.put(player.getUUID(), now + reduced);
    }

    public static void clearCooldown(Player player) {
        expirations.remove(player.getUUID());
        tookDamage.remove(player.getUUID());
    }

    // Helper to check if damage was taken (for shockwave logic)
    public static boolean tookDamage(Player player) {
        return tookDamage.getOrDefault(player.getUUID(), false);
    }
}