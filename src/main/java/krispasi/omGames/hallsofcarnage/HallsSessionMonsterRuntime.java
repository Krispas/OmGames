package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

final class HallsSessionMonsterRuntime {
    private static final int SPAWN_INTERVAL_TICKS = 100;

    private final JavaPlugin plugin;
    private final World world;
    private final HallsConfig.BlockPoint origin;
    private final Set<UUID> participants;
    private final Map<String, HallsMonsterType> monsterTypes;
    private final IntSupplier maxSculkSupplier;
    private final Predicate<UUID> aliveParticipantPredicate;
    private final Consumer<String> debugSink;
    private final Set<UUID> spawnedMonsters = new HashSet<>();
    private final Map<UUID, Long> concealedParticipants = new java.util.HashMap<>();
    private List<HallsExplorationGenerator.Cell> spawnCells = List.of();
    private List<HallsMonsterType> commonPool = List.of();
    private HallsMonsterType activeSpecialType;
    private Random random = new Random();
    private BukkitTask spawnTask;
    private int maxAlive;
    private int baseMaxAlive;
    private int spawnedThisFloor;
    private int spawnCooldownTicks;
    private int capExtensionCooldownTicks;
    private int capExtensionIntervalTicks;

    HallsSessionMonsterRuntime(JavaPlugin plugin,
                               World world,
                               HallsConfig.BlockPoint origin,
                               Set<UUID> participants,
                               Map<String, HallsMonsterType> monsterTypes,
                               IntSupplier maxSculkSupplier,
                               Predicate<UUID> aliveParticipantPredicate,
                               Consumer<String> debugSink) {
        this.plugin = plugin;
        this.world = world;
        this.origin = origin;
        this.participants = participants;
        this.monsterTypes = monsterTypes == null ? Map.of() : Map.copyOf(monsterTypes);
        this.maxSculkSupplier = maxSculkSupplier == null ? () -> 0 : maxSculkSupplier;
        this.aliveParticipantPredicate = aliveParticipantPredicate == null ? id -> true : aliveParticipantPredicate;
        this.debugSink = debugSink == null ? ignored -> { } : debugSink;
    }

    void startExplorationFloor(HallsExplorationGenerator.Plan plan,
                               HallsScenario.FloorDefinition floor,
                               HallsLevelType levelType,
                               HallsFloorModifiers modifiers,
                               Random random) {
        clear();
        this.random = random == null ? new Random() : random;
        this.spawnCells = spawnCells(plan);
        this.commonPool = monsterPool(levelType == null ? List.of() : levelType.commonMonsters());
        List<HallsMonsterType> specialPool = monsterPool(levelType == null ? List.of() : levelType.specialMonsters());
        this.activeSpecialType = modifiers != null && modifiers.useSpecialEnemy() && !specialPool.isEmpty()
                ? specialPool.get(this.random.nextInt(specialPool.size()))
                : null;
        int difficulty = parseDifficulty(floor == null ? "0" : floor.difficulty(), floor == null ? 1 : floor.firstFloor());
        int rooms = Math.max(1, floor == null ? 1 : floor.rooms());
        double enemyMultiplier = modifiers == null ? 1.0 : modifiers.enemySpawnMultiplier();
        double playerStack = participantStackMultiplier();
        this.baseMaxAlive = Math.max(2, Math.min(36, (int) Math.round((1 + rooms / 4.0 + difficulty / 15.0) * playerStack * enemyMultiplier)));
        this.maxAlive = baseMaxAlive;
        this.capExtensionIntervalTicks = Math.max(20, (int) Math.round(capExtensionIntervalTicks(difficulty) / playerStack));
        this.capExtensionCooldownTicks = capExtensionIntervalTicks;
        if (spawnCells.isEmpty() || commonPool.isEmpty()) {
            return;
        }
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        spawnCooldownTicks = 0;
        tick();
    }

    void clear() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        for (UUID entityId : Set.copyOf(spawnedMonsters)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
        spawnedMonsters.clear();
        concealedParticipants.clear();
        spawnCells = List.of();
        spawnedThisFloor = 0;
        activeSpecialType = null;
        spawnCooldownTicks = 0;
        baseMaxAlive = 0;
        maxAlive = 0;
        capExtensionCooldownTicks = 0;
        capExtensionIntervalTicks = 0;
    }

    String debugStatus() {
        pruneDeadMonsters();
        return spawnedMonsters.size() + " alive, cap " + baseMaxAlive + ", extended cap " + maxAlive
                + ", spawned " + spawnedThisFloor + ", next spawn "
                + Math.max(0, spawnCooldownTicks / 20) + "s, next cap +"
                + Math.max(0, capExtensionCooldownTicks / 20) + "s";
    }

    boolean registerSplitMonster(Entity entity) {
        if (!(entity instanceof Slime slime) || !world.equals(entity.getWorld()) || spawnCells.isEmpty()) {
            return false;
        }
        Location location = entity.getLocation();
        if (Math.abs(location.getX() - origin.x()) > 96.0 || Math.abs(location.getZ() - origin.z()) > 96.0) {
            return false;
        }
        configureSplitSlime(slime);
        spawnedMonsters.add(slime.getUniqueId());
        maxAlive++;
        return true;
    }

    void handleMonsterDeath(LivingEntity entity, Player killer) {
        if (entity == null || !spawnedMonsters.remove(entity.getUniqueId())) {
            return;
        }
        if (killer != null && participants.contains(killer.getUniqueId()) && aliveParticipantPredicate.test(killer.getUniqueId())) {
            maxAlive = Math.max(0, maxAlive - 1);
        }
    }

    void alert(Location location) {
        if (location == null || !world.equals(location.getWorld())) {
            return;
        }
        Player target = nearestParticipant(location, 96.0);
        if (target == null) {
            return;
        }
        for (UUID entityId : Set.copyOf(spawnedMonsters)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof Creature creature && creature.getWorld().equals(world)
                    && creature.getLocation().distanceSquared(location) <= 96.0 * 96.0) {
                creature.setTarget(target);
            }
        }
    }

    void clearTargetsNear(Location location, double radius) {
        if (location == null || !world.equals(location.getWorld())) {
            return;
        }
        double radiusSquared = radius * radius;
        for (UUID entityId : Set.copyOf(spawnedMonsters)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof Creature creature && creature.getWorld().equals(world)
                    && creature.getLocation().distanceSquared(location) <= radiusSquared) {
                creature.setTarget(null);
            }
        }
    }

    void concealParticipant(UUID playerId, long durationMillis) {
        if (playerId == null || durationMillis <= 0L) {
            return;
        }
        concealedParticipants.put(playerId, System.currentTimeMillis() + durationMillis);
        clearTargetsFor(playerId);
    }

    private void clearTargetsFor(UUID playerId) {
        for (UUID entityId : Set.copyOf(spawnedMonsters)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (!(entity instanceof Creature creature)) {
                continue;
            }
            LivingEntity target = creature.getTarget();
            if (target instanceof Player player && player.getUniqueId().equals(playerId)) {
                creature.setTarget(null);
            }
        }
    }

    private void tick() {
        pruneDeadMonsters();
        killMonstersInPits();
        updateSpawnCapExtension();
        updateMonsterTargets();
        spawnCooldownTicks -= 20;
        if (spawnCooldownTicks > 0) {
            return;
        }
        spawnCooldownTicks = SPAWN_INTERVAL_TICKS;
        if (spawnedMonsters.size() >= maxAlive) {
            return;
        }
        HallsExplorationGenerator.Cell cell = spawnCellAwayFromPlayers();
        if (cell == null) {
            return;
        }
        HallsMonsterType type = rollMonsterType();
        Location location = new Location(world, cell.x() + 0.5, origin.y(), cell.z() + 0.5);
        Entity entity = world.spawnEntity(location, type.entityType());
        if (entity instanceof LivingEntity living) {
            configureLivingMonster(living, type);
            spawnedMonsters.add(living.getUniqueId());
            spawnedThisFloor++;
            debugSink.accept("Spawned " + type.id() + " at " + cell.x() + " " + origin.y() + " " + cell.z()
                    + "; next spawn in " + (SPAWN_INTERVAL_TICKS / 20) + "s; alive "
                    + spawnedMonsters.size() + "/" + maxAlive + ".");
        } else {
            entity.remove();
        }
    }

    private void configureLivingMonster(LivingEntity living, HallsMonsterType type) {
        living.customName(Component.text(type.name(), NamedTextColor.DARK_RED));
        living.setCustomNameVisible(false);
        living.setPersistent(true);
        if (living instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
        }
        living.addScoreboardTag("omgames_hoc_monster");
        AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(type.health());
            living.setHealth(type.health());
        }
        if (living instanceof Zombie zombie) {
            zombie.setBaby(type.baby());
        } else if (living instanceof Ageable ageable) {
            if (type.baby()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
        }
        if (living instanceof Slime slime && type.slimeSize() > 0) {
            slime.setSize(type.slimeSize());
        }
        applyEquipment(living.getEquipment(), type);
        if (living instanceof Creature creature) {
            creature.setTarget(nearestParticipant(living.getLocation(), 18.0));
        }
    }

    private void configureSplitSlime(Slime slime) {
        slime.setPersistent(true);
        slime.setRemoveWhenFarAway(false);
        slime.addScoreboardTag("omgames_hoc_monster");
        EntityEquipment equipment = slime.getEquipment();
        if (equipment != null) {
            equipment.clear();
        }
    }

    private void applyEquipment(EntityEquipment equipment, HallsMonsterType type) {
        if (equipment == null) {
            return;
        }
        equipment.clear();
        if (!type.mainHand().isAir()) {
            equipment.setItemInMainHand(new ItemStack(type.mainHand()));
        }
        setArmor(equipment, "helmet", type.armor().get("helmet"));
        setArmor(equipment, "chestplate", type.armor().get("chestplate"));
        setArmor(equipment, "leggings", type.armor().get("leggings"));
        setArmor(equipment, "boots", type.armor().get("boots"));
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
    }

    boolean isSessionMonster(Entity entity) {
        return entity != null && (spawnedMonsters.contains(entity.getUniqueId())
                || entity.getScoreboardTags().contains("omgames_hoc_monster"));
    }

    private void pruneDeadMonsters() {
        spawnedMonsters.removeIf(entityId -> {
            Entity entity = Bukkit.getEntity(entityId);
            return entity == null || entity.isDead() || !entity.isValid();
        });
    }

    private void setArmor(EntityEquipment equipment, String slot, Material material) {
        if (material == null || material.isAir()) {
            return;
        }
        ItemStack item = new ItemStack(material);
        switch (slot) {
            case "helmet" -> equipment.setHelmet(item);
            case "chestplate" -> equipment.setChestplate(item);
            case "leggings" -> equipment.setLeggings(item);
            case "boots" -> equipment.setBoots(item);
            default -> {
            }
        }
    }

    private void killMonstersInPits() {
        for (UUID entityId : Set.copyOf(spawnedMonsters)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof LivingEntity living && living.getWorld().equals(world)
                    && living.getLocation().getY() <= origin.y() - 8) {
                spawnedMonsters.remove(entityId);
                living.setHealth(0.0);
            }
        }
    }

    private HallsMonsterType rollMonsterType() {
        HallsMonsterType warden = rollWarden();
        if (warden != null) {
            return warden;
        }
        if (activeSpecialType != null && random.nextInt(6) == 0) {
            return activeSpecialType;
        }
        return commonPool.get(random.nextInt(commonPool.size()));
    }

    private HallsMonsterType rollWarden() {
        int sculk = maxSculkSupplier.getAsInt();
        if (sculk < 65) {
            return null;
        }
        double chance = Math.min(sculk - 55, 35) / 10.0;
        if (random.nextDouble() * 100.0 >= chance) {
            return null;
        }
        HallsMonsterType configured = monsterTypes.get("warden");
        if (configured != null) {
            return configured;
        }
        return new HallsMonsterType("warden", "Warden", EntityType.WARDEN, 500.0, false, 0, Material.AIR, Map.of());
    }

    private HallsExplorationGenerator.Cell spawnCellAwayFromPlayers() {
        List<HallsExplorationGenerator.Cell> shuffled = new ArrayList<>(spawnCells);
        java.util.Collections.shuffle(shuffled, random);
        for (HallsExplorationGenerator.Cell cell : shuffled) {
            Location location = new Location(world, cell.x() + 0.5, origin.y(), cell.z() + 0.5);
            if (nearestParticipant(location, 14.0) == null && !isVisibleToParticipant(location)) {
                return cell;
            }
        }
        return null;
    }

    private boolean isVisibleToParticipant(Location location) {
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.getWorld().equals(world)) {
                continue;
            }
            Location eye = player.getEyeLocation();
            if (eye.distanceSquared(location) > 32.0 * 32.0) {
                continue;
            }
            org.bukkit.util.Vector toSpawn = location.clone().add(0.0, 1.0, 0.0).toVector().subtract(eye.toVector());
            org.bukkit.util.Vector look = eye.getDirection().normalize();
            org.bukkit.util.Vector direction = toSpawn.clone().normalize();
            if (look.dot(direction) < 0.58) {
                continue;
            }
            double distance = eye.distance(location);
            if (world.rayTraceBlocks(eye, direction, distance) == null) {
                return true;
            }
        }
        return false;
    }

    private void updateSpawnCapExtension() {
        if (capExtensionIntervalTicks <= 0) {
            return;
        }
        capExtensionCooldownTicks -= 20;
        if (capExtensionCooldownTicks > 0) {
            return;
        }
        maxAlive++;
        capExtensionCooldownTicks = capExtensionIntervalTicks;
        debugSink.accept("Monster cap increased to " + maxAlive + "; next cap increase in "
                + Math.max(0, capExtensionCooldownTicks / 20) + "s.");
    }

    private void updateMonsterTargets() {
        for (UUID entityId : Set.copyOf(spawnedMonsters)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (!(entity instanceof Creature creature) || !creature.getWorld().equals(world)) {
                continue;
            }
            LivingEntity current = creature.getTarget();
            if (current instanceof Player player
                    && player.getWorld().equals(world)
                    && participants.contains(player.getUniqueId())
                    && aliveParticipantPredicate.test(player.getUniqueId())
                    && !isConcealed(player.getUniqueId())
                    && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                    && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            creature.setTarget(nearestParticipant(creature.getLocation(), 18.0));
        }
    }

    private int capExtensionIntervalTicks(int difficulty) {
        int seconds;
        if (difficulty <= 10) {
            seconds = 60;
        } else if (difficulty >= 80) {
            seconds = 20;
        } else {
            seconds = (int) Math.round(60.0 - ((difficulty - 10) * (40.0 / 70.0)));
        }
        return seconds * 20;
    }

    private double participantStackMultiplier() {
        return 1.0 + Math.max(0, participants.size() - 1) * 0.33;
    }

    private Player nearestParticipant(Location location, double radius) {
        double bestDistance = radius * radius;
        Player best = null;
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.getWorld().equals(world)
                    || !aliveParticipantPredicate.test(playerId)
                    || isConcealed(playerId)
                    || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(location);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private boolean isConcealed(UUID playerId) {
        Long until = concealedParticipants.get(playerId);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            concealedParticipants.remove(playerId);
            return false;
        }
        return true;
    }

    private List<HallsMonsterType> monsterPool(List<String> ids) {
        List<HallsMonsterType> pool = new ArrayList<>();
        for (String id : ids) {
            HallsMonsterType type = monsterTypes.get(id);
            if (type != null) {
                pool.add(type);
            }
        }
        return List.copyOf(pool);
    }

    private List<HallsExplorationGenerator.Cell> spawnCells(HallsExplorationGenerator.Plan plan) {
        if (plan == null) {
            return List.of();
        }
        return plan.walkableCells().stream()
                .filter(cell -> Math.abs(cell.x() - origin.x()) + Math.abs(cell.z() - origin.z()) > 16)
                .filter(cell -> world.getBlockAt(cell.x(), origin.y(), cell.z()).getType().isAir())
                .toList();
    }

    private int parseDifficulty(String difficulty, int floor) {
        if (difficulty == null || difficulty.isBlank()) {
            return 0;
        }
        String normalized = difficulty.replace("floor", Integer.toString(floor)).replace(" ", "");
        int plus = normalized.indexOf('+');
        if (plus > 0) {
            return parseInt(normalized.substring(0, plus), 0) + parseInt(normalized.substring(plus + 1), 0);
        }
        return parseInt(normalized, 0);
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
