package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HallsSession {
    private static final int CLEAR_RADIUS = 72;
    private static final int CLEAR_HEIGHT = 12;
    private static final int ROOM_HEIGHT = 5;
    private static final int ELEVATOR_INNER_RADIUS = 2;
    private static final int ELEVATOR_OUTER_RADIUS = 3;
    private static final int CLEAR_COLUMNS_PER_TICK = 3;
    private static final int CORRIDOR_CELLS_PER_TICK = 96;
    private static final int MIN_ELEVATOR_TRANSITION_TICKS = 100;
    private static final int DISPLAY_INTERPOLATION_DELAY_TICKS = 1;
    private static final int DISPLAY_TELEPORT_DURATION_TICKS = 2;
    private static final double DROP_DISPLAY_SUPPORT_OFFSET = 0.08;
    private static final double DROP_SETTLE_VELOCITY_SQUARED = 0.0016;
    private static final String STARTER_ITEM_ID = "vagabonds_club";
    private static final long LEFT_BEHIND_MODIFIER_DELAY_TICKS = 100L;
    private static final String HEALTH_TOTEM_MODIFIER = "hoc_health_totem";
    private static final String SPEED_TOTEM_MODIFIER = "hoc_speed_totem";
    private static final long SCULK_MAUL_SPLASH_COOLDOWN_MILLIS = 350L;

    private final JavaPlugin plugin;
    private final int id;
    private final HallsScenario scenario;
    private final World world;
    private final HallsConfig.BlockPoint origin;
    private final File dataFolder;
    private final Map<String, HallsLevelType> levelTypes;
    private final Map<String, HallsBreakableType> breakableTypes;
    private final Map<String, HallsVegetationType> vegetationTypes;
    private final Map<String, HallsItemType> itemTypes;
    private final Map<String, HallsTrapType> trapTypes;
    private final Map<String, HallsMonsterType> monsterTypes;
    private final Map<String, HallsModifierType> modifierTypes;
    private final Map<String, HallsBuildingType> buildingTypes;
    private final Set<UUID> participants;
    private final UUID hostId;
    private final String difficultyId;
    private final double difficultyMultiplier;
    private final HallsSaveData initialSave;
    private final List<BlockSnapshot> snapshots = new ArrayList<>();
    private final Map<UUID, BreakableProp> breakableProps = new HashMap<>();
    private final Set<UUID> vegetationDisplays = new HashSet<>();
    private final Map<UUID, PhysicsDrop> physicsDrops = new HashMap<>();
    private final Map<String, Long> utilityCooldowns = new HashMap<>();
    private final Map<String, Long> sculkMaulSplashCooldowns = new HashMap<>();
    private final Set<UUID> sculkMaulSplashing = new HashSet<>();
    private final Map<Integer, List<HallsCampRuntime.PlotState>> savedCampStates = new HashMap<>();
    private final Map<Integer, HallsFloorModifiers> scannedFloorModifiers = new HashMap<>();
    private final HallsSessionTrapRuntime trapRuntime;
    private final HallsSessionMonsterRuntime monsterRuntime;
    private final HallsSessionSculkRuntime sculkRuntime;
    private final HallsCampRuntime campRuntime;
    private final java.util.function.Predicate<UUID> debugEnabled;
    private final Set<UUID> ghostPlayers = new HashSet<>();
    private final Map<UUID, Integer> healthTotemLevels = new HashMap<>();
    private final Map<UUID, Integer> speedTotemLevels = new HashMap<>();
    private boolean firstGhostCoinCacheDropped;
    private BukkitTask hudTask;
    private BukkitTask physicsDropTask;
    private BukkitTask floorBuildTask;
    private BukkitTask gameOverTask;
    private long startedAtMillis;
    private int currentFloor = 1;
    private int woodScrap;
    private int ironScrap;
    private int diamondScrap;
    private int redstoneScrap;
    private int coins;
    private ItemStack[] elevatorChestContents = new ItemStack[27];
    private int activeClearRadius = CLEAR_RADIUS;
    private String activeLevelTypeId = "howling_corridors";
    private int activeTargetRooms;
    private int activeGeneratedRooms;
    private boolean transitioning;
    private boolean running;
    private boolean elevatorChestSnapshotLocked;
    private Location startRoomSpawn;
    private long floorStartedAtMillis;
    private HallsFloorModifiers activeFloorModifiers = HallsFloorModifiers.none();
    private int compassTrailCountdown;

    public HallsSession(JavaPlugin plugin,
                        int id,
                        HallsScenario scenario,
                        World world,
                        HallsConfig.BlockPoint origin,
                        File dataFolder,
                        Map<String, HallsLevelType> levelTypes,
                        Map<String, HallsBreakableType> breakableTypes,
                        Map<String, HallsVegetationType> vegetationTypes,
                        Map<String, HallsItemType> itemTypes,
                        Map<String, HallsTrapType> trapTypes,
                        Map<String, HallsMonsterType> monsterTypes,
                        Map<String, HallsModifierType> modifierTypes,
                        Map<String, HallsBuildingType> buildingTypes,
                        UUID hostId,
                        String difficultyId,
                        double difficultyMultiplier,
                        HallsSaveData initialSave,
                        List<Player> players,
                        java.util.function.Predicate<UUID> debugEnabled) {
        this.plugin = plugin;
        this.id = id;
        this.scenario = scenario;
        this.world = world;
        this.origin = origin;
        this.dataFolder = dataFolder;
        this.levelTypes = levelTypes == null ? Map.of() : Map.copyOf(levelTypes);
        this.breakableTypes = breakableTypes == null ? Map.of() : Map.copyOf(breakableTypes);
        this.vegetationTypes = vegetationTypes == null ? Map.of() : Map.copyOf(vegetationTypes);
        this.itemTypes = itemTypes == null ? Map.of() : Map.copyOf(itemTypes);
        this.trapTypes = trapTypes == null ? Map.of() : Map.copyOf(trapTypes);
        this.monsterTypes = monsterTypes == null ? Map.of() : Map.copyOf(monsterTypes);
        this.modifierTypes = modifierTypes == null ? Map.of() : Map.copyOf(modifierTypes);
        this.buildingTypes = buildingTypes == null ? Map.of() : Map.copyOf(buildingTypes);
        this.hostId = hostId;
        this.difficultyId = normalizeId(difficultyId == null || difficultyId.isBlank() ? "normal" : difficultyId);
        this.difficultyMultiplier = Math.max(1.0, difficultyMultiplier);
        this.initialSave = initialSave;
        this.participants = new HashSet<>();
        for (Player player : players) {
            participants.add(player.getUniqueId());
        }
        this.trapRuntime = new HallsSessionTrapRuntime(plugin, world, origin, participants, this::isAliveParticipant,
                this::setBlock, this.trapTypes,
                () -> activeFloorModifiers.trapDamageMultiplier());
        this.sculkRuntime = new HallsSessionSculkRuntime(plugin, world, origin, participants, this::setBlock,
                this::isAliveParticipant);
        this.monsterRuntime = new HallsSessionMonsterRuntime(plugin, world, origin, participants, this.monsterTypes,
                sculkRuntime::maxSculkPercent, this::isAliveParticipant,
                location -> dropSessionItem(location, coinItem(1)), this::debug);
        this.campRuntime = new HallsCampRuntime(plugin, world, scenario, this.buildingTypes, this.itemTypes,
                type -> HallsItemFactory.create(plugin, type, 1), new HallsCampRuntime.ScrapAccount() {
            @Override
            public boolean canSpend(Map<String, Integer> cost) {
                return hasStoredScrap(cost);
            }

            @Override
            public boolean spend(Map<String, Integer> cost) {
                return spendStoredScrap(cost);
            }
        }, this::reducePlayerSculk, new HallsCampRuntime.TotemAccount() {
            @Override
            public boolean applyHealthTotem(Player player, int level) {
                return HallsSession.this.applyHealthTotem(player, level);
            }

            @Override
            public boolean applySpeedTotem(Player player, int level) {
                return HallsSession.this.applySpeedTotem(player, level);
            }
        }, this::scanUpcomingFloors);
        this.debugEnabled = debugEnabled == null ? ignored -> false : debugEnabled;
    }

    public int id() {
        return id;
    }

    public HallsScenario scenario() {
        return scenario;
    }

    public HallsConfig.BlockPoint origin() {
        return origin;
    }

    public int currentFloor() {
        return currentFloor;
    }

    public String activeLevelTypeId() {
        return activeLevelTypeId;
    }

    public int activeTargetRooms() {
        return activeTargetRooms;
    }

    public int activeGeneratedRooms() {
        return activeGeneratedRooms;
    }

    public String monsterDebugStatus() {
        return monsterRuntime.debugStatus();
    }

    public String debugSummary() {
        return "Session " + id + " floor " + currentFloor + ", rooms " + activeGeneratedRooms + "/" + activeTargetRooms
                + ", breakables " + breakableProps.size() + ", traps " + trapRuntime.activeTrapCount()
                + ", vegetation " + vegetationDisplays.size() + ", monsters " + monsterRuntime.debugStatus() + ".";
    }

    public boolean isTransitioning() {
        return transitioning;
    }

    public boolean canSaveAndLeave() {
        return running && !transitioning && (currentFloor == 1 || isCurrentFloorCamp());
    }

    public boolean isParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    public Set<UUID> participants() {
        return Set.copyOf(participants);
    }

    public UUID hostId() {
        return hostId;
    }

    public boolean isHost(Player player) {
        return player != null && player.getUniqueId().equals(hostId);
    }

    public boolean isSessionEntity(Entity entity) {
        return entity != null && (breakableProps.containsKey(entity.getUniqueId())
                || physicsDrops.containsKey(entity.getUniqueId())
                || campRuntime.isCampEntity(entity));
    }

    public boolean handleCampInteract(Player player, Entity entity) {
        if (player == null || entity == null || !running || !player.getWorld().equals(world)) {
            return false;
        }
        if (ghostPlayers.contains(player.getUniqueId())) {
            player.sendActionBar(Component.text("Ghosts cannot use camp plots.", NamedTextColor.GRAY));
            return campRuntime.isCampEntity(entity);
        }
        return campRuntime.handleInteract(player, entity);
    }

    public boolean handleCampInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        return campRuntime.handleInventoryClick(event);
    }

    public boolean handleCampInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        return campRuntime.handleInventoryClose(event);
    }

    public boolean isSessionMonster(Entity entity) {
        return monsterRuntime.isSessionMonster(entity);
    }

    public boolean registerSplitMonster(Entity entity) {
        return monsterRuntime.registerSplitMonster(entity);
    }

    public void handleMonsterDeath(org.bukkit.entity.LivingEntity entity, Player killer) {
        monsterRuntime.handleMonsterDeath(entity, killer);
    }

    public boolean handlePhysicsDropPickup(Player player, Entity entity) {
        if (player == null || entity == null || !running || !player.getWorld().equals(world)) {
            return false;
        }
        if (ghostPlayers.contains(player.getUniqueId())) {
            player.sendActionBar(Component.text("Ghosts cannot pick up items.", NamedTextColor.GRAY));
            return true;
        }
        PhysicsDrop drop = physicsDrops.get(entity.getUniqueId());
        if (drop == null) {
            return false;
        }
        if (isCoinItem(drop.stack())) {
            coins += multipliedCoins(Math.max(1, drop.stack().getAmount()));
            removePhysicsDrop(drop);
            world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.8f);
            return true;
        }
        if (!player.getInventory().getItemInMainHand().getType().isAir()) {
            player.sendActionBar(Component.text("Use an empty hand to pick up Halls items.", NamedTextColor.RED));
            return true;
        }
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack held = player.getInventory().getItem(slot);
        if (held != null && !held.getType().isAir()) {
            player.sendActionBar(Component.text("Your hotbar is full.", NamedTextColor.RED));
            return true;
        }
        player.getInventory().setItem(slot, drop.stack().clone());
        removePhysicsDrop(drop);
        world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.4f);
        return true;
    }

    public boolean handlePlayerDroppedItem(Player player, org.bukkit.entity.Item itemDrop) {
        if (player == null || itemDrop == null || !running || !player.getWorld().equals(world)) {
            return false;
        }
        if (ghostPlayers.contains(player.getUniqueId())) {
            itemDrop.remove();
            return true;
        }
        ItemStack stack = itemDrop.getItemStack();
        if (stack == null || stack.getType().isAir() || isLockedSlotItem(plugin, stack)) {
            return false;
        }
        Location location = itemDrop.getLocation();
        Vector velocity = itemDrop.getVelocity();
        itemDrop.remove();
        dropSessionItem(location, stack, velocity);
        return true;
    }

    public boolean handleBreakableAttack(Player player, Entity entity) {
        BreakableProp prop = entity == null ? null : breakableProps.get(entity.getUniqueId());
        if (prop == null) {
            return false;
        }
        prop.damage();
        Location location = entity.getLocation();
        world.playSound(location, Sound.BLOCK_BARREL_CLOSE, 0.7f, 1.25f);
        world.spawnParticle(Particle.BLOCK, location.clone().add(0.0, 0.75, 0.0), 12, 0.25, 0.25, 0.25,
                prop.material().createBlockData());
        if (prop.health() <= 0) {
            breakBreakableProp(prop);
            if (player != null) {
                player.sendMessage(Component.text(prop.breakMessage(), NamedTextColor.GRAY));
            }
        }
        return true;
    }

    public boolean handleElevatorButton(Player player, Block block) {
        if (!running || player == null || block == null || !player.getWorld().equals(world)) {
            return false;
        }
        if (ghostPlayers.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("Ghosts cannot operate the elevator.", NamedTextColor.GRAY));
            return true;
        }
        if (block.getX() != origin.x() - ELEVATOR_INNER_RADIUS
                || block.getY() != origin.y() + 1
                || block.getZ() != origin.z()) {
            return false;
        }
        if (transitioning) {
            player.sendMessage(Component.text("The elevator is already moving.", NamedTextColor.YELLOW));
        } else if (currentFloor < scenario.floorCount()) {
            int quota = currentCoinQuota();
            if (player.getGameMode() != GameMode.CREATIVE) {
                if (coins < quota) {
                    player.sendMessage(Component.text("The elevator needs " + quota + " coins. Current: " + coins + ".",
                            NamedTextColor.YELLOW));
                    return true;
                }
                coins = Math.max(0, coins - quota);
            }
            boolean leftBehind = markLeftBehindPlayersAsGhosts();
            closeOpenElevatorChestViewers();
            captureElevatorChestContents();
            removeElevatorCompasses();
            elevatorChestSnapshotLocked = true;
            save("floor-leave");
            transitioning = true;
            openElevatorDoors();
            world.playSound(block.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.9f, 0.7f);
            player.sendMessage(Component.text("The elevator begins its descent.", NamedTextColor.DARK_RED));
            startElevatorTransition(nextFloorAfterCampDrill(), leftBehind);
        } else {
            player.sendMessage(Component.text("No deeper placeholder floor is available.", NamedTextColor.YELLOW));
        }
        return true;
    }

    public boolean handleElevatorChestInteract(Player player, Block block) {
        if (!running || player == null || block == null || !player.getWorld().equals(world)) {
            return false;
        }
        if (block.getX() != origin.x() - ELEVATOR_INNER_RADIUS
                || block.getY() != origin.y()
                || block.getZ() != origin.z()) {
            return false;
        }
        if (transitioning) {
            player.sendActionBar(Component.text("The transfer chest is locked while the elevator is moving.", NamedTextColor.YELLOW));
            return true;
        }
        return false;
    }

    public boolean forceBuildFloor(int floor) {
        if (!running || transitioning || floor < 1 || floor > scenario.floorCount()) {
            return false;
        }
        if (floor == 1) {
            captureElevatorChestContents();
            removeSessionEntities();
            try {
                buildStartArea();
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to force rebuild Halls start floor for session " + id + ": " + ex.getMessage());
                return false;
            }
            restoreElevatorChestContents();
            openElevatorDoors();
            teleportParticipantsToElevator("Floor 1", "Reset to the start floor.");
            return true;
        }
        buildFloor(floor);
        openElevatorDoors();
        return true;
    }

    public boolean handleScrapDeposit(Player player, Block block) {
        if (!running || player == null || block == null || !player.getWorld().equals(world)) {
            return false;
        }
        if (ghostPlayers.contains(player.getUniqueId())) {
            player.sendActionBar(Component.text("Ghosts cannot deposit scrap.", NamedTextColor.GRAY));
            return true;
        }
        if (block.getX() != origin.x() - ELEVATOR_INNER_RADIUS
                || block.getY() != origin.y() + 2
                || block.getZ() != origin.z()) {
            return false;
        }
        int deposited = depositSelectedScrap(player.getInventory());
        if (deposited <= 0) {
            player.sendActionBar(Component.text("No scrap to deposit.", NamedTextColor.GRAY));
            return true;
        }
        coins += multipliedCoins(deposited);
        world.playSound(block.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.7f);
        monsterRuntime.alert(block.getLocation());
        player.sendActionBar(Component.text("Deposited " + deposited + " scrap.", NamedTextColor.GOLD));
        return true;
    }

    public void handlePlayerJoin(Player player) {
        if (player == null || !running || !participants.contains(player.getUniqueId())) {
            return;
        }
        setPlayerElevatorRespawn(player);
        applyInventoryLimit(player);
        if (ghostPlayers.contains(player.getUniqueId())) {
            applyGhostState(player);
        }
        reapplyActiveTotemBuffs(player);
    }

    public void handlePlayerQuit(Player player) {
        if (player == null || !participants.contains(player.getUniqueId())) {
            return;
        }
        clearTotemAttributeModifiers(player);
    }

    public void pushOutOfSessionProps(Player player) {
        if (player == null || !player.getWorld().equals(world)) {
            return;
        }
        Location playerLocation = player.getLocation();
        for (BreakableProp prop : Set.copyOf(breakableProps.values())) {
            Entity interaction = Bukkit.getEntity(prop.interactionId());
            if (interaction == null) {
                continue;
            }
            Location center = interaction.getLocation();
            double vertical = playerLocation.getY() - center.getY();
            if (vertical < -0.25 || vertical > 1.35) {
                continue;
            }
            double dx = playerLocation.getX() - center.getX();
            double dz = playerLocation.getZ() - center.getZ();
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared <= 0.0001 || distanceSquared > 0.64) {
                continue;
            }
            double distance = Math.sqrt(distanceSquared);
            Vector velocity = player.getVelocity();
            velocity.setX((dx / distance) * 0.28);
            velocity.setZ((dz / distance) * 0.28);
            player.setVelocity(velocity);
        }
    }

    public boolean handlePlayerMove(Player player) {
        return trapRuntime.handlePlayerMove(player, running);
    }

    public boolean handlePlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !running
                || !participants.contains(player.getUniqueId())
                || !player.getWorld().equals(world)) {
            return false;
        }
        if (ghostPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return true;
        }
        applyArmorHitEffects(player);
        if (activeFloorModifiers.armorDamageMultiplier() != 1.0 && hasHallsArmor(player)) {
            event.setDamage(event.getDamage() * Math.max(0.0, activeFloorModifiers.armorDamageMultiplier()));
        }
        if (player.getHealth() - event.getFinalDamage() > 0.0) {
            return false;
        }
        event.setCancelled(true);
        makeGhost(player);
        return true;
    }

    public boolean handleFriendlyFire(EntityDamageByEntityEvent event) {
        if (event == null || !(event.getEntity() instanceof Player target) || !participants.contains(target.getUniqueId())) {
            return false;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || !participants.contains(attacker.getUniqueId()) || !attacker.getWorld().equals(world)) {
            return false;
        }
        event.setCancelled(true);
        attacker.sendActionBar(Component.text("Friendly fire is disabled in Halls.", NamedTextColor.GRAY));
        return true;
    }

    public boolean handleWeaponHit(Player player, Entity target, EntityDamageByEntityEvent event) {
        if (player == null || target == null || !running || !participants.contains(player.getUniqueId())
                || !player.getWorld().equals(world) || ghostPlayers.contains(player.getUniqueId())
                || !(target instanceof LivingEntity living) || !monsterRuntime.isSessionMonster(living)) {
            return false;
        }
        if (event != null && activeFloorModifiers.meleeDamageMultiplier() != 1.0) {
            event.setDamage(event.getDamage() * Math.max(0.0, activeFloorModifiers.meleeDamageMultiplier()));
        }
        HallsItemType type = itemType(player.getInventory().getItemInMainHand());
        if (type == null || !type.id().equals("sculk_maul")) {
            return false;
        }
        if (sculkMaulSplashing.contains(player.getUniqueId()) || !canTriggerSculkMaulSplash(player, living)) {
            return false;
        }
        double radius = Math.max(0.0, type.stats().getOrDefault("aoe_radius", 0.0));
        double damage = Math.max(0.0, type.stats().getOrDefault("aoe_damage", 0.0));
        if (radius <= 0.0 || damage <= 0.0) {
            return false;
        }
        Location center = living.getLocation();
        int hits = 0;
        sculkMaulSplashing.add(player.getUniqueId());
        try {
            for (Entity nearby : world.getNearbyEntities(center, radius, radius, radius)) {
                if (!(nearby instanceof LivingEntity nearbyLiving)
                        || nearbyLiving.getUniqueId().equals(living.getUniqueId())
                        || !monsterRuntime.isSessionMonster(nearbyLiving)
                        || nearbyLiving.getLocation().distanceSquared(center) > radius * radius) {
                    continue;
                }
                nearbyLiving.damage(Math.min(damage, Math.max(0.0, nearbyLiving.getHealth() - 0.5)), player);
                hits++;
            }
        } finally {
            sculkMaulSplashing.remove(player.getUniqueId());
        }
        if (hits > 0) {
            world.spawnParticle(Particle.SCULK_SOUL, center.clone().add(0.0, 0.8, 0.0),
                    8, radius * 0.16, 0.25, radius * 0.16, 0.01);
            world.playSound(center, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.18f, 0.75f);
        }
        return hits > 0;
    }

    public boolean handleItemDamage(PlayerItemDamageEvent event) {
        if (event == null || !running || !participants.contains(event.getPlayer().getUniqueId())
                || !event.getPlayer().getWorld().equals(world)) {
            return false;
        }
        HallsItemType type = itemType(event.getItem());
        if (type == null || !type.category().equals("weapon")) {
            return false;
        }
        double multiplier = activeFloorModifiers.weaponDurabilityLossMultiplier();
        if (multiplier <= 1.0) {
            return false;
        }
        event.setDamage(Math.max(1, (int) Math.ceil(event.getDamage() * multiplier)));
        return true;
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.projectiles.ProjectileSource sourceHolder
                && sourceHolder instanceof Player player) {
            return player;
        }
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean hasHallsArmor(Player player) {
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            HallsItemType type = itemType(armor);
            if (type != null && type.category().equals("armor")) {
                return true;
            }
        }
        return false;
    }

    private boolean canTriggerSculkMaulSplash(Player player, LivingEntity target) {
        String key = player.getUniqueId() + ":" + target.getUniqueId();
        long now = System.currentTimeMillis();
        long nextAllowed = sculkMaulSplashCooldowns.getOrDefault(key, 0L);
        if (nextAllowed > now) {
            return false;
        }
        sculkMaulSplashCooldowns.put(key, now + SCULK_MAUL_SPLASH_COOLDOWN_MILLIS);
        if (sculkMaulSplashCooldowns.size() > 256) {
            sculkMaulSplashCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        return true;
    }

    private void applyArmorHitEffects(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        HallsItemType type = itemType(chestplate);
        if (type == null || !type.id().equals("cinderplate")) {
            return;
        }
        int durationTicks = (int) Math.round(type.stats().getOrDefault("resistance_seconds", 0.0) * 20.0);
        if (durationTicks <= 0) {
            return;
        }
        int amplifier = Math.max(0, (int) Math.round(type.stats().getOrDefault("resistance_amplifier", 1.0)) - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, amplifier, true, true, true));
    }

    public boolean blocksEating(Player player) {
        return player != null && (ghostPlayers.contains(player.getUniqueId()) || sculkRuntime.blocksEating(player));
    }

    public int forcedFoodLevel(Player player) {
        return 20;
    }

    public boolean handleItemConsume(Player player, ItemStack item) {
        return eatHallsFood(player, item, false);
    }

    public boolean handleFoodUse(Player player, ItemStack item) {
        return eatHallsFood(player, item, true);
    }

    private boolean eatHallsFood(Player player, ItemStack item, boolean consumeHeld) {
        if (player == null || item == null || !running || !participants.contains(player.getUniqueId())
                || !player.getWorld().equals(world)) {
            return false;
        }
        if (ghostPlayers.contains(player.getUniqueId())) {
            return false;
        }
        HallsItemType type = itemType(item);
        if (type == null || !type.category().equals("food")) {
            return false;
        }
        double heal = Math.max(0.0, type.stats().getOrDefault("heal", 0.0)
                * activeFloorModifiers.foodHealMultiplier());
        if (heal <= 0.0) {
            return true;
        }
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? player.getMaxHealth()
                : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        double nextHealth = Math.min(maxHealth, player.getHealth() + heal);
        player.setHealth(nextHealth);
        applyFoodBuffs(player, type);
        if (consumeHeld) {
            consumeOneHeldItem(player);
        }
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1.25f);
        player.sendActionBar(Component.text("Restored " + formatStatAmount(heal) + " health.", NamedTextColor.GREEN));
        return true;
    }

    private void applyFoodBuffs(Player player, HallsItemType type) {
        applyFoodBuff(player, type, "speed", PotionEffectType.SPEED);
        applyFoodBuff(player, type, "resistance", PotionEffectType.RESISTANCE);
        applyFoodBuff(player, type, "regeneration", PotionEffectType.REGENERATION);
        applyFoodBuff(player, type, "absorption", PotionEffectType.ABSORPTION);
    }

    private void applyFoodBuff(Player player, HallsItemType type, String key, PotionEffectType effectType) {
        int durationTicks = (int) Math.round(type.stats().getOrDefault(key + "_seconds", 0.0) * 20.0);
        if (durationTicks <= 0) {
            return;
        }
        int amplifier = Math.max(0, (int) Math.round(type.stats().getOrDefault(key + "_amplifier", 1.0)) - 1);
        player.addPotionEffect(new PotionEffect(effectType, durationTicks, amplifier, true, true, true));
    }

    public boolean handleUtilityUse(Player player, ItemStack item) {
        if (player == null || item == null || !running || !participants.contains(player.getUniqueId())
                || !player.getWorld().equals(world) || ghostPlayers.contains(player.getUniqueId())) {
            return false;
        }
        HallsItemType type = itemType(item);
        if (type == null || !type.category().equals("utility")) {
            return false;
        }
        return switch (type.id()) {
            case "smoke_bomb" -> {
                if (isUtilityOnCooldown(player, type)) {
                    yield true;
                }
                activateSmokeBomb(player, type);
                applyUtilityCooldown(player, item, type);
                yield true;
            }
            case "warding_totem" -> {
                if (isUtilityOnCooldown(player, type)) {
                    yield true;
                }
                activateWardingTotem(player, type);
                applyUtilityCooldown(player, item, type);
                yield true;
            }
            case "mending_salve" -> {
                if (isUtilityOnCooldown(player, type)) {
                    yield true;
                }
                if (activateHealingUtility(player, type)) {
                    applyUtilityCooldown(player, item, type);
                }
                yield true;
            }
            case "adrenaline_shot" -> {
                if (isUtilityOnCooldown(player, type)) {
                    yield true;
                }
                activateSelfBuffUtility(player, type, PotionEffectType.SPEED, "speed", "Adrenaline floods your legs.", Sound.ENTITY_RABBIT_JUMP);
                applyUtilityCooldown(player, item, type);
                yield true;
            }
            case "ironhide_salve" -> {
                if (isUtilityOnCooldown(player, type)) {
                    yield true;
                }
                activateSelfBuffUtility(player, type, PotionEffectType.RESISTANCE, "resistance", "Ironhide seals your skin.", Sound.BLOCK_ANVIL_USE);
                applyUtilityCooldown(player, item, type);
                yield true;
            }
            case "storm_vial" -> {
                if (isUtilityOnCooldown(player, type)) {
                    yield true;
                }
                activateMonsterPulseUtility(player, type, Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                        "The vial bursts into chained sparks.");
                applyUtilityCooldown(player, item, type);
                yield true;
            }
            case "poison_bomb" -> {
                if (isUtilityOnCooldown(player, type)) {
                    yield true;
                }
                activatePoisonBomb(player, type);
                applyUtilityCooldown(player, item, type);
                yield true;
            }
            default -> false;
        };
    }

    public void start() throws IOException {
        if (running) {
            return;
        }
        if (initialSave == null) {
            resetRunState();
            buildStartArea();
        } else {
            restoreSavedSessionState(initialSave);
            int floor = Math.max(1, Math.min(scenario.floorCount(), initialSave.currentFloor()));
            if (floor == 1) {
                buildStartArea();
                restoreElevatorChestContents();
            } else {
                buildFloor(floor);
            }
        }
        openElevatorDoors();
        running = true;
        startedAtMillis = System.currentTimeMillis();
        floorStartedAtMillis = startedAtMillis;
        startHudTask();
        Location spawn = startRoomSpawn == null ? elevatorSpawnLocation() : startRoomSpawn;
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.setGameMode(GameMode.ADVENTURE);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            setPlayerElevatorRespawn(player);
            player.getInventory().clear();
            applyInventoryLimit(player);
            if (initialSave == null) {
                giveStarterItem(player);
            } else {
                HallsSaveData.PlayerState state = initialSave.players().get(playerId);
                restoreSavedPlayer(player, state);
                restoreSavedTotemBuffs(player, state);
                if (state != null && state.ghost()) {
                    ghostPlayers.add(playerId);
                    applyGhostState(player);
                }
            }
            teleportSessionPlayer(player, spawn);
            player.sendTitle("Entering " + scenario.name(), "Floor " + currentFloor, 0, 45, 15);
            player.sendMessage(Component.text("Entering " + scenario.name() + " floor " + currentFloor + ".", NamedTextColor.DARK_RED));
        }
        save("campaign-start");
    }

    public void stop(Location fallback) {
        if (!running && snapshots.isEmpty()) {
            return;
        }
        cancelFloorBuildTask();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && fallback != null && player.getWorld().equals(world)) {
                restoreInventoryLimit(player);
                player.getInventory().clear();
                player.setRespawnLocation(fallback, true);
                player.setInvisible(false);
                player.setFlying(false);
                player.setAllowFlight(false);
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                clearTotemBuffs(player);
                player.teleport(fallback);
                player.setFallDistance(0.0f);
                player.sendTitle("Leaving the Halls", "", 0, 35, 10);
            } else if (player != null) {
                restoreInventoryLimit(player);
                player.getInventory().clear();
                player.setInvisible(false);
                player.setFlying(false);
                player.setAllowFlight(false);
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                clearTotemBuffs(player);
                if (fallback != null) {
                    player.setRespawnLocation(fallback, true);
                }
            }
        }
        restoreBlocks();
        removeSessionEntities();
        stopHudTask();
        cancelGameOverTask();
        running = false;
    }

    private void buildStartArea() throws IOException {
        currentFloor = 1;
        activeFloorModifiers = HallsFloorModifiers.none();
        floorStartedAtMillis = System.currentTimeMillis();
        HallsScenario.FloorDefinition floorDefinition = scenario.floor(1);
        HallsLevelType levelType = levelTypeFor(floorDefinition);
        activeLevelTypeId = levelType.id();
        activeTargetRooms = 1;
        activeGeneratedRooms = 1;
        clearBuildVolume();
        activeClearRadius = CLEAR_RADIUS;
        clearBuildVolume();
        buildElevator();
        HallsLayout layout = HallsLayoutLoader.load(new File(dataFolder, "level/special/start_floor.txt"));
        int roomStartX = origin.x() - layout.width() / 2;
        int roomStartZ = origin.z() + ELEVATOR_OUTER_RADIUS + 6;
        buildLayoutRoom(layout, roomStartX, origin.y(), roomStartZ,
                Map.of(BlockFace.NORTH, layout.width() / 2), levelType, new Random((((long) id) << 32) ^ 1));
        buildConnector(origin.x(), origin.y(), origin.z() + ELEVATOR_OUTER_RADIUS + 1, roomStartZ - 1, levelType);
        startRoomSpawn = new Location(world, roomStartX + layout.width() / 2.0 + 0.5,
                origin.y() + 1.0, roomStartZ + layout.depth() / 2.0 + 0.5, 0.0f, 0.0f);
        Cell blueprintCell = firstOpenStartFloorCell(layout);
        spawnBreakableProp(roomStartX + blueprintCell.x(), origin.y(), roomStartZ + blueprintCell.z(),
                breakableType("barrel"), 3, List.of(new HallsBreakableType.LootEntry("rare_blueprint", 1, 1, 1)), 1);
        closeElevatorDoors();
    }

    private Cell firstOpenStartFloorCell(HallsLayout layout) {
        for (int z = 0; z < layout.depth(); z++) {
            for (int x = 0; x < layout.width(); x++) {
                if (layout.at(x, z) == 'O') {
                    return new Cell(x, z);
                }
            }
        }
        return new Cell(layout.width() / 2, layout.depth() / 2);
    }

    private void startElevatorTransition(int destinationFloor, boolean delayModifierReveal) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) {
                return;
            }
            closeElevatorDoors();
            world.playSound(new Location(world, origin.x() + 0.5, origin.y() + 1.0, origin.z() + 0.5),
                    Sound.BLOCK_IRON_DOOR_CLOSE, 0.9f, 0.8f);
            for (UUID playerId : participants) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.getWorld().equals(world)) {
                    player.sendTitle("Descending", "The halls rearrange below.", 10, 60, 10);
                }
            }
        }, 8L);
        long buildDelay = delayModifierReveal ? 20L + LEFT_BEHIND_MODIFIER_DELAY_TICKS : 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) {
                return;
            }
            startStagedFloorBuild(destinationFloor);
        }, buildDelay);
    }

    private void buildFloor(int floor) {
        HallsScenario.FloorDefinition definition = scenario.floor(floor);
        if ("camp".equalsIgnoreCase(definition.kind())) {
            buildCampFloor(floor);
            return;
        }
        buildExplorationFloor(floor);
    }

    private void buildExplorationFloor(int floor) {
        captureCurrentCampState();
        captureElevatorChestContents();
        removeSessionEntities();
        activeClearRadius = clearRadiusFor(scenario.floor(floor));
        ExplorationBuild build = planExplorationBuild(floor);
        clearBuildVolume();
        buildElevator();
        currentFloor = floor;
        floorStartedAtMillis = System.currentTimeMillis();
        renderExplorationRooms(build);
        renderExplorationCorridors(build);
        Set<HallsExplorationGenerator.Cell> reservedCells = renderExplorationTraps(build);
        reservedCells = withReserved(reservedCells, renderExplorationLiquids(build, reservedCells));
        reservedCells = withReserved(reservedCells, renderExplorationVegetation(build, reservedCells));
        renderExplorationSculk(build, reservedCells);
        renderExplorationContents(build, reservedCells);
        startExplorationMonsters(build);
        restoreElevatorChestContents();
        closeElevatorDoors();
        teleportParticipantsToElevator("Floor " + floor, "Gather what you can.");
        applyCompassModifier();
    }

    private void buildCampFloor(int floor) {
        captureCurrentCampState();
        captureElevatorChestContents();
        removeSessionEntities();
        HallsScenario.FloorDefinition floorDefinition = scenario.floor(floor);
        HallsLevelType levelType = levelTypeFor(floorDefinition);
        activeLevelTypeId = levelType.id();
        activeTargetRooms = 1;
        activeGeneratedRooms = 1;
        activeFloorModifiers = HallsFloorModifiers.none();
        activeClearRadius = CLEAR_RADIUS;
        HallsCampLayout layout;
        try {
            layout = HallsCampFloorBuilder.load(dataFolder, floorDefinition);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to load Halls camp layout for session " + id + ": " + ex.getMessage());
            layout = new HallsCampLayout(List.of("OOOOO", "OCCCO", "OCNCO", "OCCCO", "OOOOO"), 5, 5,
                    List.of(new HallsCampLayout.BuildSpot(1, 1, 3, 1, 3, 2, 2, BlockFace.NORTH, "medium")));
        }
        activeClearRadius = Math.max(CLEAR_RADIUS, 16 + Math.max(layout.width(), layout.depth()));
        clearBuildVolume();
        buildElevator();
        currentFloor = floor;
        floorStartedAtMillis = System.currentTimeMillis();
        int roomStartX = origin.x() - layout.width() / 2;
        int roomStartZ = origin.z() + ELEVATOR_OUTER_RADIUS + 10;
        int entranceX = nearestCampEntranceX(layout, origin.x() - roomStartX);
        new HallsCampFloorBuilder(this::setBlock, campRuntime).build(layout, roomStartX, origin.y(), roomStartZ,
                levelType, entranceX);
        campRuntime.restore(savedCampStates.get(floor));
        buildCampConnector(roomStartX + entranceX, origin.y(), roomStartZ - 1, levelType);
        restoreElevatorChestContents();
        closeElevatorDoors();
        teleportParticipantsToElevator("Camp Floor " + floor, "Build, upgrade, and regroup.");
        save("camp-floor");
    }

    private int nearestCampEntranceX(HallsCampLayout layout, int targetX) {
        int bestX = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = 0; x < layout.width(); x++) {
            if (!layout.openAt(x, 0)) {
                continue;
            }
            int distance = Math.abs(x - targetX);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestX = x;
            }
        }
        return bestX >= 0 ? bestX : layout.width() / 2;
    }

    private void buildCampConnector(int targetX, int y, int targetZ, HallsLevelType levelType) {
        int startZ = origin.z() + ELEVATOR_OUTER_RADIUS + 1;
        int turnZ = Math.min(targetZ, startZ + 2);
        buildConnector(origin.x(), y, startZ, turnZ, levelType, 1);
        for (int x = Math.min(origin.x(), targetX); x <= Math.max(origin.x(), targetX); x++) {
            buildCorridorCell(x, y, turnZ, 1, false, levelType,
                    new Random((((long) x) << 32) ^ turnZ ^ 0xCA6FL));
        }
        buildConnector(targetX, y, turnZ, targetZ, levelType, 1);
    }

    private void startStagedFloorBuild(int floor) {
        cancelFloorBuildTask();
        HallsScenario.FloorDefinition definition = scenario.floor(floor);
        if ("camp".equalsIgnoreCase(definition.kind())) {
            buildCampFloor(floor);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!running) {
                    return;
                }
                openElevatorDoors();
                transitioning = false;
                world.playSound(new Location(world, origin.x() + 0.5, origin.y() + 1.0, origin.z() + 0.5),
                        Sound.BLOCK_IRON_DOOR_OPEN, 0.9f, 0.8f);
            }, MIN_ELEVATOR_TRANSITION_TICKS);
            return;
        }
        int oldClearRadius = activeClearRadius;
        activeClearRadius = clearRadiusFor(scenario.floor(floor));
        FloorBuildJob job = new FloorBuildJob(floor, Math.max(oldClearRadius, activeClearRadius));
        floorBuildTask = Bukkit.getScheduler().runTaskTimer(plugin, job::tick, 1L, 1L);
    }

    private void teleportParticipantsToElevator(String title, String subtitle) {
        Location spawn = elevatorSpawnLocation();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                boolean wasGhost = ghostPlayers.contains(playerId);
                setPlayerElevatorRespawn(player);
                clearGhostState(player);
                healForElevatorArrival(player, wasGhost);
                if (!isInsideElevator(player.getLocation())) {
                    teleportSessionPlayer(player, spawn);
                }
                player.sendTitle(title, subtitle, 10, 45, 15);
            }
        }
    }

    private void teleportSessionPlayer(Player player, Location target) {
        player.teleport(target);
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        setPlayerElevatorRespawn(player);
    }

    private Location elevatorSpawnLocation() {
        return new Location(world, origin.x() + 0.5, origin.y() + 1.0, origin.z() + 0.5, 180.0f, 0.0f);
    }

    private void setPlayerElevatorRespawn(Player player) {
        if (player != null) {
            player.setRespawnLocation(elevatorSpawnLocation(), true);
        }
    }

    private boolean isInsideElevator(Location location) {
        if (location == null || !world.equals(location.getWorld())) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= origin.x() - ELEVATOR_INNER_RADIUS
                && x <= origin.x() + ELEVATOR_INNER_RADIUS
                && z >= origin.z() - ELEVATOR_INNER_RADIUS
                && z <= origin.z() + ELEVATOR_INNER_RADIUS
                && y >= origin.y()
                && y <= origin.y() + 3;
    }

    private boolean markLeftBehindPlayersAsGhosts() {
        if (participants.size() <= 1) {
            return false;
        }
        boolean leftBehind = false;
        for (UUID playerId : participants) {
            if (ghostPlayers.contains(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.getWorld().equals(world) || isInsideElevator(player.getLocation())) {
                continue;
            }
            ghostPlayers.add(playerId);
            dropPlayerSessionInventory(player);
            applyGhostState(player);
            player.setHealth(1.0);
            player.sendTitle("Left Behind", "The elevator descended without you.", 10, 70, 20);
            player.sendMessage(Component.text("You were left behind and became a ghost.", NamedTextColor.DARK_RED));
            world.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.7f, 0.6f);
            leftBehind = true;
        }
        return leftBehind;
    }

    private ExplorationBuild planExplorationBuild(int floor) {
        long started = System.nanoTime();
        HallsScenario.FloorDefinition rawFloorDefinition = adjustedDifficulty(scenario.floor(floor));
        HallsLevelType levelType = levelTypeFor(rawFloorDefinition);
        Random random = floorRandom();
        activeFloorModifiers = scannedFloorModifiers.remove(floor);
        if (activeFloorModifiers == null) {
            activeFloorModifiers = selectFloorModifiers(rawFloorDefinition, levelType, random, true);
        } else {
            revealFloorModifiers(activeFloorModifiers);
        }
        HallsScenario.FloorDefinition floorDefinition = activeFloorModifiers.adjustFloor(rawFloorDefinition, random);
        debugModifierAdjustments(rawFloorDefinition, floorDefinition, activeFloorModifiers, levelType);
        activeLevelTypeId = levelType.id();
        activeTargetRooms = Math.max(1, floorDefinition.rooms());
        activeGeneratedRooms = 0;
        List<HallsLayout> layouts = loadExplorationLayouts(levelType);
        HallsExplorationGenerator.Plan plan = HallsExplorationGenerator.generate(
                origin.x(),
                origin.z(),
                activeClearRadius,
                ELEVATOR_OUTER_RADIUS,
                layouts,
                floorDefinition,
                levelType.corridorGeneration(),
                activeFloorModifiers.corridorDistanceMultiplier(levelType.corridorGeneration()),
                random
        );
        int targetRooms = activeTargetRooms;
        int expansions = 0;
        while ((plan.rooms().size() < targetRooms || !plan.reachable()) && expansions++ < maxPlanningExpansions(levelType)) {
            activeClearRadius += 32;
            random = floorRandom();
            plan = HallsExplorationGenerator.generate(
                    origin.x(),
                    origin.z(),
                    activeClearRadius,
                    ELEVATOR_OUTER_RADIUS,
                    layouts,
                    floorDefinition,
                    levelType.corridorGeneration(),
                    activeFloorModifiers.corridorDistanceMultiplier(levelType.corridorGeneration()),
                    random
            );
        }
        if (plan.rooms().isEmpty()) {
            debugGeneration("plan", started, "rooms 0/" + activeTargetRooms + ", reachable " + plan.reachable());
            return new ExplorationBuild(floor, floorDefinition, levelType, random, plan);
        }
        activeGeneratedRooms = plan.rooms().size();
        debugGeneration("plan", started, "rooms " + activeGeneratedRooms + "/" + activeTargetRooms
                + ", corridors " + plan.corridorCells().size() + ", reachable " + plan.reachable());
        return new ExplorationBuild(floor, floorDefinition, levelType, random, plan);
    }

    private int maxPlanningExpansions(HallsLevelType levelType) {
        String mode = levelType.corridorGeneration();
        if (mode != null) {
            String normalized = mode.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
            if (normalized.equals("maze") || normalized.equals("backrooms")) {
                return 1;
            }
        }
        return 4;
    }

    private void debugModifierAdjustments(HallsScenario.FloorDefinition before,
                                          HallsScenario.FloorDefinition after,
                                          HallsFloorModifiers modifiers,
                                          HallsLevelType levelType) {
        if (before == null || after == null || modifiers == null || modifiers.empty()) {
            return;
        }
        String corridorMode = levelType == null ? "" : levelType.corridorGeneration();
        String details = "modifiers " + modifiers.displaySummary()
                + "; rooms " + before.rooms() + " -> " + after.rooms()
                + ", breakables " + before.breakables() + " -> " + after.breakables()
                + ", trapped rooms " + before.trappedRooms() + " -> " + after.trappedRooms()
                + ", traps/room " + before.minTrapsPerRoom() + "-" + before.maxTrapsPerRoom()
                + " -> " + after.minTrapsPerRoom() + "-" + after.maxTrapsPerRoom()
                + ", holes " + before.holes() + " -> " + after.holes()
                + ", sculk patches " + before.sculkPatches() + " -> " + after.sculkPatches()
                + ", coin quota " + before.coinQuota() + " -> " + after.coinQuota()
                + ", enemy x" + formatMultiplier(modifiers.enemySpawnMultiplier())
                + ", loot x" + formatMultiplier(modifiers.lootMultiplier())
                + ", traps x" + formatMultiplier(modifiers.trapMultiplier())
                + ", sculk x" + formatMultiplier(modifiers.sculkMultiplier())
                + ", coins x" + formatMultiplier(modifiers.coinMultiplier())
                + ", corridor distance x" + formatMultiplier(modifiers.corridorDistanceMultiplier(corridorMode));
        List<String> boostedTraps = modifiers.trapBoostKinds();
        if (!boostedTraps.isEmpty()) {
            details += ", boosted traps " + String.join(",", boostedTraps);
        }
        debug(details);
    }

    private String formatMultiplier(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private void debugGeneration(String phase, long startedNanos, String details) {
        long elapsedMillis = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        debug("Generation " + phase + " took " + elapsedMillis + "ms; " + details + ".");
    }

    private void debug(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Component component = Component.text("[HoC Debug] " + message, NamedTextColor.GRAY);
        for (UUID playerId : participants) {
            if (!debugEnabled.test(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world)) {
                player.sendMessage(component);
            }
        }
    }

    private void renderExplorationRooms(ExplorationBuild build) {
        if (build.plan().rooms().isEmpty()) {
            return;
        }
        for (HallsExplorationGenerator.Room room : build.plan().rooms()) {
            buildLayoutRoom(room.layout(), room.startX(), origin.y(), room.startZ(),
                    room.openings(), build.levelType(), build.random());
        }
    }

    private void renderExplorationCorridors(ExplorationBuild build) {
        if (!build.plan().rooms().isEmpty()) {
            for (HallsExplorationGenerator.Cell point : build.plan().corridorShellCells()) {
                buildGeneratedCorridorCell(build.plan(), build.levelType(), point);
            }
        }
    }

    private Set<HallsExplorationGenerator.Cell> renderExplorationTraps(ExplorationBuild build) {
        if (build.plan().rooms().isEmpty()) {
            return Set.of();
        }
        long started = System.nanoTime();
        Set<HallsExplorationGenerator.Cell> reserved = trapRuntime.placeGeneratedTraps(build.plan(), build.random(),
                build.floorDefinition(), build.levelType(), activeFloorModifiers);
        debugGeneration("traps", started, "traps " + trapRuntime.activeTrapCount() + ", reserved cells " + reserved.size());
        return reserved;
    }

    private void renderExplorationContents(ExplorationBuild build, Set<HallsExplorationGenerator.Cell> reservedCells) {
        long started = System.nanoTime();
        int before = new HashSet<>(breakableProps.values()).size();
        int rareRoomIndex = rareBreakableRoomIndex(build.plan(), reservedCells, build.random());
        for (int i = 0; i < build.plan().rooms().size(); i++) {
            placeGeneratedRoomContents(build.plan().rooms().get(i), build.random(), build.floor(), i,
                    build.floorDefinition(), build.levelType(), reservedCells, i == rareRoomIndex);
        }
        debugGeneration("contents", started, "breakables " + (new HashSet<>(breakableProps.values()).size() - before));
    }

    private Set<HallsExplorationGenerator.Cell> renderExplorationVegetation(ExplorationBuild build,
                                                                            Set<HallsExplorationGenerator.Cell> reservedCells) {
        if (build == null || build.plan().rooms().isEmpty() || vegetationTypes.isEmpty()
                || build.levelType().vegetationChance() <= 0.0 || build.levelType().vegetation().isEmpty()) {
            return Set.of();
        }
        long started = System.nanoTime();
        Set<HallsExplorationGenerator.Cell> occupied = new HashSet<>();
        placeVegetationInCells(build, vegetationRoomCells(build), reservedCells, occupied, 1.0);
        placeVegetationInCells(build, vegetationCorridorCells(build), reservedCells, occupied, 0.5);
        debugGeneration("vegetation", started, "displays " + occupied.size());
        return Set.copyOf(occupied);
    }

    private Set<HallsExplorationGenerator.Cell> renderExplorationLiquids(ExplorationBuild build,
                                                                         Set<HallsExplorationGenerator.Cell> reservedCells) {
        if (build == null || build.plan().rooms().isEmpty() || !build.levelType().liquid().enabled()) {
            return Set.of();
        }
        long started = System.nanoTime();
        Set<HallsExplorationGenerator.Cell> liquidCells = new HashSet<>(build.plan().liquidCells());
        for (HallsExplorationGenerator.Room room : build.plan().rooms()) {
            liquidCells.addAll(roomLiquidCells(build, room, reservedCells, liquidCells));
        }
        for (HallsExplorationGenerator.Cell cell : liquidCells) {
            renderLiquidCell(cell, build.levelType(), liquidCells);
        }
        debugGeneration("liquids", started, "cells " + liquidCells.size());
        return Set.copyOf(liquidCells);
    }

    private Set<HallsExplorationGenerator.Cell> roomLiquidCells(ExplorationBuild build,
                                                                HallsExplorationGenerator.Room room,
                                                                Set<HallsExplorationGenerator.Cell> reservedCells,
                                                                Set<HallsExplorationGenerator.Cell> existingLiquid) {
        List<HallsExplorationGenerator.Cell> candidates = openInteriorCells(room).stream()
                .map(cell -> new HallsExplorationGenerator.Cell(room.startX() + cell.x(), room.startZ() + cell.z()))
                .filter(cell -> canPlaceRoomLiquid(cell, build.plan(), reservedCells, existingLiquid))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (candidates.isEmpty()) {
            return Set.of();
        }
        int target = Math.max(1, (int) Math.round(candidates.size() * build.levelType().liquid().roomCoverage()));
        HallsExplorationGenerator.Cell start = candidates.get(build.random().nextInt(candidates.size()));
        Set<HallsExplorationGenerator.Cell> candidateSet = new HashSet<>(candidates);
        Set<HallsExplorationGenerator.Cell> result = new HashSet<>();
        java.util.ArrayDeque<HallsExplorationGenerator.Cell> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        result.add(start);
        while (!queue.isEmpty() && result.size() < target) {
            HallsExplorationGenerator.Cell current = queue.remove();
            List<HallsExplorationGenerator.Cell> nextCells = new ArrayList<>();
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                HallsExplorationGenerator.Cell next = new HallsExplorationGenerator.Cell(
                        current.x() + face.getModX(),
                        current.z() + face.getModZ());
                if (candidateSet.contains(next) && !result.contains(next)) {
                    nextCells.add(next);
                }
            }
            java.util.Collections.shuffle(nextCells, build.random());
            for (HallsExplorationGenerator.Cell next : nextCells) {
                result.add(next);
                queue.add(next);
                if (result.size() >= target) {
                    break;
                }
            }
        }
        return result;
    }

    private boolean canPlaceRoomLiquid(HallsExplorationGenerator.Cell cell,
                                       HallsExplorationGenerator.Plan plan,
                                       Set<HallsExplorationGenerator.Cell> reservedCells,
                                       Set<HallsExplorationGenerator.Cell> existingLiquid) {
        if (plan.corridorCells().contains(cell) || existingLiquid.contains(cell)
                || (reservedCells != null && reservedCells.contains(cell))) {
            return false;
        }
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            HallsExplorationGenerator.Cell side = new HallsExplorationGenerator.Cell(
                    cell.x() + face.getModX(),
                    cell.z() + face.getModZ());
            if (plan.corridorCells().contains(side) || (reservedCells != null && reservedCells.contains(side))) {
                return false;
            }
        }
        return true;
    }

    private void renderLiquidCell(HallsExplorationGenerator.Cell cell,
                                  HallsLevelType levelType,
                                  Set<HallsExplorationGenerator.Cell> liquidCells) {
        Material liquid = levelType.liquid().material();
        setBlock(cell.x(), origin.y() - 1, cell.z(), liquid);
        setBlock(cell.x(), origin.y() - 2, cell.z(), liquid);
        setBlock(cell.x(), origin.y() - 3, cell.z(), wallMaterial(levelType, cell.x(), origin.y() - 3, cell.z(), false, 0xA710));
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            HallsExplorationGenerator.Cell side = new HallsExplorationGenerator.Cell(
                    cell.x() + face.getModX(),
                    cell.z() + face.getModZ());
            if (liquidCells.contains(side)) {
                continue;
            }
            setBlock(side.x(), origin.y() - 2, side.z(),
                    wallMaterial(levelType, side.x(), origin.y() - 2, side.z(), false, 0xA710));
            setBlock(side.x(), origin.y() - 3, side.z(),
                    wallMaterial(levelType, side.x(), origin.y() - 3, side.z(), false, 0xA710));
        }
    }

    private void placeVegetationInCells(ExplorationBuild build,
                                        List<HallsExplorationGenerator.Cell> cells,
                                        Set<HallsExplorationGenerator.Cell> reservedCells,
                                        Set<HallsExplorationGenerator.Cell> occupied,
                                        double chanceMultiplier) {
        if (cells.isEmpty()) {
            return;
        }
        java.util.Collections.shuffle(cells, build.random());
        double chance = Math.max(0.0, Math.min(1.0, build.levelType().vegetationChance() * chanceMultiplier));
        for (HallsExplorationGenerator.Cell absolute : cells) {
            if (build.random().nextDouble() >= chance) {
                continue;
            }
            if ((reservedCells != null && reservedCells.contains(absolute)) || occupied.contains(absolute)) {
                continue;
            }
            HallsVegetationType type = weightedVegetation(build.levelType(), build.random());
            if (type == null) {
                continue;
            }
            spawnVegetationDisplay(absolute.x(), origin.y(), absolute.z(), type, build.random());
            occupied.add(absolute);
        }
    }

    private List<HallsExplorationGenerator.Cell> vegetationRoomCells(ExplorationBuild build) {
        List<HallsExplorationGenerator.Cell> cells = new ArrayList<>();
        for (HallsExplorationGenerator.Room room : build.plan().rooms()) {
            for (Cell cell : openInteriorCells(room)) {
                cells.add(new HallsExplorationGenerator.Cell(room.startX() + cell.x(), room.startZ() + cell.z()));
            }
        }
        return cells;
    }

    private List<HallsExplorationGenerator.Cell> vegetationCorridorCells(ExplorationBuild build) {
        return build.plan().corridorCells().stream()
                .filter(cell -> Math.abs(cell.x() - origin.x()) + Math.abs(cell.z() - origin.z()) > 12)
                .filter(cell -> !isInsideGeneratedRoomShell(cell, build.plan().rooms()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private Set<HallsExplorationGenerator.Cell> withReserved(Set<HallsExplorationGenerator.Cell> first,
                                                             Set<HallsExplorationGenerator.Cell> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return Set.of();
        }
        Set<HallsExplorationGenerator.Cell> result = new HashSet<>();
        if (first != null) {
            result.addAll(first);
        }
        if (second != null) {
            result.addAll(second);
        }
        return Set.copyOf(result);
    }

    private int rareBreakableRoomIndex(HallsExplorationGenerator.Plan plan,
                                       Set<HallsExplorationGenerator.Cell> reservedCells,
                                       Random random) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < plan.rooms().size(); i++) {
            HallsExplorationGenerator.Room room = plan.rooms().get(i);
            Set<HallsExplorationGenerator.Cell> usedCells = new HashSet<>();
            if (randomFreeContentCell(openInteriorCells(room), room, random, reservedCells, usedCells) != null) {
                candidates.add(i);
            }
        }
        return candidates.isEmpty() ? -1 : candidates.get(random.nextInt(candidates.size()));
    }

    private void startExplorationMonsters(ExplorationBuild build) {
        if (build == null || build.plan().rooms().isEmpty()) {
            monsterRuntime.clear();
            return;
        }
        debug("Starting monster runtime on floor " + build.floor() + ": " + build.levelType().id() + ".");
        monsterRuntime.startExplorationFloor(build.plan(), build.floorDefinition(), build.levelType(), activeFloorModifiers, build.random());
    }

    private void renderExplorationSculk(ExplorationBuild build, Set<HallsExplorationGenerator.Cell> reservedCells) {
        if (build == null || build.plan().rooms().isEmpty()) {
            sculkRuntime.clearFloor();
            return;
        }
        sculkRuntime.placePatches(build.plan(), build.floorDefinition(), build.random(), reservedCells);
    }

    private Random floorRandom() {
        long seed = java.util.concurrent.ThreadLocalRandom.current().nextLong()
                ^ System.nanoTime()
                ^ UUID.randomUUID().getMostSignificantBits()
                ^ UUID.randomUUID().getLeastSignificantBits();
        return new Random(seed);
    }

    private HallsFloorModifiers selectFloorModifiers(HallsScenario.FloorDefinition floorDefinition,
                                                     HallsLevelType levelType,
                                                     Random random,
                                                     boolean reveal) {
        if (floorDefinition == null || !"exploration".equalsIgnoreCase(floorDefinition.kind()) || modifierTypes.isEmpty()) {
            return HallsFloorModifiers.none();
        }
        int difficulty = parseDifficulty(floorDefinition.difficulty(), floorDefinition.firstFloor());
        int goodChance = Math.max(0, Math.min(100, 50 - difficulty));
        List<HallsModifierType> good = applicableModifiers(levelType, true);
        List<HallsModifierType> bad = applicableModifiers(levelType, false);
        List<HallsModifierType> selected = new ArrayList<>();
        for (int slot = 0; slot < 3; slot++) {
            boolean wantGood = random.nextInt(100) < goodChance;
            HallsModifierType modifier = weightedModifier(wantGood ? good : bad, random);
            if (modifier == null) {
                modifier = weightedModifier(wantGood ? bad : good, random);
            }
            if (modifier != null) {
                selected.add(modifier);
            }
        }
        HallsFloorModifiers modifiers = new HallsFloorModifiers(selected);
        if (reveal) {
            revealFloorModifiers(modifiers);
        }
        return modifiers;
    }

    private List<String> scanUpcomingFloors(int scannerLevel) {
        if (!isCurrentFloorCamp()) {
            return List.of("Scanner only works from camp floors.");
        }
        int remaining = Math.max(1, Math.min(3, scannerLevel));
        List<String> lines = new ArrayList<>();
        for (int floor = currentFloor + 1; floor <= scenario.floorCount() && lines.size() < remaining; floor++) {
            HallsScenario.FloorDefinition raw = adjustedDifficulty(scenario.floor(floor));
            if (raw == null || !"exploration".equalsIgnoreCase(raw.kind())) {
                continue;
            }
            HallsLevelType levelType = levelTypeFor(raw);
            HallsFloorModifiers modifiers = scannedFloorModifiers.computeIfAbsent(floor,
                    ignored -> selectFloorModifiers(raw, levelType, floorRandom(), false));
            lines.add("F" + floor + " " + levelType.name() + ": " + modifiers.displaySummary());
        }
        return lines.isEmpty() ? List.of("No upcoming exploration floors found.") : lines;
    }

    private List<HallsModifierType> applicableModifiers(HallsLevelType levelType, boolean good) {
        String levelTypeId = levelType == null ? "" : levelType.id();
        return modifierTypes.values().stream()
                .filter(modifier -> modifier.weight() > 0 && modifier.good() == good)
                .filter(modifier -> {
                    Object restricted = modifier.effects().get("level_type");
                    return restricted == null || normalizeId(String.valueOf(restricted)).equals(levelTypeId);
                })
                .sorted(Comparator.comparing(HallsModifierType::id))
                .toList();
    }

    private HallsModifierType weightedModifier(List<HallsModifierType> pool, Random random) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        int totalWeight = pool.stream().mapToInt(HallsModifierType::weight).sum();
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (HallsModifierType modifier : pool) {
            roll -= modifier.weight();
            if (roll < 0) {
                return modifier;
            }
        }
        return pool.getFirst();
    }

    private void revealFloorModifiers(HallsFloorModifiers modifiers) {
        if (modifiers == null || modifiers.empty()) {
            return;
        }
        List<HallsModifierType> selected = modifiers.selected();
        for (int i = 0; i < selected.size(); i++) {
            HallsModifierType modifier = selected.get(i);
            int delay = 24 + i * 32;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!running) {
                    return;
                }
                for (UUID playerId : participants) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.getWorld().equals(world)) {
                        player.sendTitle("Modifier", modifier.icon() + " " + modifier.displayName(), 0, 48, 16);
                        player.playSound(player.getLocation(),
                                modifier.good() ? Sound.BLOCK_NOTE_BLOCK_CHIME : Sound.BLOCK_NOTE_BLOCK_BASS,
                                0.8f,
                                modifier.good() ? 1.6f : 0.65f);
                    }
                }
            }, delay);
        }
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

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private List<HallsLayout> loadExplorationLayouts(HallsLevelType levelType) {
        File folder = new File(dataFolder, "level/" + levelType.id());
        File[] files = folder.listFiles((dir, name) -> name.startsWith("exploration_") && name.endsWith(".txt"));
        if (files == null || files.length == 0) {
            return List.of(fallbackExplorationLayout());
        }
        List<HallsLayout> layouts = new ArrayList<>();
        for (File file : java.util.Arrays.stream(files).sorted(Comparator.comparing(File::getName)).toList()) {
            try {
                addLayoutRotations(layouts, HallsLayoutLoader.load(file));
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to load Halls exploration template " + file + ": " + ex.getMessage());
            }
        }
        return layouts.isEmpty() ? List.of(fallbackExplorationLayout()) : layouts;
    }

    private void addLayoutRotations(List<HallsLayout> layouts, HallsLayout layout) {
        HallsLayout current = layout;
        for (int i = 0; i < 4; i++) {
            if (!layouts.contains(current)) {
                layouts.add(current);
            }
            current = rotateClockwise(current);
        }
    }

    private HallsLayout rotateClockwise(HallsLayout layout) {
        List<String> rows = new ArrayList<>();
        for (int z = 0; z < layout.width(); z++) {
            StringBuilder row = new StringBuilder();
            for (int x = layout.depth() - 1; x >= 0; x--) {
                row.append(layout.at(z, x));
            }
            rows.add(row.toString());
        }
        return new HallsLayout(rows, layout.depth(), layout.width());
    }

    private HallsLevelType levelTypeFor(HallsScenario.FloorDefinition floorDefinition) {
        if (floorDefinition == null) {
            return levelTypes.getOrDefault("howling_corridors", HallsLevelType.fallback("howling_corridors"));
        }
        return levelTypes.getOrDefault(floorDefinition.levelType(), HallsLevelType.fallback(floorDefinition.levelType()));
    }

    private HallsLevelType activeLevelType() {
        return levelTypes.getOrDefault(activeLevelTypeId, HallsLevelType.fallback(activeLevelTypeId));
    }

    private int clearRadiusFor(HallsScenario.FloorDefinition floorDefinition) {
        int rooms = Math.max(1, floorDefinition.rooms());
        return Math.max(CLEAR_RADIUS, 30 + (int) Math.ceil(Math.sqrt(rooms) * 14.0));
    }

    private HallsLayout fallbackExplorationLayout() {
        return new HallsLayout(List.of(
                "OOOOOOOOO",
                "OOOXOOOOO",
                "OOOXOOXOO",
                "OOOOOOXOO",
                "OXOOOOOOO",
                "OOOOOOOOO"
        ), 9, 6);
    }

    private void clearBuildVolume() {
        clearBuildVolumeColumns(origin.x() - activeClearRadius, origin.x() + activeClearRadius, activeClearRadius);
    }

    private void clearBuildVolumeColumns(int minX, int maxX, int radius) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = origin.y() - 16; y <= origin.y() + CLEAR_HEIGHT; y++) {
                for (int z = origin.z() - radius; z <= origin.z() + radius; z++) {
                    if (isProtectedElevatorTransferCell(x, z)) {
                        continue;
                    }
                    setBlock(x, y, z, Material.AIR);
                }
            }
        }
    }

    private void placeRoomCeilingLights(HallsLayout layout,
                                        int startX,
                                        int y,
                                        int startZ,
                                        HallsLevelType levelType,
                                        Random random) {
        List<Cell> cells = openInteriorCells(layout);
        if (cells.isEmpty()) {
            return;
        }
        java.util.Collections.shuffle(cells, random);
        int targetLights = Math.max(1, Math.min(4, cells.size() / 36));
        int placed = 0;
        for (Cell cell : cells) {
            if (placed >= targetLights) {
                return;
            }
            if (isNearLayoutEdge(layout, cell)) {
                continue;
            }
            setBlock(startX + cell.x(), y + ROOM_HEIGHT, startZ + cell.z(), levelType.light());
            placed++;
        }
        if (placed == 0) {
            Cell cell = cells.getFirst();
            setBlock(startX + cell.x(), y + ROOM_HEIGHT, startZ + cell.z(), levelType.light());
        }
    }

    private boolean isNearLayoutEdge(HallsLayout layout, Cell cell) {
        return cell.x() <= 1 || cell.z() <= 1
                || cell.x() >= layout.width() - 2
                || cell.z() >= layout.depth() - 2;
    }

    private void buildElevator() {
        Material corner = Material.REINFORCED_DEEPSLATE;
        Material side = Material.RED_NETHER_BRICKS;
        Material back = Material.DEEPSLATE_BRICKS;
        Material machine = Material.matchMaterial("CHISELED_TUFF_BRICKS") == null
                ? Material.TUFF_BRICKS
                : Material.matchMaterial("CHISELED_TUFF_BRICKS");
        Material door = firstMaterial("WAXED_WEATHERED_COPPER_BARS", "WAXED_WEATHERED_COPPER_GRATE", "COPPER_BARS", "IRON_BARS");
        Material floor = Material.PACKED_MUD;
        Material ceiling = Material.SMITHING_TABLE;

        for (int x = -ELEVATOR_OUTER_RADIUS; x <= ELEVATOR_OUTER_RADIUS; x++) {
            for (int z = -ELEVATOR_OUTER_RADIUS; z <= ELEVATOR_OUTER_RADIUS; z++) {
                setBlock(origin.x() + x, origin.y() - 1, origin.z() + z, floor);
                setBlock(origin.x() + x, origin.y() + 4, origin.z() + z, ceiling);
            }
        }
        setBlock(origin.x(), origin.y() + 4, origin.z(), Material.SEA_LANTERN);
        for (int x = -2; x <= 2; x++) {
            setBlock(origin.x() + x, origin.y() + 4, origin.z() + ELEVATOR_OUTER_RADIUS + 1, back);
        }
        for (int y = 0; y <= 3; y++) {
            for (int x = -ELEVATOR_OUTER_RADIUS; x <= ELEVATOR_OUTER_RADIUS; x++) {
                Material backMaterial = Math.abs(x) == ELEVATOR_OUTER_RADIUS ? corner : Math.abs(x) == 2 ? side : back;
                Material frontMaterial = Math.abs(x) <= 1 && y <= 2 ? door : Math.abs(x) == ELEVATOR_OUTER_RADIUS ? corner : Math.abs(x) == 2 ? side : back;
                setBlock(origin.x() + x, origin.y() + y, origin.z() - ELEVATOR_OUTER_RADIUS, backMaterial);
                setBlock(origin.x() + x, origin.y() + y, origin.z() + ELEVATOR_OUTER_RADIUS, frontMaterial,
                        frontMaterial == door ? BlockFace.EAST : null);
            }
            for (int z = -ELEVATOR_INNER_RADIUS; z <= ELEVATOR_INNER_RADIUS; z++) {
                Material sideWall = Math.abs(z) == ELEVATOR_INNER_RADIUS ? side : back;
                setBlock(origin.x() - ELEVATOR_OUTER_RADIUS, origin.y() + y, origin.z() + z, sideWall);
                setBlock(origin.x() + ELEVATOR_OUTER_RADIUS, origin.y() + y, origin.z() + z, sideWall);
            }
        }
        setBlock(origin.x() - ELEVATOR_INNER_RADIUS, origin.y(), origin.z(), Material.CHEST, BlockFace.EAST);
        setBlock(origin.x() - ELEVATOR_INNER_RADIUS, origin.y() + 1, origin.z(), Material.STONE_BUTTON, BlockFace.EAST);
        setBlock(origin.x() - ELEVATOR_INNER_RADIUS, origin.y() + 2, origin.z(), Material.HOPPER, BlockFace.WEST);
        setBlock(origin.x() - ELEVATOR_OUTER_RADIUS, origin.y(), origin.z(), machine);
        setBlock(origin.x() - ELEVATOR_OUTER_RADIUS, origin.y() + 1, origin.z(), machine);
        setBlock(origin.x() - ELEVATOR_OUTER_RADIUS, origin.y() + 2, origin.z(), machine);
        buildElevatorVestibule(true);
    }

    private void buildLayoutRoom(HallsLayout layout,
                                 int startX,
                                 int y,
                                 int startZ,
                                 Map<BlockFace, Integer> openings,
                                 HallsLevelType levelType,
                                 Random random) {
        Material floor = levelType.floor();
        Material ceiling = levelType.ceiling();
        for (int z = -1; z <= layout.depth(); z++) {
            for (int x = -1; x <= layout.width(); x++) {
                boolean border = x < 0 || z < 0 || x >= layout.width() || z >= layout.depth();
                boolean opening = border && isRoomOpening(layout, x, z, openings, levelType);
                char cell = border ? 'X' : layout.at(x, z);
                int blockX = startX + x;
                int blockZ = startZ + z;
                boolean wallColumn = !opening && (border || cell == 'X');
                setBlock(blockX, y - 1, blockZ, wallColumn
                        ? roomWallMaterial(levelType, layout, x, z, blockX, y - 1, blockZ)
                        : floor);
                setBlock(blockX, y + ROOM_HEIGHT, blockZ, ceiling);
                if (wallColumn) {
                    for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                        setBlock(blockX, y + dy, blockZ,
                                roomWallMaterial(levelType, layout, x, z, blockX, y + dy, blockZ));
                    }
                } else {
                    for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                        setBlock(blockX, y + dy, blockZ, opening && dy >= 3
                                ? roomWallMaterial(levelType, layout, x, z, blockX, y + dy, blockZ)
                                : Material.AIR);
                    }
                }
            }
        }
        placeRoomCeilingLights(layout, startX, y, startZ, levelType, random);
    }

    private boolean isRoomOpening(HallsLayout layout,
                                  int x,
                                  int z,
                                  Map<BlockFace, Integer> openings,
                                  HallsLevelType levelType) {
        int centerX = layout.width() / 2;
        int centerZ = layout.depth() / 2;
        int halfWidth = wideRoomOpenings(levelType) ? 1 : 0;
        if (z == -1) {
            return Math.abs(x - openings.getOrDefault(BlockFace.NORTH, centerX + 1000)) <= halfWidth;
        }
        if (z == layout.depth()) {
            return Math.abs(x - openings.getOrDefault(BlockFace.SOUTH, centerX + 1000)) <= halfWidth;
        }
        if (x == -1) {
            return Math.abs(z - openings.getOrDefault(BlockFace.WEST, centerZ + 1000)) <= halfWidth;
        }
        if (x == layout.width()) {
            return Math.abs(z - openings.getOrDefault(BlockFace.EAST, centerZ + 1000)) <= halfWidth;
        }
        return false;
    }

    private boolean wideRoomOpenings(HallsLevelType levelType) {
        if (levelType == null || levelType.corridorGeneration() == null) {
            return false;
        }
        String mode = levelType.corridorGeneration().trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        return mode.equals("large_corridors") || mode.equals("large_corridor")
                || mode.equals("wide") || mode.equals("wide_corridors")
                || mode.equals("open_halls") || mode.equals("open_hall");
    }

    private void buildConnector(int x, int y, int startZ, int endZ) {
        buildConnector(x, y, startZ, endZ, HallsLevelType.fallback("howling_corridors"));
    }

    private void buildConnector(int x, int y, int startZ, int endZ, HallsLevelType levelType) {
        buildConnector(x, y, startZ, endZ, levelType, 0);
    }

    private void buildConnector(int x, int y, int startZ, int endZ, HallsLevelType levelType, int baseHalfWidth) {
        for (int z = Math.min(startZ, endZ); z <= Math.max(startZ, endZ); z++) {
            int halfWidth = Math.max(baseHalfWidth, z == origin.z() + ELEVATOR_OUTER_RADIUS + 1 ? 1 : 0);
            buildCorridorCell(x, y, z, halfWidth, true, levelType, new Random((((long) x) << 32) ^ z));
        }
    }

    private void buildCorridorCell(int x, int y, int z, int halfWidth, boolean northSouth) {
        buildCorridorCell(x, y, z, halfWidth, northSouth, HallsLevelType.fallback("howling_corridors"), new Random());
    }

    private void buildCorridorCell(int x,
                                   int y,
                                   int z,
                                   int halfWidth,
                                   boolean northSouth,
                                   HallsLevelType levelType,
                                   Random random) {
        Material floor = levelType.corridorFloor();
        Material ceiling = levelType.corridorCeiling();
        int pathMin = -halfWidth;
        int pathMax = halfWidth;
        Set<HallsExplorationGenerator.Cell> openCells = new HashSet<>();
        for (int offset = pathMin; offset <= pathMax; offset++) {
            int openX = northSouth ? x + offset : x;
            int openZ = northSouth ? z : z + offset;
            openCells.add(new HallsExplorationGenerator.Cell(openX, openZ));
        }
        for (int offset = pathMin - 1; offset <= pathMax + 1; offset++) {
            int blockX = northSouth ? x + offset : x;
            int blockZ = northSouth ? z : z + offset;
            if (isProtectedElevatorTransferCell(blockX, blockZ)) {
                continue;
            }
            boolean path = offset >= pathMin && offset <= pathMax;
            HallsExplorationGenerator.Cell wallCell = new HallsExplorationGenerator.Cell(blockX, blockZ);
            setBlock(blockX, y - 1, blockZ, path
                    ? floor
                    : corridorWallMaterial(levelType, wallCell, y - 1, openCells));
            setBlock(blockX, y + 3, blockZ, path && isCorridorLightCell(blockX, blockZ) ? levelType.light() : ceiling);
            for (int dy = 0; dy < 3; dy++) {
                setBlock(blockX, y + dy, blockZ, path
                        ? Material.AIR
                        : corridorWallMaterial(levelType, wallCell, y + dy, openCells));
            }
        }
    }

    private boolean isRoomCorner(HallsLayout layout, int x, int z) {
        return (x < 0 || x >= layout.width()) && (z < 0 || z >= layout.depth());
    }

    private boolean isRoomPillarColumn(HallsLayout layout, int x, int z) {
        if (isRoomCorner(layout, x, z)) {
            return true;
        }
        if (x < 0 || z < 0 || x >= layout.width() || z >= layout.depth() || layout.at(x, z) != 'X') {
            return false;
        }
        boolean northOpen = layout.at(x, z - 1) == 'O';
        boolean southOpen = layout.at(x, z + 1) == 'O';
        boolean eastOpen = layout.at(x + 1, z) == 'O';
        boolean westOpen = layout.at(x - 1, z) == 'O';
        return (northOpen || southOpen) && (eastOpen || westOpen);
    }

    private Material roomWallMaterial(HallsLevelType levelType, HallsLayout layout, int x, int z, int blockX, int y, int blockZ) {
        return wallMaterial(levelType, blockX, y, blockZ, isRoomPillarColumn(layout, x, z), 0x5A17);
    }

    private Material corridorWallMaterial(HallsLevelType levelType,
                                          HallsExplorationGenerator.Cell point,
                                          int y,
                                          Set<HallsExplorationGenerator.Cell> openCells) {
        return wallMaterial(levelType, point.x(), y, point.z(), isCorridorPillarColumn(point, openCells), 0xC011);
    }

    private Material wallMaterial(HallsLevelType levelType, int x, int y, int z, boolean pillar, long salt) {
        int groupX = Math.floorDiv(x, 7);
        int groupZ = Math.floorDiv(z, 7);
        Random paletteRandom = new Random((((long) groupX) * 341873128712L)
                ^ (((long) groupZ) * 132897987541L)
                ^ (((long) id) << 24)
                ^ (((long) currentFloor) << 8)
                ^ salt);
        HallsLevelType.BlockPalette palette = pillar
                ? levelType.pillarPalette(paletteRandom)
                : levelType.wallPalette(paletteRandom);
        Random columnRandom = new Random((((long) x) * 341873128712L)
                ^ (((long) y) * 42317861L)
                ^ (((long) z) * 132897987541L)
                ^ (((long) id) << 24)
                ^ (((long) currentFloor) << 8)
                ^ salt
                ^ 0x51EC1A7EL);
        return palette.material(columnRandom);
    }

    private void buildGeneratedCorridorMask(HallsExplorationGenerator.Plan plan,
                                            HallsLevelType levelType) {
        for (HallsExplorationGenerator.Cell point : plan.corridorShellCells()) {
            buildGeneratedCorridorCell(plan, levelType, point);
        }
    }

    private void buildGeneratedCorridorCell(HallsExplorationGenerator.Plan plan,
                                            HallsLevelType levelType,
                                            HallsExplorationGenerator.Cell point) {
        if (isProtectedElevatorTransferCell(point.x(), point.z())) {
            return;
        }
        Set<HallsExplorationGenerator.Cell> openCells = plan.corridorCells();
        boolean open = openCells.contains(point);
        boolean insideRoomShell = isInsideGeneratedRoomShell(point, plan.rooms());
        if (!open && insideRoomShell) {
            return;
        }
        setBlock(point.x(), origin.y() - 1, point.z(), open
                ? (plan.liquidCells().contains(point) ? levelType.liquid().material() : levelType.corridorFloor())
                : corridorWallMaterial(levelType, point, origin.y() - 1, openCells));
        if (open && plan.liquidCells().contains(point)) {
            setBlock(point.x(), origin.y() - 2, point.z(), levelType.liquid().material());
            setBlock(point.x(), origin.y() - 3, point.z(),
                    corridorWallMaterial(levelType, point, origin.y() - 3, openCells));
        }
        if (!insideRoomShell) {
            setBlock(point.x(), origin.y() + 3, point.z(),
                    open && isCorridorLightCell(point.x(), point.z()) ? levelType.light() : levelType.corridorCeiling());
        }
        for (int dy = 0; dy < 3; dy++) {
            setBlock(point.x(), origin.y() + dy, point.z(), open
                    ? Material.AIR
                    : corridorWallMaterial(levelType, point, origin.y() + dy, openCells));
        }
    }

    private boolean isCorridorPillarColumn(HallsExplorationGenerator.Cell point,
                                           Set<HallsExplorationGenerator.Cell> openCells) {
        boolean northOpen = openCells.contains(new HallsExplorationGenerator.Cell(point.x(), point.z() - 1));
        boolean southOpen = openCells.contains(new HallsExplorationGenerator.Cell(point.x(), point.z() + 1));
        boolean eastOpen = openCells.contains(new HallsExplorationGenerator.Cell(point.x() + 1, point.z()));
        boolean westOpen = openCells.contains(new HallsExplorationGenerator.Cell(point.x() - 1, point.z()));
        return (northOpen || southOpen) && (eastOpen || westOpen);
    }

    private boolean isCorridorLightCell(int x, int z) {
        return Math.floorMod((x * 31) ^ (z * 17) ^ (id * 13) ^ currentFloor, 11) == 0;
    }

    private boolean isInsideGeneratedRoomShell(HallsExplorationGenerator.Cell point,
                                               List<HallsExplorationGenerator.Room> rooms) {
        for (HallsExplorationGenerator.Room room : rooms) {
            if (point.x() >= room.startX() - 1
                    && point.x() <= room.startX() + room.layout().width()
                    && point.z() >= room.startZ() - 1
                    && point.z() <= room.startZ() + room.layout().depth()) {
                return true;
            }
        }
        return false;
    }

    private RoomBounds protectedElevatorBounds() {
        return new RoomBounds(origin.x() - ELEVATOR_OUTER_RADIUS, origin.x() + ELEVATOR_OUTER_RADIUS,
                origin.z() - ELEVATOR_OUTER_RADIUS, origin.z() + ELEVATOR_OUTER_RADIUS);
    }

    private boolean isProtectedElevatorCell(int x, int z) {
        return protectedElevatorBounds().contains(x, z);
    }

    private boolean isProtectedElevatorTransferCell(int x, int z) {
        return isProtectedElevatorCell(x, z)
                || (z == origin.z() + ELEVATOR_OUTER_RADIUS + 1
                && x >= origin.x() - ELEVATOR_INNER_RADIUS
                && x <= origin.x() + ELEVATOR_INNER_RADIUS);
    }

    private void openElevatorDoors() {
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                setBlock(origin.x() + x, origin.y() + y, origin.z() + ELEVATOR_OUTER_RADIUS, Material.AIR);
            }
        }
        for (int x = -1; x <= 1; x++) {
            setBlock(origin.x() + x, origin.y() + 3, origin.z() + ELEVATOR_OUTER_RADIUS, Material.DEEPSLATE_BRICKS);
        }
        buildElevatorVestibule(true);
    }

    private void closeElevatorDoors() {
        Material door = firstMaterial("WAXED_WEATHERED_COPPER_BARS", "WAXED_WEATHERED_COPPER_GRATE", "COPPER_BARS", "IRON_BARS");
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                setBlock(origin.x() + x, origin.y() + y, origin.z() + ELEVATOR_OUTER_RADIUS, door, BlockFace.EAST);
            }
        }
        for (int x = -1; x <= 1; x++) {
            setBlock(origin.x() + x, origin.y() + 3, origin.z() + ELEVATOR_OUTER_RADIUS, Material.DEEPSLATE_BRICKS);
        }
        buildElevatorVestibule(false);
    }

    private void buildElevatorVestibule(boolean open) {
        int z = origin.z() + ELEVATOR_OUTER_RADIUS + 1;
        HallsLevelType levelType = activeLevelType();
        for (int x = -2; x <= 2; x++) {
            int blockX = origin.x() + x;
            setBlock(blockX, origin.y() - 1, z, levelType.corridorFloor());
            setBlock(blockX, origin.y() + 3, z, levelType.corridorCeiling());
            setBlock(blockX, origin.y() + 4, z,
                    wallMaterial(levelType, blockX, origin.y() + 4, z, Math.abs(x) == 2, 0xE1E7A7));
            boolean sideWall = Math.abs(x) == 2;
            for (int y = 0; y <= 2; y++) {
                setBlock(blockX, origin.y() + y, z,
                        sideWall
                                ? wallMaterial(levelType, blockX, origin.y() + y, z, true, 0xE1E7A7)
                                : open ? Material.AIR : Material.BLACK_CONCRETE);
            }
        }
    }

    private void placeRoomContents(RoomPlacement room,
                                   Random random,
                                   int floor,
                                   int roomIndex,
                                   HallsScenario.FloorDefinition floorDefinition) {
        List<Cell> cells = openInteriorCells(room);
        if (cells.isEmpty()) {
            return;
        }
        List<Integer> propLootRolls = bundledBreakableLootRolls(
                targetBreakableDropsForRoom(floorDefinition, roomIndex), cells.size(), random);
        for (int i = 0; i < propLootRolls.size(); i++) {
            Cell cell = cells.get(random.nextInt(cells.size()));
            spawnBreakableProp(room.startX() + cell.x(), origin.y(), room.startZ() + cell.z(),
                    propArchetype(i, roomIndex, HallsLevelType.fallback("howling_corridors")),
                    propHealth(i), propLootRolls.get(i));
        }
    }

    private void placeGeneratedRoomContents(HallsExplorationGenerator.Room room,
                                            Random random,
                                            int floor,
                                            int roomIndex,
                                            HallsScenario.FloorDefinition floorDefinition,
                                            HallsLevelType levelType,
                                            Set<HallsExplorationGenerator.Cell> reservedCells,
                                            boolean forceRareBreakable) {
        List<Cell> cells = openInteriorCells(room);
        if (cells.isEmpty()) {
            return;
        }
        List<Integer> propLootRolls = bundledBreakableLootRolls(
                targetBreakableDropsForRoom(floorDefinition, roomIndex), cells.size(), random);
        Set<HallsExplorationGenerator.Cell> usedCells = new HashSet<>();
        if (forceRareBreakable) {
            Cell cell = randomFreeContentCell(cells, room, random, reservedCells, usedCells);
            if (cell == null) {
                return;
            }
            usedCells.add(new HallsExplorationGenerator.Cell(room.startX() + cell.x(), room.startZ() + cell.z()));
            spawnBreakableProp(room.startX() + cell.x(), origin.y(), room.startZ() + cell.z(),
                    rarePropArchetype(levelType), propHealth(0), 1);
            if (!propLootRolls.isEmpty()) {
                propLootRolls.removeFirst();
            }
        }
        for (int i = 0; i < propLootRolls.size(); i++) {
            Cell cell = randomFreeContentCell(cells, room, random, reservedCells, usedCells);
            if (cell == null) {
                return;
            }
            usedCells.add(new HallsExplorationGenerator.Cell(room.startX() + cell.x(), room.startZ() + cell.z()));
            spawnBreakableProp(room.startX() + cell.x(), origin.y(), room.startZ() + cell.z(),
                    commonPropArchetype(i, roomIndex, levelType), propHealth(i), propLootRolls.get(i));
        }
    }

    private int targetBreakableDropsForRoom(HallsScenario.FloorDefinition floorDefinition, int roomIndex) {
        int rooms = Math.max(1, floorDefinition.rooms());
        int baseDrops = Math.max(1, floorDefinition.breakables() / rooms);
        return baseDrops + (roomIndex < floorDefinition.breakables() % rooms ? 1 : 0);
    }

    private List<Integer> bundledBreakableLootRolls(int targetDrops, int availableCells, Random random) {
        int remainingDrops = Math.max(0, targetDrops);
        int remainingProps = Math.min(Math.max(0, availableCells), (remainingDrops + 1) / 2);
        List<Integer> rolls = new ArrayList<>();
        while (remainingDrops > 0 && remainingProps > 0) {
            int minForThis = Math.max(1, remainingDrops - (remainingProps - 1) * 3);
            int maxForThis = Math.min(3, remainingDrops - (remainingProps - 1));
            int count = minForThis;
            if (maxForThis > minForThis) {
                count += random.nextInt(maxForThis - minForThis + 1);
            }
            rolls.add(count);
            remainingDrops -= count;
            remainingProps--;
        }
        java.util.Collections.shuffle(rolls, random);
        return rolls;
    }

    private Cell randomFreeContentCell(List<Cell> cells,
                                       HallsExplorationGenerator.Room room,
                                       Random random,
                                       Set<HallsExplorationGenerator.Cell> reservedCells,
                                       Set<HallsExplorationGenerator.Cell> usedCells) {
        List<Cell> shuffled = new ArrayList<>(cells);
        java.util.Collections.shuffle(shuffled, random);
        for (Cell cell : shuffled) {
            HallsExplorationGenerator.Cell absolute = new HallsExplorationGenerator.Cell(room.startX() + cell.x(), room.startZ() + cell.z());
            if ((reservedCells == null || !reservedCells.contains(absolute)) && !usedCells.contains(absolute)) {
                return cell;
            }
        }
        return null;
    }

    private List<Cell> openInteriorCells(RoomPlacement room) {
        List<Cell> cells = new ArrayList<>();
        for (int z = 1; z < room.layout().depth() - 1; z++) {
            for (int x = 1; x < room.layout().width() - 1; x++) {
                if (room.layout().at(x, z) == 'O' && !isNearRoomExit(room, x, z)) {
                    cells.add(new Cell(x, z));
                }
            }
        }
        return cells;
    }

    private List<Cell> openInteriorCells(HallsExplorationGenerator.Room room) {
        List<Cell> cells = new ArrayList<>();
        for (int z = 1; z < room.layout().depth() - 1; z++) {
            for (int x = 1; x < room.layout().width() - 1; x++) {
                if (room.layout().at(x, z) == 'O' && !isNearRoomExit(room, x, z)) {
                    cells.add(new Cell(x, z));
                }
            }
        }
        return cells;
    }

    private List<Cell> openInteriorCells(HallsLayout layout) {
        List<Cell> cells = new ArrayList<>();
        for (int z = 1; z < layout.depth() - 1; z++) {
            for (int x = 1; x < layout.width() - 1; x++) {
                if (layout.at(x, z) == 'O') {
                    cells.add(new Cell(x, z));
                }
            }
        }
        return cells;
    }

    private boolean isNearRoomExit(RoomPlacement room, int x, int z) {
        return x == room.layout().width() / 2 || z == room.layout().depth() / 2;
    }

    private boolean isNearRoomExit(HallsExplorationGenerator.Room room, int x, int z) {
        return x == room.layout().width() / 2 || z == room.layout().depth() / 2;
    }

    private HallsBreakableType propArchetype(int index, int roomIndex, HallsLevelType levelType) {
        return propArchetype(index, roomIndex, levelType, null);
    }

    private HallsBreakableType commonPropArchetype(int index, int roomIndex, HallsLevelType levelType) {
        HallsBreakableType type = propArchetype(index, roomIndex, levelType, "common");
        return type == null ? propArchetype(index, roomIndex, levelType, null) : type;
    }

    private HallsBreakableType rarePropArchetype(HallsLevelType levelType) {
        HallsBreakableType type = propArchetype(0, 0, levelType, "rare");
        return type == null ? propArchetype(0, 0, levelType, null) : type;
    }

    private HallsBreakableType propArchetype(int index, int roomIndex, HallsLevelType levelType, String rarity) {
        List<HallsBreakableType> archetypes = new ArrayList<>(breakableTypes.values());
        if (archetypes.isEmpty()) {
            archetypes = new ArrayList<>(HallsBreakableTypeLoader.loadBreakableTypes(plugin, null).values());
        }
        if (rarity != null) {
            archetypes = new ArrayList<>(archetypes.stream()
                    .filter(type -> type.rarity().equals(rarity))
                    .toList());
        }
        if (archetypes.isEmpty()) {
            return null;
        }
        archetypes.sort(Comparator.comparing(HallsBreakableType::id));
        int salt = levelType == null ? 0 : levelType.id().hashCode();
        return archetypes.get(Math.floorMod(index + roomIndex + salt, archetypes.size()));
    }

    private int propHealth(int index) {
        return 2 + Math.floorMod(index, 3);
    }

    private HallsBreakableType breakableType(String id) {
        HallsBreakableType type = breakableTypes.get(id);
        if (type != null) {
            return type;
        }
        return HallsBreakableTypeLoader.loadBreakableTypes(plugin, null).values().iterator().next();
    }

    private void startHudTask() {
        stopHudTask();
        hudTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sendHud, 0L, 20L);
    }

    private void stopHudTask() {
        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }
    }

    private void sendHud() {
        if (!running) {
            return;
        }
        Component shared = Component.text("Floor " + currentFloor, NamedTextColor.DARK_RED)
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(formatElapsedSeconds(), NamedTextColor.GRAY))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Scrap W" + woodScrap + " I" + ironScrap
                        + " D" + diamondScrap + " R" + redstoneScrap, NamedTextColor.GOLD))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Coins " + coins + "/" + currentCoinQuota(), NamedTextColor.YELLOW));
        if (!activeFloorModifiers.empty()) {
            shared = shared.append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(activeFloorModifiers.hudComponent());
        }
        tickDeathFog();
        tickCompassTrail();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world)) {
                String elevatorDistance = elevatorDistanceLabel(player);
                if (activeFloorModifiers.compassLevel() >= 2) {
                    elevatorDistance += " " + Math.round(player.getLocation().distance(elevatorSpawnLocation())) + "b";
                }
                Component message = shared
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Sculk " + sculkRuntime.sculkPercent(player) + "%", NamedTextColor.AQUA))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Elevator " + elevatorDistance, NamedTextColor.LIGHT_PURPLE));
                player.sendActionBar(message);
                if (ghostPlayers.contains(playerId)) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0.0, 0.9, 0.0),
                            8, 0.35, 0.45, 0.35, 0.01);
                }
            }
        }
    }

    private void tickDeathFog() {
        int witherAfterSeconds = activeFloorModifiers.witherAfterSeconds();
        if (witherAfterSeconds <= 0 || currentFloor <= 1) {
            return;
        }
        long floorSeconds = Math.max(0L, (System.currentTimeMillis() - floorStartedAtMillis) / 1000L);
        if (floorSeconds == witherAfterSeconds - 180
                || floorSeconds == witherAfterSeconds - 60
                || floorSeconds == witherAfterSeconds - 10) {
            for (UUID playerId : participants) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.getWorld().equals(world)) {
                    player.sendTitle("Death Fog", "The air is turning poisonous.", 5, 35, 10);
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.65f, 0.7f);
                }
            }
        }
        if (floorSeconds >= witherAfterSeconds) {
            for (UUID playerId : participants) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.getWorld().equals(world) && !ghostPlayers.contains(playerId)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 45, 0, true, false, true));
                }
            }
        }
    }

    private void tickCompassTrail() {
        if (activeFloorModifiers.compassLevel() < 3 || currentFloor <= 1 || transitioning) {
            return;
        }
        if (compassTrailCountdown-- > 0) {
            return;
        }
        compassTrailCountdown = 5;
        Location target = elevatorSpawnLocation();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.getWorld().equals(world) || ghostPlayers.contains(playerId)) {
                continue;
            }
            Location start = player.getLocation().clone().add(0.0, 0.25, 0.0);
            Vector direction = target.toVector().subtract(start.toVector());
            double length = direction.length();
            if (length < 1.0) {
                continue;
            }
            direction.normalize();
            for (double distance = 1.0; distance < Math.min(length, 18.0); distance += 1.5) {
                Location point = start.clone().add(direction.clone().multiply(distance));
                world.spawnParticle(Particle.END_ROD, point, 1, 0.03, 0.03, 0.03, 0.0);
            }
        }
    }

    private String elevatorDistanceLabel(Player player) {
        if (player == null || !player.getWorld().equals(world)) {
            return "FAR";
        }
        double distance = player.getLocation().distance(elevatorSpawnLocation());
        if (distance <= 30.0) {
            return "NEAR";
        }
        if (distance <= 50.0) {
            return "MEDIUM";
        }
        return "FAR";
    }

    private int currentCoinQuota() {
        HallsScenario.FloorDefinition floor = adjustedDifficulty(scenario.floor(currentFloor));
        int quota = floor.coinQuota();
        if ("exploration".equalsIgnoreCase(floor.kind()) && !activeFloorModifiers.empty()) {
            quota = Math.max(0, (int) Math.round(quota * activeFloorModifiers.coinQuotaMultiplier()));
        }
        return quota;
    }

    private int nextFloorAfterCampDrill() {
        int destination = currentFloor + 1;
        if (!isCurrentFloorCamp() || campRuntime == null) {
            return destination;
        }
        int skipsRemaining = campRuntime.highestBuiltLevel("elevator_drill");
        while (skipsRemaining > 0 && destination < scenario.floorCount()) {
            int candidate = destination + 1;
            if (candidate >= scenario.floorCount()) {
                break;
            }
            HallsScenario.FloorDefinition candidateFloor = scenario.floor(candidate);
            if (candidateFloor == null || "camp".equalsIgnoreCase(candidateFloor.kind())) {
                break;
            }
            destination = candidate;
            skipsRemaining--;
        }
        return destination;
    }

    private HallsScenario.FloorDefinition adjustedDifficulty(HallsScenario.FloorDefinition floor) {
        if (floor == null || difficultyMultiplier <= 1.0) {
            return floor;
        }
        int difficulty = Math.max(0, (int) Math.round(parseDifficulty(floor.difficulty(), floor.firstFloor()) * difficultyMultiplier));
        int trappedRooms = Math.max(0, (int) Math.round(floor.trappedRooms() * difficultyMultiplier));
        int holes = Math.max(0, (int) Math.round(floor.holes() * difficultyMultiplier));
        int sculkPatches = Math.max(0, (int) Math.round(floor.sculkPatches() * difficultyMultiplier));
        int coinQuota = Math.max(0, (int) Math.round(floor.coinQuota() * difficultyMultiplier));
        return new HallsScenario.FloorDefinition(
                floor.firstFloor(),
                floor.lastFloor(),
                floor.kind(),
                floor.levelType(),
                Integer.toString(difficulty),
                floor.rooms(),
                floor.items(),
                floor.breakables(),
                trappedRooms,
                floor.minTrapsPerRoom(),
                floor.maxTrapsPerRoom(),
                holes,
                sculkPatches,
                coinQuota,
                floor.layout());
    }

    private int multipliedCoins(int amount) {
        return Math.max(1, (int) Math.round(amount * activeFloorModifiers.coinMultiplier()));
    }

    private void applyCompassModifier() {
        if (activeFloorModifiers.compassLevel() <= 0 || currentFloor <= 1) {
            return;
        }
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.getWorld().equals(world) || ghostPlayers.contains(playerId)) {
                continue;
            }
            player.setCompassTarget(elevatorSpawnLocation());
            if (!hasCompass(player.getInventory())) {
                int slot = firstAvailableHotbarSlot(player.getInventory());
                if (slot >= 0) {
                    ItemStack compass = namedItem(Material.COMPASS, "Elevator Compass", NamedTextColor.GREEN);
                    markElevatorCompass(compass);
                    player.getInventory().setItem(slot, compass);
                }
            }
        }
    }

    private boolean hasCompass(PlayerInventory inventory) {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.COMPASS) {
                return true;
            }
        }
        return false;
    }

    private boolean isAliveParticipant(UUID playerId) {
        if (playerId == null || ghostPlayers.contains(playerId)) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerId);
        return player != null && player.getWorld().equals(world);
    }

    private String formatElapsedSeconds() {
        long elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - startedAtMillis) / 1000L);
        long minutes = elapsedSeconds / 60L;
        long seconds = elapsedSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void restoreBlocks() {
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            BlockSnapshot snapshot = snapshots.get(i);
            Block block = world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
            if (block.getState(false) instanceof Container container) {
                container.getInventory().clear();
            }
            block.setBlockData(snapshot.blockData(), false);
        }
        snapshots.clear();
    }

    private void captureElevatorChestContents() {
        captureElevatorChestContents(true);
    }

    private void captureElevatorChestContents(boolean clearContainer) {
        if (elevatorChestSnapshotLocked) {
            return;
        }
        Container container = elevatorChestContainer();
        if (container == null) {
            return;
        }
        ItemStack[] contents = container.getInventory().getContents();
        elevatorChestContents = new ItemStack[Math.max(27, contents.length)];
        for (int i = 0; i < contents.length; i++) {
            elevatorChestContents[i] = contents[i] == null ? null : contents[i].clone();
        }
        if (clearContainer) {
            container.getInventory().clear();
        }
    }

    private void closeOpenElevatorChestViewers() {
        Container container = elevatorChestContainer();
        if (container == null) {
            return;
        }
        for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(container.getInventory().getViewers())) {
            viewer.closeInventory();
        }
    }

    private void restoreElevatorChestContents() {
        Container container = elevatorChestContainer();
        if (container == null) {
            return;
        }
        container.getInventory().setContents(elevatorChestContents);
        elevatorChestSnapshotLocked = false;
    }

    private void removeElevatorCompasses() {
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                removeElevatorCompasses(player.getInventory());
            }
        }
        for (int i = 0; i < elevatorChestContents.length; i++) {
            if (isElevatorCompass(elevatorChestContents[i])) {
                elevatorChestContents[i] = null;
            }
        }
        Container container = elevatorChestContainer();
        if (container != null) {
            removeElevatorCompasses(container.getInventory());
        }
    }

    private void removeElevatorCompasses(org.bukkit.inventory.Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isElevatorCompass(inventory.getItem(i))) {
                inventory.setItem(i, null);
            }
        }
    }

    private void markElevatorCompass(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "hoc_elevator_compass"),
                PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private boolean isElevatorCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return true;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new org.bukkit.NamespacedKey(plugin, "hoc_elevator_compass"), PersistentDataType.BYTE)
                || item.getItemMeta().displayName() != null;
    }

    private Container elevatorChestContainer() {
        Block block = world.getBlockAt(origin.x() - ELEVATOR_INNER_RADIUS, origin.y(), origin.z());
        return block.getState(false) instanceof Container container ? container : null;
    }

    private void spawnBreakableProp(int x, int y, int z, HallsBreakableType archetype, int health) {
        spawnBreakableProp(x, y, z, archetype, health, 1);
    }

    private void spawnBreakableProp(int x, int y, int z, HallsBreakableType archetype, int health, int lootRolls) {
        spawnBreakableProp(x, y, z, archetype, health, archetype.loot(), lootRolls);
    }

    private void spawnBreakableProp(int x,
                                    int y,
                                    int z,
                                    HallsBreakableType archetype,
                                    int health,
                                    List<HallsBreakableType.LootEntry> loot,
                                    int lootRolls) {
        List<UUID> displayIds = new ArrayList<>();
        for (HallsBreakableType.Part part : archetype.parts()) {
            displayIds.add(spawnPropDisplay(x + part.offsetX(), y + part.offsetY(), z + part.offsetZ(), part));
        }
        Location hitboxLocation = new Location(world, x + 0.5, y, z + 0.5);
        Interaction interaction = world.spawn(hitboxLocation, Interaction.class, entity -> {
            entity.setInteractionWidth(1.0f);
            entity.setInteractionHeight(archetype.hitboxHeight());
            entity.setResponsive(true);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_breakable");
        });
        BreakableProp prop = new BreakableProp(interaction.getUniqueId(), displayIds, x, y, z, health,
                archetype.particleMaterial(), archetype.scrapDrops(), loot, Math.max(1, lootRolls), archetype.breakMessage());
        breakableProps.put(interaction.getUniqueId(), prop);
        for (UUID displayId : displayIds) {
            breakableProps.put(displayId, prop);
        }
    }

    private UUID spawnPropDisplay(double x, double y, double z, HallsBreakableType.Part part) {
        Location displayLocation = new Location(world, x, y, z);
        BlockDisplay display = world.spawn(displayLocation, BlockDisplay.class, entity -> {
            entity.setBlock(displayBlockData(part.material(), part.blockData()));
            entity.setTransformation(smallRandomScaleTransformation(part.rotationX(), part.rotationY(), part.rotationZ()));
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_breakable");
        });
        return display.getUniqueId();
    }

    private HallsVegetationType weightedVegetation(HallsLevelType levelType, Random random) {
        int totalWeight = levelType.vegetation().stream()
                .filter(entry -> entry.weight() > 0 && vegetationTypes.containsKey(entry.id()))
                .mapToInt(HallsLevelType.VegetationEntry::weight)
                .sum();
        if (totalWeight <= 0) {
            return null;
        }
        int roll = random.nextInt(totalWeight);
        for (HallsLevelType.VegetationEntry entry : levelType.vegetation()) {
            HallsVegetationType type = vegetationTypes.get(entry.id());
            if (type == null || entry.weight() <= 0) {
                continue;
            }
            roll -= entry.weight();
            if (roll < 0) {
                return type;
            }
        }
        return null;
    }

    private void spawnVegetationDisplay(int x, int y, int z, HallsVegetationType type, Random random) {
        Location displayLocation = new Location(world, x, y + type.offsetY(), z);
        BlockDisplay display = world.spawn(displayLocation, BlockDisplay.class, entity -> {
            entity.setBlock(displayBlockData(type.material(), type.blockData()));
            entity.setTransformation(vegetationTransformation(type, random));
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_vegetation");
        });
        vegetationDisplays.add(display.getUniqueId());
    }

    private Transformation vegetationTransformation(HallsVegetationType type, Random random) {
        float scale = randomDisplayScale(type.scale());
        float yaw = type.randomYaw() ? (float) (random.nextDouble() * Math.PI * 2.0) : 0.0f;
        Vector3f pivot = new Vector3f(0.5f, 0.0f, 0.5f);
        Vector3f transformedPivot = new Vector3f(pivot).mul(scale).rotate(new Quaternionf().rotateY(yaw));
        return new Transformation(
                new Vector3f(pivot).sub(transformedPivot),
                new Quaternionf().rotateY(yaw),
                new Vector3f(scale, scale, scale),
                new Quaternionf());
    }

    private void breakBreakableProp(BreakableProp prop) {
        Location dropLocation = null;
        Entity interaction = Bukkit.getEntity(prop.interactionId());
        if (interaction != null) {
            dropLocation = interaction.getLocation().clone().add(0.0, 0.25, 0.0);
        }
        removeBreakableProp(prop);
        wakePhysicsDropsNear(dropLocation);
        if (dropLocation != null) {
            world.playSound(dropLocation, Sound.BLOCK_WOOD_BREAK, 0.8f, 1.0f);
            monsterRuntime.alert(dropLocation);
            for (int i = 0; i < prop.lootRolls(); i++) {
                applyLoot(prop.loot(), prop.scrapDrops(), dropLocation);
            }
        }
    }

    private void applyLoot(List<HallsBreakableType.LootEntry> loot, List<String> scrapDrops, Location dropLocation) {
        HallsBreakableType.LootEntry entry = rollLoot(loot);
        if (entry == null) {
            return;
        }
        int amount = entry.minAmount();
        if (entry.maxAmount() > entry.minAmount()) {
            amount += new Random().nextInt(entry.maxAmount() - entry.minAmount() + 1);
        }
        applyReward(resolveBreakableReward(entry.item(), scrapDrops), amount, dropLocation);
    }

    private String resolveBreakableReward(String reward, List<String> scrapDrops) {
        if (!reward.equals("scrap") && !reward.equals("random_scrap")) {
            return reward;
        }
        List<String> configured = scrapDrops == null || scrapDrops.isEmpty()
                ? List.of("wood_scrap", "iron_scrap")
                : scrapDrops;
        return configured.get(new Random().nextInt(configured.size()));
    }

    private HallsBreakableType.LootEntry rollLoot(List<HallsBreakableType.LootEntry> loot) {
        if (loot == null || loot.isEmpty()) {
            return null;
        }
        int totalWeight = loot.stream().mapToInt(HallsBreakableType.LootEntry::weight).sum();
        int roll = new Random().nextInt(Math.max(1, totalWeight));
        for (HallsBreakableType.LootEntry entry : loot) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return loot.getLast();
    }

    private void applyReward(String reward, int amount, Location dropLocation) {
        ItemStack resolvedItem = resolveCatalogReward(reward, amount);
        if (resolvedItem != null) {
            dropSessionItem(dropLocation, resolvedItem);
            return;
        }
        switch (reward) {
            case "blueprint" -> dropSessionItem(dropLocation, blueprintFromScenario("normal", 8));
            case "normal_blueprint" -> dropSessionItem(dropLocation, blueprintFromScenario("normal", 0));
            case "rare_blueprint" -> dropSessionItem(dropLocation, blueprintFromScenario("rare", 0));
            case "coin", "coins" -> dropSessionItem(dropLocation, coinItem(amount));
            case "wood_scrap" -> dropSessionItem(dropLocation, scrapItem(Material.STICK, "Wood Scrap", NamedTextColor.GOLD, PropReward.WOOD_SCRAP, multipliedScrap(amount)));
            case "iron_scrap" -> dropSessionItem(dropLocation, scrapItem(Material.RAW_IRON, "Iron Scrap", NamedTextColor.GRAY, PropReward.IRON_SCRAP, multipliedScrap(amount)));
            case "diamond_scrap" -> dropSessionItem(dropLocation, scrapItem(Material.DIAMOND, "Diamond Scrap", NamedTextColor.AQUA, PropReward.DIAMOND_SCRAP, multipliedScrap(amount)));
            case "redstone_scrap" -> dropSessionItem(dropLocation, scrapItem(Material.REDSTONE, "Redstone Scrap", NamedTextColor.RED, PropReward.REDSTONE_SCRAP, multipliedScrap(amount)));
            case "random_scrap", "scrap" -> {
                PropReward[] scraps = {PropReward.WOOD_SCRAP, PropReward.IRON_SCRAP, PropReward.DIAMOND_SCRAP, PropReward.REDSTONE_SCRAP};
                PropReward selected = scraps[new Random().nextInt(scraps.length)];
                applyReward(scrapRewardId(selected), amount, dropLocation);
            }
            default -> {
            }
        }
    }

    private ItemStack resolveCatalogReward(String reward, int amount) {
        HallsItemType direct = itemTypes.get(reward);
        if (direct != null) {
            return definedItem(direct, amount);
        }
        return switch (reward) {
            case "weapon", "armor", "utility", "food" -> randomAllowedItem(reward, "normal", amount);
            case "rare_weapon" -> randomAllowedItem("weapon", "rare", amount);
            case "rare_armor" -> randomAllowedItem("armor", "rare", amount);
            case "rare_utility" -> randomAllowedItem("utility", "rare", amount);
            case "rare_food" -> randomAllowedItem("food", "rare", amount);
            default -> null;
        };
    }

    private int multipliedScrap(int amount) {
        return Math.max(1, (int) Math.round(amount * activeFloorModifiers.scrapDropMultiplier()));
    }

    private ItemStack randomAllowedItem(String category, String rarity, int amount) {
        List<HallsItemType> candidates = scenario.allowedItems(category).stream()
                .map(itemTypes::get)
                .filter(item -> item != null && item.category().equals(category) && item.rarity().equals(rarity))
                .sorted(Comparator.comparing(HallsItemType::id))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return definedItem(candidates.get(new Random().nextInt(candidates.size())), amount);
    }

    private ItemStack blueprintFromScenario(String preferredPool, int rareChancePercent) {
        String pool = preferredPool;
        if (rareChancePercent > 0 && !scenario.blueprintPool("rare", activeLevelTypeId).isEmpty()
                && new Random().nextInt(100) < rareChancePercent) {
            pool = "rare";
        }
        List<String> ids = scenario.blueprintPool(pool, activeLevelTypeId);
        if (ids.isEmpty() && !pool.equals("normal")) {
            ids = scenario.blueprintPool("normal", activeLevelTypeId);
        }
        if (ids.isEmpty()) {
            return blueprintPlaceholder();
        }
        List<HallsItemType> candidates = ids.stream()
                .map(itemTypes::get)
                .filter(item -> item != null && item.category().equals("blueprint"))
                .sorted(Comparator.comparing(HallsItemType::id))
                .toList();
        if (candidates.isEmpty()) {
            return blueprintPlaceholder();
        }
        return definedItem(candidates.get(new Random().nextInt(candidates.size())), 1);
    }

    private ItemStack definedItem(HallsItemType type, int amount) {
        return HallsItemFactory.create(plugin, type, amount);
    }

    private void activateSmokeBomb(Player player, HallsItemType type) {
        double radius = Math.max(6.0, type.stats().getOrDefault("radius", 10.0));
        int durationTicks = Math.max(20, (int) Math.round(type.stats().getOrDefault("duration_seconds", 6.0) * 20.0));
        monsterRuntime.concealParticipant(player.getUniqueId(), durationTicks * 50L);
        monsterRuntime.clearTargetsNear(player.getLocation(), radius);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, durationTicks, 0, true, false, true));
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation().add(0.0, 1.0, 0.0),
                70, radius * 0.25, 0.8, radius * 0.25, 0.03);
        world.spawnParticle(Particle.SMOKE, player.getLocation().add(0.0, 0.8, 0.0),
                100, radius * 0.28, 0.45, radius * 0.28, 0.02);
        world.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.9f, 0.65f);
        player.sendActionBar(Component.text("Smoke covers your escape.", NamedTextColor.GRAY));
    }

    private void activateWardingTotem(Player player, HallsItemType type) {
        double radius = Math.max(4.0, type.stats().getOrDefault("radius", 8.0));
        int durationTicks = Math.max(20, (int) Math.round(type.stats().getOrDefault("duration_seconds", 10.0) * 20.0));
        for (UUID playerId : participants) {
            Player target = Bukkit.getPlayer(playerId);
            if (target == null || !target.getWorld().equals(world) || ghostPlayers.contains(playerId)
                    || target.getLocation().distanceSquared(player.getLocation()) > radius * radius) {
                continue;
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 1, true, true, true));
            target.sendActionBar(Component.text("Warding magic hardens your skin.", NamedTextColor.GOLD));
        }
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0.0, 1.0, 0.0),
                80, radius * 0.22, 0.9, radius * 0.22, 0.08);
        world.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.85f, 1.15f);
    }

    private boolean activateHealingUtility(Player player, HallsItemType type) {
        double heal = Math.max(0.0, type.stats().getOrDefault("heal", 4.0));
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? player.getMaxHealth()
                : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        if (player.getHealth() >= maxHealth) {
            player.sendActionBar(Component.text("You are already at full health.", NamedTextColor.GRAY));
            return false;
        }
        player.setHealth(Math.min(maxHealth, player.getHealth() + heal));
        world.spawnParticle(Particle.HEART, player.getLocation().add(0.0, 1.2, 0.0), 6, 0.35, 0.35, 0.35, 0.02);
        world.playSound(player.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 0.7f, 1.35f);
        player.sendActionBar(Component.text("Restored " + formatStatAmount(heal) + " health.", NamedTextColor.GREEN));
        return true;
    }

    private void activateSelfBuffUtility(Player player,
                                         HallsItemType type,
                                         PotionEffectType effectType,
                                         String statPrefix,
                                         String message,
                                         Sound sound) {
        int durationTicks = Math.max(20, (int) Math.round(type.stats().getOrDefault(statPrefix + "_seconds", 8.0) * 20.0));
        int amplifier = Math.max(0, (int) Math.round(type.stats().getOrDefault(statPrefix + "_amplifier", 1.0)) - 1);
        player.addPotionEffect(new PotionEffect(effectType, durationTicks, amplifier, true, true, true));
        world.spawnParticle(Particle.EFFECT, player.getLocation().add(0.0, 1.0, 0.0),
                32, 0.35, 0.6, 0.35, 0.05);
        world.playSound(player.getLocation(), sound, 0.75f, 1.2f);
        player.sendActionBar(Component.text(message, NamedTextColor.GREEN));
    }

    private void activateMonsterPulseUtility(Player player,
                                             HallsItemType type,
                                             Particle particle,
                                             Sound sound,
                                             String message) {
        double radius = Math.max(1.0, type.stats().getOrDefault("radius", 5.0));
        double damage = Math.max(0.0, type.stats().getOrDefault("monster_damage", 5.0));
        Location center = player.getLocation();
        for (Entity nearby : world.getNearbyEntities(center, radius, radius, radius)) {
            if (nearby instanceof LivingEntity living
                    && monsterRuntime.isSessionMonster(living)
                    && living.getLocation().distanceSquared(center) <= radius * radius) {
                living.damage(damage, player);
            }
        }
        world.spawnParticle(particle, center.clone().add(0.0, 1.0, 0.0),
                80, radius * 0.35, 0.7, radius * 0.35, 0.08);
        world.playSound(center, sound, 0.7f, 1.45f);
        player.sendActionBar(Component.text(message, NamedTextColor.AQUA));
    }

    private void activatePoisonBomb(Player player, HallsItemType type) {
        double radius = Math.max(1.0, type.stats().getOrDefault("radius", 4.0));
        double damage = Math.max(0.0, type.stats().getOrDefault("monster_damage", 3.0));
        int poisonTicks = Math.max(20, (int) Math.round(type.stats().getOrDefault("poison_seconds", 5.0) * 20.0));
        int amplifier = Math.max(0, (int) Math.round(type.stats().getOrDefault("poison_amplifier", 1.0)) - 1);
        Location center = player.getLocation();
        for (Entity nearby : world.getNearbyEntities(center, radius, radius, radius)) {
            if (nearby instanceof LivingEntity living
                    && monsterRuntime.isSessionMonster(living)
                    && living.getLocation().distanceSquared(center) <= radius * radius) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonTicks, amplifier, true, true, true));
                if (damage > 0.0) {
                    living.damage(damage, player);
                }
            }
        }
        world.spawnParticle(Particle.ENTITY_EFFECT, center.clone().add(0.0, 1.0, 0.0),
                90, radius * 0.35, 0.65, radius * 0.35, 0.08);
        world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.clone().add(0.0, 0.6, 0.0),
                70, radius * 0.3, 0.45, radius * 0.3, 0.04);
        world.playSound(center, Sound.ENTITY_SPLASH_POTION_BREAK, 0.8f, 0.75f);
        player.sendActionBar(Component.text("Poison vapor eats into nearby monsters.", NamedTextColor.DARK_GREEN));
    }

    private boolean isUtilityOnCooldown(Player player, HallsItemType type) {
        long remainingMillis = utilityCooldowns.getOrDefault(utilityCooldownKey(player, type), 0L) - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            return false;
        }
        player.sendActionBar(Component.text(type.name() + " is cooling down: "
                + Math.max(1L, (remainingMillis + 999L) / 1000L) + "s.", NamedTextColor.YELLOW));
        return true;
    }

    private void applyUtilityCooldown(Player player, ItemStack usedItem, HallsItemType type) {
        int cooldownTicks = Math.max(0, (int) Math.round(type.stats().getOrDefault("cooldown_seconds", 0.0) * 20.0));
        if (cooldownTicks <= 0) {
            return;
        }
        ensureUseCooldownMetadata(usedItem, type);
        utilityCooldowns.put(utilityCooldownKey(player, type), System.currentTimeMillis() + cooldownTicks * 50L);
        org.bukkit.NamespacedKey cooldownKey = new org.bukkit.NamespacedKey(plugin, "hoc_" + type.id());
        player.setCooldown(cooldownKey, cooldownTicks);
        ItemStack cooldownItem = usedItem == null || usedItem.getType().isAir()
                ? player.getInventory().getItemInMainHand()
                : usedItem;
        player.setCooldown(cooldownItem, cooldownTicks);
        player.setCooldown(cooldownItem.getType(), cooldownTicks);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!running || !player.isOnline()) {
                return;
            }
            player.setCooldown(cooldownKey, cooldownTicks);
            player.setCooldown(cooldownItem, cooldownTicks);
            player.setCooldown(cooldownItem.getType(), cooldownTicks);
        });
    }

    private void ensureUseCooldownMetadata(ItemStack item, HallsItemType type) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        org.bukkit.NamespacedKey cooldownKey = new org.bukkit.NamespacedKey(plugin, "hoc_" + type.id());
        org.bukkit.inventory.meta.components.UseCooldownComponent cooldown = meta.getUseCooldown();
        if (cooldown.getCooldownSeconds() <= 0.0f
                || cooldown.getCooldownGroup() == null
                || !cooldown.getCooldownGroup().equals(cooldownKey)) {
            cooldown.setCooldownSeconds((float) Math.max(0.0, type.stats().getOrDefault("cooldown_seconds", 0.0)));
            cooldown.setCooldownGroup(cooldownKey);
            meta.setUseCooldown(cooldown);
            item.setItemMeta(meta);
        }
    }

    private String utilityCooldownKey(Player player, HallsItemType type) {
        return player.getUniqueId() + ":" + type.id();
    }

    private void consumeOneHeldItem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        held.setAmount(held.getAmount() - 1);
        player.getInventory().setItemInMainHand(held);
    }

    private HallsItemType itemType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String itemId = item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "hoc_item_id"), PersistentDataType.STRING);
        return itemId == null ? null : itemTypes.get(itemId);
    }

    private String formatStatAmount(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private String scrapRewardId(PropReward reward) {
        return switch (reward) {
            case WOOD_SCRAP -> "wood_scrap";
            case IRON_SCRAP -> "iron_scrap";
            case DIAMOND_SCRAP -> "diamond_scrap";
            case REDSTONE_SCRAP -> "redstone_scrap";
        };
    }

    private int depositSelectedScrap(PlayerInventory inventory) {
        int slot = inventory.getHeldItemSlot();
        ItemStack selected = inventory.getItem(slot);
        int deposited = depositScrapStack(selected);
        if (deposited > 0) {
            inventory.setItem(slot, null);
        }
        return deposited;
    }

    private int depositScrapStack(ItemStack item) {
        PropReward scrapType = scrapType(item);
        if (scrapType == null) {
            return 0;
        }
        int amount = Math.max(1, item.getAmount());
        switch (scrapType) {
            case WOOD_SCRAP -> woodScrap += amount;
            case IRON_SCRAP -> ironScrap += amount;
            case DIAMOND_SCRAP -> diamondScrap += amount;
            case REDSTONE_SCRAP -> redstoneScrap += amount;
            default -> {
                return 0;
            }
        }
        return amount;
    }

    public boolean addStoredScrap(String rawType, int amount) {
        PropReward scrapType = parseScrapReward(rawType);
        if (scrapType == null || amount <= 0) {
            return false;
        }
        switch (scrapType) {
            case WOOD_SCRAP -> woodScrap += amount;
            case IRON_SCRAP -> ironScrap += amount;
            case DIAMOND_SCRAP -> diamondScrap += amount;
            case REDSTONE_SCRAP -> redstoneScrap += amount;
            default -> {
                return false;
            }
        }
        coins += multipliedCoins(amount);
        return true;
    }

    private boolean reducePlayerSculk(UUID playerId, double amount) {
        if (playerId == null || amount <= 0.0) {
            return false;
        }
        int current = sculkRuntime.sculkPercent(playerId);
        if (current <= 0) {
            return false;
        }
        sculkRuntime.setSculk(playerId, Math.max(0.0, current - amount));
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.getWorld().equals(world)) {
            player.sendActionBar(Component.text("Sculk pressure reduced to "
                    + sculkRuntime.sculkPercent(playerId) + "%.", NamedTextColor.AQUA));
        }
        return true;
    }

    public static boolean isScrapId(String rawType) {
        return parseScrapReward(rawType) != null;
    }

    private static PropReward parseScrapReward(String rawType) {
        if (rawType == null) {
            return null;
        }
        return switch (rawType.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_')) {
            case "wood", "wood_scrap" -> PropReward.WOOD_SCRAP;
            case "iron", "iron_scrap" -> PropReward.IRON_SCRAP;
            case "diamond", "diamond_scrap" -> PropReward.DIAMOND_SCRAP;
            case "redstone", "redstone_scrap" -> PropReward.REDSTONE_SCRAP;
            default -> null;
        };
    }

    private boolean spendStoredScrap(Map<String, Integer> cost) {
        if (cost == null || cost.isEmpty()) {
            return true;
        }
        int wood = cost.getOrDefault("wood", cost.getOrDefault("wood_scrap", 0));
        int iron = cost.getOrDefault("iron", cost.getOrDefault("iron_scrap", 0));
        int diamond = cost.getOrDefault("diamond", cost.getOrDefault("diamond_scrap", 0));
        int redstone = cost.getOrDefault("redstone", cost.getOrDefault("redstone_scrap", 0));
        if (!hasStoredScrap(wood, iron, diamond, redstone)) {
            return false;
        }
        woodScrap -= wood;
        ironScrap -= iron;
        diamondScrap -= diamond;
        redstoneScrap -= redstone;
        return true;
    }

    private boolean hasStoredScrap(Map<String, Integer> cost) {
        if (cost == null || cost.isEmpty()) {
            return true;
        }
        int wood = cost.getOrDefault("wood", cost.getOrDefault("wood_scrap", 0));
        int iron = cost.getOrDefault("iron", cost.getOrDefault("iron_scrap", 0));
        int diamond = cost.getOrDefault("diamond", cost.getOrDefault("diamond_scrap", 0));
        int redstone = cost.getOrDefault("redstone", cost.getOrDefault("redstone_scrap", 0));
        return hasStoredScrap(wood, iron, diamond, redstone);
    }

    private boolean hasStoredScrap(int wood, int iron, int diamond, int redstone) {
        return woodScrap >= wood && ironScrap >= iron && diamondScrap >= diamond && redstoneScrap >= redstone;
    }

    private void dropSessionItem(Location location, ItemStack stack) {
        Vector velocity = new Vector((Math.random() - 0.5) * 0.18, 0.22, (Math.random() - 0.5) * 0.18);
        dropSessionItem(location, stack, velocity);
    }

    private void dropSessionItem(Location location, ItemStack stack, Vector velocity) {
        if (location == null || stack == null || stack.getType().isAir()) {
            return;
        }
        int amount = shouldSplitSessionDrop(stack) ? Math.max(1, stack.getAmount()) : 1;
        for (int i = 0; i < amount; i++) {
            ItemStack singleStack = stack.clone();
            singleStack.setAmount(shouldSplitSessionDrop(stack) ? 1 : stack.getAmount());
            if (isScrapItem(singleStack)) {
                markUniqueScrapItem(singleStack);
            }
            Vector splitVelocity = velocity == null ? new Vector() : velocity.clone();
            if (amount > 1) {
                splitVelocity.add(new Vector((Math.random() - 0.5) * 0.12, 0.03 * (i % 3), (Math.random() - 0.5) * 0.12));
            }
            dropSingleSessionItem(location, singleStack, splitVelocity);
        }
    }

    private boolean shouldSplitSessionDrop(ItemStack stack) {
        return stack.getType() != Material.ARROW && stack.getType() != Material.FIREWORK_ROCKET;
    }

    private void dropSingleSessionItem(Location location, ItemStack stack, Vector velocity) {
        Location spawnLocation = location.clone().add(0.0, 0.2, 0.0);
        ItemDisplay display = world.spawn(spawnLocation, ItemDisplay.class, entity -> {
            entity.setItemStack(stack.clone());
            entity.setInterpolationDelay(DISPLAY_INTERPOLATION_DELAY_TICKS);
            entity.setTeleportDuration(DISPLAY_TELEPORT_DURATION_TICKS);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setItemDisplayTransform(sessionDropDisplayTransform(stack));
            entity.setTransformation(sessionDropTransformation(stack));
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_physics_drop");
        });
        Interaction interaction = world.spawn(spawnLocation.clone().add(0.0, -0.15, 0.0), Interaction.class, entity -> {
            entity.setInteractionWidth(0.8f);
            entity.setInteractionHeight(0.8f);
            entity.setResponsive(true);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_physics_drop");
        });
        PhysicsDrop drop = new PhysicsDrop(interaction.getUniqueId(), display.getUniqueId(), stack.clone(), spawnLocation,
                velocity == null ? new Vector() : velocity.clone().multiply(0.65));
        physicsDrops.put(interaction.getUniqueId(), drop);
        physicsDrops.put(display.getUniqueId(), drop);
        startPhysicsDropTask();
    }

    private Transformation sessionDropTransformation(ItemStack stack) {
        float yaw = (float) (Math.random() * Math.PI * 2.0);
        Quaternionf rotation = new Quaternionf().rotateY(yaw);
        if (!usesGuiDropDisplay(stack)) {
            rotation.rotateX((float) Math.toRadians(90.0));
        }
        return new Transformation(
                new Vector3f(),
                rotation,
                new Vector3f(randomDisplayScale(0.75f), randomDisplayScale(0.75f), randomDisplayScale(0.75f)),
                new Quaternionf());
    }

    private ItemDisplay.ItemDisplayTransform sessionDropDisplayTransform(ItemStack stack) {
        return usesGuiDropDisplay(stack) ? ItemDisplay.ItemDisplayTransform.GUI : ItemDisplay.ItemDisplayTransform.NONE;
    }

    private boolean usesGuiDropDisplay(ItemStack stack) {
        return stack != null && stack.getType() == Material.TRIDENT;
    }

    private Transformation smallRandomScaleTransformation() {
        return smallRandomScaleTransformation(0.0, 0.0, 0.0);
    }

    private Transformation smallRandomScaleTransformation(double rotationX, double rotationY, double rotationZ) {
        float scaleX = randomDisplayScale(1.0f);
        float scaleY = randomDisplayScale(1.0f);
        float scaleZ = randomDisplayScale(1.0f);
        return new Transformation(
                new Vector3f((1.0f - scaleX) * 0.5f, 0.0f, (1.0f - scaleZ) * 0.5f),
                new Quaternionf().rotateXYZ((float) Math.toRadians(rotationX),
                        (float) Math.toRadians(rotationY),
                        (float) Math.toRadians(rotationZ)),
                new Vector3f(scaleX, scaleY, scaleZ),
                new Quaternionf());
    }

    private BlockData displayBlockData(Material material, String configured) {
        if (configured == null || configured.isBlank()) {
            return material.createBlockData();
        }
        try {
            if (configured.startsWith("minecraft:") || configured.startsWith(material.getKey().asString())) {
                return Bukkit.createBlockData(configured);
            }
            String suffix = configured.startsWith("[") ? configured : "[" + configured + "]";
            return material.createBlockData(suffix);
        } catch (IllegalArgumentException ex) {
            return material.createBlockData();
        }
    }

    private float randomDisplayScale(float base) {
        return (float) (base * (0.98 + Math.random() * 0.04));
    }

    private void removeSessionEntities() {
        sculkRuntime.clearFloor();
        monsterRuntime.clear();
        trapRuntime.clear();
        campRuntime.clear();
        for (BreakableProp prop : Set.copyOf(breakableProps.values())) {
            removeBreakableProp(prop);
        }
        breakableProps.clear();
        for (UUID displayId : Set.copyOf(vegetationDisplays)) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null) {
                display.remove();
            }
        }
        vegetationDisplays.clear();
        for (PhysicsDrop drop : Set.copyOf(physicsDrops.values())) {
            removePhysicsDrop(drop);
        }
        physicsDrops.clear();
        stopPhysicsDropTask();
    }

    private void removeBreakableProp(BreakableProp prop) {
        breakableProps.remove(prop.interactionId());
        for (UUID displayId : prop.displayIds()) {
            breakableProps.remove(displayId);
        }
        Entity interaction = Bukkit.getEntity(prop.interactionId());
        if (interaction != null) {
            interaction.remove();
        }
        for (UUID displayId : prop.displayIds()) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null) {
                display.remove();
            }
        }
    }

    private void removePhysicsDrop(PhysicsDrop drop) {
        physicsDrops.remove(drop.interactionId());
        physicsDrops.remove(drop.displayId());
        Entity interaction = Bukkit.getEntity(drop.interactionId());
        if (interaction != null) {
            interaction.remove();
        }
        Entity display = Bukkit.getEntity(drop.displayId());
        if (display != null) {
            display.remove();
        }
        if (physicsDrops.isEmpty()) {
            stopPhysicsDropTask();
        }
    }

    private void startPhysicsDropTask() {
        if (physicsDropTask != null) {
            return;
        }
        physicsDropTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPhysicsDrops, 1L, 1L);
    }

    private void stopPhysicsDropTask() {
        if (physicsDropTask != null) {
            physicsDropTask.cancel();
            physicsDropTask = null;
        }
    }

    private void makeGhost(Player player) {
        ghostPlayers.add(player.getUniqueId());
        dropPlayerSessionInventory(player);
        dropFirstGhostCoinCache(player);
        applyGhostState(player);
        player.setHealth(1.0);
        player.sendTitle("You are a ghost", "Wait for the next floor.", 10, 50, 20);
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.65f, 1.4f);
        if (allParticipantsGhosts()) {
            scheduleGameOver();
        }
    }

    private void dropFirstGhostCoinCache(Player player) {
        int amount = activeFloorModifiers.firstGhostCoinCache();
        if (firstGhostCoinCacheDropped || amount <= 0 || player == null) {
            return;
        }
        firstGhostCoinCacheDropped = true;
        dropSessionItem(player.getLocation().clone().add(0.0, 0.35, 0.0), coinItem(amount));
    }

    private void applyGhostState(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvisible(true);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getInventory().clear();
        applyInventoryLimit(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, true, false, false));
    }

    private void clearGhostState(Player player) {
        if (player == null) {
            return;
        }
        ghostPlayers.remove(player.getUniqueId());
        player.setInvisible(false);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    private void healForElevatorArrival(Player player, boolean wasGhost) {
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? player.getMaxHealth()
                : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        double targetHealth = wasGhost ? 10.0 : player.getHealth() + 6.0;
        player.setHealth(Math.min(maxHealth, Math.max(1.0, targetHealth)));
    }

    private void dropPlayerSessionInventory(Player player) {
        Location location = player.getLocation().clone().add(0.0, 0.4, 0.0);
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir() && !isLockedSlotItem(plugin, item)) {
                dropSessionItem(location, item.clone(), new Vector(Math.random() - 0.5, 0.2, Math.random() - 0.5));
            }
        }
        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                dropSessionItem(location, item.clone(), new Vector(Math.random() - 0.5, 0.2, Math.random() - 0.5));
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        if (!offhand.getType().isAir()) {
            dropSessionItem(location, offhand.clone(), new Vector(Math.random() - 0.5, 0.2, Math.random() - 0.5));
        }
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
    }

    private boolean allParticipantsGhosts() {
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world) && !ghostPlayers.contains(playerId)) {
                return false;
            }
        }
        return true;
    }

    private void scheduleGameOver() {
        if (gameOverTask != null) {
            return;
        }
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world)) {
                player.sendTitle("Game Over", "Restarting from floor 1.", 10, 160, 20);
            }
        }
        gameOverTask = Bukkit.getScheduler().runTaskLater(plugin, this::restartFromGameOver, 200L);
    }

    private void restartFromGameOver() {
        gameOverTask = null;
        if (!running) {
            return;
        }
        Location protectedSpawn = elevatorSpawnLocation();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world)) {
                player.teleport(protectedSpawn);
                player.setFallDistance(0.0f);
            }
        }
        removeSessionEntities();
        resetRunState();
        resetCampHarvestForNewRun();
        try {
            buildStartArea();
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to restart Halls session " + id + " after game over: " + ex.getMessage());
            return;
        }
        openElevatorDoors();
        Location spawn = startRoomSpawn == null ? elevatorSpawnLocation() : startRoomSpawn;
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.getInventory().setItemInOffHand(null);
                clearTotemBuffs(player);
                player.setHealth(Math.min(player.getMaxHealth(), 20.0));
                clearGhostState(player);
                applyInventoryLimit(player);
                giveStarterItem(player);
                teleportSessionPlayer(player, spawn);
                player.sendTitle("Run Lost", "Back to floor 1.", 0, 45, 15);
            }
        }
        save("game-over-restart");
    }

    public void save(String reason) {
        try {
            captureCurrentCampState();
            if (!elevatorChestSnapshotLocked) {
                captureElevatorChestContents(false);
            }
            File folder = new File(dataFolder, "saves");
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("Failed to create Halls save folder: " + folder);
                return;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("schema-version", 1);
            yaml.set("reason", reason);
            yaml.set("scenario", scenario.id());
            yaml.set("host", hostId.toString());
            yaml.set("difficulty.id", difficultyId);
            yaml.set("difficulty.multiplier", difficultyMultiplier);
            yaml.set("current-floor", currentFloor);
            yaml.set("active-level-type", activeLevelTypeId);
            yaml.set("saved-at", System.currentTimeMillis());
            yaml.set("participants", participants.stream().map(UUID::toString).sorted().toList());
            yaml.set("storage.wood", woodScrap);
            yaml.set("storage.iron", ironScrap);
            yaml.set("storage.diamond", diamondScrap);
            yaml.set("storage.redstone", redstoneScrap);
            yaml.set("storage.coins", coins);
            yaml.set("elevator-chest", java.util.Arrays.asList(elevatorChestContents));
            savePlayers(yaml);
            saveCamps(yaml);
            yaml.save(saveFile());
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().warning("Failed to save Halls session " + id + ": " + ex.getMessage());
        }
    }

    private void restoreSavedSessionState(HallsSaveData save) {
        ghostPlayers.clear();
        savedCampStates.clear();
        savedCampStates.putAll(save.camps());
        elevatorChestContents = cloneArray(save.elevatorChest(), 27);
        elevatorChestSnapshotLocked = true;
        woodScrap = Math.max(0, save.woodScrap());
        ironScrap = Math.max(0, save.ironScrap());
        diamondScrap = Math.max(0, save.diamondScrap());
        redstoneScrap = Math.max(0, save.redstoneScrap());
        coins = Math.max(0, save.coins());
        activeFloorModifiers = HallsFloorModifiers.none();
        firstGhostCoinCacheDropped = false;
        compassTrailCountdown = 0;
        utilityCooldowns.clear();
        sculkMaulSplashCooldowns.clear();
        sculkMaulSplashing.clear();
        scannedFloorModifiers.clear();
        healthTotemLevels.clear();
        speedTotemLevels.clear();
        sculkRuntime.clearAll();
        for (Map.Entry<UUID, HallsSaveData.PlayerState> entry : save.players().entrySet()) {
            sculkRuntime.setSculk(entry.getKey(), entry.getValue().sculk());
        }
    }

    private void restoreSavedPlayer(Player player, HallsSaveData.PlayerState state) {
        if (player == null || state == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (int slot = 0; slot <= 8 && slot < state.hotbar().length; slot++) {
            inventory.setItem(slot, cloneOrNull(state.hotbar()[slot]));
        }
        ItemStack[] armor = cloneArray(state.armor(), 4);
        inventory.setArmorContents(armor);
        inventory.setItemInOffHand(cloneOrNull(state.offhand()));
        applyInventoryLimit(player);
    }

    private ItemStack[] cloneArray(ItemStack[] source, int size) {
        ItemStack[] copy = new ItemStack[size];
        if (source == null) {
            return copy;
        }
        for (int i = 0; i < Math.min(source.length, size); i++) {
            copy[i] = cloneOrNull(source[i]);
        }
        return copy;
    }

    private void savePlayers(YamlConfiguration yaml) {
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            String path = "players." + playerId;
            yaml.set(path + ".ghost", ghostPlayers.contains(playerId));
            yaml.set(path + ".sculk", sculkRuntime.sculkPercent(playerId));
            yaml.set(path + ".health-totem-level", healthTotemLevels.getOrDefault(playerId, 0));
            yaml.set(path + ".speed-totem-level", speedTotemLevels.getOrDefault(playerId, 0));
            if (player == null) {
                continue;
            }
            yaml.set(path + ".name", player.getName());
            PlayerInventory inventory = player.getInventory();
            List<ItemStack> hotbar = new ArrayList<>();
            for (int slot = 0; slot <= 8; slot++) {
                hotbar.add(cloneOrNull(inventory.getItem(slot)));
            }
            yaml.set(path + ".hotbar", hotbar);
            yaml.set(path + ".armor", java.util.Arrays.asList(inventory.getArmorContents()));
            yaml.set(path + ".offhand", cloneOrNull(inventory.getItemInOffHand()));
        }
    }

    private void saveCamps(YamlConfiguration yaml) {
        for (Map.Entry<Integer, List<HallsCampRuntime.PlotState>> entry : savedCampStates.entrySet()) {
            List<Map<String, Object>> plots = new ArrayList<>();
            for (HallsCampRuntime.PlotState state : entry.getValue()) {
                Map<String, Object> row = new HashMap<>();
                row.put("plot", state.plotId());
                row.put("building", state.buildingId());
                row.put("level", state.level());
                row.put("harvest-remaining", state.harvestRemaining());
                row.put("harvest-used", state.harvestUsed());
                row.put("storage", java.util.Arrays.asList(cloneArray(state.storageContents(), 54)));
                plots.add(row);
            }
            yaml.set("camps." + entry.getKey() + ".plots", plots);
        }
    }

    private File saveFile() {
        String participantsKey = participants.stream()
                .map(UUID::toString)
                .sorted()
                .collect(java.util.stream.Collectors.joining("_"));
        return new File(new File(dataFolder, "saves"), scenario.id() + "_" + participantsKey + ".yml");
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private void captureCurrentCampState() {
        if (!isCurrentFloorCamp()) {
            return;
        }
        List<HallsCampRuntime.PlotState> snapshot = campRuntime.snapshot();
        if (snapshot.isEmpty()) {
            savedCampStates.remove(currentFloor);
            return;
        }
        savedCampStates.put(currentFloor, snapshot);
    }

    private boolean isCurrentFloorCamp() {
        if (currentFloor < 1 || currentFloor > scenario.floorCount()) {
            return false;
        }
        return "camp".equalsIgnoreCase(scenario.floor(currentFloor).kind());
    }

    private void resetRunState() {
        ghostPlayers.clear();
        elevatorChestContents = new ItemStack[27];
        elevatorChestSnapshotLocked = false;
        woodScrap = 0;
        ironScrap = 0;
        diamondScrap = 0;
        redstoneScrap = 0;
        coins = 0;
        activeFloorModifiers = HallsFloorModifiers.none();
        firstGhostCoinCacheDropped = false;
        compassTrailCountdown = 0;
        utilityCooldowns.clear();
        sculkMaulSplashCooldowns.clear();
        sculkMaulSplashing.clear();
        scannedFloorModifiers.clear();
        healthTotemLevels.clear();
        speedTotemLevels.clear();
        sculkRuntime.clearAll();
    }

    private void resetCampHarvestForNewRun() {
        for (Map.Entry<Integer, List<HallsCampRuntime.PlotState>> entry : new ArrayList<>(savedCampStates.entrySet())) {
            List<HallsCampRuntime.PlotState> refreshed = new ArrayList<>();
            for (HallsCampRuntime.PlotState state : entry.getValue()) {
                HallsBuildingType building = buildingTypes.get(state.buildingId());
                if (building != null && defaultRunUses(building, Math.max(1, Math.min(3, state.level()))) > 0) {
                    int level = Math.max(1, Math.min(3, state.level()));
                    refreshed.add(new HallsCampRuntime.PlotState(
                            state.plotId(),
                            state.buildingId(),
                            level,
                            defaultRunUses(building, level),
                            0,
                            state.storageContents()));
                    continue;
                }
                refreshed.add(state);
            }
            savedCampStates.put(entry.getKey(), List.copyOf(refreshed));
        }
    }

    private int defaultRunUses(HallsBuildingType building, int level) {
        if (building == null) {
            return 0;
        }
        int configured = building.level(level).harvestUses();
        if (configured > 0) {
            return configured;
        }
        if (building.id().startsWith("sculk_purifier_")) {
            return 3;
        }
        if (building.id().equals("grindstone") || building.id().equals("forge")) {
            return 1;
        }
        if (building.id().equals("health_totem") || building.id().equals("speed_totem")) {
            return 1;
        }
        return 0;
    }

    private boolean applyHealthTotem(Player player, int level) {
        if (player == null) {
            return false;
        }
        if (healthTotemLevels.getOrDefault(player.getUniqueId(), 0) > 0) {
            player.sendActionBar(Component.text("You already carry a vitality totem blessing.", NamedTextColor.GRAY));
            return false;
        }
        int normalizedLevel = Math.max(1, Math.min(3, level));
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            player.sendActionBar(Component.text("Your max health cannot be changed here.", NamedTextColor.RED));
            return false;
        }
        removeModifier(attribute, HEALTH_TOTEM_MODIFIER);
        attribute.addModifier(new AttributeModifier(new org.bukkit.NamespacedKey(plugin, HEALTH_TOTEM_MODIFIER),
                2.0 * normalizedLevel, AttributeModifier.Operation.ADD_NUMBER));
        healthTotemLevels.put(player.getUniqueId(), normalizedLevel);
        player.setHealth(Math.min(attribute.getValue(), player.getHealth() + 2.0 * normalizedLevel));
        return true;
    }

    private boolean applySpeedTotem(Player player, int level) {
        if (player == null) {
            return false;
        }
        if (speedTotemLevels.getOrDefault(player.getUniqueId(), 0) > 0) {
            player.sendActionBar(Component.text("You already carry a speed totem blessing.", NamedTextColor.GRAY));
            return false;
        }
        int normalizedLevel = Math.max(1, Math.min(3, level));
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) {
            player.sendActionBar(Component.text("Your movement speed cannot be changed here.", NamedTextColor.RED));
            return false;
        }
        removeModifier(attribute, SPEED_TOTEM_MODIFIER);
        attribute.addModifier(new AttributeModifier(new org.bukkit.NamespacedKey(plugin, SPEED_TOTEM_MODIFIER),
                0.05 * normalizedLevel, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        speedTotemLevels.put(player.getUniqueId(), normalizedLevel);
        return true;
    }

    private void restoreSavedTotemBuffs(Player player, HallsSaveData.PlayerState state) {
        if (player == null || state == null) {
            return;
        }
        clearTotemBuffs(player);
        int healthLevel = Math.max(0, Math.min(3, state.healthTotemLevel()));
        if (healthLevel > 0) {
            AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
            if (attribute != null) {
                attribute.addModifier(new AttributeModifier(new org.bukkit.NamespacedKey(plugin, HEALTH_TOTEM_MODIFIER),
                        2.0 * healthLevel, AttributeModifier.Operation.ADD_NUMBER));
                healthTotemLevels.put(player.getUniqueId(), healthLevel);
            }
        }
        int speedLevel = Math.max(0, Math.min(3, state.speedTotemLevel()));
        if (speedLevel > 0) {
            AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.addModifier(new AttributeModifier(new org.bukkit.NamespacedKey(plugin, SPEED_TOTEM_MODIFIER),
                        0.05 * speedLevel, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
                speedTotemLevels.put(player.getUniqueId(), speedLevel);
            }
        }
    }

    private void reapplyActiveTotemBuffs(Player player) {
        if (player == null) {
            return;
        }
        int healthLevel = healthTotemLevels.getOrDefault(player.getUniqueId(), 0);
        int speedLevel = speedTotemLevels.getOrDefault(player.getUniqueId(), 0);
        clearTotemAttributeModifiers(player);
        if (healthLevel > 0) {
            AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
            if (attribute != null) {
                attribute.addModifier(new AttributeModifier(new org.bukkit.NamespacedKey(plugin, HEALTH_TOTEM_MODIFIER),
                        2.0 * healthLevel, AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        if (speedLevel > 0) {
            AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (attribute != null) {
                attribute.addModifier(new AttributeModifier(new org.bukkit.NamespacedKey(plugin, SPEED_TOTEM_MODIFIER),
                        0.05 * speedLevel, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
    }

    private void clearTotemBuffs(Player player) {
        if (player == null) {
            return;
        }
        clearTotemAttributeModifiers(player);
        healthTotemLevels.remove(player.getUniqueId());
        speedTotemLevels.remove(player.getUniqueId());
    }

    private void clearTotemAttributeModifiers(Player player) {
        if (player == null) {
            return;
        }
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            removeModifier(health, HEALTH_TOTEM_MODIFIER);
            player.setHealth(Math.min(player.getHealth(), health.getValue()));
        }
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            removeModifier(speed, SPEED_TOTEM_MODIFIER);
        }
    }

    private void removeModifier(AttributeInstance attribute, String key) {
        attribute.removeModifier(new org.bukkit.NamespacedKey(plugin, key));
    }

    private void giveStarterItem(Player player) {
        HallsItemType type = itemTypes.get(STARTER_ITEM_ID);
        if (type == null) {
            return;
        }
        ItemStack item = HallsItemFactory.create(plugin, type, 1);
        int slot = firstAvailableHotbarSlot(player.getInventory());
        if (slot >= 0) {
            player.getInventory().setItem(slot, item);
        }
    }

    private void cancelGameOverTask() {
        if (gameOverTask != null) {
            gameOverTask.cancel();
            gameOverTask = null;
        }
    }

    private void cancelFloorBuildTask() {
        if (floorBuildTask != null) {
            floorBuildTask.cancel();
            floorBuildTask = null;
        }
    }

    private void tickPhysicsDrops() {
        if (!running || physicsDrops.isEmpty()) {
            stopPhysicsDropTask();
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (PhysicsDrop drop : Set.copyOf(physicsDrops.values())) {
            if (!seen.add(drop.interactionId())) {
                continue;
            }
            if (!drop.settled()) {
                tickPhysicsDrop(drop);
            }
        }
        if (physicsDrops.values().stream().allMatch(PhysicsDrop::settled)) {
            stopPhysicsDropTask();
        }
    }

    private void tickPhysicsDrop(PhysicsDrop drop) {
        Entity interaction = Bukkit.getEntity(drop.interactionId());
        Entity display = Bukkit.getEntity(drop.displayId());
        if (interaction == null || display == null) {
            removePhysicsDrop(drop);
            return;
        }
        Location next = drop.location().clone().add(drop.velocity());
        if (next.getY() < origin.y() - 0.5) {
            removePhysicsDrop(drop);
            return;
        }
        if (isDropInsideSolid(next)) {
            next.setX(drop.location().getX());
            next.setZ(drop.location().getZ());
            drop.velocity().setX(0.0);
            drop.velocity().setZ(0.0);
        }
        Optional<Double> supportY = supportYBelow(next);
        if (supportY.isPresent()) {
            next.setY(supportY.get() + DROP_DISPLAY_SUPPORT_OFFSET);
            if (drop.velocity().lengthSquared() <= DROP_SETTLE_VELOCITY_SQUARED) {
                drop.velocity().zero();
                drop.setSettled(true);
            } else {
                drop.velocity().setY(Math.max(0.0, -drop.velocity().getY() * 0.2));
                drop.velocity().multiply(0.66);
                if (drop.velocity().lengthSquared() <= DROP_SETTLE_VELOCITY_SQUARED) {
                    drop.velocity().zero();
                    drop.setSettled(true);
                }
            }
        } else {
            drop.velocity().setY(Math.max(-0.55, drop.velocity().getY() - 0.04));
            drop.velocity().multiply(new Vector(0.96, 0.98, 0.96));
        }
        drop.location().setX(next.getX());
        drop.location().setY(next.getY());
        drop.location().setZ(next.getZ());
        display.teleport(next);
        interaction.teleport(next.clone().add(0.0, -0.15, 0.0));
    }

    private boolean isDropInsideSolid(Location location) {
        int blockY = (int) Math.floor(location.getY() - 0.08);
        Block block = world.getBlockAt(location.getBlockX(), blockY, location.getBlockZ());
        return block.getType().isSolid();
    }

    private Optional<Double> supportYBelow(Location location) {
        Block block = world.getBlockAt(location.getBlockX(), (int) Math.floor(location.getY() - 0.08), location.getBlockZ());
        if (block.getType().isSolid()) {
            return Optional.of(block.getY() + 1.0);
        }
        return breakableSupportYBelow(location);
    }

    private Optional<Double> breakableSupportYBelow(Location location) {
        double bestY = Double.NEGATIVE_INFINITY;
        for (BreakableProp prop : Set.copyOf(breakableProps.values())) {
            if (location.getX() < prop.x() || location.getX() > prop.x() + 1.0
                    || location.getZ() < prop.z() || location.getZ() > prop.z() + 1.0) {
                continue;
            }
            double topY = prop.y() + 1.0;
            if (location.getY() >= topY - 0.18 && location.getY() <= topY + 0.7 && topY > bestY) {
                bestY = topY;
            }
        }
        return bestY == Double.NEGATIVE_INFINITY ? Optional.empty() : Optional.of(bestY);
    }

    private void wakePhysicsDropsNear(Location location) {
        if (location == null) {
            return;
        }
        boolean wokeAny = false;
        Set<UUID> seen = new HashSet<>();
        for (PhysicsDrop drop : Set.copyOf(physicsDrops.values())) {
            if (!seen.add(drop.interactionId()) || !drop.settled()
                    || !drop.location().getWorld().equals(location.getWorld())
                    || drop.location().distanceSquared(location) > 4.0) {
                continue;
            }
            drop.setSettled(false);
            drop.velocity().setY(0.04);
            wokeAny = true;
        }
        if (wokeAny) {
            startPhysicsDropTask();
        }
    }

    private int firstAvailableHotbarSlot(PlayerInventory inventory) {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

    private boolean tryEquipEmptyArmorSlot(PlayerInventory inventory, ItemStack item) {
        org.bukkit.inventory.EquipmentSlot slot = armorSlot(item);
        if (slot == null || inventory.getItem(slot) != null) {
            return false;
        }
        inventory.setItem(slot, item);
        return true;
    }

    private org.bukkit.inventory.EquipmentSlot armorSlot(ItemStack item) {
        return switch (item.getType()) {
            case LEATHER_HELMET, CHAINMAIL_HELMET, IRON_HELMET, GOLDEN_HELMET, DIAMOND_HELMET, NETHERITE_HELMET,
                 TURTLE_HELMET -> org.bukkit.inventory.EquipmentSlot.HEAD;
            case LEATHER_CHESTPLATE, CHAINMAIL_CHESTPLATE, IRON_CHESTPLATE, GOLDEN_CHESTPLATE, DIAMOND_CHESTPLATE,
                 NETHERITE_CHESTPLATE, ELYTRA -> org.bukkit.inventory.EquipmentSlot.CHEST;
            case LEATHER_LEGGINGS, CHAINMAIL_LEGGINGS, IRON_LEGGINGS, GOLDEN_LEGGINGS, DIAMOND_LEGGINGS,
                 NETHERITE_LEGGINGS -> org.bukkit.inventory.EquipmentSlot.LEGS;
            case LEATHER_BOOTS, CHAINMAIL_BOOTS, IRON_BOOTS, GOLDEN_BOOTS, DIAMOND_BOOTS, NETHERITE_BOOTS -> org.bukkit.inventory.EquipmentSlot.FEET;
            default -> null;
        };
    }

    private ItemStack blueprintPlaceholder() {
        return namedItem(Material.PAPER, "Building Blueprint", NamedTextColor.AQUA);
    }

    private ItemStack coinItem(int amount) {
        ItemStack item = namedItem(Material.GOLD_NUGGET, amount == 1 ? "Coin" : amount + " Coins", NamedTextColor.YELLOW);
        item.setAmount(Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "hoc_coin"),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack namedItem(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack scrapItem(Material material, String name, NamedTextColor color, PropReward reward, int amount) {
        ItemStack item = namedItem(material, name, color);
        item.setAmount(Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "hoc_scrap_type"),
                    PersistentDataType.STRING,
                    reward.name()
            );
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "hoc_scrap_unique"),
                    PersistentDataType.STRING,
                    UUID.randomUUID().toString()
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    private void markUniqueScrapItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "hoc_scrap_unique"),
                PersistentDataType.STRING,
                UUID.randomUUID().toString()
        );
        item.setItemMeta(meta);
    }

    private PropReward scrapType(ItemStack item) {
        if (!isScrapItem(item)) {
            return null;
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "hoc_scrap_type"), PersistentDataType.STRING);
        try {
            return PropReward.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isScrapItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(new org.bukkit.NamespacedKey(plugin, "hoc_scrap_type"), PersistentDataType.STRING);
    }

    private boolean isCoinItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "hoc_coin"), PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void applyInventoryLimit(Player player) {
        ItemStack barrier = lockedSlotItem();
        for (int slot = 9; slot <= 35; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current == null || current.getType().isAir() || isLockedSlotItem(plugin, current)) {
                player.getInventory().setItem(slot, barrier.clone());
            }
        }
        player.updateInventory();
    }

    private void restoreInventoryLimit(Player player) {
        clearLockedInventoryBarriers(player);
    }

    public static void clearLockedInventoryBarriers(JavaPlugin plugin, Player player) {
        if (player == null) {
            return;
        }
        for (int slot = 9; slot <= 35; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isLockedSlotItem(plugin, item)) {
                player.getInventory().setItem(slot, null);
            }
        }
        player.updateInventory();
    }

    public static boolean isLockedSlotItem(JavaPlugin plugin, ItemStack item) {
        if (plugin == null || item == null || item.getType() != Material.BARRIER || !item.hasItemMeta()) {
            return false;
        }
        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "hoc_locked_inventory_slot"), PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void clearLockedInventoryBarriers(Player player) {
        clearLockedInventoryBarriers(plugin, player);
    }

    private ItemStack lockedSlotItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Unavailable Slot", NamedTextColor.RED));
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "hoc_locked_inventory_slot"),
                    PersistentDataType.BYTE,
                    (byte) 1
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    private void setBlock(int x, int y, int z, Material material) {
        setBlock(x, y, z, material, null);
    }

    private void setBlock(int x, int y, int z, Material material, BlockFace facing) {
        Block block = world.getBlockAt(x, y, z);
        snapshots.add(new BlockSnapshot(x, y, z, block.getBlockData().clone()));
        if (block.getState(false) instanceof Container container) {
            container.getInventory().clear();
        }
        block.setType(material, false);
        if (material == Material.REDSTONE_LAMP && block.getBlockData() instanceof Lightable lightable) {
            lightable.setLit(true);
            block.setBlockData(lightable, false);
        }
        if (facing != null && block.getBlockData() instanceof Directional directional) {
            directional.setFacing(facing);
            block.setBlockData(directional, false);
        }
        if (facing != null && block.getBlockData() instanceof MultipleFacing multipleFacing) {
            for (BlockFace face : multipleFacing.getAllowedFaces()) {
                multipleFacing.setFace(face, multipleFacingState(material, facing, face, x, y, z));
            }
            block.setBlockData(multipleFacing, false);
        }
    }

    private boolean multipleFacingState(Material material, BlockFace configuredFace, BlockFace face, int x, int y, int z) {
        if (isDoorBar(material)) {
            return face == BlockFace.EAST || face == BlockFace.WEST;
        }
        if (material == Material.SCULK_VEIN) {
            return face == configuredFace;
        }
        return world.getBlockAt(x + face.getModX(), y + face.getModY(), z + face.getModZ()).getType().isSolid();
    }

    private boolean isDoorBar(Material material) {
        String name = material.name();
        return name.endsWith("_BARS") || name.equals("IRON_BARS") || name.equals("COPPER_BARS");
    }

    private Material firstMaterial(String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                return material;
            }
        }
        return Material.IRON_BARS;
    }

    private final class FloorBuildJob {
        private final int floor;
        private int clearRadius;
        private int clearX;
        private int stage;
        private int roomIndex;
        private int corridorIndex;
        private int contentRoomIndex;
        private int rareBreakableRoomIndex = -1;
        private int ticksElapsed;
        private ExplorationBuild build;
        private List<HallsExplorationGenerator.Cell> corridorShellCells = List.of();
        private Set<HallsExplorationGenerator.Cell> reservedCells = Set.of();
        private long contentStartedNanos;
        private int contentBreakablesBefore;

        private FloorBuildJob(int floor, int clearRadius) {
            this.floor = floor;
            this.clearRadius = clearRadius;
        }

        private void tick() {
            if (!running) {
                cancelFloorBuildTask();
                return;
            }
            ticksElapsed++;
            switch (stage) {
                case 0 -> plan();
                case 1 -> clearNextColumns();
                case 2 -> buildElevatorPass();
                case 3 -> buildNextRoom();
                case 4 -> buildNextCorridorCells();
                case 5 -> buildTraps();
                case 6 -> buildNextRoomContents();
                default -> finish();
            }
        }

        private void plan() {
            captureElevatorChestContents();
            removeSessionEntities();
            build = planExplorationBuild(floor);
            clearRadius = Math.max(clearRadius, activeClearRadius);
            clearX = origin.x() - clearRadius;
            currentFloor = floor;
            stage = 1;
        }

        private void clearNextColumns() {
            int maxX = origin.x() + clearRadius;
            int endX = Math.min(maxX, clearX + CLEAR_COLUMNS_PER_TICK - 1);
            clearBuildVolumeColumns(clearX, endX, clearRadius);
            clearX = endX + 1;
            if (clearX > maxX) {
                stage = 2;
            }
        }

        private void buildElevatorPass() {
            buildElevator();
            closeElevatorDoors();
            stage = 3;
        }

        private void buildNextRoom() {
            if (build.plan().rooms().isEmpty() || roomIndex >= build.plan().rooms().size()) {
                corridorShellCells = new ArrayList<>(build.plan().corridorShellCells());
                stage = 4;
                return;
            }
            HallsExplorationGenerator.Room room = build.plan().rooms().get(roomIndex++);
            buildLayoutRoom(room.layout(), room.startX(), origin.y(), room.startZ(),
                    room.openings(), build.levelType(), build.random());
        }

        private void buildNextCorridorCells() {
            if (corridorIndex >= corridorShellCells.size()) {
                stage = 5;
                return;
            }
            int end = Math.min(corridorShellCells.size(), corridorIndex + CORRIDOR_CELLS_PER_TICK);
            while (corridorIndex < end) {
                buildGeneratedCorridorCell(build.plan(), build.levelType(), corridorShellCells.get(corridorIndex++));
            }
        }

        private void buildTraps() {
            reservedCells = renderExplorationTraps(build);
            reservedCells = withReserved(reservedCells, renderExplorationLiquids(build, reservedCells));
            reservedCells = withReserved(reservedCells, renderExplorationVegetation(build, reservedCells));
            renderExplorationSculk(build, reservedCells);
            rareBreakableRoomIndex = rareBreakableRoomIndex(build.plan(), reservedCells, build.random());
            contentStartedNanos = System.nanoTime();
            contentBreakablesBefore = new HashSet<>(breakableProps.values()).size();
            stage = 6;
        }

        private void buildNextRoomContents() {
            if (contentRoomIndex >= build.plan().rooms().size()) {
                debugGeneration("contents", contentStartedNanos,
                        "breakables " + (new HashSet<>(breakableProps.values()).size() - contentBreakablesBefore));
                startExplorationMonsters(build);
                stage = 7;
                return;
            }
            placeGeneratedRoomContents(build.plan().rooms().get(contentRoomIndex), build.random(), build.floor(),
                    contentRoomIndex, build.floorDefinition(), build.levelType(), reservedCells,
                    contentRoomIndex == rareBreakableRoomIndex);
            contentRoomIndex++;
        }

        private void finish() {
            if (ticksElapsed < MIN_ELEVATOR_TRANSITION_TICKS) {
                return;
            }
            restoreElevatorChestContents();
            closeElevatorDoors();
            floorStartedAtMillis = System.currentTimeMillis();
            teleportParticipantsToElevator("Floor " + floor, "Gather what you can.");
            applyCompassModifier();
            openElevatorDoors();
            transitioning = false;
            world.playSound(new Location(world, origin.x() + 0.5, origin.y() + 1.0, origin.z() + 0.5),
                    Sound.BLOCK_IRON_DOOR_OPEN, 0.9f, 0.8f);
            cancelFloorBuildTask();
        }
    }

    private record RoomBounds(int minX, int maxX, int minZ, int maxZ) {
        private static RoomBounds of(RoomPlacement room) {
            return new RoomBounds(room.startX() - 1, room.startX() + room.layout().width(),
                    room.startZ() - 1, room.startZ() + room.layout().depth());
        }

        private RoomBounds inflate(int amount) {
            return new RoomBounds(minX - amount, maxX + amount, minZ - amount, maxZ + amount);
        }

        private boolean intersects(RoomBounds other) {
            return minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        private boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private record BlockSnapshot(int x, int y, int z, BlockData blockData) {
    }

    private record ExplorationBuild(int floor,
                                    HallsScenario.FloorDefinition floorDefinition,
                                    HallsLevelType levelType,
                                    Random random,
                                    HallsExplorationGenerator.Plan plan) {
    }

    private static final class PhysicsDrop {
        private final UUID interactionId;
        private final UUID displayId;
        private final ItemStack stack;
        private final Location location;
        private final Vector velocity;
        private boolean settled;

        private PhysicsDrop(UUID interactionId, UUID displayId, ItemStack stack, Location location, Vector velocity) {
            this.interactionId = interactionId;
            this.displayId = displayId;
            this.stack = stack;
            this.location = location;
            this.velocity = velocity;
        }

        private UUID interactionId() {
            return interactionId;
        }

        private UUID displayId() {
            return displayId;
        }

        private ItemStack stack() {
            return stack;
        }

        private Location location() {
            return location;
        }

        private Vector velocity() {
            return velocity;
        }

        private boolean settled() {
            return settled;
        }

        private void setSettled(boolean settled) {
            this.settled = settled;
        }
    }

    private record RoomPlacement(HallsLayout layout, int startX, int startZ) {
        private int centerX() {
            return startX + layout.width() / 2;
        }

        private int centerZ() {
            return startZ + layout.depth() / 2;
        }

        private int northExitZ() {
            return startZ - 1;
        }

        private int southExitZ() {
            return startZ + layout.depth();
        }

        private int westExitX() {
            return startX - 1;
        }

        private int eastExitX() {
            return startX + layout.width();
        }
    }

    private record Cell(int x, int z) {
    }

    private static final class BreakableProp {
        private final UUID interactionId;
        private final List<UUID> displayIds;
        private final int x;
        private final int y;
        private final int z;
        private final Material material;
        private final List<String> scrapDrops;
        private final List<HallsBreakableType.LootEntry> loot;
        private final int lootRolls;
        private final String breakMessage;
        private int health;

        private BreakableProp(UUID interactionId,
                              List<UUID> displayIds,
                              int x,
                              int y,
                              int z,
                              int health,
                              Material material,
                              List<String> scrapDrops,
                              List<HallsBreakableType.LootEntry> loot,
                              int lootRolls,
                              String breakMessage) {
            this.interactionId = interactionId;
            this.displayIds = List.copyOf(displayIds);
            this.x = x;
            this.y = y;
            this.z = z;
            this.health = health;
            this.material = material;
            this.scrapDrops = List.copyOf(scrapDrops);
            this.loot = List.copyOf(loot);
            this.lootRolls = Math.max(1, lootRolls);
            this.breakMessage = breakMessage;
        }

        private UUID interactionId() {
            return interactionId;
        }

        private List<UUID> displayIds() {
            return displayIds;
        }

        private int x() {
            return x;
        }

        private int y() {
            return y;
        }

        private int z() {
            return z;
        }

        private int health() {
            return health;
        }

        private Material material() {
            return material;
        }

        private List<String> scrapDrops() {
            return scrapDrops;
        }

        private List<HallsBreakableType.LootEntry> loot() {
            return loot;
        }

        private int lootRolls() {
            return lootRolls;
        }

        private String breakMessage() {
            return breakMessage;
        }

        private void damage() {
            health--;
        }
    }

    private enum PropReward {
        WOOD_SCRAP,
        IRON_SCRAP,
        DIAMOND_SCRAP,
        REDSTONE_SCRAP
    }

}
