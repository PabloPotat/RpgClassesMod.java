package net.pablo.rpgclasses.keybinds;


import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.pablo.rpgclasses.RpgClassesMod;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final KeyMapping USE_SKILL = new KeyMapping(
            "key.rpgclasses.class_skill",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.rpgclasses"
    );

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(USE_SKILL);
    }
}