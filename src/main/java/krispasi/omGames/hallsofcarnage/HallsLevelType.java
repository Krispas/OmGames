package krispasi.omGames.hallsofcarnage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Material;

public record HallsLevelType(
        String id,
        String name,
        String corridorGeneration,
        Material floor,
        Material ceiling,
        Material corridorFloor,
        Material corridorCeiling,
        Material light,
        List<BlockPalette> walls,
        List<BlockPalette> pillars,
        List<String> commonMonsters,
        List<String> specialMonsters,
        double vegetationChance,
        List<VegetationEntry> vegetation,
        LiquidSettings liquid
) {
    public HallsLevelType {
        commonMonsters = List.copyOf(commonMonsters);
        specialMonsters = List.copyOf(specialMonsters);
        vegetationChance = Math.max(0.0, Math.min(1.0, vegetationChance));
        vegetation = List.copyOf(vegetation);
        liquid = liquid == null ? LiquidSettings.none() : liquid;
    }

    public static HallsLevelType fallback(String id) {
        String normalizedId = id == null || id.isBlank() ? "howling_corridors" : id;
        if (normalizedId.equals("frozen_halls")) {
            return new HallsLevelType(
                    normalizedId,
                    "Frozen Halls",
                    "cave",
                    Material.PACKED_ICE,
                    Material.BLUE_ICE,
                    Material.PACKED_ICE,
                    Material.BLUE_ICE,
                    Material.PEARLESCENT_FROGLIGHT,
                    List.of(
                            new BlockPalette(Material.POLISHED_DIORITE, Material.ICE, 0.10),
                            new BlockPalette(Material.TUFF_BRICKS, Material.BLUE_ICE, 0.06)
                    ),
                    List.of(new BlockPalette(Material.PACKED_ICE, Material.BLUE_ICE, 0.12)),
                    List.of("stray", "zombie"),
                    List.of("bogged"),
                    0.01,
                    List.of(new VegetationEntry("deadbush", 1)),
                    LiquidSettings.none()
            );
        }
        if (normalizedId.equals("deep_crypt")) {
            return new HallsLevelType(
                    normalizedId,
                    "Deep Crypt",
                    "maze",
                    Material.SMOOTH_SANDSTONE,
                    Material.CHISELED_SANDSTONE,
                    Material.CUT_SANDSTONE,
                    Material.CHISELED_SANDSTONE,
                    Material.OCHRE_FROGLIGHT,
                    List.of(
                            new BlockPalette(Material.SANDSTONE, Material.CHISELED_SANDSTONE, 0.08),
                            new BlockPalette(Material.RED_SANDSTONE, Material.CHISELED_RED_SANDSTONE, 0.06)
                    ),
                    List.of(new BlockPalette(Material.CUT_SANDSTONE, Material.CHISELED_SANDSTONE, 0.10)),
                    List.of("husk", "skeleton"),
                    List.of("breeze"),
                    0.035,
                    List.of(new VegetationEntry("deadbush", 3), new VegetationEntry("dry_grass", 2)),
                    LiquidSettings.none()
            );
        }
        if (normalizedId.equals("infernal_chambers")) {
            return new HallsLevelType(
                    normalizedId,
                    "Infernal Chambers",
                    "large_corridors",
                    Material.CRACKED_POLISHED_BLACKSTONE_BRICKS,
                    Material.POLISHED_BLACKSTONE_BRICKS,
                    Material.BLACKSTONE,
                    Material.POLISHED_BLACKSTONE_BRICKS,
                    Material.SHROOMLIGHT,
                    List.of(
                            new BlockPalette(Material.POLISHED_BLACKSTONE_BRICKS, Material.MAGMA_BLOCK, 0.08),
                            new BlockPalette(Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, 0.10)
                    ),
                    List.of(new BlockPalette(Material.BASALT, Material.POLISHED_BASALT, 0.12)),
                    List.of("piglin", "blaze", "breeze", "husk"),
                    List.of("piglin_brute", "wither_skeleton", "parched"),
                    0.008,
                    List.of(new VegetationEntry("deadbush", 1)),
                    LiquidSettings.none()
            );
        }
        if (normalizedId.equals("factory")) {
            return new HallsLevelType(
                    normalizedId,
                    "Factory",
                    "open_halls",
                    Material.SMOOTH_STONE,
                    Material.IRON_BLOCK,
                    Material.POLISHED_ANDESITE,
                    Material.IRON_BLOCK,
                    Material.REDSTONE_LAMP,
                    List.of(
                            new BlockPalette(Material.IRON_BLOCK, Material.COPPER_BLOCK, 0.08),
                            new BlockPalette(Material.POLISHED_ANDESITE, Material.SMOOTH_STONE, 0.12)
                    ),
                    List.of(new BlockPalette(Material.DEEPSLATE_TILES, Material.COPPER_BLOCK, 0.08)),
                    List.of("zombie", "skeleton", "pillager", "slime_medium"),
                    List.of("breeze", "creaking"),
                    0.0,
                    List.of(),
                    LiquidSettings.none()
            );
        }
        if (normalizedId.equals("backrooms")) {
            return new HallsLevelType(
                    normalizedId,
                    "Backrooms",
                    "backrooms",
                    Material.YELLOW_TERRACOTTA,
                    Material.SMOOTH_SANDSTONE,
                    Material.YELLOW_TERRACOTTA,
                    Material.SMOOTH_SANDSTONE,
                    Material.OCHRE_FROGLIGHT,
                    List.of(
                            new BlockPalette(Material.YELLOW_TERRACOTTA, Material.STRIPPED_BIRCH_WOOD, 0.06),
                            new BlockPalette(Material.END_STONE_BRICKS, Material.SMOOTH_SANDSTONE, 0.08)
                    ),
                    List.of(new BlockPalette(Material.STRIPPED_BIRCH_WOOD, Material.YELLOW_TERRACOTTA, 0.10)),
                    List.of("zombie", "skeleton", "silverfish", "creaking"),
                    List.of("breeze", "witch"),
                    0.004,
                    List.of(new VegetationEntry("deadbush", 1)),
                    LiquidSettings.none()
            );
        }
        if (normalizedId.equals("sewer")) {
            return new HallsLevelType(
                    normalizedId,
                    "Sewer",
                    "sewer",
                    Material.MUD_BRICKS,
                    Material.DEEPSLATE_BRICKS,
                    Material.MUD_BRICKS,
                    Material.DEEPSLATE_BRICKS,
                    Material.SEA_LANTERN,
                    List.of(
                            new BlockPalette(Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, 0.08),
                            new BlockPalette(Material.DEEPSLATE_BRICKS, Material.MOSSY_COBBLESTONE, 0.10)
                    ),
                    List.of(new BlockPalette(Material.MOSSY_COBBLESTONE, Material.COBBLESTONE, 0.08)),
                    List.of("drowned", "zombie", "slime_medium", "silverfish", "creaking"),
                    List.of("drowned", "bogged", "cave_spider", "warden"),
                    0.015,
                    List.of(new VegetationEntry("bush", 1)),
                    new LiquidSettings(true, Material.WATER, 0.95)
            );
        }
        return new HallsLevelType(
                normalizedId,
                "Howling Corridors",
                "normal",
                Material.PACKED_MUD,
                Material.TUFF_BRICKS,
                Material.PACKED_MUD,
                Material.DEEPSLATE_BRICKS,
                Material.SEA_LANTERN,
                List.of(
                        new BlockPalette(Material.DEEPSLATE_BRICKS, Material.DEEPSLATE, 0.08),
                        new BlockPalette(Material.COBBLED_DEEPSLATE, Material.DEEPSLATE, 0.08)
                ),
                List.of(new BlockPalette(Material.REINFORCED_DEEPSLATE, null, 0.0)),
                List.of("zombie", "creeper", "creaking", "slime_medium"),
                List.of("zombie_vanguard", "skeleton", "cave_spider"),
                0.04,
                List.of(new VegetationEntry("grass", 4), new VegetationEntry("bush", 1)),
                LiquidSettings.none()
        );
    }

    public BlockPalette wallPalette(Random random) {
        return pickPalette(walls, random, BlockPalette.fallbackWall());
    }

    public BlockPalette pillarPalette(Random random) {
        return pickPalette(pillars, random, BlockPalette.fallbackPillar());
    }

    private BlockPalette pickPalette(List<BlockPalette> palettes, Random random, BlockPalette fallback) {
        if (palettes == null || palettes.isEmpty()) {
            return fallback;
        }
        return palettes.get(random.nextInt(palettes.size()));
    }

    public record BlockPalette(Material block, Map<Material, Double> specialBlocks) {
        public BlockPalette(Material block, Material specialBlock, double specialChance) {
            this(block, specialBlock == null || specialChance <= 0.0 ? Map.of() : Map.of(specialBlock, specialChance));
        }

        public BlockPalette {
            specialBlocks = specialBlocks == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(specialBlocks));
        }

        public static BlockPalette fallbackWall() {
            return new BlockPalette(Material.DEEPSLATE_BRICKS, Material.DEEPSLATE, 0.08);
        }

        public static BlockPalette fallbackPillar() {
            return new BlockPalette(Material.REINFORCED_DEEPSLATE, null, 0.0);
        }

        public Material material(Random random) {
            double roll = random.nextDouble();
            double cumulative = 0.0;
            for (Map.Entry<Material, Double> entry : specialBlocks.entrySet()) {
                if (entry.getKey() == null || entry.getValue() <= 0.0) {
                    continue;
                }
                cumulative += entry.getValue();
                if (roll < cumulative) {
                    return entry.getKey();
                }
            }
            return block;
        }
    }

    public record VegetationEntry(String id, int weight) {
        public VegetationEntry {
            id = id == null ? "" : id;
            weight = Math.max(0, weight);
        }
    }

    public record LiquidSettings(boolean enabled, Material material, double roomCoverage) {
        public LiquidSettings {
            material = material == null || !material.isBlock() ? Material.WATER : material;
            roomCoverage = Math.max(0.0, Math.min(1.0, roomCoverage));
        }

        public static LiquidSettings none() {
            return new LiquidSettings(false, Material.WATER, 0.0);
        }
    }
}
