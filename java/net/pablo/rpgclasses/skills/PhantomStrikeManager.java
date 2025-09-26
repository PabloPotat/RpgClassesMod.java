package net.pablo.rpgclasses.skills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber
public class PhantomStrikeManager {

    private static final int INVIS_DURATION_TICKS = 60;  // 3 seconds
    private static final int WAVE_DURATION_TICKS = 10;   // slash lasts 10 ticks
    private static final double BASE_DAMAGE = 6.0;       // fixed damage (customizable)

    private static final Map<UUID, Long> invisMap = new HashMap<>();
    private static final Map<UUID, List<SlashWave>> activeWaves = new HashMap<>();

    // ===== Activation =====
    public static void activate(ServerPlayer player, long tick) {
        invisMap.put(player.getUUID(), tick + INVIS_DURATION_TICKS);

        // Stable invisibility effect (no blinking)
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, INVIS_DURATION_TICKS, 0, false, false));
    }

    // ===== Attack Handling (now inside manager) =====
    @SubscribeEvent
    public static void onPlayerAttackEvent(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;

        // Only trigger if player is currently invisible
        if (!invisMap.containsKey(player.getUUID())) return;

        long tick = player.level().getGameTime();

        // End invisibility almost immediately
        invisMap.put(player.getUUID(), tick + 1);

        // Always spawn one slash arc
        activeWaves.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                .add(new SlashWave(player, tick));
    }

    // ===== Tick Loop =====
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            long tick = level.getGameTime();

            // Handle invisibility expiry
            Iterator<Map.Entry<UUID, Long>> invisIt = invisMap.entrySet().iterator();
            while (invisIt.hasNext()) {
                Map.Entry<UUID, Long> entry = invisIt.next();
                if (tick >= entry.getValue()) {
                    invisIt.remove();
                    // Effect wears off naturally
                }
            }

            // Handle active slash waves
            Iterator<Map.Entry<UUID, List<SlashWave>>> waveIt = activeWaves.entrySet().iterator();
            while (waveIt.hasNext()) {
                List<SlashWave> waves = waveIt.next().getValue();
                waves.removeIf(w -> !w.tick(level));
                if (waves.isEmpty()) waveIt.remove();
            }
        }
    }

    // ===== Slash Wave =====
    private static class SlashWave {
        private final UUID ownerId;
        private final long startTick;

        public SlashWave(ServerPlayer owner, long startTick) {
            this.ownerId = owner.getUUID();
            this.startTick = startTick;
        }

        public boolean tick(ServerLevel level) {
            ServerPlayer owner = ownerFromId(level, ownerId);
            if (owner == null) return false;

            long age = level.getGameTime() - startTick;
            if (age > WAVE_DURATION_TICKS) return false;

            double progress = age / (double) WAVE_DURATION_TICKS;
            double radius = 1.0 + progress * 3.0; // grows outward
            Vec3 origin = owner.position();
            Vec3 look = owner.getLookAngle().normalize();

            for (LivingEntity e : owner.level().getEntitiesOfClass(
                    LivingEntity.class, owner.getBoundingBox().inflate(radius))) {
                if (e != owner) {
                    Vec3 dir = e.position().subtract(origin).normalize();
                    double dot = look.dot(dir);
                    if (dot > 0.5) { // ~60° cone in front
                        e.hurt(owner.damageSources().playerAttack(owner), (float) BASE_DAMAGE);
                    }
                }
            }

            return true;
        }

        private ServerPlayer ownerFromId(ServerLevel level, UUID id) {
            for (ServerLevel l : level.getServer().getAllLevels()) {
                ServerPlayer sp = l.getServer().getPlayerList().getPlayer(id);
                if (sp != null) return sp;
            }
            return null;
        }
    }
}
