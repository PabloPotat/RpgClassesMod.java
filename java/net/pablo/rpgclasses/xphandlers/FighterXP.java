package net.pablo.rpgclasses.xphandlers;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.RpgClassesMod;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.classes.Fighter;
import net.pablo.rpgclasses.utils.AFKUtils;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID)
public class FighterXP {

    private static final int XP_CAP = 50;
    private static final double STREAK_MULTIPLIER = 0.15; // combo multiplier
    private static final long XP_MIN_INTERVAL_MS = 100; // grant XP at most once per 100ms

    // Track last XP award per player
    private static final Map<Player, Long> lastXPAwardTime = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerDamage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        // Skip AFK players
        if (AFKUtils.isPlayerAFK(player)) return;

        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {

            Fighter fighter = null;
            boolean isPrimary = false;
            boolean isSecondary = false;

            if (cap.getSelectedClass() instanceof Fighter fPrimary) {
                fighter = fPrimary;
                isPrimary = true;
            } else if (cap.getSecondaryClass() instanceof Fighter fSecondary) {
                fighter = fSecondary;
                isSecondary = true;
            }

            if (fighter == null) return;

            // Reset combo if timed out or standing still
            fighter.resetComboIfTimedOut(player.getUUID(), player);

            // Increase combo for this hit only if player moved
            fighter.increaseCombo(player.getUUID(), player);
            int combo = fighter.getCombo(player.getUUID());

            // Only award XP once per attack window
            long now = System.currentTimeMillis();
            long lastXP = lastXPAwardTime.getOrDefault(player, 0L);
            if (now - lastXP < XP_MIN_INTERVAL_MS) return; // skip if too soon
            lastXPAwardTime.put(player, now);

            if (combo > 0) {
                double level = cap.getLevel("fighter");
                double baseXP = 2 + level * 0.8;
                double xpFromCombo = baseXP * (1.0 + STREAK_MULTIPLIER * (combo - 1));
                int xpToAward = (int) Math.min(xpFromCombo, XP_CAP);

                if (isPrimary) {
                    XPUtils.addXPAndCheckLevel(player, cap, "fighter", xpToAward);
                }
                if (isSecondary) {
                    XPUtils.addSecondaryXP(player, cap, "fighter", xpToAward);
                }
            }
        });
    }
}
