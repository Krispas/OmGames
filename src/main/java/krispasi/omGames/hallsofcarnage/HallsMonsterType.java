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
        double attackDamage,
        Material mainHand,
        Map<String, Material> armor
) {
    HallsMonsterType {
        scale = Math.max(0.1, scale);
        movementSpeedMultiplier = Math.max(0.0, movementSpeedMultiplier);
        attackDamage = attackDamage < 0.0 ? -1.0 : attackDamage;
        armor = Map.copyOf(armor);
    }
}
