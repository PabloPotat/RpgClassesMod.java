package net.pablo.rpgclasses.classes;

import net.minecraft.world.entity.player.Player;

public abstract class RPGClass {
    private final String className;
    private final double maxHealth;
    private final double movementSpeed;
    private final double attackDamage;

    public RPGClass(String name, double hp, double speed, double attack) {
        this.className = name;
        this.maxHealth = hp;
        this.movementSpeed = speed;
        this.attackDamage = attack;
    }

    public String getClassName() { return className; }
    public double getMaxHealth() { return maxHealth; }
    public double getMovementSpeed() { return movementSpeed; }
    public double getAttackDamage() { return attackDamage; }

    public abstract void applyClassEffect(Player player);
    public abstract void removeClassEffect(Player player);
}
