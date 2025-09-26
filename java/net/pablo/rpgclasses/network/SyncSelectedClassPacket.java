package net.pablo.rpgclasses.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.pablo.rpgclasses.client.ClientClassCache;

import java.util.function.Supplier;

public class SyncSelectedClassPacket {
    private final String className;

    public SyncSelectedClassPacket(String className) {
        this.className = className;
    }

    public static SyncSelectedClassPacket decode(FriendlyByteBuf buf) {
        return new SyncSelectedClassPacket(buf.readUtf(50));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(className);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        ctxSupplier.get().enqueueWork(() -> {
            ClientClassCache.selectedClass = className;
        });
        ctxSupplier.get().setPacketHandled(true);
    }
}
