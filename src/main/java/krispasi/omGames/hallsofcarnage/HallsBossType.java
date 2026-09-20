package krispasi.omGames.hallsofcarnage;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

record HallsBossType(
        String id,
        String name,
        double health,
        double multiplayerHpBoost,
        Material displayMaterial,
        String itemModel,
        List<DisplayPart> displayParts,
        Overdrive overdrive
) {
    HallsBossType {
        id = normalize(id);
        name = name == null || name.isBlank() ? id : name;
        health = Math.max(1.0, health);
        multiplayerHpBoost = Math.max(1.0, multiplayerHpBoost);
        displayMaterial = displayMaterial == null ? Material.SPAWNER : displayMaterial;
        itemModel = itemModel == null ? "" : itemModel.trim();
        displayParts = displayParts == null ? List.of() : List.copyOf(displayParts);
        overdrive = overdrive == null ? Overdrive.defaults() : overdrive;
    }

    record DisplayPart(Material material,
                       double offsetX,
                       double offsetY,
                       double offsetZ,
                       double scaleX,
                       double scaleY,
                       double scaleZ) {
        DisplayPart {
            material = material == null ? Material.SPAWNER : material;
            scaleX = Math.max(0.05, scaleX);
            scaleY = Math.max(0.05, scaleY);
            scaleZ = Math.max(0.05, scaleZ);
        }
    }

    record WeightedMonster(String monsterId, int weight) {
        WeightedMonster {
            monsterId = normalize(monsterId);
            weight = Math.max(0, weight);
        }
    }

    record Overdrive(int spawnChargeTicks,
                     int spawnCooldownTicks,
                     int jumpReadyTicks,
                     int jumpCooldownTicks,
                     int xBlastMoveTicks,
                     int xBlastChargeTicks,
                     int xBlastCooldownTicks,
                     int minSpawnCount,
                     int maxSpawnCount,
                     double shockwaveDamage,
                     double xBlastDamage,
                     List<WeightedMonster> spawnPool) {
        Overdrive {
            spawnChargeTicks = Math.max(1, spawnChargeTicks);
            spawnCooldownTicks = Math.max(1, spawnCooldownTicks);
            jumpReadyTicks = Math.max(1, jumpReadyTicks);
            jumpCooldownTicks = Math.max(1, jumpCooldownTicks);
            xBlastMoveTicks = Math.max(1, xBlastMoveTicks);
            xBlastChargeTicks = Math.max(1, xBlastChargeTicks);
            xBlastCooldownTicks = Math.max(1, xBlastCooldownTicks);
            minSpawnCount = Math.max(1, minSpawnCount);
            maxSpawnCount = Math.max(minSpawnCount, maxSpawnCount);
            shockwaveDamage = Math.max(0.0, shockwaveDamage);
            xBlastDamage = Math.max(0.0, xBlastDamage);
            spawnPool = spawnPool == null ? List.of() : List.copyOf(spawnPool);
        }

        static Overdrive defaults() {
            return new Overdrive(60, 140, 10, 20, 20, 40, 40,
                    3, 5, 6.0, 8.0,
                    List.of(
                            new WeightedMonster("splinter", 3),
                            new WeightedMonster("zombie", 3),
                            new WeightedMonster("vindicator", 2),
                            new WeightedMonster("husk", 2),
                            new WeightedMonster("hoglin_slow", 1),
                            new WeightedMonster("slime_medium", 1)
                    ));
        }
    }

    Map<String, Integer> weightedSpawnPool() {
        java.util.LinkedHashMap<String, Integer> pool = new java.util.LinkedHashMap<>();
        for (WeightedMonster monster : overdrive.spawnPool()) {
            if (!monster.monsterId().isBlank() && monster.weight() > 0) {
                pool.put(monster.monsterId(), monster.weight());
            }
        }
        return Map.copyOf(pool);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
