package krispasi.omGames.bedwars.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import krispasi.omGames.bedwars.event.BedwarsMoonBigConfig;
import krispasi.omGames.bedwars.event.BedwarsMatchEventType;
import krispasi.omGames.bedwars.model.BlockPoint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

final class GameSessionMoonBigAsteroidRuntime {
    private static final Material ASTEROID_MATERIAL = Material.MAGMA_BLOCK;
    private static final int ASTEROID_SPAWN_Y = 300;
    private static final int ASTEROID_SUB_STEPS = 2;

    private final GameSession session;
    private final List<BukkitTask> sessionTasks;
    private final Map<BlockPoint, OccupiedAsteroidBlock> occupiedBlocks = new HashMap<>();

    GameSessionMoonBigAsteroidRuntime(GameSession session, List<BukkitTask> sessionTasks) {
        this.session = session;
        this.sessionTasks = sessionTasks;
    }

    void startLoop() {
        BedwarsMoonBigConfig config = session.getBedwarsManager().getMoonBigConfig();
        if (config == null) {
            return;
        }
        scheduleNext(config);
    }

    void reset() {
        for (Map.Entry<BlockPoint, OccupiedAsteroidBlock> entry : new ArrayList<>(occupiedBlocks.entrySet())) {
            restoreOccupiedBlock(entry.getKey(), entry.getValue());
        }
        occupiedBlocks.clear();
    }

    private void scheduleNext(BedwarsMoonBigConfig config) {
        if (session.plugin == null || config == null) {
            return;
        }
        double intervalSeconds = session.resolveMoonBigAsteroidIntervalSeconds(config);
        if (intervalSeconds <= 0.0) {
            return;
        }
        long delayTicks = Math.max(1L, Math.round(intervalSeconds * 20.0));
        BukkitTask task = session.plugin.getServer().getScheduler().runTaskLater(session.plugin, () -> {
            session.safeRun("moonBigAsteroid", () -> {
                if (!session.isRunning() || session.getActiveMatchEvent() != BedwarsMatchEventType.MOON_BIG) {
                    return;
                }
                spawnAsteroid(config);
                scheduleNext(config);
            });
        }, delayTicks);
        sessionTasks.add(task);
    }

    private void spawnAsteroid(BedwarsMoonBigConfig config) {
        World world = session.getArena().getWorld();
        BlockPoint corner1 = session.getArena().getCorner1();
        BlockPoint corner2 = session.getArena().getCorner2();
        if (world == null || corner1 == null || corner2 == null || config == null) {
            return;
        }
        int minX = Math.min(corner1.x(), corner2.x());
        int maxX = Math.max(corner1.x(), corner2.x());
        int minZ = Math.min(corner1.z(), corner2.z());
        int maxZ = Math.max(corner1.z(), corner2.z());
        int x = ThreadLocalRandom.current().nextInt(minX, maxX + 1);
        int z = ThreadLocalRandom.current().nextInt(minZ, maxZ + 1);
        int groundY = world.getHighestBlockYAt(x, z);
        if (groundY <= world.getMinHeight() + 1) {
            return;
        }
        Block groundBlock = world.getBlockAt(x, groundY, z);
        if (groundBlock.getType().isAir()) {
            return;
        }
        int radius = ThreadLocalRandom.current().nextInt(config.radiusMin(), config.radiusMax() + 1);
        int maxValidSpawnY = world.getMaxHeight() - 1 - radius;
        int spawnY = Math.min(ASTEROID_SPAWN_Y, maxValidSpawnY);
        if (spawnY <= groundY + 1) {
            return;
        }
        startAsteroid(world, x, spawnY + 0.5, z, groundY, radius, config);
    }

    private void startAsteroid(World world,
                               int centerX,
                               double centerY,
                               int centerZ,
                               int groundY,
                               int radius,
                               BedwarsMoonBigConfig config) {
        if (world == null || config == null) {
            return;
        }
        List<BlockPoint> offsets = buildSphereOffsets(radius);
        Set<BlockPoint> initialPositions = resolveAsteroidBlocks(centerX, centerY, centerZ, offsets);
        if (initialPositions.isEmpty()) {
            return;
        }
        if (findCollisionPoint(world, initialPositions, Set.of(), groundY) != null) {
            return;
        }
        occupyBlocks(world, initialPositions);

        double speedPerStep = (config.fallSpeedBlocksPerSecond() * 3.0) / (20.0 * ASTEROID_SUB_STEPS);
        BukkitTask task = new BukkitRunnable() {
            private double currentY = centerY;
            private Set<BlockPoint> currentPositions = new HashSet<>(initialPositions);

            @Override
            public void run() {
                session.safeRun("moonBigAsteroidTick", () -> {
                    if (!session.isRunning() || session.getActiveMatchEvent() != BedwarsMatchEventType.MOON_BIG) {
                        clearCurrentAsteroid();
                        cancel();
                        return;
                    }
                    for (int step = 0; step < ASTEROID_SUB_STEPS; step++) {
                        double nextY = currentY - speedPerStep;
                        if (nextY <= world.getMinHeight()) {
                            session.handleMoonBigAsteroidImpact(
                                    new Location(world, centerX + 0.5, currentY, centerZ + 0.5),
                                    radius,
                                    config);
                            clearCurrentAsteroid();
                            cancel();
                            return;
                        }
                        Set<BlockPoint> nextPositions = resolveAsteroidBlocks(centerX, nextY, centerZ, offsets);
                        BlockPoint collisionPoint = findCollisionPoint(world, nextPositions, currentPositions, groundY);
                        if (collisionPoint != null) {
                            session.handleMoonBigAsteroidImpact(resolveImpactLocation(world, collisionPoint),
                                    radius,
                                    config);
                            clearCurrentAsteroid();
                            cancel();
                            return;
                        }
                        transitionBlocks(world, currentPositions, nextPositions);
                        currentPositions = nextPositions;
                        currentY = nextY;
                    }
                    session.spawnMoonBigAsteroidParticles(world, new Location(world, centerX + 0.5, currentY, centerZ + 0.5));
                });
            }

            private void clearCurrentAsteroid() {
                releaseBlocks(world, currentPositions);
                currentPositions = Set.of();
            }
        }.runTaskTimer(session.plugin, 0L, 1L);
        sessionTasks.add(task);
    }

    private List<BlockPoint> buildSphereOffsets(int radius) {
        List<BlockPoint> offsets = new ArrayList<>();
        int r = Math.max(1, radius);
        int r2 = r * r;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) {
                        continue;
                    }
                    offsets.add(new BlockPoint(dx, dy, dz));
                }
            }
        }
        return offsets;
    }

    private Set<BlockPoint> resolveAsteroidBlocks(int centerX,
                                                  double centerY,
                                                  int centerZ,
                                                  List<BlockPoint> offsets) {
        Set<BlockPoint> points = new HashSet<>();
        int baseY = (int) Math.floor(centerY);
        for (BlockPoint offset : offsets) {
            points.add(new BlockPoint(centerX + offset.x(), baseY + offset.y(), centerZ + offset.z()));
        }
        return points;
    }

    private BlockPoint findCollisionPoint(World world,
                                          Set<BlockPoint> nextPositions,
                                          Set<BlockPoint> currentPositions,
                                          int groundY) {
        if (world == null || nextPositions == null || nextPositions.isEmpty()) {
            return null;
        }
        for (BlockPoint point : nextPositions) {
            if (point == null) {
                continue;
            }
            if (point.y() <= groundY || point.y() < world.getMinHeight() || point.y() >= world.getMaxHeight()) {
                return point;
            }
            if (currentPositions.contains(point) || occupiedBlocks.containsKey(point)) {
                continue;
            }
            Block block = world.getBlockAt(point.x(), point.y(), point.z());
            if (!block.isPassable()) {
                return point;
            }
        }
        return null;
    }

    private Location resolveImpactLocation(World world, BlockPoint point) {
        if (world == null || point == null) {
            return null;
        }
        return new Location(world, point.x() + 0.5, point.y() + 0.5, point.z() + 0.5);
    }

    private void transitionBlocks(World world,
                                  Set<BlockPoint> currentPositions,
                                  Set<BlockPoint> nextPositions) {
        if (world == null) {
            return;
        }
        Set<BlockPoint> toRelease = new HashSet<>(currentPositions);
        toRelease.removeAll(nextPositions);
        Set<BlockPoint> toAcquire = new HashSet<>(nextPositions);
        toAcquire.removeAll(currentPositions);
        releaseBlocks(world, toRelease);
        occupyBlocks(world, toAcquire);
    }

    private void occupyBlocks(World world, Set<BlockPoint> positions) {
        if (world == null || positions == null || positions.isEmpty()) {
            return;
        }
        for (BlockPoint point : positions) {
            if (point == null) {
                continue;
            }
            OccupiedAsteroidBlock occupied = occupiedBlocks.get(point);
            if (occupied != null) {
                occupied.references++;
                continue;
            }
            Block block = world.getBlockAt(point.x(), point.y(), point.z());
            BlockState originalState = block.getState();
            block.setType(ASTEROID_MATERIAL, false);
            occupiedBlocks.put(point, new OccupiedAsteroidBlock(originalState));
        }
    }

    private void releaseBlocks(World world, Set<BlockPoint> positions) {
        if (world == null || positions == null || positions.isEmpty()) {
            return;
        }
        for (BlockPoint point : positions) {
            if (point == null) {
                continue;
            }
            OccupiedAsteroidBlock occupied = occupiedBlocks.get(point);
            if (occupied == null) {
                continue;
            }
            occupied.references--;
            if (occupied.references > 0) {
                continue;
            }
            restoreOccupiedBlock(point, occupied);
            occupiedBlocks.remove(point);
        }
    }

    private void restoreOccupiedBlock(BlockPoint point, OccupiedAsteroidBlock occupied) {
        if (point == null || occupied == null || occupied.originalState == null) {
            return;
        }
        occupied.originalState.update(true, false);
    }

    private static final class OccupiedAsteroidBlock {
        private final BlockState originalState;
        private int references = 1;

        private OccupiedAsteroidBlock(BlockState originalState) {
            this.originalState = originalState;
        }
    }
}
