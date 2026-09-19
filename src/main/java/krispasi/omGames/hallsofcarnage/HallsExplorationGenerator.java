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
    private static final int MAX_CONNECTOR_TARGETS = 18;
    private static final int CONNECTOR_CANDIDATE_ATTEMPTS = 28;

    private final int originX;
    private final int originZ;
    private final int clearRadius;
    private final Bounds protectedElevator;
    private final BlockFace elevatorFrontFace;
    private final Random random;
    private final CorridorMode corridorMode;
    private final double corridorDistanceMultiplier;
    private final List<Room> rooms = new ArrayList<>();
    private final Set<Cell> corridorCells = new HashSet<>();
    private final Set<Cell> corridorShellCells = new HashSet<>();
    private final Set<Cell> lowCeilingCorridorCells = new HashSet<>();
    private final Set<Cell> ventGateCells = new HashSet<>();
    private final Set<Cell> liquidCells = new HashSet<>();
    private final Set<Cell> roomShellCells = new HashSet<>();
    private final Set<Cell> roomInteriorCells = new HashSet<>();
    private final Set<Cell> networkCells = new HashSet<>();
    private final Map<DoorOffsetKey, List<Integer>> validDoorOffsetCache = new HashMap<>();

    private HallsExplorationGenerator(int originX,
                                      int originZ,
                                      int clearRadius,
                                      Bounds protectedElevator,
                                      BlockFace elevatorFrontFace,
                                      String corridorGeneration,
                                      double corridorDistanceMultiplier,
                                      Random random) {
        this.originX = originX;
        this.originZ = originZ;
        this.clearRadius = clearRadius;
        this.protectedElevator = protectedElevator;
        this.elevatorFrontFace = elevatorFrontFace == null ? BlockFace.SOUTH : elevatorFrontFace;
        this.corridorMode = CorridorMode.from(corridorGeneration);
        this.corridorDistanceMultiplier = Math.max(0.5, corridorDistanceMultiplier);
        this.random = random;
    }

    static Plan generate(int originX,
                         int originZ,
                         int clearRadius,
                         int elevatorOuterRadius,
                         BlockFace elevatorFrontFace,
                         List<HallsLayout> layouts,
                         HallsScenario.FloorDefinition floorDefinition,
                         String corridorGeneration,
                         double corridorDistanceMultiplier,
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
                elevatorFrontFace,
                corridorGeneration,
                corridorDistanceMultiplier,
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
        if (corridorMode == CorridorMode.BUNKER) {
            generateBunkerLayout(layouts, targetRooms);
            return;
        }
        if (!addFirstRoom(layouts)) {
            return;
        }
        int attempts = 0;
        while (rooms.size() < targetRooms && attempts++ < roomPlacementAttemptLimit(targetRooms)) {
            HallsLayout layout = layouts.get(random.nextInt(layouts.size()));
            RoomConnection candidate = randomRoomConnection(layout);
            if (candidate == null || !canPlaceRoom(candidate.room())) {
                continue;
            }
            Cell candidateDoor = doorCell(candidate.room(), candidate.roomFace(), candidate.roomOffset());
            Cell anchorDoor = doorCell(candidate.anchor(), candidate.anchorFace(), candidate.anchorOffset());
            List<Cell> path = findConnectorPath(candidateDoor, Set.of(anchorDoor),
                    List.of(Bounds.of(candidate.room()), Bounds.of(candidate.anchor())));
            if (path.isEmpty()) {
                continue;
            }
            candidate.anchor().openings().put(candidate.anchorFace(), candidate.anchorOffset());
            candidate.room().openings().put(candidate.roomFace(), candidate.roomOffset());
            networkCells.add(anchorDoor);
            addRoom(candidate.room());
            rememberCorridor(path, shouldUseLibraryVentConnector(path));
        }
        addFirstRoomOnwardRoutes();
        addRoomToRoomLoops();
        if (corridorMode == CorridorMode.MAZE) {
            addGridOpenHalls();
        } else if (corridorMode == CorridorMode.BACKROOMS) {
            addBackroomsGridOpenHalls();
        } else if (corridorMode == CorridorMode.OPEN_HALLS) {
            addRoomLocalOpenHalls();
        } else if (corridorMode == CorridorMode.CAVE) {
            addMazeBranches(Math.max(rooms.size() / 2, 4));
        }
        if (corridorMode == CorridorMode.LIBRARY) {
            markLibraryVentGates();
        }
    }

    private void seedElevatorNetwork() {
        for (int step = 1; step <= 4; step++) {
            Cell cell = elevatorFrontCell(step);
            networkCells.add(cell);
            corridorCells.add(cell);
        }
    }

    private boolean addFirstRoom(List<HallsLayout> layouts) {
        if (corridorMode == CorridorMode.SEWER || corridorMode == CorridorMode.BUNKER) {
            return addFirstSewerRoom(layouts);
        }
        BlockFace face = elevatorFrontFace.getOppositeFace();
        HallsLayout layout = randomLayoutWithDoor(layouts, face);
        if (layout == null) {
            return false;
        }
        int offset = doorOffset(layout, face);
        Room first = firstRoom(layout, face, offset, 12);
        Cell door = doorCell(first, face, offset);
        List<Cell> path = directVerticalPath(elevatorFrontCell(1), door);
        first.openings().put(face, offset);
        addRoom(first);
        rememberCorridor(path);
        return true;
    }

    private boolean addFirstSewerRoom(List<HallsLayout> layouts) {
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockFace face = random.nextBoolean() ? BlockFace.WEST : BlockFace.EAST;
            HallsLayout layout = randomLayoutWithDoor(layouts, face);
            if (layout == null) {
                continue;
            }
            int offset = doorOffset(layout, face);
            int corridorDistance = corridorMode == CorridorMode.BUNKER ? 14 + random.nextInt(7) : 18 + random.nextInt(9);
            int corridorZ = elevatorFrontFace == BlockFace.NORTH
                    ? originZ - corridorDistance
                    : originZ + corridorDistance;
            int sideGap = 8 + random.nextInt(7);
            int startX = face == BlockFace.WEST
                    ? originX + sideGap
                    : originX - sideGap - layout.width();
            Room first = new Room(layout, startX, corridorZ - offset);
            if (!canPlaceRoom(first)) {
                continue;
            }
            Cell door = doorCell(first, face, offset);
            Cell start = elevatorFrontCell(1);
            List<Cell> path = findConnectorPath(start, Set.of(door), Bounds.of(first));
            if (path.isEmpty()) {
                continue;
            }
            first.openings().put(face, offset);
            addRoom(first);
            rememberCorridor(path);
            return true;
        }
        return false;
    }

    private Room firstRoom(HallsLayout layout, BlockFace face, int offset, int gap) {
        return switch (face) {
            case NORTH -> new Room(layout, originX - offset, originZ + gap);
            case SOUTH -> new Room(layout, originX - offset, originZ - gap - layout.depth());
            case EAST -> new Room(layout, originX - gap - layout.width(), originZ - offset);
            case WEST -> new Room(layout, originX + gap, originZ - offset);
            default -> new Room(layout, originX - offset, originZ + gap);
        };
    }

    private Cell elevatorFrontCell(int distance) {
        int edge = switch (elevatorFrontFace) {
            case NORTH -> protectedElevator.minZ() - distance;
            case SOUTH -> protectedElevator.maxZ() + distance;
            default -> originZ;
        };
        int side = switch (elevatorFrontFace) {
            case EAST -> protectedElevator.maxX() + distance;
            case WEST -> protectedElevator.minX() - distance;
            default -> originX;
        };
        return new Cell(side, edge);
    }

    private HallsLayout randomLayoutWithDoor(List<HallsLayout> layouts, BlockFace face) {
        List<HallsLayout> candidates = layouts.stream()
                .filter(layout -> !validDoorOffsets(layout, face).isEmpty())
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private RoomConnection randomRoomConnection(HallsLayout layout) {
        List<Room> anchors = rooms.stream()
                .filter(room -> !availableFaces(room).isEmpty())
                .toList();
        if (anchors.isEmpty()) {
            return null;
        }
        Room anchor = anchors.get(random.nextInt(anchors.size()));
        if (corridorMode == CorridorMode.BACKROOMS) {
            RoomConnection backroomsConnection = randomBackroomsRoomConnection(layout, anchor);
            if (backroomsConnection != null) {
                return backroomsConnection;
            }
        }
        List<BlockFace> faces = availableFaces(anchor);
        Collections.shuffle(faces, random);
        BlockFace face = faces.getFirst();
        int baseGap = corridorMode == CorridorMode.MAZE ? 2 + random.nextInt(5) : 5 + random.nextInt(14);
        if (corridorMode == CorridorMode.BUNKER) {
            baseGap = 8 + random.nextInt(16);
        }
        int gap = Math.max(1, (int) Math.round(baseGap * corridorDistanceMultiplier));
        int lateralBase = Math.max(2, (int) Math.round((corridorMode == CorridorMode.MAZE ? 3 : 10) * corridorDistanceMultiplier));
        int lateralRange = lateralBase + Math.max(anchor.layout().width(), anchor.layout().depth()) / 2
                + Math.max(layout.width(), layout.depth()) / 2;
        int lateral = random.nextInt(lateralRange * 2 + 1) - lateralRange;
        Room room = switch (face) {
            case NORTH -> new Room(layout, anchor.centerX() + lateral - layout.width() / 2,
                    anchor.startZ() - gap - layout.depth());
            case SOUTH -> new Room(layout, anchor.centerX() + lateral - layout.width() / 2,
                    anchor.startZ() + anchor.layout().depth() + gap);
            case EAST -> new Room(layout, anchor.startX() + anchor.layout().width() + gap,
                    anchor.centerZ() + lateral - layout.depth() / 2);
            case WEST -> new Room(layout, anchor.startX() - gap - layout.width(),
                    anchor.centerZ() + lateral - layout.depth() / 2);
            default -> randomRoomAnywhere(layout);
        };
        BlockFace roomFace = face.getOppositeFace();
        return new RoomConnection(
                anchor,
                room,
                face,
                roomFace,
                doorOffset(anchor.layout(), face),
                doorOffset(layout, roomFace)
        );
    }

    private RoomConnection randomBackroomsRoomConnection(HallsLayout layout, Room anchor) {
        List<BlockFace> available = availableFaces(anchor);
        if (available.isEmpty()) {
            return null;
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = 14 + random.nextInt(58);
            int dx = (int) Math.round(Math.cos(angle) * distance) + random.nextInt(17) - 8;
            int dz = (int) Math.round(Math.sin(angle) * distance) + random.nextInt(17) - 8;
            if (Math.abs(dx) + Math.abs(dz) < 14) {
                dz += dz < 0 ? -14 : 14;
            }
            Room room = new Room(layout,
                    anchor.centerX() + dx - layout.width() / 2,
                    anchor.centerZ() + dz - layout.depth() / 2);
            int centerDx = room.centerX() - anchor.centerX();
            int centerDz = room.centerZ() - anchor.centerZ();
            BlockFace anchorFace;
            if (Math.abs(Math.abs(centerDx) - Math.abs(centerDz)) <= 6 && random.nextBoolean()) {
                anchorFace = centerDx >= 0 ? BlockFace.EAST : BlockFace.WEST;
            } else if (Math.abs(centerDx) > Math.abs(centerDz)) {
                anchorFace = centerDx >= 0 ? BlockFace.EAST : BlockFace.WEST;
            } else {
                anchorFace = centerDz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
            }
            if (!available.contains(anchorFace)) {
                continue;
            }
            BlockFace roomFace = anchorFace.getOppositeFace();
            if (validDoorOffsets(anchor.layout(), anchorFace).isEmpty() || validDoorOffsets(layout, roomFace).isEmpty()) {
                continue;
            }
            return new RoomConnection(
                    anchor,
                    room,
                    anchorFace,
                    roomFace,
                    doorOffset(anchor.layout(), anchorFace),
                    doorOffset(layout, roomFace)
            );
        }
        return null;
    }

    private Room randomRoomAnywhere(HallsLayout layout) {
        int usableRadius = Math.min(LOGICAL_RADIUS - 8, Math.max(24, clearRadius - 8));
        int x = originX + random.nextInt(usableRadius * 2 + 1) - usableRadius - layout.width() / 2;
        int z = originZ + random.nextInt(usableRadius * 2 + 1) - usableRadius - layout.depth() / 2;
        return new Room(layout, x, z);
    }

    private boolean canPlaceRoom(Room room) {
        Bounds bounds = Bounds.of(room);
        int spacing = roomSpacing();
        if (!insideBuildArea(bounds.inflate(spacing)) || bounds.intersects(protectedElevator.inflate(6))) {
            return false;
        }
        for (Room existing : rooms) {
            if (bounds.inflate(spacing).intersects(Bounds.of(existing).inflate(spacing))) {
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

    private int roomSpacing() {
        return corridorMode == CorridorMode.MAZE || corridorMode == CorridorMode.BUNKER ? 0 : 2;
    }

    private int doorOffset(HallsLayout layout, BlockFace face) {
        List<Integer> offsets = validDoorOffsets(layout, face);
        if (!offsets.isEmpty()) {
            return offsets.get(random.nextInt(offsets.size()));
        }
        int span = face == BlockFace.NORTH || face == BlockFace.SOUTH ? layout.width() : layout.depth();
        if (span <= 2) {
            return Math.max(0, span / 2);
        }
        return 1 + random.nextInt(span - 2);
    }

    private List<Integer> validDoorOffsets(HallsLayout layout, BlockFace face) {
        DoorOffsetKey key = new DoorOffsetKey(layout, face);
        List<Integer> cached = validDoorOffsetCache.get(key);
        if (cached != null) {
            return cached;
        }
        int span = face == BlockFace.NORTH || face == BlockFace.SOUTH ? layout.width() : layout.depth();
        List<Integer> offsets = new ArrayList<>();
        for (int offset = 1; offset < span - 1; offset++) {
            if (isValidDoorOffset(layout, face, offset)) {
                offsets.add(offset);
            }
        }
        List<Integer> result = List.copyOf(offsets);
        validDoorOffsetCache.put(key, result);
        return result;
    }

    private boolean isValidDoorOffset(HallsLayout layout, BlockFace face, int offset) {
        if (wideRoomOpenings()) {
            if (offset <= 1) {
                return false;
            }
            int span = face == BlockFace.NORTH || face == BlockFace.SOUTH ? layout.width() : layout.depth();
            if (offset >= span - 2) {
                return false;
            }
            return isSingleDoorOffsetOpen(layout, face, offset - 1)
                    && isSingleDoorOffsetOpen(layout, face, offset)
                    && isSingleDoorOffsetOpen(layout, face, offset + 1);
        }
        return isSingleDoorOffsetOpen(layout, face, offset);
    }

    private boolean wideRoomOpenings() {
        return corridorMode == CorridorMode.LARGE_CORRIDORS
                || corridorMode == CorridorMode.OPEN_HALLS
                || corridorMode == CorridorMode.LIBRARY;
    }

    private boolean isSingleDoorOffsetOpen(HallsLayout layout, BlockFace face, int offset) {
        return switch (face) {
            case NORTH -> layout.at(offset, 0) == 'O' && layout.at(offset, 1) == 'O';
            case SOUTH -> layout.at(offset, layout.depth() - 1) == 'O' && layout.at(offset, layout.depth() - 2) == 'O';
            case EAST -> layout.at(layout.width() - 1, offset) == 'O' && layout.at(layout.width() - 2, offset) == 'O';
            case WEST -> layout.at(0, offset) == 'O' && layout.at(1, offset) == 'O';
            default -> false;
        };
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
        return findConnectorPath(start, targets, List.of());
    }

    private List<Cell> findConnectorPath(Cell start, Set<Cell> targets, Bounds blockedCandidate) {
        return findConnectorPath(start, targets, blockedCandidate == null ? List.of() : List.of(blockedCandidate));
    }

    private List<Cell> findConnectorPath(Cell start, Set<Cell> targets, List<Bounds> blockedBounds) {
        if (!insideBuildArea(start) || targets.isEmpty()) {
            return List.of();
        }
        List<Cell> candidates = closestTargets(start, targets);
        for (Cell target : candidates) {
            for (int attempt = 0; attempt < connectorCandidateAttempts(); attempt++) {
                List<Cell> path = connectorCandidatePath(start, target, attempt);
                if (isValidConnectorPath(path, start, targets, blockedBounds)) {
                    return path;
                }
            }
        }
        return List.of();
    }

    private int connectorCandidateAttempts() {
        return switch (corridorMode) {
            case CAVE -> 44;
            case LARGE_CORRIDORS, LIBRARY -> 2;
            case BUNKER -> 18;
            case MAZE, BACKROOMS -> 18;
            default -> CONNECTOR_CANDIDATE_ATTEMPTS;
        };
    }

    private List<Cell> closestTargets(Cell start, Set<Cell> targets) {
        List<Cell> sorted = new ArrayList<>(targets);
        sorted.sort(java.util.Comparator.comparingInt(target -> manhattanDistance(start, target)));
        if (sorted.size() > MAX_CONNECTOR_TARGETS) {
            sorted = new ArrayList<>(sorted.subList(0, MAX_CONNECTOR_TARGETS));
        }
        Collections.shuffle(sorted, random);
        return sorted;
    }

    private List<Cell> connectorCandidatePath(Cell start, Cell target, int attempt) {
        if (corridorMode == CorridorMode.CAVE) {
            return caveCandidatePath(start, target);
        }
        if (corridorMode == CorridorMode.BUNKER) {
            return bunkerCandidatePath(start, target, attempt);
        }
        return orthogonalCandidatePath(start, target, attempt);
    }

    private List<Cell> bunkerCandidatePath(Cell start, Cell target, int attempt) {
        List<Cell> waypoints = new ArrayList<>();
        boolean horizontalFirst = attempt % 2 == 0;
        int detour = 4 + random.nextInt(13);
        if (horizontalFirst) {
            int x = clamp(random.nextBoolean() ? Math.max(start.x(), target.x()) + detour : Math.min(start.x(), target.x()) - detour,
                    originX - clearRadius + 2, originX + clearRadius - 2);
            int zJitter = clamp((start.z() + target.z()) / 2 + random.nextInt(13) - 6,
                    originZ - clearRadius + 2, originZ + clearRadius - 2);
            waypoints.add(new Cell(x, start.z()));
            waypoints.add(new Cell(x, zJitter));
            waypoints.add(new Cell(target.x(), zJitter));
        } else {
            int z = clamp(random.nextBoolean() ? Math.max(start.z(), target.z()) + detour : Math.min(start.z(), target.z()) - detour,
                    originZ - clearRadius + 2, originZ + clearRadius - 2);
            int xJitter = clamp((start.x() + target.x()) / 2 + random.nextInt(13) - 6,
                    originX - clearRadius + 2, originX + clearRadius - 2);
            waypoints.add(new Cell(start.x(), z));
            waypoints.add(new Cell(xJitter, z));
            waypoints.add(new Cell(xJitter, target.z()));
        }
        waypoints.add(target);
        return pathThrough(start, waypoints);
    }

    private List<Cell> orthogonalCandidatePath(Cell start, Cell target, int attempt) {
        List<Cell> waypoints = new ArrayList<>();
        boolean horizontalFirst = attempt % 2 == 0;
        int detour = corridorMode == CorridorMode.LARGE_CORRIDORS
                || (corridorMode == CorridorMode.LIBRARY && attempt < 8)
                || attempt < 4 ? 0 : 2 + random.nextInt(9);
        if (detour == 0) {
            waypoints.add(horizontalFirst ? new Cell(target.x(), start.z()) : new Cell(start.x(), target.z()));
        } else if (horizontalFirst) {
            int x = clamp(random.nextBoolean() ? Math.max(start.x(), target.x()) + detour : Math.min(start.x(), target.x()) - detour,
                    originX - clearRadius + 2, originX + clearRadius - 2);
            waypoints.add(new Cell(x, start.z()));
            waypoints.add(new Cell(x, target.z()));
        } else {
            int z = clamp(random.nextBoolean() ? Math.max(start.z(), target.z()) + detour : Math.min(start.z(), target.z()) - detour,
                    originZ - clearRadius + 2, originZ + clearRadius - 2);
            waypoints.add(new Cell(start.x(), z));
            waypoints.add(new Cell(target.x(), z));
        }
        waypoints.add(target);
        return pathThrough(start, waypoints);
    }

    private List<Cell> caveCandidatePath(Cell start, Cell target) {
        List<Cell> path = new ArrayList<>();
        Set<Cell> seen = new HashSet<>();
        Cell current = start;
        path.add(current);
        seen.add(current);
        int directDistance = manhattanDistance(start, target);
        int maxSteps = Math.max(32, directDistance * 4 + 32);
        for (int step = 0; step < maxSteps && !current.equals(target); step++) {
            List<BlockFace> faces = caveStepFaces(current, target);
            Cell next = null;
            int distance = manhattanDistance(current, target);
            for (BlockFace face : faces) {
                Cell candidate = step(current, face);
                if (!insideBuildArea(candidate) || (seen.contains(candidate) && !candidate.equals(target))) {
                    continue;
                }
                int candidateDistance = manhattanDistance(candidate, target);
                if (candidateDistance > distance + 1 && random.nextInt(100) < 80) {
                    continue;
                }
                next = candidate;
                break;
            }
            if (next == null) {
                return List.of();
            }
            current = next;
            path.add(current);
            seen.add(current);
        }
        return current.equals(target) ? path : List.of();
    }

    private List<BlockFace> caveStepFaces(Cell current, Cell target) {
        List<BlockFace> pull = new ArrayList<>();
        if (current.x() < target.x()) {
            pull.add(BlockFace.EAST);
        } else if (current.x() > target.x()) {
            pull.add(BlockFace.WEST);
        }
        if (current.z() < target.z()) {
            pull.add(BlockFace.SOUTH);
        } else if (current.z() > target.z()) {
            pull.add(BlockFace.NORTH);
        }
        Collections.shuffle(pull, random);

        List<BlockFace> faces = new ArrayList<>();
        if (!pull.isEmpty() && random.nextInt(100) < 68) {
            faces.add(pull.getFirst());
        }
        List<BlockFace> side = new ArrayList<>(List.of(CARDINAL_FACES));
        side.removeAll(pull);
        Collections.shuffle(side, random);
        faces.addAll(side);
        faces.addAll(pull);
        for (BlockFace face : CARDINAL_FACES) {
            if (!faces.contains(face)) {
                faces.add(face);
            }
        }
        return faces;
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

    private boolean isValidConnectorPath(List<Cell> path, Cell start, Set<Cell> targets, List<Bounds> blockedBounds) {
        if (path.size() < 2 || (corridorMode != CorridorMode.CAVE && hasShortZigzags(path))) {
            return false;
        }
        Set<Cell> seen = new HashSet<>();
        for (int i = 0; i < path.size(); i++) {
            Cell cell = path.get(i);
            if (!seen.add(cell)) {
                return false;
            }
            if (i > 0 && manhattanDistance(path.get(i - 1), cell) != 1) {
                return false;
            }
            boolean target = i == path.size() - 1 && targets.contains(cell);
            if (!canCorridorOccupy(cell, start, target, blockedBounds, isNearConnectorEndpoint(cell, start, targets))) {
                return false;
            }
        }
        return targets.contains(path.getLast());
    }

    private boolean isNearConnectorEndpoint(Cell cell, Cell start, Set<Cell> targets) {
        if (manhattanDistance(cell, start) <= 2) {
            return true;
        }
        for (Cell target : targets) {
            if (manhattanDistance(cell, target) <= 2) {
                return true;
            }
        }
        return false;
    }

    private boolean canCorridorOccupy(Cell cell, Cell start, boolean target, Bounds blockedCandidate) {
        return canCorridorOccupy(cell, start, target, blockedCandidate == null ? List.of() : List.of(blockedCandidate), true);
    }

    private boolean canCorridorOccupy(Cell cell,
                                      Cell start,
                                      boolean target,
                                      List<Bounds> blockedBounds,
                                      boolean allowRoomShellAdjacency) {
        if (!insideBuildArea(cell) || protectedElevator.contains(cell.x(), cell.z())) {
            return false;
        }
        if (!cell.equals(start) && !target) {
            for (Bounds blocked : blockedBounds) {
                if (blocked.contains(cell.x(), cell.z())) {
                    return false;
                }
            }
        }
        if (cell.equals(start) || target || corridorCells.contains(cell)) {
            return true;
        }
        if (allowRoomShellAdjacency) {
            return !roomShellCells.contains(cell) && !roomInteriorCells.contains(cell);
        }
        return !roomShellCells.contains(cell) && !roomInteriorCells.contains(cell)
                && !isAdjacentToRoomShell(cell);
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
        rememberCorridor(path, false);
    }

    private void rememberCorridor(List<Cell> path, boolean forceLibraryVent) {
        if (path.isEmpty()) {
            return;
        }
        Set<Cell> carved = switch (corridorMode) {
            case CAVE -> naturalCaveCorridorCells(path);
            case LARGE_CORRIDORS -> largeCorridorCells(path);
            case LIBRARY -> libraryCorridorCells(path, forceLibraryVent);
            case SEWER -> sewerCorridorCells(path);
            case BUNKER -> new HashSet<>(path);
            case MAZE, BACKROOMS, OPEN_HALLS -> openHallConnectorCells(path);
            case NORMAL -> new HashSet<>(path);
        };
        corridorCells.addAll(carved);
        networkCells.addAll(carved);
        for (Cell point : carved) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    corridorShellCells.add(new Cell(point.x() + dx, point.z() + dz));
                }
            }
        }
    }

    private Set<Cell> largeCorridorCells(List<Cell> path) {
        Set<Cell> cells = new HashSet<>(path);
        for (int i = 0; i < path.size(); i++) {
            Cell current = path.get(i);
            boolean eastWest = isEastWestSegment(path, i);
            List<Cell> widened = eastWest
                    ? List.of(new Cell(current.x(), current.z() - 1), new Cell(current.x(), current.z() + 1))
                    : List.of(new Cell(current.x() - 1, current.z()), new Cell(current.x() + 1, current.z()));
            for (Cell cell : widened) {
                if (canWidenCorridorInto(cell)) {
                    cells.add(cell);
                }
            }
        }
        return cells;
    }

    private boolean shouldUseLibraryVentConnector(List<Cell> path) {
        return false;
    }

    private Set<Cell> libraryCorridorCells(List<Cell> path, boolean forceVent) {
        boolean vent = forceVent && path.size() >= 7;
        Set<Cell> cells = vent ? new HashSet<>(path) : largeCorridorCells(path);
        if (vent) {
            lowCeilingCorridorCells.addAll(cells);
        }
        return cells;
    }

    private Set<Cell> sewerCorridorCells(List<Cell> path) {
        Set<Cell> cells = new HashSet<>(path);
        for (int i = 0; i < path.size(); i++) {
            Cell current = path.get(i);
            if (i < 3 || i > path.size() - 4) {
                cells.add(current);
                continue;
            }
            boolean eastWest = isEastWestSegment(path, i);
            int dxMin = eastWest ? 0 : -2;
            int dxMax = eastWest ? 0 : 2;
            int dzMin = eastWest ? -2 : 0;
            int dzMax = eastWest ? 2 : 0;
            for (int dx = dxMin; dx <= dxMax; dx++) {
                for (int dz = dzMin; dz <= dzMax; dz++) {
                    Cell cell = new Cell(current.x() + dx, current.z() + dz);
                    if (cell.equals(current) || canWidenCorridorInto(cell)) {
                        cells.add(cell);
                        if (Math.abs(eastWest ? dz : dx) <= 1) {
                            liquidCells.add(cell);
                        }
                    }
                }
            }
        }
        return cells;
    }

    private Set<Cell> naturalCaveCorridorCells(List<Cell> path) {
        Set<Cell> cells = new HashSet<>();
        for (int i = 0; i < path.size(); i++) {
            Cell current = path.get(i);
            int radius = random.nextInt(100) < 76 ? 1 : 2;
            carveDisc(cells, current, radius);
            if (i > 0 && i < path.size() - 1 && random.nextInt(100) < 35) {
                BlockFace side = random.nextBoolean()
                        ? turnLeft(directionBetween(path.get(i - 1), current))
                        : turnRight(directionBetween(path.get(i - 1), current));
                carveDisc(cells, step(current, side), 1);
            }
        }
        return cells;
    }

    private void carveDisc(Set<Cell> cells, Cell center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > radius + random.nextInt(2)) {
                    continue;
                }
                Cell cell = new Cell(center.x() + dx, center.z() + dz);
                if (cell.equals(center) || canWidenCorridorInto(cell)) {
                    cells.add(cell);
                }
            }
        }
    }

    private Set<Cell> openHallConnectorCells(List<Cell> path) {
        Set<Cell> cells = new HashSet<>(path);
        for (Cell current : path) {
            for (BlockFace face : CARDINAL_FACES) {
                Cell cell = step(current, face);
                if (canWidenCorridorInto(cell)) {
                    cells.add(cell);
                }
            }
        }
        return cells;
    }

    private boolean isEastWestSegment(List<Cell> path, int index) {
        Cell current = path.get(index);
        if (index > 0 && path.get(index - 1).z() == current.z() && path.get(index - 1).x() != current.x()) {
            return true;
        }
        return index < path.size() - 1 && path.get(index + 1).z() == current.z() && path.get(index + 1).x() != current.x();
    }

    private boolean canWidenCorridorInto(Cell cell) {
        return insideBuildArea(cell)
                && !protectedElevator.contains(cell.x(), cell.z())
                && !roomShellCells.contains(cell)
                && !roomInteriorCells.contains(cell);
    }

    private void addFirstRoomOnwardRoutes() {
        if (rooms.size() < 3) {
            return;
        }
        Room first = rooms.getFirst();
        int wantedOpenings = Math.min(3, Math.max(2, rooms.size() / 5));
        int attempts = 0;
        while (first.openings().size() < wantedOpenings && attempts++ < rooms.size() * 8) {
            Room target = rooms.get(1 + random.nextInt(rooms.size() - 1));
            if (connectRooms(first, target, true)) {
                continue;
            }
            connectRooms(target, first, true);
        }
    }

    private void addRoomToRoomLoops() {
        int target = corridorMode == CorridorMode.MAZE ? Math.max(rooms.size() / 4, 3) : Math.max(rooms.size() / 2, 5);
        int added = 0;
        int attempts = 0;
        while (added < target && attempts++ < roomLoopAttemptLimit()) {
            Room from = rooms.get(random.nextInt(rooms.size()));
            Room to = rooms.get(random.nextInt(rooms.size()));
            if (from == to || manhattanDistance(new Cell(from.centerX(), from.centerZ()), new Cell(to.centerX(), to.centerZ())) < 12) {
                continue;
            }
            if (connectRooms(from, to, false)) {
                added++;
            }
        }
    }

    private boolean connectRooms(Room from, Room to, boolean allowShort) {
        List<BlockFace> fromFaces = availableFaces(from);
        List<BlockFace> toFaces = availableFaces(to);
        Collections.shuffle(fromFaces, random);
        Collections.shuffle(toFaces, random);
        for (BlockFace fromFace : fromFaces) {
            int fromOffset = doorOffset(from.layout(), fromFace);
            Cell fromDoor = doorCell(from, fromFace, fromOffset);
            for (BlockFace toFace : toFaces) {
                int toOffset = doorOffset(to.layout(), toFace);
                Cell toDoor = doorCell(to, toFace, toOffset);
                List<Cell> path = findConnectorPath(fromDoor, Set.of(toDoor), List.of(Bounds.of(from), Bounds.of(to)));
                if (path.size() < (allowShort ? 4 : 10)) {
                    continue;
                }
                from.openings().put(fromFace, fromOffset);
                to.openings().put(toFace, toOffset);
                networkCells.add(fromDoor);
                networkCells.add(toDoor);
                rememberCorridor(path, shouldUseLibraryVentConnector(path));
                return true;
            }
        }
        return false;
    }

    private List<BlockFace> availableFaces(Room room) {
        List<BlockFace> faces = new ArrayList<>();
        for (BlockFace face : CARDINAL_FACES) {
            if (!room.openings().containsKey(face) && !validDoorOffsets(room.layout(), face).isEmpty()) {
                faces.add(face);
            }
        }
        return faces;
    }

    private void addMazeBranches(int targetBranches) {
        List<Cell> starts = new ArrayList<>(networkCells);
        if (starts.isEmpty()) {
            return;
        }
        int added = 0;
        int attempts = 0;
        while (added < targetBranches && attempts++ < targetBranches * 18) {
            Cell current = starts.get(random.nextInt(starts.size()));
            BlockFace direction = randomFace();
            List<Cell> branch = new ArrayList<>();
            int segmentCount = 2 + random.nextInt(4);
            boolean failed = false;
            for (int segment = 0; segment < segmentCount && !failed; segment++) {
                int length = 3 + random.nextInt(8);
                for (int step = 0; step < length; step++) {
                    Cell next = step(current, direction);
                    if (!canCorridorOccupy(next, current, false, List.of(), false)) {
                        failed = true;
                        break;
                    }
                    branch.add(next);
                    current = next;
                }
                direction = randomTurn(direction);
            }
            if (branch.size() < 8 || hasShortZigzags(branch)) {
                continue;
            }
            rememberCorridor(branch);
            starts.addAll(branch);
            added++;
        }
    }

    private void generateBunkerLayout(List<HallsLayout> layouts, int targetRooms) {
        List<Cell> trunk = bunkerTrunkPath();
        rememberBunkerMainCorridor(trunk);
        int attempts = 0;
        while (rooms.size() < targetRooms && attempts++ < targetRooms * 120) {
            HallsLayout layout = layouts.get(random.nextInt(layouts.size()));
            if (placeBunkerRoom(layout, trunk)) {
                continue;
            }
            trunk = new ArrayList<>(corridorCells);
        }
        addRoomToRoomLoops();
        int extraBranches = Math.max(4, rooms.size() / 2);
        addMazeBranches(extraBranches);
    }

    private List<Cell> bunkerTrunkPath() {
        int radius = Math.min(clearRadius - 10, Math.max(32, 38 + rooms.size() * 2));
        int frontZ = elevatorFrontCell(4).z();
        int northZ = clamp(originZ - radius, originZ - clearRadius + 3, originZ + clearRadius - 3);
        int southZ = clamp(originZ + radius / 2, originZ - clearRadius + 3, originZ + clearRadius - 3);
        int eastX = clamp(originX + radius, originX - clearRadius + 3, originX + clearRadius - 3);
        int westX = clamp(originX - radius, originX - clearRadius + 3, originX + clearRadius - 3);
        List<Cell> waypoints = List.of(
                new Cell(originX, northZ),
                new Cell(eastX, northZ),
                new Cell(eastX, southZ),
                new Cell(westX, southZ),
                new Cell(westX, frontZ),
                new Cell(originX, frontZ)
        );
        return pathThrough(elevatorFrontCell(1), waypoints);
    }

    private boolean placeBunkerRoom(HallsLayout layout, List<Cell> trunk) {
        List<Cell> anchors = new ArrayList<>(trunk);
        Collections.shuffle(anchors, random);
        for (Cell anchor : anchors) {
            List<BlockFace> faces = new ArrayList<>(List.of(CARDINAL_FACES));
            Collections.shuffle(faces, random);
            for (BlockFace attachFace : faces) {
                BlockFace roomFace = attachFace.getOppositeFace();
                if (validDoorOffsets(layout, roomFace).isEmpty()) {
                    continue;
                }
                int offset = doorOffset(layout, roomFace);
                int gap = 3 + random.nextInt(4);
                Cell door = anchor;
                for (int i = 0; i < gap; i++) {
                    door = step(door, attachFace);
                }
                Room room = bunkerRoomFromDoor(layout, roomFace, offset, door);
                if (!canPlaceRoom(room)) {
                    continue;
                }
                List<Cell> path = pathThrough(anchor, List.of(door));
                if (!isValidConnectorPath(path, anchor, Set.of(door), List.of(Bounds.of(room)))) {
                    continue;
                }
                room.openings().put(roomFace, offset);
                addRoom(room);
                rememberCorridor(path);
                return true;
            }
        }
        return false;
    }

    private Room bunkerRoomFromDoor(HallsLayout layout, BlockFace roomFace, int offset, Cell door) {
        return switch (roomFace) {
            case NORTH -> new Room(layout, door.x() - offset, door.z() + 1);
            case SOUTH -> new Room(layout, door.x() - offset, door.z() - layout.depth());
            case EAST -> new Room(layout, door.x() - layout.width(), door.z() - offset);
            case WEST -> new Room(layout, door.x() + 1, door.z() - offset);
            default -> new Room(layout, door.x() - layout.width() / 2, door.z() - layout.depth() / 2);
        };
    }

    private void rememberBunkerMainCorridor(List<Cell> path) {
        Set<Cell> carved = largeCorridorCells(path);
        corridorCells.addAll(carved);
        networkCells.addAll(carved);
        for (Cell point : carved) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    corridorShellCells.add(new Cell(point.x() + dx, point.z() + dz));
                }
            }
        }
    }

    private void markLibraryVentGates() {
        if (lowCeilingCorridorCells.isEmpty()) {
            return;
        }
        for (Cell vent : lowCeilingCorridorCells) {
            BlockFace face = libraryVentGateFace(vent);
            if (face != BlockFace.SELF && libraryVentGateHasSideWalls(vent, face)) {
                ventGateCells.add(vent);
            }
        }
        for (Room room : rooms) {
            for (Map.Entry<BlockFace, Integer> opening : room.openings().entrySet()) {
                Cell door = doorCell(room, opening.getKey(), opening.getValue());
                if (lowCeilingCorridorCells.contains(door) && libraryVentGateHasSideWalls(door, opening.getKey().getOppositeFace())) {
                    ventGateCells.add(door);
                    continue;
                }
                Cell outside = step(door, opening.getKey());
                if (lowCeilingCorridorCells.contains(outside) && libraryVentGateHasSideWalls(outside, opening.getKey().getOppositeFace())) {
                    ventGateCells.add(outside);
                }
            }
        }
    }

    private BlockFace libraryVentGateFace(Cell vent) {
        for (BlockFace face : CARDINAL_FACES) {
            Cell neighbor = step(vent, face);
            if (corridorCells.contains(neighbor) && !lowCeilingCorridorCells.contains(neighbor)) {
                return face;
            }
        }
        return BlockFace.SELF;
    }

    private boolean libraryVentGateHasSideWalls(Cell vent, BlockFace transitionFace) {
        if (transitionFace == BlockFace.SELF) {
            return false;
        }
        boolean eastWestTransition = transitionFace == BlockFace.EAST || transitionFace == BlockFace.WEST;
        Cell firstSide = eastWestTransition
                ? new Cell(vent.x(), vent.z() - 1)
                : new Cell(vent.x() - 1, vent.z());
        Cell secondSide = eastWestTransition
                ? new Cell(vent.x(), vent.z() + 1)
                : new Cell(vent.x() + 1, vent.z());
        return libraryVentSideIsWall(firstSide) && libraryVentSideIsWall(secondSide);
    }

    private boolean libraryVentSideIsWall(Cell cell) {
        return !corridorCells.contains(cell) && !roomInteriorCells.contains(cell);
    }

    private void widenBunkerSpine() {
        if (rooms.isEmpty()) {
            return;
        }
        Set<Cell> carved = largeCorridorCells(new ArrayList<>(corridorCells));
        corridorCells.addAll(carved);
        networkCells.addAll(carved);
        for (Cell point : carved) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    corridorShellCells.add(new Cell(point.x() + dx, point.z() + dz));
                }
            }
        }
    }

    private void addRoomLocalOpenHalls() {
        Set<Cell> mazeArea = mazeAreaCells();
        if (mazeArea.isEmpty()) {
            return;
        }
        List<Cell> starts = new ArrayList<>(networkCells.stream()
                .filter(mazeArea::contains)
                .toList());
        if (starts.isEmpty()) {
            starts = mazeStartsFromRoomDoors(mazeArea);
        }
        if (starts.isEmpty()) {
            return;
        }
        Set<Cell> mazeCells = new HashSet<>();
        Cell start = starts.get(random.nextInt(starts.size()));
        ArrayDeque<Cell> stack = new ArrayDeque<>();
        stack.push(start);
        mazeCells.add(start);
        int targetCells = Math.min(mazeArea.size(), Math.max(160, rooms.size() * 80));
        int attempts = 0;
        Map<Cell, List<OpenHallStep>> steps = openHallSteps(mazeArea);
        while (!stack.isEmpty() && mazeCells.size() < targetCells && attempts++ < targetCells * 20) {
            Cell current = stack.peek();
            List<OpenHallStep> candidates = new ArrayList<>(steps.getOrDefault(current, List.of()));
            Collections.shuffle(candidates, random);
            Cell next = null;
            for (OpenHallStep candidate : candidates) {
                if (!mazeCells.contains(candidate.target()) && !mazeCells.contains(candidate.between())) {
                    next = candidate.target();
                    mazeCells.add(candidate.between());
                    mazeCells.add(candidate.target());
                    break;
                }
            }
            if (next == null) {
                stack.pop();
            } else {
                stack.push(next);
            }
        }
        if (mazeCells.isEmpty()) {
            return;
        }
        rememberCorridor(new ArrayList<>(mazeCells));
        addMazeRoomOpenings(mazeCells);
    }

    private Map<Cell, List<OpenHallStep>> openHallSteps(Set<Cell> mazeArea) {
        Map<Cell, List<OpenHallStep>> steps = new HashMap<>();
        for (Cell cell : mazeArea) {
            List<OpenHallStep> candidates = new ArrayList<>(4);
            for (BlockFace face : CARDINAL_FACES) {
                Cell between = step(cell, face);
                Cell target = step(between, face);
                if (mazeArea.contains(between) && mazeArea.contains(target)) {
                    candidates.add(new OpenHallStep(between, target));
                }
            }
            steps.put(cell, List.copyOf(candidates));
        }
        return steps;
    }

    private void addGridOpenHalls() {
        Set<Cell> openCells = cellsWithinRoomDistance(8);
        openCells.removeIf(this::isOpenHallStructuralBlock);
        if (openCells.isEmpty()) {
            return;
        }
        corridorCells.addAll(openCells);
        networkCells.addAll(openCells);
        for (Cell point : openCells) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    corridorShellCells.add(new Cell(point.x() + dx, point.z() + dz));
                }
            }
        }
        addOpenHallRoomOpenings(openCells);
    }

    private void addBackroomsGridOpenHalls() {
        Set<Cell> openCells = cellsWithinRoomDistance(9);
        openCells.removeIf(this::isBackroomsColumnBlock);
        addBackroomsLongWalls(openCells);
        if (openCells.isEmpty()) {
            return;
        }
        corridorCells.addAll(openCells);
        networkCells.addAll(openCells);
        for (Cell point : openCells) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    corridorShellCells.add(new Cell(point.x() + dx, point.z() + dz));
                }
            }
        }
        addOpenHallRoomOpenings(openCells);
    }

    private boolean isBackroomsColumnBlock(Cell cell) {
        int gridX = Math.floorMod(cell.x(), 11);
        int gridZ = Math.floorMod(cell.z(), 11);
        return gridX == 0 && gridZ == 0 && random.nextInt(100) < 55;
    }

    private void addBackroomsLongWalls(Set<Cell> openCells) {
        if (openCells.isEmpty()) {
            return;
        }
        int targetWalls = Math.max(8, rooms.size() * 3);
        int added = 0;
        int attempts = 0;
        List<Cell> starts = new ArrayList<>(openCells);
        while (added < targetWalls && attempts++ < targetWalls * 18) {
            Cell start = starts.get(random.nextInt(starts.size()));
            boolean eastWest = random.nextBoolean();
            int length = 5 + random.nextInt(12);
            Set<Cell> wall = new HashSet<>();
            for (int step = 0; step < length; step++) {
                Cell cell = eastWest
                        ? new Cell(start.x() + step, start.z())
                        : new Cell(start.x(), start.z() + step);
                if (openCells.contains(cell) && !nearRoomDoor(cell)) {
                    wall.add(cell);
                }
            }
            if (wall.size() < 4) {
                continue;
            }
            List<Cell> ordered = new ArrayList<>(wall);
            ordered.sort(eastWest
                    ? java.util.Comparator.comparingInt(Cell::x)
                    : java.util.Comparator.comparingInt(Cell::z));
            if (ordered.size() >= 7) {
                wall.remove(ordered.get(2 + random.nextInt(ordered.size() - 4)));
            }
            Set<Cell> trialOpen = new HashSet<>(openCells);
            trialOpen.removeAll(wall);
            Set<Cell> trialWalkable = new HashSet<>(roomInteriorCells);
            trialWalkable.addAll(corridorCells);
            trialWalkable.addAll(trialOpen);
            if (allRoomsReachable(trialWalkable)) {
                openCells.clear();
                openCells.addAll(trialOpen);
                added++;
            }
        }
    }

    private boolean nearRoomDoor(Cell cell) {
        for (Room room : rooms) {
            for (Map.Entry<BlockFace, Integer> opening : room.openings().entrySet()) {
                Cell door = doorCell(room, opening.getKey(), opening.getValue());
                if (manhattanDistance(cell, door) <= 2 || manhattanDistance(cell, step(door, opening.getKey())) <= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isOpenHallStructuralBlock(Cell cell) {
        int gridX = Math.floorMod(cell.x(), 7);
        int gridZ = Math.floorMod(cell.z(), 7);
        if (gridX == 0 && gridZ == 0) {
            return true;
        }
        boolean northSouthRib = gridX == 0 && gridZ >= 2 && gridZ <= 4;
        boolean eastWestRib = gridZ == 0 && gridX >= 2 && gridX <= 4;
        return (northSouthRib || eastWestRib) && random.nextInt(100) < 34;
    }

    private void addOpenHallRoomOpenings(Set<Cell> openCells) {
        for (Room room : rooms) {
            List<DoorCandidate> doors = new ArrayList<>();
            for (BlockFace face : CARDINAL_FACES) {
                for (int offset : validDoorOffsets(room.layout(), face)) {
                    Cell door = doorCell(room, face, offset);
                    if (openCells.contains(door) || openCells.contains(step(door, face))) {
                        doors.add(new DoorCandidate(face, offset));
                    }
                }
            }
            Collections.shuffle(doors, random);
            int wanted = Math.min(4, Math.max(2, doors.size() / 3));
            int added = 0;
            for (DoorCandidate door : doors) {
                if (added >= wanted) {
                    break;
                }
                if (!room.openings().containsKey(door.face())) {
                    room.openings().put(door.face(), door.offset());
                    rememberCorridor(List.of(doorCell(room, door.face(), door.offset())));
                    added++;
                }
            }
        }
    }

    private Set<Cell> mazeAreaCells() {
        return cellsWithinRoomDistance(10);
    }

    private Set<Cell> cellsWithinRoomDistance(int distance) {
        Set<Cell> cells = new HashSet<>();
        for (Room room : rooms) {
            Bounds roomBounds = Bounds.of(room);
            Bounds bounds = roomBounds.inflate(distance);
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    Cell cell = new Cell(x, z);
                    if (distanceFromBounds(cell, roomBounds) <= distance && canWidenCorridorInto(cell)) {
                        cells.add(cell);
                    }
                }
            }
        }
        return cells;
    }

    private List<Cell> mazeStartsFromRoomDoors(Set<Cell> mazeArea) {
        List<Cell> starts = new ArrayList<>();
        for (Room room : rooms) {
            for (Map.Entry<BlockFace, Integer> opening : room.openings().entrySet()) {
                Cell door = doorCell(room, opening.getKey(), opening.getValue());
                Cell outside = step(door, opening.getKey());
                if (mazeArea.contains(outside)) {
                    starts.add(outside);
                }
            }
        }
        return starts;
    }

    private int distanceFromBounds(Cell cell, Bounds bounds) {
        int dx = cell.x() < bounds.minX() ? bounds.minX() - cell.x()
                : cell.x() > bounds.maxX() ? cell.x() - bounds.maxX()
                : 0;
        int dz = cell.z() < bounds.minZ() ? bounds.minZ() - cell.z()
                : cell.z() > bounds.maxZ() ? cell.z() - bounds.maxZ()
                : 0;
        return dx + dz;
    }

    private void addMazeRoomOpenings(Set<Cell> mazeCells) {
        for (Room room : rooms) {
            List<DoorCandidate> doors = new ArrayList<>();
            for (BlockFace face : CARDINAL_FACES) {
                for (int offset : validDoorOffsets(room.layout(), face)) {
                    Cell door = doorCell(room, face, offset);
                    if (mazeCells.contains(door) || mazeCells.contains(step(door, face))) {
                        doors.add(new DoorCandidate(face, offset));
                    }
                }
            }
            Collections.shuffle(doors, random);
            int added = 0;
            for (DoorCandidate door : doors) {
                if (added >= 2) {
                    break;
                }
                if (!room.openings().containsKey(door.face())) {
                    room.openings().put(door.face(), door.offset());
                    rememberCorridor(List.of(doorCell(room, door.face(), door.offset())));
                    added++;
                }
            }
        }
    }

    private BlockFace randomTurn(BlockFace previous) {
        List<BlockFace> faces = new ArrayList<>(List.of(CARDINAL_FACES));
        faces.remove(previous.getOppositeFace());
        Collections.shuffle(faces, random);
        return faces.getFirst();
    }

    private int roomPlacementAttemptLimit(int targetRooms) {
        if (corridorMode == CorridorMode.BACKROOMS) {
            return targetRooms * 420;
        }
        return targetRooms * (corridorMode == CorridorMode.MAZE ? 260 : 1000);
    }

    private int roomLoopAttemptLimit() {
        int roomCount = Math.max(1, rooms.size());
        return (corridorMode == CorridorMode.MAZE || corridorMode == CorridorMode.BACKROOMS)
                ? roomCount * 18
                : roomCount * roomCount * 5;
    }

    private BlockFace directionBetween(Cell from, Cell to) {
        if (to.x() > from.x()) {
            return BlockFace.EAST;
        }
        if (to.x() < from.x()) {
            return BlockFace.WEST;
        }
        if (to.z() > from.z()) {
            return BlockFace.SOUTH;
        }
        if (to.z() < from.z()) {
            return BlockFace.NORTH;
        }
        return randomFace();
    }

    private BlockFace turnLeft(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            case WEST -> BlockFace.SOUTH;
            default -> randomFace();
        };
    }

    private BlockFace turnRight(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case SOUTH -> BlockFace.WEST;
            case EAST -> BlockFace.SOUTH;
            case WEST -> BlockFace.NORTH;
            default -> randomFace();
        };
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

    private boolean isAdjacentToRoomShell(Cell cell) {
        return roomShellCells.contains(new Cell(cell.x() + 1, cell.z()))
                || roomShellCells.contains(new Cell(cell.x() - 1, cell.z()))
                || roomShellCells.contains(new Cell(cell.x(), cell.z() + 1))
                || roomShellCells.contains(new Cell(cell.x(), cell.z() - 1));
    }

    private int manhattanDistance(Cell first, Cell second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.z() - second.z());
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean hasShortZigzags(List<Cell> path) {
        if (path.size() < 6) {
            return false;
        }
        List<Integer> runLengths = new ArrayList<>();
        Character currentAxis = null;
        int currentRun = 0;
        for (int i = 1; i < path.size(); i++) {
            Cell previous = path.get(i - 1);
            Cell current = path.get(i);
            char axis = previous.x() == current.x() ? 'z' : 'x';
            if (currentAxis == null || currentAxis == axis) {
                currentAxis = axis;
                currentRun++;
            } else {
                runLengths.add(currentRun);
                currentAxis = axis;
                currentRun = 1;
            }
        }
        runLengths.add(currentRun);
        for (int i = 1; i < runLengths.size() - 1; i++) {
            if (runLengths.get(i) < 2) {
                return true;
            }
        }
        return runLengths.size() >= 4
                && runLengths.stream().filter(length -> length <= 2).count() >= runLengths.size() - 1L;
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
                Set.copyOf(lowCeilingCorridorCells),
                Set.copyOf(ventGateCells),
                Set.copyOf(liquidCells),
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
        Cell start = elevatorFrontCell(1);
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
                Set<Cell> lowCeilingCorridorCells,
                Set<Cell> ventGateCells,
                Set<Cell> liquidCells,
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

    private record RoomConnection(Room anchor,
                                  Room room,
                                  BlockFace anchorFace,
                                  BlockFace roomFace,
                                  int anchorOffset,
                                  int roomOffset) {
    }

    private record DoorCandidate(BlockFace face, int offset) {
    }

    private record DoorOffsetKey(HallsLayout layout, BlockFace face) {
    }

    private record OpenHallStep(Cell between, Cell target) {
    }

    private enum CorridorMode {
        NORMAL,
        CAVE,
        LARGE_CORRIDORS,
        MAZE,
        BACKROOMS,
        OPEN_HALLS,
        SEWER,
        LIBRARY,
        BUNKER;

        private static CorridorMode from(String value) {
            if (value == null) {
                return NORMAL;
            }
            return switch (value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_')) {
                case "cave", "caves", "natural" -> CAVE;
                case "large_corridors", "large_corridor", "wide", "wide_corridors" -> LARGE_CORRIDORS;
                case "maze", "mazelike", "deep_crypt" -> MAZE;
                case "backrooms", "backroom" -> BACKROOMS;
                case "open_halls", "open_hall", "legacy_maze" -> OPEN_HALLS;
                case "sewer", "sewers" -> SEWER;
                case "library", "library_vents", "library_corridors" -> LIBRARY;
                case "bunker", "bunker_corridors" -> BUNKER;
                default -> NORMAL;
            };
        }
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
