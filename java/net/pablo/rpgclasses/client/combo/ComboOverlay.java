package net.pablo.rpgclasses.client.combo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.pablo.rpgclasses.client.combo.ComboData;

public class ComboOverlay {
    public static final IGuiOverlay COMBO_OVERLAY = ComboOverlay::renderComboCounter;

    private static void renderComboCounter(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        int combo = ComboData.getCombo();

        if (combo > 0) {
            int x = screenWidth / 2 + 50;
            int y = screenHeight / 2 - 30;
            guiGraphics.drawString(minecraft.font, "Combo: " + combo, x, y, 0xFFFFFF, true);
        }
    }
}
