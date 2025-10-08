package net.pablo.rpgclasses.progression;

import net.pablo.rpgclasses.capability.PlayerProgressionData;

import java.util.*;

public class NodeRegistry {

    private static final Map<String, List<ProgressionNode>> CLASS_NODES = new HashMap<>();

    static {
        initializeWarriorNodes();
        initializeFighterNodes();
        // Initialize other classes with placeholder data
        CLASS_NODES.put("rogue", new ArrayList<>());
        CLASS_NODES.put("ranger", new ArrayList<>());
        CLASS_NODES.put("mage", new ArrayList<>());
        CLASS_NODES.put("tank", new ArrayList<>());
        CLASS_NODES.put("gambler", new ArrayList<>());
    }

    private static void initializeWarriorNodes() {
        List<ProgressionNode> nodes = new ArrayList<>();

        // SKILL LINE (Total: 33 levels)
        nodes.add(new ProgressionNode("warrior_skill_1", "skill", 5, "STAT", "+2 HP", "Vitality I", "Increases max health by 2"));
        nodes.add(new ProgressionNode("warrior_skill_2", "skill", 5, "STAT", "+1 ATK", "Power I", "Increases attack damage by 1", "warrior_skill_1"));
        nodes.add(new ProgressionNode("warrior_skill_3", "skill", 5, "CUSTOM_POINT", "skill", "Custom Stat Point", "Unlock custom stat point", "warrior_skill_2"));
        nodes.add(new ProgressionNode("warrior_skill_4", "skill", 5, "STAT", "+2% Lifesteal", "Blood Siphon", "Gain 2% lifesteal", "warrior_skill_3"));
        nodes.add(new ProgressionNode("warrior_skill_5", "skill", 5, "STAT", "+3 HP", "Vitality II", "Increases max health by 3", "warrior_skill_4"));
        nodes.add(new ProgressionNode("warrior_skill_6", "skill", 5, "CUSTOM_POINT", "skill", "Custom Stat Point", "Unlock custom stat point", "warrior_skill_5"));
        nodes.add(new ProgressionNode("warrior_skill_7", "skill", 3, "UNLOCK", "SKILL", "Vermillion Laceration", "Unlock Warrior skill!", "warrior_skill_6"));

        // PASSIVE LINE (Total: 33 levels)
        nodes.add(new ProgressionNode("warrior_passive_1", "passive", 5, "STAT", "+2% DMG", "Might I", "Increases damage by 2%"));
        nodes.add(new ProgressionNode("warrior_passive_2", "passive", 5, "STAT", "+3 HP", "Vitality I", "Increases max health by 3", "warrior_passive_1"));
        nodes.add(new ProgressionNode("warrior_passive_3", "passive", 5, "CUSTOM_POINT", "passive", "Custom Stat Point", "Unlock custom stat point", "warrior_passive_2"));
        nodes.add(new ProgressionNode("warrior_passive_4", "passive", 5, "STAT", "+2% DMG", "Might II", "Increases damage by 2%", "warrior_passive_3"));
        nodes.add(new ProgressionNode("warrior_passive_5", "passive", 5, "STAT", "+3 HP", "Vitality II", "Increases max health by 3", "warrior_passive_4"));
        nodes.add(new ProgressionNode("warrior_passive_6", "passive", 5, "CUSTOM_POINT", "passive", "Custom Stat Point", "Unlock custom stat point", "warrior_passive_5"));
        nodes.add(new ProgressionNode("warrior_passive_7", "passive", 3, "UNLOCK", "PASSIVE", "Bloodlust", "Unlock Warrior passive!", "warrior_passive_6"));

        // ITEM LINE (Total: 34 levels)
        nodes.add(new ProgressionNode("warrior_item_1", "item", 5, "STAT", "+1 ATK", "Sharpness I", "Increases attack damage by 1"));
        nodes.add(new ProgressionNode("warrior_item_2", "item", 5, "STAT", "+5 HP", "Fortitude I", "Increases max health by 5", "warrior_item_1"));
        nodes.add(new ProgressionNode("warrior_item_3", "item", 5, "CUSTOM_POINT", "item", "Custom Stat Point", "Unlock custom stat point", "warrior_item_2"));
        nodes.add(new ProgressionNode("warrior_item_4", "item", 5, "STAT", "+1 ATK", "Sharpness II", "Increases attack damage by 1", "warrior_item_3"));
        nodes.add(new ProgressionNode("warrior_item_5", "item", 5, "STAT", "+5 HP", "Fortitude II", "Increases max health by 5", "warrior_item_4"));
        nodes.add(new ProgressionNode("warrior_item_6", "item", 5, "CUSTOM_POINT", "item", "Custom Stat Point", "Unlock custom stat point", "warrior_item_5"));
        nodes.add(new ProgressionNode("warrior_item_7", "item", 4, "UNLOCK", "ITEM", "Crimson Oath", "Unlock Warrior item!", "warrior_item_6"));

        CLASS_NODES.put("warrior", nodes);
    }

    private static void initializeFighterNodes() {
        List<ProgressionNode> nodes = new ArrayList<>();

        // SKILL LINE
        nodes.add(new ProgressionNode("fighter_skill_1", "skill", 5, "STAT", "+2% AOE DMG", "Area Impact", "AOE damage +2%"));
        nodes.add(new ProgressionNode("fighter_skill_2", "skill", 5, "STAT", "+1 ATK", "Power Strike", "Attack damage +1", "fighter_skill_1"));
        nodes.add(new ProgressionNode("fighter_skill_3", "skill", 5, "CUSTOM_POINT", "skill", "Custom Stat Point", "Unlock custom stat", "fighter_skill_2"));
        nodes.add(new ProgressionNode("fighter_skill_4", "skill", 5, "STAT", "+0.5s Stun", "Stunning Blow", "Stun duration +0.5s", "fighter_skill_3"));
        nodes.add(new ProgressionNode("fighter_skill_5", "skill", 5, "STAT", "+2% AOE", "Area Impact II", "AOE radius +2%", "fighter_skill_4"));
        nodes.add(new ProgressionNode("fighter_skill_6", "skill", 5, "CUSTOM_POINT", "skill", "Custom Stat Point", "Unlock custom stat", "fighter_skill_5"));
        nodes.add(new ProgressionNode("fighter_skill_7", "skill", 3, "UNLOCK", "SKILL", "Seismic Smash", "Unlock Fighter skill!", "fighter_skill_6"));

        // PASSIVE LINE
        nodes.add(new ProgressionNode("fighter_passive_1", "passive", 5, "STAT", "+2% Combo DMG", "Combo Master", "Combo damage +2%"));
        nodes.add(new ProgressionNode("fighter_passive_2", "passive", 5, "STAT", "+1% ATK Speed", "Swift Strikes", "Attack speed +1%", "fighter_passive_1"));
        nodes.add(new ProgressionNode("fighter_passive_3", "passive", 5, "CUSTOM_POINT", "passive", "Custom Stat Point", "Unlock custom stat", "fighter_passive_2"));
        nodes.add(new ProgressionNode("fighter_passive_4", "passive", 5, "STAT", "+2% DMG", "Might", "All damage +2%", "fighter_passive_3"));
        nodes.add(new ProgressionNode("fighter_passive_5", "passive", 5, "STAT", "+1 ATK", "Power", "Attack damage +1", "fighter_passive_4"));
        nodes.add(new ProgressionNode("fighter_passive_6", "passive", 5, "CUSTOM_POINT", "passive", "Custom Stat Point", "Unlock custom stat", "fighter_passive_5"));
        nodes.add(new ProgressionNode("fighter_passive_7", "passive", 3, "UNLOCK", "PASSIVE", "Combo Mastery", "Unlock Fighter passive!", "fighter_passive_6"));

        // ITEM LINE
        nodes.add(new ProgressionNode("fighter_item_1", "item", 5, "STAT", "+1 ATK", "Weapon Enhancement", "Attack damage +1"));
        nodes.add(new ProgressionNode("fighter_item_2", "item", 5, "STAT", "+2% Fury", "Fury Build", "Fury generation +2%", "fighter_item_1"));
        nodes.add(new ProgressionNode("fighter_item_3", "item", 5, "CUSTOM_POINT", "item", "Custom Stat Point", "Unlock custom stat", "fighter_item_2"));
        nodes.add(new ProgressionNode("fighter_item_4", "item", 5, "STAT", "+1 ATK", "Weapon Enhancement II", "Attack damage +1", "fighter_item_3"));
        nodes.add(new ProgressionNode("fighter_item_5", "item", 5, "STAT", "+3% Shockwave", "Shockwave Power", "Shockwave damage +3%", "fighter_item_4"));
        nodes.add(new ProgressionNode("fighter_item_6", "item", 5, "CUSTOM_POINT", "item", "Custom Stat Point", "Unlock custom stat", "fighter_item_5"));
        nodes.add(new ProgressionNode("fighter_item_7", "item", 4, "UNLOCK", "ITEM", "Bloodforged Sigil", "Unlock Fighter item!", "fighter_item_6"));

        CLASS_NODES.put("fighter", nodes);
    }

    // Public API
    public static List<ProgressionNode> getNodesForClass(String className) {
        return CLASS_NODES.getOrDefault(className.toLowerCase(), new ArrayList<>());
    }

    public static List<ProgressionNode> getNodesForLine(String className, String line) {
        return getNodesForClass(className).stream()
                .filter(node -> node.getLine().equals(line))
                .toList();
    }

    public static ProgressionNode getNode(String className, String nodeId) {
        return getNodesForClass(className).stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    public static boolean canPurchaseNode(PlayerProgressionData progression, ProgressionNode node) {
        if (progression.hasNode(node.getLine(), node.getId())) {
            return false;
        }

        if (node.hasPrerequisites()) {
            for (String prereq : node.getPrerequisites()) {
                if (!progression.hasNode(node.getLine(), prereq)) {
                    return false;
                }
            }
        }

        return true;
    }
}