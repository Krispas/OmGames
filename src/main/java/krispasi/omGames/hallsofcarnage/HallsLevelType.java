package krispasi.omGames.hallsofcarnage;

import java.util.List;
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
        List<BlockPalette> pillars
) {
    public static HallsLevelType fallback(String id) {
        String normalizedId = id == null || id.isBlank() ? "howling_corridors" : id;
        if (normalizedId.equals("frozen_halls")) {
            return new HallsLevelType(
                    normalizedId,
                    "Frozen Halls",
                    "normal",
                    Material.PACKED_ICE,
                    Material.BLUE_ICE,
                    Material.PACKED_ICE,
                    Material.BLUE_ICE,
                    Material.PEARLESCENT_FROGLIGHT,
                    List.of(
                            new BlockPalette(Material.POLISHED_DIORITE, Material.ICE, 0.10),
                            new BlockPalette(Material.TUFF_BRICKS, Material.BLUE_ICE, 0.06)
                    ),
                    List.of(new BlockPalette(Material.PACKED_ICE, Material.BLUE_ICE, 0.12))
            );
        }
        if (normalizedId.equals("deep_crypt")) {
            return new HallsLevelType(
                    normalizedId,
                    "Deep Crypt",
                    "normal",
                    Material.SMOOTH_SANDSTONE,
                    Material.CHISELED_SANDSTONE,
                    Material.CUT_SANDSTONE,
                    Material.CHISELED_SANDSTONE,
                    Material.OCHRE_FROGLIGHT,
                    List.of(
                            new BlockPalette(Material.SANDSTONE, Material.CHISELED_SANDSTONE, 0.08),
                            new BlockPalette(Material.RED_SANDSTONE, Material.CHISELED_RED_SANDSTONE, 0.06)
                    ),
                    List.of(new BlockPalette(Material.CUT_SANDSTONE, Material.CHISELED_SANDSTONE, 0.10))
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
                List.of(new BlockPalette(Material.REINFORCED_DEEPSLATE, null, 0.0))
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

    public record BlockPalette(Material block, Material specialBlock, double specialChance) {
        public static BlockPalette fallbackWall() {
            return new BlockPalette(Material.DEEPSLATE_BRICKS, Material.DEEPSLATE, 0.08);
        }

        public static BlockPalette fallbackPillar() {
            return new BlockPalette(Material.REINFORCED_DEEPSLATE, null, 0.0);
        }

        public Material material(Random random) {
            if (specialBlock != null && specialChance > 0.0 && random.nextDouble() < specialChance) {
                return specialBlock;
            }
            return block;
        }
    }
}
