package net.pablo.rpgclasses.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.pablo.rpgclasses.xphandlers.GamblerXP;

public class LoadedDice extends Item {

    public LoadedDice(Properties properties) {
        super(properties.stacksTo(64));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return GamblerXP.onUseDice(level, player, hand);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // 🔮 gives enchanted glint effect
    }

}
