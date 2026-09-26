package krispasi.omGames.hallsofcarnage;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Sound;

record HallsBossType(
        String id,
        String name,
        String ai,
        double health,
        double multiplayerHpBoost,
        long directHitInvulnerabilityMillis,
        Material displayMaterial,
        String itemModel,
        double hitboxWidth,
        double hitboxHeight,
        double hitboxYOffset,
        List<DisplayPart> displayParts,
        Map<String, Animation> animations,
        Drops drops,
        Overdrive overdrive,
        ArchaicGuard archaicGuard
) {
    HallsBossType {
        id = normalize(id);
        name = name == null || name.isBlank() ? id : name;
        ai = normalize(ai).isBlank() ? id : normalize(ai);
        health = Math.max(1.0, health);
        multiplayerHpBoost = Math.max(1.0, multiplayerHpBoost);
        directHitInvulnerabilityMillis = Math.max(0L, directHitInvulnerabilityMillis);
        displayMaterial = displayMaterial == null ? Material.SPAWNER : displayMaterial;
        itemModel = itemModel == null ? "" : itemModel.trim();
        hitboxWidth = Math.max(0.5, hitboxWidth);
        hitboxHeight = Math.max(0.5, hitboxHeight);
        displayParts = displayParts == null ? List.of() : List.copyOf(displayParts);
        animations = animations == null ? Map.of() : Map.copyOf(animations);
        drops = drops == null ? Drops.defaults() : drops;
        overdrive = overdrive == null ? Overdrive.defaults() : overdrive;
        archaicGuard = archaicGuard == null ? ArchaicGuard.defaults() : archaicGuard;
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

    record ArchaicGuard(int missileAimTicks,
                        int missileLockTicks,
                        int missileCooldownTicks,
                        double missileDamage,
                        double missileRadius,
                        int shockwaveChargeTicks,
                        int shockwaveCooldownTicks,
                        int normalShockwaveMin,
                        int normalShockwaveMax,
                        int enragedShockwaveMin,
                        int enragedShockwaveMax,
                        double shockwaveDamage,
                        double shockwaveSpeedBlocksPerSecond,
                        int wallChargeTicks,
                        int wallGapTicks,
                        int wallCooldownTicks,
                        int normalWallMin,
                        int normalWallMax,
                        int enragedWallMin,
                        int enragedWallMax,
                        double wallDamage,
                        double wallSpeedBlocksPerSecond,
                        double normalWallSafeDegrees,
                        double enragedWallSafeDegrees,
                        int spawnRiseTicks,
                        int spawnCooldownTicks,
                        int spawnLockoutTicks,
                        int minSpawnCount,
                        int maxSpawnCount,
                        int repositionTicks,
                        int repositionCooldownTicks,
                        double repositionRadius,
                        double cloudRadius,
                        int cloudDurationTicks,
                        int cloudEffectTicks,
                        double phaseThreshold,
                        List<WeightedMonster> normalSpawnPool,
                        List<WeightedMonster> enragedSpawnPool) {
        ArchaicGuard {
            missileAimTicks = Math.max(1, missileAimTicks);
            missileLockTicks = Math.max(1, missileLockTicks);
            missileCooldownTicks = Math.max(1, missileCooldownTicks);
            missileDamage = Math.max(0.0, missileDamage);
            missileRadius = Math.max(0.5, missileRadius);
            shockwaveChargeTicks = Math.max(1, shockwaveChargeTicks);
            shockwaveCooldownTicks = Math.max(1, shockwaveCooldownTicks);
            normalShockwaveMin = Math.max(1, normalShockwaveMin);
            normalShockwaveMax = Math.max(normalShockwaveMin, normalShockwaveMax);
            enragedShockwaveMin = Math.max(1, enragedShockwaveMin);
            enragedShockwaveMax = Math.max(enragedShockwaveMin, enragedShockwaveMax);
            shockwaveDamage = Math.max(0.0, shockwaveDamage);
            shockwaveSpeedBlocksPerSecond = Math.max(0.5, shockwaveSpeedBlocksPerSecond);
            wallChargeTicks = Math.max(1, wallChargeTicks);
            wallGapTicks = Math.max(1, wallGapTicks);
            wallCooldownTicks = Math.max(1, wallCooldownTicks);
            normalWallMin = Math.max(1, normalWallMin);
            normalWallMax = Math.max(normalWallMin, normalWallMax);
            enragedWallMin = Math.max(1, enragedWallMin);
            enragedWallMax = Math.max(enragedWallMin, enragedWallMax);
            wallDamage = Math.max(0.0, wallDamage);
            wallSpeedBlocksPerSecond = Math.max(0.5, wallSpeedBlocksPerSecond);
            normalWallSafeDegrees = Math.max(1.0, normalWallSafeDegrees);
            enragedWallSafeDegrees = Math.max(1.0, enragedWallSafeDegrees);
            spawnRiseTicks = Math.max(1, spawnRiseTicks);
            spawnCooldownTicks = Math.max(1, spawnCooldownTicks);
            spawnLockoutTicks = Math.max(1, spawnLockoutTicks);
            minSpawnCount = Math.max(1, minSpawnCount);
            maxSpawnCount = Math.max(minSpawnCount, maxSpawnCount);
            repositionTicks = Math.max(1, repositionTicks);
            repositionCooldownTicks = Math.max(1, repositionCooldownTicks);
            repositionRadius = Math.max(1.0, repositionRadius);
            cloudRadius = Math.max(0.5, cloudRadius);
            cloudDurationTicks = Math.max(5, cloudDurationTicks);
            cloudEffectTicks = Math.max(20, cloudEffectTicks);
            phaseThreshold = Math.max(0.05, Math.min(0.95, phaseThreshold));
            normalSpawnPool = normalSpawnPool == null ? List.of() : List.copyOf(normalSpawnPool);
            enragedSpawnPool = enragedSpawnPool == null ? List.of() : List.copyOf(enragedSpawnPool);
        }

        static ArchaicGuard defaults() {
            return new ArchaicGuard(60, 20, 40, 10.0, 2.8,
                    60, 40, 1, 3, 3, 5, 6.0, 4.0,
                    60, 60, 100, 1, 3, 1, 3, 9.0, 4.0, 30.0, 24.0,
                    12, 60, 500, 1, 2,
                    40, 60, 9.0, 2.4, 100, 80, 0.33,
                    List.of(new WeightedMonster("bedrock_walker", 1)),
                    List.of(new WeightedMonster("rotting_soldier", 1)));
        }
    }

    Map<String, Integer> weightedSpawnPool() {
        return weightedPool(overdrive.spawnPool());
    }

    Map<String, Integer> archaicSpawnPool(boolean enraged) {
        return weightedPool(enraged ? archaicGuard.enragedSpawnPool() : archaicGuard.normalSpawnPool());
    }

    private static Map<String, Integer> weightedPool(List<WeightedMonster> monsters) {
        java.util.LinkedHashMap<String, Integer> pool = new java.util.LinkedHashMap<>();
        for (WeightedMonster monster : monsters) {
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
