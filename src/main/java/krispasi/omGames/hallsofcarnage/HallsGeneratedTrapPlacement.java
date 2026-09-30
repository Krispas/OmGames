package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import krispasi.omGames.hallsofcarnage.HallsSessionTrapRuntime.TrapCandidate;
import krispasi.omGames.hallsofcarnage.HallsSessionTrapRuntime.TrapKind;

final class HallsGeneratedTrapPlacement {
    private final HallsSessionTrapRuntime runtime;
    private final HallsExplorationGenerator.Plan plan;
    private final Random random;
    private final HallsFloorModifiers modifiers;
    private final Set<HallsExplorationGenerator.Cell> liquidCells;
    private final Set<HallsExplorationGenerator.Cell> occupied = new HashSet<>();
    private final List<HallsTrapType> pool;
    private final HallsTrapType holeType;
    private final boolean globalReachabilityChecks;
    private final int targetHoles;
    private final int targetTrappedRooms;
    private final int minTrapsPerRoom;
    private final int maxTrapsPerRoom;
    private final List<TrapCandidate> holes = new ArrayList<>();
    private final List<TrapCandidate> fallbackHoles = new ArrayList<>();
    private final Map<HallsExplorationGenerator.Room, List<TrapCandidate>> candidatesByRoom = new IdentityHashMap<>();
    private List<HallsExplorationGenerator.Room> rooms;
    private List<TrapCandidate> roomCandidates;
    private HallsTrapType roomType;
    private int preparedRooms;
    private int holeIndex;
    private int holesPlaced;
    private int roomIndex;
    private int roomsPlaced;
    private int candidateIndex;
    private int targetRoomTraps;
    private int placedInRoom;
    private boolean holesDone;
    private boolean done;
    private HallsTrapPlacementGeometry.FloorConnectivity floorConnectivity;

    HallsGeneratedTrapPlacement(HallsSessionTrapRuntime runtime, Map<String, HallsTrapType> trapTypes,
                               HallsExplorationGenerator.Plan plan, Random random,
                               HallsScenario.FloorDefinition floorDefinition, HallsLevelType levelType,
                               HallsFloorModifiers modifiers, Set<HallsExplorationGenerator.Cell> liquidCells) {
        this.runtime = runtime;
        this.plan = plan;
        this.random = random;
        this.modifiers = modifiers;
        this.liquidCells = liquidCells == null ? Set.of() : Set.copyOf(liquidCells);
        pool = runtime.trapPool(levelType.id());
        holeType = trapTypes.values().stream()
                .filter(type -> type.kind().equals("hole") && type.weight() > 0 && type.allowedForLevelType(levelType.id()))
                .findFirst().orElse(null);
        globalReachabilityChecks = runtime.globalReachabilityChecks(levelType);
        targetHoles = Math.max(0, floorDefinition.holes());
        targetTrappedRooms = Math.max(0, floorDefinition.trappedRooms());
        minTrapsPerRoom = Math.max(0, floorDefinition.minTrapsPerRoom());
        maxTrapsPerRoom = Math.max(minTrapsPerRoom, floorDefinition.maxTrapsPerRoom());
    }

    // Main-thread only. A single placement/reachability check is atomic and may exceed the budget.
    boolean tick() {
        if (done) {
            return true;
        }
        if ((pool.isEmpty() || targetTrappedRooms <= 0 || maxTrapsPerRoom <= 0)
                && (holeType == null || targetHoles <= 0)) {
            done = true;
            return true;
        }
        long started = System.nanoTime();
        for (int work = 0; work < 8 && !done; work++) {
            if (work > 0 && System.nanoTime() - started >= 4_000_000L) {
                break;
            }
            if (preparedRooms < plan.rooms().size()) {
                prepareRoom(plan.rooms().get(preparedRooms), preparedRooms);
                preparedRooms++;
                continue;
            }
            if (!holesDone) {
                if (rooms == null) {
                    holes.addAll(fallbackHoles);
                    fallbackHoles.clear();
                    Collections.shuffle(holes, random);
                    rooms = new ArrayList<>(candidatesByRoom.keySet());
                }
                if (holeType != null && holesPlaced < targetHoles && holeIndex < holes.size()) {
                    TrapCandidate candidate = holes.get(holeIndex++);
                    if (!liquidCells.contains(candidate.cell())
                            && runtime.placeHole(candidate, floorConnectivity(), random, holeType, occupied,
                            globalReachabilityChecks, modifiers, liquidCells)) {
                        holesPlaced++;
                    }
                    continue;
                }
                holesDone = true;
                Collections.shuffle(rooms, random);
                continue;
            }
            if (roomIndex >= rooms.size() || roomsPlaced >= targetTrappedRooms || pool.isEmpty()) {
                done = true;
                continue;
            }
            if (roomCandidates == null) {
                roomCandidates = new ArrayList<>(candidatesByRoom.get(rooms.get(roomIndex)));
                Collections.shuffle(roomCandidates, random);
                roomType = runtime.weightedTrap(pool, random, modifiers);
                if (runtime.trapKind(roomType.kind()) == TrapKind.SWINGING_BLADE) {
                    Map<TrapCandidate, Integer> scores = new IdentityHashMap<>();
                    for (TrapCandidate candidate : roomCandidates) {
                        scores.put(candidate, runtime.bestSwingLaneHalfSpan(candidate));
                    }
                    roomCandidates.sort((first, second) -> Integer.compare(scores.get(second), scores.get(first)));
                }
                targetRoomTraps = minTrapsPerRoom == maxTrapsPerRoom ? minTrapsPerRoom
                        : minTrapsPerRoom + random.nextInt(maxTrapsPerRoom - minTrapsPerRoom + 1);
                candidateIndex = 0;
                placedInRoom = 0;
                continue;
            }
            if (placedInRoom >= targetRoomTraps || candidateIndex >= roomCandidates.size()) {
                if (placedInRoom > 0) {
                    roomsPlaced++;
                }
                roomIndex++;
                roomCandidates = null;
                continue;
            }
            TrapCandidate candidate = roomCandidates.get(candidateIndex++);
            HallsTrapType type = random.nextInt(100) < 10 ? runtime.weightedTrap(pool, random, modifiers) : roomType;
            if (runtime.tryPlaceTrap(candidate, floorConnectivity(), random, type, occupied, globalReachabilityChecks, liquidCells)) {
                placedInRoom++;
            }
        }
        return done;
    }

    double progress() {
        if (done) {
            return 1.0;
        }
        if (preparedRooms < plan.rooms().size()) {
            return 0.25 * preparedRooms / plan.rooms().size();
        }
        if (!holesDone) {
            return 0.25 + 0.35 * Math.max((double) holeIndex / Math.max(1, holes.size()),
                    (double) holesPlaced / Math.max(1, targetHoles));
        }
        double partialRoom = roomCandidates == null ? 0.0
                : (double) candidateIndex / Math.max(1, roomCandidates.size());
        return Math.min(1.0, 0.6 + 0.4 * Math.max((roomIndex + partialRoom) / Math.max(1, rooms.size()),
                (double) roomsPlaced / Math.max(1, targetTrappedRooms)));
    }

    Set<HallsExplorationGenerator.Cell> occupiedCells() {
        return Set.copyOf(occupied);
    }

    void cancel() {
        done = true;
        occupied.clear();
        floorConnectivity = null;
    }

    private HallsTrapPlacementGeometry.FloorConnectivity floorConnectivity() {
        if (globalReachabilityChecks && floorConnectivity == null) {
            floorConnectivity = new HallsTrapPlacementGeometry.FloorConnectivity(
                    plan.walkableCells(), runtime.floorReachabilityStarts());
        }
        return floorConnectivity;
    }

    private void prepareRoom(HallsExplorationGenerator.Room room, int roomIndex) {
        Set<HallsExplorationGenerator.Cell> openCells = Set.copyOf(runtime.roomOpenCells(room));
        Set<HallsExplorationGenerator.Cell> roomCells = Set.copyOf(runtime.roomAllCells(room));
        List<TrapCandidate> candidates = new ArrayList<>();
        for (HallsExplorationGenerator.Cell cell : Set.copyOf(runtime.roomTrapCandidateCells(room))) {
            if (plan.walkableCells().contains(cell) && runtime.farFromElevator(cell)) {
                candidates.add(new TrapCandidate(room, cell, openCells, roomCells));
            }
        }
        if (!candidates.isEmpty()) {
            candidatesByRoom.put(room, candidates);
        }
        for (HallsExplorationGenerator.Cell cell : openCells) {
            if (!plan.walkableCells().contains(cell) || runtime.nearRoomOpening(room, cell)) {
                continue;
            }
            TrapCandidate candidate = new TrapCandidate(room, cell, openCells, roomCells);
            if (runtime.farFromElevator(cell)) {
                holes.add(candidate);
            } else if (roomIndex > 0) {
                fallbackHoles.add(candidate);
            }
        }
    }
}
