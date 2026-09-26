package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class HallsBossTypeLoader {
    private HallsBossTypeLoader() {
    }

    static Map<String, HallsBossType> loadBossTypes(JavaPlugin plugin, File folder) {
        if (folder == null || !folder.exists()) {
            return Map.of();
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            return Map.of();
        }
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        Map<String, HallsBossType> types = new LinkedHashMap<>();
        for (File file : sorted) {
            try {
                HallsBossType type = loadBossType(file);
                if (!type.id().isBlank()) {
                    types.put(type.id(), type);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Failed to load Halls boss " + file + ": " + ex.getMessage());
            }
        }
        return Map.copyOf(types);
    }

    private static HallsBossType loadBossType(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String fallbackId = file.getName().replaceFirst("\\.[^.]+$", "");
        return new HallsBossType(
                normalizeId(config.getString("id", fallbackId)),
                config.getString("name", fallbackId),
                config.getString("ai", fallbackId),
                config.getDouble("health", 150.0),
                config.getDouble("multiplayer-hp-boost", 1.3),
                directHitInvulnerabilityMillis(config),
                material(config.getString("display.material"), Material.SPAWNER),
                config.getString("display.item-model", ""),
                config.getDouble("display.hitbox.width", 5.0),
                config.getDouble("display.hitbox.height", 5.0),
                config.getDouble("display.hitbox.y-offset", 0.1),
                displayParts(config.getConfigurationSection("display.parts")),
                animations(config.getConfigurationSection("animations")),
                drops(config.getConfigurationSection("drops")),
                overdrive(config.getConfigurationSection("overdrive")),
                archaicGuard(config.getConfigurationSection("archaic-guard"))
        );
    }

    private static List<HallsBossType.DisplayPart> displayParts(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<HallsBossType.DisplayPart> parts = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            parts.add(new HallsBossType.DisplayPart(
                    normalizeId(key),
                    material(row.getString("material"), Material.SPAWNER),
                    row.getDouble("offset.x", row.getDouble("offset-x", 0.0)),
                    row.getDouble("offset.y", row.getDouble("offset-y", 0.0)),
                    row.getDouble("offset.z", row.getDouble("offset-z", 0.0)),
                    row.getDouble("scale.x", row.getDouble("scale-x", 1.0)),
                    row.getDouble("scale.y", row.getDouble("scale-y", 1.0)),
                    row.getDouble("scale.z", row.getDouble("scale-z", 1.0))
            ));
        }
        return List.copyOf(parts);
    }

    private static Map<String, HallsBossType.Animation> animations(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, HallsBossType.Animation> animations = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            Map<String, List<HallsBossType.Keyframe>> partFrames = new LinkedHashMap<>();
            ConfigurationSection parts = row.getConfigurationSection("parts");
            if (parts != null) {
                for (String partKey : parts.getKeys(false)) {
                    ConfigurationSection part = parts.getConfigurationSection(partKey);
                    if (part != null) {
                        partFrames.put(normalizeId(partKey), keyframes(part.getConfigurationSection("frames")));
                    }
                }
            }
            animations.put(normalizeId(key), new HallsBossType.Animation(
                    row.getBoolean("loop", false),
                    keyframes(row.getConfigurationSection("frames")),
                    partFrames
            ));
        }
        return Map.copyOf(animations);
    }

    private static List<HallsBossType.Keyframe> keyframes(ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        List<HallsBossType.Keyframe> frames = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            frames.add(new HallsBossType.Keyframe(
                    row.getInt("tick", parseTickKey(key)),
                    row.getDouble("offset.x", row.getDouble("offset-x", 0.0)),
                    row.getDouble("offset.y", row.getDouble("offset-y", 0.0)),
                    row.getDouble("offset.z", row.getDouble("offset-z", 0.0)),
                    row.getDouble("yaw-offset", row.getDouble("yaw", 0.0)),
                    row.getDouble("scale.x", row.getDouble("scale-x", 1.0)),
                    row.getDouble("scale.y", row.getDouble("scale-y", 1.0)),
                    row.getDouble("scale.z", row.getDouble("scale-z", 1.0)),
                    sounds(row)
            ));
        }
        return List.copyOf(frames);
    }

    private static List<HallsBossType.SoundCue> sounds(ConfigurationSection frame) {
        if (frame == null) {
            return List.of();
        }
        List<HallsBossType.SoundCue> sounds = new ArrayList<>();
        HallsBossType.SoundCue direct = soundCue(frame, "sound", "volume", "pitch");
        if (direct != null) {
            sounds.add(direct);
        }
        if (frame.isList("sounds")) {
            for (Map<?, ?> raw : frame.getMapList("sounds")) {
                Sound sound = sound(raw.get("sound"));
                if (sound == null) {
                    continue;
                }
                sounds.add(new HallsBossType.SoundCue(
                        sound,
                        floatValue(raw.get("volume"), 1.0f),
                        floatValue(raw.get("pitch"), 1.0f)
                ));
            }
        } else {
            ConfigurationSection section = frame.getConfigurationSection("sounds");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    ConfigurationSection row = section.getConfigurationSection(key);
                    if (row == null) {
                        continue;
                    }
                    HallsBossType.SoundCue cue = soundCue(row, "sound", "volume", "pitch");
                    if (cue != null) {
                        sounds.add(cue);
                    }
                }
            }
        }
        return List.copyOf(sounds);
    }

    private static HallsBossType.SoundCue soundCue(ConfigurationSection section,
                                                   String soundPath,
                                                   String volumePath,
                                                   String pitchPath) {
        Sound sound = sound(section.getString(soundPath));
        if (sound == null) {
            return null;
        }
        return new HallsBossType.SoundCue(
                sound,
                (float) section.getDouble(volumePath, 1.0),
                (float) section.getDouble(pitchPath, 1.0)
        );
    }

    private static int parseTickKey(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static HallsBossType.Overdrive overdrive(ConfigurationSection section) {
        HallsBossType.Overdrive defaults = HallsBossType.Overdrive.defaults();
        if (section == null) {
            return defaults;
        }
        return new HallsBossType.Overdrive(
                seconds(section, "spawn.charge-seconds", defaults.spawnChargeTicks()),
                seconds(section, "spawn.cooldown-seconds", defaults.spawnCooldownTicks()),
                seconds(section, "jump.ready-seconds", defaults.jumpReadyTicks()),
                seconds(section, "jump.cooldown-seconds", defaults.jumpCooldownTicks()),
                seconds(section, "x-blast.move-seconds", defaults.xBlastMoveTicks()),
                seconds(section, "x-blast.charge-seconds", defaults.xBlastChargeTicks()),
                seconds(section, "x-blast.cooldown-seconds", defaults.xBlastCooldownTicks()),
                Math.max(1, section.getInt("spawn.count.min", defaults.minSpawnCount())),
                Math.max(1, section.getInt("spawn.count.max", defaults.maxSpawnCount())),
                section.getDouble("jump.shockwave-damage", defaults.shockwaveDamage()),
                section.getDouble("jump.shockwave-speed-blocks-per-second", defaults.shockwaveSpeedBlocksPerSecond()),
                Math.max(0, section.getInt("spawn.max-alive", defaults.maxAliveMinions())),
                seconds(section, "spawn.minion-respawn-cooldown-seconds", defaults.minionRespawnCooldownTicks()),
                section.getDouble("spawn.low-health-cooldown-multiplier", defaults.lowHealthMinionCooldownMultiplier()),
                section.getDouble("phase.initial-scale-multiplier", defaults.initialScaleMultiplier()),
                section.getDouble("phase.enraged-scale-multiplier", defaults.enragedScaleMultiplier()),
                Math.max(1, section.getInt("jump.chain.normal.min", defaults.normalShockwaveChainMin())),
                Math.max(1, section.getInt("jump.chain.normal.max", defaults.normalShockwaveChainMax())),
                Math.max(1, section.getInt("jump.chain.enraged.min", defaults.enragedShockwaveChainMin())),
                Math.max(1, section.getInt("jump.chain.enraged.max", defaults.enragedShockwaveChainMax())),
                Math.max(1, section.getInt("x-blast.enraged-chains", defaults.enragedXBlastChains())),
                section.getDouble("x-blast.damage", defaults.xBlastDamage()),
                spawnPool(section.getConfigurationSection("spawn.pool"), HallsBossType.Overdrive.defaults().spawnPool())
        );
    }

    private static HallsBossType.Drops drops(ConfigurationSection section) {
        HallsBossType.Drops defaults = HallsBossType.Drops.defaults();
        if (section == null) {
            return defaults;
        }
        return new HallsBossType.Drops(section.getInt("random-scrap", defaults.randomScrap()));
    }

    private static HallsBossType.ArchaicGuard archaicGuard(ConfigurationSection section) {
        HallsBossType.ArchaicGuard defaults = HallsBossType.ArchaicGuard.defaults();
        if (section == null) {
            return defaults;
        }
        return new HallsBossType.ArchaicGuard(
                seconds(section, "missile.aim-seconds", defaults.missileAimTicks()),
                seconds(section, "missile.lock-seconds", defaults.missileLockTicks()),
                seconds(section, "missile.cooldown-seconds", defaults.missileCooldownTicks()),
                section.getDouble("missile.damage", defaults.missileDamage()),
                section.getDouble("missile.radius", defaults.missileRadius()),
                seconds(section, "shockwave.charge-seconds", defaults.shockwaveChargeTicks()),
                seconds(section, "shockwave.cooldown-seconds", defaults.shockwaveCooldownTicks()),
                Math.max(1, section.getInt("shockwave.chain.normal.min", defaults.normalShockwaveMin())),
                Math.max(1, section.getInt("shockwave.chain.normal.max", defaults.normalShockwaveMax())),
                Math.max(1, section.getInt("shockwave.chain.enraged.min", defaults.enragedShockwaveMin())),
                Math.max(1, section.getInt("shockwave.chain.enraged.max", defaults.enragedShockwaveMax())),
                section.getDouble("shockwave.damage", defaults.shockwaveDamage()),
                section.getDouble("shockwave.speed-blocks-per-second", defaults.shockwaveSpeedBlocksPerSecond()),
                seconds(section, "walls.charge-seconds", defaults.wallChargeTicks()),
                seconds(section, "walls.gap-seconds", defaults.wallGapTicks()),
                seconds(section, "walls.cooldown-seconds", defaults.wallCooldownTicks()),
                Math.max(1, section.getInt("walls.chain.normal.min", defaults.normalWallMin())),
                Math.max(1, section.getInt("walls.chain.normal.max", defaults.normalWallMax())),
                Math.max(1, section.getInt("walls.chain.enraged.min", defaults.enragedWallMin())),
                Math.max(1, section.getInt("walls.chain.enraged.max", defaults.enragedWallMax())),
                section.getDouble("walls.damage", defaults.wallDamage()),
                section.getDouble("walls.speed-blocks-per-second", defaults.wallSpeedBlocksPerSecond()),
                section.getDouble("walls.safe-degrees.normal", defaults.normalWallSafeDegrees()),
                section.getDouble("walls.safe-degrees.enraged", defaults.enragedWallSafeDegrees()),
                seconds(section, "spawn.rise-seconds", defaults.spawnRiseTicks()),
                seconds(section, "spawn.cooldown-seconds", defaults.spawnCooldownTicks()),
                seconds(section, "spawn.lockout-seconds", defaults.spawnLockoutTicks()),
                Math.max(1, section.getInt("spawn.count.min", defaults.minSpawnCount())),
                Math.max(1, section.getInt("spawn.count.max", defaults.maxSpawnCount())),
                seconds(section, "reposition.move-seconds", defaults.repositionTicks()),
                seconds(section, "reposition.cooldown-seconds", defaults.repositionCooldownTicks()),
                section.getDouble("reposition.radius", defaults.repositionRadius()),
                section.getDouble("reposition.cloud-radius", defaults.cloudRadius()),
                seconds(section, "reposition.cloud-duration-seconds", defaults.cloudDurationTicks()),
                seconds(section, "reposition.cloud-effect-seconds", defaults.cloudEffectTicks()),
                section.getDouble("phase.threshold", defaults.phaseThreshold()),
                spawnPool(section.getConfigurationSection("spawn.pool.normal"), defaults.normalSpawnPool()),
                spawnPool(section.getConfigurationSection("spawn.pool.enraged"), defaults.enragedSpawnPool())
        );
    }

    private static long directHitInvulnerabilityMillis(YamlConfiguration config) {
        if (config.contains("direct-hit-invulnerability-millis")) {
            return Math.max(0L, config.getLong("direct-hit-invulnerability-millis"));
        }
        if (config.contains("direct-hit-invulnerability-seconds")) {
            return Math.max(0L, Math.round(config.getDouble("direct-hit-invulnerability-seconds") * 1000.0));
        }
        return 500L;
    }

    private static List<HallsBossType.WeightedMonster> spawnPool(ConfigurationSection section,
                                                                 List<HallsBossType.WeightedMonster> fallback) {
        if (section == null) {
            return fallback == null ? List.of() : fallback;
        }
        List<HallsBossType.WeightedMonster> pool = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            pool.add(new HallsBossType.WeightedMonster(normalizeId(key), section.getInt(key, 0)));
        }
        return List.copyOf(pool);
    }

    private static int seconds(ConfigurationSection section, String path, int fallbackTicks) {
        if (!section.contains(path)) {
            return fallbackTicks;
        }
        return Math.max(1, (int) Math.round(section.getDouble(path) * 20.0));
    }

    private static Material material(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private static Sound sound(Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(raw.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static float floatValue(Object raw, float fallback) {
        if (raw instanceof Number number) {
            return number.floatValue();
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
