package net.pablo.rpgclasses.utils;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Visual debugging helper using particles and chat messages
 * This version is less noisy - only shows important messages
 */
public class DebugHelper {

    // Toggle to enable/disable debug messages (set to false to reduce spam)
    public static boolean SHOW_DEBUG_MESSAGES = false;

    /**
     * Show success particles (green)
     */
    public static void showSuccess(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("§a✓ " + message));
        spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, 10);
    }

    /**
     * Show error particles (red)
     */
    public static void showError(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("§c✗ " + message));
        spawnParticles(player, ParticleTypes.ANGRY_VILLAGER, 10);
    }

    /**
     * Show warning particles (orange)
     */
    public static void showWarning(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("§6⚠ " + message));
        spawnParticles(player, ParticleTypes.FLAME, 5);
    }

    /**
     * Show info particles (blue) - Only if debug is enabled
     */
    public static void showInfo(ServerPlayer player, String message) {
        if (!SHOW_DEBUG_MESSAGES) return; // Skip if debug disabled

        player.sendSystemMessage(Component.literal("§b● [DEBUG] " + message));
        spawnParticles(player, ParticleTypes.ENCHANT, 5);
    }

    /**
     * Spawn particles around player
     */
    private static void spawnParticles(ServerPlayer player, net.minecraft.core.particles.ParticleOptions particle, int count) {
        if (player.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = player.position().add(0, player.getEyeHeight(), 0);
            serverLevel.sendParticles(particle,
                    pos.x, pos.y, pos.z,
                    count, 0.5, 0.5, 0.5, 0.1);
        }
    }

    /**
     * Debug capability data - Only if debug is enabled
     */
    public static void debugCapability(ServerPlayer player) {
        if (!SHOW_DEBUG_MESSAGES) {
            player.sendSystemMessage(Component.literal("§7[Debug mode is OFF. Set DebugHelper.SHOW_DEBUG_MESSAGES = true to enable]"));
            return;
        }

        player.getCapability(net.pablo.rpgclasses.capability.PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            String className = cap.getSelectedClass() != null ? cap.getSelectedClass().getClassName() : "NONE";
            int level = cap.getSelectedClass() != null ? cap.getLevel(className) : 0;

            player.sendSystemMessage(Component.literal("§6=== CAPABILITY DEBUG ==="));
            player.sendSystemMessage(Component.literal("§eClass: §f" + className));
            player.sendSystemMessage(Component.literal("§eLevel: §f" + level));

            var progression = cap.getProgressionData();
            player.sendSystemMessage(Component.literal("§eSkill Points (" + className + "): §f" +
                    progression.getSkillPoints(className)));
            player.sendSystemMessage(Component.literal("§eTotal Spent: §f" +
                    progression.getTotalSpentLevels()));

            player.sendSystemMessage(Component.literal("§6Purchased Nodes:"));
            for (String line : new String[]{"skill", "passive", "item"}) {
                var nodes = progression.getPurchasedNodes(line);
                player.sendSystemMessage(Component.literal("  §e" + line + ": §f" +
                        String.join(", ", nodes)));
            }
        });
    }
}