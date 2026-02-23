package krispasi.omGames.bedwars.item;

import org.bukkit.Material;

/**
 * Data model for a custom item definition.
 * <p>Includes material, projectile tuning (velocity, yield, damage, knockback), and
 * entity stats (health, speed, lifetime).</p>
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
    private final int uses;

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
                                int uses) {
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
        this.uses = uses;
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

    public int getUses() {
        return uses;
    }
}
