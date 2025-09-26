package net.pablo.rpgclasses.xphandlers;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.pablo.rpgclasses.capability.IPlayerClass;
import net.pablo.rpgclasses.utils.AFKUtils;
import net.pablo.rpgclasses.hud.XPDisplay;
import net.pablo.rpgclasses.registry.IconRegistry;

public class XPUtils {

    // Unified secondary XP multiplier
    public static final double SECONDARY_XP_MULTIPLIER = 0.55;

    /** Add XP and notify player (primary) - OLD METHOD (for compatibility) */
    public static void addXPAndCheckLevel(Player player, IPlayerClass cap, String className, int xpAmount) {
        addXPAndCheckLevel(player, cap, className, xpAmount, true); // Default to primary
    }

    /** Add XP and notify player (with primary flag) - NEW METHOD */
    public static void addXPAndCheckLevel(Player player, IPlayerClass cap, String className, int xpAmount, boolean primary) {
        if (player == null || cap == null || className == null) return;

        // Check AFK
        if (AFKUtils.isPlayerAFK(player)) return;

        // Normalize class key
        String key = className.toLowerCase();

        // Get the icon for this class
        ItemStack icon = IconRegistry.getIcon(key);

        // Show floating XP on HUD
        XPDisplay.showXP(xpAmount, icon, primary);

        // Get old level before adding XP
        int oldLevel = cap.getLevel(key);

        // Add XP
        cap.addXP(key, xpAmount);

        // Retrieve updated XP and level
        int newLevel = cap.getLevel(key);
        int newXP = cap.getXP(key);

        // Optional: chat notification
        player.sendSystemMessage(Component.literal(
                "Gained " + xpAmount + " XP for " + className +
                        " (Level: " + newLevel + ", XP: " + newXP + ")"
        ));

        // Notify player if they leveled up
        if (newLevel > oldLevel) {
            player.sendSystemMessage(Component.literal(
                    "Your " + className + " class leveled up to " + newLevel + "!"
            ));

            // Show level-up popup on HUD with icon
            XPDisplay.showLevelUp(newLevel, icon);
        }
    }

    /** Add secondary XP consistently */
    public static void addSecondaryXP(Player player, IPlayerClass cap, String className, int baseXP) {
        int secondaryXP = (int)(baseXP * SECONDARY_XP_MULTIPLIER);
        // Always mark secondary XP as not primary (right side)
        addXPAndCheckLevel(player, cap, className, secondaryXP, false);
    }

    /** Add primary XP (left side) */
    public static void addPrimaryXP(Player player, IPlayerClass cap, String className, int xpAmount) {
        addXPAndCheckLevel(player, cap, className, xpAmount, true);
    }
}