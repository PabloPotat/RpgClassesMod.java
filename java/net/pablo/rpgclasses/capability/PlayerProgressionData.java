package net.pablo.rpgclasses.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

public class PlayerProgressionData {
    // Track purchased nodes by line
    private final Map<String, Set<String>> purchasedNodes = new HashMap<>();

    // Track custom stats spent per line
    private final Map<String, Map<String, Integer>> spentCustomStats = new HashMap<>();

    // Track skill points per class (separate currency for each class)
    private final Map<String, Integer> classSkillPoints = new HashMap<>();

    // Track if final rewards are unlocked
    private boolean skillUnlocked = false;
    private boolean passiveUnlocked = false;
    private boolean itemUnlocked = false;

    public PlayerProgressionData() {
        purchasedNodes.put("skill", new HashSet<>());
        purchasedNodes.put("passive", new HashSet<>());
        purchasedNodes.put("item", new HashSet<>());

        spentCustomStats.put("skill", new HashMap<>());
        spentCustomStats.put("passive", new HashMap<>());
        spentCustomStats.put("item", new HashMap<>());
    }

    // Node management
    public boolean hasNode(String line, String nodeId) {
        return purchasedNodes.get(line).contains(nodeId);
    }

    public void purchaseNode(String line, String nodeId, int cost, String className) {
        if (!hasNode(line, nodeId)) {
            purchasedNodes.get(line).add(nodeId);
            // Deduct from class-specific points
            int current = classSkillPoints.getOrDefault(className, 0);
            classSkillPoints.put(className, current - cost);
        }
    }

    // ADDED: Overload for commands that don't specify class
    public void purchaseNode(String line, String nodeId, int cost) {
        if (!hasNode(line, nodeId)) {
            purchasedNodes.get(line).add(nodeId);
        }
    }

    public Set<String> getPurchasedNodes(String line) {
        return new HashSet<>(purchasedNodes.get(line));
    }

    public int getNodeCount(String line) {
        return purchasedNodes.get(line).size();
    }

    // ADDED: Get total spent levels across all lines
    public int getTotalSpentLevels() {
        int total = 0;
        for (Set<String> nodes : purchasedNodes.values()) {
            total += nodes.size();
        }
        return total;
    }

    // ADDED: Get available levels based on class level
    public int getAvailableLevels(int classLevel) {
        return Math.max(0, classLevel - getTotalSpentLevels());
    }

    // Skill points management (per class)
    public int getSkillPoints(String className) {
        return classSkillPoints.getOrDefault(className, 0);
    }

    public void setSkillPoints(String className, int amount) {
        classSkillPoints.put(className, amount);
    }

    public void addSkillPoints(String className, int amount) {
        int current = classSkillPoints.getOrDefault(className, 0);
        classSkillPoints.put(className, current + amount);
    }

    public Map<String, Integer> getAllClassSkillPoints() {
        return new HashMap<>(classSkillPoints);
    }

    // Custom stats
    public int getCustomStatLevel(String line, String statName) {
        return spentCustomStats.get(line).getOrDefault(statName, 0);
    }

    public void spendCustomStat(String line, String statName) {
        Map<String, Integer> lineStats = spentCustomStats.get(line);
        lineStats.put(statName, lineStats.getOrDefault(statName, 0) + 1);
    }

    public Map<String, Integer> getCustomStats(String line) {
        return new HashMap<>(spentCustomStats.get(line));
    }

    public int getAvailableCustomPoints(String line) {
        // Count CUSTOM_POINT nodes purchased
        int totalPoints = 0;
        for (String nodeId : purchasedNodes.get(line)) {
            if (nodeId.contains("_3") || nodeId.contains("_6")) { // Nodes 3 and 6 give custom points
                totalPoints++;
            }
        }

        // Subtract spent points
        int spentPoints = spentCustomStats.get(line).values().stream().mapToInt(Integer::intValue).sum();
        return totalPoints - spentPoints;
    }

    // Final unlocks
    public boolean isSkillUnlocked() { return skillUnlocked; }
    public void setSkillUnlocked(boolean unlocked) { this.skillUnlocked = unlocked; }

    public boolean isPassiveUnlocked() { return passiveUnlocked; }
    public void setPassiveUnlocked(boolean unlocked) { this.passiveUnlocked = unlocked; }

    public boolean isItemUnlocked() { return itemUnlocked; }
    public void setItemUnlocked(boolean unlocked) { this.itemUnlocked = unlocked; }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        // Save purchased nodes
        CompoundTag nodesTag = new CompoundTag();
        purchasedNodes.forEach((line, nodes) -> {
            ListTag nodeList = new ListTag();
            nodes.forEach(nodeId -> nodeList.add(StringTag.valueOf(nodeId)));
            nodesTag.put(line, nodeList);
        });
        tag.put("purchasedNodes", nodesTag);

        // Save custom stats
        CompoundTag statsTag = new CompoundTag();
        spentCustomStats.forEach((line, stats) -> {
            CompoundTag lineTag = new CompoundTag();
            stats.forEach((statName, level) -> lineTag.putInt(statName, level));
            statsTag.put(line, lineTag);
        });
        tag.put("spentCustomStats", statsTag);

        // Save class skill points
        CompoundTag pointsTag = new CompoundTag();
        classSkillPoints.forEach((className, points) -> pointsTag.putInt(className, points));
        tag.put("classSkillPoints", pointsTag);

        // Save unlocks
        tag.putBoolean("skillUnlocked", skillUnlocked);
        tag.putBoolean("passiveUnlocked", passiveUnlocked);
        tag.putBoolean("itemUnlocked", itemUnlocked);

        return tag;
    }

    public void load(CompoundTag tag) {
        // Load purchased nodes
        if (tag.contains("purchasedNodes")) {
            CompoundTag nodesTag = tag.getCompound("purchasedNodes");
            nodesTag.getAllKeys().forEach(line -> {
                ListTag nodeList = nodesTag.getList(line, Tag.TAG_STRING);
                Set<String> nodes = new HashSet<>();
                for (int i = 0; i < nodeList.size(); i++) {
                    nodes.add(nodeList.getString(i));
                }
                purchasedNodes.put(line, nodes);
            });
        }

        // Load custom stats
        if (tag.contains("spentCustomStats")) {
            CompoundTag statsTag = tag.getCompound("spentCustomStats");
            statsTag.getAllKeys().forEach(line -> {
                CompoundTag lineTag = statsTag.getCompound(line);
                Map<String, Integer> stats = new HashMap<>();
                lineTag.getAllKeys().forEach(statName -> stats.put(statName, lineTag.getInt(statName)));
                spentCustomStats.put(line, stats);
            });
        }

        // Load class skill points
        if (tag.contains("classSkillPoints")) {
            CompoundTag pointsTag = tag.getCompound("classSkillPoints");
            pointsTag.getAllKeys().forEach(className ->
                    classSkillPoints.put(className, pointsTag.getInt(className)));
        }

        // Load unlocks
        skillUnlocked = tag.getBoolean("skillUnlocked");
        passiveUnlocked = tag.getBoolean("passiveUnlocked");
        itemUnlocked = tag.getBoolean("itemUnlocked");
    }

    public void copyFrom(PlayerProgressionData other) {
        this.purchasedNodes.clear();
        other.purchasedNodes.forEach((line, nodes) ->
                this.purchasedNodes.put(line, new HashSet<>(nodes)));

        this.spentCustomStats.clear();
        other.spentCustomStats.forEach((line, stats) ->
                this.spentCustomStats.put(line, new HashMap<>(stats)));

        this.classSkillPoints.clear();
        this.classSkillPoints.putAll(other.getAllClassSkillPoints());

        this.skillUnlocked = other.skillUnlocked;
        this.passiveUnlocked = other.passiveUnlocked;
        this.itemUnlocked = other.itemUnlocked;
    }
}