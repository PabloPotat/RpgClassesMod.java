package net.pablo.rpgclasses.capability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.network.NetworkHandler;
import net.pablo.rpgclasses.network.SyncSelectedClassPacket;
import net.pablo.rpgclasses.registry.RPGClassRegistry;
import net.pablo.rpgclasses.classes.RPGClass;
import net.pablo.rpgclasses.RpgClassesMod;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID)
public class PlayerClassEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {

                if (cap.getSelectedClass() != null) {
                    String className = cap.getSelectedClass().getClassName();

                    if (player instanceof ServerPlayer serverPlayer) {
                        NetworkHandler.sendToClient(new SyncSelectedClassPacket(className), serverPlayer);
                    }
                }


            // Remove previous primary class effect if changed
            String prevClass = cap.getPreviousClassName();
            RPGClass current = cap.getSelectedClass();
            if (prevClass != null && (current == null || !prevClass.equals(current.getClassName()))) {
                RPGClass oldClass = RPGClassRegistry.getClassByName(prevClass);
                if (oldClass != null) oldClass.removeClassEffect(player);
                cap.setPreviousClassName(null);
            }

            // Remove previous secondary class effect if changed
            String prevSecondary = cap.getPreviousSecondaryClassName();
            RPGClass currentSec = cap.getSecondaryClass();
            if (prevSecondary != null && (currentSec == null || !prevSecondary.equals(currentSec.getClassName()))) {
                RPGClass oldSecClass = RPGClassRegistry.getClassByName(prevSecondary);
                if (oldSecClass != null) oldSecClass.removeClassEffect(player);
                cap.setPreviousSecondaryClassName(null);
            }

            // Apply current class effects
            if (current != null) current.applyClassEffect(player);
            if (currentSec != null) currentSec.applyClassEffect(player);
        });
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() == null) return;

        Player original = event.getOriginal();
        Player clone = (Player) event.getEntity();

        original.reviveCaps(); // ensure original capabilities are active

        original.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(oldCap -> {
            clone.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(newCap -> {

                // Copy everything (classes, XP, levels)
                newCap.copyFrom(oldCap);

                // Apply current class effects immediately
                RPGClass primary = newCap.getSelectedClass();
                RPGClass secondary = newCap.getSecondaryClass();

                if (primary != null) primary.applyClassEffect(clone);
                if (secondary != null) secondary.applyClassEffect(clone);

                System.out.println("[RPGClasses] Cloned class data from " +
                        (primary != null ? primary.getClassName() : "null") + " / " +
                        (secondary != null ? secondary.getClassName() : "null"));
            });
        });
    }


}
