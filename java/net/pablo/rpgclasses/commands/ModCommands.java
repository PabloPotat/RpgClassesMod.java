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
                        .then(Commands.literal("info").executes(ctx -> showProgressionInfo(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("reset").executes(ctx -> resetProgression(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("resetcustom")
                                .then(Commands.argument("line", StringArgumentType.word())
                                        .executes(ctx -> resetCustomStats(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "line")))))
                        .then(Commands.literal("addpoints")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> addSkillPoints(ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "amount")))))
                        .then(Commands.literal("listnodes").executes(ctx -> listNodes(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("buynode")
                                .then(Commands.argument("nodeId", StringArgumentType.string())
                                        .executes(ctx -> forceBuyNode(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "nodeId")))))
                        .then(Commands.literal("upgradecustom")
                                .then(Commands.argument("line", StringArgumentType.word())
                                        .then(Commands.argument("statName", StringArgumentType.string())
                                                .executes(ctx -> upgradeCustomStat(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "line"),
                                                        StringArgumentType.getString(ctx, "statName"))))))
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
            player.sendSystemMessage(Component.literal("§eSkill Points: §f" + progression.getSkillPoints(className)));

            player.sendSystemMessage(Component.literal("\n§6Purchased Nodes:"));
            player.sendSystemMessage(Component.literal("  §aSkill: §f" + progression.getNodeCount("skill")));
            player.sendSystemMessage(Component.literal("  §dPassive: §f" + progression.getNodeCount("passive")));
            player.sendSystemMessage(Component.literal("  §eItem: §f" + progression.getNodeCount("item")));

            player.sendSystemMessage(Component.literal("\n§6Custom Stat Points:"));
            player.sendSystemMessage(Component.literal("  §aSkill: §f" + progression.getAvailableCustomPoints("skill")));
            player.sendSystemMessage(Component.literal("  §dPassive: §f" + progression.getAvailableCustomPoints("passive")));
            player.sendSystemMessage(Component.literal("  §eItem: §f" + progression.getAvailableCustomPoints("item")));

            player.sendSystemMessage(Component.literal("\n§6Final Unlocks:"));
            player.sendSystemMessage(Component.literal("  §aSkill: " +
                    (progression.isSkillUnlocked() ? "§a✓" : "§c✗")));
            player.sendSystemMessage(Component.literal("  §dPassive: " +
                    (progression.isPassiveUnlocked() ? "§a✓" : "§c✗")));
            player.sendSystemMessage(Component.literal("  §eItem: " +
                    (progression.isItemUnlocked() ? "§a✓" : "§c✗")));

            spawnParticles(player, ParticleTypes.ENCHANT, 15);
        });
        return 1;
    }

    private static int resetProgression(ServerPlayer player) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            PlayerProgressionData progression = cap.getProgressionData();
            PlayerProgressionData newProgression = new PlayerProgressionData();
            progression.copyFrom(newProgression);

            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
            spawnParticles(player, ParticleTypes.EXPLOSION, 30);
            player.sendSystemMessage(Component.literal("§a✓ Full progression reset!"));
        });
        return 1;
    }

    private static int resetCustomStats(ServerPlayer player, String line) {
        if (!line.equals("skill") && !line.equals("passive") && !line.equals("item")) {
            player.sendSystemMessage(Component.literal("§cInvalid line! Use: skill, passive, or item"));
            return 0;
        }

        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            PlayerProgressionData progression = cap.getProgressionData();
            var customStats = progression.getCustomStats(line);

            // Clear all custom stats for this line
            for (String statName : customStats.keySet()) {
                int level = customStats.get(statName);
                for (int i = 0; i < level; i++) {
                    // You'd need to add a method to remove stat levels
                    // For now, just clear the map
                }
            }

            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
            spawnParticles(player, ParticleTypes.POOF, 20);
            player.sendSystemMessage(Component.literal("§a✓ Custom stats for " + line + " line reset!"));
        });
        return 1;
    }

    private static int addSkillPoints(ServerPlayer player, int amount) {
        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            if (cap.getSelectedClass() == null) {
                player.sendSystemMessage(Component.literal("§cNo class selected!"));
                return;
            }

            String className = cap.getSelectedClass().getClassName();
            PlayerProgressionData progression = cap.getProgressionData();

            int before = progression.getSkillPoints(className);
            progression.addSkillPoints(className, amount);
            int after = progression.getSkillPoints(className);

            spawnParticles(player, ParticleTypes.HAPPY_VILLAGER, 30);
            player.sendSystemMessage(Component.literal(
                    "§a✓ Added " + amount + " skill points! §7(" + before + " → " + after + ")"));

            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
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
            int availablePoints = progression.getSkillPoints(className);

            player.sendSystemMessage(Component.literal("§6=== Available Nodes ==="));
            player.sendSystemMessage(Component.literal("§7Skill Points: §e" + availablePoints));
            player.sendSystemMessage(Component.literal("§7Legend: §a✓ Owned | §a○ Can Buy | §e⚠ Need Points | §c✗ Locked"));

            for (String line : new String[]{"skill", "passive", "item"}) {
                player.sendSystemMessage(Component.literal("\n§e" + line.toUpperCase() + " LINE:"));

                List<ProgressionNode> lineNodes = NodeRegistry.getNodesForLine(className, line);
                for (int i = 0; i < lineNodes.size(); i++) {
                    ProgressionNode node = lineNodes.get(i);
                    String status = NodeRegistry.getPurchaseStatus(progression, node, availablePoints);

                    player.sendSystemMessage(Component.literal(
                            "  " + (i + 1) + ". " + status + " §f" + node.getDisplayName() +
                                    " §7(" + node.getCost() + " pts)"));

                    // Show description for next available node
                    if (status.contains("CAN PURCHASE")) {
                        player.sendSystemMessage(Component.literal(
                                "     §7→ " + node.getDescription()));
                    }
                }
            }

            spawnParticles(player, ParticleTypes.ENCHANT, 10);
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
            spawnParticles(player, ParticleTypes.TOTEM_OF_UNDYING, 40);
            player.sendSystemMessage(Component.literal("§a✓ Force purchased: " + node.getDisplayName()));

            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
        });
        return 1;
    }

    private static int upgradeCustomStat(ServerPlayer player, String line, String statName) {
        if (!line.equals("skill") && !line.equals("passive") && !line.equals("item")) {
            player.sendSystemMessage(Component.literal("§cInvalid line! Use: skill, passive, or item"));
            return 0;
        }

        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            PlayerProgressionData progression = cap.getProgressionData();

            int availablePoints = progression.getAvailableCustomPoints(line);
            int currentLevel = progression.getCustomStatLevel(line, statName);

            if (availablePoints <= 0) {
                player.sendSystemMessage(Component.literal("§cNo custom stat points for " + line + " line!"));
                return;
            }

            if (currentLevel >= 5) {
                player.sendSystemMessage(Component.literal("§c" + statName + " is already at max level (5)!"));
                return;
            }

            progression.spendCustomStat(line, statName);
            int newLevel = currentLevel + 1;
            int remaining = progression.getAvailableCustomPoints(line);

            spawnParticles(player, ParticleTypes.ENCHANTED_HIT, 30);
            player.sendSystemMessage(Component.literal(
                    "§a✓ " + statName + " → Level " + newLevel + " §7(" + line + " points: " + remaining + ")"));

            NetworkHandler.sendToClient(new SyncProgressionPacket(progression), player);
        });
        return 1;
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