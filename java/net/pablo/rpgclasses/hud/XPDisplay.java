package net.pablo.rpgclasses.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList; // Added for thread safety

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class XPDisplay {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final float XP_FLOAT_SPEED = 0.8f;
    private static final long XP_FADE_DURATION = 1500;
    private static final int ICON_SIZE = 16;
    private static final int TEXT_ICON_GAP = 2;
    private static final int MAX_STACK = 15;

    // Use thread-safe lists to prevent ConcurrentModificationException
    private static final List<FloatingXP> primaryXP = new CopyOnWriteArrayList<>();
    private static final List<FloatingXP> secondaryXP = new CopyOnWriteArrayList<>();

    // Call this to show XP gain
    public static void showXP(int amount, ItemStack icon, boolean isPrimary) {
        FloatingXP xp = new FloatingXP(amount, icon, System.currentTimeMillis());
        if (isPrimary) {
            primaryXP.add(xp);
            if (primaryXP.size() > MAX_STACK) {
                primaryXP.remove(0);
            }
        } else {
            secondaryXP.add(xp);
            if (secondaryXP.size() > MAX_STACK) {
                secondaryXP.remove(0);
            }
        }
    }

    public static void showLevelUp(int level, ItemStack icon) {
        // Implement level-up notifications here if needed
    }

    @SubscribeEvent
    public static void renderXPOverlay(RenderGuiEvent.Post event) {
        GuiGraphics gui = event.getGuiGraphics();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // GUI Scale independent positioning
        int hotbarTop = screenHeight - 40;
        int baseY = hotbarTop - (int)(screenHeight * 0.04f);

        renderXPList(gui, font, primaryXP, true, screenWidth, screenHeight, baseY);
        renderXPList(gui, font, secondaryXP, false, screenWidth, screenHeight, baseY);
    }

    private static void renderXPList(GuiGraphics gui, Font font, List<FloatingXP> list, boolean isPrimary,
                                     int screenWidth, int screenHeight, int baseY) {
        if (list.isEmpty()) return;

        // Use iterator for safe removal (or use CopyOnWriteArrayList as above)
        Iterator<FloatingXP> iterator = list.iterator();
        List<FloatingXP> toRemove = new ArrayList<>(); // Store expired notifications

        while (iterator.hasNext()) {
            FloatingXP xp = iterator.next();
            long elapsed = System.currentTimeMillis() - xp.startTime;

            if (elapsed > XP_FADE_DURATION) {
                toRemove.add(xp); // Mark for removal instead of removing directly
                continue;
            }

            float progress = elapsed / (float) XP_FADE_DURATION;
            float alpha = progress < 0.7f ? 1f : 1f - ((progress - 0.7f) / 0.3f);

            // Float upward from the same starting position
            float yOffset = -XP_FLOAT_SPEED * elapsed / 20f;

            int yPos = (int)(baseY + yOffset);

            // Screen-relative visibility check
            int minY = (int)(screenHeight * 0.10f);
            if (yPos > minY && alpha > 0.05f) {
                int color = 0xFFFFFF | ((int)(alpha * 255) << 24);

                gui.pose().pushPose();
                gui.pose().translate(0, 0, 500);

                // Calculate total width of icon + text
                String text = "+" + xp.amount + " XP";
                int textWidth = font.width(text);
                int totalWidth = textWidth;

                if (!xp.icon.isEmpty()) {
                    totalWidth += ICON_SIZE + TEXT_ICON_GAP;
                }

                // MORE CENTRALIZED POSITIONING:
                int centerX;
                if (isPrimary) {
                    centerX = (int)(screenWidth * 0.40f);
                } else {
                    centerX = (int)(screenWidth * 0.60f);
                }

                // Calculate starting X position for the combined element
                int startX = centerX - totalWidth / 2;
                int currentX = startX;

                // Draw icon if exists
                if (!xp.icon.isEmpty()) {
                    gui.renderItem(xp.icon, currentX, yPos - ICON_SIZE / 2);
                    currentX += ICON_SIZE + TEXT_ICON_GAP;
                }

                // Draw XP text with 2px gap from icon
                gui.drawString(font, text, currentX, yPos - font.lineHeight / 2, color, true);

                gui.pose().popPose();
            }
        }

        // Remove expired notifications after iteration
        list.removeAll(toRemove);
    }

    private static class FloatingXP {
        int amount;
        ItemStack icon;
        long startTime;

        FloatingXP(int amount, ItemStack icon, long startTime) {
            this.amount = amount;
            this.icon = icon;
            this.startTime = startTime;
        }
    }
}