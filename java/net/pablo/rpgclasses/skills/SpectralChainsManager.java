package net.pablo.rpgclasses.skills;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.effect.ModEffects;

import java.util.*;

/**
 * Spectral Chains - Ranged Amplifier / Control
 *
 * Next 3 projectiles are imbued with spectral energy:
 * - First hit → Anchored (30% slow, +15% ranged damage from caster) - 7s duration
 * - 2nd hit → Tethered (can't exceed 6 blocks from anchor, share 10% bonus damage) - 5s duration for all
 * - 3rd hit → 2nd Tether - 3s duration for all
 *
 * Note: Cooldown handled externally by cooldown manager
 */
@Mod.EventBusSubscriber
public class SpectralChainsManager {

    // Config
    private static final int PROJECTILE_COUNT = 3;
    private static final long EFFECT_DURATION_SOLO = 7_000; // 7s for anchor alone
    private static final long EFFECT_DURATION_TWO = 5_000; // 5s when 2nd enemy hit
    private static final long EFFECT_DURATION_THREE = 3_000; // 3s when 3rd enemy hit

    private static final float ANCHOR_SPEED_MULTIPLIER = 0.7f; // 30% slow
    private static final float BONUS_DAMAGE_MULTIPLIER = 0.15f; // +15% damage
    private static final float TETHER_DAMAGE_SHARE = 0.10f; // 10% shared
    private static final double TETHER_RANGE = 6.0; // blocks
    private static final double TETHER_PULL_STRENGTH = 0.15; // pull force multiplier

    private static final float EXPIRE_BURST_DAMAGE = 4.0f; // AOE damage on expire
    private static final double EXPIRE_BURST_RADIUS = 4.0; // blocks

    // State
    private static final Map<UUID, AbilityState> activeAbilities = new HashMap<>();
    private static final Map<UUID, UUID> spectralProjectiles = new HashMap<>();
    private static final Map<UUID, ChainEffect> activeEffects = new HashMap<>();

    // Visuals
    private static final Map<UUID, List<ChainParticle>> activeChainParticles = new HashMap<>();
    private static final Map<UUID, HaloTask> activeHalos = new HashMap<>();

    // ====== Inner Classes ======
    private static class AbilityState {
        final UUID ownerId;
        int projectilesRemaining;

        AbilityState(UUID ownerId) {
            this.ownerId = ownerId;
            this.projectilesRemaining = PROJECTILE_COUNT;
        }
    }

    private static class ChainEffect {
        final UUID ownerId;
        UUID anchoredEnemyId;
        final List<UUID> tetheredEnemyIds = new ArrayList<>();
        long effectExpiry;

        ChainEffect(UUID ownerId, UUID anchoredId, long currentTime) {
            this.ownerId = ownerId;
            this.anchoredEnemyId = anchoredId;
            this.effectExpiry = currentTime + EFFECT_DURATION_SOLO;
        }

        void addTether(UUID enemyId) {
            if (tetheredEnemyIds.size() < 2 && !tetheredEnemyIds.contains(enemyId)) {
                tetheredEnemyIds.add(enemyId);
            }
        }

        int getTotalAffectedCount() {
            return 1 + tetheredEnemyIds.size(); // anchor + tethers
        }

        long getDurationForCount(int count) {
            switch(count) {
                case 1: return EFFECT_DURATION_SOLO;
                case 2: return EFFECT_DURATION_TWO;
                case 3: return EFFECT_DURATION_THREE;
                default: return EFFECT_DURATION_THREE;
            }
        }

        void updateDuration(long currentTime) {
            int count = getTotalAffectedCount();
            long newDuration = getDurationForCount(count);
            this.effectExpiry = currentTime + newDuration;
        }

        boolean isAnchored(UUID enemyId) {
            return anchoredEnemyId != null && anchoredEnemyId.equals(enemyId);
        }

        boolean isTethered(UUID enemyId) {
            return tetheredEnemyIds.contains(enemyId);
        }

        boolean isAffected(UUID enemyId) {
            return isAnchored(enemyId) || isTethered(enemyId);
        }
    }

    private static class ChainParticle {
        final ServerLevel level;
        final UUID anchorId;
        final UUID tetherId;
        int ticks = 0;

        ChainParticle(ServerLevel level, UUID anchorId, UUID tetherId) {
            this.level = level;
            this.anchorId = anchorId;
            this.tetherId = tetherId;
        }

        boolean tick() {
            ticks++;
            if (ticks % 10 != 0) return true; // pulse every 0.5s

            LivingEntity anchor = getEntity(level, anchorId);
            LivingEntity tether = getEntity(level, tetherId);
            if (anchor == null || tether == null || !anchor.isAlive() || !tether.isAlive()) {
                return false;
            }

            Vec3 start = anchor.position().add(0, anchor.getBbHeight() * 0.5, 0);
            Vec3 end = tether.position().add(0, tether.getBbHeight() * 0.5, 0);
            Vec3 dir = end.subtract(start).normalize();
            double distance = start.distanceTo(end);

            // Draw ghostly chain beam
            for (double d = 0; d < distance; d += 0.5) {
                Vec3 pos = start.add(dir.scale(d));
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.01);
            }

            // Subtle metallic clink sound
            level.playSound(null, anchor.getX(), anchor.getY(), anchor.getZ(),
                    SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.3f, 1.5f);

            return true;
        }
    }

    private static class HaloTask {
        final ServerLevel level;
        final UUID targetId;
        long expiryTime;

        HaloTask(ServerLevel level, UUID targetId, long expiryTime) {
            this.level = level;
            this.targetId = targetId;
            this.expiryTime = expiryTime;
        }

        boolean tick() {
            if (System.currentTimeMillis() > expiryTime) return false;

            LivingEntity target = getEntity(level, targetId);
            if (target == null || !target.isAlive()) return false;

            double radius = target.getBbWidth() * 0.8;
            double yOffset = target.getBbHeight() * 0.6;
            double angle = (level.getGameTime() % 360) * Math.PI / 30;

            double x = target.getX() + radius * Math.cos(angle);
            double z = target.getZ() + radius * Math.sin(angle);

            // Cyan soul wisps rotating around anchored target
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, target.getY() + yOffset, z, 1, 0, 0.02, 0, 0.01);

            return true;
        }
    }

    // ====== Activation ======
    public static void activate(Player player) {
        UUID playerId = player.getUUID();

        activeAbilities.put(playerId, new AbilityState(playerId));

        // Activation visuals
        if (player.level() instanceof ServerLevel) {
            ServerLevel level = (ServerLevel) player.level();
            // Soul particle ring
            for (int i = 0; i < 20; i++) {
                double angle = (Math.PI * 2 * i) / 20;
                double radius = 1.5;
                double x = player.getX() + Math.cos(angle) * radius;
                double z = player.getZ() + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.SOUL, x, player.getY() + 1, z, 1, 0, 0.1, 0, 0.05);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }

    // ====== Automatic Projectile Tracking ======
    /**
     * Automatically tracks projectiles fired by players with active Spectral Chains.
     * Works with vanilla arrows, modded projectiles, spell projectiles, etc.
     * Empowers the next 3 projectiles regardless of timing.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // Only process on server side
        if (event.getLevel().isClientSide()) return;

        if (!(event.getEntity() instanceof Projectile)) return;
        Projectile projectile = (Projectile) event.getEntity();

        if (!(projectile.getOwner() instanceof Player)) return;
        Player shooter = (Player) projectile.getOwner();

        UUID shooterId = shooter.getUUID();
        AbilityState state = activeAbilities.get(shooterId);

        if (state == null || state.projectilesRemaining <= 0) return;

        // Mark projectile as spectral
        spectralProjectiles.put(projectile.getUUID(), shooterId);
        state.projectilesRemaining--;

        // Spectral glow trail
        if (shooter.level() instanceof ServerLevel) {
            ServerLevel level = (ServerLevel) shooter.level();
            Vec3 pos = projectile.position();
            for (int i = 0; i < 5; i++) {
                level.sendParticles(ParticleTypes.ENCHANT,
                        pos.x, pos.y, pos.z, 1, 0.1, 0.1, 0.1, 0.1);
            }
        }

        // End ability window if all projectiles used
        if (state.projectilesRemaining == 0) {
            activeAbilities.remove(shooterId);
        }
    }

    // ====== Projectile Impact ======
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Projectile)) return;
        Projectile projectile = (Projectile) event.getProjectile();

        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        ServerLevel level = (ServerLevel) event.getEntity().level();

        UUID projectileId = projectile.getUUID();
        UUID shooterId = spectralProjectiles.remove(projectileId);

        if (shooterId == null) return; // Not a spectral projectile

        // Find hit entity - check both direct hit and nearby entities
        LivingEntity hitEntity = null;

        // Try to get direct hit from raycast result
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult) {
            net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) event.getRayTraceResult();
            if (entityHit.getEntity() instanceof LivingEntity && !entityHit.getEntity().getUUID().equals(shooterId)) {
                hitEntity = (LivingEntity) entityHit.getEntity();
            }
        }

        // If no direct hit, search nearby
        if (hitEntity == null) {
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    projectile.getBoundingBox().inflate(2.0))) {
                if (!entity.getUUID().equals(shooterId)) {
                    hitEntity = entity;
                    break;
                }
            }
        }

        if (hitEntity == null) return;

        Player shooter = level.getServer().getPlayerList().getPlayer(shooterId);
        if (shooter == null) return;

        applySpectralHit(shooter, hitEntity, level);
    }

    private static void applySpectralHit(Player shooter, LivingEntity target, ServerLevel level) {
        UUID shooterId = shooter.getUUID();
        UUID targetId = target.getUUID();
        long now = System.currentTimeMillis();

        ChainEffect effect = activeEffects.get(shooterId);

        // First hit: Apply anchor
        if (effect == null) {
            effect = new ChainEffect(shooterId, targetId, now);
            activeEffects.put(shooterId, effect);

            // Apply Chained effect to anchor (7s duration)
            target.addEffect(new MobEffectInstance(ModEffects.CHAINED.get(),
                    (int)(EFFECT_DURATION_SOLO / 50), 0, false, true));

            // Anchor visuals: chains sink into ground + cyan soul wisps
            Vec3 pos = target.position();
            for (int i = 0; i < 30; i++) {
                double angle = (Math.PI * 2 * i) / 30;
                double radius = target.getBbWidth();
                double x = pos.x + Math.cos(angle) * radius;
                double z = pos.z + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, pos.y, z, 2, 0, 0, 0, 0.02);
            }

            // Ground chains particles
            for (int i = 0; i < 12; i++) {
                double angle = (Math.PI * 2 * i) / 12;
                double radius = target.getBbWidth() * 1.2;
                double x = pos.x + Math.cos(angle) * radius;
                double z = pos.z + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.SMOKE, x, pos.y + 0.1, z, 3, 0, 0.05, 0, 0.01);
            }

            // Start rotating halo
            activeHalos.put(targetId, new HaloTask(level, targetId, effect.effectExpiry));

            level.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        // Subsequent hits: Apply tether (max 2 tethers = 3 total with anchor)
        else if (effect.tetheredEnemyIds.size() < 2 &&
                !effect.isAnchored(targetId) &&
                !effect.isTethered(targetId)) {

            effect.addTether(targetId);

            // Update duration for all affected enemies
            effect.updateDuration(now);
            long newDuration = effect.effectExpiry - now;
            int newDurationTicks = (int)(newDuration / 50);

            // Reapply effect to anchor with new duration
            LivingEntity anchor = getEntity(level, effect.anchoredEnemyId);
            if (anchor != null) {
                anchor.removeEffect(ModEffects.CHAINED.get());
                anchor.addEffect(new MobEffectInstance(ModEffects.CHAINED.get(),
                        newDurationTicks, 0, false, true));
            }

            // Apply effect to newly tethered enemy
            target.addEffect(new MobEffectInstance(ModEffects.CHAINED.get(),
                    newDurationTicks, 0, false, true));

            // Update existing tethered enemies' duration
            for (UUID otherId : effect.tetheredEnemyIds) {
                if (otherId.equals(targetId)) continue; // Skip the one we just added
                LivingEntity other = getEntity(level, otherId);
                if (other != null) {
                    other.removeEffect(ModEffects.CHAINED.get());
                    other.addEffect(new MobEffectInstance(ModEffects.CHAINED.get(),
                            newDurationTicks, 0, false, true));
                }
            }

            // Update halo expiry times
            final ChainEffect finalEffect = effect;
            activeHalos.forEach((uuid, halo) -> {
                if (finalEffect.isAffected(uuid)) {
                    halo.expiryTime = finalEffect.effectExpiry;
                }
            });

            // Create ghostly chain beam particles
            UUID anchorId = effect.anchoredEnemyId;
            activeChainParticles.computeIfAbsent(shooterId, k -> new ArrayList<>())
                    .add(new ChainParticle(level, anchorId, targetId));

            // Tether application particles
            Vec3 pos = target.position();
            for (int i = 0; i < 20; i++) {
                double angle = (Math.PI * 2 * i) / 20;
                double radius = target.getBbWidth() * 0.6;
                double x = pos.x + Math.cos(angle) * radius;
                double z = pos.z + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.ENCHANT, x, pos.y + target.getBbHeight() * 0.5, z, 1, 0, 0, 0, 0.02);
            }

            level.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }

    // ====== Damage Amplification ======
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player)) return;
        Player attacker = (Player) event.getSource().getEntity();

        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity target = (LivingEntity) event.getEntity();

        UUID attackerId = attacker.getUUID();
        UUID targetId = target.getUUID();

        ChainEffect effect = activeEffects.get(attackerId);
        if (effect == null) return;

        long now = System.currentTimeMillis();
        if (now > effect.effectExpiry) {
            expireEffect(attackerId);
            return;
        }

        // Only amplify ranged damage (projectiles, spells)
        if (!isRangedDamage(event.getSource())) return;

        // Anchored target takes +15% bonus damage
        if (effect.isAnchored(targetId)) {
            float baseDamage = event.getAmount();
            float bonusDamage = baseDamage * BONUS_DAMAGE_MULTIPLIER;
            event.setAmount(baseDamage + bonusDamage);

            // Ripple effect along chains
            if (attacker.level() instanceof ServerLevel) {
                ServerLevel level = (ServerLevel) attacker.level();
                Vec3 pos = target.position();
                for (int i = 0; i < 15; i++) {
                    double angle = (Math.PI * 2 * i) / 15;
                    double radius = 0.8;
                    double x = pos.x + Math.cos(angle) * radius;
                    double z = pos.z + Math.sin(angle) * radius;
                    level.sendParticles(ParticleTypes.CRIT, x, pos.y + target.getBbHeight() * 0.5, z, 1, 0, 0.1, 0, 0.05);
                }
                // Metallic clink on bonus damage
                level.playSound(null, pos.x, pos.y, pos.z,
                        SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.5f, 1.5f);
            }

            // Share 10% of bonus damage to tethered enemies
            float sharedDamage = bonusDamage * TETHER_DAMAGE_SHARE;
            if (attacker.level() instanceof ServerLevel) {
                ServerLevel level = (ServerLevel) attacker.level();
                for (UUID tetheredId : effect.tetheredEnemyIds) {
                    LivingEntity tethered = getEntity(level, tetheredId);
                    if (tethered != null && tethered.isAlive()) {
                        tethered.hurt(attacker.damageSources().playerAttack(attacker), sharedDamage);

                        // Shared damage indicator particles
                        Vec3 tPos = tethered.position();
                        for (int i = 0; i < 8; i++) {
                            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                                    tPos.x, tPos.y + tethered.getBbHeight() * 0.5, tPos.z, 1, 0.2, 0.2, 0.2, 0.1);
                        }
                    }
                }
            }
        }
    }

    // ====== Tick Loop ======
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long now = System.currentTimeMillis();

        // Clean up expired effects
        List<UUID> expiredEffects = new ArrayList<>();
        for (Map.Entry<UUID, ChainEffect> entry : activeEffects.entrySet()) {
            if (now > entry.getValue().effectExpiry) {
                expiredEffects.add(entry.getKey());
            }
        }
        for (UUID ownerId : expiredEffects) {
            expireEffect(ownerId);
        }

        // Tick chain particles (ghostly beams between anchor and tethered)
        for (List<ChainParticle> particles : activeChainParticles.values()) {
            particles.removeIf(p -> !p.tick());
        }
        activeChainParticles.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // Tick halos (rotating cyan wisps around anchored)
        activeHalos.entrySet().removeIf(entry -> !entry.getValue().tick());

        // Apply movement constraints
        for (ChainEffect effect : activeEffects.values()) {
            if (effect.anchoredEnemyId == null) continue;

            for (ServerLevel serverLevel : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
                LivingEntity anchor = getEntity(serverLevel, effect.anchoredEnemyId);
                if (anchor == null || !anchor.isAlive()) continue;

                // Handle tethered enemies
                for (UUID tetheredId : effect.tetheredEnemyIds) {
                    LivingEntity tethered = getEntity(serverLevel, tetheredId);
                    if (tethered == null || !tethered.isAlive()) continue;

                    double distance = anchor.position().distanceTo(tethered.position());

                    // Enforce tether range: pull if too far
                    if (distance > TETHER_RANGE) {
                        Vec3 direction = anchor.position().subtract(tethered.position()).normalize();
                        double pullAmount = (distance - TETHER_RANGE) * TETHER_PULL_STRENGTH;
                        Vec3 pullVector = direction.scale(pullAmount);
                        tethered.setDeltaMovement(tethered.getDeltaMovement().add(pullVector));
                        tethered.hurtMarked = true;
                    }
                }
            }
        }
    }

    // ====== Effect Expiration ======
    private static void expireEffect(UUID ownerId) {
        ChainEffect effect = activeEffects.remove(ownerId);
        if (effect == null) return;

        // Get the player who owns this effect
        Player owner = null;
        for (ServerLevel serverLevel : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null) break;
        }

        // Shatter particles and AOE burst on expiration
        for (ServerLevel serverLevel : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            // Anchor shatter + AOE burst
            LivingEntity anchor = getEntity(serverLevel, effect.anchoredEnemyId);
            if (anchor != null) {
                Vec3 pos = anchor.position();

                // Explosive shatter particles
                for (int i = 0; i < 50; i++) {
                    double angle = (Math.PI * 2 * i) / 50;
                    double radius = 1.5;
                    double vx = Math.cos(angle) * 0.3;
                    double vz = Math.sin(angle) * 0.3;
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.x + vx, pos.y + 0.5, pos.z + vz, 1, vx, 0.1, vz, 0.2);
                }

                // Shockwave ring
                for (int i = 0; i < 30; i++) {
                    double angle = (Math.PI * 2 * i) / 30;
                    double radius = EXPIRE_BURST_RADIUS * 0.8;
                    double x = pos.x + Math.cos(angle) * radius;
                    double z = pos.z + Math.sin(angle) * radius;
                    serverLevel.sendParticles(ParticleTypes.POOF, x, pos.y + 0.2, z, 3, 0, 0.1, 0, 0.05);
                }

                // AOE Damage
                for (LivingEntity nearbyEntity : serverLevel.getEntitiesOfClass(LivingEntity.class,
                        anchor.getBoundingBox().inflate(EXPIRE_BURST_RADIUS))) {
                    if (nearbyEntity.getUUID().equals(ownerId)) continue; // Don't damage the caster

                    double distance = anchor.position().distanceTo(nearbyEntity.position());
                    if (distance <= EXPIRE_BURST_RADIUS) {
                        // Apply damage (with falloff based on distance)
                        float damageMultiplier = 1.0f - (float)(distance / EXPIRE_BURST_RADIUS) * 0.5f;
                        float finalDamage = EXPIRE_BURST_DAMAGE * damageMultiplier;

                        if (owner != null) {
                            nearbyEntity.hurt(owner.damageSources().playerAttack(owner), finalDamage);
                        } else {
                            nearbyEntity.hurt(serverLevel.damageSources().magic(), finalDamage);
                        }

                        // Knockback away from anchor
                        Vec3 knockbackDir = nearbyEntity.position().subtract(anchor.position()).normalize();
                        nearbyEntity.setDeltaMovement(nearbyEntity.getDeltaMovement().add(knockbackDir.scale(0.3)));
                        nearbyEntity.hurtMarked = true;
                    }
                }

                serverLevel.playSound(null, pos.x, pos.y, pos.z,
                        SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.3f);
            }

            // Tether shatter (smaller bursts)
            for (UUID tetheredId : effect.tetheredEnemyIds) {
                LivingEntity tethered = getEntity(serverLevel, tetheredId);
                if (tethered != null) {
                    Vec3 pos = tethered.position();

                    // Smaller shatter effect for tethered
                    for (int i = 0; i < 30; i++) {
                        double angle = (Math.PI * 2 * i) / 30;
                        double vx = Math.cos(angle) * 0.2;
                        double vz = Math.sin(angle) * 0.2;
                        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                                pos.x + vx, pos.y + 0.5, pos.z + vz, 1, vx, 0.05, vz, 0.12);
                    }

                    // Mini AOE damage around tethered enemies
                    for (LivingEntity nearbyEntity : serverLevel.getEntitiesOfClass(LivingEntity.class,
                            tethered.getBoundingBox().inflate(EXPIRE_BURST_RADIUS * 0.5))) {
                        if (nearbyEntity.getUUID().equals(ownerId)) continue;
                        if (nearbyEntity.getUUID().equals(tetheredId)) continue;

                        double distance = tethered.position().distanceTo(nearbyEntity.position());
                        if (distance <= EXPIRE_BURST_RADIUS * 0.5) {
                            float damageMultiplier = 1.0f - (float)(distance / (EXPIRE_BURST_RADIUS * 0.5)) * 0.5f;
                            float finalDamage = (EXPIRE_BURST_DAMAGE * 0.5f) * damageMultiplier;

                            if (owner != null) {
                                nearbyEntity.hurt(owner.damageSources().playerAttack(owner), finalDamage);
                            } else {
                                nearbyEntity.hurt(serverLevel.damageSources().magic(), finalDamage);
                            }
                        }
                    }
                }
            }
        }

        // Clean up visuals
        activeChainParticles.remove(ownerId);
        if (effect.anchoredEnemyId != null) {
            activeHalos.remove(effect.anchoredEnemyId);
        }
        for (UUID tetheredId : effect.tetheredEnemyIds) {
            activeHalos.remove(tetheredId);
        }
    }

    // ====== Helpers ======
    private static boolean isRangedDamage(DamageSource source) {
        // Check for projectile or spell damage
        return source.isIndirect() ||
                source instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource;
    }

    private static LivingEntity getEntity(ServerLevel level, UUID id) {
        if (id == null) return null;
        Entity entity = level.getEntity(id);
        return entity instanceof LivingEntity ? (LivingEntity) entity : null;
    }

    // ====== Query Methods (for UI/debugging) ======
    public static boolean isActive(Player player) {
        AbilityState state = activeAbilities.get(player.getUUID());
        return state != null && state.projectilesRemaining > 0;
    }

    public static int getProjectilesRemaining(Player player) {
        AbilityState state = activeAbilities.get(player.getUUID());
        return state != null ? state.projectilesRemaining : 0;
    }

    public static boolean hasActiveEffect(Player player) {
        return activeEffects.containsKey(player.getUUID());
    }
}