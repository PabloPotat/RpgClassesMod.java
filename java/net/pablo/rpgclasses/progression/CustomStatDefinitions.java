package net.pablo.rpgclasses.progression;

import java.util.*;

/**
 * Defines all custom stats available for each class line (Elden Ring style)
 * Using ONLY vanilla Minecraft attributes
 */
public class CustomStatDefinitions {

    public static class CustomStat {
        private final String id;
        private final String displayName;
        private final String description;
        private final int maxLevel;
        private final String effectPerLevel;
        private final MinecraftAttribute attribute;
        private final double valuePerLevel;
        private final boolean isPercentage;

        public CustomStat(String id, String displayName, String description, int maxLevel,
                          String effectPerLevel, MinecraftAttribute attribute, double valuePerLevel, boolean isPercentage) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.maxLevel = maxLevel;
            this.effectPerLevel = effectPerLevel;
            this.attribute = attribute;
            this.valuePerLevel = valuePerLevel;
            this.isPercentage = isPercentage;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public int getMaxLevel() { return maxLevel; }
        public String getEffectPerLevel() { return effectPerLevel; }
        public MinecraftAttribute getAttribute() { return attribute; }
        public double getValuePerLevel() { return valuePerLevel; }
        public boolean isPercentage() { return isPercentage; }
    }

    public enum MinecraftAttribute {
        MAX_HEALTH,           // net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH
        ARMOR,                // net.minecraft.world.entity.ai.attributes.Attributes.ARMOR
        ARMOR_TOUGHNESS,      // net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS
        ATTACK_DAMAGE,        // net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE
        ATTACK_SPEED,         // net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED
        MOVEMENT_SPEED,       // net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED
        ATTACK_KNOCKBACK,     // net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK
        KNOCKBACK_RESISTANCE  // net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE
    }

    // Warrior Stats - Focus on Health, Armor, and Damage
    private static final List<CustomStat> WARRIOR_SKILL_STATS = Arrays.asList(
            new CustomStat("vitality", "Vitality", "Increases maximum health", 20,
                    "+2 HP per level", MinecraftAttribute.MAX_HEALTH, 2.0, false),
            new CustomStat("strength", "Strength", "Increases attack damage", 15,
                    "+1 Attack Damage per level", MinecraftAttribute.ATTACK_DAMAGE, 1.0, false),
            new CustomStat("vigor", "Vigor", "Greatly increases health pool", 15,
                    "+3 HP per level", MinecraftAttribute.MAX_HEALTH, 3.0, false),
            new CustomStat("power", "Power", "Increases attack power", 10,
                    "+5% Attack Damage per level", MinecraftAttribute.ATTACK_DAMAGE, 0.05, true),
            new CustomStat("might", "Might", "Increases raw damage output", 10,
                    "+2 Attack Damage per level", MinecraftAttribute.ATTACK_DAMAGE, 2.0, false)
    );

    private static final List<CustomStat> WARRIOR_PASSIVE_STATS = Arrays.asList(
            new CustomStat("fortitude", "Fortitude", "Increases armor rating", 20,
                    "+1 Armor per level", MinecraftAttribute.ARMOR, 1.0, false),
            new CustomStat("resilience", "Resilience", "Increases armor toughness", 15,
                    "+0.5 Armor Toughness per level", MinecraftAttribute.ARMOR_TOUGHNESS, 0.5, false),
            new CustomStat("endurance", "Endurance", "Increases health significantly", 15,
                    "+4 HP per level", MinecraftAttribute.MAX_HEALTH, 4.0, false),
            new CustomStat("stability", "Stability", "Increases knockback resistance", 10,
                    "+5% Knockback Resist per level", MinecraftAttribute.KNOCKBACK_RESISTANCE, 0.05, true),
            new CustomStat("constitution", "Constitution", "Boosts overall survivability", 12,
                    "+2 Armor per level", MinecraftAttribute.ARMOR, 2.0, false)
    );

    private static final List<CustomStat> WARRIOR_ITEM_STATS = Arrays.asList(
            new CustomStat("swiftness", "Swiftness", "Increases attack speed", 15,
                    "+5% Attack Speed per level", MinecraftAttribute.ATTACK_SPEED, 0.05, true),
            new CustomStat("agility", "Agility", "Increases movement speed", 10,
                    "+2% Movement Speed per level", MinecraftAttribute.MOVEMENT_SPEED, 0.02, true),
            new CustomStat("ferocity", "Ferocity", "Increases attack damage", 12,
                    "+1.5 Attack Damage per level", MinecraftAttribute.ATTACK_DAMAGE, 1.5, false),
            new CustomStat("impact", "Impact", "Increases knockback power", 10,
                    "+0.5 Knockback per level", MinecraftAttribute.ATTACK_KNOCKBACK, 0.5, false),
            new CustomStat("haste", "Haste", "Significantly increases attack speed", 10,
                    "+8% Attack Speed per level", MinecraftAttribute.ATTACK_SPEED, 0.08, true)
    );

    // Fighter Stats - Focus on Speed and Knockback
    private static final List<CustomStat> FIGHTER_SKILL_STATS = Arrays.asList(
            new CustomStat("striking_power", "Striking Power", "Increases attack damage", 15,
                    "+1 Attack Damage per level", MinecraftAttribute.ATTACK_DAMAGE, 1.0, false),
            new CustomStat("combat_speed", "Combat Speed", "Increases attack speed", 12,
                    "+6% Attack Speed per level", MinecraftAttribute.ATTACK_SPEED, 0.06, true),
            new CustomStat("health_boost", "Health Boost", "Increases maximum health", 18,
                    "+2 HP per level", MinecraftAttribute.MAX_HEALTH, 2.0, false),
            new CustomStat("force", "Force", "Increases knockback on attacks", 10,
                    "+0.6 Knockback per level", MinecraftAttribute.ATTACK_KNOCKBACK, 0.6, false),
            new CustomStat("brutality", "Brutality", "Raw damage increase", 12,
                    "+6% Attack Damage per level", MinecraftAttribute.ATTACK_DAMAGE, 0.06, true)
    );

    private static final List<CustomStat> FIGHTER_PASSIVE_STATS = Arrays.asList(
            new CustomStat("iron_skin", "Iron Skin", "Increases armor rating", 18,
                    "+1 Armor per level", MinecraftAttribute.ARMOR, 1.0, false),
            new CustomStat("toughness", "Toughness", "Increases armor toughness", 12,
                    "+0.6 Armor Toughness per level", MinecraftAttribute.ARMOR_TOUGHNESS, 0.6, false),
            new CustomStat("mobility", "Mobility", "Increases movement speed", 12,
                    "+1.5% Movement Speed per level", MinecraftAttribute.MOVEMENT_SPEED, 0.015, true),
            new CustomStat("steadfast", "Steadfast", "Increases knockback resistance", 10,
                    "+6% Knockback Resist per level", MinecraftAttribute.KNOCKBACK_RESISTANCE, 0.06, true),
            new CustomStat("hardy", "Hardy", "Boosts health pool", 15,
                    "+3 HP per level", MinecraftAttribute.MAX_HEALTH, 3.0, false)
    );

    private static final List<CustomStat> FIGHTER_ITEM_STATS = Arrays.asList(
            new CustomStat("dexterity", "Dexterity", "Increases attack speed", 15,
                    "+5% Attack Speed per level", MinecraftAttribute.ATTACK_SPEED, 0.05, true),
            new CustomStat("speed", "Speed", "Increases movement speed", 12,
                    "+2.5% Movement Speed per level", MinecraftAttribute.MOVEMENT_SPEED, 0.025, true),
            new CustomStat("precision", "Precision", "Increases attack damage", 10,
                    "+1.2 Attack Damage per level", MinecraftAttribute.ATTACK_DAMAGE, 1.2, false),
            new CustomStat("momentum", "Momentum", "Increases knockback power", 12,
                    "+0.4 Knockback per level", MinecraftAttribute.ATTACK_KNOCKBACK, 0.4, false),
            new CustomStat("reflexes", "Reflexes", "Greatly increases attack speed", 8,
                    "+10% Attack Speed per level", MinecraftAttribute.ATTACK_SPEED, 0.10, true)
    );

    // Registry
    private static final Map<String, Map<String, List<CustomStat>>> CLASS_STATS = new HashMap<>();

    static {
        Map<String, List<CustomStat>> warriorStats = new HashMap<>();
        warriorStats.put("skill", WARRIOR_SKILL_STATS);
        warriorStats.put("passive", WARRIOR_PASSIVE_STATS);
        warriorStats.put("item", WARRIOR_ITEM_STATS);
        CLASS_STATS.put("warrior", warriorStats);

        Map<String, List<CustomStat>> fighterStats = new HashMap<>();
        fighterStats.put("skill", FIGHTER_SKILL_STATS);
        fighterStats.put("passive", FIGHTER_PASSIVE_STATS);
        fighterStats.put("item", FIGHTER_ITEM_STATS);
        CLASS_STATS.put("fighter", fighterStats);
    }

    // Public API
    public static List<CustomStat> getStatsForLine(String className, String line) {
        Map<String, List<CustomStat>> classStats = CLASS_STATS.get(className.toLowerCase());
        if (classStats == null) return new ArrayList<>();
        return classStats.getOrDefault(line, new ArrayList<>());
    }

    public static CustomStat getStat(String className, String line, String statId) {
        return getStatsForLine(className, line).stream()
                .filter(stat -> stat.getId().equals(statId))
                .findFirst()
                .orElse(null);
    }

    public static boolean isValidStat(String className, String line, String statId) {
        return getStat(className, line, statId) != null;
    }
}