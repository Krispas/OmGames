package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.bukkit.Material;

public final class HallsCampFloorBuilder {
    public interface BlockPlacer {
        void setBlock(int x, int y, int z, Material material);
    }

    private static final int ROOM_HEIGHT = 5;

    private final BlockPlacer blockPlacer;
    private final HallsCampRuntime campRuntime;

    public HallsCampFloorBuilder(BlockPlacer blockPlacer,
                                 HallsCampRuntime campRuntime) {
        this.blockPlacer = blockPlacer;
        this.campRuntime = campRuntime;
    }

    public static HallsCampLayout load(File dataFolder, HallsScenario.FloorDefinition floorDefinition) throws IOException {
        String layoutPath = floorDefinition.layout().isBlank() ? "camps/camp_1.txt" : floorDefinition.layout();
        if (layoutPath.startsWith("level/")) {
            layoutPath = layoutPath.substring("level/".length());
        }
        return HallsCampLayoutLoader.load(new File(dataFolder, "level/" + layoutPath));
    }

    public void build(HallsCampLayout layout, int roomStartX, int y, int roomStartZ, HallsLevelType levelType) {
        buildRoom(layout, roomStartX, y, roomStartZ, levelType);
        renderCampPlots(layout, roomStartX, y, roomStartZ);
    }

    private void buildRoom(HallsCampLayout layout, int startX, int y, int startZ, HallsLevelType levelType) {
        for (int z = -1; z <= layout.depth(); z++) {
            for (int x = -1; x <= layout.width(); x++) {
                boolean border = x < 0 || z < 0 || x >= layout.width() || z >= layout.depth();
                boolean opening = border && z == -1 && x == layout.width() / 2;
                boolean wall = !opening && (border || layout.at(x, z) == 'X');
                int blockX = startX + x;
                int blockZ = startZ + z;
                Material wallMaterial = wallMaterial(levelType, blockX, blockZ);
                blockPlacer.setBlock(blockX, y - 1, blockZ, wall ? wallMaterial : levelType.floor());
                blockPlacer.setBlock(blockX, y + ROOM_HEIGHT, blockZ, levelType.ceiling());
                for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                    blockPlacer.setBlock(blockX, y + dy, blockZ, wall ? wallMaterial : Material.AIR);
                }
            }
        }
        blockPlacer.setBlock(startX + layout.width() / 2, y + ROOM_HEIGHT, startZ + layout.depth() / 2, levelType.light());
    }

    private Material wallMaterial(HallsLevelType levelType, int x, int z) {
        Random random = new Random((x * 341873128712L) ^ (z * 132897987541L) ^ 0xCA4F);
        return levelType.wallPalette(random).material(random);
    }

    private void renderCampPlots(HallsCampLayout layout, int roomStartX, int y, int roomStartZ) {
        for (int z = 0; z < layout.depth(); z++) {
            for (int x = 0; x < layout.width(); x++) {
                char cell = layout.at(x, z);
                if (cell == 'C' || cell == 'N' || cell == 'S' || cell == 'E' || cell == 'W') {
                    blockPlacer.setBlock(roomStartX + x, y - 1, roomStartZ + z, Material.OAK_PLANKS);
                }
            }
        }
        for (HallsCampLayout.BuildSpot spot : layout.buildSpots()) {
            campRuntime.addPlot(roomStartX + spot.centerX(), y, roomStartZ + spot.centerZ(), spot);
        }
    }
}
