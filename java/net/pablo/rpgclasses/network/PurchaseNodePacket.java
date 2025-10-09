package net.pablo.rpgclasses.network;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.capability.PlayerProgressionData;
import net.pablo.rpgclasses.progression.NodeRegistry;
import net.pablo.rpgclasses.progression.ProgressionNode;

import java.util.function.Supplier;

public class PurchaseNodePacket {
    private final String nodeId;

    public PurchaseNodePacket(String nodeId) {
        this.nodeId = nodeId;
    }

    public PurchaseNodePacket(FriendlyByteBuf buf) {
        this.nodeId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(nodeId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                String className = cap.getSelectedClass() != null ?
                        cap.getSelectedClass().getClassName() : null;

                if (className == null) {
                    debugFail(player, "No class selected!");
                    return;
                }

                ProgressionNode node = NodeRegistry.getNode(className, nodeId);
                if (node == null) {
                    debugFail(player, "Invalid node: " + nodeId);
                    return;
                }

                PlayerProgressionData progression = cap.getProgressionData();

                // Check if already purchased
                if (progression.hasNode(node.getLine(), nodeId)) {
                    debugFail(player, "Already purchased: " + node.getDisplayName());
                    return;
                }

                // Check prerequisites
                if (!NodeRegistry.canPurchaseNode(progression, node)) {
                    debugFail(player, "Prerequisites not met for: " + node.getDisplayName());
                    if (node.hasPrerequisites()) {
                        for (String prereq : node.getPrerequisites()) {
                            boolean has = progression.hasNode(node.getLine(), prereq);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "§7  - " + prereq + ": " + (has ? "§a✓" : "§c✗")));
                        }
                    }
                    return;
                }

                // Check skill points
                int availablePoints = progression.getSkillPoints(className);
                int nodeCost = node.getCost();

                debugMessage(player, "§e[PURCHASE] Attempting: %s", node.getDisplayName());
                debugMessage(player, "§e[COST] Need: %d | Have: %d", nodeCost, availablePoints);

                if (availablePoints < nodeCost) {
                    debugFail(player, "Not enough points! Need " + nodeCost + ", have " + availablePoints);
                    return;
                }

                // CRITICAL: Deduct points BEFORE purchasing node
                int pointsBefore = availablePoints;
                progression.addSkillPoints(className, -nodeCost); // Deduct the cost
                int pointsAfterDeduction = progression.getSkillPoints(className);

                debugMessage(player, "§e[DEDUCT] %d - %d = %d points",
                        pointsBefore, nodeCost, pointsAfterDeduction);

                // Now purchase the node (this just marks it as owned)
                progression.purchaseNode(node.getLine(), node.getId(), 0, className);

                debugMessage(player, "§a[SUCCESS] Node purchased! Points remaining: %d",
                        pointsAfterDeduction);

                // Apply reward
                applyNodeReward(player, node, progression, className);

                // Success particles
                spawnParticles(player, ParticleTypes.ENCHANT, 30);

                // Get final points after everything
                int finalPoints = progression.getSkillPoints(className);

                // Success message
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a✓ Unlocked: §f" + node.getDisplayName() +
                                " §7(" + node.getLine() + " | -" + nodeCost + " pts | " + finalPoints + " remaining)"
                ));

                // Sync to client
                NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
            });
        });
        return true;
    }

    private void applyNodeReward(ServerPlayer player, ProgressionNode node,
                                 PlayerProgressionData progression, String className) {
        String rewardType = node.getRewardType();

        debugMessage(player, "§d[REWARD] Type: %s | Value: %s", rewardType, node.getRewardValue());

        switch (rewardType) {
            case "STAT":
                applyStatBonus(player, node.getRewardValue());
                break;

            case "CUSTOM_POINT":
                int availableCustom = progression.getAvailableCustomPoints(node.getLine());
                debugMessage(player, "§6[CUSTOM] +1 point for %s line (Total: %d)",
                        node.getLine(), availableCustom);

                spawnParticles(player, ParticleTypes.TOTEM_OF_UNDYING, 20);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§6✦ +1 Custom Stat Point for " + node.getLine() +
                                " line! §7(Available: " + availableCustom + ")"));
                break;

            case "UNLOCK":
                unlockFinalReward(player, node, progression);
                break;

            default:
                debugMessage(player, "§c[WARN] Unknown reward type: %s", rewardType);
        }
    }

    private void applyStatBonus(ServerPlayer player, String bonus) {
        debugMessage(player, "§b[STAT] Applying: %s", bonus);

        try {
            if (bonus.contains("HP")) {
                int amount = Integer.parseInt(bonus.replaceAll("[^0-9]", ""));
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (attr != null) {
                    attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.randomUUID(), "progression_hp", amount,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                    player.setHealth(player.getHealth() + amount); // Heal for the bonus
                    debugMessage(player, "§a[STAT] +%d HP applied", amount);
                }
            } else if (bonus.contains("ATK") && !bonus.contains("%")) {
                int amount = Integer.parseInt(bonus.replaceAll("[^0-9]", ""));
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (attr != null) {
                    attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.randomUUID(), "progression_atk", amount,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                    debugMessage(player, "§a[STAT] +%d ATK applied", amount);
                }
            } else if (bonus.contains("% DMG") || bonus.contains("%DMG")) {
                double percent = Double.parseDouble(bonus.replaceAll("[^0-9.]", "")) / 100.0;
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (attr != null) {
                    attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.randomUUID(), "progression_dmg_pct", percent,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
                    debugMessage(player, "§a[STAT] +%.1f%% DMG applied", percent * 100);
                }
            } else if (bonus.contains("Lifesteal") || bonus.contains("lifesteal")) {
                debugMessage(player, "§e[STAT] Lifesteal tracking not yet implemented");
            }
        } catch (Exception e) {
            debugMessage(player, "§c[ERROR] Failed to parse stat: %s", bonus);
        }
    }

    private void unlockFinalReward(ServerPlayer player, ProgressionNode node, PlayerProgressionData progression) {
        String line = node.getLine();
        debugMessage(player, "§5[UNLOCK] Final reward for %s line!", line);

        switch (line) {
            case "skill":
                progression.setSkillUnlocked(true);
                spawnParticles(player, ParticleTypes.DRAGON_BREATH, 50);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§6§l✦ SKILL UNLOCKED: " + node.getDisplayName() + " ✦"));
                break;

            case "passive":
                progression.setPassiveUnlocked(true);
                spawnParticles(player, ParticleTypes.ENCHANTED_HIT, 50);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§d§l✦ PASSIVE UNLOCKED: " + node.getDisplayName() + " ✦"));
                break;

            case "item":
                progression.setItemUnlocked(true);
                spawnParticles(player, ParticleTypes.END_ROD, 50);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§e§l✦ ITEM UNLOCKED: " + node.getDisplayName() + " ✦"));
                break;
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