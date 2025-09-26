package net.pablo.rpgclasses.xphandlers;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.item.ModItems;

import java.util.Random;

public class GamblerXP {

    private static final Random random = new Random();

    // Called when a player right-clicks with Gambler dice
    public static InteractionResultHolder<ItemStack> onUseDice(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {

            player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                boolean isPrimary = cap.getSelectedClass() != null &&
                        "Gambler".equalsIgnoreCase(cap.getSelectedClass().getClassName());
                boolean isSecondary = cap.getSecondaryClass() != null &&
                        "Gambler".equalsIgnoreCase(cap.getSecondaryClass().getClassName());

                if (stack.is(ModItems.LOADED_DICE.get())) {
                    if (!isPrimary) {
                        player.sendSystemMessage(Component.literal("Only a primary Gambler can use Loaded Dice!"));
                        return;
                    }

                    int xpToAward = rollXp(cap.getLevel("gambler"));
                    XPUtils.addXPAndCheckLevel(player, cap, "gambler", xpToAward);

                    player.sendSystemMessage(Component.literal("🎲 You rolled Loaded Dice and gained " + xpToAward + " Gambler XP!"));

                    if (!player.getAbilities().instabuild) stack.shrink(1);

                } else if (stack.is(ModItems.WEIGHTED_DICE.get())) {
                    if (!isSecondary) {
                        player.sendSystemMessage(Component.literal("Only a secondary Gambler can use Weighted Dice!"));
                        return;
                    }

                    int xpToAward = rollXp(cap.getLevel("gambler"));
                    XPUtils.addSecondaryXP(player, cap, "gambler", xpToAward);

                    player.sendSystemMessage(Component.literal("🎲 You rolled Weighted Dice and gained " + xpToAward + " Gambler XP!"));

                    if (!player.getAbilities().instabuild) stack.shrink(1);
                }
            });
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static int rollXp(int level) {
        int base = 5 + (int)(level * 1.2);
        if (base > 70) base = 70;

        int bonusCap = 10 + (level * 2);
        if (bonusCap > 180) bonusCap = 180;

        int bonus = random.nextInt(bonusCap);
        return base + bonus;
    }
}
