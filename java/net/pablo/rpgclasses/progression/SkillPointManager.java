package net.pablo.rpgclasses.progression;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.RpgClassesMod;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.capability.PlayerProgressionData;
import net.pablo.rpgclasses.network.NetworkHandler;
import net.pablo.rpgclasses.network.SyncProgressionPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages skill point rewards when players level up their class.
 * Grants 1 skill point per class level gained.
 */
@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID)
public class SkillPointManager {

    // Track last known level to detect level ups
    private static final Map<UUID, Integer> lastKnownLevel = new HashMap<>();

    // How many skill points per level
    private static final int POINTS_PER_LEVEL = 1;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;

        Player player = event.player;

        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) return;

            String className = cap.getSelectedClass().getClassName();
            int currentLevel = cap.getLevel(className);
            UUID playerId = player.getUUID();

            // FIXED: Get progression data here
            PlayerProgressionData progression = cap.getProgressionData();

            // Get last known level
            Integer lastLevel = lastKnownLevel.get(playerId);

            if (lastLevel == null) {
                // First time seeing this player, just store current level
                lastKnownLevel.put(playerId, currentLevel);

                // Initialize skill points based on current level FOR THIS CLASS
                if (progression.getSkillPoints(className) == 0 && currentLevel > 1) {
                    // Grant points for all past levels for this class
                    int pointsToGrant = (currentLevel - 1) * POINTS_PER_LEVEL;
                    progression.addSkillPoints(className, pointsToGrant);

                    if (player instanceof ServerPlayer serverPlayer) {
                        NetworkHandler.sendToClient(new SyncProgressionPacket(progression), serverPlayer);
                    }
                }
                return;
            }

            // Check if player leveled up
            if (currentLevel > lastLevel) {
                int levelsGained = currentLevel - lastLevel;
                int pointsToGrant = levelsGained * POINTS_PER_LEVEL;

                progression.addSkillPoints(className, pointsToGrant);

                // Update last known level
                lastKnownLevel.put(playerId, currentLevel);

                // Notify player
                if (player instanceof ServerPlayer serverPlayer) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§e⭐ +" + pointsToGrant + " " + className + " Skill Point" + (pointsToGrant > 1 ? "s" : "") +
                                    "! §7(" + progression.getSkillPoints(className) + " available)"
                    ));

                    // Sync to client
                    NetworkHandler.sendToClient(new SyncProgressionPacket(progression), serverPlayer);
                }
            } else if (currentLevel < lastLevel) {
                // Level decreased (maybe due to death penalty or reset)
                lastKnownLevel.put(playerId, currentLevel);
            }
        });
    }

    /**
     * Manually grant skill points to a player for a specific class
     */
    public static void grantSkillPoints(ServerPlayer player, String className, int amount) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            PlayerProgressionData progression = cap.getProgressionData();
            progression.addSkillPoints(className, amount);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§a✓ Granted " + amount + " " + className + " skill points!"
            ));

            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
        });
    }

    /**
     * Get how many points a player should have based on their level
     */
    public static int getExpectedSkillPoints(int classLevel) {
        return (classLevel - 1) * POINTS_PER_LEVEL;
    }
}