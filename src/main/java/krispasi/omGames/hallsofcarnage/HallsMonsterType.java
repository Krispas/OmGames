package krispasi.omGames.hallsofcarnage;

import java.util.Map;
import java.util.List;
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
        Map<String, Material> armor,
        List<DeathChild> deathChildren
) {
    HallsMonsterType {
        scale = Math.max(0.1, scale);
        movementSpeedMultiplier = Math.max(0.0, movementSpeedMultiplier);
        attackDamage = attackDamage < 0.0 ? -1.0 : attackDamage;
        armor = Map.copyOf(armor);
        deathChildren = deathChildren == null ? List.of() : List.copyOf(deathChildren);
    }

    record DeathChild(String monsterId, int count) {
        DeathChild {
            monsterId = monsterId == null ? "" : monsterId;
            count = Math.max(0, count);
        }
    }
}
