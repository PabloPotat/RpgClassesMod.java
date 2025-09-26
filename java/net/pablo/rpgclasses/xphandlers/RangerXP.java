package net.pablo.rpgclasses.xphandlers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.RpgClassesMod;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.utils.AFKUtils;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID)
public class RangerXP {

    private static final double MIN_EFFECTIVE_RANGE = 2.0; // Minimum distance for XP
    private static final double DISTANCE_DIVISOR = 10.0;   // Distance scaling divisor

    @SubscribeEvent
    public static void onProjectileHit(ProjectileImpactEvent event) {
        if (!(event.getEntity() instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof Player player)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity)) return;
        if (player.level().isClientSide) return; // Ensure server-side

        // Skip AFK players
        if (AFKUtils.isPlayerAFK(player)) return;

        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            boolean isPrimary = cap.getSelectedClass() != null &&
                    "Ranger".equalsIgnoreCase(cap.getSelectedClass().getClassName());
            boolean isSecondary = cap.getSecondaryClass() != null &&
                    "Ranger".equalsIgnoreCase(cap.getSecondaryClass().getClassName());

            if (!isPrimary && !isSecondary) return;

            // --- Distance calculation ---
            double dx = hit.getEntity().getX() - player.getX();
            double dy = hit.getEntity().getY() - player.getY();
            double dz = hit.getEntity().getZ() - player.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distance < MIN_EFFECTIVE_RANGE) return;

            // Ranger level
            int level = cap.getLevel("ranger");
            if (level < 1) level = 1; // Safety check

            // Base XP formula (capped at 75)
            double xpBase = Math.min(5 * (1 + 0.05 * (level - 1)), 75);

            // Distance XP formula (capped at 100)
            double xpDistance = xpBase * (1 + (distance - MIN_EFFECTIVE_RANGE) / DISTANCE_DIVISOR);
            xpDistance = Math.min(xpDistance, 100);

            int xpToAward = (int) Math.round(xpDistance);

            // Award XP
            if (isPrimary) {
                XPUtils.addXPAndCheckLevel(player, cap, "ranger", xpToAward);
            }
            if (isSecondary) {
                XPUtils.addSecondaryXP(player, cap, "ranger", xpToAward);
            }
        });
    }
}
