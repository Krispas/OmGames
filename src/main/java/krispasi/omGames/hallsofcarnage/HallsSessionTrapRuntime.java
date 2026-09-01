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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

final class HallsSessionTrapRuntime {
    private static final int ROOM_HEIGHT = 5;
    private static final int ELEVATOR_OUTER_RADIUS = 3;

    private final JavaPlugin plugin;
    private final World world;
    private final HallsConfig.BlockPoint origin;
    private final Set<UUID> participants;
    private final BlockSetter blockSetter;
    private final List<HallsTrap> traps = new ArrayList<>();
    private final Map<UUID, Long> trapDamageCooldowns = new java.util.HashMap<>();
    private BukkitTask trapTask;

    HallsSessionTrapRuntime(JavaPlugin plugin,
                            World world,
                            HallsConfig.BlockPoint origin,
                            Set<UUID> participants,
                            BlockSetter blockSetter) {
        this.plugin = plugin;
        this.world = world;
        this.origin = origin;
        this.participants = participants;
        this.blockSetter = blockSetter;
    }

    void clear() {
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
        List<HallsExplorationGenerator.Cell> candidates = trapCandidates(plan);
        if (candidates.isEmpty()) {
            return;
        }
        int difficulty = parseDifficulty(floorDefinition.difficulty());
        int targetTraps = Math.min(candidates.size(), Math.max(3, plan.rooms().size() / 3 + difficulty / 12));
        List<TrapKind> pool = new ArrayList<>(List.of(
                TrapKind.HOLE,
                TrapKind.BEAR_TRAP,
                TrapKind.SWINGING_BLADE,
                TrapKind.WALL_SPIKES,
                TrapKind.PROXIMITY_MINE
        ));
        if (levelType.id().equals("frozen_halls")) {
            pool.add(TrapKind.FALLING_ICE);
        }
        if (levelType.id().equals("deep_crypt")) {
            pool.add(TrapKind.POISON_DARTS);
        }
        Collections.shuffle(candidates, random);
        Set<HallsExplorationGenerator.Cell> occupied = new HashSet<>();
        int placed = 0;
        for (HallsExplorationGenerator.Cell cell : candidates) {
            if (placed >= targetTraps || isNearExistingTrap(cell, occupied)) {
                continue;
            }
            TrapKind kind = pool.get(random.nextInt(pool.size()));
            if (kind == TrapKind.HOLE && !floorReachableWithout(plan.walkableCells(), cell)) {
                buildPitBridge(cell);
                traps.add(new HallsTrap(TrapKind.HOLE_BRIDGE, cell.x(), cell.z(), random.nextInt(80)));
            } else {
                buildTrap(kind, cell, random);
                traps.add(new HallsTrap(kind, cell.x(), cell.z(), random.nextInt(80)));
            }
            occupied.add(cell);
            placed++;
        }
        startTrapTask();
    }

    private List<HallsExplorationGenerator.Cell> trapCandidates(HallsExplorationGenerator.Plan plan) {
        Set<HallsExplorationGenerator.Cell> walkable = plan.walkableCells();
        List<HallsExplorationGenerator.Cell> candidates = new ArrayList<>();
        for (HallsExplorationGenerator.Room room : plan.rooms()) {
            for (HallsExplorationGenerator.Cell cell : roomOpenInteriorCells(room)) {
                if (walkable.contains(cell) && farFromElevator(cell)) {
                    candidates.add(cell);
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

    private boolean floorReachableWithout(Set<HallsExplorationGenerator.Cell> walkable, HallsExplorationGenerator.Cell blocked) {
        HallsExplorationGenerator.Cell start = new HallsExplorationGenerator.Cell(origin.x(), origin.z() + ELEVATOR_OUTER_RADIUS + 1);
        if (!walkable.contains(start) || blocked.equals(start)) {
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
                if (!next.equals(blocked) && walkable.contains(next) && reachable.add(next)) {
                    queue.add(next);
                }
            }
        }
        for (HallsExplorationGenerator.Cell cell : walkable) {
            if (!cell.equals(blocked) && !reachable.contains(cell)) {
                return false;
            }
        }
        return true;
    }

    private void buildTrap(TrapKind kind, HallsExplorationGenerator.Cell cell, Random random) {
        switch (kind) {
            case HOLE -> buildPit(cell);
            case BEAR_TRAP -> setBlock(cell.x(), origin.y(), cell.z(), Material.IRON_TRAPDOOR);
            case PROXIMITY_MINE -> setBlock(cell.x(), origin.y(), cell.z(), Material.STONE_PRESSURE_PLATE);
            case SWINGING_BLADE -> buildSwingingBlade(cell);
            case WALL_SPIKES -> setBlock(cell.x(), origin.y(), cell.z(), Material.POINTED_DRIPSTONE, BlockFace.UP);
            case FALLING_ICE -> setBlock(cell.x(), origin.y() + ROOM_HEIGHT - 1, cell.z(), Material.POINTED_DRIPSTONE, BlockFace.DOWN);
            case POISON_DARTS -> setBlock(cell.x(), origin.y() + 1, cell.z(), Material.DISPENSER, random.nextBoolean() ? BlockFace.EAST : BlockFace.WEST);
            case HOLE_BRIDGE -> buildPitBridge(cell);
        }
    }

    private void buildPit(HallsExplorationGenerator.Cell cell) {
        setBlock(cell.x(), origin.y() - 1, cell.z(), Material.AIR);
        for (int y = origin.y() - 2; y >= origin.y() - 10; y--) {
            setBlock(cell.x(), y, cell.z(), y == origin.y() - 10 ? Material.BLACK_CONCRETE : Material.AIR);
        }
    }

    private void buildPitBridge(HallsExplorationGenerator.Cell cell) {
        buildPit(cell);
        setBlock(cell.x(), origin.y() - 1, cell.z(), Material.SPRUCE_PLANKS);
        setBlock(cell.x() - 1, origin.y(), cell.z(), Material.SPRUCE_FENCE);
        setBlock(cell.x() + 1, origin.y(), cell.z(), Material.SPRUCE_FENCE);
    }

    private void buildSwingingBlade(HallsExplorationGenerator.Cell cell) {
        setBlock(cell.x(), origin.y() + 3, cell.z(), Material.IRON_BARS);
        setBlock(cell.x(), origin.y() + 2, cell.z(), Material.IRON_BARS);
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
                boolean active = age % 60L < 16L;
                world.spawnParticle(Particle.CRIT, center.clone().add(0.0, 1.3, 0.0), active ? 16 : 4, 0.55, 0.15, 0.55, 0.02);
                if (active) {
                    damagePlayersNear(center, 1.15, 200.0, "A swinging blade cuts you down.");
                }
            }
            case WALL_SPIKES -> {
                boolean active = age % 70L < 12L;
                if (active) {
                    world.spawnParticle(Particle.BLOCK, center, 10, 0.45, 0.25, 0.45, Material.POINTED_DRIPSTONE.createBlockData());
                    damagePlayersNear(center, 1.1, 12.0, "Wall spikes pierce you.");
                }
            }
            case FALLING_ICE -> {
                if (age % 90L == 0L) {
                    world.spawnParticle(Particle.BLOCK, center.clone().add(0.0, 3.5, 0.0), 24, 0.35, 0.3, 0.35, Material.PACKED_ICE.createBlockData());
                    world.playSound(center, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.6f);
                    damagePlayersNear(center, 1.0, 200.0, "Falling ice shatters above you.");
                }
            }
            case POISON_DARTS -> {
                if (age % 55L == 0L) {
                    world.spawnParticle(Particle.SWEEP_ATTACK, center, 8, 2.5, 0.05, 0.05, 0.01);
                    for (Player player : nearbyParticipants(center, 5.5)) {
                        if (player.getLocation().getBlockZ() == trap.z()) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
                            damagePlayerFromTrap(player, 4.0, "Poison darts strike from the wall.");
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
                case BEAR_TRAP -> damagePlayerFromTrap(player, 12.0, "A bear trap snaps shut.");
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
        world.createExplosion(location, 2.4f, false, false);
        damagePlayerFromTrap(player, 18.0, "A proximity mine detonates.");
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

    private int parseDifficulty(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String digits = value.replaceAll("[^0-9-]", "");
        if (digits.isBlank() || digits.equals("-")) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return 0;
        }
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

    private record HallsTrap(TrapKind kind, int x, int z, int phase) {
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
