package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.block.BlockFace;

final class HallsExplorationGenerator {
    private static final BlockFace[] CARDINAL_FACES = {
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    };

    private final int originX;
    private final int originZ;
    private final int clearRadius;
    private final Bounds protectedElevator;
    private final Random random;
    private final List<Room> rooms = new ArrayList<>();
    private final List<List<Cell>> corridors = new ArrayList<>();
    private final Set<Cell> corridorMask = new HashSet<>();
    private final Set<Cell> corridorShellMask = new HashSet<>();

    private HallsExplorationGenerator(int originX, int originZ, int clearRadius, Bounds protectedElevator, Random random) {
        this.originX = originX;
        this.originZ = originZ;
        this.clearRadius = clearRadius;
        this.protectedElevator = protectedElevator;
        this.random = random;
    }

    static Plan generate(int originX,
                         int originZ,
                         int clearRadius,
                         int elevatorOuterRadius,
                         List<HallsLayout> layouts,
                         HallsScenario.FloorDefinition floorDefinition,
                         Random random) {
        Bounds elevatorBounds = new Bounds(
                originX - elevatorOuterRadius,
                originX + elevatorOuterRadius,
                originZ - elevatorOuterRadius,
                originZ + elevatorOuterRadius
        );
        HallsExplorationGenerator generator = new HallsExplorationGenerator(
                originX,
                originZ,
                clearRadius,
                elevatorBounds,
                random
        );
        generator.generate(layouts, floorDefinition);
        return new Plan(List.copyOf(generator.rooms), List.copyOf(generator.corridors));
    }

    private void generate(List<HallsLayout> layouts, HallsScenario.FloorDefinition floorDefinition) {
        if (layouts.isEmpty()) {
            return;
        }
        int targetRooms = Math.max(1, Math.min(28, floorDefinition.rooms()));
        HallsLayout firstLayout = layouts.get(random.nextInt(layouts.size()));
        Room first = new Room(firstLayout, originX - firstLayout.width() / 2, originZ + 9);
        first.openings().put(BlockFace.NORTH, firstLayout.width() / 2);
        rooms.add(first);

        int attempts = 0;
        while (rooms.size() < targetRooms && attempts++ < targetRooms * 120) {
            Room anchor = rooms.get(random.nextInt(rooms.size()));
            HallsLayout layout = layouts.get(random.nextInt(layouts.size()));
            BlockFace placementFace = randomFace();
            Room candidate = candidateRoom(anchor, layout, placementFace);
            if (!canPlaceRoom(candidate)) {
                continue;
            }
            BlockFace anchorFace = chooseDoorFace(anchor, placementFace);
            BlockFace candidateFace = chooseDoorFace(candidate, placementFace.getOppositeFace());
            int anchorOffset = doorOffset(anchor.layout(), anchorFace);
            int candidateOffset = doorOffset(candidate.layout(), candidateFace);
            Cell anchorDoor = doorCell(anchor, anchorFace, anchorOffset);
            Cell candidateDoor = doorCell(candidate, candidateFace, candidateOffset);
            List<Cell> carvedPath = carveConnector(anchorDoor, candidateDoor);
            if (carvedPath.isEmpty()) {
                continue;
            }
            anchor.openings().put(anchorFace, anchorOffset);
            candidate.openings().put(candidateFace, candidateOffset);
            rooms.add(candidate);
            rememberCorridor(carvedPath);
        }

        addLoopCorridors();
    }

    private Room candidateRoom(Room anchor, HallsLayout layout, BlockFace direction) {
        int gap = 7 + random.nextInt(13);
        int lateralRange = 9 + random.nextInt(12);
        int jitter = random.nextInt(lateralRange * 2 + 1) - lateralRange;
        return switch (direction) {
            case NORTH -> new Room(layout, anchor.centerX() + jitter - layout.width() / 2,
                    anchor.startZ() - gap - layout.depth());
            case SOUTH -> new Room(layout, anchor.centerX() + jitter - layout.width() / 2,
                    anchor.startZ() + anchor.layout().depth() + gap);
            case EAST -> new Room(layout, anchor.startX() + anchor.layout().width() + gap,
                    anchor.centerZ() + jitter - layout.depth() / 2);
            case WEST -> new Room(layout, anchor.startX() - gap - layout.width(),
                    anchor.centerZ() + jitter - layout.depth() / 2);
            default -> new Room(layout, anchor.startX(), anchor.startZ());
        };
    }

    private boolean canPlaceRoom(Room room) {
        if (room.startX() - 2 < originX - clearRadius
                || room.startX() + room.layout().width() + 2 > originX + clearRadius
                || room.startZ() - 2 < originZ - clearRadius
                || room.startZ() + room.layout().depth() + 2 > originZ + clearRadius) {
            return false;
        }
        Bounds bounds = Bounds.of(room).inflate(4);
        if (bounds.intersects(protectedElevator.inflate(3))) {
            return false;
        }
        for (Room existing : rooms) {
            if (bounds.intersects(Bounds.of(existing).inflate(4))) {
                return false;
            }
        }
        for (Cell shellCell : roomShellCells(room)) {
            if (corridorMask.contains(shellCell) || corridorShellMask.contains(shellCell)) {
                return false;
            }
        }
        return true;
    }

    private BlockFace chooseDoorFace(Room room, BlockFace preferred) {
        if (!room.openings().containsKey(preferred) && random.nextDouble() < 0.72) {
            return preferred;
        }
        List<BlockFace> faces = new ArrayList<>(List.of(CARDINAL_FACES));
        Collections.shuffle(faces, random);
        for (BlockFace face : faces) {
            if (!room.openings().containsKey(face)) {
                return face;
            }
        }
        return preferred;
    }

    private int doorOffset(HallsLayout layout, BlockFace face) {
        int span = face == BlockFace.NORTH || face == BlockFace.SOUTH ? layout.width() : layout.depth();
        if (span <= 2) {
            return Math.max(0, span / 2);
        }
        return 1 + random.nextInt(span - 2);
    }

    private Cell doorCell(Room room, BlockFace face, int offset) {
        return switch (face) {
            case NORTH -> new Cell(room.startX() + offset, room.northExitZ());
            case SOUTH -> new Cell(room.startX() + offset, room.southExitZ());
            case EAST -> new Cell(room.eastExitX(), room.startZ() + offset);
            case WEST -> new Cell(room.westExitX(), room.startZ() + offset);
            default -> new Cell(room.centerX(), room.centerZ());
        };
    }

    private List<Cell> carveConnector(Cell start, Cell end) {
        int maxLength = maximumConnectorLength(start, end);
        for (int attempt = 0; attempt < 14; attempt++) {
            List<Cell> path = buildOrthogonalCandidate(start, end);
            if (path.size() <= maxLength && canCarve(path, start, end)) {
                return path;
            }
        }
        List<Cell> directPath = buildDirectCandidate(start, end, random.nextBoolean());
        return directPath.size() <= maxLength && canCarve(directPath, start, end) ? directPath : List.of();
    }

    private List<Cell> buildOrthogonalCandidate(Cell start, Cell end) {
        int detour = random.nextDouble() < 0.55 ? 0 : 3 + random.nextInt(8);
        boolean horizontalFirst = random.nextBoolean();
        List<Cell> waypoints = new ArrayList<>();
        if (detour == 0) {
            waypoints.add(horizontalFirst ? new Cell(end.x(), start.z()) : new Cell(start.x(), end.z()));
        } else if (horizontalFirst) {
            int x = clamp(random.nextBoolean() ? Math.max(start.x(), end.x()) + detour : Math.min(start.x(), end.x()) - detour,
                    originX - clearRadius + 3, originX + clearRadius - 3);
            waypoints.add(new Cell(x, start.z()));
            waypoints.add(new Cell(x, end.z()));
        } else {
            int z = clamp(random.nextBoolean() ? Math.max(start.z(), end.z()) + detour : Math.min(start.z(), end.z()) - detour,
                    originZ - clearRadius + 3, originZ + clearRadius - 3);
            waypoints.add(new Cell(start.x(), z));
            waypoints.add(new Cell(end.x(), z));
        }
        waypoints.add(end);
        return pathThrough(start, waypoints);
    }

    private List<Cell> buildDirectCandidate(Cell start, Cell end, boolean horizontalFirst) {
        List<Cell> waypoints = new ArrayList<>();
        waypoints.add(horizontalFirst ? new Cell(end.x(), start.z()) : new Cell(start.x(), end.z()));
        waypoints.add(end);
        return pathThrough(start, waypoints);
    }

    private List<Cell> pathThrough(Cell start, List<Cell> waypoints) {
        List<Cell> path = new ArrayList<>();
        Cell current = start;
        path.add(current);
        for (Cell waypoint : waypoints) {
            while (current.x() != waypoint.x()) {
                current = new Cell(current.x() + Integer.compare(waypoint.x(), current.x()), current.z());
                path.add(current);
            }
            while (current.z() != waypoint.z()) {
                current = new Cell(current.x(), current.z() + Integer.compare(waypoint.z(), current.z()));
                path.add(current);
            }
        }
        return path;
    }

    private boolean canCarve(List<Cell> path, Cell start, Cell end) {
        if (path.isEmpty()) {
            return false;
        }
        Set<Cell> pathCells = new HashSet<>(path);
        if (pathCells.size() != path.size()) {
            return false;
        }
        for (int i = 1; i < path.size(); i++) {
            if (manhattanDistance(path.get(i - 1), path.get(i)) != 1) {
                return false;
            }
        }
        for (Cell cell : path) {
            if (!insideBuildArea(cell) || protectedElevator.contains(cell.x(), cell.z())) {
                return false;
            }
            if (!cell.equals(start) && !cell.equals(end) && (corridorMask.contains(cell) || insideAnyRoom(cell))) {
                return false;
            }
        }
        for (Cell shellCell : corridorShellCells(pathCells)) {
            if (!pathCells.contains(shellCell)
                    && (corridorMask.contains(shellCell) || protectedElevator.contains(shellCell.x(), shellCell.z()))) {
                return false;
            }
        }
        return true;
    }

    private boolean insideBuildArea(Cell cell) {
        return cell.x() >= originX - clearRadius + 1
                && cell.x() <= originX + clearRadius - 1
                && cell.z() >= originZ - clearRadius + 1
                && cell.z() <= originZ + clearRadius - 1;
    }

    private boolean insideAnyRoom(Cell cell) {
        for (Room room : rooms) {
            if (Bounds.of(room).contains(cell.x(), cell.z())) {
                return true;
            }
        }
        return false;
    }

    private void rememberCorridor(List<Cell> path) {
        Set<Cell> openCells = new HashSet<>(path);
        corridorMask.addAll(openCells);
        corridorShellMask.addAll(corridorShellCells(openCells));
        corridors.add(List.copyOf(path));
    }

    private Set<Cell> corridorShellCells(Set<Cell> openCells) {
        Set<Cell> shell = new HashSet<>();
        for (Cell cell : openCells) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    shell.add(new Cell(cell.x() + dx, cell.z() + dz));
                }
            }
        }
        return shell;
    }

    private Set<Cell> roomShellCells(Room room) {
        Set<Cell> cells = new HashSet<>();
        for (int z = -1; z <= room.layout().depth(); z++) {
            for (int x = -1; x <= room.layout().width(); x++) {
                cells.add(new Cell(room.startX() + x, room.startZ() + z));
            }
        }
        return cells;
    }

    private void addLoopCorridors() {
        int target = Math.max(1, rooms.size() / 6);
        int added = 0;
        int attempts = 0;
        while (added < target && attempts++ < rooms.size() * rooms.size()) {
            Room from = rooms.get(random.nextInt(rooms.size()));
            Room to = rooms.get(random.nextInt(rooms.size()));
            if (from == to || connectedDistance(from, to) < 16) {
                continue;
            }
            BlockFace fromFace = chooseDoorFace(from, faceToward(from, to));
            BlockFace toFace = chooseDoorFace(to, faceToward(to, from));
            if (from.openings().containsKey(fromFace) || to.openings().containsKey(toFace)) {
                continue;
            }
            int fromOffset = doorOffset(from.layout(), fromFace);
            int toOffset = doorOffset(to.layout(), toFace);
            List<Cell> path = carveConnector(doorCell(from, fromFace, fromOffset), doorCell(to, toFace, toOffset));
            if (path.isEmpty()) {
                continue;
            }
            from.openings().put(fromFace, fromOffset);
            to.openings().put(toFace, toOffset);
            rememberCorridor(path);
            added++;
        }
    }

    private int connectedDistance(Room from, Room to) {
        return Math.abs(from.centerX() - to.centerX()) + Math.abs(from.centerZ() - to.centerZ());
    }

    private BlockFace faceToward(Room from, Room to) {
        int dx = to.centerX() - from.centerX();
        int dz = to.centerZ() - from.centerZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private BlockFace randomFace() {
        return CARDINAL_FACES[random.nextInt(CARDINAL_FACES.length)];
    }

    private int maximumConnectorLength(Cell start, Cell end) {
        return Math.min(58, manhattanDistance(start, end) + 22);
    }

    private int manhattanDistance(Cell start, Cell end) {
        return Math.abs(start.x() - end.x()) + Math.abs(start.z() - end.z());
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record Plan(List<Room> rooms, List<List<Cell>> corridors) {
    }

    static final class Room {
        private final HallsLayout layout;
        private final int startX;
        private final int startZ;
        private final Map<BlockFace, Integer> openings = new HashMap<>();

        private Room(HallsLayout layout, int startX, int startZ) {
            this.layout = layout;
            this.startX = startX;
            this.startZ = startZ;
        }

        HallsLayout layout() {
            return layout;
        }

        int startX() {
            return startX;
        }

        int startZ() {
            return startZ;
        }

        Map<BlockFace, Integer> openings() {
            return openings;
        }

        int centerX() {
            return startX + layout.width() / 2;
        }

        int centerZ() {
            return startZ + layout.depth() / 2;
        }

        int northExitZ() {
            return startZ - 1;
        }

        int southExitZ() {
            return startZ + layout.depth();
        }

        int westExitX() {
            return startX - 1;
        }

        int eastExitX() {
            return startX + layout.width();
        }
    }

    record Cell(int x, int z) {
    }

    private record Bounds(int minX, int maxX, int minZ, int maxZ) {
        private static Bounds of(Room room) {
            return new Bounds(room.startX() - 1, room.startX() + room.layout().width(),
                    room.startZ() - 1, room.startZ() + room.layout().depth());
        }

        private Bounds inflate(int amount) {
            return new Bounds(minX - amount, maxX + amount, minZ - amount, maxZ + amount);
        }

        private boolean intersects(Bounds other) {
            return minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        private boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }
}
