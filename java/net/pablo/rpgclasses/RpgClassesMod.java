package net.pablo.rpgclasses;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.pablo.rpgclasses.effect.ModEffects;
import net.pablo.rpgclasses.item.ModCreativeModeTabs;
import net.pablo.rpgclasses.item.ModItems;
import net.pablo.rpgclasses.keybinds.KeyBindings;
import net.pablo.rpgclasses.network.NetworkHandler;
import net.pablo.rpgclasses.setup.ModIcons;
import net.pablo.rpgclasses.skills.PhantomStrikeManager;
import net.pablo.rpgclasses.skills.SeismicSmashManager;

@Mod(RpgClassesMod.MOD_ID)
public class RpgClassesMod {
    public static final String MOD_ID = "rpgclasses";

    public RpgClassesMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModCreativeModeTabs.register(modEventBus);

        modEventBus.addListener(this::setup);

        // Register items
        ModItems.ITEMS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);


    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // your code here
    }

    private void setup(final FMLCommonSetupEvent event) {
        // Register keybindings (works in your old style)
        MinecraftForge.EVENT_BUS.addListener(KeyBindings::register);

        // Register SkybreakerStrike events
        MinecraftForge.EVENT_BUS.addListener(SeismicSmashManager::onServerTick);

        // Register network channel
        NetworkHandler.register();

        ModIcons.register(event);
    }
}