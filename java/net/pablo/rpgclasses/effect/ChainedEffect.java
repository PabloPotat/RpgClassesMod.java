package net.pablo.rpgclasses.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Chained Effect - Applied by Spectral Chains ability
 *
 * Reduces movement speed by 30% and displays spectral chain particles.
 * Also reduces dash/mobility effectiveness (if applicable).
 */
public class ChainedEffect extends MobEffect {

    private static final double MOVEMENT_SPEED_REDUCTION = -0.3; // -30% movement speed

    public ChainedEffect() {
        super(MobEffectCategory.HARMFUL, 0x00FFFF); // Cyan color

        // Apply movement speed reduction
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                "7f8c5c9e-3d2a-4f1e-9b7a-6e4d3c2b1a0f", // Unique UUID for this modifier
                MOVEMENT_SPEED_REDUCTION,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // Spawn chain particles around the entity
        if (entity.level() instanceof ServerLevel serverLevel) {
            // Particle tick rate: show particles every 10 ticks (0.5s)
            if (entity.tickCount % 10 == 0) {
                spawnChainParticles(serverLevel, entity);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // Apply effect every tick
        return true;
    }

    /**
     * Spawn spectral chain particles around the entity
     */
    private void spawnChainParticles(ServerLevel level, LivingEntity entity) {
        Vec3 pos = entity.position();
        double height = entity.getBbHeight();

        // Ground chains (circling at feet)
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2 * i) / 8;
            double radius = entity.getBbWidth() * 0.7;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;

            // Smoke particles for chain effect
            level.sendParticles(ParticleTypes.SMOKE,
                    x, pos.y + 0.1, z,
                    1, 0.02, 0, 0.02, 0.01);
        }

        // Rising soul wisps (to indicate debuff)
        if (entity.tickCount % 20 == 0) { // Every second
            for (int i = 0; i < 3; i++) {
                double angle = entity.getRandom().nextDouble() * Math.PI * 2;
                double radius = entity.getBbWidth() * 0.5;
                double x = pos.x + Math.cos(angle) * radius;
                double z = pos.z + Math.sin(angle) * radius;

                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        x, pos.y + height * 0.5, z,
                        1, 0, 0.05, 0, 0.02);
            }
        }
    }

    /**
     * Get movement speed multiplier for this effect
     * Can be used by mobility abilities to reduce their effectiveness
     */
    public static double getMovementMultiplier() {
        return 1.0 + MOVEMENT_SPEED_REDUCTION; // Returns 0.7 (70% speed)
    }

    /**
     * Check if entity should have reduced dash/mobility
     * Can be used by dash abilities to reduce distance
     */
    public static boolean shouldReduceMobility(LivingEntity entity) {
        return entity.hasEffect(ModEffects.CHAINED.get()); // Use .get() to retrieve the actual effect
    }

    /**
     * Get dash distance multiplier (for integration with dash abilities)
     */
    public static double getDashMultiplier() {
        return 0.7; // 30% reduction to dash distance
    }
}