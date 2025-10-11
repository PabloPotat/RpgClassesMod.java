package net.pablo.rpgclasses.skills;

import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PhantomCooldownManager {

    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_DURATION_TICKS = 700; // 20 seconds (20 ticks per second)

    public static boolean isOnCooldown(Player player) {
        UUID playerId = player.getUUID();
        Long cooldownEnd = cooldowns.get(playerId);

        if (cooldownEnd == null) return false;

        long currentTick = player.level().getGameTime();
        if (currentTick >= cooldownEnd) {
            cooldowns.remove(playerId);
            return false;
        }
        return true;
    }

    public static void setCooldown(Player player) {
        long currentTick = player.level().getGameTime();
        cooldowns.put(player.getUUID(), currentTick + COOLDOWN_DURATION_TICKS);
    }

    public static long getRemainingCooldown(Player player) {
        UUID playerId = player.getUUID();
        Long cooldownEnd = cooldowns.get(playerId);

        if (cooldownEnd == null) return 0;

        long currentTick = player.level().getGameTime();
        return Math.max(0, cooldownEnd - currentTick);
    }

    public static double getRemainingCooldownSeconds(Player player) {
        return getRemainingCooldown(player) / 20.0;
    }
}