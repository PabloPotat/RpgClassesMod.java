package net.pablo.rpgclasses.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.pablo.rpgclasses.client.combo.ComboData;

import java.util.function.Supplier;

public class SyncComboPacket {
    private final int combo;

    public SyncComboPacket(int combo) {
        this.combo = combo;
    }

    public SyncComboPacket(FriendlyByteBuf buf) {
        this.combo = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(combo);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ComboData.setCombo(combo); // update client-side storage
        });
        ctx.get().setPacketHandled(true);
    }
}
