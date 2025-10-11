package net.pablo.rpgclasses.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.capability.IPlayerClass;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.capability.PlayerProgressionData;
import net.pablo.rpgclasses.network.NetworkHandler;
import net.pablo.rpgclasses.network.SyncProgressionPacket;
import net.pablo.rpgclasses.progression.NodeRegistry;
import net.pablo.rpgclasses.progression.ProgressionNode;
import net.pablo.rpgclasses.registry.RPGClassRegistry;
import net.pablo.rpgclasses.utils.DebugHelper;

import java.util.List;

@Mod.EventBusSubscriber
public class ModCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // ------------------ CLASS COMMANDS ------------------
        dispatcher.register(
                Commands.literal("setclass")
                        .then(Commands.argument("classname", StringArgumentType.word())
                                .executes(ctx -> {
                                    String className = StringArgumentType.getString(ctx, "classname");
                                    var player = ctx.getSource().getPlayerOrException();
                                    var rpgClass = RPGClassRegistry.getClassByName(className);

                                    if (rpgClass == null) {
                                        ctx.getSource().sendFailure(Component.literal("§cClass '" + className + "' not found."));
                                        return 0;
                                    }

                                    player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                                        if (cap.getSelectedClass() != null) {
                                            cap.getSelectedClass().removeClassEffect(player);
                                            cap.setPreviousClassName(cap.getSelectedClass().getClassName());
                                        }
                                        cap.setSelectedClass(rpgClass);
                                        rpgClass.applyClassEffect(player);

                                        spawnParticles(player, ParticleTypes.ENCHANT, 30);
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§a✓ Primary class set to '" + className + "'."), false);
                                    });
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );

        dispatcher.register(
                Commands.literal("setsecondary")
                        .then(Commands.argument("classname", StringArgumentType.word())
                                .executes(ctx -> {
                                    String className = StringArgumentType.getString(ctx, "classname");
                                    var player = ctx.getSource().getPlayerOrException();
                                    var rpgClass = RPGClassRegistry.getClassByName(className);

                                    if (rpgClass == null) {
                                        ctx.getSource().sendFailure(Component.literal("§cClass '" + className + "' not found."));
                                        return 0;
                                    }

                                    player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                                        if (!cap.canPickSecondaryClass()) {
                                            ctx.getSource().sendFailure(Component.literal("§cYou must reach level 50 in a primary class to unlock a secondary class."));
                                            return;
                                        }

                                        if (cap.getSelectedClass() != null && cap.getSelectedClass().getClassName().equalsIgnoreCase(className)) {
                                            ctx.getSource().sendFailure(Component.literal("§cCannot pick the same class as primary for secondary."));
                                            return;
                                        }

                                        if (cap.getSecondaryClass() != null) {
                                            cap.getSecondaryClass().removeClassEffect(player);
                                            cap.setPreviousSecondaryClassName(cap.getSecondaryClass().getClassName());
                                        }

                                        cap.setSecondaryClass(rpgClass);
                                        rpgClass.applyClassEffect(player);

                                        spawnParticles(player, ParticleTypes.ENCHANT, 30);
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§a✓ Secondary class set to '" + className + "'."), false);
                                    });

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );

        dispatcher.register(
                Commands.literal("addxp")
                        .then(Commands.argument("classname", StringArgumentType.word())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            String className = StringArgumentType.getString(ctx, "classname");
                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();

                                            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                                                int oldLevel = cap.getLevel(className);
                                                cap.addXP(className, amount);
                                                int newLevel = cap.getLevel(className);

                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                        "§a✓ Added " + amount + " XP to " + className +
                                                                ". Level: " + newLevel + " (" + cap.getXP(className) + " XP)"), false);

                                                if (newLevel > oldLevel) {
                                                    spawnParticles(player, ParticleTypes.END_ROD, 40);
                                                    player.sendSystemMessage(Component.literal(
                                                            "§6⭐ Your " + className + " class leveled up to " + newLevel + "!"
                                                    ));
                                                }
                                            });

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
        );

        dispatcher.register(
                Commands.literal("setlevel")
                        .then(Commands.argument("classname", StringArgumentType.word())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            String className = StringArgumentType.getString(ctx, "classname");
                                            int newLevel = IntegerArgumentType.getInteger(ctx, "level");
                                            var player = ctx.getSource().getPlayerOrException();
                                            var rpgClass = RPGClassRegistry.getClassByName(className);

                                            if (rpgClass == null) {
                                                ctx.getSource().sendFailure(Component.literal("§cClass '" + className + "' not found."));
                                                return 0;
                                            }

                                            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                                                if (cap.getSelectedClass() != null && cap.getSelectedClass().getClassName().equalsIgnoreCase(className)) {
                                                    cap.setLevel(className, newLevel);
                                                    spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, 20);
                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("§a✓ Primary class " + className + " level set to " + newLevel), false);
                                                } else if (cap.getSecondaryClass() != null && cap.getSecondaryClass().getClassName().equalsIgnoreCase(className)) {
                                                    cap.setLevel(className, newLevel);
                                                    spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, 20);
                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("§a✓ Secondary class " + className + " level set to " + newLevel), false);
                                                } else {
                                                    ctx.getSource().sendFailure(Component.literal("§cPlayer does not have that class."));
                                                }
                                            });

                                            return Command.SINGLE_SUCCESS;
                                        })))
        );

        dispatcher.register(
                Commands.literal("myclass")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayerOrException();
                            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                                StringBuilder sb = new StringBuilder("§6=== Classes Info ===\n");
                                if (cap.getSelectedClass() != null) {
                                    String cname = cap.getSelectedClass().getClassName();
                                    sb.append("§ePrimary: §f").append(cname)
                                            .append(" §7| Level: §f").append(cap.getLevel(cname))
                                            .append(" §7| XP: §f").append(cap.getXP(cname)).append("\n");
                                }
                                if (cap.getSecondaryClass() != null) {
                                    String cname = cap.getSecondaryClass().getClassName();
                                    sb.append("§eSecondary: §f").append(cname)
                                            .append(" §7| Level: §f").append(cap.getLevel(cname))
                                            .append(" §7| XP: §f").append(cap.getXP(cname)).append("\n");
                                }

                                ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            });
                            return Command.SINGLE_SUCCESS;
                        })
        );

        // ------------------ PROGRESSION COMMANDS ------------------
        dispatcher.register(
                Commands.literal("progression")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("info").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DebugHelper.debugCapability(player);
                            return showProgressionInfo(player);
                        }))
                        .then(Commands.literal("reset").executes(ctx ->
                                resetProgression(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("addlevels")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> addLevels(ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("listnodes").executes(ctx ->
                                listNodes(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("buynode")
                                .then(Commands.argument("nodeId", StringArgumentType.string())
                                        .executes(ctx -> forceBuyNode(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "nodeId")))))
                        .then(Commands.literal("addpoints")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> addSkillPoints(ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("customstatinfo").executes(ctx ->
                                showCustomStatInfo(ctx.getSource().getPlayerOrException())))
        );


        // ------------------ PROGRESSION LOGIC ------------------
        dispatcher.register(
                Commands.literal("progression")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("info").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DebugHelper.debugCapability(player);
                            return showProgressionInfo(player);
                        }))
                        .then(Commands.literal("reset").executes(ctx ->
                                resetProgression(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("addlevels")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> addLevels(ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("listnodes").executes(ctx ->
                                listNodes(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("buynode")
                                .then(Commands.argument("nodeId", StringArgumentType.string())
                                        .executes(ctx -> forceBuyNode(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "nodeId")))))
                        .then(Commands.literal("addpoints")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> addSkillPoints(ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("customstatinfo").executes(ctx ->
                                showCustomStatInfo(ctx.getSource().getPlayerOrException())))
        );
    }

// ------------------ PROGRESSION LOGIC ------------------

    private static int showProgressionInfo(ServerPlayer player) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            String className = cap.getSelectedClass() != null ?
                    cap.getSelectedClass().getClassName() : "None";
            int classLevel = cap.getLevel(className);
            PlayerProgressionData progression = cap.getProgressionData();

            player.sendSystemMessage(Component.literal("§6=== Progression Info ==="));
            player.sendSystemMessage(Component.literal("§eClass: §f" + className));
            player.sendSystemMessage(Component.literal("§eClass Level: §f" + classLevel));
            player.sendSystemMessage(Component.literal("§eSkill Points: §f" +
                    progression.getSkillPoints(className)));
            player.sendSystemMessage(Component.literal("§eSpent Levels: §f" +
                    progression.getTotalSpentLevels()));
            player.sendSystemMessage(Component.literal("§eAvailable Levels: §f" +
                    progression.getAvailableLevels(classLevel)));

            player.sendSystemMessage(Component.literal("§6Purchased Nodes:"));
            player.sendSystemMessage(Component.literal("  §aSkill: §f" +
                    progression.getNodeCount("skill")));
            player.sendSystemMessage(Component.literal("  §dPassive: §f" +
                    progression.getNodeCount("passive")));
            player.sendSystemMessage(Component.literal("  §eItem: §f" +
                    progression.getNodeCount("item")));

            player.sendSystemMessage(Component.literal("§6Unlocked:"));
            player.sendSystemMessage(Component.literal("  §aSkill: " +
                    (progression.isSkillUnlocked() ? "§a✓" : "§c✗")));
            player.sendSystemMessage(Component.literal("  §dPassive: " +
                    (progression.isPassiveUnlocked() ? "§a✓" : "§c✗")));
            player.sendSystemMessage(Component.literal("  §eItem: " +
                    (progression.isItemUnlocked() ? "§a✓" : "§c✗")));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int resetProgression(ServerPlayer player) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            DebugHelper.showInfo(player, "Resetting progression...");

            PlayerProgressionData progression = cap.getProgressionData();
            PlayerProgressionData newProgression = new PlayerProgressionData();
            progression.copyFrom(newProgression);

            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
            DebugHelper.showSuccess(player, "Progression reset complete!");
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int addLevels(ServerPlayer player, int amount) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                DebugHelper.showError(player, "No class selected!");
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            int oldLevel = cap.getLevel(className);
            cap.setLevel(className, oldLevel + amount);

            DebugHelper.showSuccess(player, "Added " + amount + " levels! Now level " +
                    (oldLevel + amount));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int listNodes(ServerPlayer player) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                DebugHelper.showError(player, "No class selected!");
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            PlayerProgressionData progression = cap.getProgressionData();

            DebugHelper.showInfo(player, "Listing nodes for " + className);
            player.sendSystemMessage(Component.literal("§6=== Available Nodes ==="));

            for (String line : new String[]{"skill", "passive", "item"}) {
                player.sendSystemMessage(Component.literal("§e" + line.toUpperCase() + " LINE:"));
                for (ProgressionNode node : NodeRegistry.getNodesForLine(className, line)) {
                    boolean purchased = progression.hasNode(line, node.getId());
                    boolean canPurchase = NodeRegistry.canPurchaseNode(progression, node);
                    String status = purchased ? "§a✓" : canPurchase ? "§e○" : "§c✗";
                    player.sendSystemMessage(Component.literal(
                            "  " + status + " §f" + node.getId() + " §7(" + node.getCost() +
                                    " pts) §f- " + node.getDisplayName()));
                }
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int forceBuyNode(ServerPlayer player, String nodeId) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                DebugHelper.showError(player, "No class selected!");
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            DebugHelper.showInfo(player, "Attempting to buy node: " + nodeId);

            ProgressionNode node = NodeRegistry.getNode(className, nodeId);

            if (node == null) {
                DebugHelper.showError(player, "Node not found: " + nodeId);
                player.sendSystemMessage(Component.literal("§cAvailable nodes for " + className + ":"));
                for (ProgressionNode n : NodeRegistry.getNodesForClass(className)) {
                    player.sendSystemMessage(Component.literal("  §7- " + n.getId()));
                }
                return;
            }

            PlayerProgressionData progression = cap.getProgressionData();

            if (progression.hasNode(node.getLine(), nodeId)) {
                DebugHelper.showWarning(player, "Already purchased!");
                return;
            }

            // Check prerequisites
            if (!NodeRegistry.canPurchaseNode(progression, node)) {
                DebugHelper.showWarning(player, "Prerequisites not met!");
                if (node.hasPrerequisites()) {
                    player.sendSystemMessage(Component.literal("§cRequired nodes:"));
                    for (String prereq : node.getPrerequisites()) {
                        boolean has = progression.hasNode(node.getLine(), prereq);
                        player.sendSystemMessage(Component.literal("  " +
                                (has ? "§a✓" : "§c✗") + " §f" + prereq));
                    }
                }
                return;
            }

            // Get current skill points
            int currentPoints = progression.getSkillPoints(className);
            DebugHelper.showInfo(player, "Current points: " + currentPoints + ", Cost: " + node.getCost());

            if (currentPoints < node.getCost()) {
                DebugHelper.showError(player, "Not enough skill points! Have " + currentPoints +
                        ", need " + node.getCost());
                return;
            }

            // Purchase the node (THIS WAS THE BUG - using wrong method)
            progression.purchaseNode(node.getLine(), nodeId, node.getCost(), className);

            int newPoints = progression.getSkillPoints(className);
            DebugHelper.showSuccess(player, "Purchased " + node.getDisplayName() +
                    "! Points: " + currentPoints + " → " + newPoints);

            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int addSkillPoints(ServerPlayer player, int amount) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                DebugHelper.showError(player, "No class selected!");
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            PlayerProgressionData progression = cap.getProgressionData();

            int oldPoints = progression.getSkillPoints(className);
            progression.addSkillPoints(className, amount);
            int newPoints = progression.getSkillPoints(className);

            DebugHelper.showSuccess(player, "Added " + amount + " skill points! " +
                    oldPoints + " → " + newPoints);

            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int showCustomStatInfo(ServerPlayer player) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                DebugHelper.showError(player, "No class selected!");
                return;
            }

            PlayerProgressionData progression = cap.getProgressionData();

            player.sendSystemMessage(Component.literal("§6=== Custom Stat Info ==="));
            for (String line : new String[]{"skill", "passive", "item"}) {
                int available = progression.getAvailableCustomPoints(line);
                player.sendSystemMessage(Component.literal("§e" + line.toUpperCase() + " LINE:"));
                player.sendSystemMessage(Component.literal("  §aAvailable Points: §f" + available));

                var stats = progression.getCustomStats(line);
                if (!stats.isEmpty()) {
                    player.sendSystemMessage(Component.literal("  §dSpent Stats:"));
                    stats.forEach((statName, level) ->
                            player.sendSystemMessage(Component.literal("    §f" + statName + ": §a" + level))
                    );
                } else {
                    player.sendSystemMessage(Component.literal("  §7No stats spent yet"));
                }
            }

            DebugHelper.showInfo(player, "Custom stat info displayed");
        });
        return Command.SINGLE_SUCCESS;
    }

    // Utility method for particles
    private static void spawnParticles(ServerPlayer player, net.minecraft.core.particles.ParticleOptions particle, int count) {
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