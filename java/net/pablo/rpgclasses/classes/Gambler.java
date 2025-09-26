package net.pablo.rpgclasses.classes;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.pablo.rpgclasses.classes.RPGClass;

import java.util.UUID;

public class Gambler extends RPGClass {

    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("a1f6c2ee-2e90-4e29-8a16-ff7bc637d3c1");
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("8f8a5407-f07d-4d94-8de7-47bc9d1c9e2e");
    private static final UUID ATTACK_MODIFIER_UUID = UUID.fromString("d3a55f9c-5e2f-4a5e-bf3c-66e06a10c9db");

    public Gambler() {
        super("gambler", 30.0, 0.1, 4.0);
    }

    @Override
    public void applyClassEffect(Player player) {
        addAttributeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_UUID, getMaxHealth() - 20.0);
        addAttributeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID, getMovementSpeed());
        addAttributeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER_UUID, getAttackDamage());
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1, false, false));
    }

    @Override
    public void removeClassEffect(Player player) {
        removeAttributeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_UUID);
        removeAttributeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID);
        removeAttributeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_MODIFIER_UUID);
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