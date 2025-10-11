package net.pablo.rpgclasses.skills;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.client.ClientClassCache;
import net.pablo.rpgclasses.keybinds.KeyBindings;
import net.pablo.rpgclasses.network.NetworkHandler;


@Mod.EventBusSubscriber(Dist.CLIENT) // ✅ This automatically registers it for CLIENT only
public class ClassSkillClientHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (KeyBindings.USE_SKILL.consumeClick()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;

            // Client-side feedback per class
            switch (ClientClassCache.selectedClass) {
                case "fighter":
                    if (player.onGround() && !SeismicCooldownManager.isOnCooldown(player)) {
                        player.setDeltaMovement(player.getDeltaMovement().x, 0.8, player.getDeltaMovement().z);

                        // Particles & sound
                        for (int i = 0; i < 5; i++) {
                            player.level().addParticle(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 0, 0.1, 0);
                        }
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.8f);

                        NetworkHandler.INSTANCE.sendToServer(new ClassSkillPressedPacket());
                    }
                    break;
                case "tank":
                    if (!StormCooldownManager.isOnCooldown(player)) {
                        NetworkHandler.INSTANCE.sendToServer(new ClassSkillPressedPacket());
                    }
                    break;

                case "warrior": {
                    NetworkHandler.INSTANCE.sendToServer(new ClassSkillPressedPacket());
                }
                break;


                case "mage":{
                    NetworkHandler.INSTANCE.sendToServer(new ClassSkillPressedPacket());
                }
                break;
                case "rogue": {
                    NetworkHandler.INSTANCE.sendToServer(new ClassSkillPressedPacket());
                }
                break;
                case "ranger": {
                    NetworkHandler.INSTANCE.sendToServer(new ClassSkillPressedPacket());
                }
                break;
            }
            }
        }


    private static void showRogueEffect(LocalPlayer player) {
        // Rogue visual effect
    }
}