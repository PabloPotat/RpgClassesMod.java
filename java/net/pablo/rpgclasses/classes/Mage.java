package net.pablo.rpgclasses.classes;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class Mage extends RPGClass {
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("b9e07a41-51a3-4f17-9a14-2ecf8d32e672");
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("d92a09a6-f03d-4380-b8f0-7f1b3de234b7");
    private static final UUID ATTACK_MODIFIER_UUID = UUID.fromString("f833eb55-c29b-42b9-9d49-6e5e5ca2725a");

    public Mage() {
        super("mage", 20.0, 0.05, 2.0);
    }

    @Override
    public void applyClassEffect(Player player) {
        addAttributeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_UUID, getMaxHealth() - 20.0);
        addAttributeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID, getMovementSpeed());
        addAttributeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER_UUID, getAttackDamage());
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0, false, false));
    }

    @Override
    public void removeClassEffect(Player player) {
        removeAttributeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_UUID);
        removeAttributeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID);
        removeAttributeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER_UUID);
        player.removeEffect(MobEffects.REGENERATION);
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
