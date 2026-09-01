package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
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

    private final JavaPlugin plugin;
    private final int id;
    private final HallsScenario scenario;
    private final World world;
    private final HallsConfig.BlockPoint origin;
    private final File dataFolder;
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
    private boolean transitioning;
    private boolean running;

    public HallsSession(JavaPlugin plugin,
                        int id,
                        HallsScenario scenario,
                        World world,
                        HallsConfig.BlockPoint origin,
                        File dataFolder,
                        List<Player> players) {
        this.plugin = plugin;
        this.id = id;
        this.scenario = scenario;
        this.world = world;
        this.origin = origin;
        this.dataFolder = dataFolder;
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
        clearBuildVolume();
        buildElevator();
        HallsLayout layout = HallsLayoutLoader.load(new File(dataFolder, "level/special/start_floor.txt"));
        int roomStartZ = origin.z() + ELEVATOR_OUTER_RADIUS + 6;
        buildLayoutRoom(layout, origin.x() - layout.width() / 2, origin.y(), roomStartZ,
                Map.of(BlockFace.NORTH, layout.width() / 2));
        buildConnector(origin.x(), origin.y(), origin.z() + ELEVATOR_OUTER_RADIUS + 1, roomStartZ - 1);
        spawnBreakableProp(origin.x() - layout.width() / 2 + 1, origin.y(), roomStartZ + 1, Material.BARREL, 3, PropReward.BLUEPRINT);
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
        buildElevator();
        restoreElevatorChestContents();
        currentFloor = floor;
        buildExplorationRooms(floor);
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
        List<HallsLayout> layouts = loadExplorationLayouts();
        HallsScenario.FloorDefinition floorDefinition = scenario.floor(floor);
        Random random = new Random((((long) id) << 32) ^ floor);
        ExplorationPlan plan = generateNormalExplorationRooms(layouts, floorDefinition, random);
        if (plan.rooms().isEmpty()) {
            return;
        }

        RoomNode first = plan.rooms().getFirst();
        first.openings().put(BlockFace.NORTH, first.placement().layout().width() / 2);
        List<List<ConnectorPoint>> connectorPaths = new ArrayList<>();
        for (RoomConnection connection : plan.connections()) {
            List<ConnectorPoint> path = findConnectorPath(connection.fromDoor(), connection.toDoor(), plan.rooms(), random);
            if (path.isEmpty()) {
                plugin.getLogger().warning("Skipped blocked Halls connector in session " + id + ".");
                continue;
            }
            connectorPaths.add(path);
        }
        for (RoomNode room : plan.rooms()) {
            buildLayoutRoom(room.placement().layout(), room.placement().startX(), origin.y(), room.placement().startZ(), room.openings());
        }
        buildConnector(origin.x(), origin.y(), origin.z() + ELEVATOR_OUTER_RADIUS + 1, first.placement().northExitZ());
        for (List<ConnectorPoint> path : connectorPaths) {
            buildConnectorPath(path, plan.rooms());
        }
        for (int i = 0; i < plan.rooms().size(); i++) {
            placeRoomContents(plan.rooms().get(i).placement(), random, floor, i, floorDefinition);
        }
    }

    private ExplorationPlan generateNormalExplorationRooms(List<HallsLayout> layouts,
                                                           HallsScenario.FloorDefinition floorDefinition,
                                                           Random random) {
        int targetRooms = Math.max(1, Math.min(28, floorDefinition.rooms()));
        List<RoomNode> rooms = new ArrayList<>();
        List<RoomConnection> connections = new ArrayList<>();
        HallsLayout firstLayout = layouts.get(random.nextInt(layouts.size()));
        RoomPlacement first = new RoomPlacement(firstLayout, origin.x() - firstLayout.width() / 2,
                origin.z() + ELEVATOR_OUTER_RADIUS + 6);
        RoomNode firstNode = new RoomNode(first);
        firstNode.openings().put(BlockFace.NORTH, firstLayout.width() / 2);
        rooms.add(firstNode);

        int attempts = 0;
        while (rooms.size() < targetRooms && attempts++ < targetRooms * 80) {
            RoomNode anchor = rooms.get(random.nextInt(rooms.size()));
            BlockFace direction = randomDirection(random);
            if (anchor.openings().containsKey(direction)) {
                continue;
            }
            HallsLayout layout = layouts.get(random.nextInt(layouts.size()));
            RoomPlacement candidate = candidateRoom(anchor.placement(), layout, direction, random);
            if (!isInsideBuildVolume(candidate) || intersectsProtectedElevator(candidate) || intersectsAny(candidate, rooms)) {
                continue;
            }
            RoomNode node = new RoomNode(candidate);
            BlockFace anchorFace = unusedDoorFace(anchor, direction, random);
            BlockFace candidateFace = unusedDoorFace(node, direction.getOppositeFace(), random);
            int anchorDoorOffset = doorOffset(anchor.placement().layout(), anchorFace, random);
            int candidateDoorOffset = doorOffset(candidate.layout(), candidateFace, random);
            ConnectorPoint fromDoor = connectorPoint(anchor.placement(), anchorFace, anchorDoorOffset);
            ConnectorPoint toDoor = connectorPoint(candidate, candidateFace, candidateDoorOffset);
            RoomConnection connection = new RoomConnection(anchor, node, anchorFace, candidateFace, fromDoor, toDoor);
            List<RoomNode> candidateRooms = new ArrayList<>(rooms);
            candidateRooms.add(node);
            if (findConnectorPath(fromDoor, toDoor, candidateRooms, random).isEmpty()) {
                continue;
            }
            anchor.openings().put(anchorFace, anchorDoorOffset);
            node.openings().put(candidateFace, candidateDoorOffset);
            connections.add(connection);
            rooms.add(node);
        }
        addExtraConnections(rooms, connections, random);
        return new ExplorationPlan(rooms, connections);
    }

    private RoomPlacement candidateRoom(RoomPlacement anchor, HallsLayout layout, BlockFace direction, Random random) {
        int gap = 8 + random.nextInt(14);
        int lateralRange = 10 + random.nextInt(14);
        int jitter = random.nextInt(lateralRange * 2 + 1) - lateralRange;
        return switch (direction) {
            case NORTH -> new RoomPlacement(layout, anchor.centerX() + jitter - layout.width() / 2,
                    anchor.startZ() - gap - layout.depth());
            case SOUTH -> new RoomPlacement(layout, anchor.centerX() + jitter - layout.width() / 2,
                    anchor.startZ() + anchor.layout().depth() + gap);
            case EAST -> new RoomPlacement(layout, anchor.startX() + anchor.layout().width() + gap,
                    anchor.centerZ() + jitter - layout.depth() / 2);
            case WEST -> new RoomPlacement(layout, anchor.startX() - gap - layout.width(),
                    anchor.centerZ() + jitter - layout.depth() / 2);
            default -> new RoomPlacement(layout, anchor.startX(), anchor.startZ());
        };
    }

    private void addExtraConnections(List<RoomNode> rooms, List<RoomConnection> connections, Random random) {
        int targetConnections = Math.max(1, rooms.size() / 4);
        int added = 0;
        int attempts = 0;
        while (added < targetConnections && attempts++ < rooms.size() * rooms.size() * 2) {
            RoomNode from = rooms.get(random.nextInt(rooms.size()));
            RoomNode to = rooms.get(random.nextInt(rooms.size()));
            if (from == to) {
                continue;
            }
            BlockFace fromFace = unusedDoorFace(from, randomDirection(random), random);
            BlockFace toFace = unusedDoorFace(to, randomDirection(random), random);
            if (from.openings().containsKey(fromFace) || to.openings().containsKey(toFace)
                    || hasConnection(connections, from, to)) {
                continue;
            }
            int fromOffset = doorOffset(from.placement().layout(), fromFace, random);
            int toOffset = doorOffset(to.placement().layout(), toFace, random);
            ConnectorPoint fromDoor = connectorPoint(from.placement(), fromFace, fromOffset);
            ConnectorPoint toDoor = connectorPoint(to.placement(), toFace, toOffset);
            List<ConnectorPoint> path = findConnectorPath(fromDoor, toDoor, rooms, random);
            if (path.isEmpty()) {
                continue;
            }
            from.openings().put(fromFace, fromOffset);
            to.openings().put(toFace, toOffset);
            connections.add(new RoomConnection(from, to, fromFace, toFace, fromDoor, toDoor));
            added++;
        }
    }

    private BlockFace unusedDoorFace(RoomNode room, BlockFace preferred, Random random) {
        if (!room.openings().containsKey(preferred) && random.nextDouble() < 0.65) {
            return preferred;
        }
        List<BlockFace> faces = new ArrayList<>(List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST));
        Collections.shuffle(faces, random);
        for (BlockFace face : faces) {
            if (!room.openings().containsKey(face)) {
                return face;
            }
        }
        return preferred;
    }

    private int doorOffset(HallsLayout layout, BlockFace face, Random random) {
        int span = face == BlockFace.NORTH || face == BlockFace.SOUTH ? layout.width() : layout.depth();
        if (span <= 2) {
            return Math.max(0, span / 2);
        }
        return 1 + random.nextInt(span - 2);
    }

    private boolean hasConnection(List<RoomConnection> connections, RoomNode first, RoomNode second) {
        for (RoomConnection connection : connections) {
            if ((connection.from() == first && connection.to() == second)
                    || (connection.from() == second && connection.to() == first)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInsideBuildVolume(RoomPlacement room) {
        return room.startX() - 2 >= origin.x() - CLEAR_RADIUS
                && room.startX() + room.layout().width() + 2 <= origin.x() + CLEAR_RADIUS
                && room.startZ() - 2 >= origin.z() - CLEAR_RADIUS
                && room.startZ() + room.layout().depth() + 2 <= origin.z() + CLEAR_RADIUS;
    }

    private boolean intersectsProtectedElevator(RoomPlacement room) {
        return RoomBounds.of(room).inflate(2).intersects(protectedElevatorBounds());
    }

    private boolean intersectsAny(RoomPlacement candidate, List<RoomNode> rooms) {
        RoomBounds candidateBounds = RoomBounds.of(candidate).inflate(4);
        for (RoomNode room : rooms) {
            if (candidateBounds.intersects(RoomBounds.of(room.placement()).inflate(4))) {
                return true;
            }
        }
        return false;
    }

    private BlockFace randomDirection(Random random) {
        BlockFace[] directions = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        return directions[random.nextInt(directions.length)];
    }

    private List<HallsLayout> loadExplorationLayouts() {
        File folder = new File(dataFolder, "level/howling_corridors");
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
        for (int x = origin.x() - CLEAR_RADIUS; x <= origin.x() + CLEAR_RADIUS; x++) {
            for (int y = origin.y() - 1; y <= origin.y() + CLEAR_HEIGHT; y++) {
                for (int z = origin.z() - CLEAR_RADIUS; z <= origin.z() + CLEAR_RADIUS; z++) {
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
    }

    private void buildLayoutRoom(HallsLayout layout, int startX, int y, int startZ, Map<BlockFace, Integer> openings) {
        Material floor = Material.PACKED_MUD;
        Material ceiling = Material.TUFF_BRICKS;
        Material wall = Material.DEEPSLATE_BRICKS;
        for (int z = -1; z <= layout.depth(); z++) {
            for (int x = -1; x <= layout.width(); x++) {
                boolean border = x < 0 || z < 0 || x >= layout.width() || z >= layout.depth();
                boolean opening = border && isRoomOpening(layout, x, z, openings);
                char cell = border ? 'X' : layout.at(x, z);
                int blockX = startX + x;
                int blockZ = startZ + z;
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
        for (int z = Math.min(startZ, endZ); z <= Math.max(startZ, endZ); z++) {
            int halfWidth = z == origin.z() + ELEVATOR_OUTER_RADIUS + 1 ? 1 : 0;
            buildCorridorCell(x, y, z, halfWidth, true);
        }
    }

    private ConnectorPoint connectorPoint(RoomPlacement room, BlockFace face, int offset) {
        return switch (face) {
            case NORTH -> new ConnectorPoint(room.startX() + offset, room.northExitZ());
            case SOUTH -> new ConnectorPoint(room.startX() + offset, room.southExitZ());
            case EAST -> new ConnectorPoint(room.eastExitX(), room.startZ() + offset);
            case WEST -> new ConnectorPoint(room.westExitX(), room.startZ() + offset);
            default -> new ConnectorPoint(room.centerX(), room.centerZ());
        };
    }

    private void buildCorridorCell(int x, int y, int z, int halfWidth, boolean northSouth) {
        Material floor = Material.PACKED_MUD;
        Material wall = Material.DEEPSLATE_BRICKS;
        int pathMin = -halfWidth;
        int pathMax = halfWidth;
        for (int offset = pathMin - 1; offset <= pathMax + 1; offset++) {
            int blockX = northSouth ? x + offset : x;
            int blockZ = northSouth ? z : z + offset;
            if (isProtectedElevatorCell(blockX, blockZ)) {
                continue;
            }
            setBlock(blockX, y - 1, blockZ, floor);
            setBlock(blockX, y + 3, blockZ, wall);
            for (int dy = 0; dy < 3; dy++) {
                boolean path = offset >= pathMin && offset <= pathMax;
                setBlock(blockX, y + dy, blockZ, path ? Material.AIR : wall);
            }
        }
    }

    private List<ConnectorPoint> findConnectorPath(ConnectorPoint start, ConnectorPoint end, List<RoomNode> rooms, Random random) {
        int maximumLength = maximumConnectorLength(start, end);
        for (int attempt = 0; attempt < 5; attempt++) {
            List<ConnectorPoint> targets = connectorWaypoints(start, end, random);
            List<ConnectorPoint> wholePath = new ArrayList<>();
            ConnectorPoint current = start;
            boolean complete = true;
            for (ConnectorPoint target : targets) {
                List<ConnectorPoint> segment = findDirectConnectorPath(current, target, rooms, random);
                if (segment.isEmpty()) {
                    complete = false;
                    break;
                }
                if (!wholePath.isEmpty()) {
                    segment = segment.subList(1, segment.size());
                }
                wholePath.addAll(segment);
                current = target;
            }
            if (complete && wholePath.size() <= maximumLength && isContiguousPath(wholePath)
                    && wholePath.size() >= manhattanDistance(start, end) + 4) {
                return wholePath;
            }
        }
        List<ConnectorPoint> directPath = findDirectConnectorPath(start, end, rooms, random);
        return isContiguousPath(directPath) ? directPath : List.of();
    }

    private List<ConnectorPoint> connectorWaypoints(ConnectorPoint start, ConnectorPoint end, Random random) {
        List<ConnectorPoint> waypoints = new ArrayList<>();
        int minX = Math.max(origin.x() - CLEAR_RADIUS + 4, Math.min(start.x(), end.x()) - 8);
        int maxX = Math.min(origin.x() + CLEAR_RADIUS - 4, Math.max(start.x(), end.x()) + 8);
        int minZ = Math.max(origin.z() - CLEAR_RADIUS + 4, Math.min(start.z(), end.z()) - 8);
        int maxZ = Math.min(origin.z() + CLEAR_RADIUS - 4, Math.max(start.z(), end.z()) + 8);
        int count = random.nextDouble() < 0.75 ? 1 : 2;
        for (int i = 0; i < count; i++) {
            if (random.nextBoolean()) {
                int x = minX + random.nextInt(Math.max(1, maxX - minX + 1));
                int z = i % 2 == 0 ? start.z() : end.z();
                waypoints.add(new ConnectorPoint(x, z));
            } else {
                int x = i % 2 == 0 ? start.x() : end.x();
                int z = minZ + random.nextInt(Math.max(1, maxZ - minZ + 1));
                waypoints.add(new ConnectorPoint(x, z));
            }
        }
        waypoints.add(end);
        return waypoints;
    }

    private int manhattanDistance(ConnectorPoint start, ConnectorPoint end) {
        return Math.abs(start.x() - end.x()) + Math.abs(start.z() - end.z());
    }

    private int maximumConnectorLength(ConnectorPoint start, ConnectorPoint end) {
        return Math.min(72, manhattanDistance(start, end) * 2 + 18);
    }

    private boolean isContiguousPath(List<ConnectorPoint> path) {
        if (path.isEmpty()) {
            return false;
        }
        for (int i = 1; i < path.size(); i++) {
            if (manhattanDistance(path.get(i - 1), path.get(i)) != 1) {
                return false;
            }
        }
        return true;
    }

    private List<ConnectorPoint> findDirectConnectorPath(ConnectorPoint start, ConnectorPoint end, List<RoomNode> rooms, Random random) {
        int minX = origin.x() - CLEAR_RADIUS + 1;
        int maxX = origin.x() + CLEAR_RADIUS - 1;
        int minZ = origin.z() - CLEAR_RADIUS + 1;
        int maxZ = origin.z() + CLEAR_RADIUS - 1;
        Deque<ConnectorPoint> queue = new ArrayDeque<>();
        Set<ConnectorPoint> visited = new HashSet<>();
        Map<ConnectorPoint, ConnectorPoint> previous = new HashMap<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            ConnectorPoint current = queue.removeFirst();
            if (current.equals(end)) {
                return reconstructPath(previous, end);
            }
            for (ConnectorPoint next : orderedNeighbors(current, end, random)) {
                if (next.x() < minX || next.x() > maxX || next.z() < minZ || next.z() > maxZ
                        || visited.contains(next) || isBlockedConnectorPoint(next, start, end, rooms)) {
                    continue;
                }
                visited.add(next);
                previous.put(next, current);
                queue.addLast(next);
            }
        }
        return List.of();
    }

    private List<ConnectorPoint> orderedNeighbors(ConnectorPoint point, ConnectorPoint end, Random random) {
        List<ConnectorPoint> neighbors = new ArrayList<>(List.of(
                new ConnectorPoint(point.x() + 1, point.z()),
                new ConnectorPoint(point.x() - 1, point.z()),
                new ConnectorPoint(point.x(), point.z() + 1),
                new ConnectorPoint(point.x(), point.z() - 1)
        ));
        Collections.shuffle(neighbors, random);
        Map<ConnectorPoint, Integer> penalties = new HashMap<>();
        for (ConnectorPoint neighbor : neighbors) {
            penalties.put(neighbor, random.nextInt(8));
        }
        neighbors.sort(Comparator.comparingInt(candidate ->
                Math.abs(candidate.x() - end.x()) + Math.abs(candidate.z() - end.z()) + penalties.get(candidate)));
        return neighbors;
    }

    private List<ConnectorPoint> reconstructPath(Map<ConnectorPoint, ConnectorPoint> previous, ConnectorPoint end) {
        List<ConnectorPoint> path = new ArrayList<>();
        ConnectorPoint current = end;
        path.add(current);
        while (previous.containsKey(current)) {
            current = previous.get(current);
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    private boolean isBlockedConnectorPoint(ConnectorPoint point, ConnectorPoint start, ConnectorPoint end, List<RoomNode> rooms) {
        if (point.equals(start) || point.equals(end)) {
            return false;
        }
        if (isProtectedElevatorCell(point.x(), point.z())) {
            return true;
        }
        for (RoomNode room : rooms) {
            if (RoomBounds.of(room.placement()).contains(point.x(), point.z())) {
                return true;
            }
        }
        return false;
    }

    private void buildConnectorPath(List<ConnectorPoint> path, List<RoomNode> rooms) {
        Set<ConnectorPoint> openCells = new HashSet<>(path);
        Set<ConnectorPoint> shellCells = new HashSet<>();
        for (ConnectorPoint point : openCells) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    shellCells.add(new ConnectorPoint(point.x() + dx, point.z() + dz));
                }
            }
        }
        Material floor = Material.PACKED_MUD;
        Material wall = Material.DEEPSLATE_BRICKS;
        for (ConnectorPoint point : shellCells) {
            if (isProtectedElevatorCell(point.x(), point.z())) {
                continue;
            }
            boolean open = openCells.contains(point);
            if (!open && isInsideRoomShell(point, rooms)) {
                continue;
            }
            setBlock(point.x(), origin.y() - 1, point.z(), floor);
            setBlock(point.x(), origin.y() + 3, point.z(), wall);
            for (int dy = 0; dy < 3; dy++) {
                setBlock(point.x(), origin.y() + dy, point.z(), open ? Material.AIR : wall);
            }
        }
    }

    private boolean isInsideRoomShell(ConnectorPoint point, List<RoomNode> rooms) {
        for (RoomNode room : rooms) {
            if (RoomBounds.of(room.placement()).contains(point.x(), point.z())) {
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
                setBlock(origin.x() + x, origin.y() + y, origin.z() + ELEVATOR_OUTER_RADIUS + 1,
                        y >= 3 ? Material.DEEPSLATE_BRICKS : Material.AIR);
            }
        }
    }

    private void closeElevatorDoors() {
        Material door = firstMaterial("WAXED_WEATHERED_COPPER_BARS", "WAXED_WEATHERED_COPPER_GRATE", "COPPER_BARS", "IRON_BARS");
        for (int y = 0; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                setBlock(origin.x() + x, origin.y() + y, origin.z() + ELEVATOR_OUTER_RADIUS, door, BlockFace.EAST);
                setBlock(origin.x() + x, origin.y() + y, origin.z() + ELEVATOR_OUTER_RADIUS + 1, Material.BLACK_CONCRETE);
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
                    propMaterial(i, roomIndex), propHealth(i), propReward(floor, roomIndex, i));
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

    private boolean isNearRoomExit(RoomPlacement room, int x, int z) {
        return x == room.layout().width() / 2 || z == room.layout().depth() / 2;
    }

    private Material propMaterial(int index, int roomIndex) {
        Material[] materials = {Material.BARREL, Material.OAK_LOG, Material.IRON_ORE, Material.REDSTONE_ORE, Material.DEEPSLATE_DIAMOND_ORE};
        return materials[Math.floorMod(index + roomIndex, materials.length)];
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

    private void spawnBreakableProp(int x, int y, int z, Material material, int health, PropReward reward) {
        Location displayLocation = new Location(world, x, y, z);
        BlockDisplay display = world.spawn(displayLocation, BlockDisplay.class, entity -> {
            entity.setBlock(material.createBlockData());
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_breakable");
        });
        Location hitboxLocation = new Location(world, x + 0.5, y, z + 0.5);
        Interaction interaction = world.spawn(hitboxLocation, Interaction.class, entity -> {
            entity.setInteractionWidth(1.0f);
            entity.setInteractionHeight(1.0f);
            entity.setResponsive(true);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_breakable");
        });
        BreakableProp prop = new BreakableProp(interaction.getUniqueId(), display.getUniqueId(), health, material, reward);
        breakableProps.put(interaction.getUniqueId(), prop);
        breakableProps.put(display.getUniqueId(), prop);
    }

    private void breakBreakableProp(BreakableProp prop) {
        Location dropLocation = null;
        Entity interaction = Bukkit.getEntity(prop.interactionId());
        if (interaction != null) {
            dropLocation = interaction.getLocation().clone().add(0.0, 0.25, 0.0);
        }
        removeBreakableProp(prop);
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
        Location spawnLocation = location.clone().add(0.0, 0.2, 0.0);
        ItemStack singleStack = stack.clone();
        ItemDisplay display = world.spawn(spawnLocation, ItemDisplay.class, entity -> {
            entity.setItemStack(singleStack.clone());
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
        PhysicsDrop drop = new PhysicsDrop(interaction.getUniqueId(), display.getUniqueId(), singleStack, spawnLocation,
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
        breakableProps.remove(prop.displayId());
        Entity interaction = Bukkit.getEntity(prop.interactionId());
        if (interaction != null) {
            interaction.remove();
        }
        Entity display = Bukkit.getEntity(prop.displayId());
        if (display != null) {
            display.remove();
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
            tickPhysicsDrop(drop);
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
        if (isSolidAt(next.clone().add(0.0, -0.05, 0.0))) {
            next.setY(Math.floor(next.getY()) + 0.12);
            drop.velocity().setY(Math.max(0.0, -drop.velocity().getY() * 0.2));
            drop.velocity().multiply(0.72);
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

    private boolean isSolidAt(Location location) {
        return location.getBlock().getType().isSolid();
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
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, "hoc_scrap_type"),
                    PersistentDataType.STRING,
                    reward.name()
            );
            item.setItemMeta(meta);
        }
        return item;
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

    private record ExplorationPlan(List<RoomNode> rooms, List<RoomConnection> connections) {
    }

    private static final class RoomNode {
        private final RoomPlacement placement;
        private final Map<BlockFace, Integer> openings = new HashMap<>();

        private RoomNode(RoomPlacement placement) {
            this.placement = placement;
        }

        private RoomPlacement placement() {
            return placement;
        }

        private Map<BlockFace, Integer> openings() {
            return openings;
        }
    }

    private record RoomConnection(
            RoomNode from,
            RoomNode to,
            BlockFace fromFace,
            BlockFace toFace,
            ConnectorPoint fromDoor,
            ConnectorPoint toDoor
    ) {
    }

    private record ConnectorPoint(int x, int z) {
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

    private record PhysicsDrop(
            UUID interactionId,
            UUID displayId,
            ItemStack stack,
            Location location,
            Vector velocity
    ) {
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
        private final UUID displayId;
        private final Material material;
        private final PropReward reward;
        private int health;

        private BreakableProp(UUID interactionId, UUID displayId, int health, Material material, PropReward reward) {
            this.interactionId = interactionId;
            this.displayId = displayId;
            this.health = health;
            this.material = material;
            this.reward = reward;
        }

        private UUID interactionId() {
            return interactionId;
        }

        private UUID displayId() {
            return displayId;
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
            return switch (reward) {
                case BLUEPRINT -> "You broke open a dusty barrel.";
                case WOOD_SCRAP -> "Recovered wood scrap.";
                case IRON_SCRAP -> "Recovered iron scrap.";
                case DIAMOND_SCRAP -> "Recovered diamond scrap.";
                case REDSTONE_SCRAP -> "Recovered redstone scrap.";
            };
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
}
