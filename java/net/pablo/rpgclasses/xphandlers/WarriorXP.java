package net.pablo.rpgclasses.xphandlers;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.RpgClassesMod;
import net.pablo.rpgclasses.capability.PlayerClassProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID)
public class WarriorXP {

    private static final Map<UUID, double[]> lastPosition = new HashMap<>();
    private static final Map<UUID, Long> lastHitTime = new HashMap<>();
    private static final double MIN_MOVE_DISTANCE = 0.1; // Minimum movement to be active
    private static final long COOLDOWN_MS = 500; // Optional cooldown per hit

    private static final double BASE_MIN = 2.0;
    private static final double BASE_MAX = 40.0;
    private static final int CAP_LEVEL = 60;
    private static final double MAX_HP_MULTIPLIER = 1.75;
    private static final int XP_CAP = 70;

    @SubscribeEvent
    public static void onPlayerDamage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            boolean isWarriorPrimary = cap.getSelectedClass() != null &&
                    "Warrior".equalsIgnoreCase(cap.getSelectedClass().getClassName());
            boolean isWarriorSecondary = cap.getSecondaryClass() != null &&
                    "Warrior".equalsIgnoreCase(cap.getSecondaryClass().getClassName());
            if (!isWarriorPrimary && !isWarriorSecondary) return;

            // Movement detection for anti-AFK
            double[] lastPos = lastPosition.getOrDefault(player.getUUID(),
                    new double[]{player.getX(), player.getY(), player.getZ()});
            double distance = Math.sqrt(Math.pow(player.getX() - lastPos[0], 2)
                    + Math.pow(player.getY() - lastPos[1], 2)
                    + Math.pow(player.getZ() - lastPos[2], 2));
            lastPosition.put(player.getUUID(), new double[]{player.getX(), player.getY(), player.getZ()});
            if (distance < MIN_MOVE_DISTANCE) return;

            // Cooldown enforcement
            long now = System.currentTimeMillis();
            long lastHit = lastHitTime.getOrDefault(player.getUUID(), 0L);
            if (now - lastHit < COOLDOWN_MS) return;
            lastHitTime.put(player.getUUID(), now);

            int level = cap.getLevel("warrior");
            double baseXP = calculateBaseXP(level);

            // HP multiplier
            double hpMultiplier = 1.0 + (1.0 - (player.getHealth() / player.getMaxHealth())) * 0.75;
            double xpToAward = Math.min(baseXP * hpMultiplier, XP_CAP);

            // Award XP to primary/secondary
            if (isWarriorPrimary) {
                XPUtils.addXPAndCheckLevel(player, cap, "warrior", (int) xpToAward);
            }
            if (isWarriorSecondary) {
                int secXp = (int) Math.round(xpToAward * 0.5); // secondary scale
                XPUtils.addXPAndCheckLevel(player, cap, "warrior", secXp);
            }
        });
    }

    private static double calculateBaseXP(int level) {
        if (level >= CAP_LEVEL) return BASE_MAX;
        return BASE_MIN + ((BASE_MAX - BASE_MIN) / (CAP_LEVEL - 1)) * (level - 1);
    }
}
