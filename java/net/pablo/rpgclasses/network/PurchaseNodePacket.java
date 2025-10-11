package net.pablo.rpgclasses.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.capability.PlayerProgressionData;
import net.pablo.rpgclasses.progression.NodeRegistry;
import net.pablo.rpgclasses.progression.ProgressionNode;
import net.pablo.rpgclasses.utils.DebugHelper;

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
            if (player == null) {
                System.err.println("[RPGClasses] PurchaseNodePacket: Player is null!");
                return;
            }

            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                String className = cap.getSelectedClass() != null ?
                        cap.getSelectedClass().getClassName() : null;

                if (className == null) {
                    DebugHelper.showError(player, "No class selected!");
                    return;
                }

                DebugHelper.showInfo(player, "Processing node purchase: " + nodeId);

                // Get the node
                ProgressionNode node = NodeRegistry.getNode(className, nodeId);
                if (node == null) {
                    DebugHelper.showError(player, "Invalid node: " + nodeId);
                    return;
                }

                PlayerProgressionData progression = cap.getProgressionData();

                // Debug current state
                DebugHelper.showInfo(player, "Node: " + node.getDisplayName() +
                        " | Line: " + node.getLine() + " | Cost: " + node.getCost());

                // Check if already purchased
                if (progression.hasNode(node.getLine(), nodeId)) {
                    DebugHelper.showWarning(player, "Already purchased!");
                    return;
                }

                // Check prerequisites
                if (!NodeRegistry.canPurchaseNode(progression, node)) {
                    DebugHelper.showError(player, "Prerequisites not met!");

                    if (node.hasPrerequisites()) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cRequired:"));
                        for (String prereq : node.getPrerequisites()) {
                            boolean has = progression.hasNode(node.getLine(), prereq);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "  " + (has ? "§a✓" : "§c✗") + " §f" + prereq));
                        }
                    }
                    return;
                }

                // Check skill points FOR THIS CLASS
                int availablePoints = progression.getSkillPoints(className);
                DebugHelper.showInfo(player, "Points check: Have " + availablePoints +
                        ", Need " + node.getCost());

                if (availablePoints < node.getCost()) {
                    DebugHelper.showError(player, "Not enough " + className +
                            " skill points! Need " + node.getCost() + ", have " + availablePoints);
                    return;
                }

                // Purchase the node (THIS DEDUCTS POINTS)
                int pointsBefore = progression.getSkillPoints(className);
                progression.purchaseNode(node.getLine(), nodeId, node.getCost(), className);
                int pointsAfter = progression.getSkillPoints(className);

                DebugHelper.showSuccess(player, "Node purchased! Points: " +
                        pointsBefore + " → " + pointsAfter);

                // Apply reward
                applyNodeReward(player, node, progression);

                // Check if this unlocked a custom stat point
                if (node.getRewardType().equals("CUSTOM_POINT")) {
                    int availableCustomPoints = progression.getAvailableCustomPoints(node.getLine());
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§6✦ +1 Custom Stat Point for " + node.getLine() +
                                    " line! §7(Available: " + availableCustomPoints + ")"));
                }

                // Notify player
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a✓ Unlocked: " + node.getDisplayName()));

                // Sync to client
                NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
            });
        });
        ctx.setPacketHandled(true);
        return true;
    }

    private void applyNodeReward(ServerPlayer player, ProgressionNode node,
                                 PlayerProgressionData progression) {
        DebugHelper.showInfo(player, "Applying reward: " + node.getRewardType() +
                " | " + node.getRewardValue());

        switch (node.getRewardType()) {
            case "STAT":
                applyStatBonus(player, node.getRewardValue());
                break;

            case "CUSTOM_POINT":
                // Custom points are automatically tracked in purchaseNode()
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§e⭐ Custom Stat Point earned! Open Custom Stats menu to spend."));
                break;

            case "UNLOCK":
                unlockFinalReward(player, node, progression);
                break;

            default:
                DebugHelper.showWarning(player, "Unknown reward type: " + node.getRewardType());
        }
    }

    private void applyStatBonus(ServerPlayer player, String bonus) {
        DebugHelper.showInfo(player, "Applying stat bonus: " + bonus);

        try {
            if (bonus.contains("HP")) {
                int amount = Integer.parseInt(bonus.replaceAll("[^0-9]", ""));
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (attr != null) {
                    attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.randomUUID(), "progression_hp", amount,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                    DebugHelper.showSuccess(player, "Applied +" + amount + " HP");
                }
            } else if (bonus.contains("ATK") && !bonus.contains("%")) {
                int amount = Integer.parseInt(bonus.replaceAll("[^0-9]", ""));
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (attr != null) {
                    attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.randomUUID(), "progression_atk", amount,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                    DebugHelper.showSuccess(player, "Applied +" + amount + " ATK");
                }
            } else if (bonus.contains("% DMG") || bonus.contains("%DMG")) {
                double percent = Double.parseDouble(bonus.replaceAll("[^0-9.]", "")) / 100.0;
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (attr != null) {
                    attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.randomUUID(), "progression_dmg_pct", percent,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
                    DebugHelper.showSuccess(player, "Applied +" + (percent * 100) + "% DMG");
                }
            } else if (bonus.contains("Lifesteal") || bonus.contains("lifesteal")) {
                // Store lifesteal in progression data or apply via event handler
                DebugHelper.showWarning(player, "Lifesteal bonus (not yet implemented)");
            }
        } catch (Exception e) {
            DebugHelper.showError(player, "Failed to parse bonus: " + bonus);
            System.err.println("[RPGClasses] Failed to parse stat bonus: " + bonus);
            e.printStackTrace();
        }
    }

    private void unlockFinalReward(ServerPlayer player, ProgressionNode node,
                                   PlayerProgressionData progression) {
        String line = node.getLine();

        DebugHelper.showSuccess(player, "Unlocking final reward for " + line + " line!");

        switch (line) {
            case "skill":
                progression.setSkillUnlocked(true);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§6§l✦ SKILL UNLOCKED: " + node.getDisplayName() + " ✦"));
                break;

            case "passive":
                progression.setPassiveUnlocked(true);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§d§l✦ PASSIVE UNLOCKED: " + node.getDisplayName() + " ✦"));
                break;

            case "item":
                progression.setItemUnlocked(true);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§e§l✦ ITEM UNLOCKED: " + node.getDisplayName() + " ✦"));
                // TODO: Give the actual item to the player
                break;
        }
    }
}