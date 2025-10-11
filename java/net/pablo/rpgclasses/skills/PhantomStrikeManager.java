package net.pablo.rpgclasses.skills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.effect.ModEffects;

import java.util.*;

@Mod.EventBusSubscriber
public class PhantomStrikeManager {

    private static final int PHANTOM_DURATION_TICKS = 140;
    private static final int HIT_EXTENSION_TICKS = 30;
    private static final int WAVE_DURATION_TICKS = 14;
    private static final double BASE_DAMAGE = 7.0;
    private static final int MAX_WAVES = 3;

    private static final Map<UUID, List<SlashWave>> activeWaves = new HashMap<>();
    private static final Map<UUID, Integer> slashCount = new HashMap<>();

    public static void activate(ServerPlayer player, long tick) {
        if (PhantomCooldownManager.isOnCooldown(player)) return;

        PhantomCooldownManager.setCooldown(player);
        slashCount.put(player.getUUID(), 0);

        player.addEffect(new MobEffectInstance(
                ModEffects.PHANTOM.get(),
                PHANTOM_DURATION_TICKS,
                0,
                false,
                true
        ));
    }

    @SubscribeEvent
    public static void onPlayerAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (!player.hasEffect(ModEffects.PHANTOM.get())) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;

        UUID playerId = player.getUUID();
        int currentCount = slashCount.getOrDefault(playerId, 0);

        if (currentCount >= MAX_WAVES) {
            return;
        }

        slashCount.put(playerId, currentCount + 1);

        player.addEffect(new MobEffectInstance(
                ModEffects.PHANTOM.get(),
                HIT_EXTENSION_TICKS,
                0,
                false,
                true
        ));

        createSlashWave(player);
    }

    private static void createSlashWave(ServerPlayer player) {
        long tick = player.level().getGameTime();

        List<SlashWave> waves = activeWaves.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());

        waves.removeIf(wave -> !wave.isActive(tick));

        SlashWave newWave = new SlashWave(player, tick);
        waves.add(newWave);

        if (player.level() instanceof ServerLevel serverLevel) {
            newWave.tick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<UUID> toRemoveCount = new ArrayList<>();
            for (Map.Entry<UUID, Integer> entry : slashCount.entrySet()) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player == null || !player.hasEffect(ModEffects.PHANTOM.get())) {
                    toRemoveCount.add(entry.getKey());
                }
            }
            toRemoveCount.forEach(slashCount::remove);

            List<Map.Entry<UUID, List<SlashWave>>> waveEntries = new ArrayList<>(activeWaves.entrySet());
            for (Map.Entry<UUID, List<SlashWave>> entry : waveEntries) {
                List<SlashWave> waves = entry.getValue();

                List<SlashWave> wavesToRemove = new ArrayList<>();
                for (SlashWave wave : new ArrayList<>(waves)) {
                    if (!wave.tick(level)) {
                        wavesToRemove.add(wave);
                    }
                }
                waves.removeAll(wavesToRemove);

                if (waves.isEmpty()) {
                    activeWaves.remove(entry.getKey());
                }
            }
        }
    }

    private static class SlashWave {
        private final UUID ownerId;
        private final long startTick;
        private final Set<UUID> hitEntities = new HashSet<>();
        private final Vec3 startPos;
        private final Vec3 direction;

        public SlashWave(ServerPlayer owner, long startTick) {
            this.ownerId = owner.getUUID();
            this.startTick = startTick;
            this.startPos = owner.position().add(0, owner.getEyeHeight() * 0.5, 0);
            this.direction = owner.getLookAngle().normalize();
        }

        public boolean isActive(long currentTick) {
            return (currentTick - startTick) <= WAVE_DURATION_TICKS;
        }

        public boolean tick(ServerLevel level) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner == null) return false;

            long age = level.getGameTime() - startTick;
            if (age > WAVE_DURATION_TICKS) return false;

            double progress = age / (double) WAVE_DURATION_TICKS;
            double forwardDistance = progress * 5.0;
            double radius = 1.8 + progress * 3.0;
            Vec3 origin = startPos.add(direction.scale(forwardDistance));

            spawnSlashParticles(level, origin, direction, radius, progress, age);

            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(radius + forwardDistance),
                    e -> e != owner && !hitEntities.contains(e.getUUID()))) {

                Vec3 toEntity = entity.position().subtract(origin);
                double distance = toEntity.length();
                if (distance > 0.5 && distance <= radius) {
                    double dot = direction.dot(toEntity.normalize());
                    if (dot > 0.2) {
                        entity.hurt(owner.damageSources().playerAttack(owner), (float) BASE_DAMAGE);
                        hitEntities.add(entity.getUUID());

                        Vec3 knockback = direction.add(toEntity.normalize().scale(0.3)).normalize().scale(0.6);
                        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback.x, 0.25, knockback.z));
                    }
                }
            }

            return true;
        }

        private void spawnSlashParticles(ServerLevel level, Vec3 origin, Vec3 look, double radius, double progress, long age) {
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 up = right.cross(look).normalize();

            int totalParticles = 8;
            for (int i = 0; i < totalParticles; i++) {
                double t = i / (double) (totalParticles - 1);
                double angle = (t - 0.5) * Math.PI;

                double cosAngle = Math.cos(angle);
                double sinAngle = Math.sin(angle);

                Vec3 direction = look.scale(cosAngle)
                        .add(right.scale(sinAngle * 0.8))
                        .add(up.scale(sinAngle * 0.3))
                        .normalize();

                Vec3 particlePos = origin.add(direction.scale(radius));

                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                        particlePos.x(), particlePos.y(), particlePos.z(),
                        1, 0.0, 0.0, 0.0, 0.0);
            }

            if (age == 0) {
                level.playSound(null, origin.x(), origin.y(), origin.z(),
                        net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.3f);
            }
        }
    }
}