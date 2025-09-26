package net.pablo.rpgclasses.classes;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class Tank extends RPGClass {

    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("a1b2c3d4-5e6f-7a8b-9c0d-123456abcdef");
    private static final UUID ARMOR_MODIFIER_UUID = UUID.fromString("b2c3d4e5-6f7a-8b9c-0d1e-234567abcdef");
    private static final UUID ATTACK_DAMAGE_MODIFIER_UUID = UUID.fromString("c3d4e5f6-7a8b-9c0d-1e2f-345678abcdef");

    private double baseArmor; // Add this field

    public Tank() {
        super("tank", 40.0, 0.0, 3.0); // Pass 0.0 for armor to super, handle separately
        this.baseArmor = 10.0; // Set default tank armor
    }

    public double getBaseArmor() {
        return baseArmor;
    }

    @Override
    public void applyClassEffect(Player player) {
        // Health: adjust relative to current max
        addAttributeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_UUID, getMaxHealth() - player.getMaxHealth());

        // Armor
        addAttributeModifier(player, Attributes.ARMOR, ARMOR_MODIFIER_UUID, getBaseArmor());

        // Attack Damage
        addAttributeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER_UUID, getAttackDamage());

        // Optional: resistance effect
        if (!player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0, false, false));
        }
    }

    @Override
    public void removeClassEffect(Player player) {
        removeAttributeModifier(player, Attributes.MAX_HEALTH, HEALTH_MODIFIER_UUID);
        removeAttributeModifier(player, Attributes.ARMOR, ARMOR_MODIFIER_UUID);
        removeAttributeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER_UUID);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
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
