package net.pablo.rpgclasses.capability;

import net.pablo.rpgclasses.classes.RPGClass;

import java.util.Map;

public interface IPlayerClass {
    RPGClass getSelectedClass();
    void setSelectedClass(RPGClass clazz);

    RPGClass getSecondaryClass();
    void setSecondaryClass(RPGClass clazz);

    String getPreviousClassName();
    void setPreviousClassName(String className);

    String getPreviousSecondaryClassName();
    void setPreviousSecondaryClassName(String className);

    int getLevel(String className);
    void setLevel(String className, int level);

    int getXP(String className);
    void addXP(String className, int xp);

    int xpToLevel(int level);

    Map<String, Integer> getClassXPMap();
    Map<String, Integer> getClassLevelMap();

    boolean canPickSecondaryClass();

    // NEW: Progression system
    PlayerProgressionData getProgressionData();

    void copyFrom(IPlayerClass other);
}