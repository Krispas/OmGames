package krispasi.omGames.bedwars.item;

import org.bukkit.Material;

public class CustomItemDefinition {
    private final String id;
    private final CustomItemType type;
    private final Material material;
    private final double velocity;
    private final float yield;
    private final boolean incendiary;
    private final int maxBlocks;
    private final int bridgeWidth;

    public CustomItemDefinition(String id,
                                CustomItemType type,
                                Material material,
                                double velocity,
                                float yield,
                                boolean incendiary,
                                int maxBlocks,
                                int bridgeWidth) {
        this.id = id;
        this.type = type;
        this.material = material;
        this.velocity = velocity;
        this.yield = yield;
        this.incendiary = incendiary;
        this.maxBlocks = maxBlocks;
        this.bridgeWidth = bridgeWidth;
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
}
