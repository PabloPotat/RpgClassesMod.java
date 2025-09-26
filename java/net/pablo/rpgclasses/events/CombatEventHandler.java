package net.pablo.rpgclasses.events;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.classes.Fighter;
import net.pablo.rpgclasses.classes.RPGClass;

@Mod.EventBusSubscriber(modid = "rpgclasses", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CombatEventHandler {

    // In CombatEventHandler.java - after increasing combo:
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            var cap = player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).orElse(null);
            if (cap == null) return;

            RPGClass selectedClass = cap.getSelectedClass();
            if (selectedClass instanceof Fighter fighter) {
                fighter.increaseCombo(player.getUUID(), player);
                // The tick handler will handle the timeout checking now
            }
        }
    }

    private static Fighter getPrimaryFighterClass(Player player) {
        var cap = player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).orElse(null);
        if (cap == null) return null;

        RPGClass selectedClass = cap.getSelectedClass();
        if (selectedClass == null) return null;

        // Don't check by instance, check by class name
        if (selectedClass.getClassName().equalsIgnoreCase("fighter")) {
            // Return the actual instance from the capability
            return (Fighter) selectedClass;
        }

        return null;
    }
}