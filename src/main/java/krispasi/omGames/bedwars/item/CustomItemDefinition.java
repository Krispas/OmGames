package krispasi.omGames.bedwars.item;

import org.bukkit.Material;

/**
 * Data model for a custom item definition.
 * <p>Includes material, projectile tuning (velocity, yield, damage, knockback), aura tuning
 * (heal), and entity stats (health, speed, lifetime).</p>
 * @see krispasi.omGames.bedwars.item.CustomItemType
 */
public class CustomItemDefinition {
    private final String id;
    private final CustomItemType type;
    private final Material material;
    private final double velocity;
    private final float yield;
    private final boolean incendiary;
    private final int maxBlocks;
    private final int bridgeWidth;
    private final double damage;
    private final double knockback;
    private final int lifetimeSeconds;
    private final double health;
    private final double speed;
    private final double range;
    private final double heal;
    private final int uses;
    private final int cooldownSeconds;
    private final double saveChancePercent;

    public CustomItemDefinition(String id,
                                CustomItemType type,
                                Material material,
                                double velocity,
                                float yield,
                                boolean incendiary,
                                int maxBlocks,
                                int bridgeWidth,
                                double damage,
                                double knockback,
                                int lifetimeSeconds,
                                double health,
                                double speed,
                                double range,
                                double heal,
                                int uses,
                                int cooldownSeconds,
                                double saveChancePercent) {
        this.id = id;
        this.type = type;
        this.material = material;
        this.velocity = velocity;
        this.yield = yield;
        this.incendiary = incendiary;
        this.maxBlocks = maxBlocks;
        this.bridgeWidth = bridgeWidth;
        this.damage = damage;
        this.knockback = knockback;
        this.lifetimeSeconds = lifetimeSeconds;
        this.health = health;
        this.speed = speed;
        this.range = range;
        this.heal = heal;
        this.uses = uses;
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
        this.saveChancePercent = Math.max(0.0, Math.min(100.0, saveChancePercent));
    }

    public String getId() {
        return id;
    }

    public CustomItemType getType() {
        return type;
    }

    public Material getMaterial() {
        return material;
    }

    public double getVelocity() {
        return velocity;
    }

    public float getYield() {
        return yield;
    }

    public boolean isIncendiary() {
        return incendiary;
    }

    public int getMaxBlocks() {
        return maxBlocks;
    }

    public int getBridgeWidth() {
        return bridgeWidth;
    }

    public double getDamage() {
        return damage;
    }

    public double getKnockback() {
        return knockback;
    }

    public int getLifetimeSeconds() {
        return lifetimeSeconds;
    }

    public double getHealth() {
        return health;
    }

    public double getSpeed() {
        return speed;
    }

    public double getRange() {
        return range;
    }

    public double getHeal() {
        return heal;
    }

    public int getUses() {
        return uses;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public double getSaveChancePercent() {
        return saveChancePercent;
    }
}
