package net.pablo.rpgclasses.skills;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.UUID;

public class VermillionCooldown {
    private static final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private static final int DEFAULT_COOLDOWN_TICKS = 20 * 15; // 15 seconds

    public static boolean isOnCooldown(Player player) {
        UUID id = player.getUUID();
        if (!cooldowns.containsKey(id)) return false;

        long expireTime = cooldowns.get(id);
        return System.currentTimeMillis() < expireTime;
    }

    public static void setCooldown(Player player, int ticks) {
        long durationMs = ticks * 50L; // convert ticks to ms
        cooldowns.put(player.getUUID(), System.currentTimeMillis() + durationMs);
    }

    public static void setCooldown(Player player) {
        setCooldown(player, DEFAULT_COOLDOWN_TICKS);
    }

    public static int getRemainingTicks(Player player) {
        UUID id = player.getUUID();
        if (!cooldowns.containsKey(id)) return 0;
        long expireTime = cooldowns.get(id);
        long remainingMs = expireTime - System.currentTimeMillis();
        return (int) Math.max(0, remainingMs / 50);
    }
}
