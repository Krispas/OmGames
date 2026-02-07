package krispasi.omGames.bedwars.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.BlockPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class GeneratorManager {
    private static final long BASE_IRON_INTERVAL = 20L;
    private static final long BASE_GOLD_INTERVAL = 160L;
    private static final long DIAMOND_TIER_ONE_INTERVAL = 30L * 20L;
    private static final long DIAMOND_TIER_TWO_INTERVAL = 23L * 20L;
    private static final long DIAMOND_TIER_THREE_INTERVAL = 12L * 20L;
    private static final long EMERALD_TIER_ONE_INTERVAL = 50L * 20L;
    private static final long EMERALD_TIER_TWO_INTERVAL = 30L * 20L;
    private static final long EMERALD_TIER_THREE_INTERVAL = 12L * 20L;
    private static final long RESOURCE_DESPAWN_MILLIS = 5L * 60L * 1000L;
    private static final long CLEANUP_INTERVAL_TICKS = 20L * 20L;

    private static final int BASE_IRON_CAP = 64;
    private static final int BASE_GOLD_CAP = 32;
    private static final int DIAMOND_CAP = 8;
    private static final int EMERALD_CAP = 6;

    private final JavaPlugin plugin;
    private final Arena arena;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<UUID, Long> trackedDrops = new HashMap<>();
    private int diamondTier = 1;
    private int emeraldTier = 1;
    private BukkitTask cleanupTask;

    public GeneratorManager(JavaPlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
    }

    public void start() {
        stop();
        for (GeneratorInfo generator : arena.getGenerators()) {
            if (generator.type() == GeneratorType.BASE) {
                scheduleDrop(generator.location(), Material.IRON_INGOT, BASE_IRON_INTERVAL, BASE_IRON_CAP);
                scheduleDrop(generator.location(), Material.GOLD_INGOT, BASE_GOLD_INTERVAL, BASE_GOLD_CAP);
            } else if (generator.type() == GeneratorType.DIAMOND) {
                scheduleDrop(generator.location(), Material.DIAMOND, getDiamondInterval(), DIAMOND_CAP);
            } else if (generator.type() == GeneratorType.EMERALD) {
                scheduleDrop(generator.location(), Material.EMERALD, getEmeraldInterval(), EMERALD_CAP);
            }
        }
        startCleanup();
    }

    public void refresh() {
        start();
    }

    public void stop() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        trackedDrops.clear();
    }

    public void setDiamondTier(int tier) {
        diamondTier = Math.max(1, Math.min(3, tier));
    }

    public void setEmeraldTier(int tier) {
        emeraldTier = Math.max(1, Math.min(3, tier));
    }

    private long getDiamondInterval() {
        return switch (diamondTier) {
            case 2 -> DIAMOND_TIER_TWO_INTERVAL;
            case 3 -> DIAMOND_TIER_THREE_INTERVAL;
            default -> DIAMOND_TIER_ONE_INTERVAL;
        };
    }

    private long getEmeraldInterval() {
        return switch (emeraldTier) {
            case 2 -> EMERALD_TIER_TWO_INTERVAL;
            case 3 -> EMERALD_TIER_THREE_INTERVAL;
            default -> EMERALD_TIER_ONE_INTERVAL;
        };
    }

    private void scheduleDrop(BlockPoint point, Material material, long intervalTicks, int cap) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            World world = arena.getWorld();
            if (world == null) {
                return;
            }
            Location dropLocation = point.toLocation(world).add(0, 0.1, 0);
            if (countNearbyItems(dropLocation, material, 1.5) >= cap) {
                return;
            }
            Item item = world.dropItem(dropLocation, new ItemStack(material));
            item.setVelocity(new Vector(0, 0, 0));
            trackedDrops.put(item.getUniqueId(), System.currentTimeMillis());
        }, 0L, intervalTicks);
        tasks.add(task);
    }

    private void startCleanup() {
        cleanupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupExpiredDrops,
                CLEANUP_INTERVAL_TICKS,
                CLEANUP_INTERVAL_TICKS);
    }

    private void cleanupExpiredDrops() {
        if (trackedDrops.isEmpty()) {
            return;
        }
        long cutoff = System.currentTimeMillis() - RESOURCE_DESPAWN_MILLIS;
        var iterator = trackedDrops.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Item item)) {
                iterator.remove();
                continue;
            }
            if (entry.getValue() > cutoff) {
                continue;
            }
            item.remove();
            iterator.remove();
        }
    }

    private int countNearbyItems(Location location, Material material, double radius) {
        int amount = 0;
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof Item item && item.getItemStack().getType() == material) {
                amount += item.getItemStack().getAmount();
            }
        }
        return amount;
    }
}
