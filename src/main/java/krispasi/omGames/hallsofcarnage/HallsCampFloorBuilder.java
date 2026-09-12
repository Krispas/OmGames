package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public void build(HallsCampLayout layout,
                      int roomStartX,
                      int y,
                      int roomStartZ,
                      HallsLevelType levelType,
                      int northOpeningX) {
        buildRoom(layout, roomStartX, y, roomStartZ, levelType, northOpeningX);
        renderCampPlots(layout, roomStartX, y, roomStartZ);
    }

    private void buildRoom(HallsCampLayout layout,
                           int startX,
                           int y,
                           int startZ,
                           HallsLevelType levelType,
                           int northOpeningX) {
        for (int z = -1; z <= layout.depth(); z++) {
            for (int x = -1; x <= layout.width(); x++) {
                boolean border = x < 0 || z < 0 || x >= layout.width() || z >= layout.depth();
                boolean opening = border && z == -1 && x >= 0 && x < layout.width()
                        && Math.abs(x - northOpeningX) <= 1;
                boolean wall = !opening && (border || layout.at(x, z) == 'X');
                int blockX = startX + x;
                int blockZ = startZ + z;
                Material wallMaterial = wallMaterial(levelType, blockX, blockZ, pillarColumn(layout, x, z, border));
                blockPlacer.setBlock(blockX, y - 1, blockZ, wall ? wallMaterial : levelType.floor());
                blockPlacer.setBlock(blockX, y + ROOM_HEIGHT, blockZ, levelType.ceiling());
                for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                    boolean openingHeader = opening && dy >= 3;
                    blockPlacer.setBlock(blockX, y + dy, blockZ, wall || openingHeader ? wallMaterial : Material.AIR);
                }
            }
        }
        placeLights(layout, startX, y, startZ, levelType);
    }

    private boolean pillarColumn(HallsCampLayout layout, int x, int z, boolean border) {
        if (border) {
            return (x < 0 || x >= layout.width()) && (z < 0 || z >= layout.depth());
        }
        if (layout.at(x, z) != 'X') {
            return false;
        }
        boolean northOpen = layout.openAt(x, z - 1);
        boolean southOpen = layout.openAt(x, z + 1);
        boolean eastOpen = layout.openAt(x + 1, z);
        boolean westOpen = layout.openAt(x - 1, z);
        return (northOpen || southOpen) && (eastOpen || westOpen);
    }

    private void placeLights(HallsCampLayout layout, int startX, int y, int startZ, HallsLevelType levelType) {
        List<int[]> candidates = new ArrayList<>();
        for (int z = 1; z < layout.depth() - 1; z++) {
            for (int x = 1; x < layout.width() - 1; x++) {
                if (layout.openAt(x, z)) {
                    candidates.add(new int[]{x, z});
                }
            }
        }
        if (candidates.isEmpty()) {
            for (int z = 0; z < layout.depth(); z++) {
                for (int x = 0; x < layout.width(); x++) {
                    if (layout.openAt(x, z)) {
                        candidates.add(new int[]{x, z});
                    }
                }
            }
        }
        Collections.shuffle(candidates, new Random((((long) startX) << 32) ^ startZ ^ 0xCA9E1L));
        int target = Math.max(1, Math.min(5, Math.max(1, candidates.size()) / 28));
        for (int i = 0; i < Math.min(target, candidates.size()); i++) {
            int[] cell = candidates.get(i);
            blockPlacer.setBlock(startX + cell[0], y + ROOM_HEIGHT, startZ + cell[1], levelType.light());
        }
    }

    private Material wallMaterial(HallsLevelType levelType, int x, int z, boolean pillar) {
        int groupX = Math.floorDiv(x, 7);
        int groupZ = Math.floorDiv(z, 7);
        Random paletteRandom = new Random((((long) groupX) * 341873128712L)
                ^ (((long) groupZ) * 132897987541L)
                ^ 0xCA4F);
        HallsLevelType.BlockPalette palette = pillar
                ? levelType.pillarPalette(paletteRandom)
                : levelType.wallPalette(paletteRandom);
        Random columnRandom = new Random((((long) x) * 341873128712L)
                ^ (((long) z) * 132897987541L)
                ^ 0x51EC1A7EL);
        return palette.material(columnRandom);
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
