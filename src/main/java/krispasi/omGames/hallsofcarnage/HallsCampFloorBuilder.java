package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

public final class HallsCampFloorBuilder {
    public interface BlockPlacer {
        void setBlock(int x, int y, int z, Material material, BlockFace facing);

        default void setBlock(int x, int y, int z, Material material) {
            setBlock(x, y, z, material, null);
        }

        default Material wallMaterial(HallsLevelType levelType, int x, int y, int z, boolean pillar) {
            Random random = new Random((((long) x) * 341873128712L)
                    ^ (((long) y) * 42317861L)
                    ^ (((long) z) * 132897987541L)
                    ^ 0xCA4F);
            return (pillar ? levelType.pillarPalette(random) : levelType.wallPalette(random)).material(random);
        }
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

    public static HallsCampLayout load(File dataFolder, HallsScenario scenario) throws IOException {
        String layoutPath = scenario == null || scenario.camp().layout().isBlank()
                ? "camps/camp_1.txt"
                : scenario.camp().layout();
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
        build(layout, roomStartX, y, roomStartZ, levelType, northOpeningX, BlockFace.NORTH);
    }

    public void build(HallsCampLayout layout,
                      int roomStartX,
                      int y,
                      int roomStartZ,
                      HallsLevelType levelType,
                      int openingX,
                      BlockFace openingFace) {
        buildRoom(layout, roomStartX, y, roomStartZ, levelType, openingX, openingFace);
        renderCampPlots(layout, roomStartX, y, roomStartZ);
        renderCampDoors(layout, roomStartX, y, roomStartZ, levelType);
    }

    private void buildRoom(HallsCampLayout layout,
                           int startX,
                           int y,
                           int startZ,
                           HallsLevelType levelType,
                           int openingX,
                           BlockFace openingFace) {
        for (int z = -1; z <= layout.depth(); z++) {
            for (int x = -1; x <= layout.width(); x++) {
                boolean border = x < 0 || z < 0 || x >= layout.width() || z >= layout.depth();
                boolean opening = border && x >= 0 && x < layout.width()
                        && Math.abs(x - openingX) <= 1
                        && ((openingFace == BlockFace.NORTH && z == -1)
                        || (openingFace == BlockFace.SOUTH && z == layout.depth()));
                boolean wall = !opening && (border || layout.at(x, z) == 'X');
                int blockX = startX + x;
                int blockZ = startZ + z;
                boolean pillar = pillarColumn(layout, x, z, border);
                Material wallMaterial = blockPlacer.wallMaterial(levelType, blockX, y, blockZ, pillar);
                blockPlacer.setBlock(blockX, y - 1, blockZ, wall ? wallMaterial : levelType.floor());
                blockPlacer.setBlock(blockX, y + ROOM_HEIGHT, blockZ, levelType.ceiling());
                for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                    boolean openingHeader = opening && dy >= 3;
                    blockPlacer.setBlock(blockX, y + dy, blockZ, wall || openingHeader
                            ? blockPlacer.wallMaterial(levelType, blockX, y + dy, blockZ, pillar)
                            : Material.AIR);
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
        Random random = new Random((((long) startX) << 32) ^ startZ ^ 0xCA9E1L);
        Set<Cell> lit = new HashSet<>();
        for (List<Cell> room : campRooms(layout)) {
            List<Cell> candidates = new ArrayList<>();
            for (Cell cell : room) {
                if (cell.x() > 0 && cell.z() > 0 && cell.x() < layout.width() - 1 && cell.z() < layout.depth() - 1) {
                    candidates.add(cell);
                }
            }
            if (candidates.isEmpty()) {
                candidates.addAll(room);
            }
            Collections.shuffle(candidates, random);
            if (!candidates.isEmpty()) {
                Cell cell = candidates.getFirst();
                blockPlacer.setBlock(startX + cell.x(), y + ROOM_HEIGHT, startZ + cell.z(), levelType.light());
                lit.add(cell);
            }
        }

        List<Cell> extraCandidates = new ArrayList<>();
        for (int z = 1; z < layout.depth() - 1; z++) {
            for (int x = 1; x < layout.width() - 1; x++) {
                Cell cell = new Cell(x, z);
                if (isCampRoomCell(layout, x, z) && !lit.contains(cell)) {
                    extraCandidates.add(cell);
                }
            }
        }
        Collections.shuffle(extraCandidates, random);
        int target = Math.max(lit.size(), Math.min(lit.size() + 4,
                Math.max(1, (lit.size() + extraCandidates.size()) / 28)));
        for (int i = 0; lit.size() < target && i < extraCandidates.size(); i++) {
            Cell cell = extraCandidates.get(i);
            blockPlacer.setBlock(startX + cell.x(), y + ROOM_HEIGHT, startZ + cell.z(), levelType.light());
            lit.add(cell);
        }
    }

    private List<List<Cell>> campRooms(HallsCampLayout layout) {
        List<List<Cell>> rooms = new ArrayList<>();
        Set<Cell> visited = new HashSet<>();
        for (int z = 0; z < layout.depth(); z++) {
            for (int x = 0; x < layout.width(); x++) {
                Cell start = new Cell(x, z);
                if (visited.contains(start) || !isCampRoomCell(layout, x, z)) {
                    continue;
                }
                List<Cell> room = new ArrayList<>();
                ArrayDeque<Cell> queue = new ArrayDeque<>();
                queue.add(start);
                visited.add(start);
                while (!queue.isEmpty()) {
                    Cell cell = queue.remove();
                    room.add(cell);
                    for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                        Cell next = new Cell(cell.x() + face.getModX(), cell.z() + face.getModZ());
                        if (visited.contains(next) || !isCampRoomCell(layout, next.x(), next.z())) {
                            continue;
                        }
                        visited.add(next);
                        queue.add(next);
                    }
                }
                rooms.add(room);
            }
        }
        return rooms;
    }

    private boolean isCampRoomCell(HallsCampLayout layout, int x, int z) {
        char cell = layout.at(x, z);
        return cell != 'X' && cell != 'D';
    }

    private record Cell(int x, int z) {
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

    private void renderCampDoors(HallsCampLayout layout,
                                 int roomStartX,
                                 int y,
                                 int roomStartZ,
                                 HallsLevelType levelType) {
        for (HallsCampLayout.DoorCell door : layout.doors()) {
            int worldX = roomStartX + door.x();
            int worldZ = roomStartZ + door.z();
            blockPlacer.setBlock(worldX, y - 1, worldZ, levelType.floor());
            blockPlacer.setBlock(worldX, y + ROOM_HEIGHT, worldZ, levelType.ceiling());
            BlockFace barFacing = doorBarFacing(layout, door);
            for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                if (dy < 3) {
                    blockPlacer.setBlock(worldX, y + dy, worldZ, Material.IRON_BARS, barFacing);
                } else {
                    blockPlacer.setBlock(worldX, y + dy, worldZ,
                            blockPlacer.wallMaterial(levelType, worldX, y + dy, worldZ, false));
                }
            }
            campRuntime.addDoor(worldX, y, worldZ, door);
        }
    }

    private BlockFace doorBarFacing(HallsCampLayout layout, HallsCampLayout.DoorCell door) {
        boolean northOpen = layout.openAt(door.x(), door.z() - 1);
        boolean southOpen = layout.openAt(door.x(), door.z() + 1);
        boolean eastOpen = layout.openAt(door.x() + 1, door.z());
        boolean westOpen = layout.openAt(door.x() - 1, door.z());
        if ((northOpen || southOpen) && !(eastOpen || westOpen)) {
            return BlockFace.EAST;
        }
        if ((eastOpen || westOpen) && !(northOpen || southOpen)) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }
}
