package net.pablo.rpgclasses.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.capability.PlayerProgressionData;

import java.util.function.Supplier;

public class SpendCustomStatPacket {
    private final String line;      // "skill", "passive", "item"
    private final String statName;  // "Blood Resonance", "Crimson Fury", etc.

    public SpendCustomStatPacket(String line, String statName) {
        this.line = line;
        this.statName = statName;
    }

    public SpendCustomStatPacket(FriendlyByteBuf buf) {
        this.line = buf.readUtf();
        this.statName = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(line);
        buf.writeUtf(statName);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                PlayerProgressionData progression = cap.getProgressionData();

                // FIXED: Check if player has available custom points FOR THIS LINE
                int availablePoints = progression.getAvailableCustomPoints(line);

                if (availablePoints <= 0) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cNo custom stat points available for " + line + " line! Unlock nodes 3 & 6."));
                    return;
                }

                // Check if stat is at max level
                int currentLevel = progression.getCustomStatLevel(line, statName);
                int maxLevel = getMaxLevelForStat(statName);

                if (currentLevel >= maxLevel) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c" + statName + " is already at max level!"));
                    return;
                }

                // FIXED: Spend the point for this line (no need for separate method)
                progression.spendCustomStat(line, statName);

                // Apply the custom stat effect
                applyCustomStatEffect(player, line, statName, currentLevel + 1);

                // FIXED: Get updated available points after spending
                int remainingPoints = progression.getAvailableCustomPoints(line);

                // Notify player
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a✓ " + statName + " → Level " + (currentLevel + 1) +
                                " §7(" + line + " points: " + remainingPoints + ")"));

                // Sync to client
                NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
            });
        });
        return true;
    }

    private int getMaxLevelForStat(String statName) {
        // Define max levels for custom stats
        // This should match your custom stat definitions
        return 5; // Default max
    }

    private void applyCustomStatEffect(ServerPlayer player, String line, String statName, int level) {
        // Apply the actual custom stat bonus
        // This depends on your stat definitions

        // Example for Warrior stats:
        if (statName.equals("Blood Resonance")) {
            // +3% Lifesteal effectiveness per level
            // You'd need to implement lifesteal tracking
        } else if (statName.equals("Crimson Fury")) {
            // +4% Low HP damage bonus per level
            // Apply damage modifier when HP is low
        }
        // Add more custom stat effects based on your design

        System.out.println("[RPGClasses] Applied custom stat: " + statName + " Level " + level + " to " + player.getName().getString());
    }
}