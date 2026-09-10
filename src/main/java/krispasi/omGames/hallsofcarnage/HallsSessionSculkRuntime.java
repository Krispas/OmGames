package krispasi.omGames.hallsofcarnage;

import java.util.HashMap;
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

final class HallsSessionSculkRuntime {
    private static final int ROOM_HEIGHT = 5;
    private static final double SCULK_GAIN_PER_SECOND = 0.75;

    private final JavaPlugin plugin;
    private final World world;
    private final HallsConfig.BlockPoint origin;
    private final Set<UUID> participants;
    private final BlockSetter blockSetter;
    private final Set<HallsExplorationGenerator.Cell> patchCells = new HashSet<>();
    private final List<Patch> patches = new java.util.ArrayList<>();
    private final Map<UUID, Double> playerSculk = new HashMap<>();
    private BukkitTask tickTask;

    HallsSessionSculkRuntime(JavaPlugin plugin,
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

    Set<HallsExplorationGenerator.Cell> placePatches(HallsExplorationGenerator.Plan plan,
                                                     HallsScenario.FloorDefinition floor,
                                                     Random random) {
        clearFloor();
        if (plan == null || plan.walkableCells().isEmpty() || floor == null || floor.sculkPatches() <= 0) {
            startTicking();
            return Set.of();
        }
        java.util.List<HallsExplorationGenerator.Cell> cells = plan.walkableCells().stream()
                .filter(cell -> Math.abs(cell.x() - origin.x()) + Math.abs(cell.z() - origin.z()) > 12)
                .toList();
        if (cells.isEmpty()) {
            startTicking();
            return Set.of();
        }
        Set<HallsExplorationGenerator.Cell> validCells = Set.copyOf(plan.walkableCells());
        for (int i = 0; i < floor.sculkPatches(); i++) {
            HallsExplorationGenerator.Cell center = cells.get(random.nextInt(cells.size()));
            int radius = 3 + random.nextInt(5);
            carvePatch(center, radius, validCells, random);
        }
        startTicking();
        return Set.copyOf(patchCells);
    }

    void clearFloor() {
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

    int sculkPercent(Player player) {
        if (player == null) {
            return 0;
        }
        return (int) Math.round(playerSculk.getOrDefault(player.getUniqueId(), 0.0));
    }

    boolean blocksEating(Player player) {
        return sculkPercent(player) >= 90;
    }

    private void carvePatch(HallsExplorationGenerator.Cell center,
                            int radius,
                            Set<HallsExplorationGenerator.Cell> validCells,
                            Random random) {
        double radiusSquared = radius * radius;
        double xStretch = 0.85 + random.nextDouble() * 0.45;
        double zStretch = 0.85 + random.nextDouble() * 0.45;
        patches.add(new Patch(center.x() + 0.5, center.z() + 0.5, radius, xStretch, zStretch));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double shaped = (dx * dx) / xStretch + (dz * dz) / zStretch;
                if (shaped > radiusSquared || random.nextDouble() > 0.86) {
                    continue;
                }
                int x = center.x() + dx;
                int z = center.z() + dz;
                HallsExplorationGenerator.Cell cell = new HallsExplorationGenerator.Cell(x, z);
                if (!validCells.contains(cell)) {
                    continue;
                }
                patchCells.add(cell);
                if (random.nextDouble() < 0.80) {
                    blockSetter.setBlock(x, origin.y() - 1, z, Material.SCULK, null);
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
        }
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
            boolean inSculk = isInSculk(player.getLocation());
            double current = playerSculk.getOrDefault(playerId, 0.0);
            double next = inSculk ? Math.min(100.0, current + SCULK_GAIN_PER_SECOND) : current;
            playerSculk.put(playerId, next);
            applySculkEffects(player, next, inSculk);
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

    private void applySculkEffects(Player player, double sculk, boolean inSculk) {
        if (inSculk) {
            player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.45f, 0.7f);
            world.spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0.0, 0.15, 0.0), 3, 0.35, 0.1, 0.35, 0.0);
        }
        if (sculk > 35.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 45, 0, true, false, true));
        }
        if (sculk > 80.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 45, 0, true, false, true));
        }
        if (sculk > 90.0) {
            player.setFoodLevel(Math.min(player.getFoodLevel(), 16));
            player.setSaturation(0.0f);
        } else {
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
        if (sculk >= 100.0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 45, 0, true, false, true));
            player.sendActionBar(Component.text("The sculk has taken hold.", NamedTextColor.DARK_AQUA));
        }
    }

    @FunctionalInterface
    interface BlockSetter {
        void setBlock(int x, int y, int z, Material material, BlockFace face);
    }

    private record Patch(double centerX, double centerZ, int radius, double xStretch, double zStretch) {
    }
}
