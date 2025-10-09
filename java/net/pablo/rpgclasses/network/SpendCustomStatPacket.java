package net.pablo.rpgclasses.network;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.capability.PlayerProgressionData;
import net.pablo.rpgclasses.progression.CustomStatDefinitions;

import java.util.function.Supplier;

public class SpendCustomStatPacket {
    private final String line;
    private final String statId;

    public SpendCustomStatPacket(String line, String statId) {
        this.line = line;
        this.statId = statId;
    }

    public SpendCustomStatPacket(FriendlyByteBuf buf) {
        this.line = buf.readUtf();
        this.statId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(line);
        buf.writeUtf(statId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                if (cap.getSelectedClass() == null) {
                    debugFail(player, "No class selected!");
                    return;
                }

                String className = cap.getSelectedClass().getClassName();
                PlayerProgressionData progression = cap.getProgressionData();

                // Validate stat exists
                CustomStatDefinitions.CustomStat stat = CustomStatDefinitions.getStat(className, line, statId);
                if (stat == null) {
                    debugFail(player, "Invalid stat: " + statId);
                    return;
                }

                // Check available custom points for this line
                int availablePoints = progression.getAvailableCustomPoints(line);
                int currentLevel = progression.getCustomStatLevel(line, statId);
                int maxLevel = stat.getMaxLevel();

                debugMessage(player, "§e[CUSTOM STAT] Attempting: %s (%s line)", stat.getDisplayName(), line);
                debugMessage(player, "§e[LEVELS] Current: %d | Max: %d", currentLevel, maxLevel);
                debugMessage(player, "§e[POINTS] Available: %d", availablePoints);

                if (availablePoints <= 0) {
                    debugFail(player, "No custom stat points for " + line + " line! Unlock nodes 3 & 6.");
                    return;
                }

                if (currentLevel >= maxLevel) {
                    debugFail(player, stat.getDisplayName() + " is already at max level (" + maxLevel + ")!");
                    return;
                }

                // Spend the point
                progression.spendCustomStat(line, statId);
                int newLevel = currentLevel + 1;
                int remainingPoints = progression.getAvailableCustomPoints(line);

                debugMessage(player, "§a[SUCCESS] %s: %d → %d", stat.getDisplayName(), currentLevel, newLevel);
                debugMessage(player, "§a[POINTS] Remaining: %d", remainingPoints);

                // Apply the custom stat effect
                applyCustomStatEffect(player, stat, newLevel);

                // Success particles
                spawnParticles(player, ParticleTypes.ENCHANTED_HIT, 25);

                // Success message
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a✓ " + stat.getDisplayName() + " §f" + currentLevel + " → " + newLevel + " / " + maxLevel));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7   " + stat.getEffectPerLevel()));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§e" + line.toUpperCase() + " Points: §f" + remainingPoints + " remaining"));

                // Sync to client
                NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
            });
        });
        ctx.setPacketHandled(true);
        return true;
    }

    private void applyCustomStatEffect(ServerPlayer player, CustomStatDefinitions.CustomStat stat, int level) {
        debugMessage(player, "§d[EFFECT] Applying %s L%d", stat.getDisplayName(), level);

        // Get the modifier UUID (unique per stat)
        java.util.UUID modifierUUID = java.util.UUID.nameUUIDFromBytes((stat.getId() + "_custom_stat").getBytes());

        // Calculate the total value for this level
        double totalValue = stat.getValuePerLevel() * level;
        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation operation;

        if (stat.isPercentage()) {
            operation = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE;
            debugMessage(player, "§b[STAT] %s: +%.1f%%", stat.getDisplayName(), totalValue * 100);
        } else {
            operation = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION;
            debugMessage(player, "§b[STAT] %s: +%.1f", stat.getDisplayName(), totalValue);
        }

        // Get the appropriate Minecraft attribute
        net.minecraft.world.entity.ai.attributes.Attribute minecraftAttr = getMinecraftAttribute(stat.getAttribute());

        if (minecraftAttr != null) {
            var attr = player.getAttribute(minecraftAttr);
            if (attr != null) {
                // Remove old modifier if it exists
                attr.removeModifier(modifierUUID);

                // Add new modifier with updated value
                attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        modifierUUID,
                        "custom_stat_" + stat.getId(),
                        totalValue,
                        operation
                ));

                debugMessage(player, "§a[APPLIED] Attribute modifier updated successfully");

                // Special case: If it's max health, heal the player for the difference
                if (stat.getAttribute() == CustomStatDefinitions.MinecraftAttribute.MAX_HEALTH) {
                    player.setHealth(Math.min(player.getHealth() + (float)stat.getValuePerLevel(), player.getMaxHealth()));
                    debugMessage(player, "§c[HEAL] +%.1f HP restored", stat.getValuePerLevel());
                }
            } else {
                debugMessage(player, "§c[ERROR] Could not find attribute for %s", stat.getAttribute());
            }
        } else {
            debugMessage(player, "§c[ERROR] Unknown attribute type: %s", stat.getAttribute());
        }

        spawnParticles(player, ParticleTypes.WITCH, 15);
    }

    private net.minecraft.world.entity.ai.attributes.Attribute getMinecraftAttribute(CustomStatDefinitions.MinecraftAttribute attr) {
        switch (attr) {
            case MAX_HEALTH:
                return net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
            case ARMOR:
                return net.minecraft.world.entity.ai.attributes.Attributes.ARMOR;
            case ARMOR_TOUGHNESS:
                return net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS;
            case ATTACK_DAMAGE:
                return net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
            case ATTACK_SPEED:
                return net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED;
            case MOVEMENT_SPEED:
                return net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
            case ATTACK_KNOCKBACK:
                return net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK;
            case KNOCKBACK_RESISTANCE:
                return net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE;
            default:
                return null;
        }
    }

    // Debug utilities
    private void debugMessage(ServerPlayer player, String message, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
    }

    private void debugFail(ServerPlayer player, String message) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[FAIL] " + message));
        spawnParticles(player, ParticleTypes.SMOKE, 10);
    }

    private void spawnParticles(ServerPlayer player, net.minecraft.core.particles.ParticleOptions particle, int count) {
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