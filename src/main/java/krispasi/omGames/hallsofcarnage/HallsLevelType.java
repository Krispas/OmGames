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
        return new HallsLevelType(
                id == null || id.isBlank() ? "howling_corridors" : id,
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
