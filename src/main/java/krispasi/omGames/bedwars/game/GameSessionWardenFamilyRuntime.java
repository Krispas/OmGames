package krispasi.omGames.bedwars.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import krispasi.omGames.bedwars.generator.GeneratorInfo;
import krispasi.omGames.bedwars.generator.GeneratorType;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.upgrade.TeamUpgradeType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

final class GameSessionWardenFamilyRuntime {
    private static final String WARDEN_FAMILY_TAG = "bw_warden_family";
    private static final String WARDEN_GARY_TAG = "bw_warden_gary";
    private static final String WARDEN_WIFE_TAG = "bw_warden_wife";
    private static final String WARDEN_JR_TAG = "bw_warden_jr";
    private static final int PATROL_CHECK_TICKS = 10;
    private static final int TERRITORY_RADIUS = 30;
    private static final int DIG_TICKS = 20;
    private static final int EMERGE_TICKS = 24;

    private final GameSession session;
    private final List<BukkitTask> sessionTasks;
    private final Set<UUID> relocatingWardens = new HashSet<>();
    private final Map<BlockPoint, BlockState> temporaryMiddlePlatformBlocks = new HashMap<>();

    private UUID garyId;
    private UUID wifeId;
    private UUID jrId;
    private BukkitTask patrolTask;

    GameSessionWardenFamilyRuntime(GameSession session, List<BukkitTask> sessionTasks) {
        this.session = session;
        this.sessionTasks = sessionTasks;
    }

    void applySharedTier(int tier) {
        int clampedTier = Math.max(0, Math.min(3, tier));
        if (clampedTier <= 0) {
            clear();
            return;
        }
        Location spawn = resolveMiddleSpawn(true);
        if (spawn == null || spawn.getWorld() == null) {
            return;
        }
        ensureWarden(1, spawn, 500.0, "Gary the Warden");
        if (clampedTier >= 2) {
            ensureWarden(2, spawn, 200.0, "Gary's Wife");
        } else {
            removeByTier(2);
        }
        if (clampedTier >= 3) {
            ensureWarden(3, spawn, 100.0, "Gary Jr.");
        } else {
            removeByTier(3);
        }
        startPatrolTask();
    }

    void clear() {
        stopPatrolTask();
        removeByTier(3);
        removeByTier(2);
        removeByTier(1);
        relocatingWardens.clear();
        clearTemporaryMiddlePlatform();
    }

    boolean isWardenFamilyEntity(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(WARDEN_FAMILY_TAG);
    }

    boolean isDowngradeOnDeathEntity(Entity entity) {
        return entity != null && (entity.getScoreboardTags().contains(WARDEN_WIFE_TAG)
                || entity.getScoreboardTags().contains(WARDEN_JR_TAG));
    }

    void handleDeath(Entity entity) {
        if (!(entity instanceof Warden) || !isDowngradeOnDeathEntity(entity)) {
            return;
        }
        int current = session.getSharedUpgradeTier(TeamUpgradeType.WARDEN_FAMILY);
        if (current <= 1) {
            return;
        }
        session.setSharedUpgradeTier(TeamUpgradeType.WARDEN_FAMILY, current - 1);
    }

    void handleTarget(EntityTargetLivingEntityEvent event) {
        if (event == null || !(event.getEntity() instanceof Warden warden) || !isWardenFamilyEntity(warden)) {
            return;
        }
        if (!(event.getTarget() instanceof Player target)) {
            return;
        }
        if (!isInsideTerritory(target.getLocation())) {
            event.setCancelled(true);
            warden.setTarget(null);
            relocateWithDig(warden);
        }
    }

    void handleDamage(EntityDamageEvent event) {
        if (event == null || !(event.getEntity() instanceof Warden warden) || !isWardenFamilyEntity(warden)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID
                || (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && event.getFinalDamage() >= warden.getHealth())) {
            event.setCancelled(true);
            relocateWithDig(warden);
        }
    }

    String scoreboardTag() {
        return WARDEN_FAMILY_TAG;
    }

    private void startPatrolTask() {
        if (patrolTask != null) {
            return;
        }
        JavaPlugin plugin = session.getBedwarsManager().getPlugin();
        if (plugin == null) {
            return;
        }
        patrolTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!session.isRunning()) {
                    clear();
                    return;
                }
                patrolWardens();
            }
        }.runTaskTimer(plugin, 0L, PATROL_CHECK_TICKS);
        sessionTasks.add(patrolTask);
    }

    private void stopPatrolTask() {
        if (patrolTask != null) {
            patrolTask.cancel();
            patrolTask = null;
        }
    }

    private void patrolWardens() {
        int tier = session.getSharedUpgradeTier(TeamUpgradeType.WARDEN_FAMILY);
        if (tier <= 0) {
            clear();
            return;
        }
        Location spawn = resolveMiddleSpawn(true);
        if (spawn == null) {
            return;
        }
        ensureWarden(1, spawn, 500.0, "Gary the Warden");
        if (tier >= 2) {
            ensureWarden(2, spawn, 200.0, "Gary's Wife");
        }
        if (tier >= 3) {
            ensureWarden(3, spawn, 100.0, "Gary Jr.");
        }
        patrolOne(1);
        patrolOne(2);
        patrolOne(3);
    }

    private void patrolOne(int tier) {
        Warden warden = getByTier(tier);
        if (warden == null) {
            return;
        }
        if (relocatingWardens.contains(warden.getUniqueId())) {
            return;
        }
        if (!isInsideTerritory(warden.getLocation())) {
            warden.setTarget(null);
            relocateWithDig(warden);
            return;
        }
        LivingEntity target = warden.getTarget();
        if (target != null && !isInsideTerritory(target.getLocation())) {
            warden.setTarget(null);
            relocateWithDig(warden);
        }
    }

    private void ensureWarden(int tier, Location spawn, double maxHealth, String name) {
        Warden existing = getByTier(tier);
        if (existing != null && existing.isValid() && !existing.isDead()) {
            configureWarden(existing, maxHealth, name);
            return;
        }
        spawnWithEmerge(tier, spawn, maxHealth, name, maxHealth);
    }

    private void configureWarden(Warden warden, double maxHealth, String name) {
        warden.customName(Component.text(name));
        warden.setCustomNameVisible(true);
        warden.setAware(true);
        warden.setTarget(null);
        AttributeInstance maxHealthAttribute = warden.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(maxHealth);
        }
        if (warden.getHealth() > maxHealth || warden.getHealth() <= 0.0) {
            warden.setHealth(maxHealth);
        }
        applyScaleForName(warden, name);
    }

    private void applyScaleForName(Warden warden, String name) {
        if (warden == null) {
            return;
        }
        AttributeInstance scaleAttribute = warden.getAttribute(Attribute.SCALE);
        if (scaleAttribute == null) {
            return;
        }
        if ("Gary Jr.".equals(name)) {
            scaleAttribute.setBaseValue(0.7);
            return;
        }
        scaleAttribute.setBaseValue(1.0);
    }

    private void relocateWithDig(Warden warden) {
        if (warden == null || !warden.isValid() || warden.isDead()) {
            return;
        }
        UUID id = warden.getUniqueId();
        if (!relocatingWardens.add(id)) {
            return;
        }
        int tier = resolveTier(warden);
        if (tier <= 0) {
            relocatingWardens.remove(id);
            return;
        }
        double maxHealth = resolveMaxHealth(tier);
        double health = Math.max(1.0, Math.min(warden.getHealth(), maxHealth));
        warden.setTarget(null);
        warden.setAI(false);
        warden.setVelocity(new Vector(0, 0, 0));
        warden.setPose(Pose.DIGGING, true);

        JavaPlugin plugin = session.getBedwarsManager().getPlugin();
        if (plugin == null) {
            relocatingWardens.remove(id);
            return;
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!session.isRunning()) {
                relocatingWardens.remove(id);
                return;
            }
            if (warden.isValid() && !warden.isDead()) {
                warden.remove();
            }
            setByTier(tier, null);
            Location spawn = resolveMiddleSpawn(true);
            if (spawn != null) {
                spawnWithEmerge(tier, spawn, maxHealth, resolveName(tier), health);
            }
            relocatingWardens.remove(id);
        }, DIG_TICKS);
        sessionTasks.add(task);
    }

    private void spawnWithEmerge(int tier, Location spawn, double maxHealth, String name, double health) {
        World world = spawn.getWorld();
        if (world == null) {
            return;
        }
        Warden warden = world.spawn(spawn, Warden.class, entity -> {
            entity.addScoreboardTag(WARDEN_FAMILY_TAG);
            if (tier == 1) {
                entity.addScoreboardTag(WARDEN_GARY_TAG);
            } else if (tier == 2) {
                entity.addScoreboardTag(WARDEN_WIFE_TAG);
            } else if (tier == 3) {
                entity.addScoreboardTag(WARDEN_JR_TAG);
            }
            entity.setRemoveWhenFarAway(false);
            entity.setPersistent(true);
            entity.setCanPickupItems(false);
            entity.setAI(false);
            entity.setPose(Pose.EMERGING, true);
        });
        configureWarden(warden, maxHealth, name);
        warden.setHealth(Math.max(1.0, Math.min(health, maxHealth)));
        setByTier(tier, warden.getUniqueId());

        JavaPlugin plugin = session.getBedwarsManager().getPlugin();
        if (plugin == null) {
            return;
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!warden.isValid() || warden.isDead()) {
                return;
            }
            warden.setPose(Pose.STANDING, false);
            warden.setAI(true);
            warden.setTarget(null);
        }, EMERGE_TICKS);
        sessionTasks.add(task);
    }

    private void removeByTier(int tier) {
        Warden warden = getByTier(tier);
        if (warden != null) {
            warden.remove();
        }
        setByTier(tier, null);
    }

    private Warden getByTier(int tier) {
        UUID id = getIdByTier(tier);
        if (id == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(id);
        if (!(entity instanceof Warden warden) || !warden.isValid() || warden.isDead()) {
            setByTier(tier, null);
            return null;
        }
        return warden;
    }

    private UUID getIdByTier(int tier) {
        return switch (tier) {
            case 1 -> garyId;
            case 2 -> wifeId;
            case 3 -> jrId;
            default -> null;
        };
    }

    private void setByTier(int tier, UUID id) {
        switch (tier) {
            case 1 -> garyId = id;
            case 2 -> wifeId = id;
            case 3 -> jrId = id;
            default -> {
            }
        }
    }

    private int resolveTier(Warden warden) {
        if (warden.getScoreboardTags().contains(WARDEN_GARY_TAG)) {
            return 1;
        }
        if (warden.getScoreboardTags().contains(WARDEN_WIFE_TAG)) {
            return 2;
        }
        if (warden.getScoreboardTags().contains(WARDEN_JR_TAG)) {
            return 3;
        }
        return 0;
    }

    private String resolveName(int tier) {
        return switch (tier) {
            case 1 -> "Gary the Warden";
            case 2 -> "Gary's Wife";
            case 3 -> "Gary Jr.";
            default -> "Warden";
        };
    }

    private double resolveMaxHealth(int tier) {
        return switch (tier) {
            case 1 -> 500.0;
            case 2 -> 200.0;
            case 3 -> 100.0;
            default -> 100.0;
        };
    }

    private Location resolveMiddleSpawn(boolean allowPlatform) {
        BlockPoint center = session.getArena().getCenter();
        World world = session.getArena().getWorld();
        if (center == null || world == null) {
            return null;
        }
        if (allowPlatform) {
            ensureMiddlePlatformIfNeeded(world, center);
        }
        return new Location(world, center.x() + 0.5, center.y() + 1.0, center.z() + 0.5);
    }

    private void ensureMiddlePlatformIfNeeded(World world, BlockPoint center) {
        if (world == null || center == null) {
            return;
        }
        Block support = world.getBlockAt(center.x(), center.y(), center.z());
        if (!support.isEmpty()) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockAt(center.x() + dx, center.y(), center.z() + dz);
                if (!block.isEmpty()) {
                    continue;
                }
                BlockPoint point = new BlockPoint(block.getX(), block.getY(), block.getZ());
                temporaryMiddlePlatformBlocks.putIfAbsent(point, block.getState());
                block.setType(Material.STONE, false);
            }
        }
    }

    private void clearTemporaryMiddlePlatform() {
        if (temporaryMiddlePlatformBlocks.isEmpty()) {
            return;
        }
        for (BlockState state : temporaryMiddlePlatformBlocks.values()) {
            if (state != null) {
                state.update(true, false);
            }
        }
        temporaryMiddlePlatformBlocks.clear();
    }

    private boolean isInsideTerritory(Location location) {
        if (location == null) {
            return false;
        }
        List<GeneratorInfo> generators = session.getArena().getGenerators();
        if (generators == null || generators.isEmpty()) {
            return false;
        }
        int radiusSquared = TERRITORY_RADIUS * TERRITORY_RADIUS;
        int x = location.getBlockX();
        int z = location.getBlockZ();
        for (GeneratorInfo generator : generators) {
            if (generator == null || generator.location() == null) {
                continue;
            }
            GeneratorType type = generator.type();
            if (type != GeneratorType.DIAMOND && type != GeneratorType.EMERALD) {
                continue;
            }
            int dx = x - generator.location().x();
            int dz = z - generator.location().z();
            if (dx * dx + dz * dz <= radiusSquared) {
                return true;
            }
        }
        return false;
    }
}
