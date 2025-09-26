package net.pablo.rpgclasses.utils;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public class AFKUtils {

    // Store last positions and rotations
    private static final Map<Player, PlayerData> playerDataMap = new HashMap<>();

    // Call this every tick for each player
    public static void updatePlayer(Player player) {
        PlayerData data = playerDataMap.getOrDefault(player, new PlayerData(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));

        double dx = Math.abs(player.getX() - data.x);
        double dy = Math.abs(player.getY() - data.y);
        double dz = Math.abs(player.getZ() - data.z);
        double dYaw = Math.abs(player.getYRot() - data.yaw);
        double dPitch = Math.abs(player.getXRot() - data.pitch);

        boolean moved = dx > 0.01 || dy > 0.01 || dz > 0.01;
        boolean looked = dYaw > 0.01 || dPitch > 0.01;

        if (moved || looked) {
            data.ticksSinceActive = 0;
        } else {
            data.ticksSinceActive++;
        }

        data.x = player.getX();
        data.y = player.getY();
        data.z = player.getZ();
        data.yaw = player.getYRot();
        data.pitch = player.getXRot();

        playerDataMap.put(player, data);
    }

    // Returns true if player is considered AFK
    public static boolean isPlayerAFK(Player player) {
        PlayerData data = playerDataMap.get(player);
        if (data == null) return false;
        return data.ticksSinceActive > 200; // 600 ticks = 30 seconds
    }

    private static class PlayerData {
        double x, y, z;
        float yaw, pitch;
        int ticksSinceActive = 0;

        PlayerData(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
