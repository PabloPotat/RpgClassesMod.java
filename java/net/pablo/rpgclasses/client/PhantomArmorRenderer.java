package net.pablo.rpgclasses.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.RpgClassesMod;
import net.pablo.rpgclasses.effect.ModEffects;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.*;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PhantomArmorRenderer {

    private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD
    };

    private static final EquipmentSlot[] HAND_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
    };

    private static final Map<UUID, Deque<Runnable>> restoreQueues = new WeakHashMap<>();
    private static final boolean CURIOS_LOADED = ModList.get().isLoaded("curios");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;

        if (!player.hasEffect(ModEffects.PHANTOM.get())) return;

        Deque<Runnable> queue = restoreQueues.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        restoreItems(queue);

        Inventory invPlayer = player.getInventory();

        // Hide armor
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            int index = getArmorInventoryIndex(slot);
            if (index >= 0) {
                ItemStack stack = invPlayer.getItem(index);
                queue.add(() -> invPlayer.setItem(index, stack));
                invPlayer.setItem(index, ItemStack.EMPTY);
            }
        }

        // Hide held items
        for (EquipmentSlot slot : HAND_SLOTS) {
            int index = getHandInventoryIndex(slot, invPlayer);
            if (index >= 0) {
                ItemStack stack = invPlayer.getItem(index);
                queue.add(() -> invPlayer.setItem(index, stack));
                invPlayer.setItem(index, ItemStack.EMPTY);
            }
        }

        // Hide Curios items
        if (CURIOS_LOADED) {
            hideCurios(player, queue);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;

        Deque<Runnable> queue = restoreQueues.get(player.getUUID());
        if (queue != null) {
            restoreItems(queue);
        }
    }

    private static void hideCurios(AbstractClientPlayer player, Deque<Runnable> queue) {
        CuriosApi.getCuriosInventory(player).ifPresent(curiosInventory -> {
            Map<String, ICurioStacksHandler> curios = curiosInventory.getCurios();

            for (Map.Entry<String, ICurioStacksHandler> entry : curios.entrySet()) {
                ICurioStacksHandler stacksHandler = entry.getValue();
                IDynamicStackHandler stackHandler = stacksHandler.getStacks();

                for (int i = 0; i < stackHandler.getSlots(); i++) {
                    final int slot = i;
                    ItemStack stack = stackHandler.getStackInSlot(slot);

                    if (!stack.isEmpty()) {
                        ItemStack stackCopy = stack.copy();
                        queue.add(() -> stackHandler.setStackInSlot(slot, stackCopy));
                        stackHandler.setStackInSlot(slot, ItemStack.EMPTY);
                    }
                }
            }
        });
    }

    private static void restoreItems(Deque<Runnable> queue) {
        Runnable runnable;
        while ((runnable = queue.poll()) != null) {
            try {
                runnable.run();
            } catch (Throwable e) {
                System.err.println("[PhantomArmor] Failed to restore item: " + e.getMessage());
            }
        }
    }

    private static int getArmorInventoryIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 39;
            case CHEST -> 38;
            case LEGS -> 37;
            case FEET -> 36;
            default -> -1;
        };
    }

    private static int getHandInventoryIndex(EquipmentSlot slot, Inventory inventory) {
        return switch (slot) {
            case MAINHAND -> inventory.selected;
            case OFFHAND -> 40;
            default -> -1;
        };
    }
}