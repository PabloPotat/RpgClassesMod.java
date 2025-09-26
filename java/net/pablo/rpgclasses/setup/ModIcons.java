package net.pablo.rpgclasses.setup;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.pablo.rpgclasses.item.ModItems;
import net.pablo.rpgclasses.registry.IconRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralized registry for all class, skill, and ability icons.
 * Ensures all icons are safely registered and available for HUD/XP display.
 */
public class ModIcons {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void register(final FMLCommonSetupEvent event) {
        // ----- CLASS ICONS -----
        IconRegistry.register("fighter", new ItemStack(Items.IRON_SWORD));
        IconRegistry.register("tank", new ItemStack(Items.SHIELD));
        IconRegistry.register("ranger", new ItemStack(Items.BOW));
        IconRegistry.register("gambler", new ItemStack(ModItems.LOADED_DICE.get()));

        // ----- SAFE MODDED ITEMS -----
        registerModItem("mage", "irons_spellbooks", "diamond_spell_book"); // Fixed mod ID
        registerModItem("rogue", "irons_spellbooks", "keeper_flamberge"); // Fixed item name

        // ----- FALLBACK ICONS -----
        // Add fallbacks for missing modded items
        if (!IconRegistry.hasIcon("mage")) {
            IconRegistry.register("mage", new ItemStack(Items.ENCHANTED_BOOK));
            LOGGER.warn("Modded mage icon not found, using fallback");
        }

        if (!IconRegistry.hasIcon("rogue")) {
            IconRegistry.register("rogue", new ItemStack(Items.IRON_SWORD));
            LOGGER.warn("Modded rogue icon not found, using fallback");
        }
    }

    /**
     * Safely register an icon for a class or skill from a modded item.
     * If the item is missing, registers an empty ItemStack to prevent crashes.
     */
    private static void registerModItem(String key, String modId, String itemName) {
        ResourceLocation id = new ResourceLocation(modId, itemName);
        if (ForgeRegistries.ITEMS.containsKey(id)) {
            IconRegistry.register(key, new ItemStack(ForgeRegistries.ITEMS.getValue(id)));
            LOGGER.info("Registered icon for {} using {}:{}", key, modId, itemName);
        } else {
            LOGGER.error("Failed to register icon for {}: Item {}:{} not found", key, modId, itemName);
            // Don't register empty stack yet - we'll use fallback in main register method
        }
    }
}