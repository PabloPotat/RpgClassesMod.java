package net.pablo.rpgclasses.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class PhantomEffect extends MobEffect {

    public PhantomEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x5E5E5E); // gray color
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        // 1️⃣ Maintain invisibility
        if (!entity.isInvisible()) {
            entity.setInvisible(true);
        }

        // Sync invisibility for server players
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.setInvisible(true);
        }

        // 2️⃣ Prevent mobs from targeting the player
        clearMobTargets(entity);

        // 3️⃣ Smoke trail for movement
        if (entity.isSprinting() || entity.getDeltaMovement().length() > 0.1) {
            spawnSmokeTrail(level, entity);
        }

        // 4️⃣ Smoke silhouette every 40 ticks (~2 seconds)
        if (entity.tickCount % 40 == 0) {
            spawnSmokeSilhouette(level, entity);
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
            // Remove invisibility
            entity.setInvisible(false);
            entity.getPersistentData().remove("PhantomInvisible");

            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.setInvisible(false);
            }

            // Optional: final smoke burst
            spawnSmokeSilhouette((ServerLevel) entity.level(), entity);
        }
    }

    // === PARTICLE EFFECT METHODS ===

    private void spawnSmokeTrail(ServerLevel level, LivingEntity entity) {
        Vec3 pos = entity.position();
        Vec3 motion = entity.getDeltaMovement();

        level.sendParticles(ParticleTypes.SMOKE,
                pos.x() - motion.x() * 0.5,
                pos.y() + entity.getEyeHeight() * 0.5,
                pos.z() - motion.z() * 0.5,
                2, 0.1, 0.1, 0.1, 0.02
        );
    }

    private void spawnSmokeSilhouette(ServerLevel level, LivingEntity entity) {
        Vec3 pos = entity.position();
        float width = entity.getBbWidth();
        float height = entity.getBbHeight();

        for (int i = 0; i < 20; i++) {
            double x = pos.x() + (level.random.nextDouble() - 0.5) * width;
            double y = pos.y() + level.random.nextDouble() * height;
            double z = pos.z() + (level.random.nextDouble() - 0.5) * width;

            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    x, y, z,
                    1, 0.05, 0.05, 0.05, 0.02
            );
        }

        // Soul particles for ghostly effect
        for (int i = 0; i < 5; i++) {
            double x = pos.x() + (level.random.nextDouble() - 0.5) * width;
            double y = pos.y() + level.random.nextDouble() * height;
            double z = pos.z() + (level.random.nextDouble() - 0.5) * width;

            level.sendParticles(ParticleTypes.SOUL,
                    x, y, z,
                    1, 0.02, 0.02, 0.02, 0.01
            );
        }

        // Optional: sound effect
        level.playSound(null, entity.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.3f, 1.0f);
    }

    // === MOB TARGET CLEARING ===

    private void clearMobTargets(LivingEntity entity) {
        double radius = 16.0;
        for (LivingEntity mob : entity.level().getEntitiesOfClass(Mob.class,
                entity.getBoundingBox().inflate(radius),
                e -> e.getTarget() == entity)) {

            // Cast to Mob and use setTarget method
            if (mob instanceof Mob mobEntity) {
                mobEntity.setTarget(null);

                // Optional: Make mobs look confused for a moment
                if (mob.level().random.nextFloat() < 0.3f) {
                    Vec3 randomPos = mob.position().add(
                            (mob.level().random.nextDouble() - 0.5) * 10,
                            0,
                            (mob.level().random.nextDouble() - 0.5) * 10
                    );
                    mobEntity.getLookControl().setLookAt(randomPos.x(), randomPos.y(), randomPos.z());
                }
            }
        }
    }
}
