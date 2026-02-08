package krispasi.omGames.bedwars.item;

public enum CustomItemType {
    FIREBALL(1.15, 2.2f, false, 0, 1),
    BRIDGE_EGG(1.2, 0.0f, false, 64, 1);

    private final double defaultVelocity;
    private final float defaultYield;
    private final boolean defaultIncendiary;
    private final int defaultMaxBlocks;
    private final int defaultBridgeWidth;

    CustomItemType(double defaultVelocity,
                   float defaultYield,
                   boolean defaultIncendiary,
                   int defaultMaxBlocks,
                   int defaultBridgeWidth) {
        this.defaultVelocity = defaultVelocity;
        this.defaultYield = defaultYield;
        this.defaultIncendiary = defaultIncendiary;
        this.defaultMaxBlocks = defaultMaxBlocks;
        this.defaultBridgeWidth = defaultBridgeWidth;
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
}
