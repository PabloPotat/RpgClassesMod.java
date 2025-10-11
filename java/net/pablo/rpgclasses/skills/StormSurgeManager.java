package net.pablo.rpgclasses.skills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.RpgClassesMod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StormSurgeManager {

    // Constants
    public static final int DURATION_TICKS = 100; // 5 seconds
    public static final double COOLDOWN_SECONDS = 40.0;
    private static final double ARMOR_BUFF = 10.0;
    private static final double MOVEMENT_SLOW_MULTIPLIER = -0.5;

    // Shockwave params
    private static final double SHOCKWAVE_MAX_RADIUS = 5.0;
    private static final double SHOCKWAVE_STEP = 0.3;
    private static final double SHOCKWAVE_KNOCKBACK = 1.5;
    private static final double SHOCKWAVE_DAMAGE_MULTIPLIER = 0.5;

    // UUIDs
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("b7f1e2c2-5a4b-4a6e-9f3d-1b2e3d4c5f6a");
    private static final UUID ARMOR_BUFF_UUID = UUID.fromString("a1111111-1111-1111-1111-111111111111");
    private static final UUID KNOCKBACK_RESISTANCE_UUID = UUID.fromString("c2222222-2222-2222-2222-222222222222");

    // Modifier names
    private static final String ARMOR_MODIFIER_NAME = "stormsurge_armor_buff";
    private static final String SPEED_MODIFIER_NAME = "stormsurge_slow";
    private static final String KNOCKBACK_RESISTANCE_NAME = "stormsurge_knockback_immunity";

    // Data storage
    private static final Map<UUID, StormSurgeData> activeStormSurgePlayers = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, Double>> damageReceived = new ConcurrentHashMap<>();
    private static final Map<UUID, ShockwaveData> activeShockwaves = new ConcurrentHashMap<>();

    /** Data class for tracking stormbound state */
    private static class StormSurgeData {
        public final long startTick;
        public final UUID playerId;

        public StormSurgeData(UUID playerId, long startTick) {
            this.playerId = playerId;
            this.startTick = startTick;

        }
    }

    /** Data class for tracking shockwave state */
    private static class ShockwaveData {
        public final UUID playerId;
        public double radius;
        public final float damage;
        public final long startTick;

        public ShockwaveData(UUID playerId, float damage, long startTick) {
            this.playerId = playerId;
            this.radius = 0;
            this.damage = damage;
            this.startTick = startTick;
        }
    }

    /** Start Stormbound */
    public static void startStormSurge(ServerPlayer player) {
        UUID playerId = player.getUUID();

        if (StormCooldownManager.isOnCooldown(player) || isStormSurgeActive(player) || isShockwaveActive(player)){
            return;
        }

        // Spawn lightning on player to signal start
        if (player.level() instanceof ServerLevel serverLevel) {
            spawnVisualLightning(serverLevel, player);
        }

        // Apply buffs and debuffs
        applyStormSurgeEffects(player);

        // Play activation sound
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.8f);

        // Initialize damage tracking
        damageReceived.put(playerId, new HashMap<>());

        // Store active state
        activeStormSurgePlayers.put(playerId, new StormSurgeData(playerId, player.level().getGameTime()));
    }

    /** Apply buffs */
    private static void applyStormSurgeEffects(Player player) {
        // Remove any existing modifiers first to avoid duplicates
        removeStormSurgeEffects(player);

        AttributeModifier armorModifier = new AttributeModifier(
                ARMOR_BUFF_UUID, ARMOR_MODIFIER_NAME, ARMOR_BUFF, AttributeModifier.Operation.ADDITION);
        player.getAttribute(Attributes.ARMOR).addTransientModifier(armorModifier);

        AttributeModifier speedModifier = new AttributeModifier(
                SPEED_MODIFIER_UUID, SPEED_MODIFIER_NAME, MOVEMENT_SLOW_MULTIPLIER, AttributeModifier.Operation.MULTIPLY_TOTAL);
        player.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(speedModifier);

        AttributeModifier knockbackModifier = new AttributeModifier(
                KNOCKBACK_RESISTANCE_UUID, KNOCKBACK_RESISTANCE_NAME, 1.0, AttributeModifier.Operation.ADDITION);
        player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).addTransientModifier(knockbackModifier);
    }

    /** Remove buffs */
    private static void removeStormSurgeEffects(Player player) {
        // Just remove the modifiers by UUID - it's safe to call even if they don't exist
        player.getAttribute(Attributes.ARMOR).removeModifier(ARMOR_BUFF_UUID);
        player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED_MODIFIER_UUID);
        player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).removeModifier(KNOCKBACK_RESISTANCE_UUID);
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && isStormSurgeActive(player)) {
            LivingEntity attacker = null;
            if (event.getSource().getEntity() instanceof LivingEntity livingAttacker) {
                attacker = livingAttacker;
            }
            if (attacker != null) {
                recordDamage(player, event.getAmount(), attacker);
                StormCooldownManager.markDamageTaken(player);
            }
        }
    }

    /** Record damage */
    public static void recordDamage(Player player, double amount, LivingEntity attacker) {
        if (player == null || attacker == null || !isStormSurgeActive(player)) return;

        UUID playerId = player.getUUID();
        UUID attackerId = attacker.getUUID();

        damageReceived.computeIfAbsent(playerId, k -> new HashMap<>())
                .merge(attackerId, amount, Double::sum);
    }

    /** End Stormbound */
    public static void endStormSurge(Player player) {
        UUID playerId = player.getUUID();
        StormSurgeData data = activeStormSurgePlayers.remove(playerId);
        if (data == null) return;

        // Remove buffs
        removeStormSurgeEffects(player);

        // Handle cooldown with refund logic (sets cooldown internally)
        StormCooldownManager.onStormSurgeEnd(player);

        // Shockwave at end (only if damage was taken)
        if (StormCooldownManager.tookDamage(player) && player.level() instanceof ServerLevel serverLevel) {
            spawnShockwave(serverLevel, player);
        }

        // Clean up damage tracking
        damageReceived.remove(playerId);
    }

    /** Shockwave implementation */
    private static void spawnShockwave(ServerLevel level, Player player) {
        double totalDamageReceived = damageReceived.getOrDefault(player.getUUID(), Collections.emptyMap())
                .values().stream().mapToDouble(Double::doubleValue).sum();

        if (totalDamageReceived <= 0) return;

        final float scaledDamage = (float) (totalDamageReceived * SHOCKWAVE_DAMAGE_MULTIPLIER);

        // Play shockwave sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 0.5f);

        // Start shockwave animation
        activeShockwaves.put(player.getUUID(), new ShockwaveData(player.getUUID(), scaledDamage, level.getGameTime()));
    }

    /** Update shockwave expansion */
    private static void updateShockwave(ServerLevel level, Player player, ShockwaveData shockwaveData) {
        if (shockwaveData.radius >= SHOCKWAVE_MAX_RADIUS) {
            activeShockwaves.remove(player.getUUID());

            return;
        }

        // Create custom damage source from player
        DamageSource shockwaveDamage = level.damageSources().playerAttack((ServerPlayer) player);

        double currentRadius = shockwaveData.radius;

        // Spawn particles in a circle
        for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI / 8) {
            double x = player.getX() + currentRadius * Math.cos(angle);
            double z = player.getZ() + currentRadius * Math.sin(angle);
            double y = player.getY() + 0.5;

            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    x, y, z, 3, 0.1, 0.1, 0.1, 0.05);
        }

        // Damage and knockback at the current ring
        if (currentRadius > 0) {
            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(currentRadius + 0.5));

            for (LivingEntity entity : entities) {
                if (entity == player || !entity.isAlive()) continue;

                double distance = entity.distanceTo(player);
                if (distance <= currentRadius + 0.5 && distance >= currentRadius - 0.5) {
                    // Apply knockback
                    Vec3 direction = entity.position().subtract(player.position()).normalize();
                    entity.setDeltaMovement(
                            direction.x * SHOCKWAVE_KNOCKBACK,
                            Math.min(0.7, SHOCKWAVE_KNOCKBACK * 0.3),
                            direction.z * SHOCKWAVE_KNOCKBACK
                    );

                    // Apply damage directly from player
                    entity.hurt(shockwaveDamage, shockwaveData.damage);

                    // Play hit sound
                    level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3f, 1.2f);
                }
            }
        }

        // Increase radius for next tick
        shockwaveData.radius += SHOCKWAVE_STEP;
    }

    /** Spawn visual lightning on player */
    private static void spawnVisualLightning(ServerLevel serverLevel, LivingEntity target) {
        LightningBolt lightning = new LightningBolt(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, serverLevel);
        lightning.moveTo(target.getX(), target.getY(), target.getZ());
        lightning.setVisualOnly(true);
        lightning.setCause(null);
        serverLevel.addFreshEntity(lightning);

        // Play lightning sound
        serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0f, 1.0f);
    }

    /** Check active */
    public static boolean isStormSurgeActive(Player player) {
        return activeStormSurgePlayers.containsKey(player.getUUID());
    }

    /** Check if shockwave is active */
    public static boolean isShockwaveActive(Player player) {
        return activeShockwaves.containsKey(player.getUUID());
    }

    /** Get remaining duration */
    public static int getRemainingDuration(Player player) {
        StormSurgeData data = activeStormSurgePlayers.get(player.getUUID());
        if (data == null || player.level() == null) return 0;

        long elapsed = player.level().getGameTime() - data.startTick;
        return Math.max(0, DURATION_TICKS - (int) elapsed);
    }

    /** Tick handler */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;

        // Handle shockwave animation
        if (isShockwaveActive(player)) {
            ShockwaveData shockwaveData = activeShockwaves.get(player.getUUID());
            if (player.level() instanceof ServerLevel serverLevel) {
                updateShockwave(serverLevel, player, shockwaveData);
            }
            return;
        }

        // Handle normal stormbound duration
        if (isStormSurgeActive(player)) {
            int remaining = getRemainingDuration(player);
            if (remaining <= 0) {
                endStormSurge(player);
            }
        }
    }
}