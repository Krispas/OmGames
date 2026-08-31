package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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

    public boolean isParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    public Set<UUID> participants() {
        return Set.copyOf(participants);
    }

    public void start() throws IOException {
        if (running) {
            return;
        }
        buildStartArea();
        running = true;
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
                player.teleport(fallback);
            }
        }
        restoreBlocks();
        running = false;
    }

    private void buildStartArea() throws IOException {
        clearBuildVolume();
        buildElevator();
        HallsLayout layout = HallsLayoutLoader.load(new File(dataFolder, "level/special/start_floor.txt"));
        int roomStartZ = origin.z() + ELEVATOR_OUTER_RADIUS + 6;
        buildLayoutRoom(layout, origin.x() - layout.width() / 2, origin.y(), roomStartZ);
        buildConnector(origin.x(), origin.y(), origin.z() + ELEVATOR_OUTER_RADIUS + 1, roomStartZ - 1);
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
                Material backMaterial = Math.abs(x) == ELEVATOR_OUTER_RADIUS ? corner : x == -2 ? side : back;
                Material frontMaterial = Math.abs(x) <= 1 ? door : Math.abs(x) == ELEVATOR_OUTER_RADIUS ? corner : side;
                setBlock(origin.x() + x, origin.y() + y, origin.z() - ELEVATOR_OUTER_RADIUS, backMaterial);
                setBlock(origin.x() + x, origin.y() + y, origin.z() + ELEVATOR_OUTER_RADIUS, frontMaterial);
            }
            for (int z = -ELEVATOR_INNER_RADIUS; z <= ELEVATOR_INNER_RADIUS; z++) {
                setBlock(origin.x() - ELEVATOR_OUTER_RADIUS, origin.y() + y, origin.z() + z, z == 0 ? machine : side);
                setBlock(origin.x() + ELEVATOR_OUTER_RADIUS, origin.y() + y, origin.z() + z, back);
            }
        }
        setBlock(origin.x() - ELEVATOR_OUTER_RADIUS, origin.y(), origin.z(), Material.CHEST);
        setBlock(origin.x() - ELEVATOR_OUTER_RADIUS, origin.y() + 1, origin.z(), Material.STONE_BUTTON, BlockFace.EAST);
        setBlock(origin.x() - ELEVATOR_OUTER_RADIUS, origin.y() + 2, origin.z(), Material.HOPPER);
    }

    private void buildLayoutRoom(HallsLayout layout, int startX, int y, int startZ) {
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
                }
            }
        }
        setBlock(startX + 1, y, startZ + 1, Material.BARREL);
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

    private void restoreBlocks() {
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            BlockSnapshot snapshot = snapshots.get(i);
            Block block = world.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
            block.setBlockData(snapshot.blockData(), false);
        }
        snapshots.clear();
    }

    private void setBlock(int x, int y, int z, Material material) {
        setBlock(x, y, z, material, null);
    }

    private void setBlock(int x, int y, int z, Material material, BlockFace facing) {
        Block block = world.getBlockAt(x, y, z);
        snapshots.add(new BlockSnapshot(x, y, z, block.getBlockData().clone()));
        block.setType(material, false);
        if (facing != null && block.getBlockData() instanceof Directional directional) {
            directional.setFacing(facing);
            block.setBlockData(directional, false);
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
}
