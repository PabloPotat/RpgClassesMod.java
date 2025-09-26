package net.pablo.rpgclasses.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.pablo.rpgclasses.registry.RPGClassRegistry;
import net.pablo.rpgclasses.capability.IPlayerClass;
import net.pablo.rpgclasses.capability.PlayerClassProvider;

@Mod.EventBusSubscriber
public class ModCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        // Set primary class
        event.getDispatcher().register(
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

        // Set secondary class
        event.getDispatcher().register(
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
                                            // Remove previous secondary effects
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

        // Add XP to class
        event.getDispatcher().register(
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

        // Display class info
        event.getDispatcher().register(
                Commands.literal("myclass")
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayerOrException();
                            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                                IPlayerClass selected = cap.getSelectedClass() != null ? cap : null;
                                IPlayerClass secondary = cap.getSecondaryClass() != null ? cap : null;

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

    // set level
        event.getDispatcher().register(
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

    }
}
