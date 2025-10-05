package net.pablo.rpgclasses.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;

public class PhantomEffect extends MobEffect {

    public PhantomEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x5E5E5E); // gray color for phantom
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        // 1️⃣ Apply invisibility ONCE at the start, not every tick
        if (!entity.getPersistentData().getBoolean("PhantomInvisApplied")) {
            // Apply vanilla invisibility effect with full phantom duration (no blinking)
            net.minecraft.world.effect.MobEffectInstance invisEffect =
                    new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.INVISIBILITY,
                            140, // Slightly longer than phantom effect to ensure it covers full duration
                            0,
                            false,  // ambient = false (shows in GUI but armor will be hidden by mixin)
                            false  // showParticles = false (no vanilla particles)
                    );
            entity.addEffect(invisEffect);

            // Set invisible flag
            entity.setInvisible(true);

            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.setInvisible(true);
                entity.getPersistentData().putBoolean("PhantomInvisible", true);

                // Mark that armor should be hidden (used by mixin)
                serverPlayer.getPersistentData().putBoolean("PhantomHideArmor", true);

                // Force update entity data to all clients
                serverPlayer.getServer().getPlayerList().broadcastAll(
                        new ClientboundSetEntityDataPacket(serverPlayer.getId(), serverPlayer.getEntityData().packDirty())
                );
            }
        }

        // 2️⃣ Prevent mobs from targeting the player
        clearMobTargets(entity);

        // 3️⃣ Continuous particle trail for movement (fairness - shows position)
        if (entity.getDeltaMovement().length() > 0.05) {
            spawnMovementTrail(level, entity);
        }

        // 4️⃣ Spawn ghost silhouette every 40 ticks (~2 seconds)
        if (entity.tickCount % 40 == 0) {
            spawnGhostSilhouette(level, entity);
        }

        // 5️⃣ Ambient phantom particles
        if (entity.tickCount % 10 == 0) {
            spawnAmbientParticles(level, entity);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // run every tick
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity,
                                         net.minecraft.world.entity.ai.attributes.AttributeMap attributes,
                                         int amplifier) {
        super.removeAttributeModifiers(entity, attributes, amplifier);
        if (!entity.level().isClientSide()) {
            // Remove both invisibility methods
            entity.setInvisible(false);
            entity.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            entity.getPersistentData().remove("PhantomInvisible");
            entity.getPersistentData().remove("PhantomInvisApplied"); // Reset for next use
            entity.getPersistentData().remove("PhantomHideArmor"); // Remove armor hiding flag

            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.setInvisible(false);
                // Force sync visibility return to all clients
                serverPlayer.getServer().getPlayerList().broadcastAll(
                        new ClientboundSetEntityDataPacket(serverPlayer.getId(), serverPlayer.getEntityData().packDirty())
                );
            }

            // Final dramatic particle burst
            spawnFinalBurst((ServerLevel) entity.level(), entity);

            System.out.println("[PhantomEffect] Removed phantom effect and invisibility from " +
                    (entity instanceof ServerPlayer ? ((ServerPlayer) entity).getName().getString() : entity.getDisplayName().getString()));
        }
    }

    // === PARTICLE EFFECT METHODS ===

    private void spawnMovementTrail(ServerLevel level, LivingEntity entity) {
        Vec3 pos = entity.position();
        Vec3 motion = entity.getDeltaMovement();

        // Smoke trail behind the player - shows recent position
        for (int i = 0; i < 3; i++) {
            double offsetMult = (i + 1) * 0.3;
            level.sendParticles(ParticleTypes.SMOKE,
                    pos.x() - motion.x() * offsetMult,
                    pos.y() + entity.getEyeHeight() * 0.5,
                    pos.z() - motion.z() * offsetMult,
                    1, 0.05, 0.05, 0.05, 0.01
            );
        }

        // Faint soul particles for mystical effect
        level.sendParticles(ParticleTypes.SOUL,
                pos.x() + (level.random.nextDouble() - 0.5) * 0.5,
                pos.y() + level.random.nextDouble() * entity.getBbHeight(),
                pos.z() + (level.random.nextDouble() - 0.5) * 0.5,
                1, 0.02, 0.02, 0.02, 0.005
        );
    }

    private void spawnGhostSilhouette(ServerLevel level, LivingEntity entity) {
        Vec3 pos = entity.position();
        float width = entity.getBbWidth();
        float height = entity.getBbHeight();

        // Create a ghostly outline/silhouette
        int particleCount = 25;
        for (int i = 0; i < particleCount; i++) {
            // Create particles around the entity's silhouette
            double angle = (2 * Math.PI * i) / particleCount;
            double x = pos.x() + Math.cos(angle) * width * 0.6;
            double z = pos.z() + Math.sin(angle) * width * 0.6;
            double y = pos.y() + level.random.nextDouble() * height;

            // Large smoke particles for the silhouette
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    x, y, z,
                    1, 0.02, 0.05, 0.02, 0.01
            );
        }

        // Add some soul particles for extra ghostly effect
        for (int i = 0; i < 8; i++) {
            double x = pos.x() + (level.random.nextDouble() - 0.5) * width;
            double y = pos.y() + level.random.nextDouble() * height;
            double z = pos.z() + (level.random.nextDouble() - 0.5) * width;

            level.sendParticles(ParticleTypes.SOUL,
                    x, y, z,
                    1, 0.03, 0.03, 0.03, 0.01
            );
        }

        // Add ash particles for additional ghostly effect
        for (int i = 0; i < 5; i++) {
            double x = pos.x() + (level.random.nextDouble() - 0.5) * width * 1.2;
            double y = pos.y() + level.random.nextDouble() * height;
            double z = pos.z() + (level.random.nextDouble() - 0.5) * width * 1.2;

            level.sendParticles(ParticleTypes.WHITE_ASH,
                    x, y, z,
                    1, 0.02, 0.05, 0.02, 0.01
            );
        }

        // Soft sound effect for the silhouette appearance
        level.playSound(null, entity.blockPosition(), SoundEvents.SOUL_ESCAPE,
                SoundSource.PLAYERS, 0.2f, 1.2f);
    }

    private void spawnAmbientParticles(ServerLevel level, LivingEntity entity) {
        Vec3 pos = entity.position();

        // Subtle ambient particles around the phantom
        for (int i = 0; i < 2; i++) {
            double x = pos.x() + (level.random.nextDouble() - 0.5) * entity.getBbWidth() * 1.5;
            double y = pos.y() + level.random.nextDouble() * entity.getBbHeight();
            double z = pos.z() + (level.random.nextDouble() - 0.5) * entity.getBbWidth() * 1.5;

            // Mix of different particles for mystical effect
            if (level.random.nextFloat() < 0.7f) {
                level.sendParticles(ParticleTypes.SOUL,
                        x, y, z, 1, 0.01, 0.01, 0.01, 0.001);
            } else {
                level.sendParticles(ParticleTypes.SMOKE,
                        x, y, z, 1, 0.01, 0.01, 0.01, 0.001);
            }
        }
    }

    private void spawnFinalBurst(ServerLevel level, LivingEntity entity) {
        Vec3 pos = entity.position();

        // Dramatic particle burst when phantom effect ends
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                pos.x(), pos.y() + entity.getEyeHeight(), pos.z(),
                15, 0.3, 0.3, 0.3, 0.1
        );

        level.sendParticles(ParticleTypes.SOUL,
                pos.x(), pos.y() + entity.getEyeHeight(), pos.z(),
                10, 0.2, 0.2, 0.2, 0.05
        );

        // Sound effect for dramatic end
        level.playSound(null, entity.blockPosition(), SoundEvents.SOUL_ESCAPE,
                SoundSource.PLAYERS, 0.8f, 0.8f);
    }

    // === MOB TARGET CLEARING ===

    private void clearMobTargets(LivingEntity entity) {
        double radius = 25.0; // Larger radius for better coverage

        // Clear targets from all nearby mobs
        for (Mob mob : entity.level().getEntitiesOfClass(Mob.class,
                entity.getBoundingBox().inflate(radius))) {

            // Clear current target if it's the phantom player
            if (mob.getTarget() == entity) {
                mob.setTarget(null);
            }

            // Prevent mob from detecting the phantom player
            // Force mob to "forget" about the player for a short time
            if (mob.getSensing() != null) {
                // Reset mob's sensing to prevent re-targeting
                mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_ATTACKABLE);
                mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
            }

            // Make them look confused/distracted (NO PARTICLES ON ENEMIES)
            if (mob.level().random.nextFloat() < 0.4f) {
                // Look in a random direction
                Vec3 randomPos = mob.position().add(
                        (mob.level().random.nextDouble() - 0.5) * 10,
                        0,
                        (mob.level().random.nextDouble() - 0.5) * 10
                );
                mob.getLookControl().setLookAt(randomPos.x(), randomPos.y(), randomPos.z());
            }
        }
    }
}