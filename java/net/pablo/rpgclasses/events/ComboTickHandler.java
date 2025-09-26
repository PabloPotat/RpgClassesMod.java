// ComboTickHandler.java
package net.pablo.rpgclasses.events;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.classes.Fighter;
import net.pablo.rpgclasses.classes.RPGClass;

@Mod.EventBusSubscriber(modid = "rpgclasses", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ComboTickHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Player player = event.player;
            var cap = player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).orElse(null);
            if (cap == null) return;

            RPGClass selectedClass = cap.getSelectedClass();
            if (selectedClass instanceof Fighter fighter) {
                // CONSTANTLY update the combo timeout check
                fighter.resetComboIfTimedOut(player.getUUID(), player);
            }
        }
    }
}