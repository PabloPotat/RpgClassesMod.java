package net.pablo.rpgclasses.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.network.NetworkEvent;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.capability.PlayerProgressionData;
import net.pablo.rpgclasses.progression.CustomStatDefinitions;
import net.pablo.rpgclasses.utils.DebugHelper;

import java.util.UUID;
import java.util.function.Supplier;

public class SpendCustomStatPacket {
    private final String line;      // "skill", "passive", "item"
    private final String statName;  // "Attack Power", "Vitality", etc.

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
            if (player == null) {
                System.err.println("[RPGClasses] SpendCustomStatPacket: Player is null!");
                return;
            }

            DebugHelper.showInfo(player, "Processing custom stat: " + statName + " for " + line);

            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                PlayerProgressionData progression = cap.getProgressionData();

                // Validate stat exists
                CustomStatDefinitions.CustomStat stat = CustomStatDefinitions.getStat(line, statName);
                if (stat == null) {
                    DebugHelper.showError(player, "Invalid stat: " + statName);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cAvailable stats for " + line + ":"));
                    for (String validStat : CustomStatDefinitions.getStatNames(line)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "  §7- " + validStat));
                    }
                    return;
                }

                // Check if player has available custom points FOR THIS LINE
                int availablePoints = progression.getAvailableCustomPoints(line);
                DebugHelper.showInfo(player, "Available custom points for " + line + ": " + availablePoints);

                if (availablePoints <= 0) {
                    DebugHelper.showError(player, "No custom stat points available for " + line +
                            " line! Unlock nodes 3 & 6.");
                    return;
                }

                // Check if stat is at max level
                int currentLevel = progression.getCustomStatLevel(line, statName);
                int maxLevel = stat.getMaxLevel();

                DebugHelper.showInfo(player, statName + " level: " + currentLevel + "/" + maxLevel);

                if (currentLevel >= maxLevel) {
                    DebugHelper.showWarning(player, statName + " is already at max level!");
                    return;
                }

                // Spend the point for this line
                progression.spendCustomStat(line, statName);
                int newLevel = currentLevel + 1;

                // Apply the custom stat effect
                applyCustomStatEffect(player, stat, newLevel);

                // Get updated available points after spending
                int remainingPoints = progression.getAvailableCustomPoints(line);

                // Notify player
                String valueDisplay = stat.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE ?
                        String.format("+%.0f%%", stat.getValuePerLevel() * 100) :
                        String.format("+%.1f", stat.getValuePerLevel());

                DebugHelper.showSuccess(player, statName + " → Level " + newLevel +
                        " (" + valueDisplay + ") | Remaining: " + remainingPoints);

                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a✓ " + statName + " → Level " + newLevel + " " + valueDisplay +
                                " §7(" + line + " points: " + remainingPoints + ")"));

                // Sync to client
                NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
            });
        });
        ctx.setPacketHandled(true);
        return true;
    }

    private void applyCustomStatEffect(ServerPlayer player, CustomStatDefinitions.CustomStat stat, int level) {
        DebugHelper.showInfo(player, "Applying " + stat.getName() + " Level " + level);

        AttributeInstance attribute = player.getAttribute(stat.getAttribute());
        if (attribute == null) {
            DebugHelper.showError(player, "Attribute not found: " + stat.getAttribute());
            return;
        }

        // Create unique UUID for this stat at this level
        UUID modifierId = UUID.nameUUIDFromBytes(
                ("custom_stat_" + stat.getName() + "_level_" + level).getBytes());

        // Remove old modifier if it exists (for level ups)
        if (level > 1) {
            UUID oldModifierId = UUID.nameUUIDFromBytes(
                    ("custom_stat_" + stat.getName() + "_level_" + (level - 1)).getBytes());
            AttributeModifier oldModifier = attribute.getModifier(oldModifierId);
            if (oldModifier != null) {
                attribute.removeModifier(oldModifierId);
            }
        }

        // Apply new modifier with cumulative value
        double totalValue = stat.getValuePerLevel() * level;
        AttributeModifier modifier = new AttributeModifier(
                modifierId,
                "custom_stat_" + stat.getName(),
                totalValue,
                stat.getOperation()
        );

        attribute.addPermanentModifier(modifier);

        // Format display value
        String displayValue = stat.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE ?
                String.format("%.0f%%", totalValue * 100) :
                String.format("%.1f", totalValue);

        DebugHelper.showSuccess(player, "Applied " + stat.getName() + ": " + displayValue +
                " (Level " + level + ")");

        System.out.println("[RPGClasses] Applied custom stat: " + stat.getName() +
                " Level " + level + " (" + displayValue + ") to " + player.getName().getString());
    }
}