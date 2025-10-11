package net.pablo.rpgclasses.skills;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.pablo.rpgclasses.classes.Fighter;

import java.util.*;

/**
 * Central server-side manager:
 * - Tracks players who initiated Skybreaker jump
 * - Detects landing via previous-on-ground tracking
 * - Applies AOE damage, delayed stun (launch then stun), particles, sounds
 * - Integrates with CooldownManager for refunds if no targets hit
 *
 * Note: This class uses static state and exposes a few utility methods for packet to call.
 * It is registered as an event listener for server ticks elsewhere (or via @SubscribeEvent here).
 */
public class SeismicSmashManager {
    public static final double SKILL_COOLDOWN_SECONDS = 25.0;
    private static final double AOE_RADIUS = 5.0;
    private static final double BASE_DAMAGE = 6.0;
    private static final double ATTACK_SCALING = 0.15;
    private static final double BASE_STUN_SECONDS = 2.0;
    private static final double STUN_PER_COMBO = 0.5;
    private static final double MAX_STUN_SECONDS = 4.0;
    private static final double COOLDOWN_REFUND_PERCENT = 0.5;

    // Tracking maps (thread-safe-ish via synchronized blocks where needed)
    private static final Map<UUID, Boolean> jumpingPlayers = new HashMap<>();
    private static final Map<UUID, Boolean> wasOnGround = new HashMap<>();
    // pendingCooldowns: set by packet start; confirmed or refunded on landing
    private static final Map<UUID, Double> pendingCooldowns = new HashMap<>();

    // Delayed stuns (allow launch to complete then apply effects)
    private static final class DelayedStun {
        final float stunSeconds;
        final Player source;
        int ticksLeft;
        DelayedStun(float s, Player src, int delayTicks) { stunSeconds = s; source = src; ticksLeft = delayTicks; }
    }
    private static final Map<UUID, DelayedStun> delayedStuns = new HashMap<>();

    public static void startJump(ServerPlayer serverPlayer) {
        UUID id = serverPlayer.getUUID();

        synchronized (jumpingPlayers) {
            // Prevent activating if already mid-jump
            if (jumpingPlayers.getOrDefault(id, false)) return;

            // Mark as jumping
            jumpingPlayers.put(id, true);
            wasOnGround.put(id, true);

            // Set initial cooldown in CooldownManager (will be adjusted on landing)
            SeismicCooldownManager.setCooldown(serverPlayer, SKILL_COOLDOWN_SECONDS);
            // Store pending cooldown to know how much to refund if missed
            pendingCooldowns.put(id, SKILL_COOLDOWN_SECONDS);
        }

        // Apply upward movement
        serverPlayer.setDeltaMovement(
                serverPlayer.getDeltaMovement().x,
                0.8, // jump velocity
                serverPlayer.getDeltaMovement().z
        );

        // Play jump sound
        serverPlayer.level().playSound(
                null,
                serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS,
                1.0f, 0.8f
        );
    }


    public static void setPendingCooldown(ServerPlayer player, double seconds) {
        pendingCooldowns.put(player.getUUID(), seconds);
    }

    // Should be registered to server tick events; kept static for simplicity.
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Landing checks
        List<UUID> toProcess = new ArrayList<>();
        synchronized (jumpingPlayers) {
            toProcess.addAll(jumpingPlayers.keySet());
        }
        for (UUID uuid : toProcess) {
            ServerPlayer player = getServerPlayer(uuid);
            if (player == null) {
                synchronized (jumpingPlayers) { jumpingPlayers.remove(uuid); wasOnGround.remove(uuid); pendingCooldowns.remove(uuid); }
                continue;
            }
            boolean currentlyOnGround = player.onGround();
            boolean previously = wasOnGround.getOrDefault(uuid, false);

            if (!previously && currentlyOnGround && jumpingPlayers.getOrDefault(uuid, false)) {
                // landed
                handleLanding(player);
                synchronized (jumpingPlayers) { jumpingPlayers.remove(uuid); wasOnGround.remove(uuid); }
            } else {
                wasOnGround.put(uuid, currentlyOnGround);
            }
        }

        // Handle delayed stuns
        handleDelayedStuns();
    }

    private static void handleLanding(ServerPlayer player) {
        Fighter fighter = getPrimaryFighterClass(player);
        if (fighter == null) return;

        player.fallDistance = 0f;

        int combo = fighter.getCombo(player.getUUID());
        double damage = BASE_DAMAGE + combo + fighter.getBaseAttack() * ATTACK_SCALING;

        Level world = player.level();
        List<LivingEntity> targets = getNearbyEnemies(player, AOE_RADIUS);

        // Apply landing effects & sound
        createLandingEffects(player);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.0f);

        boolean hitAny = false;
        double stunSeconds = Math.min(BASE_STUN_SECONDS + STUN_PER_COMBO * combo, MAX_STUN_SECONDS);

        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), (float) damage);
            target.setDeltaMovement(target.getDeltaMovement().x, 0.8, target.getDeltaMovement().z);
            delayedStuns.put(target.getUUID(), new DelayedStun((float) stunSeconds, player, 2));
            addLaunchParticlesFor(target);
            hitAny = true;
        }

        // Adjust cooldown
        Double pending = pendingCooldowns.remove(player.getUUID());
        if (pending != null && !hitAny) {
            // refund 50% if missed
            SeismicCooldownManager.refundCooldownPercent(player, COOLDOWN_REFUND_PERCENT);
        }

        // Remove player from jumping map
        synchronized (jumpingPlayers) {
            jumpingPlayers.remove(player.getUUID());
            wasOnGround.remove(player.getUUID());
        }
    }



    private static void handleDelayedStuns() {
        Iterator<Map.Entry<UUID, DelayedStun>> it = delayedStuns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DelayedStun> e = it.next();
            DelayedStun ds = e.getValue();
            ds.ticksLeft--;
            if (ds.ticksLeft <= 0) {
                it.remove();
                LivingEntity target = findEntityByUUID(e.getKey());
                if (target != null && target.isAlive()) {
                    // apply stun effects
                    applyFinalStunEffects(target, ds.stunSeconds, ds.source);
                }
            }
        }
    }

    private static void applyFinalStunEffects(LivingEntity target, float stunSeconds, Player source) {
        int ticks = Math.round(stunSeconds * 20f);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 255, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 255, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 255, false, true));

        target.setDeltaMovement(0,0,0);
        target.hurtMarked = true;

        Level world = target.level();
        if (world instanceof ServerLevel serverLevel) {
            createDizzyEffect(serverLevel, target, stunSeconds);
        }

        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5f, 1.25f);
    }

    private static void createLandingEffects(Player player) {
        Level world = player.level();

        // Create ground impact particles
        for (int i = 0; i < 20; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double distance = world.random.nextDouble() * AOE_RADIUS;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;

            world.addParticle(ParticleTypes.CLOUD, x, player.getY(), z, 0, 0.1, 0);
            world.addParticle(ParticleTypes.CRIT, x, player.getY() + 0.1, z, 0, 0, 0);
        }

        // Stun effect particles around hit enemies
        List<LivingEntity> targets = getNearbyEnemies(player, AOE_RADIUS);
        for (LivingEntity target : targets) {
            for (int i = 0; i < 5; i++) {
                world.addParticle(ParticleTypes.ENCHANTED_HIT,
                        target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        0, 0.1, 0);
            }
        }
    }

    private static void addLaunchParticlesFor(LivingEntity e) {
        Level world = e.level();
        if (world instanceof ServerLevel serverLevel) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double radius = 0.4;
                double x = e.getX() + Math.cos(angle) * radius;
                double z = e.getZ() + Math.sin(angle) * radius;
                serverLevel.sendParticles(ParticleTypes.CLOUD, x, e.getY(), z, 2, 0, 0.1, 0, 0.05);
            }
            serverLevel.sendParticles(ParticleTypes.POOF, e.getX(), e.getY(), e.getZ(), 6, 0.2, 0.15, 0.2, 0.06);
            e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SLIME_JUMP, SoundSource.NEUTRAL, 0.8f, 0.75f);
        }
    }

    private static void createDizzyEffect(ServerLevel serverLevel, LivingEntity target, double duration) {
        double cx = target.getX();
        double cy = target.getY() + target.getBbHeight() + 0.8;
        double cz = target.getZ();
        double radius = 0.6;
        // single-frame circle + center glows; a repeating animation could be added by scheduling ticks.
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            ParticleOptions p = (i % 3 == 0) ? ParticleTypes.CRIT : (i % 3 == 1) ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.ELECTRIC_SPARK;
            serverLevel.sendParticles(p, x, cy, z, 1, 0, 0, 0, 0.1);
        }
        serverLevel.sendParticles(ParticleTypes.GLOW, cx, cy, cz, 3, 0.2, 0.2, 0.2, 0.04);
    }

    private static LivingEntity findEntityByUUID(UUID uuid) {
        // try to locate entity across all loaded server levels
        Optional<ServerLevel> maybe = Optional.empty();
        try {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            for (Level lvl : server.getAllLevels()) {
                if (lvl instanceof ServerLevel s) {
                    Entity ent = s.getEntity(uuid);
                    if (ent instanceof LivingEntity le) return le;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static List<LivingEntity> getNearbyEnemies(Player player, double radius) {
        Level world = player.level();
        AABB area = player.getBoundingBox().inflate(radius);

        return world.getEntitiesOfClass(LivingEntity.class, area, e ->
                e != player && e.isAlive()
        );
    }


    private static ServerPlayer getServerPlayer(UUID uuid) {
        try {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) return null;
            return server.getPlayerList().getPlayer(uuid);
        } catch (Exception e) {
            return null;
        }
    }

    private static Fighter getPrimaryFighterClass(Player player) {
        var cap = player.getCapability(net.pablo.rpgclasses.capability.PlayerClassProvider.PLAYER_CLASS_CAPABILITY).orElse(null);
        if (cap == null) return null;
        if (cap.getSelectedClass() instanceof Fighter f) return f;
        return null;
    }
}
