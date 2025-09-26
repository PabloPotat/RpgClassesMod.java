package net.pablo.rpgclasses.classes;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class Rogue extends RPGClass {
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("f3b6c8d1-2a22-4d12-a1f2-5e1f6b2d0a77");
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("a7c8d9e2-3b11-4f23-9c33-1d2e45f67890");
    private static final UUID ATTACK_SPEED_MODIFIER_UUID = UUID.fromString("d1f2b3c4-5a67-4e8d-9c12-abcdef123456");

    public Rogue() {
        super("rogue", 18.0, 0.25, 4.0); // Example: lower HP, higher speed and attack
    }

    @Override
    public void applyClassEffect(Player player) {
        addAttributeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_UUID, getMaxHealth() - 20.0);
        addAttributeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID, getMovementSpeed());
        addAttributeModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER_UUID, getAttackDamage());

        // Apply invisibility effect (short duration, refresh every tick)
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1, false, false));
    }

    @Override
    public void removeClassEffect(Player player) {
        removeAttributeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_UUID);
        removeAttributeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID);
        removeAttributeModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER_UUID);

        player.removeEffect(MobEffects.DAMAGE_BOOST);
    }

    private void addAttributeModifier(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id, double amount) {
        var instance = player.getAttribute(attribute);
        if (instance != null && instance.getModifier(id) == null) {
            instance.addPermanentModifier(new AttributeModifier(id, "rpgclass_bonus", amount, AttributeModifier.Operation.ADDITION));
        }
    }

    private void removeAttributeModifier(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id) {
        var instance = player.getAttribute(attribute);
        if (instance != null) {
            var modifier = instance.getModifier(id);
            if (modifier != null) instance.removeModifier(modifier);
        }
    }
}
