package net.pablo.rpgclasses.skills;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.network.ClientboundSyncMana;
import io.redspace.ironsspellbooks.setup.Messages;
import io.redspace.ironsspellbooks.api.events.ChangeManaEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber
public class ArcaneBrandManager {

    private static final Map<UUID, Map<UUID, Long>> playerMarks = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>> playerHaloTargets = new HashMap<>();
    private static final Map<UUID, BeamTask> activeBeams = new HashMap<>();

    private static final long MARK_DURATION_MILLIS = 12_000; // 10s
    private static final float SPELL_DAMAGE_MULTIPLIER = 1.30f;
    private static final float MAX_BONUS_DAMAGE = 6f;

    private static final int BEAM_TICKS = 10;
    private static final double BEAM_STEP = 0.5;

    // --- Tick-based mana tracking for guaranteed next-tick amplification
    private static final Map<UUID, Long> recentManaUseTick = new HashMap<>();
    private static final long SPELL_CAST_TICK_WINDOW = 50; // 2 ticks buffer

    /** Cast Arcane Brand around the caster */
    public static void cast(Player caster) {
        if (ArcaneBrandCooldownManager.isOnCooldown(caster)) return;
        if (!(caster.level() instanceof ServerLevel level)) return;

        var targets = level.getEntitiesOfClass(LivingEntity.class,
                caster.getBoundingBox().inflate(8),
                e -> e != caster
        ).stream().limit(5).toList();

        if (targets.isEmpty()) return;

        UUID casterId = caster.getUUID();
        playerMarks.putIfAbsent(casterId, new HashMap<>());
        playerHaloTargets.putIfAbsent(casterId, new HashMap<>());

        for (LivingEntity target : targets) {
            activeBeams.put(UUID.randomUUID(), new BeamTask(level, caster, target, casterId));
        }
        ArcaneBrandCooldownManager.setCooldown(caster);
    }

    /** Track mana spending by tick */
    @SubscribeEvent
    public static void onManaChange(ChangeManaEvent event) {
        if (event.getNewMana() < event.getOldMana()) {
            Player player = event.getEntity();
            long currentTick = player.level().getGameTime();
            recentManaUseTick.put(player.getUUID(), currentTick);
        }
    }

    /** Progressive beam task holder */
    private static class BeamTask {
        final ServerLevel level;
        final LivingEntity caster;
        final LivingEntity target;
        final UUID casterId;
        final Vec3 start;
        final Vec3 end;
        final Vec3 direction;
        final double distance;
        int tick = 0;

        BeamTask(ServerLevel level, LivingEntity caster, LivingEntity target, UUID casterId) {
            this.level = level;
            this.caster = caster;
            this.target = target;
            this.casterId = casterId;
            this.start = caster.position().add(0, caster.getEyeHeight() * 0.8, 0);
            this.end = target.position().add(0, target.getBbHeight() * 0.5, 0);
            this.direction = end.subtract(start).normalize();
            this.distance = start.distanceTo(end);
        }

        boolean tick() {
            if (!caster.isAlive() || !target.isAlive()) return true;
            tick++;

            double progress = (double) tick / BEAM_TICKS;
            double currentDistance = progress * distance;

            for (double d = 0; d < currentDistance; d += BEAM_STEP) {
                Vec3 pos = start.add(direction.scale(d));
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0.01);
            }

            if (tick >= BEAM_TICKS) {
                applyMark(casterId, target);
                return true;
            }
            return false;
        }
    }

    /** Apply mark and halo */
    private static void applyMark(UUID casterId, LivingEntity target) {
        long expiry = System.currentTimeMillis() + MARK_DURATION_MILLIS;
        playerMarks.get(casterId).put(target.getUUID(), expiry);
        playerHaloTargets.get(casterId).put(target.getUUID(), expiry);
    }

    /** Cleanup + tick beams + halo rotation */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player.level() instanceof ServerLevel level)) return;
        if (event.phase != TickEvent.Phase.END) return;

        // Tick beams
        Iterator<Map.Entry<UUID, BeamTask>> it = activeBeams.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().tick()) it.remove();
        }

        long now = System.currentTimeMillis();
        long currentTick = event.player.level().getGameTime();

        // Rotate halos per caster
        for (Map.Entry<UUID, Map<UUID, Long>> entry : playerHaloTargets.entrySet()) {
            Map<UUID, Long> haloMap = entry.getValue();
            Iterator<Map.Entry<UUID, Long>> haloIt = haloMap.entrySet().iterator();
            while (haloIt.hasNext()) {
                Map.Entry<UUID, Long> haloEntry = haloIt.next();
                if (haloEntry.getValue() < now) {
                    haloIt.remove();
                    continue;
                }
                LivingEntity target = level.getEntity(haloEntry.getKey()) instanceof LivingEntity l ? l : null;
                if (target == null || !target.isAlive()) {
                    haloIt.remove();
                    continue;
                }

                double radius = target.getBbWidth() * 0.8;
                double yOffset = target.getBbHeight() * 0.6;
                double angle = (level.getGameTime() % 360) * Math.PI / 30;

                double x1 = target.getX() + radius * Math.cos(angle);
                double z1 = target.getZ() + radius * Math.sin(angle);

                level.sendParticles(ParticleTypes.GLOW, x1, target.getY() + yOffset, z1, 1, 0, 0, 0, 0.01);
            }
        }

        // Clean up old mana tick entries
        recentManaUseTick.entrySet().removeIf(entry -> currentTick - entry.getValue() > SPELL_CAST_TICK_WINDOW);
    }

    /** Check if target is marked by caster */
    public static boolean isMarked(Player caster, LivingEntity target) {
        Map<UUID, Long> marks = playerMarks.get(caster.getUUID());
        if (marks == null) return false;

        Long expiry = marks.get(target.getUUID());
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /** Modify damage for hybrid amplification */
    public static float modifyDamage(LivingEntity target, Player caster, float baseDamage, Object damageSource) {
        if (caster == null || target == null) return baseDamage;

        boolean isAmplifiable = false;
        long currentTick = caster.level().getGameTime();

        // Case 1: direct spell damage
        if (damageSource instanceof SpellDamageSource) {
            isAmplifiable = true;
        }
        // Case 2: recent mana loss triggers damage (non-spell sources)
        else if (damageSource instanceof net.minecraft.world.damagesource.DamageSource ds) {
            Long lastManaTick = recentManaUseTick.get(caster.getUUID());
            if (lastManaTick != null && currentTick - lastManaTick <= SPELL_CAST_TICK_WINDOW) {
                isAmplifiable = true;
            }
        }

        if (isAmplifiable && isMarked(caster, target)) {
            float amplified = baseDamage * SPELL_DAMAGE_MULTIPLIER;
            // Remove mark after empowered damage
            playerMarks.get(caster.getUUID()).remove(target.getUUID());
            playerHaloTargets.get(caster.getUUID()).remove(target.getUUID());
            return Math.min(amplified, baseDamage + MAX_BONUS_DAMAGE);
        }

        return baseDamage;
    }

    /** Event: apply amplification */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target == null) return;

        Object source = event.getSource();
        Player caster = null;

        if (source instanceof SpellDamageSource sds && sds.getEntity() instanceof Player p) {
            caster = p;
        }
// Use proper cast and check for DamageSource entity
        else if (source instanceof net.minecraft.world.damagesource.DamageSource ds && ds.getEntity() instanceof Player p) {
            caster = p;
        }


        event.setAmount(modifyDamage(target, caster, event.getAmount(), source));
    }

    /** Event: mana restore on kill (only if mark was still active) */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Object source = event.getSource();
        Player caster = null;

        if (source instanceof SpellDamageSource sds && sds.getEntity() instanceof Player p) {
            caster = p;
        }
// Use proper cast and check for DamageSource entity
        else if (source instanceof net.minecraft.world.damagesource.DamageSource ds && ds.getEntity() instanceof Player p) {
            caster = p;
        }


        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity target = (LivingEntity) event.getEntity();
        if (caster == null) return;
        if (!isMarked(caster, target)) return;

        MagicData pmg = MagicData.getPlayerMagicData(caster);
        if (pmg != null) {
            float manaRestore = 20f;
            pmg.addMana(manaRestore);
            if (caster instanceof ServerPlayer serverPlayer) {
                Messages.sendToPlayer(new ClientboundSyncMana(pmg), serverPlayer);
            }
        }

        // Remove marks & halos
        playerMarks.get(caster.getUUID()).remove(target.getUUID());
        playerHaloTargets.get(caster.getUUID()).remove(target.getUUID());
    }
}
