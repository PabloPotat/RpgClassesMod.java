package net.pablo.rpgclasses.classes;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.pablo.rpgclasses.network.NetworkHandler;
import net.pablo.rpgclasses.network.SyncComboPacket;
import net.pablo.rpgclasses.utils.AFKUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Fighter extends RPGClass {

    private static final double DEFAULT_HEALTH = 30.0;
    private static final double DEFAULT_SPEED = 0.1;
    private static final double DEFAULT_ATTACK = 4.0;

    private static final long COMBO_TIMEOUT_MS = 5000; // 5 sec hit timeout
    private static final long COMBO_MIN_HIT_INTERVAL_MS = 50; // prevent double-counting

    private final Map<UUID, Integer> comboMap = new HashMap<>();
    private final Map<UUID, Long> lastHitTime = new HashMap<>();

    public Fighter() {
        super("fighter", DEFAULT_HEALTH, DEFAULT_SPEED, DEFAULT_ATTACK);
    }

    /** Get current combo count */
    public int getCombo(UUID playerId) {
        return comboMap.getOrDefault(playerId, 0);
    }

    /** Increase combo on hit */
    /** Increase combo on hit; only once per hit (even if multiple entities) */
    public void increaseCombo(UUID playerId, Player player) {
        long now = System.currentTimeMillis();
        long lastHit = lastHitTime.getOrDefault(playerId, 0L);

        // If within min interval, treat as same hit → do not increase combo
        if (now - lastHit < COMBO_MIN_HIT_INTERVAL_MS) return;

        if (AFKUtils.isPlayerAFK(player)) {
            resetCombo(playerId);
            syncComboToClient(playerId, player);
            return;
        }

        // Increase combo by 1 (regardless of number of entities hit)
        int combo = getCombo(playerId) + 1;
        comboMap.put(playerId, combo);

        // Update lastHitTime to now so subsequent hits within this interval are ignored
        lastHitTime.put(playerId, now);

        syncComboToClient(playerId, player);
    }


    /** Reset combo if timed out or player AFK */
    public void resetComboIfTimedOut(UUID playerId, Player player) {
        long now = System.currentTimeMillis();
        long lastHit = lastHitTime.getOrDefault(playerId, 0L);

        if (AFKUtils.isPlayerAFK(player) || now - lastHit > COMBO_TIMEOUT_MS) {
            resetCombo(playerId);
            syncComboToClient(playerId, player);
        }
    }

    /** Manual combo reset */
    public void resetCombo(UUID playerId) {
        comboMap.put(playerId, 0);
        lastHitTime.put(playerId, 0L);
    }

    /** Sync combo to client */
    private void syncComboToClient(UUID playerId, Player player) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            int combo = getCombo(playerId);
            NetworkHandler.sendToClient(new SyncComboPacket(combo), serverPlayer);
        }
    }

    /** Return base attack stat */
    public double getBaseAttack() {
        return getAttackDamage();
    }

    @Override
    public void applyClassEffect(Player player) {
        // Keep your existing attribute/effect code
    }

    @Override
    public void removeClassEffect(Player player) {
        // Keep your existing remove code
    }
}
