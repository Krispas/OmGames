package krispasi.omGames.hallsofcarnage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

final class HallsSessionSculkRuntime {
    private static final int ROOM_HEIGHT = 5;
    private static final double SCULK_GAIN_PER_SECOND = 1.5;

    private final JavaPlugin plugin;
    private final World world;
    private final HallsConfig.BlockPoint origin;
    private final Set<UUID> participants;
    private final BlockSetter blockSetter;
    private final Set<HallsExplorationGenerator.Cell> patchCells = new HashSet<>();
    private final List<Patch> patches = new java.util.ArrayList<>();
    private final Map<UUID, Double> playerSculk = new HashMap<>();
    private final Predicate<UUID> canGainSculk;
    private final double gainMultiplier;
    private BukkitTask tickTask;
    private PatchPlacement pendingPlacement;

    HallsSessionSculkRuntime(JavaPlugin plugin,
                             World world,
                             HallsConfig.BlockPoint origin,
                             Set<UUID> participants,
                             BlockSetter blockSetter,
                             Predicate<UUID> canGainSculk,
                             double gainMultiplier) {
        this.plugin = plugin;
        this.world = world;
        this.origin = origin;
        this.participants = participants;
        this.blockSetter = blockSetter;
        this.canGainSculk = canGainSculk == null ? (playerId -> true) : canGainSculk;
        this.gainMultiplier = Math.max(0.0, gainMultiplier);
    }

    Set<HallsExplorationGenerator.Cell> placePatches(HallsExplorationGenerator.Plan plan,
                                                     HallsScenario.FloorDefinition floor,
                                                     Random random,
                                                     Set<HallsExplorationGenerator.Cell> blockedCells) {
        PatchPlacement placement = beginPatches(plan, floor, random, blockedCells);
        while (!placement.tick()) {
            // Direct development rebuilds still finish synchronously.
        }
        return Set.copyOf(patchCells);
    }

    PatchPlacement beginPatches(HallsExplorationGenerator.Plan plan,
                                HallsScenario.FloorDefinition floor,
                                Random random,
                                Set<HallsExplorationGenerator.Cell> blockedCells) {
        clearFloor();
        pendingPlacement = new PatchPlacement(plan, floor, random, blockedCells);
        return pendingPlacement;
    }

    final class PatchPlacement {
        private final Set<HallsExplorationGenerator.Cell> validCells;
        private final List<HallsExplorationGenerator.Cell> candidates;
        private final Random random;
        private final int target;
        private int placed;
        private boolean complete;
        private HallsExplorationGenerator.Cell center;
        private int radius;
        private int dx;
        private int dz;
        private double xStretch;
        private double zStretch;

        private PatchPlacement(HallsExplorationGenerator.Plan plan,
                               HallsScenario.FloorDefinition floor,
                               Random random,
                               Set<HallsExplorationGenerator.Cell> blockedCells) {
            this.random = random;
            validCells = plan == null ? new HashSet<>() : new HashSet<>(plan.walkableCells());
            if (blockedCells != null) {
                validCells.removeAll(blockedCells);
            }
            candidates = validCells.stream()
                    .filter(cell -> Math.abs(cell.x() - origin.x()) + Math.abs(cell.z() - origin.z()) > 12)
                    .toList();
            target = floor == null || candidates.isEmpty() ? 0 : Math.max(0, floor.sculkPatches());
        }

        boolean tick() {
            if (complete || pendingPlacement != this) {
                return true;
            }
            long deadline = System.nanoTime() + 4_000_000L;
            for (int work = 0; work < 128 && placed < target; work++) {
                if (center == null) {
                    center = candidates.get(random.nextInt(candidates.size()));
                    radius = 3 + random.nextInt(5);
                    xStretch = 0.85 + random.nextDouble() * 0.45;
                    zStretch = 0.85 + random.nextDouble() * 0.45;
                    patches.add(new Patch(center.x() + 0.5, center.z() + 0.5, radius, xStretch, zStretch));
                    dx = -radius;
                    dz = -radius;
                }
                carvePatchCell(center, radius, dx, dz, xStretch, zStretch, validCells, random);
                if (++dz > radius) {
                    dz = -radius;
                    if (++dx > radius) {
                        center = null;
                        placed++;
                    }
                }
                if (System.nanoTime() >= deadline) {
                    break;
                }
            }
            if (placed >= target) {
                complete = true;
                pendingPlacement = null;
                startTicking();
            }
            return complete;
        }

        double progress() {
            int width = radius * 2 + 1;
            double partial = center == null ? 0.0 : ((dx + radius) * width + dz + radius) / (double) (width * width);
            return target == 0 ? 1.0 : (placed + partial) / target;
        }
    }

    void clearFloor() {
        pendingPlacement = null;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        clearPatchesOnly();
    }

    void clearAll() {
        clearFloor();
        playerSculk.clear();
    }

    int maxSculkPercent() {
        return (int) Math.round(playerSculk.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0));
    }

    int maxSculkPercent(Predicate<UUID> playerFilter) {
        Predicate<UUID> filter = playerFilter == null ? ignored -> true : playerFilter;
        return (int) Math.round(playerSculk.entrySet().stream()
                .filter(entry -> filter.test(entry.getKey()))
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(0.0));
    }

    int sculkPercent(Player player) {
        if (player == null) {
            return 0;
        }
        return sculkPercent(player.getUniqueId());
    }

    int sculkPercent(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return (int) Math.round(playerSculk.getOrDefault(playerId, 0.0));
    }

    void setSculk(UUID playerId, double value) {
        if (playerId == null) {
            return;
        }
        playerSculk.put(playerId, Math.max(0.0, Math.min(100.0, value)));
    }

    boolean reduce(UUID playerId, double amount) {
        if (playerId == null || amount <= 0.0) {
            return false;
        }
        double current = playerSculk.getOrDefault(playerId, 0.0);
        if (current <= 0.0) {
            return false;
        }
        playerSculk.put(playerId, Math.max(0.0, current - amount));
        return true;
    }

    boolean blocksEating(Player player) {
        return false;
    }

    private void carvePatchCell(HallsExplorationGenerator.Cell center,
                                int radius, int dx, int dz,
                                double xStretch, double zStretch,
                                Set<HallsExplorationGenerator.Cell> validCells,
                                Random random) {
        double shaped = (dx * dx) / xStretch + (dz * dz) / zStretch;
        if (shaped > radius * radius || random.nextDouble() > 0.86) {
            return;
        }
        int x = center.x() + dx;
        int z = center.z() + dz;
        HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(x, z);
        if (!validCells.contains(cell)) {
            return;
        }
        patchCells.add(cell);
        if (random.nextDouble() < 0.80) {
            int floorY = sculkFloorY(x, z);
            blockSetter.setBlock(x, floorY, z, Material.SCULK, null);
        }
        maybePlaceVein(x, origin.y(), z, BlockFace.DOWN, random);
        for (int y = origin.y(); y <= origin.y() + ROOM_HEIGHT; y++) {
            maybePlaceVein(x, y, z, BlockFace.EAST, random);
            maybePlaceVein(x, y, z, BlockFace.WEST, random);
            maybePlaceVein(x, y, z, BlockFace.SOUTH, random);
            maybePlaceVein(x, y, z, BlockFace.NORTH, random);
        }
        maybePlaceVein(x, origin.y() + ROOM_HEIGHT - 1, z, BlockFace.UP, random);
    }

    private void maybePlaceVein(int x, int y, int z, BlockFace face, Random random) {
        if (random.nextDouble() > 0.35 || !world.getBlockAt(x, y, z).getType().isAir()) {
            return;
        }
        Material support = world.getBlockAt(x + face.getModX(), y + face.getModY(), z + face.getModZ()).getType();
        if (!support.isSolid() || support == Material.SCULK_VEIN) {
            return;
        }
        blockSetter.setBlock(x, y, z, Material.SCULK_VEIN, face);
    }

    private int sculkFloorY(int x, int z) {
        Material topFloor = world.getBlockAt(x, origin.y() - 1, z).getType();
        if (topFloor == Material.WATER || topFloor == Material.LAVA) {
            return origin.y() - 3;
        }
        return origin.y() - 1;
    }

    private void clearPatchesOnly() {
        patchCells.clear();
        patches.clear();
    }

    private void startTicking() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPlayers, 20L, 20L);
    }

    private void tickPlayers() {
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.getWorld().equals(world)) {
                continue;
            }
            if (!canGainSculk.test(playerId)) {
                continue;
            }
            boolean inSculk = isInSculk(player.getLocation());
            double current = playerSculk.getOrDefault(playerId, 0.0);
            double next = inSculk ? Math.min(100.0, current + SCULK_GAIN_PER_SECOND * gainMultiplier) : current;
            playerSculk.put(playerId, next);
            playSculkFeedback(player, inSculk);
        }
    }

    private boolean isInSculk(Location location) {
        if (location == null || !world.equals(location.getWorld())) {
            return false;
        }
        double x = location.getX();
        double z = location.getZ();
        for (Patch patch : patches) {
            double dx = x - patch.centerX();
            double dz = z - patch.centerZ();
            double shaped = (dx * dx) / patch.xStretch() + (dz * dz) / patch.zStretch();
            if (shaped <= patch.radius() * patch.radius()) {
                return true;
            }
        }
        return false;
    }

    private void playSculkFeedback(Player player, boolean inSculk) {
        if (inSculk) {
            player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.45f, 0.7f);
            world.spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0.0, 0.15, 0.0), 3, 0.35, 0.1, 0.35, 0.0);
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
    }

    @FunctionalInterface
    interface BlockSetter {
        void setBlock(int x, int y, int z, Material material, BlockFace face);
    }

    private record Patch(double centerX, double centerZ, int radius, double xStretch, double zStretch) {
    }
}
