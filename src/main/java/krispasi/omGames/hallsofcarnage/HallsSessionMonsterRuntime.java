package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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
    private final Set<UUID> spawnedMonsters = new HashSet<>();
    private List<HallsExplorationGenerator.Cell> spawnCells = List.of();
    private List<HallsMonsterType> commonPool = List.of();
    private List<HallsMonsterType> specialPool = List.of();
    private Random random = new Random();
    private BukkitTask spawnTask;
    private int maxAlive;
    private int baseMaxAlive;
    private int totalSpawnBudget;
    private int spawnedThisFloor;
    private long floorStartedAtMillis;

    HallsSessionMonsterRuntime(JavaPlugin plugin,
                               World world,
                               HallsConfig.BlockPoint origin,
                               Set<UUID> participants,
                               Map<String, HallsMonsterType> monsterTypes,
                               IntSupplier maxSculkSupplier) {
        this.plugin = plugin;
        this.world = world;
        this.origin = origin;
        this.participants = participants;
        this.monsterTypes = monsterTypes == null ? Map.of() : Map.copyOf(monsterTypes);
        this.maxSculkSupplier = maxSculkSupplier == null ? () -> 0 : maxSculkSupplier;
    }

    void startExplorationFloor(HallsExplorationGenerator.Plan plan,
                               HallsScenario.FloorDefinition floor,
                               HallsLevelType levelType,
                               Random random) {
        clear();
        this.random = random == null ? new Random() : random;
        this.spawnCells = spawnCells(plan);
        this.commonPool = monsterPool(levelType == null ? List.of() : levelType.commonMonsters());
        this.specialPool = monsterPool(levelType == null ? List.of() : levelType.specialMonsters());
        int difficulty = parseDifficulty(floor == null ? "0" : floor.difficulty(), floor == null ? 1 : floor.firstFloor());
        int rooms = Math.max(1, floor == null ? 1 : floor.rooms());
        this.baseMaxAlive = Math.max(2, Math.min(16, participants.size() + rooms / 4 + difficulty / 15));
        this.maxAlive = baseMaxAlive;
        this.totalSpawnBudget = Math.max(maxAlive, rooms + difficulty / 3);
        this.floorStartedAtMillis = System.currentTimeMillis();
        if (spawnCells.isEmpty() || commonPool.isEmpty()) {
            return;
        }
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnTick, 20L, SPAWN_INTERVAL_TICKS);
        spawnTick();
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
        spawnCells = List.of();
        spawnedThisFloor = 0;
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
        world.playSound(location, Sound.ENTITY_ZOMBIE_AMBIENT, 0.7f, 0.65f);
    }

    private void spawnTick() {
        spawnedMonsters.removeIf(entityId -> {
            Entity entity = Bukkit.getEntity(entityId);
            return entity == null || entity.isDead() || !entity.isValid();
        });
        updateSpawnLimit();
        if (spawnedThisFloor >= totalSpawnBudget) {
            if (spawnedMonsters.isEmpty() && spawnTask != null) {
                spawnTask.cancel();
                spawnTask = null;
            }
            return;
        }
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
        } else {
            entity.remove();
        }
    }

    private void configureLivingMonster(LivingEntity living, HallsMonsterType type) {
        living.customName(Component.text(type.name(), NamedTextColor.DARK_RED));
        living.setCustomNameVisible(false);
        living.setPersistent(false);
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
    }

    private void applyEquipment(EntityEquipment equipment, HallsMonsterType type) {
        if (equipment == null) {
            return;
        }
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

    private HallsMonsterType rollMonsterType() {
        HallsMonsterType warden = rollWarden();
        if (warden != null) {
            return warden;
        }
        if (!specialPool.isEmpty() && spawnedThisFloor > 0 && spawnedThisFloor % 7 == 0) {
            return specialPool.get(random.nextInt(specialPool.size()));
        }
        return commonPool.get(random.nextInt(commonPool.size()));
    }

    private HallsMonsterType rollWarden() {
        int sculk = maxSculkSupplier.getAsInt();
        if (sculk <= 50) {
            return null;
        }
        int chance = Math.min(sculk - 40, 35);
        if (random.nextInt(100) >= chance) {
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

    private void updateSpawnLimit() {
        long minutes = Math.max(0L, (System.currentTimeMillis() - floorStartedAtMillis) / 60_000L);
        int scaled = (int) Math.ceil(baseMaxAlive * (1.0 + minutes * 0.05));
        maxAlive = Math.max(baseMaxAlive, Math.min(40, scaled));
        totalSpawnBudget = Math.max(totalSpawnBudget, maxAlive + (int) minutes);
    }

    private Player nearestParticipant(Location location, double radius) {
        double bestDistance = radius * radius;
        Player best = null;
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.getWorld().equals(world)) {
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
