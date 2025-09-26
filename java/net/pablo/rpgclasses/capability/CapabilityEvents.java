package net.pablo.rpgclasses.capability;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.RpgClassesMod;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID)
public class CapabilityEvents {

    @SubscribeEvent
    public static void onRegisterCaps(RegisterCapabilitiesEvent event) {
        event.register(IPlayerClass.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(PlayerClassProvider.PLAYER_CLASS_ID, new PlayerClassProvider());
            System.out.println("[RPGClasses] Attached PlayerClass capability to player " + event.getObject().getUUID());
        }
    }
}
