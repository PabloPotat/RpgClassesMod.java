package net.pablo.rpgclasses.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.pablo.rpgclasses.capability.PlayerClassProvider;
import net.pablo.rpgclasses.capability.PlayerProgressionData;

import java.util.function.Supplier;

public class SyncProgressionPacket {
    private final CompoundTag progressionData;

    public SyncProgressionPacket(PlayerProgressionData progression) {
        this.progressionData = progression.save();
    }

    public SyncProgressionPacket(FriendlyByteBuf buf) {
        this.progressionData = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(progressionData);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            // CLIENT SIDE
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            mc.player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
                PlayerProgressionData progression = cap.getProgressionData();
                progression.load(progressionData);

                System.out.println("[RPGClasses] Synced progression data to client");
            });
        });
        return true;
    }
}