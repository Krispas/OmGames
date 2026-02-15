package krispasi.omGames.bedwars.item;

public enum CustomItemType {
    FIREBALL(1.15, 3.2f, false, 0, 1, 4.0, 1.6, 0, -1.0, -1.0),
    BRIDGE_EGG(1.2, 0.0f, false, 64, 1, 0.0, 0.0, 0, -1.0, -1.0),
    BED_BUG(1.2, 0.0f, false, 0, 1, 0.0, 0.0, 15, -1.0, -1.0),
    DREAM_DEFENDER(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 180, -1.0, -1.0),
    CRYSTAL(0.0, 6.0f, false, 0, 1, 0.0, 0.0, 0, -1.0, -1.0),
    HAPPY_GHAST(0.0, 0.0f, false, 0, 1, 0.0, 0.0, 180, -1.0, -1.0);

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

    CustomItemType(double defaultVelocity,
                   float defaultYield,
                   boolean defaultIncendiary,
                   int defaultMaxBlocks,
                   int defaultBridgeWidth,
                   double defaultDamage,
                   double defaultKnockback,
                   int defaultLifetimeSeconds,
                   double defaultHealth,
                   double defaultSpeed) {
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
}
