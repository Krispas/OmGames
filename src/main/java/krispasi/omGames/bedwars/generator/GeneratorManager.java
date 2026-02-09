package krispasi.omGames.bedwars.generator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class GeneratorManager {
    private static final long[] BASE_IRON_INTERVALS = {20L, 16L, 12L, 8L, 6L};
    private static final long[] BASE_GOLD_INTERVALS = {160L, 120L, 90L, 70L, 50L};
    private static final int[] BASE_IRON_AMOUNTS = {1, 1, 1, 1, 2};
    private static final int[] BASE_GOLD_AMOUNTS = {1, 1, 1, 1, 2};
    private static final long DIAMOND_TIER_ONE_INTERVAL = 30L * 20L;
    private static final long DIAMOND_TIER_TWO_INTERVAL = 23L * 20L;
    private static final long DIAMOND_TIER_THREE_INTERVAL = 12L * 20L;
    private static final long EMERALD_TIER_ONE_INTERVAL = 50L * 20L;
    private static final long EMERALD_TIER_TWO_INTERVAL = 30L * 20L;
    private static final long EMERALD_TIER_THREE_INTERVAL = 12L * 20L;
    private static final long RESOURCE_DESPAWN_MILLIS = 5L * 60L * 1000L;
    private static final long CLEANUP_INTERVAL_TICKS = 20L * 20L;

    private static final int BASE_IRON_CAP = 64;
    private static final int BASE_GOLD_CAP = 12;
    private static final int DIAMOND_CAP = 4;
    private static final int EMERALD_CAP = 2;
    private static final String HOLOGRAM_TAG = "bw_generator_holo";
    private static final double HOLOGRAM_TITLE_OFFSET = 1.8;
    private static final double HOLOGRAM_TIER_OFFSET = 1.5;

    private final JavaPlugin plugin;
    private final Arena arena;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<UUID, Long> trackedDrops = new HashMap<>();
    private final Map<TeamColor, Integer> baseForgeTiers = new EnumMap<>(TeamColor.class);
    private final Map<String, GeneratorHologram> holograms = new HashMap<>();
    private int diamondTier = 1;
    private int emeraldTier = 1;
    private BukkitTask cleanupTask;

    public GeneratorManager(JavaPlugin plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
    }

    public void start() {
        stopTasks(false);
        for (GeneratorInfo generator : arena.getGenerators()) {
            if (generator.type() == GeneratorType.BASE) {
                int tier = generator.team() != null ? baseForgeTiers.getOrDefault(generator.team(), 0) : 0;
                ForgeSettings settings = getForgeSettings(tier);
                scheduleDrop(generator.location(), Material.IRON_INGOT,
                        settings.ironInterval(), BASE_IRON_CAP, settings.ironAmount());
                scheduleDrop(generator.location(), Material.GOLD_INGOT,
                        settings.goldInterval(), BASE_GOLD_CAP, settings.goldAmount());
            } else if (generator.type() == GeneratorType.DIAMOND) {
                scheduleDrop(generator.location(), Material.DIAMOND, getDiamondInterval(), DIAMOND_CAP, 1);
            } else if (generator.type() == GeneratorType.EMERALD) {
                scheduleDrop(generator.location(), Material.EMERALD, getEmeraldInterval(), EMERALD_CAP, 1);
            }
        }
        ensureHolograms();
        startCleanup();
    }

    public void refresh() {
        start();
    }

    public void stop() {
        stopTasks(true);
        removeHolograms();
    }

    public void setDiamondTier(int tier) {
        diamondTier = Math.max(1, Math.min(3, tier));
        updateHologramTiers();
    }

    public void setEmeraldTier(int tier) {
        emeraldTier = Math.max(1, Math.min(3, tier));
        updateHologramTiers();
    }

    public void setBaseForgeTiers(Map<TeamColor, Integer> tiers) {
        baseForgeTiers.clear();
        baseForgeTiers.putAll(tiers);
    }

    public void setBaseForgeTier(TeamColor team, int tier) {
        if (team == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(4, tier));
        baseForgeTiers.put(team, clamped);
        refresh();
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

    private void scheduleDrop(BlockPoint point, Material material, long intervalTicks, int cap, int amount) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            World world = arena.getWorld();
            if (world == null) {
                return;
            }
            Location dropLocation = point.toLocation(world).add(0, 0.1, 0);
            if (countNearbyItems(dropLocation, material, 1.5) >= cap) {
                return;
            }
            int spawnAmount = Math.max(1, amount);
            Item item = world.dropItem(dropLocation, new ItemStack(material, spawnAmount));
            item.setVelocity(new Vector(0, 0, 0));
            trackedDrops.put(item.getUniqueId(), System.currentTimeMillis());
        }, 0L, intervalTicks);
        tasks.add(task);
    }

    private void stopTasks(boolean clearDrops) {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (clearDrops) {
            trackedDrops.clear();
        }
    }

    private ForgeSettings getForgeSettings(int tier) {
        int index = Math.max(0, Math.min(4, tier));
        return new ForgeSettings(
                BASE_IRON_INTERVALS[index],
                BASE_GOLD_INTERVALS[index],
                BASE_IRON_AMOUNTS[index],
                BASE_GOLD_AMOUNTS[index]
        );
    }

    private record ForgeSettings(long ironInterval, long goldInterval, int ironAmount, int goldAmount) {
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

    private void ensureHolograms() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        if (holograms.isEmpty()) {
            removeWorldHolograms(world);
            for (GeneratorInfo generator : arena.getGenerators()) {
                if (generator.type() != GeneratorType.DIAMOND && generator.type() != GeneratorType.EMERALD) {
                    continue;
                }
                spawnHologram(generator, world);
            }
        }
        updateHologramTiers();
    }

    private void spawnHologram(GeneratorInfo generator, World world) {
        Location base = generator.location().toLocation(world);
        Location titleLocation = base.clone().add(0, HOLOGRAM_TITLE_OFFSET, 0);
        Location tierLocation = base.clone().add(0, HOLOGRAM_TIER_OFFSET, 0);
        Component title = generator.type() == GeneratorType.DIAMOND
                ? Component.text("Diamond Generator", NamedTextColor.AQUA)
                : Component.text("Emerald Generator", NamedTextColor.GREEN);
        Component tier = Component.text("Tier " + toRoman(getTier(generator.type())), NamedTextColor.GRAY);
        ArmorStand titleStand = world.spawn(titleLocation, ArmorStand.class, stand -> configureHologram(stand, title));
        ArmorStand tierStand = world.spawn(tierLocation, ArmorStand.class, stand -> configureHologram(stand, tier));
        holograms.put(generator.key(), new GeneratorHologram(generator.type(), titleStand.getUniqueId(), tierStand.getUniqueId()));
    }

    private void configureHologram(ArmorStand stand, Component text) {
        stand.setMarker(true);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
        stand.setSilent(true);
        stand.setCanPickupItems(false);
        stand.customName(text);
        stand.setCustomNameVisible(true);
        stand.addScoreboardTag(HOLOGRAM_TAG);
    }

    private void updateHologramTiers() {
        if (holograms.isEmpty()) {
            return;
        }
        var iterator = holograms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, GeneratorHologram> entry = iterator.next();
            GeneratorHologram hologram = entry.getValue();
            Entity entity = Bukkit.getEntity(hologram.tierId());
            if (!(entity instanceof ArmorStand stand)) {
                iterator.remove();
                continue;
            }
            int tier = getTier(hologram.type());
            stand.customName(Component.text("Tier " + toRoman(tier), NamedTextColor.GRAY));
        }
    }

    private void removeHolograms() {
        for (GeneratorHologram hologram : holograms.values()) {
            removeEntity(hologram.titleId());
            removeEntity(hologram.tierId());
        }
        holograms.clear();
    }

    private void removeWorldHolograms(World world) {
        for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
            if (entity.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                entity.remove();
            }
        }
    }

    private void removeEntity(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) {
            entity.remove();
        }
    }

    private int getTier(GeneratorType type) {
        return type == GeneratorType.DIAMOND ? diamondTier : emeraldTier;
    }

    private String toRoman(int tier) {
        return switch (tier) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    private record GeneratorHologram(GeneratorType type, UUID titleId, UUID tierId) {
    }
}
