package net.pablo.rpgclasses.registry;

import net.pablo.rpgclasses.classes.*;

import java.util.HashMap;
import java.util.Map;

public class RPGClassRegistry {
    private static final Map<String, RPGClass> CLASSES = new HashMap<>();

    static {
        register(new Fighter());
        register(new Mage());
        register(new Ranger());
        register(new Rogue());
        register(new Tank());
        register(new Warrior());
        register(new Gambler());
    }

    public static void register(RPGClass rpgClass) {
        CLASSES.put(rpgClass.getClassName().toLowerCase(), rpgClass);
    }

    public static RPGClass getClassByName(String name) {
        if (name == null) return null;
        return CLASSES.get(name.toLowerCase());
    }
    
}
