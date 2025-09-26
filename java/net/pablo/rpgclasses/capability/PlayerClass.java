package net.pablo.rpgclasses.capability;

import net.pablo.rpgclasses.classes.RPGClass;
import net.pablo.rpgclasses.network.NetworkHandler;
import net.pablo.rpgclasses.network.SyncSelectedClassPacket;

import java.util.HashMap;
import java.util.Map;

public class PlayerClass implements IPlayerClass {
    private RPGClass selectedClass;
    private RPGClass secondaryClass;

    private String previousClassName;
    private String previousSecondaryClassName;

    private final Map<String, Integer> classXP = new HashMap<>();
    private final Map<String, Integer> classLevel = new HashMap<>();

    @Override
    public RPGClass getSelectedClass() { return selectedClass; }

    @Override
    public void setSelectedClass(RPGClass clazz) {
        if (selectedClass != null) previousClassName = selectedClass.getClassName();
        selectedClass = clazz;
        initializeClassData(clazz);

    }

    @Override
    public RPGClass getSecondaryClass() { return secondaryClass; }

    @Override
    public void setSecondaryClass(RPGClass clazz) {
        if (clazz == null) return;
        // Prevent selecting same class as primary
        if (selectedClass != null && selectedClass.getClassName().equalsIgnoreCase(clazz.getClassName())) return;
        secondaryClass = clazz;
        initializeClassData(clazz);
    }

    @Override
    public String getPreviousClassName() { return previousClassName; }
    @Override
    public void setPreviousClassName(String className) { previousClassName = className; }

    @Override
    public String getPreviousSecondaryClassName() { return previousSecondaryClassName; }
    @Override
    public void setPreviousSecondaryClassName(String className) { previousSecondaryClassName = className; }

    private void initializeClassData(RPGClass clazz) {
        if (clazz == null) return;
        classLevel.putIfAbsent(clazz.getClassName(), 1);
        classXP.putIfAbsent(clazz.getClassName(), 0);
    }

    @Override
    public int getLevel(String className) { return classLevel.getOrDefault(className, 1); }
    @Override
    public void setLevel(String className, int level) { classLevel.put(className, level); }

    @Override
    public int getXP(String className) { return classXP.getOrDefault(className, 0); }

    @Override
    public void addXP(String className, int xp) {
        if (className == null) return;

        int oldLevel = getLevel(className);
        int totalXP = getXP(className) + xp;
        int lvl = oldLevel;

        // Use new 1.08 exponential requirement
        while (totalXP >= xpToLevel(lvl)) {
            totalXP -= xpToLevel(lvl);
            lvl++;
        }

        classLevel.put(className, lvl);
        classXP.put(className, totalXP);
    }

    @Override
    public int xpToLevel(int level) {
        return (int) (100 * Math.pow(1.08, level - 1)); // exponential
    }

    @Override
    public Map<String, Integer> getClassXPMap() { return classXP; }
    @Override
    public Map<String, Integer> getClassLevelMap() { return classLevel; }

    @Override
    public boolean canPickSecondaryClass() {
        return classLevel.values().stream().anyMatch(lvl -> lvl >= 50);
    }

    @Override
    public void copyFrom(IPlayerClass other) {
        if (other == null) return;
        this.selectedClass = other.getSelectedClass();
        this.secondaryClass = other.getSecondaryClass();
        this.previousClassName = other.getPreviousClassName();
        this.previousSecondaryClassName = other.getPreviousSecondaryClassName();
        this.classXP.clear();
        this.classXP.putAll(other.getClassXPMap());
        this.classLevel.clear();
        this.classLevel.putAll(other.getClassLevelMap());
    }
}
