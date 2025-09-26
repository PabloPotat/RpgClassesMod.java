package net.pablo.rpgclasses.item;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.pablo.rpgclasses.RpgClassesMod;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RpgClassesMod.MOD_ID);

    public static  final RegistryObject<Item> LOADED_DICE = ITEMS.register("loaded_dice",
            () -> new LoadedDice(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> WEIGHTED_DICE = ITEMS.register("weighted_dice",
            () -> new WeightedDice(new Item.Properties().stacksTo(64)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
