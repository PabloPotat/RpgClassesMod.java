package net.pablo.rpgclasses.skills;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.RpgClassesMod;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VermillionLacerationManager {

    private static final double AURA_RADIUS = 5.0;
    private static final int DURATION_TICKS = 120;
    private static final double HP_PERCENTAGE_ON_CAST = 0.3;
    private static final double AURA_DAMAGE = 2.0;
    private static final double LIFESTEAL_PERCENT = 0.25;
    private static final int DAMAGE_INTERVAL = 10;
    private static final int SLASH_INTERVAL = 6;
    private static final int SLASH_COUNT = 5; // Increased for better coverage

    // Reduced curve height for less hook-like appearance
    private static final double CURVE_HEIGHT = 0.5;
    // Increased arc angle for wider slashes
    private static final double ARC_ANGLE = Math.PI / 2; // 90 degrees

    private static final Map<UUID, PlayerAuraData> activePlayers = new ConcurrentHashMap<>();
    private static final List<ActiveSlash> activeSlashes = new ArrayList<>();

    public static void cast(Player player) {
        if (player == null || player.level().isClientSide()) return;

        // ✅ Cooldown check
        if (VermillionCooldown.isOnCooldown(player)) {
            return; // prevent casting if still cooling down
        }

        float targetHp = Math.max(1.0f, (float) (player.getMaxHealth() * HP_PERCENTAGE_ON_CAST));
        player.setHealth(targetHp);

        Vec3 castPosition = player.position();
        activePlayers.put(player.getUUID(), new PlayerAuraData(DURATION_TICKS, castPosition, player.getYRot()));

        // Create initial slash burst
        if (!player.level().isClientSide()) {
            createAnimeSlashBurst(castPosition, player.level());
        }

        // ✅ Apply cooldown after successful cast
        VermillionCooldown.setCooldown(player);
    }

    private static void createAnimeSlashBurst(Vec3 center, Level level) {
        if (!level.isClientSide()) {
            // Create arc-style slashes distributed evenly around the player
            for (int i = 0; i < SLASH_COUNT; i++) {
                // Random horizontal angle around the full 360 degrees
                double horizontalAngle = Math.random() * 2 * Math.PI;

                // Random vertical angle with more emphasis on horizontal slashes
                double verticalAngle = (Math.random() - 0.5) * Math.PI / 4;

                // Random radius within the aura
                double radius = 2.0 + Math.random() * (AURA_RADIUS - 2.0);

                // Calculate start and end points for an arc slash
                // Create arc by varying the angle slightly for start and end points
                double startArcAngle = horizontalAngle - ARC_ANGLE / 2;
                double endArcAngle = horizontalAngle + ARC_ANGLE / 2;

                // Keep vertical angle similar for a horizontal arc
                Vec3 startPoint = calculatePointOnSphere(center, startArcAngle, verticalAngle, radius);
                Vec3 endPoint = calculatePointOnSphere(center, endArcAngle, verticalAngle, radius);

                // Convert to spherical coordinates
                Vec3 relativeStart = startPoint.subtract(center);
                double startHA = Math.atan2(relativeStart.z, relativeStart.x);
                double startVA = Math.asin(relativeStart.y / relativeStart.length());

                Vec3 relativeEnd = endPoint.subtract(center);
                double endHA = Math.atan2(relativeEnd.z, relativeEnd.x);
                double endVA = Math.asin(relativeEnd.y / relativeEnd.length());

                // Faster slashes
                int duration = 8 + (int)(Math.random() * 6);
                boolean reverse = Math.random() > 0.5;
                boolean curved = true; // Always curved for arc effect

                activeSlashes.add(new ActiveSlash(
                        center,
                        startHA, startVA, radius,
                        endHA, endVA, radius,
                        duration,
                        reverse,
                        curved,
                        level
                ));
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // Update all active slashes
        Iterator<ActiveSlash> slashIterator = activeSlashes.iterator();
        while (slashIterator.hasNext()) {
            ActiveSlash slash = slashIterator.next();
            slash.update();
            if (slash.isComplete()) {
                slashIterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        UUID uuid = player.getUUID();
        PlayerAuraData auraData = activePlayers.get(uuid);
        if (auraData == null) return;

        // Server-side logic only
        if (!player.level().isClientSide()) {
            // Dome stays at original cast position

            // Spawn spherical dome particles at the original cast position
            spawnSphericalDomeParticles(auraData.castPosition, player.level());

            auraData.damageCounter++;
            if (auraData.damageCounter >= DAMAGE_INTERVAL) {
                applyAuraDamage(player, auraData.castPosition);
                auraData.damageCounter = 0;
            }

            // Spawn new slash bursts more frequently
            if (player.tickCount % SLASH_INTERVAL == 0) {
                createAnimeSlashBurst(auraData.castPosition, player.level());
            }
        }

        // Decrease duration
        auraData.ticksLeft--;
        if (auraData.ticksLeft <= 0) {
            activePlayers.remove(uuid);
        }
    }

    private static void applyAuraDamage(Player player, Vec3 center) {
        AABB auraBox = new AABB(
                center.x - AURA_RADIUS, center.y - AURA_RADIUS, center.z - AURA_RADIUS,
                center.x + AURA_RADIUS, center.y + AURA_RADIUS, center.z + AURA_RADIUS
        );

        float totalHeal = 0f;
        boolean damagedAny = false;

        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, auraBox)) {
            if (target == player || !target.isAlive() || target.isInvulnerable()) continue;
            if (target instanceof Player targetPlayer && !player.canHarmPlayer(targetPlayer)) continue;

            // Check if target is actually inside the spherical dome
            double distanceSq = target.distanceToSqr(center.x, center.y, center.z);
            if (distanceSq > AURA_RADIUS * AURA_RADIUS) continue;

            // Create custom damage source that prevents knockback
            DamageSource noKnockbackDamage = new DamageSource(player.damageSources().playerAttack(player).typeHolder()) {
                @Override
                public boolean scalesWithDifficulty() {
                    return false;
                }
            };

            boolean damaged = target.hurt(noKnockbackDamage, (float) AURA_DAMAGE);
            if (damaged) {
                totalHeal += (float) (AURA_DAMAGE * LIFESTEAL_PERCENT);
                damagedAny = true;

                // Add blood effect on hit
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            new DustParticleOptions(new Vector3f(0.8f, 0.1f, 0.1f), 1.0f),
                            target.getX(), target.getY() + target.getBbHeight()/2, target.getZ(),
                            3, 0.3, 0.3, 0.3, 0.1
                    );
                }
            }
        }

        if (damagedAny && totalHeal > 0f) {
            player.heal(totalHeal);
        }
    }

    private static void spawnSphericalDomeParticles(Vec3 center, Level level) {
        // Create a spherical dome of red particles
        if (level.getGameTime() % 5 == 0) {
            int particlesPerLayer = 8;
            int layers = 4;

            for (int layer = 0; layer < layers; layer++) {
                double heightRatio = (double) layer / (layers - 1);
                double verticalAngle = (heightRatio * Math.PI / 2);
                double radius = AURA_RADIUS * Math.sin(verticalAngle);
                double y = center.y + AURA_RADIUS * Math.cos(verticalAngle);

                for (int i = 0; i < particlesPerLayer; i++) {
                    double angle = 2 * Math.PI * i / particlesPerLayer;
                    double x = center.x + radius * Math.cos(angle);
                    double z = center.z + radius * Math.sin(angle);

                    if (level instanceof ServerLevel serverLevel) {
                        // Red particles for the dome
                        serverLevel.sendParticles(
                                new DustParticleOptions(new Vector3f(0.8f, 0.1f, 0.1f), 1.0f),
                                x, y, z,
                                1, 0, 0, 0, 0
                        );
                    }
                }
            }
        }
    }

    // Client-side particle spawning for custom anime slashes
    public static void spawnAnimeSlashParticles(Vec3 center, double progress,
                                                double startHA, double startVA, double startR,
                                                double endHA, double endVA, double endR,
                                                boolean reverse, boolean curved) {

        // Calculate position along the curve
        Vec3 position;
        if (curved) {
            position = calculateCurvedPoint(center, progress,
                    startHA, startVA, startR,
                    endHA, endVA, endR,
                    reverse);
        } else {
            Vec3 startPoint = calculatePointOnSphere(center, startHA, startVA, startR);
            Vec3 endPoint = calculatePointOnSphere(center, endHA, endVA, endR);
            double t = reverse ? (1 - progress) : progress;
            position = startPoint.add(endPoint.subtract(startPoint).scale(t));
        }

        // Calculate tangent direction for arc-facing particles
        Vec3 startPoint = calculatePointOnSphere(center, startHA, startVA, startR);
        Vec3 endPoint = calculatePointOnSphere(center, endHA, endVA, endR);
        Vec3 tangent = endPoint.subtract(startPoint).normalize();

        // Create custom slash effect with multiple particles
        createCustomSlashEffect(position, tangent, progress);
    }

    // Calculate a point along a curved path
    private static Vec3 calculateCurvedPoint(Vec3 center, double progress,
                                             double startHA, double startVA, double startR,
                                             double endHA, double endVA, double endR,
                                             boolean reverse) {

        Vec3 startPoint = calculatePointOnSphere(center, startHA, startVA, startR);
        Vec3 endPoint = calculatePointOnSphere(center, endHA, endVA, endR);

        // Calculate control point for the curve (midpoint pushed outward)
        Vec3 midpoint = startPoint.add(endPoint.subtract(startPoint).scale(0.5));
        Vec3 centerToMid = midpoint.subtract(center).normalize();

        // Push control point outward for arc effect (reduced curve height)
        Vec3 controlPoint = midpoint.add(centerToMid.scale(CURVE_HEIGHT));

        double t = reverse ? (1 - progress) : progress;

        // Quadratic Bezier curve calculation
        double omt = 1 - t;
        Vec3 point = startPoint.scale(omt * omt)
                .add(controlPoint.scale(2 * omt * t))
                .add(endPoint.scale(t * t));

        return point;
    }

    // Create a custom slash effect without using the sweep attack
    private static void createCustomSlashEffect(Vec3 position, Vec3 direction, double progress) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        // Calculate perpendicular vector for slash width
        Vec3 perpendicular = calculatePerpendicular(direction);

        // Main slash line - WHITE particles (more intense)
        Vec3 linePos = position.add(perpendicular.scale((Math.random() - 0.5) * 0.5));

        level.addParticle(
                new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 2.0f),
                linePos.x, linePos.y, linePos.z,
                0, 0, 0
        );

        // Additional particles along the slash for more intensity
        for (int i = 0; i < 2; i++) {
            Vec3 offsetPos = position.add(perpendicular.scale((Math.random() - 0.5) * 0.8));
            level.addParticle(
                    new DustParticleOptions(new Vector3f(1.0f, 0.8f, 0.8f), 1.5f),
                    offsetPos.x, offsetPos.y, offsetPos.z,
                    0, 0, 0
            );
        }

        // Glowing trail particles - RED (more intense)
        if (progress > 0.2) {
            Vec3 trailPos = position.subtract(direction.scale(0.3));

            for (int i = 0; i < 3; i++) {
                Vec3 trailOffset = trailPos.add(
                        (Math.random() - 0.5) * 0.4,
                        (Math.random() - 0.5) * 0.4,
                        (Math.random() - 0.5) * 0.4
                );

                level.addParticle(
                        new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.1f), 1.2f),
                        trailOffset.x, trailOffset.y, trailOffset.z,
                        0, 0, 0
                );
            }
        }

        // Impact particles at the leading edge (more intense)
        if (progress > 0.8) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(
                        ParticleTypes.FLAME,
                        position.x + (Math.random() - 0.5) * 0.5,
                        position.y + (Math.random() - 0.5) * 0.5,
                        position.z + (Math.random() - 0.5) * 0.5,
                        (Math.random() - 0.5) * 0.2,
                        (Math.random() - 0.5) * 0.2,
                        (Math.random() - 0.5) * 0.2
                );
            }
        }
    }

    // Helper method to calculate a point on a sphere
    private static Vec3 calculatePointOnSphere(Vec3 center, double horizontalAngle, double verticalAngle, double radius) {
        double x = center.x + radius * Math.cos(horizontalAngle) * Math.cos(verticalAngle);
        double z = center.z + radius * Math.sin(horizontalAngle) * Math.cos(verticalAngle);
        double y = center.y + radius * Math.sin(verticalAngle);
        return new Vec3(x, y, z);
    }

    // Helper method to calculate a perpendicular vector
    private static Vec3 calculatePerpendicular(Vec3 v) {
        // Find a vector not parallel to v
        Vec3 notParallel = Math.abs(v.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return v.cross(notParallel).normalize();
    }

    private static class PlayerAuraData {
        public int ticksLeft;
        public int damageCounter;
        public final Vec3 castPosition;
        public final float playerYaw;

        public PlayerAuraData(int ticksLeft, Vec3 castPosition, float playerYaw) {
            this.ticksLeft = ticksLeft;
            this.damageCounter = 0;
            this.castPosition = castPosition;
            this.playerYaw = playerYaw;
        }
    }

    private static class ActiveSlash {
        private final Vec3 center;
        private final double startHA, startVA, startR;
        private final double endHA, endVA, endR;
        private int currentTick;
        private final int maxTicks;
        private final boolean reverse;
        private final boolean curved;
        private final Level level;

        public ActiveSlash(Vec3 center,
                           double startHA, double startVA, double startR,
                           double endHA, double endVA, double endR,
                           int maxTicks, boolean reverse, boolean curved, Level level) {
            this.center = center;
            this.startHA = startHA;
            this.startVA = startVA;
            this.startR = startR;
            this.endHA = endHA;
            this.endVA = endVA;
            this.endR = endR;
            this.currentTick = 0;
            this.maxTicks = maxTicks;
            this.reverse = reverse;
            this.curved = curved;
            this.level = level;
        }

        public void update() {
            currentTick++;
            double progress = currentTick / (double) maxTicks;

            if (level instanceof ServerLevel serverLevel) {
                Vec3 position;
                if (curved) {
                    position = calculateCurvedPoint(center, progress,
                            startHA, startVA, startR,
                            endHA, endVA, endR,
                            reverse);
                } else {
                    Vec3 startPoint = calculatePointOnSphere(center, startHA, startVA, startR);
                    Vec3 endPoint = calculatePointOnSphere(center, endHA, endVA, endR);
                    double t = reverse ? (1 - progress) : progress;
                    position = startPoint.add(endPoint.subtract(startPoint).scale(t));
                }

                // Calculate tangent direction for arc-facing particles
                Vec3 startPoint = calculatePointOnSphere(center, startHA, startVA, startR);
                Vec3 endPoint = calculatePointOnSphere(center, endHA, endVA, endR);
                Vec3 tangent = endPoint.subtract(startPoint).normalize();

                createServerSlashEffect(serverLevel, position, tangent, progress);
            }
        }

        private void createServerSlashEffect(ServerLevel serverLevel, Vec3 position, Vec3 direction, double progress) {
            Vec3 perpendicular = calculatePerpendicular(direction);

            // Main slash line
            Vec3 linePos = position.add(perpendicular.scale((Math.random() - 0.5) * 0.5));

            serverLevel.sendParticles(
                    new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 2.0f),
                    linePos.x, linePos.y, linePos.z,
                    1, 0, 0, 0, 0
            );

            // Additional particles
            for (int i = 0; i < 2; i++) {
                Vec3 offsetPos = position.add(perpendicular.scale((Math.random() - 0.5) * 0.8));
                serverLevel.sendParticles(
                        new DustParticleOptions(new Vector3f(1.0f, 0.8f, 0.8f), 1.5f),
                        offsetPos.x, offsetPos.y, offsetPos.z,
                        1, 0, 0, 0, 0
                );
            }

            // Trail particles
            if (progress > 0.2) {
                Vec3 trailPos = position.subtract(direction.scale(0.3));

                for (int i = 0; i < 3; i++) {
                    Vec3 trailOffset = trailPos.add(
                            (Math.random() - 0.5) * 0.4,
                            (Math.random() - 0.5) * 0.4,
                            (Math.random() - 0.5) * 0.4
                    );

                    serverLevel.sendParticles(
                            new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.1f), 1.2f),
                            trailOffset.x, trailOffset.y, trailOffset.z,
                            1, 0, 0, 0, 0
                    );
                }
            }

            // Impact particles
            if (progress > 0.8) {
                for (int i = 0; i < 3; i++) {
                    serverLevel.sendParticles(
                            ParticleTypes.FLAME,
                            position.x + (Math.random() - 0.5) * 0.5,
                            position.y + (Math.random() - 0.5) * 0.5,
                            position.z + (Math.random() - 0.5) * 0.5,
                            1,
                            (Math.random() - 0.5) * 0.2,
                            (Math.random() - 0.5) * 0.2,
                            (Math.random() - 0.5) * 0.2,
                            0
                    );
                }
            }
        }

        public boolean isComplete() {
            return currentTick >= maxTicks;
        }
    }

    public static void clearPlayerAura(UUID playerId) {
        activePlayers.remove(playerId);
    }

    public static void clearAll() {
        activePlayers.clear();
        activeSlashes.clear();
    }

    public static boolean hasActiveAura(Player player) {
        return player != null && activePlayers.containsKey(player.getUUID());
    }
}