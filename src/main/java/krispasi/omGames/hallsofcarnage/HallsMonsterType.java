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
        Material mainHand,
        Map<String, Material> armor
) {
    HallsMonsterType {
        armor = Map.copyOf(armor);
    }
}
