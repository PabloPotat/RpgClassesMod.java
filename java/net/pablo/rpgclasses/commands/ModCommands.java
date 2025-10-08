package net.pablo.rpgclasses.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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

/**
 * Unified command registration for RPG Classes and Progression.
 */
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
                                    CommandSourceStack source = ctx.getSource();
                                    var player = source.getPlayerOrException();

                                    var rpgClass = RPGClassRegistry.getClassByName(className);
                                    if (rpgClass == null) {
                                        source.sendFailure(Component.literal("Class '" + className + "' not found."));
                                        return 0;
                                    }

                                    player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                                        if (cap.getSelectedClass() != null) {
                                            cap.getSelectedClass().removeClassEffect(player);
                                            cap.setPreviousClassName(cap.getSelectedClass().getClassName());
                                        }

                                        cap.setSelectedClass(rpgClass);
                                        rpgClass.applyClassEffect(player);

                                        source.sendSuccess(() ->
                                                Component.literal("Primary class set to '" + className + "'."), false);
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
                                        ctx.getSource().sendFailure(
                                                Component.literal("Class '" + className + "' not found.")
                                        );
                                        return 0;
                                    }

                                    player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                                        if (!cap.canPickSecondaryClass()) {
                                            ctx.getSource().sendFailure(
                                                    Component.literal("You must reach level 50 in a primary class to unlock a secondary class.")
                                            );
                                            return;
                                        }

                                        if (cap.getSelectedClass() != null && cap.getSelectedClass().getClassName().equalsIgnoreCase(className)) {
                                            ctx.getSource().sendFailure(
                                                    Component.literal("Cannot pick the same class as primary for secondary.")
                                            );
                                            return;
                                        }

                                        if (cap.getSecondaryClass() != null) {
                                            cap.getSecondaryClass().removeClassEffect(player);
                                            cap.setPreviousSecondaryClassName(cap.getSecondaryClass().getClassName());
                                        }

                                        cap.setSecondaryClass(rpgClass);
                                        rpgClass.applyClassEffect(player);

                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("Secondary class set to '" + className + "'."), false
                                        );
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
                                            Player player = ctx.getSource().getPlayerOrException();

                                            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                                                int oldLevel = cap.getLevel(className);
                                                cap.addXP(className, amount);
                                                int newLevel = cap.getLevel(className);

                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                        "Added " + amount + " XP to " + className +
                                                                ". Level: " + newLevel + " (" + cap.getXP(className) + " XP)"), false);

                                                if (newLevel > oldLevel) {
                                                    player.sendSystemMessage(Component.literal(
                                                            "Your " + className + " class leveled up to " + newLevel + "!"
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
                                                ctx.getSource().sendFailure(Component.literal("Class '" + className + "' not found."));
                                                return 0;
                                            }

                                            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                                                if (cap.getSelectedClass() != null && cap.getSelectedClass().getClassName().equalsIgnoreCase(className)) {
                                                    cap.setLevel(className, newLevel);
                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("Primary class " + className + " level set to " + newLevel), false);
                                                } else if (cap.getSecondaryClass() != null && cap.getSecondaryClass().getClassName().equalsIgnoreCase(className)) {
                                                    cap.setLevel(className, newLevel);
                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("Secondary class " + className + " level set to " + newLevel), false);
                                                } else {
                                                    ctx.getSource().sendFailure(Component.literal("Player does not have that class."));
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
                                StringBuilder sb = new StringBuilder("Classes Info:\n");
                                if (cap.getSelectedClass() != null) {
                                    String cname = cap.getSelectedClass().getClassName();
                                    sb.append("Primary: ").append(cname)
                                            .append(" | Level: ").append(cap.getLevel(cname))
                                            .append(" | XP: ").append(cap.getXP(cname)).append("\n");
                                }
                                if (cap.getSecondaryClass() != null) {
                                    String cname = cap.getSecondaryClass().getClassName();
                                    sb.append("Secondary: ").append(cname)
                                            .append(" | Level: ").append(cap.getLevel(cname))
                                            .append(" | XP: ").append(cap.getXP(cname)).append("\n");
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
                        .then(Commands.literal("info").executes(ctx -> showProgressionInfo(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("reset").executes(ctx -> resetProgression(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("addlevels")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> addLevels(ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("listnodes").executes(ctx -> listNodes(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("buynode")
                                .then(Commands.argument("nodeId", StringArgumentType.string())
                                        .executes(ctx -> forceBuyNode(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "nodeId")))))
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
            player.sendSystemMessage(Component.literal("§eSpent Levels: §f" + progression.getTotalSpentLevels()));
            player.sendSystemMessage(Component.literal("§eAvailable Levels: §f" +
                    progression.getAvailableLevels(classLevel)));

            player.sendSystemMessage(Component.literal("§6Purchased Nodes:"));
            player.sendSystemMessage(Component.literal("  §aSkill: §f" + progression.getNodeCount("skill")));
            player.sendSystemMessage(Component.literal("  §dPassive: §f" + progression.getNodeCount("passive")));
            player.sendSystemMessage(Component.literal("  §eItem: §f" + progression.getNodeCount("item")));

            player.sendSystemMessage(Component.literal("§6Unlocked:"));
            player.sendSystemMessage(Component.literal("  §aSkill: " +
                    (progression.isSkillUnlocked() ? "§a✓" : "§c✗")));
            player.sendSystemMessage(Component.literal("  §dPassive: " +
                    (progression.isPassiveUnlocked() ? "§a✓" : "§c✗")));
            player.sendSystemMessage(Component.literal("  §eItem: " +
                    (progression.isItemUnlocked() ? "§a✓" : "§c✗")));
        });
        return 1;
    }

    private static int resetProgression(ServerPlayer player) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            PlayerProgressionData progression = cap.getProgressionData();
            PlayerProgressionData newProgression = new PlayerProgressionData();
            progression.copyFrom(newProgression);
            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
            player.sendSystemMessage(Component.literal("§a✓ Progression reset!"));
        });
        return 1;
    }

    private static int addLevels(ServerPlayer player, int amount) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                player.sendSystemMessage(Component.literal("§cNo class selected!"));
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            int oldLevel = cap.getLevel(className);
            cap.setLevel(className, oldLevel + amount);
            player.sendSystemMessage(Component.literal("§a✓ Added " + amount + " levels! Now level " + (oldLevel + amount)));
        });
        return 1;
    }

    private static int listNodes(ServerPlayer player) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                player.sendSystemMessage(Component.literal("§cNo class selected!"));
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            PlayerProgressionData progression = cap.getProgressionData();

            player.sendSystemMessage(Component.literal("§6=== Available Nodes ==="));
            for (String line : new String[]{"skill", "passive", "item"}) {
                player.sendSystemMessage(Component.literal("§e" + line.toUpperCase() + " LINE:"));
                for (ProgressionNode node : NodeRegistry.getNodesForLine(className, line)) {
                    boolean purchased = progression.hasNode(line, node.getId());
                    boolean canPurchase = NodeRegistry.canPurchaseNode(progression, node);
                    String status = purchased ? "§a✓" : canPurchase ? "§e○" : "§c✗";
                    player.sendSystemMessage(Component.literal(
                            "  " + status + " §f" + node.getId() + " §7(" + node.getCost() + " lvl) §f- " +
                                    node.getDisplayName()));
                }
            }
        });
        return 1;
    }

    private static int forceBuyNode(ServerPlayer player, String nodeId) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                player.sendSystemMessage(Component.literal("§cNo class selected!"));
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            ProgressionNode node = NodeRegistry.getNode(className, nodeId);

            if (node == null) {
                player.sendSystemMessage(Component.literal("§cNode not found: " + nodeId));
                return;
            }

            PlayerProgressionData progression = cap.getProgressionData();
            if (progression.hasNode(node.getLine(), nodeId)) {
                player.sendSystemMessage(Component.literal("§cAlready purchased!"));
                return;
            }

            progression.purchaseNode(node.getLine(), nodeId, 0);
            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
            player.sendSystemMessage(Component.literal("§a✓ Force purchased: " + node.getDisplayName()));
        });
        return 1;
    }
}
