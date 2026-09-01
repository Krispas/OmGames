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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class HallsSession {
    private static final int CLEAR_RADIUS = 72;
    private static final int CLEAR_HEIGHT = 9;
    private static final int ROOM_HEIGHT = 5;
    private static final int ELEVATOR_INNER_RADIUS = 2;
    private static final int ELEVATOR_OUTER_RADIUS = 3;
    private static final int DISPLAY_INTERPOLATION_DELAY_TICKS = 1;
    private static final int DISPLAY_TELEPORT_DURATION_TICKS = 2;
    private static final double DROP_DISPLAY_SUPPORT_OFFSET = 0.08;
    private static final double DROP_SETTLE_VELOCITY_SQUARED = 0.0016;

    private final JavaPlugin plugin;
    private final int id;
    private final HallsScenario scenario;
    private final World world;
    private final HallsConfig.BlockPoint origin;
    private final File dataFolder;
    private final Map<String, HallsLevelType> levelTypes;
    private final Set<UUID> participants;
    private final List<BlockSnapshot> snapshots = new ArrayList<>();
    private final Map<UUID, BreakableProp> breakableProps = new HashMap<>();
    private final Map<UUID, PhysicsDrop> physicsDrops = new HashMap<>();
    private BukkitTask hudTask;
    private BukkitTask physicsDropTask;
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

    public HallsSession(JavaPlugin plugin,
                        int id,
                        HallsScenario scenario,
                        World world,
                        HallsConfig.BlockPoint origin,
                        File dataFolder,
                        Map<String, HallsLevelType> levelTypes,
                        List<Player> players) {
        this.plugin = plugin;
        this.id = id;
        this.scenario = scenario;
        this.world = world;
        this.origin = origin;
        this.dataFolder = dataFolder;
        this.levelTypes = levelTypes == null ? Map.of() : Map.copyOf(levelTypes);
        this.participants = new HashSet<>();
        for (Player player : players) {
            participants.add(player.getUniqueId());
        }
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

    public boolean isTransitioning() {
        return transitioning;
    }

    public boolean isParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    public Set<UUID> participants() {
        return Set.copyOf(participants);
    }

    public boolean isSessionEntity(Entity entity) {
        return entity != null && (breakableProps.containsKey(entity.getUniqueId())
                || physicsDrops.containsKey(entity.getUniqueId()));
    }

    public boolean handlePhysicsDropPickup(Player player, Entity entity) {
        if (player == null || entity == null || !running || !player.getWorld().equals(world)) {
            return false;
        }
        PhysicsDrop drop = physicsDrops.get(entity.getUniqueId());
        if (drop == null) {
            return false;
        }
        if (!player.getInventory().getItemInMainHand().getType().isAir()) {
            player.sendActionBar(Component.text("Use an empty hand to pick up Halls items.", NamedTextColor.RED));
            return true;
        }
        int slot = firstAvailableHotbarSlot(player.getInventory());
        if (slot < 0) {
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
        if (block.getX() != origin.x() - ELEVATOR_INNER_RADIUS
                || block.getY() != origin.y() + 1
                || block.getZ() != origin.z()) {
            return false;
        }
        if (transitioning) {
            player.sendMessage(Component.text("The elevator is already moving.", NamedTextColor.YELLOW));
        } else if (currentFloor < scenario.floorCount()) {
            transitioning = true;
            openElevatorDoors();
            world.playSound(block.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.9f, 0.7f);
            player.sendMessage(Component.text("The elevator begins its descent.", NamedTextColor.DARK_RED));
            startElevatorTransition(currentFloor + 1);
        } else {
            player.sendMessage(Component.text("No deeper placeholder floor is available.", NamedTextColor.YELLOW));
        }
        return true;
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
        buildExplorationFloor(floor);
        openElevatorDoors();
        return true;
    }

    public boolean handleScrapDeposit(Player player, Block block) {
        if (!running || player == null || block == null || !player.getWorld().equals(world)) {
            return false;
        }
        if (block.getX() != origin.x() - ELEVATOR_INNER_RADIUS
                || block.getY() != origin.y() + 2
                || block.getZ() != origin.z()) {
            return false;
        }
        int deposited = depositScrap(player.getInventory());
        if (deposited <= 0) {
            player.sendActionBar(Component.text("No scrap to deposit.", NamedTextColor.GRAY));
            return true;
        }
        coins += deposited;
        world.playSound(block.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.7f);
        player.sendActionBar(Component.text("Deposited " + deposited + " scrap.", NamedTextColor.GOLD));
        return true;
    }

    public void handlePlayerJoin(Player player) {
        if (player == null || !running || !participants.contains(player.getUniqueId())) {
            return;
        }
        applyInventoryLimit(player);
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

    public void start() throws IOException {
        if (running) {
            return;
        }
        buildStartArea();
        openElevatorDoors();
        running = true;
        startedAtMillis = System.currentTimeMillis();
        startHudTask();
        Location spawn = new Location(world, origin.x() + 0.5, origin.y() + 1.0, origin.z() + 0.5, 180.0f, 0.0f);
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.teleport(spawn);
            player.setGameMode(GameMode.ADVENTURE);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setRespawnLocation(spawn, true);
            player.getInventory().clear();
            applyInventoryLimit(player);
            player.sendMessage(Component.text("Entering " + scenario.name() + " floor 1.", NamedTextColor.DARK_RED));
        }
    }

    public void stop(Location fallback) {
        if (!running && snapshots.isEmpty()) {
            return;
        }
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && fallback != null && player.getWorld().equals(world)) {
                restoreInventoryLimit(player);
                player.getInventory().clear();
                player.teleport(fallback);
            } else if (player != null) {
                restoreInventoryLimit(player);
                player.getInventory().clear();
            }
        }
        restoreBlocks();
        removeSessionEntities();
        stopHudTask();
        running = false;
    }

    private void buildStartArea() throws IOException {
        currentFloor = 1;
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
        int roomStartZ = origin.z() + ELEVATOR_OUTER_RADIUS + 6;
        buildLayoutRoom(layout, origin.x() - layout.width() / 2, origin.y(), roomStartZ,
                Map.of(BlockFace.NORTH, layout.width() / 2), levelType, new Random((((long) id) << 32) ^ 1));
        buildConnector(origin.x(), origin.y(), origin.z() + ELEVATOR_OUTER_RADIUS + 1, roomStartZ - 1, levelType);
        spawnBreakableProp(origin.x() - layout.width() / 2 + 1, origin.y(), roomStartZ + 1,
                PropArchetype.BARREL, 3, PropReward.BLUEPRINT);
        closeElevatorDoors();
    }

    private void startElevatorTransition(int destinationFloor) {
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!running) {
                return;
            }
            buildExplorationFloor(destinationFloor);
            openElevatorDoors();
            transitioning = false;
            world.playSound(new Location(world, origin.x() + 0.5, origin.y() + 1.0, origin.z() + 0.5),
                    Sound.BLOCK_IRON_DOOR_OPEN, 0.9f, 0.8f);
        }, 200L);
    }

    private void buildExplorationFloor(int floor) {
        captureElevatorChestContents();
        removeSessionEntities();
        clearBuildVolume();
        activeClearRadius = clearRadiusFor(scenario.floor(floor));
        clearBuildVolume();
        buildElevator();
        currentFloor = floor;
        buildExplorationRooms(floor);
        restoreElevatorChestContents();
        closeElevatorDoors();
        teleportParticipantsToElevator("Floor " + floor, "Gather what you can.");
    }

    private void teleportParticipantsToElevator(String title, String subtitle) {
        Location spawn = new Location(world, origin.x() + 0.5, origin.y() + 1.0, origin.z() + 0.5, 180.0f, 0.0f);
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.teleport(spawn);
                player.sendTitle(title, subtitle, 10, 45, 15);
            }
        }
    }

    private void buildExplorationRooms(int floor) {
        HallsScenario.FloorDefinition floorDefinition = scenario.floor(floor);
        HallsLevelType levelType = levelTypeFor(floorDefinition);
        activeLevelTypeId = levelType.id();
        activeTargetRooms = Math.max(1, floorDefinition.rooms());
        activeGeneratedRooms = 0;
        List<HallsLayout> layouts = loadExplorationLayouts(levelType);
        Random random = new Random((((long) id) << 32) ^ floor);
        HallsExplorationGenerator.Plan plan = HallsExplorationGenerator.generate(
                origin.x(),
                origin.z(),
                activeClearRadius,
                ELEVATOR_OUTER_RADIUS,
                layouts,
                floorDefinition,
                random
        );
        int targetRooms = activeTargetRooms;
        int expansions = 0;
        while ((plan.rooms().size() < targetRooms || !plan.reachable()) && expansions++ < 4) {
            activeClearRadius += 32;
            clearBuildVolume();
            buildElevator();
            random = new Random(((((long) id) << 32) ^ floor) + expansions * 9973L);
            plan = HallsExplorationGenerator.generate(
                    origin.x(),
                    origin.z(),
                    activeClearRadius,
                    ELEVATOR_OUTER_RADIUS,
                    layouts,
                    floorDefinition,
                    random
            );
        }
        if (plan.rooms().isEmpty()) {
            return;
        }
        activeGeneratedRooms = plan.rooms().size();

        for (HallsExplorationGenerator.Room room : plan.rooms()) {
            buildLayoutRoom(room.layout(), room.startX(), origin.y(), room.startZ(), room.openings(), levelType, random);
        }
        buildGeneratedCorridorMask(plan, levelType);
        for (int i = 0; i < plan.rooms().size(); i++) {
            placeGeneratedRoomContents(plan.rooms().get(i), random, floor, i, floorDefinition, levelType);
        }
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
                layouts.add(HallsLayoutLoader.load(file));
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to load Halls exploration template " + file + ": " + ex.getMessage());
            }
        }
        return layouts.isEmpty() ? List.of(fallbackExplorationLayout()) : layouts;
    }

    private HallsLevelType levelTypeFor(HallsScenario.FloorDefinition floorDefinition) {
        if (floorDefinition == null) {
            return levelTypes.getOrDefault("howling_corridors", HallsLevelType.fallback("howling_corridors"));
        }
        return levelTypes.getOrDefault(floorDefinition.levelType(), HallsLevelType.fallback(floorDefinition.levelType()));
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
        for (int x = origin.x() - activeClearRadius; x <= origin.x() + activeClearRadius; x++) {
            for (int y = origin.y() - 1; y <= origin.y() + CLEAR_HEIGHT; y++) {
                for (int z = origin.z() - activeClearRadius; z <= origin.z() + activeClearRadius; z++) {
                    setBlock(x, y, z, Material.AIR);
                }
            }
        }
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
                Material frontMaterial = Math.abs(x) <= 1 ? door : Math.abs(x) == ELEVATOR_OUTER_RADIUS ? corner : Math.abs(x) == 2 ? side : back;
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
                boolean opening = border && isRoomOpening(layout, x, z, openings);
                char cell = border ? 'X' : layout.at(x, z);
                int blockX = startX + x;
                int blockZ = startZ + z;
                Material wall = roomWallMaterial(levelType, layout, x, z, blockX, blockZ);
                setBlock(blockX, y - 1, blockZ, floor);
                setBlock(blockX, y + ROOM_HEIGHT, blockZ, ceiling);
                if (!opening && (border || cell == 'X')) {
                    for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                        setBlock(blockX, y + dy, blockZ, wall);
                    }
                } else {
                    for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                        setBlock(blockX, y + dy, blockZ, opening && dy >= 3 ? wall : Material.AIR);
                    }
                }
            }
        }
    }

    private boolean isRoomOpening(HallsLayout layout, int x, int z, Map<BlockFace, Integer> openings) {
        int centerX = layout.width() / 2;
        int centerZ = layout.depth() / 2;
        if (z == -1) {
            return x == openings.getOrDefault(BlockFace.NORTH, centerX + 1000);
        }
        if (z == layout.depth()) {
            return x == openings.getOrDefault(BlockFace.SOUTH, centerX + 1000);
        }
        if (x == -1) {
            return z == openings.getOrDefault(BlockFace.WEST, centerZ + 1000);
        }
        if (x == layout.width()) {
            return z == openings.getOrDefault(BlockFace.EAST, centerZ + 1000);
        }
        return false;
    }

    private void buildConnector(int x, int y, int startZ, int endZ) {
        buildConnector(x, y, startZ, endZ, HallsLevelType.fallback("howling_corridors"));
    }

    private void buildConnector(int x, int y, int startZ, int endZ, HallsLevelType levelType) {
        for (int z = Math.min(startZ, endZ); z <= Math.max(startZ, endZ); z++) {
            int halfWidth = z == origin.z() + ELEVATOR_OUTER_RADIUS + 1 ? 1 : 0;
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
            if (isProtectedElevatorCell(blockX, blockZ)) {
                continue;
            }
            boolean path = offset >= pathMin && offset <= pathMax;
            setBlock(blockX, y - 1, blockZ, floor);
            setBlock(blockX, y + 3, blockZ, path && isCorridorLightCell(blockX, blockZ) ? levelType.light() : ceiling);
            for (int dy = 0; dy < 3; dy++) {
                HallsExplorationGenerator.Cell point = new HallsExplorationGenerator.Cell(blockX, blockZ);
                setBlock(blockX, y + dy, blockZ,
                        path ? Material.AIR : corridorWallMaterial(levelType, point, openCells));
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

    private Material roomWallMaterial(HallsLevelType levelType, HallsLayout layout, int x, int z, int blockX, int blockZ) {
        return wallMaterial(levelType, blockX, blockZ, isRoomPillarColumn(layout, x, z), 0x5A17);
    }

    private Material corridorWallMaterial(HallsLevelType levelType,
                                          HallsExplorationGenerator.Cell point,
                                          Set<HallsExplorationGenerator.Cell> openCells) {
        return wallMaterial(levelType, point.x(), point.z(), isCorridorPillarColumn(point, openCells), 0xC011);
    }

    private Material wallMaterial(HallsLevelType levelType, int x, int z, boolean pillar, long salt) {
        Random columnRandom = new Random((((long) x) * 341873128712L)
                ^ (((long) z) * 132897987541L)
                ^ (((long) id) << 24)
                ^ (((long) currentFloor) << 8)
                ^ salt);
        HallsLevelType.BlockPalette palette = pillar
                ? levelType.pillarPalette(columnRandom)
                : levelType.wallPalette(columnRandom);
        return palette.material(columnRandom);
    }

    private void buildGeneratedCorridorMask(HallsExplorationGenerator.Plan plan,
                                            HallsLevelType levelType) {
        Set<HallsExplorationGenerator.Cell> openCells = plan.corridorCells();
        Material floor = levelType.corridorFloor();
        Material ceiling = levelType.corridorCeiling();
        for (HallsExplorationGenerator.Cell point : plan.corridorShellCells()) {
            if (isProtectedElevatorCell(point.x(), point.z())) {
                continue;
            }
            boolean open = openCells.contains(point);
            boolean insideRoomShell = isInsideGeneratedRoomShell(point, plan.rooms());
            if (!open && insideRoomShell) {
                continue;
            }
            setBlock(point.x(), origin.y() - 1, point.z(), floor);
            if (!insideRoomShell) {
                setBlock(point.x(), origin.y() + 3, point.z(),
                        open && isCorridorLightCell(point.x(), point.z()) ? levelType.light() : ceiling);
            }
            for (int dy = 0; dy < 3; dy++) {
                setBlock(point.x(), origin.y() + dy, point.z(),
                        open ? Material.AIR : corridorWallMaterial(levelType, point, openCells));
            }
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

    private void openElevatorDoors() {
        for (int y = 0; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                setBlock(origin.x() + x, origin.y() + y, origin.z() + ELEVATOR_OUTER_RADIUS, Material.AIR);
            }
        }
        buildElevatorVestibule(true);
    }

    private void closeElevatorDoors() {
        Material door = firstMaterial("WAXED_WEATHERED_COPPER_BARS", "WAXED_WEATHERED_COPPER_GRATE", "COPPER_BARS", "IRON_BARS");
        for (int y = 0; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                setBlock(origin.x() + x, origin.y() + y, origin.z() + ELEVATOR_OUTER_RADIUS, door, BlockFace.EAST);
            }
        }
        buildElevatorVestibule(false);
    }

    private void buildElevatorVestibule(boolean open) {
        int z = origin.z() + ELEVATOR_OUTER_RADIUS + 1;
        Material wall = Material.DEEPSLATE_BRICKS;
        for (int x = -2; x <= 2; x++) {
            setBlock(origin.x() + x, origin.y() - 1, z, Material.PACKED_MUD);
            setBlock(origin.x() + x, origin.y() + 3, z, wall);
            setBlock(origin.x() + x, origin.y() + 4, z, wall);
            boolean sideWall = Math.abs(x) == 2;
            for (int y = 0; y <= 2; y++) {
                setBlock(origin.x() + x, origin.y() + y, z,
                        sideWall ? wall : open ? Material.AIR : Material.BLACK_CONCRETE);
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
        Cell light = cells.get(Math.floorMod(roomIndex * 3 + floor, cells.size()));
        setBlock(room.startX() + light.x(), origin.y() + ROOM_HEIGHT - 1, room.startZ() + light.z(), Material.SEA_LANTERN);
        int baseProps = Math.max(1, floorDefinition.breakables() / Math.max(1, floorDefinition.rooms()));
        int props = Math.min(cells.size(), baseProps + (roomIndex < floorDefinition.breakables() % Math.max(1, floorDefinition.rooms()) ? 1 : 0));
        for (int i = 0; i < props; i++) {
            Cell cell = cells.get(random.nextInt(cells.size()));
            spawnBreakableProp(room.startX() + cell.x(), origin.y(), room.startZ() + cell.z(),
                    propArchetype(i, roomIndex, HallsLevelType.fallback("howling_corridors")),
                    propHealth(i), propReward(floor, roomIndex, i));
        }
    }

    private void placeGeneratedRoomContents(HallsExplorationGenerator.Room room,
                                            Random random,
                                            int floor,
                                            int roomIndex,
                                            HallsScenario.FloorDefinition floorDefinition,
                                            HallsLevelType levelType) {
        List<Cell> cells = openInteriorCells(room);
        if (cells.isEmpty()) {
            return;
        }
        Cell light = cells.get(Math.floorMod(roomIndex * 3 + floor, cells.size()));
        setBlock(room.startX() + light.x(), origin.y() + ROOM_HEIGHT - 1, room.startZ() + light.z(), levelType.light());
        int baseProps = Math.max(1, floorDefinition.breakables() / Math.max(1, floorDefinition.rooms()));
        int props = Math.min(cells.size(), baseProps + (roomIndex < floorDefinition.breakables() % Math.max(1, floorDefinition.rooms()) ? 1 : 0));
        for (int i = 0; i < props; i++) {
            Cell cell = cells.get(random.nextInt(cells.size()));
            spawnBreakableProp(room.startX() + cell.x(), origin.y(), room.startZ() + cell.z(),
                    propArchetype(i, roomIndex, levelType), propHealth(i), propReward(floor, roomIndex, i));
        }
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

    private boolean isNearRoomExit(RoomPlacement room, int x, int z) {
        return x == room.layout().width() / 2 || z == room.layout().depth() / 2;
    }

    private boolean isNearRoomExit(HallsExplorationGenerator.Room room, int x, int z) {
        return x == room.layout().width() / 2 || z == room.layout().depth() / 2;
    }

    private PropArchetype propArchetype(int index, int roomIndex, HallsLevelType levelType) {
        PropArchetype[] archetypes = {
                PropArchetype.BARREL,
                PropArchetype.CHEST,
                PropArchetype.TABLE,
                PropArchetype.CHAIR,
                PropArchetype.STOOL,
                PropArchetype.RADIATOR,
                PropArchetype.METAL_BARREL
        };
        int salt = levelType == null ? 0 : levelType.id().hashCode();
        return archetypes[Math.floorMod(index + roomIndex + salt, archetypes.length)];
    }

    private int propHealth(int index) {
        return 2 + Math.floorMod(index, 3);
    }

    private PropReward propReward(int floor, int roomIndex, int index) {
        PropReward[] rewards = {PropReward.WOOD_SCRAP, PropReward.IRON_SCRAP, PropReward.REDSTONE_SCRAP, PropReward.DIAMOND_SCRAP};
        return rewards[Math.floorMod(floor + roomIndex + index, rewards.length)];
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
        Component message = Component.text("Floor " + currentFloor, NamedTextColor.DARK_RED)
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(formatElapsedSeconds(), NamedTextColor.GRAY))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Scrap W" + woodScrap + " I" + ironScrap
                        + " D" + diamondScrap + " R" + redstoneScrap, NamedTextColor.GOLD))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Coins " + coins, NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Sculk 0", NamedTextColor.AQUA));
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world)) {
                player.sendActionBar(message);
            }
        }
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
        Container container = elevatorChestContainer();
        if (container == null) {
            elevatorChestContents = new ItemStack[27];
            return;
        }
        ItemStack[] contents = container.getInventory().getContents();
        elevatorChestContents = new ItemStack[Math.max(27, contents.length)];
        for (int i = 0; i < contents.length; i++) {
            elevatorChestContents[i] = contents[i] == null ? null : contents[i].clone();
        }
        container.getInventory().clear();
    }

    private void restoreElevatorChestContents() {
        Container container = elevatorChestContainer();
        if (container == null) {
            return;
        }
        container.getInventory().setContents(elevatorChestContents);
    }

    private Container elevatorChestContainer() {
        Block block = world.getBlockAt(origin.x() - ELEVATOR_INNER_RADIUS, origin.y(), origin.z());
        return block.getState(false) instanceof Container container ? container : null;
    }

    private void spawnBreakableProp(int x, int y, int z, PropArchetype archetype, int health, PropReward reward) {
        List<UUID> displayIds = new ArrayList<>();
        for (PropPart part : archetype.parts()) {
            displayIds.add(spawnPropDisplay(x + part.offsetX(), y + part.offsetY(), z + part.offsetZ(), part.material()));
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
                archetype.particleMaterial(), reward, archetype.breakMessage());
        breakableProps.put(interaction.getUniqueId(), prop);
        for (UUID displayId : displayIds) {
            breakableProps.put(displayId, prop);
        }
    }

    private UUID spawnPropDisplay(int x, int y, int z, Material material) {
        Location displayLocation = new Location(world, x, y, z);
        BlockDisplay display = world.spawn(displayLocation, BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_breakable");
        });
        return display.getUniqueId();
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
            applyReward(prop.reward(), dropLocation);
        }
    }

    private void applyReward(PropReward reward, Location dropLocation) {
        switch (reward) {
            case BLUEPRINT -> dropSessionItem(dropLocation, blueprintPlaceholder());
            case WOOD_SCRAP -> {
                dropSessionItem(dropLocation, scrapItem(Material.STICK, "Wood Scrap", NamedTextColor.GOLD, reward));
            }
            case IRON_SCRAP -> {
                dropSessionItem(dropLocation, scrapItem(Material.RAW_IRON, "Iron Scrap", NamedTextColor.GRAY, reward));
            }
            case DIAMOND_SCRAP -> {
                dropSessionItem(dropLocation, scrapItem(Material.DIAMOND, "Diamond Scrap", NamedTextColor.AQUA, reward));
            }
            case REDSTONE_SCRAP -> {
                dropSessionItem(dropLocation, scrapItem(Material.REDSTONE, "Redstone Scrap", NamedTextColor.RED, reward));
            }
        }
    }

    private int depositScrap(PlayerInventory inventory) {
        int deposited = 0;
        for (int slot = 0; slot <= 8; slot++) {
            deposited += depositScrapStack(inventory.getItem(slot));
            if (isScrapItem(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        deposited += depositScrapStack(offhand);
        if (isScrapItem(offhand)) {
            inventory.setItemInOffHand(null);
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

    private void removeSessionEntities() {
        for (BreakableProp prop : Set.copyOf(breakableProps.values())) {
            removeBreakableProp(prop);
        }
        breakableProps.clear();
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

    private ItemStack blueprintPlaceholder() {
        return namedItem(Material.PAPER, "Building Blueprint", NamedTextColor.AQUA);
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

    private ItemStack scrapItem(Material material, String name, NamedTextColor color, PropReward reward) {
        ItemStack item = namedItem(material, name, color);
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
        if (facing != null && block.getBlockData() instanceof Directional directional) {
            directional.setFacing(facing);
            block.setBlockData(directional, false);
        }
        if (facing != null && block.getBlockData() instanceof MultipleFacing multipleFacing) {
            if (multipleFacing.getAllowedFaces().contains(facing)) {
                multipleFacing.setFace(facing, true);
            }
            if (multipleFacing.getAllowedFaces().contains(facing.getOppositeFace())) {
                multipleFacing.setFace(facing.getOppositeFace(), true);
            }
            if (multipleFacing.getAllowedFaces().contains(BlockFace.UP)) {
                multipleFacing.setFace(BlockFace.UP, true);
            }
            if (multipleFacing.getAllowedFaces().contains(BlockFace.DOWN)) {
                multipleFacing.setFace(BlockFace.DOWN, true);
            }
            block.setBlockData(multipleFacing, false);
        }
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
        private final PropReward reward;
        private final String breakMessage;
        private int health;

        private BreakableProp(UUID interactionId,
                              List<UUID> displayIds,
                              int x,
                              int y,
                              int z,
                              int health,
                              Material material,
                              PropReward reward,
                              String breakMessage) {
            this.interactionId = interactionId;
            this.displayIds = List.copyOf(displayIds);
            this.x = x;
            this.y = y;
            this.z = z;
            this.health = health;
            this.material = material;
            this.reward = reward;
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

        private PropReward reward() {
            return reward;
        }

        private String breakMessage() {
            return breakMessage;
        }

        private void damage() {
            health--;
        }
    }

    private enum PropReward {
        BLUEPRINT,
        WOOD_SCRAP,
        IRON_SCRAP,
        DIAMOND_SCRAP,
        REDSTONE_SCRAP
    }

    private enum PropArchetype {
        BARREL("You broke open a dusty barrel.", 1.0f, Material.BARREL,
                new PropPart(0, 0, 0, Material.BARREL)),
        CHEST("You smashed a rotting chest.", 1.0f, Material.CHEST,
                new PropPart(0, 0, 0, Material.CHEST)),
        TABLE("You splintered an old table.", 1.0f, Material.OAK_PLANKS,
                new PropPart(0, 0, 0, Material.OAK_FENCE),
                new PropPart(0, 1, 0, Material.OAK_PRESSURE_PLATE)),
        CHAIR("You kicked apart a wooden chair.", 1.0f, Material.OAK_STAIRS,
                new PropPart(0, 0, 0, Material.OAK_STAIRS),
                new PropPart(0, 1, 0, Material.OAK_TRAPDOOR)),
        STOOL("You cracked apart a small stool.", 0.8f, Material.OAK_FENCE,
                new PropPart(0, 0, 0, Material.OAK_FENCE),
                new PropPart(0, 1, 0, Material.OAK_PRESSURE_PLATE)),
        RADIATOR("You tore scrap from a dead radiator.", 1.0f, Material.IRON_BARS,
                new PropPart(0, 0, 0, Material.IRON_BARS),
                new PropPart(0, 1, 0, Material.SMOOTH_QUARTZ_SLAB)),
        METAL_BARREL("You dented open a metal barrel.", 1.0f, Material.CAULDRON,
                new PropPart(0, 0, 0, Material.CAULDRON),
                new PropPart(0, 1, 0, Material.HEAVY_WEIGHTED_PRESSURE_PLATE));

        private final String breakMessage;
        private final float hitboxHeight;
        private final Material particleMaterial;
        private final List<PropPart> parts;

        PropArchetype(String breakMessage, float hitboxHeight, Material particleMaterial, PropPart... parts) {
            this.breakMessage = breakMessage;
            this.hitboxHeight = hitboxHeight;
            this.particleMaterial = particleMaterial;
            this.parts = List.of(parts);
        }

        private String breakMessage() {
            return breakMessage;
        }

        private float hitboxHeight() {
            return hitboxHeight;
        }

        private Material particleMaterial() {
            return particleMaterial;
        }

        private List<PropPart> parts() {
            return parts;
        }
    }

    private record PropPart(int offsetX, int offsetY, int offsetZ, Material material) {
    }
}
