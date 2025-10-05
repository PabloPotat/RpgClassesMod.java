package net.pablo.rpgclasses.skills;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.pablo.rpgclasses.capability.PlayerClassProvider;

import java.util.function.Supplier;

public class ClassSkillPressedPacket {

    public ClassSkillPressedPacket() {}

    public static ClassSkillPressedPacket decode(FriendlyByteBuf buf) { return new ClassSkillPressedPacket(); }
    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            var cap = player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).orElse(null);
            if (cap == null) return;

            switch (cap.getSelectedClass().getClass().getSimpleName()) {
                case "Fighter":
                    if (SeismicCooldownManager.isOnCooldown(player)) {
                        // Optionally: send feedback packet/sound to client
                        return;
                    }
                    SeismicSmashManager.startJump(player); // ✅ all old logic preserved
                    // Set initial skill cooldown (will be refunded if no targets hit)
                    SeismicSmashManager.setPendingCooldown(player, SeismicSmashManager.SKILL_COOLDOWN_SECONDS);
                    break;
                case "Tank":
                    {
                        StormSurgeManager.startStormSurge(player);

                    }
                    break;
                case "Warrior":
                    {
                        VermillionLacerationManager.cast(player);
                    }
                    break;
                case "Mage":
                    {
                        ArcaneBrandManager.cast(player);
                    }
                    break;
                case "Rogue":
                    {
                        PhantomStrikeManager.activate(player, player.tickCount);
                    }
                    break;
                case "Ranger":
                {
                    SpectralChainsManager.activate(player);
                }
                break;
            }
        });
        ctx.setPacketHandled(true);
    }
}
