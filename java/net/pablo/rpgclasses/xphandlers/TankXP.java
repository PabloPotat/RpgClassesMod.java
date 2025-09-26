package net.pablo.rpgclasses.xphandlers;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.pablo.rpgclasses.RpgClassesMod;
import net.pablo.rpgclasses.capability.PlayerClassProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = RpgClassesMod.MOD_ID)
public class TankXP {

    private static final long AFK_TIMEOUT = 10_000;        // 10 seconds
    private static final int BASE_XP = 1;                  // XP at level 1
    private static final int XP_CAP = 55;                  // Max XP per hit

    private static final Map<UUID, Long> lastAttack = new HashMap<>();
    private static final Map<UUID, Long> lastMove = new HashMap<>();
    private static final Map<UUID, Vec3> lastPos = new HashMap<>();

    @SubscribeEvent
    public static void onHitTaken(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        player.getCapability(PlayerClassProvider.PLAYER_CLASS_CAPABILITY).ifPresent(cap -> {
            boolean isPrimary = cap.getSelectedClass() != null &&
                    "Tank".equalsIgnoreCase(cap.getSelectedClass().getClassName());
            boolean isSecondary = cap.getSecondaryClass() != null &&
                    "Tank".equalsIgnoreCase(cap.getSecondaryClass().getClassName());

            if (!isPrimary && !isSecondary) return;

            UUID id = player.getUUID();
            long now = System.currentTimeMillis();

            // --- AFK prevention: must have moved AND attacked in last 10s ---
            boolean activeMove = lastMove.containsKey(id) && now - lastMove.get(id) < AFK_TIMEOUT;
            boolean activeAttack = lastAttack.containsKey(id) && now - lastAttack.get(id) < AFK_TIMEOUT;
            if (!(activeMove && activeAttack)) return;

            int level = cap.getLevel("tank");
            if (level < 1) level = 1;

            double xp = BASE_XP * Math.pow(1.08, level - 1);
            if (xp > XP_CAP) xp = XP_CAP;

            double damageTaken = event.getAmount();
            if (damageTaken <= 0) return;  // ignore fully mitigated hits

            int xpToAward = (int) Math.round(xp);

            // --- Award XP ---
            if (isPrimary) {
                XPUtils.addXPAndCheckLevel(player, cap, "Tank", xpToAward);
            }
            if (isSecondary) {
                XPUtils.addSecondaryXP(player, cap, "Tank", xpToAward);
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        lastAttack.put(player.getUUID(), System.currentTimeMillis());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide) return;

        UUID id = player.getUUID();
        Vec3 currentPos = player.position();

        if (!lastPos.containsKey(id) || !lastPos.get(id).equals(currentPos)) {
            lastPos.put(id, currentPos);
            lastMove.put(id, System.currentTimeMillis());
        }
    }
}
