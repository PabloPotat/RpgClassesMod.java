package net.pablo.rpgclasses.registry;

import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for mapping string keys (classes, skills, abilities) to ItemStack icons.
 * Can be used for HUD, XP display, skill UI, etc.
 */
public class IconRegistry {

    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    /**
     * Register an icon with a string key.
     * @param key The key representing a class, skill, or ability.
     * @param icon The ItemStack to display as an icon.
     */
    public static void register(String key, ItemStack icon) {
        ICONS.put(key, icon);
    }

    /**
     * Get the icon for a given key.
     * @param key The key representing a class, skill, or ability.
     * @return The ItemStack icon, or ItemStack.EMPTY if none registered.
     */
    public static ItemStack getIcon(String key) {
        return ICONS.getOrDefault(key, ItemStack.EMPTY);
    }

    /**
     * Check if an icon exists for a given key.
     * @param key The key to check.
     * @return True if an icon is registered for this key.
     */
    public static boolean hasIcon(String key) {
        return ICONS.containsKey(key);
    }
}
