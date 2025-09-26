package net.pablo.rpgclasses.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.pablo.rpgclasses.RpgClassesMod;
import net.pablo.rpgclasses.skills.ClassSkillPressedPacket;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;

    public static SimpleChannel INSTANCE; // no static init

    public static void register() {
        if (INSTANCE != null) return; // prevent double registration

        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(RpgClassesMod.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        INSTANCE.registerMessage(packetId++, SyncComboPacket.class,
                SyncComboPacket::encode,
                SyncComboPacket::new,
                SyncComboPacket::handle
        );

        INSTANCE.registerMessage(packetId++, ClassSkillPressedPacket.class,
                ClassSkillPressedPacket::encode,
                ClassSkillPressedPacket::decode,
                ClassSkillPressedPacket::handle
        );

        INSTANCE.registerMessage(packetId++, SyncSelectedClassPacket.class,
               SyncSelectedClassPacket::encode,
               SyncSelectedClassPacket::decode,
               SyncSelectedClassPacket::handle
        );
    }

    public static <MSG> void sendToClient(MSG message, ServerPlayer player) {
        if (INSTANCE != null) {
            INSTANCE.sendTo(message, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }
}
