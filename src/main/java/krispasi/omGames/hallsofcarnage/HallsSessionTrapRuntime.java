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
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class HallsSessionTrapRuntime {
    private static final int ROOM_HEIGHT = 5;
    private static final int ELEVATOR_OUTER_RADIUS = 3;

    private final JavaPlugin plugin;
    private final World world;
    private final HallsConfig.BlockPoint origin;
    private final Set<UUID> participants;
    private final BlockSetter blockSetter;
    private final Map<String, HallsTrapType> trapTypes;
    private final List<HallsTrap> traps = new ArrayList<>();
    private final Map<UUID, Long> trapDamageCooldowns = new java.util.HashMap<>();
    private final Map<HallsTrap, Long> trapNextTriggerTicks = new IdentityHashMap<>();
    private final Set<UUID> transientTrapDisplays = new HashSet<>();
    private BukkitTask trapTask;
    private long trapRuntimeTick;

    HallsSessionTrapRuntime(JavaPlugin plugin,
                            World world,
                            HallsConfig.BlockPoint origin,
                            Set<UUID> participants,
                            BlockSetter blockSetter,
                            Map<String, HallsTrapType> trapTypes) {
        this.plugin = plugin;
        this.world = world;
        this.origin = origin;
        this.participants = participants;
        this.blockSetter = blockSetter;
        this.trapTypes = trapTypes == null ? Map.of() : Map.copyOf(trapTypes);
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

    boolean handlePlayerMove(Player player, boolean running) {
        if (player == null || !running || !participants.contains(player.getUniqueId()) || !player.getWorld().equals(world)) {
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

    Set<HallsExplorationGenerator.Cell> placeGeneratedTraps(HallsExplorationGenerator.Plan plan,
                                                            Random random,
                                                            HallsScenario.FloorDefinition floorDefinition,
                                                            HallsLevelType levelType) {
        clear();
        List<TrapCandidate> candidates = trapCandidates(plan);
        if (candidates.isEmpty()) {
            return Set.of();
        }
        int targetHoles = Math.min(candidates.size(), Math.max(0, floorDefinition.holes()));
        int targetTraps = Math.min(candidates.size(), Math.max(0, floorDefinition.traps()));
        List<HallsTrapType> pool = trapPool(levelType.id());
        HallsTrapType holeType = trapTypes.values().stream()
                .filter(type -> type.kind().equals("hole") && type.weight() > 0 && type.allowedForLevelType(levelType.id()))
                .findFirst()
                .orElse(null);
        if ((pool.isEmpty() || targetTraps <= 0) && (holeType == null || targetHoles <= 0)) {
            return Set.of();
        }
        Collections.shuffle(candidates, random);
        Set<HallsExplorationGenerator.Cell> occupied = new HashSet<>();
        int holesPlaced = 0;
        for (TrapCandidate candidate : candidates) {
            HallsExplorationGenerator.Cell cell = candidate.cell();
            if (holesPlaced >= targetHoles) {
                break;
            }
            if (holeType == null || isNearExistingTrap(cell, occupied)) {
                continue;
            }
            if (placeHole(candidate, plan, random, holeType, occupied)) {
                holesPlaced++;
            }
        }
        int placed = 0;
        Collections.shuffle(candidates, random);
        for (TrapCandidate candidate : candidates) {
            HallsExplorationGenerator.Cell cell = candidate.cell();
            if (placed >= targetTraps || isNearExistingTrap(cell, occupied) || pool.isEmpty()) {
                continue;
            }
            HallsTrapType type = weightedTrap(pool, random);
            TrapKind kind = trapKind(type.kind());
            if (kind == null) {
                continue;
            }
            BlockFace face = trapFace(kind, candidate, random);
            if ((requiresWall(kind) || kind == TrapKind.SWINGING_BLADE || kind == TrapKind.FALLING_ICE)
                    && face == BlockFace.SELF) {
                continue;
            }
            int laneSpan = kind == TrapKind.SWINGING_BLADE ? swingLaneHalfSpan(candidate, face) : 0;
            Set<HallsExplorationGenerator.Cell> footprint = trapFootprint(kind, cell, face, laneSpan);
            if ((kind == TrapKind.PROXIMITY_MINE || requiresWall(kind))
                    && !floorReachableWithout(plan.walkableCells(), footprint)) {
                continue;
            }
            List<UUID> displayIds = buildTrap(kind, cell, face, type, laneSpan);
            addTrap(new HallsTrap(kind, cell.x(), cell.z(), random.nextInt(80), type,
                    movingDisplayId(kind, displayIds), displayIds, face, laneSpan), random);
            occupied.addAll(footprint);
            placed++;
        }
        startTrapTask();
        return Set.copyOf(occupied);
    }

    private boolean placeHole(TrapCandidate candidate,
                              HallsExplorationGenerator.Plan plan,
                              Random random,
                              HallsTrapType type,
                              Set<HallsExplorationGenerator.Cell> occupied) {
        Set<HallsExplorationGenerator.Cell> pitCells = pitMask(candidate, random, type);
        if (pitCells.isEmpty() || anyNearExistingTrap(pitCells, occupied)) {
            return false;
        }
        Set<HallsExplorationGenerator.Cell> bridgeCells = bridgeCellsIfNeeded(plan.walkableCells(), pitCells);
        if (bridgeCells == null) {
            return false;
        }
        buildPit(pitCells, bridgeCells, type);
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

    private List<TrapCandidate> trapCandidates(HallsExplorationGenerator.Plan plan) {
        Set<HallsExplorationGenerator.Cell> walkable = plan.walkableCells();
        List<TrapCandidate> candidates = new ArrayList<>();
        for (HallsExplorationGenerator.Room room : plan.rooms()) {
            Set<HallsExplorationGenerator.Cell> candidateCells = Set.copyOf(roomTrapCandidateCells(room));
            Set<HallsExplorationGenerator.Cell> openCells = Set.copyOf(roomOpenCells(room));
            Set<HallsExplorationGenerator.Cell> roomCells = Set.copyOf(roomAllCells(room));
            Set<HallsExplorationGenerator.Cell> doorwayCells = Set.copyOf(roomDoorwayCells(room));
            for (HallsExplorationGenerator.Cell cell : candidateCells) {
                if (walkable.contains(cell) && farFromElevator(cell)) {
                    candidates.add(new TrapCandidate(cell, openCells, roomCells, doorwayCells));
                }
            }
        }
        return candidates;
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
        for (int z = 1; z < room.layout().depth() - 1; z++) {
            for (int x = 1; x < room.layout().width() - 1; x++) {
                cells.add(new HallsExplorationGenerator.Cell(room.startX() + x, room.startZ() + z));
            }
        }
        return cells;
    }

    private List<HallsExplorationGenerator.Cell> roomDoorwayCells(HallsExplorationGenerator.Room room) {
        List<HallsExplorationGenerator.Cell> cells = new ArrayList<>();
        for (Map.Entry<BlockFace, Integer> opening : room.openings().entrySet()) {
            HallsExplorationGenerator.Cell doorway = switch (opening.getKey()) {
                case NORTH -> new HallsExplorationGenerator.Cell(room.startX() + opening.getValue(), room.startZ());
                case SOUTH -> new HallsExplorationGenerator.Cell(room.startX() + opening.getValue(), room.startZ() + room.layout().depth() - 1);
                case EAST -> new HallsExplorationGenerator.Cell(room.startX() + room.layout().width() - 1, room.startZ() + opening.getValue());
                case WEST -> new HallsExplorationGenerator.Cell(room.startX(), room.startZ() + opening.getValue());
                default -> null;
            };
            if (doorway != null) {
                cells.add(doorway);
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

    private HallsTrapType weightedTrap(List<HallsTrapType> pool, Random random) {
        int totalWeight = pool.stream().mapToInt(HallsTrapType::weight).sum();
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (HallsTrapType type : pool) {
            roll -= type.weight();
            if (roll < 0) {
                return type;
            }
        }
        return pool.getFirst();
    }

    private boolean anyNearExistingTrap(Set<HallsExplorationGenerator.Cell> cells, Set<HallsExplorationGenerator.Cell> occupied) {
        for (HallsExplorationGenerator.Cell cell : cells) {
            if (isNearExistingTrap(cell, occupied)) {
                return true;
            }
        }
        return false;
    }

    private Set<HallsExplorationGenerator.Cell> pitMask(TrapCandidate candidate, Random random, HallsTrapType type) {
        int minSize = Math.max(5, type.minSize());
        int maxSize = Math.max(minSize, type.maxSize());
        int size = minSize + random.nextInt(maxSize - minSize + 1);
        if (size % 2 == 0) {
            size++;
        }
        for (int currentSize = size; currentSize >= minSize; currentSize -= 2) {
            int radius = currentSize / 2;
            Set<HallsExplorationGenerator.Cell> cells = new HashSet<>();
            boolean complete = true;
            for (int dx = -radius; dx <= radius && complete; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(
                            candidate.cell().x() + dx,
                            candidate.cell().z() + dz
                    );
                    if (!candidate.allRoomCells().contains(cell)) {
                        complete = false;
                        break;
                    }
                    cells.add(cell);
                }
            }
            if (complete && pitOpenCellCount(cells, candidate.roomCells()) >= Math.max(5, cells.size() / 3)
                    && !pitNearDoorway(cells, candidate.doorwayCells())) {
                return cells;
            }
        }
        return Set.of();
    }

    private int pitOpenCellCount(Set<HallsExplorationGenerator.Cell> pitCells,
                                 Set<HallsExplorationGenerator.Cell> openRoomCells) {
        int count = 0;
        for (HallsExplorationGenerator.Cell cell : pitCells) {
            if (openRoomCells.contains(cell)) {
                count++;
            }
        }
        return count;
    }

    private boolean pitNearDoorway(Set<HallsExplorationGenerator.Cell> pitCells,
                                   Set<HallsExplorationGenerator.Cell> doorwayCells) {
        for (HallsExplorationGenerator.Cell pitCell : pitCells) {
            for (HallsExplorationGenerator.Cell doorway : doorwayCells) {
                if (Math.abs(pitCell.x() - doorway.x()) + Math.abs(pitCell.z() - doorway.z()) <= 3) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<HallsExplorationGenerator.Cell> bridgeCellsIfNeeded(Set<HallsExplorationGenerator.Cell> walkable,
                                                                    Set<HallsExplorationGenerator.Cell> pitCells) {
        if (floorReachableWithout(walkable, pitCells)) {
            return Set.of();
        }
        int minX = pitCells.stream().mapToInt(HallsExplorationGenerator.Cell::x).min().orElse(0);
        int maxX = pitCells.stream().mapToInt(HallsExplorationGenerator.Cell::x).max().orElse(0);
        int minZ = pitCells.stream().mapToInt(HallsExplorationGenerator.Cell::z).min().orElse(0);
        int maxZ = pitCells.stream().mapToInt(HallsExplorationGenerator.Cell::z).max().orElse(0);
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        Set<HallsExplorationGenerator.Cell> bridge = new HashSet<>();
        if ((maxX - minX) >= (maxZ - minZ)) {
            for (int x = minX; x <= maxX; x++) {
                HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(x, centerZ);
                if (pitCells.contains(cell)) {
                    bridge.add(cell);
                }
            }
        } else {
            for (int z = minZ; z <= maxZ; z++) {
                HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(centerX, z);
                if (pitCells.contains(cell)) {
                    bridge.add(cell);
                }
            }
        }
        return floorReachableWithout(walkable, difference(pitCells, bridge)) ? bridge : null;
    }

    private Set<HallsExplorationGenerator.Cell> difference(Set<HallsExplorationGenerator.Cell> cells,
                                                           Set<HallsExplorationGenerator.Cell> removed) {
        Set<HallsExplorationGenerator.Cell> result = new HashSet<>(cells);
        result.removeAll(removed);
        return result;
    }

    private boolean floorReachableWithout(Set<HallsExplorationGenerator.Cell> walkable,
                                          Set<HallsExplorationGenerator.Cell> blocked) {
        HallsExplorationGenerator.Cell start = new HallsExplorationGenerator.Cell(origin.x(), origin.z() + ELEVATOR_OUTER_RADIUS + 1);
        if (!walkable.contains(start) || blocked.contains(start)) {
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

    private List<UUID> buildTrap(TrapKind kind, HallsExplorationGenerator.Cell cell, BlockFace face, HallsTrapType type, int laneSpan) {
        return switch (kind) {
            case BEAR_TRAP, PROXIMITY_MINE -> {
                setBlock(cell.x(), origin.y(), cell.z(), type.blockMaterial());
                yield List.of();
            }
            case SWINGING_BLADE -> buildSwingingBlade(cell, face, type, laneSpan);
            case WALL_SPIKES -> buildWallSpikes(cell, face, type);
            case FALLING_ICE -> type.ceilingMaterial().isAir() ? List.of() : List.of(spawnCeilingBlockDisplay(cell, type.ceilingMaterial()));
            case POISON_DARTS -> buildPoisonDartLauncher(cell, face, type);
            default -> List.of();
        };
    }

    private void buildPit(Set<HallsExplorationGenerator.Cell> pitCells,
                          Set<HallsExplorationGenerator.Cell> bridgeCells,
                          HallsTrapType type) {
        for (HallsExplorationGenerator.Cell cell : pitCells) {
            setBlock(cell.x(), origin.y() - 1, cell.z(), Material.AIR);
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                HallsExplorationGenerator.Cell side = step(cell, face);
                if (!pitCells.contains(side)) {
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
        UUID base = spawnWallBlockDisplay(cell, face, Material.BLACK_CONCRETE, 0.04f, 0.9f, 0.9f);
        UUID spikes = spawnTrapItemDisplay(TrapKind.WALL_SPIKES, cell, face, type, Material.IRON_SWORD, 0.0);
        return List.of(base, spikes);
    }

    private List<UUID> buildPoisonDartLauncher(HallsExplorationGenerator.Cell cell, BlockFace face, HallsTrapType type) {
        return List.of(spawnWallBlockDisplay(cell, face, type.blockMaterial(), 0.08f, 0.65f, 0.65f));
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
            if (player != null && player.getWorld().equals(world)) {
                checkPlayerTrapContact(player);
            }
        }
    }

    private void tickTrap(HallsTrap trap, long tick) {
        long age = tick + trap.phase();
        Location center = new Location(world, trap.x() + 0.5, origin.y() + 1.0, trap.z() + 0.5);
        switch (trap.kind()) {
            case SWINGING_BLADE -> {
                boolean active = age % trap.type().intervalTicks() < trap.type().activeTicks();
                Location bladeCenter = moveTrapDisplay(trap, age).orElse(center.clone().add(0.0, 1.3, 0.0));
                if (age % 5L == 0L) {
                    world.spawnParticle(Particle.CRIT, bladeCenter, active ? 8 : 2, 0.35, 0.15, 0.35, 0.02);
                }
                if (active) {
                    if (age % 2L == 0L) {
                        visualizeSwingingBladeHitbox(trap, bladeCenter);
                    }
                    damagePlayersInSwingingBlade(trap, bladeCenter, "A swinging blade cuts you down.");
                }
            }
            case WALL_SPIKES -> {
                long activeAge = age % trap.type().intervalTicks();
                boolean active = activeAge < trap.type().activeTicks();
                moveTrapDisplay(trap, active ? activeAge : 0L);
                if (active) {
                    spawnWallSpikeParticles(trap);
                    damagePlayersInLine(trap, trap.type().radius(), 0.4, trap.type().damage(), "Wall spikes pierce you.");
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
                    trapNextTriggerTicks.put(trap, tick + Math.max(60L, trap.type().intervalTicks()));
                }
            }
            default -> {
            }
        }
    }

    private void checkPlayerTrapContact(Player player) {
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        for (HallsTrap trap : List.copyOf(traps)) {
            if (trap.x() != x || trap.z() != z) {
                if (!(trap.kind() == TrapKind.PROXIMITY_MINE && Math.abs(trap.x() - x) <= 1 && Math.abs(trap.z() - z) <= 1)) {
                    continue;
                }
            }
            switch (trap.kind()) {
                case BEAR_TRAP -> damagePlayerFromTrap(player, trap.type().damage(), "A bear trap snaps shut.");
                case PROXIMITY_MINE -> triggerProximityMine(trap, player);
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

    private void triggerProximityMine(HallsTrap trap, Player player) {
        Location location = new Location(world, trap.x() + 0.5, origin.y(), trap.z() + 0.5);
        world.createExplosion(location, trap.type().explosionPower(), false, false);
        damagePlayersNear(location, Math.max(2.5, trap.type().radius()), trap.type().damage(), "A proximity mine detonates.");
        setBlock(trap.x(), origin.y(), trap.z(), Material.AIR);
        traps.removeIf(candidate -> candidate == trap);
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
        return java.util.Optional.of(trapModelLocation(trap.kind(), new HallsExplorationGenerator.Cell(trap.x(), trap.z()), trap.face(), offset));
    }

    private Location trapModelLocation(TrapKind kind, HallsExplorationGenerator.Cell cell, BlockFace face, double laneOffset) {
        double x = cell.x() + 0.5;
        double y = origin.y() + 0.08;
        double z = cell.z() + 0.5;
        if (kind == TrapKind.SWINGING_BLADE) {
            y = origin.y() + 2.1;
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
        Quaternionf rotation = new Quaternionf();
        if (kind == TrapKind.SWINGING_BLADE) {
            rotation.rotateX((float) Math.toRadians(180.0));
            if (face == BlockFace.EAST || face == BlockFace.WEST) {
                rotation.rotateZ((float) Math.toRadians(90.0));
            }
        } else if (kind == TrapKind.WALL_SPIKES) {
            rotation.rotateY((float) Math.toRadians(yawDegrees(face)));
            rotation.rotateX((float) Math.toRadians(180.0));
        } else if (requiresWall(kind)) {
            rotation.rotateY((float) Math.toRadians(yawDegrees(face)));
        } else {
            rotation.rotateX((float) Math.toRadians(90.0));
        }
        return new Transformation(translation, rotation, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private Vector3f trapModelTranslation(TrapKind kind, BlockFace face, double laneOffset) {
        if (kind == TrapKind.SWINGING_BLADE) {
            return face == BlockFace.EAST || face == BlockFace.WEST
                    ? new Vector3f((float) laneOffset, 0.0f, 0.0f)
                    : new Vector3f(0.0f, 0.0f, (float) laneOffset);
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
        if (world.getBlockAt(start.getBlockX(), origin.y(), start.getBlockZ()).getType().isSolid()) {
            start = new Location(world, trap.x() + 0.5, origin.y() + ROOM_HEIGHT - 0.2, trap.z() + 0.5);
        }
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
                if (next.getY() <= origin.y() + 0.7) {
                    world.spawnParticle(Particle.BLOCK, next, 24, 0.35, 0.3, 0.35, Material.PACKED_ICE.createBlockData());
                    world.playSound(next, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.6f);
                    damagePlayersNear(next, trap.type().radius(), trap.type().damage(), "Falling ice shatters above you.");
                    display.remove();
                    transientTrapDisplays.remove(display.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
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
        return !participantsInLine(trap, wallTrapReach(trap, trap.type().radius()), 0.45).isEmpty();
    }

    private List<Player> participantsInLine(HallsTrap trap, double radius, double width) {
        List<Player> players = new ArrayList<>();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world)
                    && isLocationInLine(trap, player.getLocation(), radius, width)) {
                players.add(player);
            }
        }
        return players;
    }

    private void damagePlayersInLine(HallsTrap trap, double radius, double width, double damage, String message) {
        for (Player player : participantsInLine(trap, wallTrapReach(trap, radius), width)) {
            damagePlayerFromTrap(player, damage, message);
        }
    }

    private boolean isLocationInLine(HallsTrap trap, Location location, double radius, double width) {
        double dx = location.getX() - (trap.x() + 0.5);
        double dz = location.getZ() - (trap.z() + 0.5);
        double forward = -(dx * trap.face().getModX() + dz * trap.face().getModZ());
        double lateral = Math.abs(trap.face().getModX() == 0 ? dx : dz);
        return forward >= 0.0 && forward <= radius && lateral <= width;
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

    private List<Player> nearbyParticipants(Location center, double radius) {
        double radiusSquared = radius * radius;
        List<Player> players = new ArrayList<>();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world)
                    && player.getLocation().distanceSquared(center) <= radiusSquared) {
                players.add(player);
            }
        }
        return players;
    }

    private void damagePlayerFromTrap(Player player, double damage, String message) {
        long now = System.currentTimeMillis();
        long nextAllowed = trapDamageCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextAllowed) {
            return;
        }
        trapDamageCooldowns.put(player.getUniqueId(), now + 900L);
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
        player.damage(damage);
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
            default -> null;
        };
    }

    private boolean requiresWall(TrapKind kind) {
        return kind == TrapKind.WALL_SPIKES || kind == TrapKind.POISON_DARTS;
    }

    private BlockFace trapFace(TrapKind kind, TrapCandidate candidate, Random random) {
        if (kind == TrapKind.SWINGING_BLADE) {
            List<BlockFace> faces = new ArrayList<>(List.of(BlockFace.EAST, BlockFace.NORTH));
            Collections.shuffle(faces, random);
            for (BlockFace face : faces) {
                if (hasSwingLane(candidate, face)) {
                    return face;
                }
            }
            return BlockFace.SELF;
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
        for (int offset = 1; offset <= 5; offset++) {
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

    private boolean hasSwingLane(TrapCandidate candidate, BlockFace face) {
        return swingLaneHalfSpan(candidate, face) >= 1;
    }

    private Set<HallsExplorationGenerator.Cell> trapFootprint(TrapKind kind, HallsExplorationGenerator.Cell cell, BlockFace face, int laneSpan) {
        Set<HallsExplorationGenerator.Cell> footprint = new HashSet<>();
        int radius = kind == TrapKind.PROXIMITY_MINE ? 1 : 0;
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

    private record TrapCandidate(HallsExplorationGenerator.Cell cell,
                                 Set<HallsExplorationGenerator.Cell> roomCells,
                                 Set<HallsExplorationGenerator.Cell> allRoomCells,
                                 Set<HallsExplorationGenerator.Cell> doorwayCells) {
    }

    private void damagePlayersInSwingingBlade(HallsTrap trap, Location bladeCenter, String message) {
        boolean eastWest = trap.face() == BlockFace.EAST || trap.face() == BlockFace.WEST;
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.getWorld().equals(world)) {
                continue;
            }
            Location location = player.getLocation();
            double along = Math.abs(eastWest ? location.getX() - bladeCenter.getX() : location.getZ() - bladeCenter.getZ());
            double lateral = Math.abs(eastWest ? location.getZ() - bladeCenter.getZ() : location.getX() - bladeCenter.getX());
            double feet = location.getY();
            double head = feet + Math.max(1.6, player.getHeight());
            boolean verticalOverlap = head >= origin.y() + 0.75 && feet <= origin.y() + 2.75;
            if (verticalOverlap && along <= 0.85 && lateral <= Math.max(0.65, trap.type().radius())) {
                damagePlayerFromTrap(player, trap.type().damage(), message);
            }
        }
    }

    private void visualizeSwingingBladeHitbox(HallsTrap trap, Location bladeCenter) {
        boolean eastWest = trap.face() == BlockFace.EAST || trap.face() == BlockFace.WEST;
        double halfAlong = 0.85;
        double halfLateral = Math.max(0.65, trap.type().radius());
        for (double along = -halfAlong; along <= halfAlong + 0.01; along += 0.35) {
            for (double lateral = -halfLateral; lateral <= halfLateral + 0.01; lateral += Math.max(0.35, halfLateral)) {
                spawnBladeHitboxParticle(bladeCenter, eastWest, along, lateral, origin.y() + 0.95);
                spawnBladeHitboxParticle(bladeCenter, eastWest, along, lateral, origin.y() + 2.55);
            }
        }
        for (double lateral = -halfLateral; lateral <= halfLateral + 0.01; lateral += 0.35) {
            spawnBladeHitboxParticle(bladeCenter, eastWest, -halfAlong, lateral, origin.y() + 1.75);
            spawnBladeHitboxParticle(bladeCenter, eastWest, halfAlong, lateral, origin.y() + 1.75);
        }
    }

    private void spawnBladeHitboxParticle(Location center, boolean eastWest, double along, double lateral, double y) {
        Location point = new Location(world,
                center.getX() + (eastWest ? along : lateral),
                y,
                center.getZ() + (eastWest ? lateral : along));
        world.spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private record HallsTrap(TrapKind kind, int x, int z, int phase, HallsTrapType type, UUID movingDisplayId, List<UUID> displayIds, BlockFace face, int laneSpan) {
    }

    private enum TrapKind {
        HOLE,
        HOLE_BRIDGE,
        BEAR_TRAP,
        PROXIMITY_MINE,
        SWINGING_BLADE,
        WALL_SPIKES,
        FALLING_ICE,
        POISON_DARTS
    }
}
