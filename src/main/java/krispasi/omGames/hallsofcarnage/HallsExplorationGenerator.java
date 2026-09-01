package krispasi.omGames.hallsofcarnage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
    private static final int LOGICAL_RADIUS = 256;

    private final int originX;
    private final int originZ;
    private final int clearRadius;
    private final Bounds protectedElevator;
    private final Random random;
    private final List<Room> rooms = new ArrayList<>();
    private final Set<Cell> corridorCells = new HashSet<>();
    private final Set<Cell> corridorShellCells = new HashSet<>();
    private final Set<Cell> roomShellCells = new HashSet<>();
    private final Set<Cell> roomInteriorCells = new HashSet<>();
    private final Set<Cell> networkCells = new HashSet<>();

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
        return generator.plan();
    }

    private void generate(List<HallsLayout> layouts, HallsScenario.FloorDefinition floorDefinition) {
        if (layouts.isEmpty()) {
            return;
        }
        int targetRooms = Math.max(1, floorDefinition.rooms());
        seedElevatorNetwork();
        addFirstRoom(layouts.get(random.nextInt(layouts.size())));
        int attempts = 0;
        while (rooms.size() < targetRooms && attempts++ < targetRooms * 1000) {
            HallsLayout layout = layouts.get(random.nextInt(layouts.size()));
            Room candidate = randomRoom(layout);
            if (!canPlaceRoom(candidate)) {
                continue;
            }
            BlockFace face = faceTowardNetwork(candidate);
            int offset = doorOffset(layout, face);
            Cell door = doorCell(candidate, face, offset);
            List<Cell> path = findConnectorPath(door, networkCells, Bounds.of(candidate));
            if (path.isEmpty()) {
                continue;
            }
            candidate.openings().put(face, offset);
            addRoom(candidate);
            rememberCorridor(path);
        }
        addLoopCorridors();
    }

    private void seedElevatorNetwork() {
        for (int z = protectedElevator.maxZ() + 1; z <= protectedElevator.maxZ() + 4; z++) {
            Cell cell = new Cell(originX, z);
            networkCells.add(cell);
            corridorCells.add(cell);
        }
    }

    private void addFirstRoom(HallsLayout layout) {
        Room first = new Room(layout, originX - layout.width() / 2, originZ + 12);
        BlockFace face = BlockFace.NORTH;
        int offset = layout.width() / 2;
        Cell door = doorCell(first, face, offset);
        List<Cell> path = directVerticalPath(new Cell(originX, protectedElevator.maxZ() + 1), door);
        first.openings().put(face, offset);
        addRoom(first);
        rememberCorridor(path);
    }

    private Room randomRoom(HallsLayout layout) {
        int usableRadius = Math.min(LOGICAL_RADIUS - 8, Math.max(24, clearRadius - 8));
        int x = originX + random.nextInt(usableRadius * 2 + 1) - usableRadius - layout.width() / 2;
        int z = originZ + random.nextInt(usableRadius * 2 + 1) - usableRadius - layout.depth() / 2;
        return new Room(layout, x, z);
    }

    private boolean canPlaceRoom(Room room) {
        Bounds bounds = Bounds.of(room);
        if (!insideBuildArea(bounds.inflate(2)) || bounds.intersects(protectedElevator.inflate(6))) {
            return false;
        }
        for (Room existing : rooms) {
            if (bounds.inflate(2).intersects(Bounds.of(existing).inflate(2))) {
                return false;
            }
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                if (corridorCells.contains(new Cell(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    private BlockFace faceTowardNetwork(Room room) {
        int dx = originX - room.centerX();
        int dz = originZ - room.centerZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
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

    private List<Cell> findConnectorPath(Cell start, Set<Cell> targets) {
        return findConnectorPath(start, targets, null);
    }

    private List<Cell> findConnectorPath(Cell start, Set<Cell> targets, Bounds blockedCandidate) {
        if (!insideBuildArea(start)) {
            return List.of();
        }
        Queue<Cell> queue = new ArrayDeque<>();
        Map<Cell, Cell> previous = new HashMap<>();
        Set<Cell> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        int maxVisited = Math.max(6000, clearRadius * clearRadius);
        while (!queue.isEmpty() && seen.size() < maxVisited) {
            Cell current = queue.remove();
            if (!current.equals(start) && targets.contains(current)) {
                return reconstructPath(previous, current);
            }
            List<BlockFace> faces = new ArrayList<>(List.of(CARDINAL_FACES));
            Collections.shuffle(faces, random);
            for (BlockFace face : faces) {
                Cell next = step(current, face);
                if (seen.contains(next) || !canCorridorOccupy(next, start, targets.contains(next), blockedCandidate)) {
                    continue;
                }
                seen.add(next);
                previous.put(next, current);
                queue.add(next);
            }
        }
        return List.of();
    }

    private boolean canCorridorOccupy(Cell cell, Cell start, boolean target, Bounds blockedCandidate) {
        if (!insideBuildArea(cell) || protectedElevator.contains(cell.x(), cell.z())) {
            return false;
        }
        if (!cell.equals(start) && blockedCandidate != null && blockedCandidate.contains(cell.x(), cell.z())) {
            return false;
        }
        if (cell.equals(start) || target || corridorCells.contains(cell)) {
            return true;
        }
        return !roomShellCells.contains(cell) && !roomInteriorCells.contains(cell);
    }

    private List<Cell> reconstructPath(Map<Cell, Cell> previous, Cell end) {
        List<Cell> path = new ArrayList<>();
        Cell current = end;
        path.add(current);
        while (previous.containsKey(current)) {
            current = previous.get(current);
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    private Cell step(Cell cell, BlockFace face) {
        return switch (face) {
            case NORTH -> new Cell(cell.x(), cell.z() - 1);
            case SOUTH -> new Cell(cell.x(), cell.z() + 1);
            case EAST -> new Cell(cell.x() + 1, cell.z());
            case WEST -> new Cell(cell.x() - 1, cell.z());
            default -> cell;
        };
    }

    private List<Cell> directVerticalPath(Cell start, Cell end) {
        List<Cell> path = new ArrayList<>();
        Cell current = start;
        path.add(current);
        while (current.z() != end.z()) {
            current = new Cell(current.x(), current.z() + Integer.compare(end.z(), current.z()));
            path.add(current);
        }
        while (current.x() != end.x()) {
            current = new Cell(current.x() + Integer.compare(end.x(), current.x()), current.z());
            path.add(current);
        }
        return path;
    }

    private void addRoom(Room room) {
        rooms.add(room);
        Bounds bounds = Bounds.of(room);
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                Cell cell = new Cell(x, z);
                if (x == bounds.minX() || x == bounds.maxX() || z == bounds.minZ() || z == bounds.maxZ()
                        || room.layout().at(x - room.startX(), z - room.startZ()) == 'X') {
                    roomShellCells.add(cell);
                } else {
                    roomInteriorCells.add(cell);
                }
            }
        }
        for (Map.Entry<BlockFace, Integer> opening : room.openings().entrySet()) {
            networkCells.add(doorCell(room, opening.getKey(), opening.getValue()));
        }
    }

    private void rememberCorridor(List<Cell> path) {
        if (path.isEmpty()) {
            return;
        }
        corridorCells.addAll(path);
        networkCells.addAll(path);
        for (Cell point : path) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    corridorShellCells.add(new Cell(point.x() + dx, point.z() + dz));
                }
            }
        }
    }

    private void addLoopCorridors() {
        int target = Math.max(2, rooms.size() / 5);
        int added = 0;
        int attempts = 0;
        while (added < target && attempts++ < rooms.size() * 20) {
            Room room = rooms.get(random.nextInt(rooms.size()));
            BlockFace face = randomFace();
            if (room.openings().containsKey(face)) {
                continue;
            }
            int offset = doorOffset(room.layout(), face);
            Cell door = doorCell(room, face, offset);
            Set<Cell> targets = new HashSet<>(networkCells);
            for (Cell cell : openInteriorCells(room)) {
                targets.remove(cell);
            }
            List<Cell> path = findConnectorPath(door, targets);
            if (path.size() < 8) {
                continue;
            }
            room.openings().put(face, offset);
            networkCells.add(door);
            rememberCorridor(path);
            added++;
        }
    }

    private List<Cell> openInteriorCells(Room room) {
        List<Cell> cells = new ArrayList<>();
        for (int z = 0; z < room.layout().depth(); z++) {
            for (int x = 0; x < room.layout().width(); x++) {
                if (room.layout().at(x, z) == 'O') {
                    cells.add(new Cell(room.startX() + x, room.startZ() + z));
                }
            }
        }
        return cells;
    }

    private boolean insideBuildArea(Bounds bounds) {
        return bounds.minX() >= originX - clearRadius + 1
                && bounds.maxX() <= originX + clearRadius - 1
                && bounds.minZ() >= originZ - clearRadius + 1
                && bounds.maxZ() <= originZ + clearRadius - 1;
    }

    private boolean insideBuildArea(Cell cell) {
        return cell.x() >= originX - clearRadius + 1
                && cell.x() <= originX + clearRadius - 1
                && cell.z() >= originZ - clearRadius + 1
                && cell.z() <= originZ + clearRadius - 1;
    }

    private BlockFace randomFace() {
        return CARDINAL_FACES[random.nextInt(CARDINAL_FACES.length)];
    }

    private Plan plan() {
        Set<Cell> walkable = new HashSet<>(corridorCells);
        walkable.addAll(roomInteriorCells);
        return new Plan(
                List.copyOf(rooms),
                Set.copyOf(corridorCells),
                Set.copyOf(corridorShellCells),
                Set.copyOf(walkable),
                allRoomsReachable(walkable)
        );
    }

    private boolean allRoomsReachable(Set<Cell> walkable) {
        if (rooms.isEmpty()) {
            return false;
        }
        Set<Cell> reachable = new HashSet<>();
        Queue<Cell> queue = new ArrayDeque<>();
        Cell start = new Cell(originX, protectedElevator.maxZ() + 1);
        queue.add(start);
        reachable.add(start);
        while (!queue.isEmpty()) {
            Cell current = queue.remove();
            for (BlockFace face : CARDINAL_FACES) {
                Cell next = step(current, face);
                if (reachable.contains(next) || !walkable.contains(next)) {
                    continue;
                }
                reachable.add(next);
                queue.add(next);
            }
        }
        for (Room room : rooms) {
            boolean roomReachable = openInteriorCells(room).stream().anyMatch(reachable::contains);
            if (!roomReachable) {
                return false;
            }
        }
        return true;
    }

    record Plan(List<Room> rooms,
                Set<Cell> corridorCells,
                Set<Cell> corridorShellCells,
                Set<Cell> walkableCells,
                boolean reachable) {
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
