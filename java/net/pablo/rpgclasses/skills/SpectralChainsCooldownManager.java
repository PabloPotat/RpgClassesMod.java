package net.pablo.rpgclasses.skills;

import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectralChainsCooldownManager {
    private static final Map<UUID, Long> expirations = new ConcurrentHashMap<>();
    private static final double COOLDOWN_SECONDS = 45.0; // 45 second cooldown

    private SpectralChainsCooldownManager() {}

    public static boolean isOnCooldown(Player player) {
        return getRemainingSeconds(player) > 0.0;
    }

    public static double getRemainingSeconds(Player player) {
        Long exp = expirations.get(player.getUUID());
        if (exp == null) return 0.0;
        long now = System.currentTimeMillis();
        return Math.max(0.0, (exp - now) / 1000.0);
    }

    public static void setCooldown(Player player) {
        expirations.put(player.getUUID(), System.currentTimeMillis() + Math.round(COOLDOWN_SECONDS * 1000.0));
    }

    public static void clearCooldown(Player player) {
        expirations.remove(player.getUUID());
    }
}