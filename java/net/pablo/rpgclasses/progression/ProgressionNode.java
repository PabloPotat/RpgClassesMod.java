package net.pablo.rpgclasses.progression;

public class ProgressionNode {
    private final String id;              // "warrior_skill_1"
    private final String line;            // "skill", "passive", "item"
    private final int cost;               // How many class levels to unlock
    private final String rewardType;      // "STAT", "CUSTOM_POINT", "UNLOCK"
    private final String rewardValue;     // "+2 HP", "Blood Resonance", "SKILL"
    private final String displayName;     // What shows in UI
    private final String description;     // Tooltip info
    private final String[] prerequisites; // Required nodes before this one

    public ProgressionNode(String id, String line, int cost, String rewardType,
                           String rewardValue, String displayName, String description,
                           String... prerequisites) {
        this.id = id;
        this.line = line;
        this.cost = cost;
        this.rewardType = rewardType;
        this.rewardValue = rewardValue;
        this.displayName = displayName;
        this.description = description;
        this.prerequisites = prerequisites;
    }

    public String getId() { return id; }
    public String getLine() { return line; }
    public int getCost() { return cost; }
    public String getRewardType() { return rewardType; }
    public String getRewardValue() { return rewardValue; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String[] getPrerequisites() { return prerequisites; }

    public boolean hasPrerequisites() {
        return prerequisites != null && prerequisites.length > 0;
    }
}