package net.pablo.rpgclasses.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.capability.PlayerProgressionData;
import net.pablo.rpgclasses.network.NetworkHandler;
import net.pablo.rpgclasses.network.SyncProgressionPacket;
import net.pablo.rpgclasses.progression.NodeRegistry;
import net.pablo.rpgclasses.progression.ProgressionNode;
import net.pablo.rpgclasses.utils.DebugHelper;

/**
 * Test commands to verify progression system is working
 */
@Mod.EventBusSubscriber
public class TestCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("testprogression")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("setup").executes(ctx ->
                                setupTestEnvironment(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("verify").executes(ctx ->
                                verifyProgression(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("buysequence").executes(ctx ->
                                testSequentialBuying(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("pointstest").executes(ctx ->
                                testPointDeduction(ctx.getSource().getPlayerOrException())))
        );
    }

    /**
     * Set up a test environment with class and points
     */
    private static int setupTestEnvironment(ServerPlayer player) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            DebugHelper.showInfo(player, "Setting up test environment...");

            // Check if class is set
            if (cap.getSelectedClass() == null) {
                DebugHelper.showError(player, "No class selected! Use /setclass warrior first");
                return;
            }

            String className = cap.getSelectedClass().getClassName();

            // Set level to 100 for testing
            cap.setLevel(className, 100);
            DebugHelper.showSuccess(player, "Set level to 100");

            // Give 100 skill points
            PlayerProgressionData progression = cap.getProgressionData();
            progression.setSkillPoints(className, 100);
            DebugHelper.showSuccess(player, "Set skill points to 100");

            // Sync to client
            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);

            DebugHelper.showSuccess(player, "Test environment ready!");
            DebugHelper.debugCapability(player);
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Verify all progression data is correct
     */
    private static int verifyProgression(ServerPlayer player) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                DebugHelper.showError(player, "No class selected!");
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            PlayerProgressionData progression = cap.getProgressionData();

            DebugHelper.showInfo(player, "Verifying progression system...");

            // Check 1: Skill points
            int points = progression.getSkillPoints(className);
            if (points > 0) {
                DebugHelper.showSuccess(player, "✓ Skill points: " + points);
            } else {
                DebugHelper.showError(player, "✗ No skill points!");
            }

            // Check 2: Node registry
            int nodeCount = NodeRegistry.getNodesForClass(className).size();
            if (nodeCount > 0) {
                DebugHelper.showSuccess(player, "✓ Nodes loaded: " + nodeCount);
            } else {
                DebugHelper.showError(player, "✗ No nodes found for " + className);
            }

            // Check 3: First nodes are purchasable
            for (String line : new String[]{"skill", "passive", "item"}) {
                ProgressionNode firstNode = NodeRegistry.getNodesForLine(className, line).get(0);
                boolean canBuy = NodeRegistry.canPurchaseNode(progression, firstNode);

                if (canBuy) {
                    DebugHelper.showSuccess(player, "✓ Can purchase first " + line + " node");
                } else {
                    DebugHelper.showError(player, "✗ Cannot purchase first " + line + " node");
                }
            }

            // Check 4: Purchased nodes
            int totalPurchased = progression.getTotalSpentLevels();
            DebugHelper.showInfo(player, "Total nodes purchased: " + totalPurchased);

            DebugHelper.showSuccess(player, "Verification complete!");
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Test buying nodes in sequence
     */
    private static int testSequentialBuying(ServerPlayer player) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                DebugHelper.showError(player, "No class selected!");
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            PlayerProgressionData progression = cap.getProgressionData();

            DebugHelper.showInfo(player, "Testing sequential node purchasing...");

            // Try to buy first 3 nodes in skill line
            String line = "skill";
            var nodes = NodeRegistry.getNodesForLine(className, line);

            for (int i = 0; i < Math.min(3, nodes.size()); i++) {
                ProgressionNode node = nodes.get(i);

                DebugHelper.showInfo(player, "Attempting to buy: " + node.getId());

                // Check if already purchased
                if (progression.hasNode(line, node.getId())) {
                    DebugHelper.showWarning(player, "Already purchased!");
                    continue;
                }

                // Check prerequisites
                if (!NodeRegistry.canPurchaseNode(progression, node)) {
                    DebugHelper.showError(player, "Prerequisites not met for " + node.getId());

                    if (node.hasPrerequisites()) {
                        for (String prereq : node.getPrerequisites()) {
                            boolean has = progression.hasNode(line, prereq);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "  " + (has ? "§a✓" : "§c✗") + " " + prereq));
                        }
                    }
                    continue;
                }

                // Check points
                int pointsBefore = progression.getSkillPoints(className);
                if (pointsBefore < node.getCost()) {
                    DebugHelper.showError(player, "Not enough points! Have " + pointsBefore +
                            ", need " + node.getCost());
                    break;
                }

                // Purchase
                progression.purchaseNode(line, node.getId(), node.getCost(), className);
                int pointsAfter = progression.getSkillPoints(className);

                DebugHelper.showSuccess(player, "Purchased " + node.getDisplayName() +
                        "! Points: " + pointsBefore + " → " + pointsAfter);

                // Verify the purchase
                if (!progression.hasNode(line, node.getId())) {
                    DebugHelper.showError(player, "ERROR: Node not marked as purchased!");
                }
            }

            // Sync to client
            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);

            DebugHelper.showSuccess(player, "Sequential test complete!");
            DebugHelper.debugCapability(player);
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Test that points are being deducted correctly
     */
    private static int testPointDeduction(ServerPlayer player) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                DebugHelper.showError(player, "No class selected!");
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            PlayerProgressionData progression = cap.getProgressionData();

            DebugHelper.showInfo(player, "Testing point deduction...");

            // Set known amount
            progression.setSkillPoints(className, 50);
            int initialPoints = progression.getSkillPoints(className);
            DebugHelper.showInfo(player, "Initial points: " + initialPoints);

            // Manually deduct points
            progression.purchaseNode("skill", "test_node_1", 10, className);
            int afterDeduct = progression.getSkillPoints(className);

            if (afterDeduct == initialPoints - 10) {
                DebugHelper.showSuccess(player, "✓ Points deducted correctly: " +
                        initialPoints + " → " + afterDeduct);
            } else {
                DebugHelper.showError(player, "✗ Points NOT deducted! Still at: " + afterDeduct);
            }

            // Try the overload method (without className)
            int beforeOverload = progression.getSkillPoints(className);
            progression.purchaseNode("passive", "test_node_2", 5);
            int afterOverload = progression.getSkillPoints(className);

            if (afterOverload == beforeOverload) {
                DebugHelper.showInfo(player, "Overload method doesn't deduct points (expected)");
            } else {
                DebugHelper.showWarning(player, "Overload method deducted points (unexpected)");
            }

            // Reset points
            progression.setSkillPoints(className, 100);
            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);

            DebugHelper.showSuccess(player, "Point deduction test complete!");
        });
        return Command.SINGLE_SUCCESS;
    }
}