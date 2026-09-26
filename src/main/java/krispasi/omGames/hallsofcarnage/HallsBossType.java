package krispasi.omGames.hallsofcarnage;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Sound;

record HallsBossType(
        String id,
        String name,
        double health,
        double multiplayerHpBoost,
        Material displayMaterial,
        String itemModel,
        List<DisplayPart> displayParts,
        Map<String, Animation> animations,
        Drops drops,
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
        animations = animations == null ? Map.of() : Map.copyOf(animations);
        drops = drops == null ? Drops.defaults() : drops;
        overdrive = overdrive == null ? Overdrive.defaults() : overdrive;
    }

    record DisplayPart(String id,
                       Material material,
                       double offsetX,
                       double offsetY,
                       double offsetZ,
                       double scaleX,
                       double scaleY,
                       double scaleZ) {
        DisplayPart {
            id = normalize(id).isBlank() ? "core" : normalize(id);
            material = material == null ? Material.SPAWNER : material;
            scaleX = Math.max(0.05, scaleX);
            scaleY = Math.max(0.05, scaleY);
            scaleZ = Math.max(0.05, scaleZ);
        }
    }

    record Animation(boolean loop,
                     List<Keyframe> frames,
                     Map<String, List<Keyframe>> partFrames) {
        Animation {
            frames = frames == null ? List.of() : frames.stream()
                    .sorted(java.util.Comparator.comparingInt(Keyframe::tick))
                    .toList();
            partFrames = partFrames == null ? Map.of() : Map.copyOf(partFrames);
        }
    }

    record Keyframe(int tick,
                    double offsetX,
                    double offsetY,
                    double offsetZ,
                    double yawOffset,
                    double scaleX,
                    double scaleY,
                    double scaleZ,
                    List<SoundCue> sounds) {
        Keyframe {
            tick = Math.max(0, tick);
            scaleX = scaleX <= 0.0 ? 1.0 : scaleX;
            scaleY = scaleY <= 0.0 ? 1.0 : scaleY;
            scaleZ = scaleZ <= 0.0 ? 1.0 : scaleZ;
            sounds = sounds == null ? List.of() : List.copyOf(sounds);
        }
    }

    record SoundCue(Sound sound, float volume, float pitch) {
        SoundCue {
            sound = sound == null ? Sound.BLOCK_NOTE_BLOCK_HAT : sound;
            volume = Math.max(0.0f, volume);
            pitch = Math.max(0.01f, pitch);
        }
    }

    record Drops(int randomScrap) {
        Drops {
            randomScrap = Math.max(0, randomScrap);
        }

        static Drops defaults() {
            return new Drops(0);
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
                     double shockwaveSpeedBlocksPerSecond,
                     int maxAliveMinions,
                     int minionRespawnCooldownTicks,
                     double lowHealthMinionCooldownMultiplier,
                     double initialScaleMultiplier,
                     double enragedScaleMultiplier,
                     int normalShockwaveChainMin,
                     int normalShockwaveChainMax,
                     int enragedShockwaveChainMin,
                     int enragedShockwaveChainMax,
                     int enragedXBlastChains,
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
            shockwaveSpeedBlocksPerSecond = Math.max(0.5, shockwaveSpeedBlocksPerSecond);
            maxAliveMinions = Math.max(0, maxAliveMinions);
            minionRespawnCooldownTicks = Math.max(1, minionRespawnCooldownTicks);
            lowHealthMinionCooldownMultiplier = Math.max(1.0, lowHealthMinionCooldownMultiplier);
            initialScaleMultiplier = Math.max(0.05, initialScaleMultiplier);
            enragedScaleMultiplier = Math.max(0.05, enragedScaleMultiplier);
            normalShockwaveChainMin = Math.max(1, normalShockwaveChainMin);
            normalShockwaveChainMax = Math.max(normalShockwaveChainMin, normalShockwaveChainMax);
            enragedShockwaveChainMin = Math.max(1, enragedShockwaveChainMin);
            enragedShockwaveChainMax = Math.max(enragedShockwaveChainMin, enragedShockwaveChainMax);
            enragedXBlastChains = Math.max(1, enragedXBlastChains);
            xBlastDamage = Math.max(0.0, xBlastDamage);
            spawnPool = spawnPool == null ? List.of() : List.copyOf(spawnPool);
        }

        static Overdrive defaults() {
            return new Overdrive(60, 140, 10, 20, 20, 40, 40,
                    3, 5, 6.0, 4.0,
                    3, 300, 2.0, 0.8, 1.0,
                    2, 4, 3, 5, 3,
                    8.0,
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
