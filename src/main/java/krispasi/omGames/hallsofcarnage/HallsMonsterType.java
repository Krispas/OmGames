package krispasi.omGames.hallsofcarnage;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

record HallsMonsterType(
        String id,
        String name,
        EntityType entityType,
        double health,
        boolean baby,
        int slimeSize,
        double scale,
        double movementSpeedMultiplier,
        Material mainHand,
        Map<String, Material> armor
) {
    HallsMonsterType {
        scale = Math.max(0.1, scale);
        movementSpeedMultiplier = Math.max(0.0, movementSpeedMultiplier);
        armor = Map.copyOf(armor);
    }
}
