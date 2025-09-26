package net.pablo.rpgclasses.passive;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.RpgClassesMod;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.classes.Fighter;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID)
public class SurgingBlow {

    private static final int MIN_COMBO = 5;
    private static final double PER_COMBO_MULTIPLIER = 0.1; // +10% dmg per step
    private static final double MAX_MULTIPLIER = 3.0;       // hard cap

    @SubscribeEvent
    public static void onHit(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            Fighter fighter = null;

            if (cap.getSelectedClass() instanceof Fighter fPrimary) {
                fighter = fPrimary;
            } else if (cap.getSecondaryClass() instanceof Fighter fSecondary) {
                fighter = fSecondary;
            }

            if (fighter == null) return;

            int combo = fighter.getCombo(player.getUUID());

            // Trigger only if combo is a multiple of MIN_COMBO (5, 10, 15...)
            if (combo > 0 && combo % MIN_COMBO == 0) {
                LivingEntity target = event.getEntity();

                // Scale damage: 5 = 1.1x, 10 = 1.2x, 15 = 1.3x ...
                double multiplier = 1.0 + ((combo / MIN_COMBO) * PER_COMBO_MULTIPLIER);
                if (multiplier > MAX_MULTIPLIER) multiplier = MAX_MULTIPLIER;

                float boosted = (float) (event.getAmount() * multiplier);
                event.setAmount(boosted);
            }
        });
    }
}
