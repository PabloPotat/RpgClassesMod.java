package net.pablo.rpgclasses.xphandlers;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.RpgClassesMod;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.utils.AFKUtils;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID)
public class MageXP {

    private static final double BASE_GAIN = 5.0;
    private static final double CAP_GAIN = 50.0;
    private static final int CAP_LEVEL = 50;
    private static final double GAIN_MULT = Math.pow(CAP_GAIN / BASE_GAIN, 1.0 / (CAP_LEVEL - 1));

    @SubscribeEvent
    public static void onMageCastSpell(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide) return;

        // Skip AFK players
        if (AFKUtils.isPlayerAFK(player)) return;

        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            boolean isMagePrimary = cap.getSelectedClass() != null &&
                    "Mage".equalsIgnoreCase(cap.getSelectedClass().getClassName());
            boolean isMageSecondary = cap.getSecondaryClass() != null &&
                    "Mage".equalsIgnoreCase(cap.getSecondaryClass().getClassName());

            if (!isMagePrimary && !isMageSecondary) return;

            int mageLevel = cap.getLevel("mage");
            double xpToAdd = calculateMageXP(mageLevel);

            // Primary class XP
            if (isMagePrimary) {
                XPUtils.addXPAndCheckLevel(player, cap, "mage", (int) xpToAdd);
            }

            // Secondary class XP using unified multiplier
            if (isMageSecondary) {
                XPUtils.addSecondaryXP(player, cap, "mage", (int) xpToAdd);
            }
        });
    }

    private static double calculateMageXP(int level) {
        double gain = BASE_GAIN * Math.pow(GAIN_MULT, level - 1);
        return Math.min(gain, CAP_GAIN);
    }
}
