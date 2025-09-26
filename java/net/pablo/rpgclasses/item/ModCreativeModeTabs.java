package net.pablo.rpgclasses.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.pablo.rpgclasses.RpgClassesMod;

import java.awt.*;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RpgClassesMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> RPG_CLASSES_TAB = CREATIVE_MODE_TABS.register("rpg_classes_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.LOADED_DICE.get())) // Icon = Loaded Dice
                    .title(Component.translatable("rpg_classes_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        // Add all your RPG items here
                        pOutput.accept(ModItems.LOADED_DICE.get());
                        pOutput.accept(ModItems.WEIGHTED_DICE.get());
                    })
                    .build());


    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
