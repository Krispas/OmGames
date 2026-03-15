package krispasi.omGames.bedwars.item;

/**
 * Supported custom item types with default tuning values.
 * <p>Defaults are used when a field is missing from {@code custom-items.yml}.</p>
 */
public enum CustomItemType {
    FIREBALL(1.15, 3.2f, false, 0, 1, 4.0, 1.6, 0, -1.0, -1.0, -1.0, 0, 0),
    BRIDGE_EGG(1.2, 0.0f, false, 64, 1, 0.0, 0.0, 0, -1.0, -1.0, -1.0, 0, 0),
    BED_BUG(1.2, 0.0f, false, 0, 1, 0.0, 0.0, 15, -1.0, -1.0, -1.0, 0, 0),
    DREAM_DEFENDER(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 180, -1.0, -1.0, -1.0, 0, 0),
    CRYSTAL(0.0, 6.0f, false, 0, 1, 0.0, 0.0, 0, -1.0, -1.0, -1.0, 0, 0),
    HAPPY_GHAST(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 180, -1.0, -1.0, -1.0, 0, 0),
    RESPAWN_BEACON(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 30, -1.0, -1.0, -1.0, 0, 0),
    FLAMETHROWER(0.6, 0.6f, false, 0, 1, 2.0, 0.3, 0, -1.0, -1.0, -1.0, 32, 0),
    BRIDGE_BUILDER(0.0, 0.0f, false, 20, 3, 0.0, 0.0, 0, -1.0, -1.0, -1.0, 0, 0),
    CREEPING_ARROW(0.0, 0.0f, false, 0, 1, 6.0, 0.0, 20, -1.0, -1.0, -1.0, 0, 0),
    TACTICAL_NUKE(0.0, 30.0f, false, 0, 1, 0.0, 0.0, 60, -1.0, -1.0, -1.0, 0, 0),
    BRIDGE_ZAPPER(0.0, 0.0f, false, 10, 1, 0.0, 0.0, 0, -1.0, -1.0, -1.0, 0, 0),
    PORTABLE_SHOPKEEPER(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 30, -1.0, -1.0, -1.0, 0, 0),
    MAGIC_MILK(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 30, -1.0, -1.0, -1.0, 0, 0),
    ABYSSAL_RIFT(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 0, 30.0, -1.0, 10.0, 0, 0),
    ELYTRA_STRIKE(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 0, -1.0, -1.0, -1.0, 0, 0),
    GIGANTIFY_GRENADE(1.15, 0.0f, false, 0, 1, 0.0, 0.0, 6, -1.0, -1.0, -1.0, 0, 0),
    RAILGUN_BLAST(0.0, 0.0f, false, 0, 1, 1000.0, 0.0, 5, -1.0, -1.0, 75.0, 0, 0),
    MIRACLE_OF_THE_STARS(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 0, -1.0, -1.0, -1.0, 0, 0),
    TOWER_CHEST(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 0, -1.0, -1.0, -1.0, 0, 0),
    STEEL_SHELL(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 10, -1.0, -1.0, -1.0, 0, 0),
    PROXIMITY_MINE(0.0, 4.0f, false, 0, 1, 0.0, 0.0, 0, -1.0, -1.0, -1.0, 0, 0),
    LOCKPICK(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 0, -1.0, -1.0, -1.0, 0, 0),
    UNSTABLE_TELEPORTATION_DEVICE(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 0, -1.0, -1.0, -1.0, 0, 30),
    TIME_CAPSULE(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 0, -1.0, -1.0, -1.0, 0, 0);

    private final double defaultVelocity;
    private final float defaultYield;
    private final boolean defaultIncendiary;
    private final int defaultMaxBlocks;
    private final int defaultBridgeWidth;
    private final double defaultDamage;
    private final double defaultKnockback;
    private final int defaultLifetimeSeconds;
    private final double defaultHealth;
    private final double defaultSpeed;
    private final double defaultRange;
    private final int defaultUses;
    private final int defaultCooldownSeconds;

    CustomItemType(double defaultVelocity,
                   float defaultYield,
                   boolean defaultIncendiary,
                   int defaultMaxBlocks,
                   int defaultBridgeWidth,
                   double defaultDamage,
                   double defaultKnockback,
                   int defaultLifetimeSeconds,
                   double defaultHealth,
                   double defaultSpeed,
                   double defaultRange,
                   int defaultUses,
                   int defaultCooldownSeconds) {
        this.defaultVelocity = defaultVelocity;
        this.defaultYield = defaultYield;
        this.defaultIncendiary = defaultIncendiary;
        this.defaultMaxBlocks = defaultMaxBlocks;
        this.defaultBridgeWidth = defaultBridgeWidth;
        this.defaultDamage = defaultDamage;
        this.defaultKnockback = defaultKnockback;
        this.defaultLifetimeSeconds = defaultLifetimeSeconds;
        this.defaultHealth = defaultHealth;
        this.defaultSpeed = defaultSpeed;
        this.defaultRange = defaultRange;
        this.defaultUses = defaultUses;
        this.defaultCooldownSeconds = defaultCooldownSeconds;
    }

    public double getDefaultVelocity() {
        return defaultVelocity;
    }

    public float getDefaultYield() {
        return defaultYield;
    }

    public boolean isDefaultIncendiary() {
        return defaultIncendiary;
    }

    public int getDefaultMaxBlocks() {
        return defaultMaxBlocks;
    }

    public int getDefaultBridgeWidth() {
        return defaultBridgeWidth;
    }

    public double getDefaultDamage() {
        return defaultDamage;
    }

    public double getDefaultKnockback() {
        return defaultKnockback;
    }

    public int getDefaultLifetimeSeconds() {
        return defaultLifetimeSeconds;
    }

    public double getDefaultHealth() {
        return defaultHealth;
    }

    public double getDefaultSpeed() {
        return defaultSpeed;
    }

    public double getDefaultRange() {
        return defaultRange;
    }

    public int getDefaultUses() {
        return defaultUses;
    }

    public int getDefaultCooldownSeconds() {
        return defaultCooldownSeconds;
    }

    public double getDefaultSaveChancePercent() {
        return this == TIME_CAPSULE ? 50.0 : 0.0;
    }
}
