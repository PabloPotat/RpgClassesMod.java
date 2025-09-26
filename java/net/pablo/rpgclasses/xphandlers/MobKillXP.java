package net.pablo.rpgclasses.xphandlers;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.pablo.rpgclasses.RpgClassesMod;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.utils.AFKUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID)
public class MobKillXP {

    private static final Map<UUID, Map<UUID, Float>> damageMap = new HashMap<>();

    /** Tracks damage dealt for XP split */
    public static void trackDamage(UUID mobId, UUID playerId, float damage) {
        damageMap.computeIfAbsent(mobId, k -> new HashMap<>()).merge(playerId, damage, Float::sum);
    }

    /** Hook into player damage to track contributions */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity mob = event.getEntity();
        if (mob.level().isClientSide) return;

        float damage = event.getAmount();
        trackDamage(mob.getUUID(), player.getUUID(), damage);
    }

    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity mob = event.getEntity();
        if (mob.level().isClientSide) return;

        Map<UUID, Float> contributions = damageMap.getOrDefault(mob.getUUID(), Map.of());
        float totalDamage = contributions.values().stream().reduce(0f, Float::sum);
        if (totalDamage <= 0) return;

        int baseXP = (int) mob.getMaxHealth(); // Base XP can be customized

        for (Map.Entry<UUID, Float> entry : contributions.entrySet()) {
            Player contributingPlayer = mob.level().getPlayerByUUID(entry.getKey());
            if (contributingPlayer == null) continue;

            // Skip AFK players
            if (AFKUtils.isPlayerAFK(contributingPlayer)) continue;

            float playerDamage = entry.getValue();
            int xpGain = (int) (baseXP * (playerDamage / totalDamage));

            contributingPlayer.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                // Primary class XP
                if (cap.getSelectedClass() != null) {
                    XPUtils.addXPAndCheckLevel(contributingPlayer, cap,
                            cap.getSelectedClass().getClassName().toLowerCase(), xpGain);
                }

                // Secondary class XP (uses consistent multiplier)
                if (cap.getSecondaryClass() != null) {
                    XPUtils.addSecondaryXP(contributingPlayer, cap,
                            cap.getSecondaryClass().getClassName().toLowerCase(), xpGain);
                }
            });
        }

        damageMap.remove(mob.getUUID());
    }
}
