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
        // Only apply refund if player didn't take damage
        if (!tookDamage.getOrDefault(player.getUUID(), false)) {
            refundCooldownPercent(player, 0.5); // 50% refund
        }
        // Only remove the damage tracking, NOT the cooldown itself
        tookDamage.remove(player.getUUID());
        // DON'T remove the expiration - that's the actual cooldown!
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

    // Optional: Add a method to clear cooldown if needed
    public static void clearCooldown(Player player) {
        expirations.remove(player.getUUID());
        tookDamage.remove(player.getUUID());
    }
}