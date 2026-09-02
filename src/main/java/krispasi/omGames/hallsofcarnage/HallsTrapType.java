package krispasi.omGames.hallsofcarnage;

import java.util.List;
import org.bukkit.Material;

public record HallsTrapType(
        String id,
        String kind,
        int weight,
        List<String> levelTypes,
        Material blockMaterial,
        Material ceilingMaterial,
        Material modelMaterial,
        String itemModel,
        float modelScale,
        Material bridgeMaterial,
        int minSize,
        int maxSize,
        int depth,
        double damage,
        double radius,
        int intervalTicks,
        int activeTicks,
        float explosionPower
) {
    public HallsTrapType {
        levelTypes = List.copyOf(levelTypes);
        itemModel = itemModel == null ? "" : itemModel;
        weight = Math.max(0, weight);
        modelScale = Math.max(0.1f, modelScale);
        minSize = Math.max(1, minSize);
        maxSize = Math.max(minSize, maxSize);
        depth = Math.max(1, depth);
        damage = Math.max(0.0, damage);
        radius = Math.max(0.1, radius);
        intervalTicks = Math.max(1, intervalTicks);
        activeTicks = Math.max(1, activeTicks);
        explosionPower = Math.max(0.0f, explosionPower);
    }

    public boolean allowedForLevelType(String levelTypeId) {
        return levelTypes.isEmpty() || levelTypes.contains(levelTypeId);
    }
}
