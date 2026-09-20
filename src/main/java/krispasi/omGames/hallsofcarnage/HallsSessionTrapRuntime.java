package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.IdentityHashMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PufferFish;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class HallsSessionTrapRuntime {
    private static final int ROOM_HEIGHT = 5;
    private static final int ELEVATOR_OUTER_RADIUS = 3;
    private static final double MONSTER_TRAP_PLAYER_RADIUS = 20.0;

    private final JavaPlugin plugin;
    private final World world;
    private final HallsConfig.BlockPoint origin;
    private final Set<UUID> participants;
    private final Predicate<UUID> aliveParticipant;
    private final BlockSetter blockSetter;
    private final Map<String, HallsTrapType> trapTypes;
    private final DoubleSupplier trapDamageMultiplier;
    private final Consumer<Location> lootDropSink;
    private final BiPredicate<String, Location> trapMonsterSpawner;
    private final List<HallsTrap> traps = new ArrayList<>();
    private final Map<UUID, Long> trapDamageCooldowns = new java.util.HashMap<>();
    private final Map<HallsTrap, Long> trapNextTriggerTicks = new IdentityHashMap<>();
    private final Map<HallsTrap, Integer> trapHitPoints = new IdentityHashMap<>();
    private final Set<UUID> transientTrapDisplays = new HashSet<>();
    private BukkitTask trapTask;
    private long trapRuntimeTick;

    HallsSessionTrapRuntime(JavaPlugin plugin,
                            World world,
                            HallsConfig.BlockPoint origin,
                            Set<UUID> participants,
                            Predicate<UUID> aliveParticipant,
                            BlockSetter blockSetter,
                            Map<String, HallsTrapType> trapTypes,
                            DoubleSupplier trapDamageMultiplier,
                            Consumer<Location> lootDropSink,
                            BiPredicate<String, Location> trapMonsterSpawner) {
        this.plugin = plugin;
        this.world = world;
        this.origin = origin;
        this.participants = participants;
        this.aliveParticipant = aliveParticipant == null ? ignored -> true : aliveParticipant;
        this.blockSetter = blockSetter;
        this.trapTypes = trapTypes == null ? Map.of() : Map.copyOf(trapTypes);
        this.trapDamageMultiplier = trapDamageMultiplier == null ? () -> 1.0 : trapDamageMultiplier;
        this.lootDropSink = lootDropSink == null ? ignored -> { } : lootDropSink;
        this.trapMonsterSpawner = trapMonsterSpawner;
    }

    void clear() {
        for (HallsTrap trap : List.copyOf(traps)) {
            for (UUID displayId : trap.displayIds()) {
                Entity display = Bukkit.getEntity(displayId);
                if (display != null) {
                    display.remove();
                }
            }
        }
        for (UUID displayId : Set.copyOf(transientTrapDisplays)) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null) {
                display.remove();
            }
        }
        traps.clear();
        trapNextTriggerTicks.clear();
        transientTrapDisplays.clear();
        trapDamageCooldowns.clear();
        trapRuntimeTick = 0L;
        stopTrapTask();
    }

    int activeTrapCount() {
        return traps.size();
    }

    boolean handlePlayerMove(Player player, boolean running) {
        if (player == null || !running || !participants.contains(player.getUniqueId()) || !player.getWorld().equals(world)) {
            return false;
        }
        if (!isAliveParticipant(player)) {
            return false;
        }
        checkPlayerTrapContact(player);
        if (player.getLocation().getY() <= origin.y() - 8) {
            damagePlayerFromTrap(player, 200.0, "The pit swallows you.");
            teleportPlayerToElevator(player);
            return true;
        }
        return false;
    }

    boolean handleTrapInteract(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        for (HallsTrap trap : List.copyOf(traps)) {
            if (!trap.displayIds().contains(entity.getUniqueId())) {
                continue;
            }
            if (trap.kind() != TrapKind.ARMY_COFFIN) {
                return false;
            }
            triggerArmyCoffin(trap, player);
            return true;
        }
        return false;
    }

    boolean handleTrapAttack(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        for (HallsTrap trap : List.copyOf(traps)) {
            if (!trap.displayIds().contains(entity.getUniqueId())) {
                continue;
            }
            if (trap.kind() == TrapKind.ENCHANTED_BOOK) {
                damageEnchantedBookTrap(trap, player);
                return true;
            }
            if (trap.kind() == TrapKind.ARMY_COFFIN) {
                triggerArmyCoffin(trap, player);
                return true;
            }
        }
        return false;
    }

    boolean disarmProximityMineNear(Location location) {
        if (location == null || !world.equals(location.getWorld())) {
            return false;
        }
        for (HallsTrap trap : List.copyOf(traps)) {
            if (trap.kind() != TrapKind.PROXIMITY_MINE && trap.kind() != TrapKind.BEAR_TRAP) {
                continue;
            }
            Location center = new Location(world, trap.x() + 0.5, origin.y() + 0.1, trap.z() + 0.5);
            if (location.distanceSquared(center) <= 1.35 * 1.35) {
                world.spawnParticle(Particle.SMOKE, center, 18, 0.35, 0.15, 0.35, 0.02);
                world.playSound(center, Sound.BLOCK_LEVER_CLICK, 0.75f, 0.55f);
                removeTrap(trap);
                return true;
            }
        }
        return false;
    }

    Set<HallsExplorationGenerator.Cell> placeGeneratedTraps(HallsExplorationGenerator.Plan plan,
                                                            Random random,
                                                            HallsScenario.FloorDefinition floorDefinition,
                                                            HallsLevelType levelType,
                                                            HallsFloorModifiers modifiers,
                                                            Set<HallsExplorationGenerator.Cell> liquidCells) {
        clear();
        Set<HallsExplorationGenerator.Cell> liquidTrapCells = liquidCells == null ? Set.of() : Set.copyOf(liquidCells);
        List<TrapCandidate> candidates = trapCandidates(plan);
        List<TrapCandidate> holeCandidates = holeCandidates(plan);
        if (candidates.isEmpty() && holeCandidates.isEmpty()) {
            return Set.of();
        }
        int targetHoles = Math.min(holeCandidates.size(), Math.max(0, floorDefinition.holes()));
        int targetTrappedRooms = Math.max(0, floorDefinition.trappedRooms());
        int minTrapsPerRoom = Math.max(0, floorDefinition.minTrapsPerRoom());
        int maxTrapsPerRoom = Math.max(minTrapsPerRoom, floorDefinition.maxTrapsPerRoom());
        List<HallsTrapType> pool = trapPool(levelType.id());
        HallsTrapType holeType = trapTypes.values().stream()
                .filter(type -> type.kind().equals("hole") && type.weight() > 0 && type.allowedForLevelType(levelType.id()))
                .findFirst()
                .orElse(null);
        if ((pool.isEmpty() || targetTrappedRooms <= 0 || maxTrapsPerRoom <= 0) && (holeType == null || targetHoles <= 0)) {
            return Set.of();
        }
        boolean globalReachabilityChecks = globalReachabilityChecks(levelType);
        Collections.shuffle(holeCandidates, random);
        Set<HallsExplorationGenerator.Cell> occupied = new HashSet<>();
        int holesPlaced = 0;
        for (TrapCandidate candidate : holeCandidates) {
            HallsExplorationGenerator.Cell cell = candidate.cell();
            if (holesPlaced >= targetHoles) {
                break;
            }
            if (holeType == null) {
                continue;
            }
            if (liquidTrapCells.contains(candidate.cell())) {
                continue;
            }
            if (placeHole(candidate, plan, random, holeType, occupied, globalReachabilityChecks, modifiers, liquidTrapCells)) {
                holesPlaced++;
            }
        }
        Map<HallsExplorationGenerator.Room, List<TrapCandidate>> candidatesByRoom = candidatesByRoom(candidates);
        List<HallsExplorationGenerator.Room> trappedRooms = new ArrayList<>(candidatesByRoom.keySet());
        Collections.shuffle(trappedRooms, random);
        int roomsPlaced = 0;
        for (HallsExplorationGenerator.Room room : trappedRooms) {
            if (roomsPlaced >= targetTrappedRooms || pool.isEmpty()) {
                break;
            }
            List<TrapCandidate> roomCandidates = new ArrayList<>(candidatesByRoom.getOrDefault(room, List.of()));
            Collections.shuffle(roomCandidates, random);
            HallsTrapType roomType = weightedTrap(pool, random, modifiers);
            if (trapKind(roomType.kind()) == TrapKind.SWINGING_BLADE) {
                roomCandidates.sort((first, second) -> Integer.compare(bestSwingLaneHalfSpan(second), bestSwingLaneHalfSpan(first)));
            }
            int targetRoomTraps = minTrapsPerRoom == maxTrapsPerRoom
                    ? minTrapsPerRoom
                    : minTrapsPerRoom + random.nextInt(maxTrapsPerRoom - minTrapsPerRoom + 1);
            int placedInRoom = 0;
            for (TrapCandidate candidate : roomCandidates) {
                if (placedInRoom >= targetRoomTraps) {
                    break;
                }
                HallsTrapType type = random.nextInt(100) < 10 ? weightedTrap(pool, random, modifiers) : roomType;
                if (tryPlaceTrap(candidate, plan, random, type, occupied, globalReachabilityChecks, liquidTrapCells)) {
                    placedInRoom++;
                }
            }
            if (placedInRoom > 0) {
                roomsPlaced++;
            }
        }
        startTrapTask();
        return Set.copyOf(occupied);
    }

    private Map<HallsExplorationGenerator.Room, List<TrapCandidate>> candidatesByRoom(List<TrapCandidate> candidates) {
        Map<HallsExplorationGenerator.Room, List<TrapCandidate>> byRoom = new IdentityHashMap<>();
        for (TrapCandidate candidate : candidates) {
            byRoom.computeIfAbsent(candidate.room(), ignored -> new ArrayList<>()).add(candidate);
        }
        return byRoom;
    }

    private boolean tryPlaceTrap(TrapCandidate candidate,
                                 HallsExplorationGenerator.Plan plan,
                                 Random random,
                                 HallsTrapType type,
                                 Set<HallsExplorationGenerator.Cell> occupied,
                                 boolean globalReachabilityChecks,
                                 Set<HallsExplorationGenerator.Cell> liquidCells) {
        HallsExplorationGenerator.Cell cell = candidate.cell();
        if (isNearExistingTrap(cell, occupied)) {
            return false;
        }
        TrapKind kind = trapKind(type.kind());
        if (kind == null) {
            return false;
        }
        boolean waterTrap = isWaterTrap(kind);
        if (waterTrap && !liquidCells.contains(cell)) {
            return false;
        }
        if (!waterTrap && liquidCells.contains(cell)) {
            return false;
        }
        BlockFace face = trapFace(kind, candidate, random);
        if ((requiresWall(kind) || kind == TrapKind.SWINGING_BLADE || kind == TrapKind.FALLING_ICE)
                && face == BlockFace.SELF) {
            return false;
        }
        int laneSpan = kind == TrapKind.SWINGING_BLADE ? swingLaneHalfSpan(candidate, face) : 0;
        Set<HallsExplorationGenerator.Cell> footprint = trapFootprint(kind, cell, face, laneSpan);
        if (waterTrap) {
            if (!liquidCells.containsAll(footprint)) {
                return false;
            }
        } else if (!Collections.disjoint(footprint, liquidCells)) {
            return false;
        }
        if (globalReachabilityChecks && (kind == TrapKind.PROXIMITY_MINE || kind == TrapKind.BUBBLES || requiresWall(kind))
                && !floorReachableWithout(plan.walkableCells(), footprint)) {
            return false;
        }
        for (HallsExplorationGenerator.Cell footprintCell : footprint) {
            if (isNearExistingTrap(footprintCell, occupied)) {
                return false;
            }
        }
        List<UUID> displayIds = buildTrap(kind, cell, face, type, laneSpan);
        addTrap(new HallsTrap(kind, cell.x(), cell.z(), random.nextInt(80), type,
                movingDisplayId(kind, displayIds), displayIds, face, laneSpan), random);
        occupied.addAll(footprint);
        return true;
    }

    private boolean placeHole(TrapCandidate candidate,
                              HallsExplorationGenerator.Plan plan,
                              Random random,
                              HallsTrapType type,
                              Set<HallsExplorationGenerator.Cell> occupied,
                              boolean globalReachabilityChecks,
                              HallsFloorModifiers modifiers,
                              Set<HallsExplorationGenerator.Cell> liquidCells) {
        for (Set<HallsExplorationGenerator.Cell> pitCells : pitMasks(candidate, random, type)) {
            if (pitCells.isEmpty()
                    || !Collections.disjoint(pitCells, liquidCells)
                    || !Collections.disjoint(borderCells(pitCells), liquidCells)
                    || !Collections.disjoint(pitCells, occupied)) {
                continue;
            }
            Set<HallsExplorationGenerator.Cell> bridgeCells = bridgeCellsIfNeeded(candidate, plan.walkableCells(),
                    pitCells, globalReachabilityChecks, modifiers);
            if (bridgeCells == null) {
                continue;
            }
            if (bridgeCells.containsAll(pitCells)) {
                continue;
            }
            buildPit(pitCells, bridgeCells, roomPitCells(candidate), type);
            TrapKind kind = bridgeCells.isEmpty() ? TrapKind.HOLE : TrapKind.HOLE_BRIDGE;
            for (HallsExplorationGenerator.Cell pitCell : pitCells) {
                if (!bridgeCells.contains(pitCell)) {
                    addTrap(new HallsTrap(kind, pitCell.x(), pitCell.z(), random.nextInt(80), type,
                            null, List.of(), BlockFace.SELF, 0), random);
                }
            }
            occupied.addAll(pitCells);
            return true;
        }
        return false;
    }

    private boolean globalReachabilityChecks(HallsLevelType levelType) {
        if (levelType == null || levelType.corridorGeneration() == null) {
            return true;
        }
        String mode = levelType.corridorGeneration().trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        return !mode.equals("maze") && !mode.equals("backrooms")
                && !mode.equals("open_halls") && !mode.equals("open_hall");
    }

    private List<TrapCandidate> trapCandidates(HallsExplorationGenerator.Plan plan) {
        Set<HallsExplorationGenerator.Cell> walkable = plan.walkableCells();
        List<TrapCandidate> candidates = new ArrayList<>();
        for (HallsExplorationGenerator.Room room : plan.rooms()) {
            Set<HallsExplorationGenerator.Cell> candidateCells = Set.copyOf(roomTrapCandidateCells(room));
            Set<HallsExplorationGenerator.Cell> openCells = Set.copyOf(roomOpenCells(room));
            Set<HallsExplorationGenerator.Cell> roomCells = Set.copyOf(roomAllCells(room));
            for (HallsExplorationGenerator.Cell cell : candidateCells) {
                if (walkable.contains(cell) && farFromElevator(cell)) {
                    candidates.add(new TrapCandidate(room, cell, openCells, roomCells));
                }
            }
        }
        return candidates;
    }

    private List<TrapCandidate> holeCandidates(HallsExplorationGenerator.Plan plan) {
        Set<HallsExplorationGenerator.Cell> walkable = plan.walkableCells();
        List<TrapCandidate> preferred = new ArrayList<>();
        List<TrapCandidate> fallback = new ArrayList<>();
        List<HallsExplorationGenerator.Room> rooms = plan.rooms();
        for (int roomIndex = 0; roomIndex < rooms.size(); roomIndex++) {
            HallsExplorationGenerator.Room room = rooms.get(roomIndex);
            Set<HallsExplorationGenerator.Cell> openCells = Set.copyOf(roomOpenCells(room));
            Set<HallsExplorationGenerator.Cell> roomCells = Set.copyOf(roomAllCells(room));
            for (HallsExplorationGenerator.Cell cell : openCells) {
                if (!walkable.contains(cell) || nearRoomOpening(room, cell)) {
                    continue;
                }
                TrapCandidate candidate = new TrapCandidate(room, cell, openCells, roomCells);
                if (farFromElevator(cell)) {
                    preferred.add(candidate);
                } else if (roomIndex > 0) {
                    fallback.add(candidate);
                }
            }
        }
        if (preferred.isEmpty()) {
            return fallback;
        }
        preferred.addAll(fallback);
        return preferred;
    }

    private HallsTrapType trapTypeForRoom(TrapCandidate candidate,
                                          Map<HallsExplorationGenerator.Room, HallsTrapType> roomTrapTypes,
                                          List<HallsTrapType> pool,
                                          Random random) {
        HallsTrapType existing = roomTrapTypes.get(candidate.room());
        if (existing != null && random.nextInt(100) >= 10) {
            return existing;
        }
        return weightedTrap(pool, random, null);
    }

    private List<HallsExplorationGenerator.Cell> roomTrapCandidateCells(HallsExplorationGenerator.Room room) {
        List<HallsExplorationGenerator.Cell> cells = new ArrayList<>();
        for (int z = 0; z < room.layout().depth(); z++) {
            for (int x = 0; x < room.layout().width(); x++) {
                if (room.layout().at(x, z) == 'O'
                        && x != room.layout().width() / 2
                        && z != room.layout().depth() / 2) {
                    HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(room.startX() + x, room.startZ() + z);
                    if (!nearRoomOpening(room, cell)) {
                        cells.add(cell);
                    }
                }
            }
        }
        return cells;
    }

    private boolean nearRoomOpening(HallsExplorationGenerator.Room room, HallsExplorationGenerator.Cell cell) {
        for (Map.Entry<BlockFace, Integer> opening : room.openings().entrySet()) {
            HallsExplorationGenerator.Cell interior = switch (opening.getKey()) {
                case NORTH -> new HallsExplorationGenerator.Cell(room.startX() + opening.getValue(), room.startZ());
                case SOUTH -> new HallsExplorationGenerator.Cell(room.startX() + opening.getValue(), room.startZ() + room.layout().depth() - 1);
                case EAST -> new HallsExplorationGenerator.Cell(room.startX() + room.layout().width() - 1, room.startZ() + opening.getValue());
                case WEST -> new HallsExplorationGenerator.Cell(room.startX(), room.startZ() + opening.getValue());
                default -> cell;
            };
            if (Math.abs(cell.x() - interior.x()) + Math.abs(cell.z() - interior.z()) <= 2) {
                return true;
            }
        }
        return false;
    }

    private List<HallsExplorationGenerator.Cell> roomOpenCells(HallsExplorationGenerator.Room room) {
        List<HallsExplorationGenerator.Cell> cells = new ArrayList<>();
        for (int z = 0; z < room.layout().depth(); z++) {
            for (int x = 0; x < room.layout().width(); x++) {
                if (room.layout().at(x, z) == 'O') {
                    cells.add(new HallsExplorationGenerator.Cell(room.startX() + x, room.startZ() + z));
                }
            }
        }
        return cells;
    }

    private List<HallsExplorationGenerator.Cell> roomAllCells(HallsExplorationGenerator.Room room) {
        List<HallsExplorationGenerator.Cell> cells = new ArrayList<>();
        for (int z = 0; z < room.layout().depth(); z++) {
            for (int x = 0; x < room.layout().width(); x++) {
                cells.add(new HallsExplorationGenerator.Cell(room.startX() + x, room.startZ() + z));
            }
        }
        return cells;
    }

    private boolean farFromElevator(HallsExplorationGenerator.Cell cell) {
        return Math.abs(cell.x() - origin.x()) + Math.abs(cell.z() - origin.z()) > 12;
    }

    private boolean isNearExistingTrap(HallsExplorationGenerator.Cell cell, Set<HallsExplorationGenerator.Cell> occupied) {
        for (HallsExplorationGenerator.Cell other : occupied) {
            if (Math.abs(cell.x() - other.x()) + Math.abs(cell.z() - other.z()) <= 4) {
                return true;
            }
        }
        return false;
    }

    private List<HallsTrapType> trapPool(String levelTypeId) {
        List<HallsTrapType> pool = new ArrayList<>();
        for (HallsTrapType type : trapTypes.values()) {
            if (type.weight() <= 0 || !type.allowedForLevelType(levelTypeId)) {
                continue;
            }
            if (type.kind().equals("hole")) {
                continue;
            }
            pool.add(type);
        }
        return pool;
    }

    private HallsTrapType weightedTrap(List<HallsTrapType> pool, Random random, HallsFloorModifiers modifiers) {
        List<String> boostedKinds = modifiers == null ? List.of() : modifiers.trapBoostKinds();
        int totalWeight = pool.stream().mapToInt(type -> adjustedTrapWeight(type, boostedKinds)).sum();
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (HallsTrapType type : pool) {
            roll -= adjustedTrapWeight(type, boostedKinds);
            if (roll < 0) {
                return type;
            }
        }
        return pool.getFirst();
    }

    private int adjustedTrapWeight(HallsTrapType type, List<String> boostedKinds) {
        int weight = type.weight();
        if (boostedKinds.contains(type.id()) || boostedKinds.contains(type.kind())) {
            weight *= 2;
        }
        return Math.max(0, weight);
    }

    private List<Set<HallsExplorationGenerator.Cell>> pitMasks(TrapCandidate candidate, Random random, HallsTrapType type) {
        int minSize = Math.max(5, type.minSize());
        int maxSize = Math.max(minSize, type.maxSize());
        int size = minSize + random.nextInt(maxSize - minSize + 1);
        if (size % 2 == 0) {
            size++;
        }
        List<Set<HallsExplorationGenerator.Cell>> masks = new ArrayList<>();
        for (int currentSize = size; currentSize >= minSize; currentSize -= 2) {
            addPitMaskCandidate(masks, candidate, currentSize, Math.max(5, currentSize * currentSize / 3));
        }
        for (int currentSize = Math.min(3, minSize - 2); currentSize >= 1; currentSize -= 2) {
            addPitMaskCandidate(masks, candidate, currentSize, 1);
        }
        if (masks.isEmpty()) {
            masks.add(Set.of(candidate.cell()));
        }
        return masks;
    }

    private void addPitMaskCandidate(List<Set<HallsExplorationGenerator.Cell>> masks,
                                     TrapCandidate candidate,
                                     int size,
                                     int minimumOpenCells) {
        int radius = size / 2;
        Set<HallsExplorationGenerator.Cell> openPitCells = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(
                        candidate.cell().x() + dx,
                        candidate.cell().z() + dz
                );
                if (candidate.roomCells().contains(cell)) {
                    openPitCells.add(cell);
                }
            }
        }
        if (openPitCells.size() >= minimumOpenCells) {
            masks.add(openPitCells);
        }
    }

    private Set<HallsExplorationGenerator.Cell> bridgeCellsIfNeeded(TrapCandidate candidate,
                                                      Set<HallsExplorationGenerator.Cell> walkable,
                                                      Set<HallsExplorationGenerator.Cell> pitCells,
                                                      boolean globalReachabilityChecks,
                                                      HallsFloorModifiers modifiers) {
        Set<HallsExplorationGenerator.Cell> existingPits = roomPitCells(candidate);
        Set<HallsExplorationGenerator.Cell> allPitCells = new HashSet<>(existingPits);
        allPitCells.addAll(pitCells);
        boolean enforceRoomReachability = roomEntrancesReachable(candidate, existingPits, Set.of());
        if ((!globalReachabilityChecks || floorReachableWithout(walkable, allPitCells))
                && (!enforceRoomReachability || roomEntrancesReachable(candidate, allPitCells, Set.of()))) {
            return Set.of();
        }
        int minX = pitCells.stream().mapToInt(HallsExplorationGenerator.Cell::x).min().orElse(0);
        int maxX = pitCells.stream().mapToInt(HallsExplorationGenerator.Cell::x).max().orElse(0);
        int minZ = pitCells.stream().mapToInt(HallsExplorationGenerator.Cell::z).min().orElse(0);
        int maxZ = pitCells.stream().mapToInt(HallsExplorationGenerator.Cell::z).max().orElse(0);
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        if ((maxX - minX) >= (maxZ - minZ)) {
            Set<HallsExplorationGenerator.Cell> bridge = firstReachableBridge(candidate, walkable, allPitCells, pitCells,
                    widenBridgeOptions(horizontalBridgeOptions(pitCells, minX, maxX, minZ, maxZ, centerZ), pitCells, modifiers),
                    globalReachabilityChecks, enforceRoomReachability);
            if (bridge != null) {
                return bridge;
            }
            return firstReachableBridge(candidate, walkable, allPitCells, pitCells,
                    widenBridgeOptions(verticalBridgeOptions(pitCells, minX, maxX, minZ, maxZ, centerX), pitCells, modifiers),
                    globalReachabilityChecks, enforceRoomReachability);
        } else {
            Set<HallsExplorationGenerator.Cell> bridge = firstReachableBridge(candidate, walkable, allPitCells, pitCells,
                    widenBridgeOptions(verticalBridgeOptions(pitCells, minX, maxX, minZ, maxZ, centerX), pitCells, modifiers),
                    globalReachabilityChecks, enforceRoomReachability);
            if (bridge != null) {
                return bridge;
            }
            return firstReachableBridge(candidate, walkable, allPitCells, pitCells,
                    widenBridgeOptions(horizontalBridgeOptions(pitCells, minX, maxX, minZ, maxZ, centerZ), pitCells, modifiers),
                    globalReachabilityChecks, enforceRoomReachability);
        }
    }

    private List<Set<HallsExplorationGenerator.Cell>> widenBridgeOptions(List<Set<HallsExplorationGenerator.Cell>> options,
                                                                         Set<HallsExplorationGenerator.Cell> pitCells,
                                                                         HallsFloorModifiers modifiers) {
        int extra = modifiers == null ? 0 : modifiers.holeBridgeExtraWidth();
        if (extra <= 0) {
            return options;
        }
        List<Set<HallsExplorationGenerator.Cell>> widened = new ArrayList<>();
        for (Set<HallsExplorationGenerator.Cell> option : options) {
            Set<HallsExplorationGenerator.Cell> cells = new HashSet<>(option);
            for (HallsExplorationGenerator.Cell cell : option) {
                for (int offset = 1; offset <= extra; offset++) {
                    addIfPit(cells, pitCells, new HallsExplorationGenerator.Cell(cell.x() + offset, cell.z()));
                    addIfPit(cells, pitCells, new HallsExplorationGenerator.Cell(cell.x() - offset, cell.z()));
                    addIfPit(cells, pitCells, new HallsExplorationGenerator.Cell(cell.x(), cell.z() + offset));
                    addIfPit(cells, pitCells, new HallsExplorationGenerator.Cell(cell.x(), cell.z() - offset));
                }
            }
            widened.add(cells);
        }
        return widened;
    }

    private void addIfPit(Set<HallsExplorationGenerator.Cell> cells,
                          Set<HallsExplorationGenerator.Cell> pitCells,
                          HallsExplorationGenerator.Cell candidate) {
        if (pitCells.contains(candidate)) {
            cells.add(candidate);
        }
    }

    private Set<HallsExplorationGenerator.Cell> firstReachableBridge(TrapCandidate candidate,
                                                                     Set<HallsExplorationGenerator.Cell> walkable,
                                                                     Set<HallsExplorationGenerator.Cell> allPitCells,
                                                                     Set<HallsExplorationGenerator.Cell> newPitCells,
                                                                     List<Set<HallsExplorationGenerator.Cell>> bridgeOptions,
                                                                     boolean globalReachabilityChecks,
                                                                     boolean enforceRoomReachability) {
        for (Set<HallsExplorationGenerator.Cell> bridge : bridgeOptions) {
            Set<HallsExplorationGenerator.Cell> passableBridge = intersection(bridge, candidate.roomCells());
            if (!passableBridge.isEmpty()
                    && (!globalReachabilityChecks || floorReachableWithout(walkable, difference(allPitCells, passableBridge)))
                    && (!enforceRoomReachability || roomEntrancesReachable(candidate, allPitCells, passableBridge))) {
                return passableBridge;
            }
        }
        return null;
    }

    private Set<HallsExplorationGenerator.Cell> roomPitCells(TrapCandidate candidate) {
        Set<HallsExplorationGenerator.Cell> result = new HashSet<>();
        for (HallsTrap trap : traps) {
            if (trap.kind() != TrapKind.HOLE && trap.kind() != TrapKind.HOLE_BRIDGE) {
                continue;
            }
            HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(trap.x(), trap.z());
            if (candidate.roomCells().contains(cell)) {
                result.add(cell);
            }
        }
        return result;
    }

    private boolean roomEntrancesReachable(TrapCandidate candidate,
                                           Set<HallsExplorationGenerator.Cell> pitCells,
                                           Set<HallsExplorationGenerator.Cell> bridgeCells) {
        Set<HallsExplorationGenerator.Cell> blocked = difference(pitCells, bridgeCells);
        Set<HallsExplorationGenerator.Cell> target = difference(candidate.roomCells(), blocked);
        if (target.isEmpty()) {
            return false;
        }
        List<HallsExplorationGenerator.Cell> entrances = roomEntranceCells(candidate.room());
        if (entrances.isEmpty()) {
            entrances = List.of(target.iterator().next());
        }
        for (HallsExplorationGenerator.Cell entrance : entrances) {
            if (!target.contains(entrance) || !roomReachableFrom(entrance, target).containsAll(target)) {
                return false;
            }
        }
        return true;
    }

    private Set<HallsExplorationGenerator.Cell> roomReachableFrom(HallsExplorationGenerator.Cell start,
                                                                  Set<HallsExplorationGenerator.Cell> walkable) {
        Set<HallsExplorationGenerator.Cell> reachable = new HashSet<>();
        java.util.ArrayDeque<HallsExplorationGenerator.Cell> queue = new java.util.ArrayDeque<>();
        reachable.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            HallsExplorationGenerator.Cell current = queue.remove();
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                HallsExplorationGenerator.Cell next = step(current, face);
                if (walkable.contains(next) && reachable.add(next)) {
                    queue.add(next);
                }
            }
        }
        return reachable;
    }

    private List<HallsExplorationGenerator.Cell> roomEntranceCells(HallsExplorationGenerator.Room room) {
        List<HallsExplorationGenerator.Cell> entrances = new ArrayList<>();
        for (Map.Entry<BlockFace, Integer> opening : room.openings().entrySet()) {
            HallsExplorationGenerator.Cell cell = switch (opening.getKey()) {
                case NORTH -> new HallsExplorationGenerator.Cell(room.startX() + opening.getValue(), room.startZ());
                case SOUTH -> new HallsExplorationGenerator.Cell(room.startX() + opening.getValue(), room.startZ() + room.layout().depth() - 1);
                case EAST -> new HallsExplorationGenerator.Cell(room.startX() + room.layout().width() - 1, room.startZ() + opening.getValue());
                case WEST -> new HallsExplorationGenerator.Cell(room.startX(), room.startZ() + opening.getValue());
                default -> null;
            };
            if (cell != null) {
                entrances.add(cell);
            }
        }
        return entrances;
    }

    private List<Set<HallsExplorationGenerator.Cell>> horizontalBridgeOptions(Set<HallsExplorationGenerator.Cell> pitCells,
                                                                              int minX,
                                                                              int maxX,
                                                                              int minZ,
                                                                              int maxZ,
                                                                              int centerZ) {
        List<Set<HallsExplorationGenerator.Cell>> options = new ArrayList<>();
        for (int z : orderedRange(minZ, maxZ, centerZ)) {
            Set<HallsExplorationGenerator.Cell> bridge = new HashSet<>();
            for (int x = minX; x <= maxX; x++) {
                HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(x, z);
                if (pitCells.contains(cell)) {
                    bridge.add(cell);
                }
            }
            options.add(bridge);
        }
        return options;
    }

    private List<Set<HallsExplorationGenerator.Cell>> verticalBridgeOptions(Set<HallsExplorationGenerator.Cell> pitCells,
                                                                            int minX,
                                                                            int maxX,
                                                                            int minZ,
                                                                            int maxZ,
                                                                            int centerX) {
        List<Set<HallsExplorationGenerator.Cell>> options = new ArrayList<>();
        for (int x : orderedRange(minX, maxX, centerX)) {
            Set<HallsExplorationGenerator.Cell> bridge = new HashSet<>();
            for (int z = minZ; z <= maxZ; z++) {
                HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(x, z);
                if (pitCells.contains(cell)) {
                    bridge.add(cell);
                }
            }
            options.add(bridge);
        }
        return options;
    }

    private List<Integer> orderedRange(int min, int max, int center) {
        List<Integer> values = new ArrayList<>();
        values.add(center);
        for (int distance = 1; center - distance >= min || center + distance <= max; distance++) {
            if (center - distance >= min) {
                values.add(center - distance);
            }
            if (center + distance <= max) {
                values.add(center + distance);
            }
        }
        return values;
    }

    private Set<HallsExplorationGenerator.Cell> difference(Set<HallsExplorationGenerator.Cell> cells,
                                                           Set<HallsExplorationGenerator.Cell> removed) {
        Set<HallsExplorationGenerator.Cell> result = new HashSet<>(cells);
        result.removeAll(removed);
        return result;
    }

    private Set<HallsExplorationGenerator.Cell> intersection(Set<HallsExplorationGenerator.Cell> cells,
                                                             Set<HallsExplorationGenerator.Cell> retained) {
        Set<HallsExplorationGenerator.Cell> result = new HashSet<>(cells);
        result.retainAll(retained);
        return result;
    }

    private boolean floorReachableWithout(Set<HallsExplorationGenerator.Cell> walkable,
                                          Set<HallsExplorationGenerator.Cell> blocked) {
        HallsExplorationGenerator.Cell start = floorReachabilityStart(walkable, blocked);
        if (start == null) {
            return false;
        }
        Set<HallsExplorationGenerator.Cell> reachable = new HashSet<>();
        java.util.ArrayDeque<HallsExplorationGenerator.Cell> queue = new java.util.ArrayDeque<>();
        reachable.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            HallsExplorationGenerator.Cell current = queue.remove();
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                HallsExplorationGenerator.Cell next = switch (face) {
                    case NORTH -> new HallsExplorationGenerator.Cell(current.x(), current.z() - 1);
                    case SOUTH -> new HallsExplorationGenerator.Cell(current.x(), current.z() + 1);
                    case EAST -> new HallsExplorationGenerator.Cell(current.x() + 1, current.z());
                    case WEST -> new HallsExplorationGenerator.Cell(current.x() - 1, current.z());
                    default -> current;
                };
                if (!blocked.contains(next) && walkable.contains(next) && reachable.add(next)) {
                    queue.add(next);
                }
            }
        }
        for (HallsExplorationGenerator.Cell cell : walkable) {
            if (!blocked.contains(cell) && !reachable.contains(cell)) {
                return false;
            }
        }
        return true;
    }

    private HallsExplorationGenerator.Cell floorReachabilityStart(Set<HallsExplorationGenerator.Cell> walkable,
                                                                  Set<HallsExplorationGenerator.Cell> blocked) {
        List<HallsExplorationGenerator.Cell> starts = List.of(
                new HallsExplorationGenerator.Cell(origin.x(), origin.z() - ELEVATOR_OUTER_RADIUS - 1),
                new HallsExplorationGenerator.Cell(origin.x(), origin.z() + ELEVATOR_OUTER_RADIUS + 1),
                new HallsExplorationGenerator.Cell(origin.x() + ELEVATOR_OUTER_RADIUS + 1, origin.z()),
                new HallsExplorationGenerator.Cell(origin.x() - ELEVATOR_OUTER_RADIUS - 1, origin.z())
        );
        for (HallsExplorationGenerator.Cell start : starts) {
            if (walkable.contains(start) && !blocked.contains(start)) {
                return start;
            }
        }
        return null;
    }

    private List<UUID> buildTrap(TrapKind kind, HallsExplorationGenerator.Cell cell, BlockFace face, HallsTrapType type, int laneSpan) {
        return switch (kind) {
            case BEAR_TRAP, PROXIMITY_MINE -> {
                yield List.of(spawnTrapItemDisplay(kind, cell, face, type, type.modelMaterial(), 0.0));
            }
            case SWINGING_BLADE -> buildSwingingBlade(cell, face, type, laneSpan);
            case WALL_SPIKES -> buildWallSpikes(cell, face, type);
            case FALLING_ICE -> type.ceilingMaterial().isAir() ? List.of() : List.of(spawnCeilingBlockDisplay(cell, type.ceilingMaterial()));
            case POISON_DARTS -> buildPoisonDartLauncher(cell, face, type);
            case STEAM_VENT -> List.of(spawnFloorBlockDisplay(cell, type.blockMaterial(), type.modelScale()));
            case BUBBLES -> buildSewerWaterFixture(cell, type);
            case GEYSER -> buildSewerWaterFixture(cell, type);
            case PUFFERFISH -> List.of(spawnPufferfish(cell));
            case ARMY_COFFIN -> withInteraction(cell,
                    spawnTrapItemDisplay(kind, cell, face, type, type.modelMaterial(), 90.0), 1.35f, 1.0f);
            case HOMING_MINE -> buildHomingMine(cell, face, type);
            case ENCHANTED_BOOK -> withInteraction(cell,
                    spawnTrapItemDisplay(kind, cell, face, type, type.modelMaterial(), 0.0), 0.8f, 0.9f);
            default -> List.of();
        };
    }

    private List<UUID> withInteraction(HallsExplorationGenerator.Cell cell, UUID displayId, float width, float height) {
        Interaction interaction = world.spawn(new Location(world, cell.x() + 0.5, origin.y(), cell.z() + 0.5),
                Interaction.class, entity -> {
                    entity.setInteractionWidth(width);
                    entity.setInteractionHeight(height);
                    entity.setResponsive(true);
                    entity.setPersistent(false);
                    entity.addScoreboardTag("omgames_hoc_trap");
                });
        return List.of(displayId, interaction.getUniqueId());
    }

    private void buildPit(Set<HallsExplorationGenerator.Cell> pitCells,
                          Set<HallsExplorationGenerator.Cell> bridgeCells,
                          Set<HallsExplorationGenerator.Cell> existingPits,
                          HallsTrapType type) {
        Set<HallsExplorationGenerator.Cell> connectedPitCells = new HashSet<>(pitCells);
        connectedPitCells.addAll(existingPits);
        for (HallsExplorationGenerator.Cell cell : pitCells) {
            for (int y = origin.y(); y < origin.y() + ROOM_HEIGHT; y++) {
                setBlock(cell.x(), y, cell.z(), Material.AIR);
            }
            setBlock(cell.x(), origin.y() - 1, cell.z(), Material.AIR);
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                HallsExplorationGenerator.Cell side = step(cell, face);
                if (!connectedPitCells.contains(side)) {
                    for (int y = origin.y() - 2; y >= origin.y() - type.depth(); y--) {
                        setBlock(side.x(), y, side.z(), Material.DEEPSLATE_BRICKS);
                    }
                }
            }
            for (int y = origin.y() - 2; y >= origin.y() - type.depth(); y--) {
                setBlock(cell.x(), y, cell.z(), y == origin.y() - type.depth() ? Material.BLACK_CONCRETE : Material.AIR);
            }
        }
        for (HallsExplorationGenerator.Cell cell : bridgeCells) {
            setBlock(cell.x(), origin.y() - 1, cell.z(), type.bridgeMaterial());
        }
    }

    private List<UUID> buildSwingingBlade(HallsExplorationGenerator.Cell cell, BlockFace face, HallsTrapType type, int laneSpan) {
        boolean eastWest = face == BlockFace.EAST || face == BlockFace.WEST;
        UUID rail = spawnRailDisplay(cell, eastWest, laneSpan);
        UUID blade = spawnTrapItemDisplay(TrapKind.SWINGING_BLADE, cell, face, type, Material.IRON_SWORD, 0.0);
        return List.of(rail, blade);
    }

    private List<UUID> buildWallSpikes(HallsExplorationGenerator.Cell cell, BlockFace face, HallsTrapType type) {
        UUID base = spawnWallBlockDisplay(cell, face, Material.BLACK_CONCRETE, 0.04f, 0.45f, 0.45f);
        UUID spikes = spawnTrapItemDisplay(TrapKind.WALL_SPIKES, cell, face, type, Material.IRON_SWORD, 0.0);
        return List.of(base, spikes);
    }

    private List<UUID> buildPoisonDartLauncher(HallsExplorationGenerator.Cell cell, BlockFace face, HallsTrapType type) {
        return List.of(spawnWallBlockDisplay(cell, face, type.blockMaterial(), 0.08f, 0.65f, 0.65f));
    }

    private List<UUID> buildHomingMine(HallsExplorationGenerator.Cell cell, BlockFace face, HallsTrapType type) {
        UUID plate = spawnTrapItemDisplay(TrapKind.HOMING_MINE, cell, face, type, type.modelMaterial(), 0.0);
        UUID antenna = spawnHomingMineAntenna(cell);
        return List.of(plate, antenna);
    }

    private UUID spawnHomingMineAntenna(HallsExplorationGenerator.Cell cell) {
        Location location = new Location(world, cell.x() + 0.5, origin.y() + 0.85, cell.z() + 0.5);
        BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(connectedFenceData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setInterpolationDelay(1);
            entity.setTeleportDuration(2);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_trap");
            entity.setTransformation(new Transformation(
                    new Vector3f(-0.5f, -0.1f, -0.5f),
                    new Quaternionf(),
                    new Vector3f(1.0f, 0.75f, 1.0f),
                    new Quaternionf()));
        });
        return display.getUniqueId();
    }

    private BlockData connectedFenceData() {
        BlockData data = Material.SPRUCE_FENCE.createBlockData();
        if (data instanceof MultipleFacing multipleFacing) {
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                if (multipleFacing.getAllowedFaces().contains(face)) {
                    multipleFacing.setFace(face, true);
                }
            }
        }
        return data;
    }

    private List<UUID> buildSewerWaterFixture(HallsExplorationGenerator.Cell cell, HallsTrapType type) {
        setBlock(cell.x(), origin.y() - 1, cell.z(), Material.WATER);
        setBlock(cell.x(), origin.y() - 2, cell.z(), Material.WATER);
        return List.of(spawnLiquidBottomBlockDisplay(cell, type.blockMaterial(), type.modelScale()));
    }

    private UUID spawnPufferfish(HallsExplorationGenerator.Cell cell) {
        setBlock(cell.x(), origin.y() - 1, cell.z(), Material.WATER);
        setBlock(cell.x(), origin.y() - 2, cell.z(), Material.WATER);
        Location location = new Location(world, cell.x() + 0.5, origin.y() - 0.55, cell.z() + 0.5);
        PufferFish pufferFish = world.spawn(location, PufferFish.class, entity -> {
            entity.setPersistent(false);
            entity.setPuffState(2);
            entity.addScoreboardTag("omgames_hoc_trap");
        });
        return pufferFish.getUniqueId();
    }

    private UUID spawnCeilingBlockDisplay(HallsExplorationGenerator.Cell cell, Material material) {
        Location location = new Location(world, cell.x() + 0.5, origin.y() + ROOM_HEIGHT - 0.15, cell.z() + 0.5);
        BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setInterpolationDelay(1);
            entity.setTeleportDuration(2);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_trap");
            entity.setTransformation(new Transformation(
                    new Vector3f(-0.18f, -0.55f, -0.18f),
                    new Quaternionf(),
                    new Vector3f(0.36f, 1.1f, 0.36f),
                    new Quaternionf()));
        });
        return display.getUniqueId();
    }

    private UUID spawnFloorBlockDisplay(HallsExplorationGenerator.Cell cell, Material material, float scale) {
        Material displayMaterial = material == null || material.isAir() ? Material.IRON_TRAPDOOR : material;
        float normalizedScale = Math.max(0.2f, scale);
        Location location = new Location(world, cell.x() + 0.5, origin.y() + 0.03, cell.z() + 0.5);
        BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(displayMaterial.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setInterpolationDelay(1);
            entity.setTeleportDuration(2);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_trap");
            entity.setTransformation(new Transformation(
                    new Vector3f(-0.4f * normalizedScale, 0.0f, -0.4f * normalizedScale),
                    new Quaternionf(),
                    new Vector3f(0.8f * normalizedScale, 0.06f, 0.8f * normalizedScale),
                    new Quaternionf()));
        });
        return display.getUniqueId();
    }

    private UUID spawnLiquidBottomBlockDisplay(HallsExplorationGenerator.Cell cell, Material material, float scale) {
        Material displayMaterial = material == null || material.isAir() ? Material.IRON_TRAPDOOR : material;
        float normalizedScale = Math.max(0.2f, scale);
        Location location = new Location(world, cell.x() + 0.5, origin.y() - 1.97, cell.z() + 0.5);
        BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(displayMaterial.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setInterpolationDelay(1);
            entity.setTeleportDuration(2);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_trap");
            entity.setTransformation(new Transformation(
                    new Vector3f(-0.4f * normalizedScale, 0.0f, -0.4f * normalizedScale),
                    new Quaternionf(),
                    new Vector3f(0.8f * normalizedScale, 0.06f, 0.8f * normalizedScale),
                    new Quaternionf()));
        });
        return display.getUniqueId();
    }

    private UUID spawnRailDisplay(HallsExplorationGenerator.Cell cell, boolean eastWest, int laneSpan) {
        float length = Math.max(3.0f, laneSpan * 2.0f + 1.0f);
        float half = length / 2.0f;
        Location location = new Location(world, cell.x() + 0.5, origin.y() + ROOM_HEIGHT - 0.08, cell.z() + 0.5);
        BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(Material.BLACK_CONCRETE.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setInterpolationDelay(1);
            entity.setTeleportDuration(2);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_trap");
            entity.setTransformation(new Transformation(
                    eastWest ? new Vector3f(-half, -0.08f, -0.08f) : new Vector3f(-0.08f, -0.08f, -half),
                    new Quaternionf(),
                    eastWest ? new Vector3f(length, 0.16f, 0.16f) : new Vector3f(0.16f, 0.16f, length),
                    new Quaternionf()));
        });
        return display.getUniqueId();
    }

    private UUID spawnWallBlockDisplay(HallsExplorationGenerator.Cell cell,
                                       BlockFace face,
                                       Material material,
                                       float depth,
                                       float width,
                                       float height) {
        Location location = wallDisplayLocation(cell, face, depth);
        BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
            entity.setBlock(wallDisplayBlockData(material, face));
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setInterpolationDelay(1);
            entity.setTeleportDuration(2);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_trap");
            entity.setTransformation(new Transformation(
                    wallDisplayTranslation(face, depth, width, height),
                    new Quaternionf(),
                    wallDisplayScale(face, depth, width, height),
                    new Quaternionf()));
        });
        return display.getUniqueId();
    }

    private UUID spawnTrapItemDisplay(TrapKind kind,
                                      HallsExplorationGenerator.Cell cell,
                                      BlockFace face,
                                      HallsTrapType type,
                                      Material fallbackMaterial,
                                      double laneOffset) {
        Location location = trapModelLocation(kind, cell, face, laneOffset);
        ItemDisplay display = world.spawn(location, ItemDisplay.class);
        Material material = type.modelMaterial().isAir() ? fallbackMaterial : type.modelMaterial();
        ItemStack item = new ItemStack(material.isAir() ? fallbackMaterial : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && !type.itemModel().isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(type.itemModel());
            if (key != null) {
                meta.setItemModel(key);
            }
            item.setItemMeta(meta);
        }
        display.setItemStack(item);
        display.setBillboard(Display.Billboard.FIXED);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setInterpolationDelay(1);
        display.setTeleportDuration(2);
        display.setPersistent(false);
        display.addScoreboardTag("omgames_hoc_trap");
        display.setTransformation(trapModelTransformation(kind, face, type.modelScale(), laneOffset));
        return display.getUniqueId();
    }

    private UUID movingDisplayId(TrapKind kind, List<UUID> displayIds) {
        if (kind == TrapKind.HOMING_MINE && !displayIds.isEmpty()) {
            return displayIds.get(0);
        }
        if ((kind != TrapKind.SWINGING_BLADE && kind != TrapKind.WALL_SPIKES) || displayIds.size() < 2) {
            return null;
        }
        return displayIds.get(1);
    }

    private void startTrapTask() {
        stopTrapTask();
        if (!traps.isEmpty()) {
            trapTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTraps, 1L, 1L);
        }
    }

    private void addTrap(HallsTrap trap, Random random) {
        traps.add(trap);
        trapHitPoints.put(trap, trap.type().hitPoints());
        if (trap.kind() == TrapKind.FALLING_ICE) {
            trapNextTriggerTicks.put(trap, trapRuntimeTick + 25L + random.nextInt(Math.max(1, trap.type().intervalTicks())));
        } else if (trap.kind() == TrapKind.POISON_DARTS) {
            trapNextTriggerTicks.put(trap, trapRuntimeTick);
        }
    }

    private void stopTrapTask() {
        if (trapTask != null) {
            trapTask.cancel();
            trapTask = null;
        }
    }

    private void tickTraps() {
        if (traps.isEmpty()) {
            stopTrapTask();
            return;
        }
        long tick = ++trapRuntimeTick;
        for (HallsTrap trap : List.copyOf(traps)) {
            tickTrap(trap, tick);
        }
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world) && isAliveParticipant(player)) {
                checkPlayerTrapContact(player);
            }
        }
        for (LivingEntity monster : sessionMonsters()) {
            if (canTrapAffectMonsters(monster.getLocation())) {
                checkMonsterTrapContact(monster);
            }
        }
    }

    private void tickTrap(HallsTrap trap, long tick) {
        long age = tick + trap.phase();
        Location center = new Location(world, trap.x() + 0.5, origin.y() + 1.0, trap.z() + 0.5);
        switch (trap.kind()) {
            case SWINGING_BLADE -> {
                Location bladeCenter = moveTrapDisplay(trap, age).orElse(center.clone().add(0.0, 1.3, 0.0));
                damagePlayersInSwingingBlade(trap, bladeCenter, "A swinging blade cuts you down.");
                damageMonstersInSwingingBlade(trap, bladeCenter);
                if (age % 20L == 0L) {
                    world.playSound(bladeCenter, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f, 0.65f);
                }
            }
            case WALL_SPIKES -> {
                long activeAge = age % trap.type().intervalTicks();
                boolean moving = activeAge < trap.type().activeTicks();
                long displayAge = moving ? activeAge : 0L;
                moveTrapDisplay(trap, displayAge);
                double progress = moving
                        ? Math.sin(Math.min(1.0, displayAge / (double) Math.max(1, trap.type().activeTicks())) * Math.PI)
                        : 0.0;
                if (progress > 0.05) {
                    if (activeAge == 0L) {
                        world.playSound(center, Sound.BLOCK_PISTON_EXTEND, 0.7f, 1.45f);
                    }
                    spawnWallSpikeParticles(trap);
                    damagePlayersInLine(trap, trap.type().radius() * progress, 0.4, trap.type().damage(), "Wall spikes pierce you.");
                    damageMonstersInLine(trap, trap.type().radius() * progress, 0.4, trap.type().damage());
                }
            }
            case FALLING_ICE -> {
                if (tick >= trapNextTriggerTicks.getOrDefault(trap, tick + trap.type().intervalTicks())) {
                    launchFallingIce(trap);
                    long delay = Math.max(20L, trap.type().intervalTicks() / 2L)
                            + java.util.concurrent.ThreadLocalRandom.current().nextLong(Math.max(1L, trap.type().intervalTicks()));
                    trapNextTriggerTicks.put(trap, tick + delay);
                }
            }
            case POISON_DARTS -> {
                if (tick >= trapNextTriggerTicks.getOrDefault(trap, 0L) && participantInDartLine(trap)) {
                    spawnDartLine(trap);
                    for (Player player : participantsInLine(trap, trap.type().radius(), 0.45)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                        damagePlayerFromTrap(player, trap.type().damage(), "Poison darts strike from the wall.");
                    }
                    for (LivingEntity monster : monstersInLine(trap, trap.type().radius(), 0.45)) {
                        monster.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                        damageMonsterFromTrap(monster, trap.type().damage());
                    }
                    trapNextTriggerTicks.put(trap, tick + Math.max(60L, trap.type().intervalTicks()));
                }
            }
            case STEAM_VENT -> {
                long activeAge = age % trap.type().intervalTicks();
                if (activeAge < trap.type().activeTicks()) {
                    spawnSteamVentSmoke(trap);
                    if (activeAge % 10L == 0L) {
                        world.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 0.45f, 0.75f);
                    }
                    damagePlayersNear(center, trap.type().radius(), trap.type().damage(), "Scalding steam fills the passage.");
                    damageMonstersNear(center, trap.type().radius(), trap.type().damage());
                } else if (activeAge == trap.type().activeTicks()) {
                    world.playSound(center, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.45f, 0.65f);
                }
            }
            case BUBBLES -> {
                spawnBubbleTrapParticles(trap);
                damagePlayersNear(center, trap.type().radius(), trap.type().damage(), "Scalding bubbles bite at you.");
                damageMonstersNear(center, trap.type().radius(), trap.type().damage());
            }
            case GEYSER -> {
                long activeAge = age % trap.type().intervalTicks();
                if (activeAge < trap.type().activeTicks()) {
                    setGeyserLiquidColumn(trap, Material.BUBBLE_COLUMN);
                    spawnGeyserParticles(center);
                    if (activeAge == 0L) {
                        world.playSound(center, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.0f, 1.35f);
                    }
                    knockbackPlayersInGeyser(trap, center, trap.type().radius());
                    knockbackMonstersInGeyser(trap, center, trap.type().radius());
                } else if (activeAge == trap.type().activeTicks()) {
                    setGeyserLiquidColumn(trap, Material.WATER);
                }
            }
            case HOMING_MINE -> tickHomingMine(trap, tick, center);
            case ENCHANTED_BOOK -> {
                long activeAge = age % trap.type().intervalTicks();
                if (activeAge == 0L) {
                    world.spawnParticle(Particle.ENCHANT, center, 45, trap.type().radius() * 0.35, 0.35, trap.type().radius() * 0.35, 0.05);
                    world.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.45f, 1.55f);
                    damagePlayersNear(center, trap.type().radius(), trap.type().damage(), "An enchanted book detonates beside you.");
                    damageMonstersNear(center, trap.type().radius(), trap.type().damage());
                }
            }
            default -> {
            }
        }
    }

    private void tickHomingMine(HallsTrap trap, long tick, Location center) {
        long armedUntil = trapNextTriggerTicks.getOrDefault(trap, 0L);
        Player target = nearestParticipant(center, 7.0);
        Entity display = trap.movingDisplayId() == null ? null : Bukkit.getEntity(trap.movingDisplayId());
        if (armedUntil <= 0L) {
            if (target == null) {
                return;
            }
            trapNextTriggerTicks.put(trap, tick + Math.max(20L, trap.type().activeTicks()));
            world.playSound(center, Sound.BLOCK_COPPER_BULB_TURN_ON, 0.75f, 0.7f);
        } else if (tick >= armedUntil) {
            triggerProximityMine(trap, target == null ? null : target);
            return;
        }
        if (target != null && display != null) {
            Vector delta = target.getLocation().toVector().subtract(display.getLocation().toVector());
            delta.setY(0.0);
            if (delta.lengthSquared() <= 1.15 * 1.15) {
                triggerProximityMine(trap, target);
                return;
            }
            if (delta.lengthSquared() > 0.04) {
                Location previous = display.getLocation().clone();
                Location next = display.getLocation().add(delta.normalize().multiply(0.22));
                next.setY(homingMinePlateY());
                display.teleport(next);
                moveHomingMineDisplays(trap, previous, next);
                world.spawnParticle(Particle.SMOKE, next.clone().add(0.0, 0.2, 0.0), 2, 0.08, 0.04, 0.08, 0.01);
            }
        }
        if (tick % 10L == 0L) {
            world.playSound(display == null ? center : display.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.8f);
        }
    }

    private void moveHomingMineDisplays(HallsTrap trap, Location previousAnchor, Location nextAnchor) {
        Vector movement = nextAnchor.toVector().subtract(previousAnchor.toVector());
        if (movement.lengthSquared() <= 0.0) {
            return;
        }
        for (UUID displayId : trap.displayIds()) {
            if (displayId.equals(trap.movingDisplayId())) {
                continue;
            }
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null && entity.isValid()) {
                entity.teleport(entity.getLocation().add(movement));
            }
        }
    }

    private void spawnBubbleTrapParticles(HallsTrap trap) {
        Location center = new Location(world, trap.x() + 0.5, origin.y() - 0.45, trap.z() + 0.5);
        world.spawnParticle(Particle.BUBBLE_COLUMN_UP, center, 8, 0.35, 0.35, 0.35, 0.02);
        world.spawnParticle(Particle.BUBBLE_POP, center.clone().add(0.0, 0.85, 0.0), 3, 0.3, 0.15, 0.3, 0.01);
    }

    private void spawnGeyserParticles(Location center) {
        Location base = center.clone().add(0.0, -0.45, 0.0);
        world.spawnParticle(Particle.SPLASH, base.clone().add(0.0, 1.0, 0.0), 24, 0.25, 0.75, 0.25, 0.12);
        world.spawnParticle(Particle.CLOUD, base.clone().add(0.0, 1.4, 0.0), 8, 0.2, 0.5, 0.2, 0.04);
        world.spawnParticle(Particle.BUBBLE_COLUMN_UP, base, 12, 0.35, 0.55, 0.35, 0.08);
    }

    private void setGeyserLiquidColumn(HallsTrap trap, Material material) {
        Material columnMaterial = material == Material.BUBBLE_COLUMN ? Material.BUBBLE_COLUMN : Material.WATER;
        setBlock(trap.x(), origin.y() - 1, trap.z(), columnMaterial);
        setBlock(trap.x(), origin.y() - 2, trap.z(), columnMaterial);
    }

    private void spawnSteamVentSmoke(HallsTrap trap) {
        Location center = new Location(world, trap.x() + 0.5, origin.y() + 0.35, trap.z() + 0.5);
        world.spawnParticle(Particle.CLOUD, center, 8, 1.05, 0.2, 1.05, 0.03);
        world.spawnParticle(Particle.SMOKE, center.clone().add(0.0, 0.55, 0.0), 12, 1.0, 0.45, 1.0, 0.02);
    }

    private void checkPlayerTrapContact(Player player) {
        if (!isAliveParticipant(player)) {
            return;
        }
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        for (HallsTrap trap : List.copyOf(traps)) {
            if (trap.x() != x || trap.z() != z) {
                if (!(trap.kind() == TrapKind.PROXIMITY_MINE && Math.abs(trap.x() - x) <= 1 && Math.abs(trap.z() - z) <= 1)) {
                    continue;
                }
            }
            switch (trap.kind()) {
                case BEAR_TRAP -> triggerBearTrap(trap, player);
                case PROXIMITY_MINE -> triggerProximityMine(trap, player);
                case HOMING_MINE -> triggerProximityMine(trap, player);
                case BUBBLES -> damagePlayerFromTrap(player, trap.type().damage(), "Scalding bubbles bite at you.");
                case HOLE -> {
                    if (player.getLocation().getY() <= origin.y() - Math.max(3, trap.type().depth() - 2)) {
                        damagePlayerFromTrap(player, 200.0, "The pit swallows you.");
                        teleportPlayerToElevator(player);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void checkMonsterTrapContact(LivingEntity monster) {
        int x = monster.getLocation().getBlockX();
        int z = monster.getLocation().getBlockZ();
        for (HallsTrap trap : List.copyOf(traps)) {
            if (trap.x() != x || trap.z() != z) {
                if (!(trap.kind() == TrapKind.PROXIMITY_MINE && Math.abs(trap.x() - x) <= 1 && Math.abs(trap.z() - z) <= 1)) {
                    continue;
                }
            }
            switch (trap.kind()) {
                case BEAR_TRAP -> triggerBearTrap(trap, monster);
                case PROXIMITY_MINE -> triggerProximityMine(trap, monster);
                case HOMING_MINE -> triggerProximityMine(trap, monster);
                case BUBBLES -> damageMonsterFromTrap(monster, trap.type().damage());
                case HOLE -> {
                    if (monster.getLocation().getY() <= origin.y() - Math.max(3, trap.type().depth() - 2)) {
                        damageMonsterFromTrap(monster, 200.0);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void triggerProximityMine(HallsTrap trap, LivingEntity trigger) {
        Location location = activeMineLocation(trap);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        world.spawnParticle(Particle.EXPLOSION, location.clone().add(0.0, 0.45, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticle(Particle.SMOKE, location.clone().add(0.0, 0.35, 0.0), 30, 0.9, 0.35, 0.9, 0.03);
        damagePlayersNear(location, Math.max(2.5, trap.type().radius()), trap.type().damage(), "A proximity mine detonates.");
        damageMonstersNear(location, Math.max(2.5, trap.type().radius()), trap.type().damage());
        removeTrap(trap);
    }

    private Location activeMineLocation(HallsTrap trap) {
        Entity display = trap.movingDisplayId() == null ? null : Bukkit.getEntity(trap.movingDisplayId());
        if (trap.kind() == TrapKind.HOMING_MINE && display != null && display.isValid()) {
            return display.getLocation().clone();
        }
        return new Location(world, trap.x() + 0.5, origin.y(), trap.z() + 0.5);
    }

    private void triggerBearTrap(HallsTrap trap, LivingEntity trigger) {
        Location location = new Location(world, trap.x() + 0.5, origin.y() + 0.1, trap.z() + 0.5);
        world.spawnParticle(Particle.CRIT, location, 12, 0.25, 0.08, 0.25, 0.02);
        world.playSound(location, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.8f, 1.4f);
        if (trigger instanceof Player player) {
            damagePlayerFromTrap(player, trap.type().damage(), "A bear trap snaps shut.");
        } else {
            damageMonsterFromTrap(trigger, trap.type().damage());
        }
        removeTrap(trap);
    }

    private void triggerArmyCoffin(HallsTrap trap, Player player) {
        Location location = new Location(world, trap.x() + 0.5, origin.y() + 0.1, trap.z() + 0.5);
        int roll = java.util.concurrent.ThreadLocalRandom.current().nextInt(100);
        world.spawnParticle(Particle.BLOCK, location, 28, 0.75, 0.35, 0.75, Material.OAK_PLANKS.createBlockData());
        world.playSound(location, Sound.BLOCK_BARREL_OPEN, 0.9f, 0.55f);
        if (roll < 25) {
            spawnTrapMonster("old_bones", location);
            player.sendActionBar(Component.text("Old bones climb out of the coffin.", NamedTextColor.RED));
        } else if (roll < 50) {
            spawnTrapMonster("brooding_mother", location);
            player.sendActionBar(Component.text("Something heavy crawls out of the coffin.", NamedTextColor.RED));
        } else {
            lootDropSink.accept(location);
            player.sendActionBar(Component.text("The coffin breaks open with salvage dust inside.", NamedTextColor.GOLD));
        }
        removeTrap(trap);
    }

    private void damageEnchantedBookTrap(HallsTrap trap, Player player) {
        int remaining = trapHitPoints.getOrDefault(trap, trap.type().hitPoints()) - 1;
        if (remaining > 0) {
            trapHitPoints.put(trap, remaining);
            Location location = new Location(world, trap.x() + 0.5, origin.y() + 0.6, trap.z() + 0.5);
            world.spawnParticle(Particle.ENCHANT, location, 8, 0.25, 0.18, 0.25, 0.02);
            world.playSound(location, Sound.ITEM_BOOK_PAGE_TURN, 0.45f, 1.65f);
            player.sendActionBar(Component.text("Enchanted book HP: " + remaining + "/" + trap.type().hitPoints() + ".",
                    NamedTextColor.LIGHT_PURPLE));
            return;
        }
        Location location = new Location(world, trap.x() + 0.5, origin.y() + 0.6, trap.z() + 0.5);
        world.spawnParticle(Particle.ENCHANT, location, 28, 0.35, 0.25, 0.35, 0.03);
        world.playSound(location, Sound.ITEM_BOOK_PUT, 0.8f, 0.7f);
        player.sendActionBar(Component.text("The enchanted book unravels.", NamedTextColor.LIGHT_PURPLE));
        removeTrap(trap);
    }

    private void spawnTrapMonster(String monsterId, Location location) {
        if (trapMonsterSpawner == null || !trapMonsterSpawner.test(monsterId, location)) {
            lootDropSink.accept(location);
        }
    }

    private void removeTrap(HallsTrap trap) {
        for (UUID displayId : trap.displayIds()) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null) {
                display.remove();
            }
        }
        traps.removeIf(candidate -> candidate == trap);
        trapNextTriggerTicks.remove(trap);
        trapHitPoints.remove(trap);
    }

    private java.util.Optional<Location> moveTrapDisplay(HallsTrap trap, long age) {
        if (trap.movingDisplayId() == null) {
            return java.util.Optional.empty();
        }
        Entity display = Bukkit.getEntity(trap.movingDisplayId());
        if (!(display instanceof ItemDisplay itemDisplay)) {
            return java.util.Optional.empty();
        }
        double offset;
        if (trap.kind() == TrapKind.WALL_SPIKES) {
            double activeProgress = Math.min(1.0, age / (double) Math.max(1, trap.type().activeTicks()));
            offset = 0.15 + Math.sin(activeProgress * Math.PI) * wallTrapReach(trap, Math.min(3.0, trap.type().radius()));
        } else {
            double cycle = (age % trap.type().intervalTicks()) / (double) trap.type().intervalTicks();
            offset = Math.sin(cycle * Math.PI * 2.0) * Math.max(1, trap.laneSpan());
        }
        itemDisplay.setTransformation(trapModelTransformation(trap.kind(), trap.face(), trap.type().modelScale(), offset));
        if (trap.kind() == TrapKind.SWINGING_BLADE) {
            itemDisplay.teleport(trapModelLocation(trap.kind(), new HallsExplorationGenerator.Cell(trap.x(), trap.z()), trap.face(), offset));
        }
        return java.util.Optional.of(trapModelLocation(trap.kind(), new HallsExplorationGenerator.Cell(trap.x(), trap.z()), trap.face(), offset));
    }

    private Location trapModelLocation(TrapKind kind, HallsExplorationGenerator.Cell cell, BlockFace face, double laneOffset) {
        double x = cell.x() + 0.5;
        double y = origin.y() + 0.08;
        double z = cell.z() + 0.5;

        if (kind == TrapKind.BEAR_TRAP || kind == TrapKind.PROXIMITY_MINE || kind == TrapKind.HOMING_MINE) {
            y += 0.4;
        }
        if (kind == TrapKind.HOMING_MINE) {
            y = homingMinePlateY();
        }

        if (kind == TrapKind.SWINGING_BLADE) {
            y = origin.y() + 2.85;
            if (face == BlockFace.EAST || face == BlockFace.WEST) {
                x += laneOffset;
            } else {
                z += laneOffset;
            }
        } else if (requiresWall(kind)) {
            x += face.getModX() * 0.48 - face.getModX() * laneOffset;
            y = origin.y() + 1.55;
            z += face.getModZ() * 0.48 - face.getModZ() * laneOffset;
        }
        return new Location(world, x, y, z);
    }

    private Location wallDisplayLocation(HallsExplorationGenerator.Cell cell, BlockFace face, float depth) {
        return new Location(world,
                cell.x() + 0.5 + face.getModX() * (0.5 - depth),
                origin.y() + 1.5,
                cell.z() + 0.5 + face.getModZ() * (0.5 - depth));
    }

    private double homingMinePlateY() {
        return origin.y() + 0.73;
    }

    private Vector3f wallDisplayTranslation(BlockFace face, float depth, float width, float height) {
        float x = face == BlockFace.EAST || face == BlockFace.WEST ? -depth / 2.0f : -width / 2.0f;
        float y = -height / 2.0f;
        float z = face == BlockFace.NORTH || face == BlockFace.SOUTH ? -depth / 2.0f : -width / 2.0f;
        return new Vector3f(x, y, z);
    }

    private Vector3f wallDisplayScale(BlockFace face, float depth, float width, float height) {
        float x = face == BlockFace.EAST || face == BlockFace.WEST ? depth : width;
        float z = face == BlockFace.NORTH || face == BlockFace.SOUTH ? depth : width;
        return new Vector3f(x, height, z);
    }

    private BlockData wallDisplayBlockData(Material material, BlockFace wallFace) {
        BlockData data = material.createBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(wallFace.getOppositeFace());
        }
        return data;
    }

    private Transformation trapModelTransformation(TrapKind kind, BlockFace face, float scale) {
        return trapModelTransformation(kind, face, scale, 0.0);
    }

    private Transformation trapModelTransformation(TrapKind kind, BlockFace face, float scale, double laneOffset) {
        Vector3f translation = trapModelTranslation(kind, face, laneOffset);
        if (kind == TrapKind.SWINGING_BLADE) {
            Quaternionf leftRotation = face == BlockFace.NORTH || face == BlockFace.SOUTH
                    ? new Quaternionf(-0.7071068f, 0.0f, 0.7071068f, 0.0f)
                    : new Quaternionf(0.0f, 0.0f, 1.0f, 0.0f);
            Quaternionf rightRotation = new Quaternionf(0.0f, 0.0f, -0.38268346f, 0.9238795f);
            return new Transformation(new Vector3f(), leftRotation, new Vector3f(2.0f, 4.0f, 2.0f), rightRotation);
        }
        Quaternionf rotation = new Quaternionf();
        if (kind == TrapKind.WALL_SPIKES) {
            rotation.rotateY((float) Math.toRadians(yawDegrees(wallSpikeBladeFace(face))));
            rotation.rotateX((float) Math.toRadians(180.0));
            rotation.rotateY((float) Math.toRadians(-90.0));
            rotation.rotateZ((float) Math.toRadians(45.0));
            rotation.rotateZ((float) Math.toRadians(180.0));
        } else if (requiresWall(kind)) {
            rotation.rotateY((float) Math.toRadians(yawDegrees(face)));
        } else if (kind == TrapKind.BEAR_TRAP || kind == TrapKind.PROXIMITY_MINE || kind == TrapKind.HOMING_MINE) {
            rotation.rotateY((float) Math.toRadians(yawDegrees(face)));
        } else {
            rotation.rotateX((float) Math.toRadians(90.0));
        }
        return new Transformation(translation, rotation, trapModelScale(kind, face, scale), new Quaternionf());
    }

    private Vector3f trapModelScale(TrapKind kind, BlockFace face, float scale) {
        if (kind == TrapKind.WALL_SPIKES) {
            return new Vector3f(scale, scale, scale * 1.25f);
        }
        return new Vector3f(scale, scale, scale);
    }

    private BlockFace wallSpikeBladeFace(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH ? face : face.getOppositeFace();
    }

    private Vector3f trapModelTranslation(TrapKind kind, BlockFace face, double laneOffset) {
        if (kind == TrapKind.SWINGING_BLADE) {
            return new Vector3f();
        }
        if (requiresWall(kind)) {
            return new Vector3f(
                    (float) (-face.getModX() * laneOffset),
                    0.0f,
                    (float) (-face.getModZ() * laneOffset));
        }
        return new Vector3f();
    }

    private void launchFallingIce(HallsTrap trap) {
        double xOffset = java.util.concurrent.ThreadLocalRandom.current().nextInt(-1, 2);
        double zOffset = java.util.concurrent.ThreadLocalRandom.current().nextInt(-1, 2);
        Location start = new Location(world, trap.x() + 0.5 + xOffset, origin.y() + ROOM_HEIGHT - 0.2, trap.z() + 0.5 + zOffset);
        if (!world.getBlockAt(start.getBlockX(), origin.y() - 1, start.getBlockZ()).getType().isSolid()) {
            start = new Location(world, trap.x() + 0.5, origin.y() + ROOM_HEIGHT - 0.2, trap.z() + 0.5);
        }
        int impactY = fallingIceImpactY(start);
        BlockDisplay display = world.spawn(start, BlockDisplay.class, entity -> {
            entity.setBlock(Material.PACKED_ICE.createBlockData());
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setInterpolationDelay(1);
            entity.setTeleportDuration(2);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_trap");
            entity.setTransformation(new Transformation(new Vector3f(-0.25f, -0.25f, -0.25f), new Quaternionf(),
                    new Vector3f(0.5f, 1.2f, 0.5f), new Quaternionf()));
        });
        transientTrapDisplays.add(display.getUniqueId());
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!display.isValid()) {
                    transientTrapDisplays.remove(display.getUniqueId());
                    cancel();
                    return;
                }
                Location next = display.getLocation().add(0.0, -0.42, 0.0);
                display.teleport(next);
                world.spawnParticle(Particle.SNOWFLAKE, next, 3, 0.1, 0.1, 0.1, 0.0);
                if (next.getY() <= impactY + 0.15) {
                    world.spawnParticle(Particle.BLOCK, next, 24, 0.35, 0.3, 0.35, Material.PACKED_ICE.createBlockData());
                    world.playSound(next, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.6f);
                    damagePlayersNear(next, trap.type().radius(), trap.type().damage(), "Falling ice shatters above you.");
                    damageMonstersNear(next, trap.type().radius(), trap.type().damage());
                    display.remove();
                    transientTrapDisplays.remove(display.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private int fallingIceImpactY(Location start) {
        int x = start.getBlockX();
        int z = start.getBlockZ();
        for (int y = origin.y() + ROOM_HEIGHT - 1; y >= origin.y() - 16; y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return y + 1;
            }
        }
        return origin.y();
    }

    private void spawnDartLine(HallsTrap trap) {
        Location start = new Location(world,
                trap.x() + 0.5 + trap.face().getModX() * 0.45,
                origin.y() + 1.45,
                trap.z() + 0.5 + trap.face().getModZ() * 0.45);
        double reach = wallTrapReach(trap, trap.type().radius());
        for (double distance = 0.0; distance <= reach; distance += 0.35) {
            Location point = start.clone().add(
                    -trap.face().getModX() * distance,
                    0.0,
                    -trap.face().getModZ() * distance);
            world.spawnParticle(Particle.CRIT, point, 1, 0.01, 0.01, 0.01, 0.0);
        }
        world.playSound(start, Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.6f);
    }

    private boolean participantInDartLine(HallsTrap trap) {
        return !participantsInLine(trap, wallTrapReach(trap, trap.type().radius()) + 1.0, 1.35).isEmpty();
    }

    private List<Player> participantsInLine(HallsTrap trap, double radius, double width) {
        List<Player> players = new ArrayList<>();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world)
                    && isAliveParticipant(player)
                    && isLocationInLine(trap, player.getLocation(), radius, width)) {
                players.add(player);
            }
        }
        return players;
    }

    private void damagePlayersInLine(HallsTrap trap, double radius, double width, double damage, String message) {
        for (Player player : participantsInLine(trap, wallTrapReach(trap, radius), width)) {
            if (trap.kind() == TrapKind.WALL_SPIKES && player.getLocation().getY() < origin.y() - 0.05) {
                continue;
            }
            damagePlayerFromTrap(player, damage, message);
        }
    }

    private void damageMonstersInLine(HallsTrap trap, double radius, double width, double damage) {
        for (LivingEntity monster : monstersInLine(trap, wallTrapReach(trap, radius), width)) {
            if (trap.kind() == TrapKind.WALL_SPIKES && monster.getLocation().getY() < origin.y() - 0.05) {
                continue;
            }
            damageMonsterFromTrap(monster, damage);
        }
    }

    private boolean isLocationInLine(HallsTrap trap, Location location, double radius, double width) {
        double dx = location.getX() - (trap.x() + 0.5);
        double dz = location.getZ() - (trap.z() + 0.5);
        double forward = -(dx * trap.face().getModX() + dz * trap.face().getModZ());
        double lateral = Math.abs(trap.face().getModX() == 0 ? dx : dz);
        return forward >= 0.0 && forward <= radius && lateral <= width;
    }

    private Set<HallsExplorationGenerator.Cell> borderCells(Set<HallsExplorationGenerator.Cell> cells) {
        Set<HallsExplorationGenerator.Cell> border = new HashSet<>();
        for (HallsExplorationGenerator.Cell cell : cells) {
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                HallsExplorationGenerator.Cell side = new HallsExplorationGenerator.Cell(
                        cell.x() + face.getModX(),
                        cell.z() + face.getModZ());
                if (!cells.contains(side)) {
                    border.add(side);
                }
            }
        }
        return border;
    }

    private double wallTrapReach(HallsTrap trap, double configuredReach) {
        double maxReach = Math.max(0.5, configuredReach);
        for (int distance = 1; distance <= Math.ceil(maxReach); distance++) {
            int x = trap.x() - trap.face().getModX() * distance;
            int z = trap.z() - trap.face().getModZ() * distance;
            if (world.getBlockAt(x, origin.y() + 1, z).getType().isSolid()) {
                return Math.max(0.5, distance - 0.35);
            }
        }
        return maxReach;
    }

    private void spawnWallSpikeParticles(HallsTrap trap) {
        Location start = new Location(world,
                trap.x() + 0.5 + trap.face().getModX() * 0.45,
                origin.y() + 1.45,
                trap.z() + 0.5 + trap.face().getModZ() * 0.45);
        double reach = wallTrapReach(trap, Math.min(3.0, trap.type().radius()));
        for (double distance = 0.2; distance <= reach; distance += 0.6) {
            Location point = start.clone().add(
                    -trap.face().getModX() * distance,
                    0.0,
                    -trap.face().getModZ() * distance);
            world.spawnParticle(Particle.BLOCK, point, 2, 0.08, 0.08, 0.08, Material.POINTED_DRIPSTONE.createBlockData());
        }
    }

    private void damagePlayersNear(Location center, double radius, double damage, String message) {
        for (Player player : nearbyParticipants(center, radius)) {
            damagePlayerFromTrap(player, damage, message);
        }
    }

    private void damageMonstersNear(Location center, double radius, double damage) {
        if (!canTrapAffectMonsters(center)) {
            return;
        }
        double radiusSquared = radius * radius;
        for (LivingEntity monster : sessionMonsters()) {
            if (monster.getLocation().distanceSquared(center) <= radiusSquared) {
                damageMonsterFromTrap(monster, damage);
            }
        }
    }

    private void knockbackPlayersNear(Location center, double radius) {
        for (Player player : nearbyParticipants(center, radius)) {
            applyGeyserKnockback(player, center);
        }
    }

    private void knockbackPlayersInGeyser(HallsTrap trap, Location center, double radius) {
        for (Player player : geyserParticipants(trap, radius)) {
            applyGeyserKnockback(player, center);
        }
    }

    private void knockbackMonstersNear(Location center, double radius) {
        if (!canTrapAffectMonsters(center)) {
            return;
        }
        double radiusSquared = radius * radius;
        for (LivingEntity monster : sessionMonsters()) {
            if (monster.getLocation().distanceSquared(center) <= radiusSquared) {
                applyGeyserKnockback(monster, center);
            }
        }
    }

    private void knockbackMonstersInGeyser(HallsTrap trap, Location center, double radius) {
        if (!canTrapAffectMonsters(center)) {
            return;
        }
        double radiusSquared = radius * radius;
        for (LivingEntity monster : sessionMonsters()) {
            if (isInGeyserColumn(monster.getLocation(), trap, radiusSquared)) {
                applyGeyserKnockback(monster, center);
            }
        }
    }

    private void applyGeyserKnockback(LivingEntity entity, Location center) {
        Vector direction = entity.getLocation().toVector().subtract(center.toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() < 0.01) {
            direction = new Vector(0.0, 0.0, 1.0);
        }
        direction.normalize().multiply(0.9);
        direction.setY(1.25);
        entity.setVelocity(direction);
    }

    private List<Player> nearbyParticipants(Location center, double radius) {
        double radiusSquared = radius * radius;
        List<Player> players = new ArrayList<>();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world)
                    && isAliveParticipant(player)
                    && player.getLocation().distanceSquared(center) <= radiusSquared) {
                players.add(player);
            }
        }
        return players;
    }

    private Player nearestParticipant(Location center, double radius) {
        Player best = null;
        double bestDistance = radius * radius;
        for (Player player : nearbyParticipants(center, radius)) {
            double distance = player.getLocation().distanceSquared(center);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private List<Player> geyserParticipants(HallsTrap trap, double radius) {
        double radiusSquared = radius * radius;
        List<Player> players = new ArrayList<>();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world)
                    && isAliveParticipant(player)
                    && isInGeyserColumn(player.getLocation(), trap, radiusSquared)) {
                players.add(player);
            }
        }
        return players;
    }

    private boolean isInGeyserColumn(Location location, HallsTrap trap, double radiusSquared) {
        if (location == null || !location.getWorld().equals(world)) {
            return false;
        }
        double dx = location.getX() - (trap.x() + 0.5);
        double dz = location.getZ() - (trap.z() + 0.5);
        double feetY = location.getY();
        double headY = feetY + 1.8;
        boolean overlapsWaterColumn = headY >= origin.y() - 2.25 && feetY <= origin.y() + 1.35;
        return overlapsWaterColumn && dx * dx + dz * dz <= radiusSquared;
    }

    private void damagePlayerFromTrap(Player player, double damage, String message) {
        long now = System.currentTimeMillis();
        long nextAllowed = trapDamageCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextAllowed) {
            return;
        }
        trapDamageCooldowns.put(player.getUniqueId(), now + 900L);
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
        player.damage(damage * Math.max(0.0, trapDamageMultiplier.getAsDouble()));
    }

    private void damageMonsterFromTrap(LivingEntity monster, double damage) {
        if (monster == null || monster.isDead() || !monster.isValid()) {
            return;
        }
        long now = System.currentTimeMillis();
        long nextAllowed = trapDamageCooldowns.getOrDefault(monster.getUniqueId(), 0L);
        if (now < nextAllowed) {
            return;
        }
        trapDamageCooldowns.put(monster.getUniqueId(), now + 350L);
        monster.damage(damage);
    }

    private boolean canTrapAffectMonsters(Location center) {
        return center != null && !nearbyParticipants(center, MONSTER_TRAP_PLAYER_RADIUS).isEmpty();
    }

    private void teleportPlayerToElevator(Player player) {
        player.teleport(new Location(world, origin.x() + 0.5, origin.y() + 1.0, origin.z() + 0.5, 180.0f, 0.0f));
    }

    private TrapKind trapKind(String kind) {
        return switch (kind) {
            case "bear_trap" -> TrapKind.BEAR_TRAP;
            case "proximity_mine" -> TrapKind.PROXIMITY_MINE;
            case "swinging_blade" -> TrapKind.SWINGING_BLADE;
            case "wall_spikes" -> TrapKind.WALL_SPIKES;
            case "falling_ice" -> TrapKind.FALLING_ICE;
            case "poison_darts" -> TrapKind.POISON_DARTS;
            case "steam_vent" -> TrapKind.STEAM_VENT;
            case "bubbles" -> TrapKind.BUBBLES;
            case "geyser" -> TrapKind.GEYSER;
            case "pufferfish" -> TrapKind.PUFFERFISH;
            case "army_coffin" -> TrapKind.ARMY_COFFIN;
            case "homing_mine" -> TrapKind.HOMING_MINE;
            case "enchanted_book" -> TrapKind.ENCHANTED_BOOK;
            default -> null;
        };
    }

    private boolean requiresWall(TrapKind kind) {
        return kind == TrapKind.WALL_SPIKES || kind == TrapKind.POISON_DARTS;
    }

    private boolean isWaterTrap(TrapKind kind) {
        return kind == TrapKind.BUBBLES || kind == TrapKind.GEYSER || kind == TrapKind.PUFFERFISH;
    }

    private BlockFace trapFace(TrapKind kind, TrapCandidate candidate, Random random) {
        if (kind == TrapKind.SWINGING_BLADE) {
            int eastWestSpan = swingLaneHalfSpan(candidate, BlockFace.EAST);
            int northSouthSpan = swingLaneHalfSpan(candidate, BlockFace.NORTH);
            int bestSpan = Math.max(eastWestSpan, northSouthSpan);
            if (bestSpan < 1) {
                return BlockFace.SELF;
            }
            if (eastWestSpan == northSouthSpan) {
                return random.nextBoolean() ? BlockFace.EAST : BlockFace.NORTH;
            }
            return eastWestSpan > northSouthSpan ? BlockFace.EAST : BlockFace.NORTH;
        }
        if (kind == TrapKind.FALLING_ICE) {
            return hasFallingIceArea(candidate) ? BlockFace.DOWN : BlockFace.SELF;
        }
        if (!requiresWall(kind)) {
            return BlockFace.SELF;
        }
        List<BlockFace> faces = new ArrayList<>(List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST));
        Collections.shuffle(faces, random);
        for (BlockFace face : faces) {
            if (!candidate.roomCells().contains(step(candidate.cell(), face)) && wallTrapOpenRun(candidate, face) >= 2) {
                return face;
            }
        }
        return BlockFace.SELF;
    }

    private int swingLaneHalfSpan(TrapCandidate candidate, BlockFace face) {
        boolean eastWest = face == BlockFace.EAST || face == BlockFace.WEST;
        int halfSpan = 0;
        int maxSpan = eastWest ? candidate.room().layout().width() : candidate.room().layout().depth();
        for (int offset = 1; offset <= maxSpan; offset++) {
            HallsExplorationGenerator.Cell negative = eastWest
                    ? new HallsExplorationGenerator.Cell(candidate.cell().x() - offset, candidate.cell().z())
                    : new HallsExplorationGenerator.Cell(candidate.cell().x(), candidate.cell().z() - offset);
            HallsExplorationGenerator.Cell positive = eastWest
                    ? new HallsExplorationGenerator.Cell(candidate.cell().x() + offset, candidate.cell().z())
                    : new HallsExplorationGenerator.Cell(candidate.cell().x(), candidate.cell().z() + offset);
            if (!candidate.roomCells().contains(negative) || !candidate.roomCells().contains(positive)) {
                break;
            }
            halfSpan = offset;
        }
        return halfSpan;
    }

    private int bestSwingLaneHalfSpan(TrapCandidate candidate) {
        return Math.max(swingLaneHalfSpan(candidate, BlockFace.EAST), swingLaneHalfSpan(candidate, BlockFace.NORTH));
    }

    private boolean hasSwingLane(TrapCandidate candidate, BlockFace face) {
        return swingLaneHalfSpan(candidate, face) >= 1;
    }

    private Set<HallsExplorationGenerator.Cell> trapFootprint(TrapKind kind, HallsExplorationGenerator.Cell cell, BlockFace face, int laneSpan) {
        Set<HallsExplorationGenerator.Cell> footprint = new HashSet<>();
        int radius = kind == TrapKind.PROXIMITY_MINE || kind == TrapKind.HOMING_MINE ? 1 : 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                footprint.add(new HallsExplorationGenerator.Cell(cell.x() + dx, cell.z() + dz));
            }
        }
        if (kind == TrapKind.SWINGING_BLADE) {
            boolean eastWest = face == BlockFace.EAST || face == BlockFace.WEST;
            for (int offset = -laneSpan; offset <= laneSpan; offset++) {
                HallsExplorationGenerator.Cell laneCell = eastWest
                        ? new HallsExplorationGenerator.Cell(cell.x() + offset, cell.z())
                        : new HallsExplorationGenerator.Cell(cell.x(), cell.z() + offset);
                footprint.add(laneCell);
            }
        }
        return footprint;
    }

    private boolean hasFallingIceArea(TrapCandidate candidate) {
        int open = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (candidate.roomCells().contains(new HallsExplorationGenerator.Cell(
                        candidate.cell().x() + dx,
                        candidate.cell().z() + dz))) {
                    open++;
                }
            }
        }
        return open >= 5;
    }

    private int wallTrapOpenRun(TrapCandidate candidate, BlockFace wallFace) {
        int run = 0;
        for (int distance = 1; distance <= 5; distance++) {
            HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(
                    candidate.cell().x() - wallFace.getModX() * distance,
                    candidate.cell().z() - wallFace.getModZ() * distance);
            if (!candidate.roomCells().contains(cell)) {
                break;
            }
            run++;
        }
        return run;
    }

    private HallsExplorationGenerator.Cell step(HallsExplorationGenerator.Cell cell, BlockFace face) {
        return switch (face) {
            case NORTH -> new HallsExplorationGenerator.Cell(cell.x(), cell.z() - 1);
            case SOUTH -> new HallsExplorationGenerator.Cell(cell.x(), cell.z() + 1);
            case EAST -> new HallsExplorationGenerator.Cell(cell.x() + 1, cell.z());
            case WEST -> new HallsExplorationGenerator.Cell(cell.x() - 1, cell.z());
            default -> cell;
        };
    }

    private float yawDegrees(BlockFace face) {
        return switch (face) {
            case NORTH -> 180.0f;
            case SOUTH -> 0.0f;
            case EAST -> -90.0f;
            case WEST -> 90.0f;
            default -> 0.0f;
        };
    }

    private void setBlock(int x, int y, int z, Material material) {
        setBlock(x, y, z, material, null);
    }

    private void setBlock(int x, int y, int z, Material material, BlockFace face) {
        blockSetter.setBlock(x, y, z, material, face);
    }

    @FunctionalInterface
    interface BlockSetter {
        void setBlock(int x, int y, int z, Material material, BlockFace face);
    }

    private record TrapCandidate(HallsExplorationGenerator.Room room,
                                 HallsExplorationGenerator.Cell cell,
                                 Set<HallsExplorationGenerator.Cell> roomCells,
                                 Set<HallsExplorationGenerator.Cell> allRoomCells) {
    }

    private void damagePlayersInSwingingBlade(HallsTrap trap, Location bladeCenter, String message) {
        boolean eastWest = trap.face() == BlockFace.EAST || trap.face() == BlockFace.WEST;
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.getWorld().equals(world) || !isAliveParticipant(player)) {
                continue;
            }
            Location location = player.getLocation();
            double along = Math.abs(eastWest ? location.getX() - bladeCenter.getX() : location.getZ() - bladeCenter.getZ());
            double lateral = Math.abs(eastWest ? location.getZ() - bladeCenter.getZ() : location.getX() - bladeCenter.getX());
            double feet = location.getY();
            double head = feet + Math.max(1.6, player.getHeight());
            boolean verticalOverlap = head >= origin.y() + 1.5 && feet <= origin.y() + 3.5;
            if (verticalOverlap && along <= bladeHalfAlong() && lateral <= bladeHalfLateral(trap)) {
                damagePlayerFromTrap(player, trap.type().damage(), message);
            }
        }
    }

    private void damageMonstersInSwingingBlade(HallsTrap trap, Location bladeCenter) {
        if (!canTrapAffectMonsters(bladeCenter)) {
            return;
        }
        boolean eastWest = trap.face() == BlockFace.EAST || trap.face() == BlockFace.WEST;
        for (LivingEntity monster : sessionMonsters()) {
            Location location = monster.getLocation();
            double along = Math.abs(eastWest ? location.getX() - bladeCenter.getX() : location.getZ() - bladeCenter.getZ());
            double lateral = Math.abs(eastWest ? location.getZ() - bladeCenter.getZ() : location.getX() - bladeCenter.getX());
            double feet = location.getY();
            double head = feet + Math.max(1.0, monster.getHeight());
            boolean verticalOverlap = head >= origin.y() + 1.5 && feet <= origin.y() + 3.5;
            if (verticalOverlap && along <= bladeHalfAlong() && lateral <= bladeHalfLateral(trap)) {
                damageMonsterFromTrap(monster, trap.type().damage());
            }
        }
    }

    private List<LivingEntity> monstersInLine(HallsTrap trap, double radius, double width) {
        Location center = new Location(world, trap.x() + 0.5, origin.y() + 1.0, trap.z() + 0.5);
        if (!canTrapAffectMonsters(center)) {
            return List.of();
        }
        List<LivingEntity> monsters = new ArrayList<>();
        for (LivingEntity monster : sessionMonsters()) {
            if (isLocationInLine(trap, monster.getLocation(), radius, width)) {
                monsters.add(monster);
            }
        }
        return monsters;
    }

    private List<LivingEntity> sessionMonsters() {
        return world.getLivingEntities().stream()
                .filter(entity -> entity.getScoreboardTags().contains("omgames_hoc_monster"))
                .toList();
    }

    private boolean isAliveParticipant(Player player) {
        return player != null && aliveParticipant.test(player.getUniqueId());
    }

    private double bladeHalfAlong() {
        return 0.55;
    }

    private double bladeHalfLateral(HallsTrap trap) {
        return Math.max(0.45, trap.type().radius() * 0.67);
    }

    private record HallsTrap(TrapKind kind, int x, int z, int phase, HallsTrapType type, UUID movingDisplayId, List<UUID> displayIds, BlockFace face, int laneSpan) {
    }

    private enum TrapKind {
        HOLE,
        HOLE_BRIDGE,
        BEAR_TRAP,
        PROXIMITY_MINE,
        HOMING_MINE,
        SWINGING_BLADE,
        WALL_SPIKES,
        FALLING_ICE,
        POISON_DARTS,
        STEAM_VENT,
        BUBBLES,
        GEYSER,
        PUFFERFISH,
        ARMY_COFFIN,
        ENCHANTED_BOOK
    }
}
