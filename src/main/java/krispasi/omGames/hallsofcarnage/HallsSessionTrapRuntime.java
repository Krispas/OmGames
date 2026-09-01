package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private BukkitTask trapTask;

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
            if (trap.displayId() != null) {
                Entity display = Bukkit.getEntity(trap.displayId());
                if (display != null) {
                    display.remove();
                }
            }
        }
        traps.clear();
        trapDamageCooldowns.clear();
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

    void placeGeneratedTraps(HallsExplorationGenerator.Plan plan,
                             Random random,
                             HallsScenario.FloorDefinition floorDefinition,
                             HallsLevelType levelType) {
        clear();
        List<TrapCandidate> candidates = trapCandidates(plan);
        if (candidates.isEmpty()) {
            return;
        }
        int targetTraps = Math.min(candidates.size(), Math.max(0, floorDefinition.traps()));
        List<HallsTrapType> pool = trapPool(levelType.id());
        if (pool.isEmpty() || targetTraps <= 0) {
            return;
        }
        Collections.shuffle(candidates, random);
        Set<HallsExplorationGenerator.Cell> occupied = new HashSet<>();
        int placed = 0;
        for (TrapCandidate candidate : candidates) {
            HallsExplorationGenerator.Cell cell = candidate.cell();
            if (placed >= targetTraps || isNearExistingTrap(cell, occupied)) {
                continue;
            }
            HallsTrapType type = weightedTrap(pool, random);
            if (type.kind().equals("hole")) {
                Set<HallsExplorationGenerator.Cell> pitCells = pitMask(candidate, random, type);
                if (pitCells.isEmpty()) {
                    continue;
                }
                Set<HallsExplorationGenerator.Cell> bridgeCells = bridgeCellsIfNeeded(plan.walkableCells(), pitCells);
                if (bridgeCells == null) {
                    continue;
                }
                buildPit(pitCells, bridgeCells, type);
                TrapKind kind = bridgeCells.isEmpty() ? TrapKind.HOLE : TrapKind.HOLE_BRIDGE;
                for (HallsExplorationGenerator.Cell pitCell : pitCells) {
                    if (!bridgeCells.contains(pitCell)) {
                        traps.add(new HallsTrap(kind, pitCell.x(), pitCell.z(), random.nextInt(80), type, null));
                    }
                }
                occupied.addAll(pitCells);
            } else {
                TrapKind kind = trapKind(type.kind());
                if (kind == null) {
                    continue;
                }
                buildTrap(kind, cell, random, type);
                traps.add(new HallsTrap(kind, cell.x(), cell.z(), random.nextInt(80), type, spawnTrapModel(cell, type)));
                occupied.add(cell);
            }
            placed++;
        }
        startTrapTask();
    }

    private List<TrapCandidate> trapCandidates(HallsExplorationGenerator.Plan plan) {
        Set<HallsExplorationGenerator.Cell> walkable = plan.walkableCells();
        List<TrapCandidate> candidates = new ArrayList<>();
        for (HallsExplorationGenerator.Room room : plan.rooms()) {
            Set<HallsExplorationGenerator.Cell> roomCells = Set.copyOf(roomOpenInteriorCells(room));
            for (HallsExplorationGenerator.Cell cell : roomCells) {
                if (walkable.contains(cell) && farFromElevator(cell)) {
                    candidates.add(new TrapCandidate(cell, roomCells));
                }
            }
        }
        return candidates;
    }

    private List<HallsExplorationGenerator.Cell> roomOpenInteriorCells(HallsExplorationGenerator.Room room) {
        List<HallsExplorationGenerator.Cell> cells = new ArrayList<>();
        for (int z = 1; z < room.layout().depth() - 1; z++) {
            for (int x = 1; x < room.layout().width() - 1; x++) {
                if (room.layout().at(x, z) == 'O'
                        && x != room.layout().width() / 2
                        && z != room.layout().depth() / 2) {
                    cells.add(new HallsExplorationGenerator.Cell(room.startX() + x, room.startZ() + z));
                }
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

    private Set<HallsExplorationGenerator.Cell> pitMask(TrapCandidate candidate, Random random, HallsTrapType type) {
        int minSize = Math.max(5, type.minSize());
        int maxSize = Math.max(minSize, type.maxSize());
        int size = minSize + random.nextInt(maxSize - minSize + 1);
        if (size % 2 == 0) {
            size++;
        }
        int radius = size / 2;
        Set<HallsExplorationGenerator.Cell> cells = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) == radius && Math.abs(dz) == radius && random.nextBoolean()) {
                    continue;
                }
                HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(
                        candidate.cell().x() + dx,
                        candidate.cell().z() + dz
                );
                if (candidate.roomCells().contains(cell)) {
                    cells.add(cell);
                }
            }
        }
        return cells;
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

    private void buildTrap(TrapKind kind, HallsExplorationGenerator.Cell cell, Random random, HallsTrapType type) {
        switch (kind) {
            case BEAR_TRAP, PROXIMITY_MINE -> setBlock(cell.x(), origin.y(), cell.z(), type.blockMaterial());
            case SWINGING_BLADE -> buildSwingingBlade(cell, type);
            case WALL_SPIKES -> setBlock(cell.x(), origin.y(), cell.z(), type.blockMaterial(), BlockFace.UP);
            case FALLING_ICE -> setBlock(cell.x(), origin.y() + ROOM_HEIGHT - 1, cell.z(), type.ceilingMaterial(), BlockFace.DOWN);
            case POISON_DARTS -> setBlock(cell.x(), origin.y() + 1, cell.z(), type.blockMaterial(), random.nextBoolean() ? BlockFace.EAST : BlockFace.WEST);
            default -> {
            }
        }
    }

    private void buildPit(Set<HallsExplorationGenerator.Cell> pitCells,
                          Set<HallsExplorationGenerator.Cell> bridgeCells,
                          HallsTrapType type) {
        for (HallsExplorationGenerator.Cell cell : pitCells) {
            setBlock(cell.x(), origin.y() - 1, cell.z(), Material.AIR);
            for (int y = origin.y() - 2; y >= origin.y() - type.depth(); y--) {
                setBlock(cell.x(), y, cell.z(), y == origin.y() - type.depth() ? Material.BLACK_CONCRETE : Material.AIR);
            }
        }
        for (HallsExplorationGenerator.Cell cell : bridgeCells) {
            setBlock(cell.x(), origin.y() - 1, cell.z(), type.bridgeMaterial());
        }
    }

    private void buildSwingingBlade(HallsExplorationGenerator.Cell cell, HallsTrapType type) {
        setBlock(cell.x(), origin.y() + 3, cell.z(), type.blockMaterial());
        setBlock(cell.x(), origin.y() + 2, cell.z(), type.blockMaterial());
    }

    private java.util.UUID spawnTrapModel(HallsExplorationGenerator.Cell cell, HallsTrapType type) {
        if (type.itemModel().isBlank()) {
            return null;
        }
        Location location = new Location(world, cell.x() + 0.5, origin.y() + 0.05, cell.z() + 0.5);
        ItemDisplay display = world.spawn(location, ItemDisplay.class);
        ItemStack item = new ItemStack(type.modelMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey key = NamespacedKey.fromString(type.itemModel());
            if (key != null) {
                meta.setItemModel(key);
            }
            item.setItemMeta(meta);
        }
        display.setItemStack(item);
        display.setBillboard(Display.Billboard.FIXED);
        display.setInterpolationDelay(1);
        display.setTeleportDuration(2);
        display.setTransformation(new Transformation(
                new Vector3f(-0.5f, 0.02f, -0.5f),
                new Quaternionf().rotateX((float) Math.toRadians(90.0)),
                new Vector3f(type.modelScale(), type.modelScale(), type.modelScale()),
                new Quaternionf()
        ));
        return display.getUniqueId();
    }

    private void startTrapTask() {
        stopTrapTask();
        if (!traps.isEmpty()) {
            trapTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTraps, 10L, 10L);
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
        long tick = world.getFullTime();
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
                world.spawnParticle(Particle.CRIT, center.clone().add(0.0, 1.3, 0.0), active ? 16 : 4, 0.55, 0.15, 0.55, 0.02);
                if (active) {
                    damagePlayersNear(center, trap.type().radius(), trap.type().damage(), "A swinging blade cuts you down.");
                }
            }
            case WALL_SPIKES -> {
                boolean active = age % trap.type().intervalTicks() < trap.type().activeTicks();
                if (active) {
                    world.spawnParticle(Particle.BLOCK, center, 10, 0.45, 0.25, 0.45, Material.POINTED_DRIPSTONE.createBlockData());
                    damagePlayersNear(center, trap.type().radius(), trap.type().damage(), "Wall spikes pierce you.");
                }
            }
            case FALLING_ICE -> {
                if (age % trap.type().intervalTicks() == 0L) {
                    world.spawnParticle(Particle.BLOCK, center.clone().add(0.0, 3.5, 0.0), 24, 0.35, 0.3, 0.35, Material.PACKED_ICE.createBlockData());
                    world.playSound(center, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.6f);
                    damagePlayersNear(center, trap.type().radius(), trap.type().damage(), "Falling ice shatters above you.");
                }
            }
            case POISON_DARTS -> {
                if (age % trap.type().intervalTicks() == 0L) {
                    world.spawnParticle(Particle.SWEEP_ATTACK, center, 8, 2.5, 0.05, 0.05, 0.01);
                    for (Player player : nearbyParticipants(center, trap.type().radius())) {
                        if (player.getLocation().getBlockZ() == trap.z()) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                            damagePlayerFromTrap(player, trap.type().damage(), "Poison darts strike from the wall.");
                        }
                    }
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
                continue;
            }
            switch (trap.kind()) {
                case BEAR_TRAP -> damagePlayerFromTrap(player, trap.type().damage(), "A bear trap snaps shut.");
                case PROXIMITY_MINE -> triggerProximityMine(trap, player);
                case HOLE -> {
                    if (player.getLocation().getY() < origin.y()) {
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
        damagePlayerFromTrap(player, trap.type().damage(), "A proximity mine detonates.");
        setBlock(trap.x(), origin.y(), trap.z(), Material.AIR);
        traps.removeIf(candidate -> candidate == trap);
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

    private record TrapCandidate(HallsExplorationGenerator.Cell cell, Set<HallsExplorationGenerator.Cell> roomCells) {
    }

    private record HallsTrap(TrapKind kind, int x, int z, int phase, HallsTrapType type, java.util.UUID displayId) {
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
