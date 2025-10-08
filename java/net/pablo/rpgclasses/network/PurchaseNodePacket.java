package net.pablo.rpgclasses.network;

import net.minecraft.network.FriendlyByteBuf;
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
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNo class selected!"));
                    return;
                }

                // Get the node
                ProgressionNode node = NodeRegistry.getNode(className, nodeId);
                if (node == null) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cInvalid node!"));
                    return;
                }

                PlayerProgressionData progression = cap.getProgressionData();

                // Check if can purchase
                if (!NodeRegistry.canPurchaseNode(progression, node)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cCannot purchase this node! Check prerequisites."));
                    return;
                }

                // Check if player has enough skill points FOR THIS CLASS
                int availablePoints = progression.getSkillPoints(className);

                if (availablePoints < node.getCost()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§cNot enough " + className + " skill points! Need " + node.getCost() + ", have " + availablePoints));
                    return;
                }

                // Purchase the node (deducts from this class's points)
                progression.purchaseNode(node.getLine(), node.getId(), node.getCost(), className);

                // Apply reward
                applyNodeReward(player, node, progression);

                // Check if this unlocked a custom stat point
                if (node.getRewardType().equals("CUSTOM_POINT")) {
                    // FIXED: Calculate available custom points for this line
                    int availableCustomPoints = progression.getAvailableCustomPoints(node.getLine());
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§6✦ +1 Custom Stat Point for " + node.getLine() + " line! §7(Available: " + availableCustomPoints + ")"));
                }

                // Notify player
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§a✓ Unlocked: " + node.getDisplayName()));

                // Sync to client
                NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
            });
        });
        return true;
    }

    private void applyNodeReward(ServerPlayer player, ProgressionNode node, PlayerProgressionData progression) {
        switch (node.getRewardType()) {
            case "STAT":
                applyStatBonus(player, node.getRewardValue());
                break;

            case "CUSTOM_POINT":
                // Custom points are automatically tracked in purchaseNode()
                // Just notify the player
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§e⭐ Custom Stat Point earned! Open Custom Stats menu to spend."));
                break;

            case "UNLOCK":
                unlockFinalReward(player, node, progression);
                break;
        }
    }

    private void applyStatBonus(ServerPlayer player, String bonus) {
        // Parse bonus string like "+2 HP", "+1 ATK", "+2% DMG"
        try {
            if (bonus.contains("HP")) {
                int amount = Integer.parseInt(bonus.replaceAll("[^0-9]", ""));
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (attr != null) {
                    attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.randomUUID(), "progression_hp", amount,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                }
            } else if (bonus.contains("ATK") && !bonus.contains("%")) {
                int amount = Integer.parseInt(bonus.replaceAll("[^0-9]", ""));
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (attr != null) {
                    attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.randomUUID(), "progression_atk", amount,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                }
            } else if (bonus.contains("% DMG")) {
                double percent = Double.parseDouble(bonus.replaceAll("[^0-9.]", "")) / 100.0;
                var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (attr != null) {
                    attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.randomUUID(), "progression_dmg_pct", percent,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
                }
            }
            // Add more stat types as needed (Lifesteal, Speed, etc.)
        } catch (Exception e) {
            System.err.println("Failed to parse stat bonus: " + bonus);
        }
    }

    private void unlockFinalReward(ServerPlayer player, ProgressionNode node, PlayerProgressionData progression) {
        String line = node.getLine();

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