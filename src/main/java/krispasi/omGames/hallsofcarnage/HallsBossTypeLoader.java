package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
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
                config.getDouble("health", 150.0),
                config.getDouble("multiplayer-hp-boost", 1.3),
                material(config.getString("display.material"), Material.SPAWNER),
                config.getString("display.item-model", ""),
                displayParts(config.getConfigurationSection("display.parts")),
                animations(config.getConfigurationSection("animations")),
                overdrive(config.getConfigurationSection("overdrive"))
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
                    row.getDouble("scale.z", row.getDouble("scale-z", 1.0))
            ));
        }
        return List.copyOf(frames);
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
                spawnPool(section.getConfigurationSection("spawn.pool"))
        );
    }

    private static List<HallsBossType.WeightedMonster> spawnPool(ConfigurationSection section) {
        if (section == null) {
            return HallsBossType.Overdrive.defaults().spawnPool();
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

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
