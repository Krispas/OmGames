package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class HallsSession {
    private static final int CLEAR_RADIUS = 24;
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
    private final Set<UUID> droppedSessionItems = new HashSet<>();
    private BukkitTask hudTask;
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
        return entity != null && breakableProps.containsKey(entity.getUniqueId());
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
            player.sendMessage(Component.text("The elevator doors grind open.", NamedTextColor.DARK_RED));
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
        buildLayoutRoom(layout, origin.x() - layout.width() / 2, origin.y(), roomStartZ, true);
        buildConnector(origin.x(), origin.y(), origin.z() + ELEVATOR_OUTER_RADIUS + 1, roomStartZ - 1);
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
        }, 20L);
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
        HallsLayout layout = loadExplorationLayout();
        int startX = origin.x() - layout.width() / 2;
        int startZ = origin.z() + ELEVATOR_OUTER_RADIUS + 6;
        buildLayoutRoom(layout, startX, origin.y(), startZ, false);
        buildConnector(origin.x(), origin.y(), origin.z() + ELEVATOR_OUTER_RADIUS + 1, startZ - 1);
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

    private HallsLayout loadExplorationLayout() {
        File file = new File(dataFolder, "level/howling_corridors/exploration_1.txt");
        try {
            return HallsLayoutLoader.load(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to load Halls exploration template " + file + ": " + ex.getMessage());
            return new HallsLayout(List.of(
                    "XXXXXXXXXXXXX",
                    "XBWOXOOOIOOOX",
                    "XOOOOOOOOOOOX",
                    "XOROOXOOOWOOX",
                    "XOOOOOOOOBIOX",
                    "XXXXXXXXXXXXX"
            ), 13, 6);
        }
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

    private void buildLayoutRoom(HallsLayout layout, int startX, int y, int startZ, boolean startRoom) {
        Material floor = Material.PACKED_MUD;
        Material ceiling = Material.TUFF_BRICKS;
        Material wall = Material.DEEPSLATE_BRICKS;
        for (int z = -1; z <= layout.depth(); z++) {
            for (int x = -1; x <= layout.width(); x++) {
                boolean border = x < 0 || z < 0 || x >= layout.width() || z >= layout.depth();
                char cell = border ? 'X' : layout.at(x, z);
                int blockX = startX + x;
                int blockZ = startZ + z;
                setBlock(blockX, y - 1, blockZ, floor);
                setBlock(blockX, y + ROOM_HEIGHT, blockZ, ceiling);
                if (border || cell == 'X') {
                    for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                        setBlock(blockX, y + dy, blockZ, wall);
                    }
                } else {
                    for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                        setBlock(blockX, y + dy, blockZ, Material.AIR);
                    }
                    applyLayoutMarker(cell, blockX, y, blockZ);
                }
            }
        }
        if (startRoom) {
            spawnBreakableProp(startX + 1, y, startZ + 1, Material.BARREL, 3, PropReward.BLUEPRINT);
        }
    }

    private void buildRectRoom(int startX, int y, int startZ, int width, int depth) {
        Material floor = Material.PACKED_MUD;
        Material ceiling = Material.TUFF_BRICKS;
        Material wall = Material.DEEPSLATE_BRICKS;
        for (int z = -1; z <= depth; z++) {
            for (int x = -1; x <= width; x++) {
                boolean border = x < 0 || z < 0 || x >= width || z >= depth;
                int blockX = startX + x;
                int blockZ = startZ + z;
                setBlock(blockX, y - 1, blockZ, floor);
                setBlock(blockX, y + ROOM_HEIGHT, blockZ, ceiling);
                for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                    setBlock(blockX, y + dy, blockZ, border ? wall : Material.AIR);
                }
            }
        }
    }

    private void buildConnector(int x, int y, int startZ, int endZ) {
        Material floor = Material.PACKED_MUD;
        Material wall = Material.DEEPSLATE_BRICKS;
        for (int z = Math.min(startZ, endZ); z <= Math.max(startZ, endZ); z++) {
            for (int dx = -1; dx <= 1; dx++) {
                setBlock(x + dx, y - 1, z, floor);
                setBlock(x + dx, y + 3, z, wall);
            }
            for (int dy = 0; dy < 3; dy++) {
                setBlock(x, y + dy, z, Material.AIR);
                setBlock(x - 1, y + dy, z, wall);
                setBlock(x + 1, y + dy, z, wall);
            }
        }
    }

    private void openElevatorDoors() {
        for (int y = 0; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                setBlock(origin.x() + x, origin.y() + y, origin.z() + ELEVATOR_OUTER_RADIUS, Material.AIR);
                setBlock(origin.x() + x, origin.y() + y, origin.z() + ELEVATOR_OUTER_RADIUS + 1, Material.AIR);
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

    private void applyLayoutMarker(char marker, int x, int y, int z) {
        switch (marker) {
            case 'B' -> spawnBreakableProp(x, y, z, Material.BARREL, 3, PropReward.BLUEPRINT);
            case 'W' -> spawnBreakableProp(x, y, z, Material.OAK_LOG, 2, PropReward.WOOD_SCRAP);
            case 'I' -> spawnBreakableProp(x, y, z, Material.IRON_ORE, 3, PropReward.IRON_SCRAP);
            case 'D' -> spawnBreakableProp(x, y, z, Material.DEEPSLATE_DIAMOND_ORE, 4, PropReward.DIAMOND_SCRAP);
            case 'R' -> spawnBreakableProp(x, y, z, Material.REDSTONE_ORE, 3, PropReward.REDSTONE_SCRAP);
            case 'L' -> setBlock(x, y + ROOM_HEIGHT - 1, z, Material.SEA_LANTERN);
            default -> {
            }
        }
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
        Item drop = world.dropItemNaturally(location, stack);
        drop.setPersistent(false);
        drop.addScoreboardTag("omgames_hoc_session_drop");
        droppedSessionItems.add(drop.getUniqueId());
    }

    private void removeSessionEntities() {
        for (BreakableProp prop : Set.copyOf(breakableProps.values())) {
            removeBreakableProp(prop);
        }
        breakableProps.clear();
        for (UUID entityId : Set.copyOf(droppedSessionItems)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
        droppedSessionItems.clear();
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

    private record BlockSnapshot(int x, int y, int z, BlockData blockData) {
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
