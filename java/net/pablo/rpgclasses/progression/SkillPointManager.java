package net.pablo.rpgclasses.progression;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
    private static final int POINTS_PER_LEVEL = 1;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;

        Player player = event.player;

        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                debugMessage(player, "§7[DEBUG] No class selected");
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            int currentLevel = cap.getLevel(className);
            UUID playerId = player.getUUID();
            PlayerProgressionData progression = cap.getProgressionData();

            Integer lastLevel = lastKnownLevel.get(playerId);

            if (lastLevel == null) {
                // First time seeing this player
                lastKnownLevel.put(playerId, currentLevel);

                int currentPoints = progression.getSkillPoints(className);
                debugMessage(player, "§b[INIT] " + className + " L" + currentLevel + " | Points: " + currentPoints);

                // Grant points for existing levels if none exist
                if (currentPoints == 0 && currentLevel > 1) {
                    int pointsToGrant = (currentLevel - 1) * POINTS_PER_LEVEL;
                    progression.addSkillPoints(className, pointsToGrant);

                    debugMessage(player, "§a[GRANT] +%d points for existing levels", pointsToGrant);
                    spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, 10);

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
                int pointsBefore = progression.getSkillPoints(className);

                progression.addSkillPoints(className, pointsToGrant);
                int pointsAfter = progression.getSkillPoints(className);

                lastKnownLevel.put(playerId, currentLevel);

                // Debug feedback
                debugMessage(player, "§6[LEVEL UP] %s: %d → %d", className, lastLevel, currentLevel);
                debugMessage(player, "§6[POINTS] Before: %d | Granted: +%d | After: %d",
                        pointsBefore, pointsToGrant, pointsAfter);

                // Visual feedback
                spawnParticles(player, ParticleTypes.END_ROD, 20);
                spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, 15);

                if (player instanceof ServerPlayer serverPlayer) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§e⭐ +" + pointsToGrant + " " + className + " Skill Point" +
                                    (pointsToGrant > 1 ? "s" : "") +
                                    "! §7(Total: " + pointsAfter + ")"
                    ));
                    NetworkHandler.sendToClient(new SyncProgressionPacket(progression), serverPlayer);
                }
            } else if (currentLevel < lastLevel) {
                debugMessage(player, "§c[WARN] Level decreased: %d → %d", lastLevel, currentLevel);
                lastKnownLevel.put(playerId, currentLevel);
            }
        });
    }

    public static void grantSkillPoints(ServerPlayer player, String className, int amount) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            PlayerProgressionData progression = cap.getProgressionData();
            int before = progression.getSkillPoints(className);

            progression.addSkillPoints(className, amount);
            int after = progression.getSkillPoints(className);

            debugMessage(player, "§a[MANUAL GRANT] %s: %d + %d = %d", className, before, amount, after);
            spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, 15);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§a✓ Granted " + amount + " " + className + " skill points! §7(Total: " + after + ")"
            ));

            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
        });
    }

    public static int getExpectedSkillPoints(int classLevel) {
        return (classLevel - 1) * POINTS_PER_LEVEL;
    }

    // Debug utilities
    private static void debugMessage(Player player, String message, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
    }

    private static void spawnParticles(Player player, net.minecraft.core.particles.ParticleOptions particle, int count) {
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    particle,
                    player.getX(),
                    player.getY() + 1.0,
                    player.getZ(),
                    count,
                    0.3, 0.5, 0.3,
                    0.1
            );
        }
    }
}