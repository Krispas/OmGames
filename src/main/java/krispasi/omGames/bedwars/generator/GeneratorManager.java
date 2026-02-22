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
import krispasi.omGames.bedwars.game.GameSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Schedules generator drops and manages holograms for a match.
 * <p>Uses {@link krispasi.omGames.bedwars.generator.GeneratorSettings} to control
 * intervals, amounts, and caps for base, diamond, and emerald generators.</p>
 * <p>Handles forge splitting, visual drops, and cleanup of stale items.</p>
 * @see krispasi.omGames.bedwars.generator.GeneratorSettings
 */
public class GeneratorManager {
    private static final long CLEANUP_INTERVAL_TICKS = 20L * 20L;
    private static final long HOLOGRAM_UPDATE_INTERVAL_TICKS = 20L;

    private static final String HOLOGRAM_TAG = "bw_generator_holo";
    private static final String VISUAL_DROP_TAG = "bw_generator_visual";
    private static final double HOLOGRAM_TITLE_OFFSET = 3.8;
    private static final double HOLOGRAM_TIER_OFFSET = 3.5;
    private static final double HOLOGRAM_TIMER_OFFSET = 3.2;
    private static final double FORGE_SPLIT_RADIUS = 1.5;

    private final JavaPlugin plugin;
    private final Arena arena;
    private final GameSession session;
    private final GeneratorSettings settings;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private final Map<UUID, Long> trackedDrops = new HashMap<>();
    private final Map<TeamColor, Integer> baseForgeTiers = new EnumMap<>(TeamColor.class);
    private final Map<String, GeneratorHologram> holograms = new HashMap<>();
    private final Map<String, GeneratorTimer> generatorTimers = new HashMap<>();
    private int diamondTier = 1;
    private int emeraldTier = 1;
    private BukkitTask cleanupTask;
    private BukkitTask hologramTask;

    public GeneratorManager(JavaPlugin plugin, Arena arena, GameSession session) {
        this.plugin = plugin;
        this.arena = arena;
        this.session = session;
        this.settings = arena.getGeneratorSettings();
    }

    public void start() {
        stopTasks(false);
        generatorTimers.clear();
        for (GeneratorInfo generator : arena.getGenerators()) {
            if (generator.type() == GeneratorType.BASE) {
                int tier = generator.team() != null ? baseForgeTiers.getOrDefault(generator.team(), 0) : 0;
                ForgeSettings forgeSettings = getForgeSettings(tier);
                scheduleBaseDrop(generator, Material.IRON_INGOT,
                        forgeSettings.ironInterval(), forgeSettings.ironCap(), forgeSettings.ironAmount());
                scheduleBaseDrop(generator, Material.GOLD_INGOT,
                        forgeSettings.goldInterval(), forgeSettings.goldCap(), forgeSettings.goldAmount());
                if (forgeSettings.emeraldInterval() > 0 && forgeSettings.emeraldAmount() > 0
                        && forgeSettings.emeraldCap() > 0) {
                    scheduleBaseDrop(generator, Material.EMERALD,
                            forgeSettings.emeraldInterval(), forgeSettings.emeraldCap(), forgeSettings.emeraldAmount());
                }
            } else if (generator.type() == GeneratorType.DIAMOND) {
                scheduleDrop(generator, Material.DIAMOND, getDiamondInterval(), settings.getDiamondCap(), 1);
            } else if (generator.type() == GeneratorType.EMERALD) {
                scheduleDrop(generator, Material.EMERALD, getEmeraldInterval(), settings.getEmeraldCap(), 1);
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
        removeVisualDrops();
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
        return settings.diamondInterval(diamondTier);
    }

    private long getEmeraldInterval() {
        return settings.emeraldInterval(emeraldTier);
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

    private void scheduleBaseDrop(GeneratorInfo generator, Material material, long intervalTicks, int cap, int amount) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            World world = arena.getWorld();
            if (world == null) {
                return;
            }
            Location dropLocation = generator.location().toLocation(world).add(0, 0.1, 0);
            List<Player> recipients = findForgeRecipients(generator, dropLocation);
            if (!recipients.isEmpty()) {
                giveToRecipients(recipients, material, amount);
                dropVisualItem(world, dropLocation, new ItemStack(material, Math.max(1, amount)));
                return;
            }
            if (countNearbyItems(dropLocation, material, 1.5) >= cap) {
                return;
            }
            dropTrackedItem(world, dropLocation, new ItemStack(material, Math.max(1, amount)));
        }, 0L, intervalTicks);
        tasks.add(task);
    }

    private void scheduleDrop(GeneratorInfo generator, Material material, long intervalTicks, int cap, int amount) {
        registerTimer(generator, intervalTicks);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            World world = arena.getWorld();
            if (world == null) {
                return;
            }
            updateNextDrop(generator.key());
            Location dropLocation = generator.location().toLocation(world).add(0, 0.1, 0);
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

    private List<Player> findForgeRecipients(GeneratorInfo generator, Location dropLocation) {
        if (generator.team() == null || !session.isRunning()) {
            return List.of();
        }
        double radius = getForgeSplitRadius();
        double radiusSquared = radius * radius;
        World world = dropLocation.getWorld();
        if (world == null) {
            return List.of();
        }
        List<Player> recipients = new ArrayList<>();
        for (Player player : world.getNearbyPlayers(dropLocation, radius, radius, radius)) {
            UUID playerId = player.getUniqueId();
            if (!session.isParticipant(playerId) || session.isEliminated(playerId)) {
                continue;
            }
            if (session.getTeam(playerId) != generator.team()) {
                continue;
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(dropLocation) > radiusSquared) {
                continue;
            }
            recipients.add(player);
        }
        return recipients;
    }

    private double getForgeSplitRadius() {
        return FORGE_SPLIT_RADIUS;
    }

    private void giveToRecipients(List<Player> recipients, Material material, int amount) {
        int spawnAmount = Math.max(1, amount);
        for (Player player : recipients) {
            ItemStack stack = new ItemStack(material, spawnAmount);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            if (!leftover.isEmpty()) {
                for (ItemStack extra : leftover.values()) {
                    dropTrackedItem(player.getWorld(), player.getLocation(), extra);
                }
            }
        }
    }

    private void dropTrackedItem(World world, Location location, ItemStack stack) {
        Item item = world.dropItem(location, stack);
        item.setVelocity(new Vector(0, 0, 0));
        trackedDrops.put(item.getUniqueId(), System.currentTimeMillis());
    }

    private void dropVisualItem(World world, Location location, ItemStack stack) {
        Item item = world.dropItem(location, stack);
        item.setVelocity(new Vector(0, 0, 0));
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setInvulnerable(true);
        item.addScoreboardTag(VISUAL_DROP_TAG);
        plugin.getServer().getScheduler().runTaskLater(plugin, item::remove, 20L);
    }

    private void registerTimer(GeneratorInfo generator, long intervalTicks) {
        if (generator.type() != GeneratorType.DIAMOND && generator.type() != GeneratorType.EMERALD) {
            return;
        }
        long intervalMillis = intervalTicks * 50L;
        generatorTimers.put(generator.key(), new GeneratorTimer(intervalMillis, System.currentTimeMillis() + intervalMillis));
    }

    private void updateNextDrop(String key) {
        GeneratorTimer timer = generatorTimers.get(key);
        if (timer != null) {
            timer.setNextDropAt(System.currentTimeMillis() + timer.intervalMillis());
        }
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
            generatorTimers.clear();
        }
    }

    private ForgeSettings getForgeSettings(int tier) {
        return new ForgeSettings(
                settings.baseIronInterval(tier),
                settings.baseGoldInterval(tier),
                settings.baseIronAmount(tier),
                settings.baseGoldAmount(tier),
                settings.baseIronCap(tier),
                settings.baseGoldCap(tier),
                settings.baseEmeraldInterval(tier),
                settings.baseEmeraldAmount(tier),
                settings.baseEmeraldCap(tier)
        );
    }

    private record ForgeSettings(long ironInterval,
                                 long goldInterval,
                                 int ironAmount,
                                 int goldAmount,
                                 int ironCap,
                                 int goldCap,
                                 long emeraldInterval,
                                 int emeraldAmount,
                                 int emeraldCap) {
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
        long cutoff = System.currentTimeMillis() - settings.getResourceDespawnMillis();
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
        updateHologramTimers();
        startHologramTask();
    }

    private void spawnHologram(GeneratorInfo generator, World world) {
        Location base = generator.location().toLocation(world);
        Location titleLocation = base.clone().add(0, HOLOGRAM_TITLE_OFFSET, 0);
        Location tierLocation = base.clone().add(0, HOLOGRAM_TIER_OFFSET, 0);
        Location timerLocation = base.clone().add(0, HOLOGRAM_TIMER_OFFSET, 0);
        Component title = generator.type() == GeneratorType.DIAMOND
                ? Component.text("Diamond Generator", NamedTextColor.AQUA)
                : Component.text("Emerald Generator", NamedTextColor.GREEN);
        Component tier = Component.text("Tier " + toRoman(getTier(generator.type())), NamedTextColor.GRAY);
        Component timer = buildTimerComponent(generator.key());
        ArmorStand titleStand = world.spawn(titleLocation, ArmorStand.class, stand -> configureHologram(stand, title));
        ArmorStand tierStand = world.spawn(tierLocation, ArmorStand.class, stand -> configureHologram(stand, tier));
        ArmorStand timerStand = world.spawn(timerLocation, ArmorStand.class, stand -> configureHologram(stand, timer));
        holograms.put(generator.key(), new GeneratorHologram(generator.type(),
                titleStand.getUniqueId(),
                tierStand.getUniqueId(),
                timerStand.getUniqueId()));
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
                removeHologramEntities(hologram);
                iterator.remove();
                continue;
            }
            int tier = getTier(hologram.type());
            stand.customName(Component.text("Tier " + toRoman(tier), NamedTextColor.GRAY));
        }
    }

    private void updateHologramTimers() {
        if (holograms.isEmpty()) {
            return;
        }
        var iterator = holograms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, GeneratorHologram> entry = iterator.next();
            GeneratorHologram hologram = entry.getValue();
            Entity entity = Bukkit.getEntity(hologram.timerId());
            if (!(entity instanceof ArmorStand stand)) {
                removeHologramEntities(hologram);
                iterator.remove();
                continue;
            }
            stand.customName(buildTimerComponent(entry.getKey()));
        }
    }

    private void startHologramTask() {
        if (holograms.isEmpty() || hologramTask != null) {
            return;
        }
        hologramTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                this::updateHologramTimers,
                0L,
                HOLOGRAM_UPDATE_INTERVAL_TICKS);
    }

    private Component buildTimerComponent(String generatorKey) {
        int seconds = getSecondsUntilNextSpawn(generatorKey);
        return Component.text("Spawns in " + seconds + "s", NamedTextColor.GRAY);
    }

    private int getSecondsUntilNextSpawn(String generatorKey) {
        GeneratorTimer timer = generatorTimers.get(generatorKey);
        if (timer == null) {
            return 0;
        }
        long remaining = timer.nextDropAt() - System.currentTimeMillis();
        if (remaining < 0) {
            remaining = 0;
        }
        return (int) ((remaining + 999L) / 1000L);
    }

    private void removeHolograms() {
        for (GeneratorHologram hologram : holograms.values()) {
            removeHologramEntities(hologram);
        }
        holograms.clear();
        stopHologramTask();
    }

    private void removeHologramEntities(GeneratorHologram hologram) {
        removeEntity(hologram.titleId());
        removeEntity(hologram.tierId());
        removeEntity(hologram.timerId());
    }

    private void removeWorldHolograms(World world) {
        for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
            if (entity.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                entity.remove();
            }
        }
    }

    private void removeVisualDrops() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getEntitiesByClass(Item.class)) {
            if (entity.getScoreboardTags().contains(VISUAL_DROP_TAG)) {
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

    private void stopHologramTask() {
        if (hologramTask != null) {
            hologramTask.cancel();
            hologramTask = null;
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

    private record GeneratorHologram(GeneratorType type, UUID titleId, UUID tierId, UUID timerId) {
    }

    private static final class GeneratorTimer {
        private final long intervalMillis;
        private long nextDropAt;

        private GeneratorTimer(long intervalMillis, long nextDropAt) {
            this.intervalMillis = intervalMillis;
            this.nextDropAt = nextDropAt;
        }

        private long intervalMillis() {
            return intervalMillis;
        }

        private long nextDropAt() {
            return nextDropAt;
        }

        private void setNextDropAt(long nextDropAt) {
            this.nextDropAt = nextDropAt;
        }
    }
}
