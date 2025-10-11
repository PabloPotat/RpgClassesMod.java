package net.pablo.rpgclasses.progression;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.*;

/**
 * Defines all available custom stats that players can spend custom points on.
 * These are basic Minecraft stat increases like HP, Speed, Defense, etc.
 */
public class CustomStatDefinitions {

    // Custom stat definitions per line
    private static final Map<String, List<CustomStat>> LINE_STATS = new HashMap<>();

    static {
        initializeSkillLineStats();
        initializePassiveLineStats();
        initializeItemLineStats();
    }

    /**
     * Initialize custom stats for SKILL line
     * These focus on offensive combat stats
     */
    private static void initializeSkillLineStats() {
        List<CustomStat> stats = new ArrayList<>();

        // Attack Damage - +0.5 per level, max 5 levels
        stats.add(new CustomStat(
                "Attack Power",
                "Increases your attack damage",
                5,  // max level
                Attributes.ATTACK_DAMAGE,
                0.5,  // +0.5 ATK per level
                AttributeModifier.Operation.ADDITION
        ));

        // Attack Speed - +2% per level, max 5 levels
        stats.add(new CustomStat(
                "Attack Speed",
                "Increases your attack speed",
                5,
                Attributes.ATTACK_SPEED,
                0.02,  // +2% per level
                AttributeModifier.Operation.MULTIPLY_BASE
        ));

        // Knockback Resistance - +5% per level, max 5 levels
        stats.add(new CustomStat(
                "Knockback Resistance",
                "Reduces knockback taken",
                5,
                Attributes.KNOCKBACK_RESISTANCE,
                0.05,  // +5% per level
                AttributeModifier.Operation.ADDITION
        ));

        LINE_STATS.put("skill", stats);
    }

    /**
     * Initialize custom stats for PASSIVE line
     * These focus on defensive/utility stats
     */
    private static void initializePassiveLineStats() {
        List<CustomStat> stats = new ArrayList<>();

        // Max Health - +2 HP per level, max 5 levels
        stats.add(new CustomStat(
                "Vitality",
                "Increases your maximum health",
                5,
                Attributes.MAX_HEALTH,
                2.0,  // +2 HP per level
                AttributeModifier.Operation.ADDITION
        ));

        // Armor - +1 armor per level, max 5 levels
        stats.add(new CustomStat(
                "Defense",
                "Increases your armor value",
                5,
                Attributes.ARMOR,
                1.0,  // +1 armor per level
                AttributeModifier.Operation.ADDITION
        ));

        // Armor Toughness - +0.5 per level, max 5 levels
        stats.add(new CustomStat(
                "Toughness",
                "Increases your armor toughness",
                5,
                Attributes.ARMOR_TOUGHNESS,
                0.5,  // +0.5 toughness per level
                AttributeModifier.Operation.ADDITION
        ));

        LINE_STATS.put("passive", stats);
    }

    /**
     * Initialize custom stats for ITEM line
     * These focus on mobility and utility stats
     */
    private static void initializeItemLineStats() {
        List<CustomStat> stats = new ArrayList<>();

        // Movement Speed - +2% per level, max 5 levels
        stats.add(new CustomStat(
                "Movement Speed",
                "Increases your movement speed",
                5,
                Attributes.MOVEMENT_SPEED,
                0.02,  // +2% per level
                AttributeModifier.Operation.MULTIPLY_BASE
        ));

        // Max Health (alternative) - +1.5 HP per level, max 5 levels
        stats.add(new CustomStat(
                "Endurance",
                "Increases your maximum health",
                5,
                Attributes.MAX_HEALTH,
                1.5,  // +1.5 HP per level
                AttributeModifier.Operation.ADDITION
        ));

        // Luck - +0.5 per level, max 5 levels
        stats.add(new CustomStat(
                "Fortune",
                "Increases your luck stat",
                5,
                Attributes.LUCK,
                0.5,  // +0.5 luck per level
                AttributeModifier.Operation.ADDITION
        ));

        LINE_STATS.put("item", stats);
    }

    // ==================== PUBLIC API ====================

    /**
     * Get all custom stats available for a specific line
     */
    public static List<CustomStat> getStatsForLine(String line) {
        return LINE_STATS.getOrDefault(line, new ArrayList<>());
    }

    /**
     * Get a specific custom stat by name and line
     */
    public static CustomStat getStat(String line, String statName) {
        return getStatsForLine(line).stream()
                .filter(stat -> stat.getName().equals(statName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if a stat name is valid for a line
     */
    public static boolean isValidStat(String line, String statName) {
        return getStat(line, statName) != null;
    }

    /**
     * Get all stat names for a line
     */
    public static List<String> getStatNames(String line) {
        return getStatsForLine(line).stream()
                .map(CustomStat::getName)
                .toList();
    }

    // ==================== INNER CLASS ====================

    /**
     * Represents a single custom stat option
     */
    public static class CustomStat {
        private final String name;
        private final String description;
        private final int maxLevel;
        private final net.minecraft.world.entity.ai.attributes.Attribute attribute;
        private final double valuePerLevel;
        private final AttributeModifier.Operation operation;

        public CustomStat(String name, String description, int maxLevel,
                          net.minecraft.world.entity.ai.attributes.Attribute attribute,
                          double valuePerLevel, AttributeModifier.Operation operation) {
            this.name = name;
            this.description = description;
            this.maxLevel = maxLevel;
            this.attribute = attribute;
            this.valuePerLevel = valuePerLevel;
            this.operation = operation;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getMaxLevel() { return maxLevel; }
        public net.minecraft.world.entity.ai.attributes.Attribute getAttribute() { return attribute; }
        public double getValuePerLevel() { return valuePerLevel; }
        public AttributeModifier.Operation getOperation() { return operation; }

        /**
         * Get formatted display text for UI
         */
        public String getDisplayText(int currentLevel) {
            String value = operation == AttributeModifier.Operation.MULTIPLY_BASE ?
                    String.format("%.0f%%", valuePerLevel * 100) :
                    String.format("%.1f", valuePerLevel);

            return String.format("%s +%s", name, value);
        }

        /**
         * Get tooltip text
         */
        public String getTooltip(int currentLevel) {
            return String.format("%s\n§7%s\n§eCurrent: %d/%d",
                    name, description, currentLevel, maxLevel);
        }
    }
}