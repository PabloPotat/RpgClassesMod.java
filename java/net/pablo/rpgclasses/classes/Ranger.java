package net.pablo.rpgclasses.classes;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class Ranger extends RPGClass {
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("c4d6f871-2e45-4c9e-9c15-1d2f24bc5432");
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("9e2f8d17-6912-4f83-9f22-8a0b1de4567a");
    private static final UUID ATTACK_MODIFIER_UUID = UUID.fromString("eaf7b4b2-5f44-42c0-a77a-123f569d8c4e");

    public Ranger() {
        super("ranger", 22.0, 0.15, 3.0);
    }

    @Override
    public void applyClassEffect(Player player) {
        addAttributeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_UUID, getMaxHealth() - 20.0);
        addAttributeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID, getMovementSpeed());
        addAttributeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER_UUID, getAttackDamage());
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0, false, false));
    }

    @Override
    public void removeClassEffect(Player player) {
        removeAttributeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_UUID);
        removeAttributeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID);
        removeAttributeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER_UUID);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
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
