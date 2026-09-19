package krispasi.omGames.hallsofcarnage;

import java.awt.Color;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class HallsFloorMapRenderer extends MapRenderer {
    private static final Color BACKGROUND = new Color(24, 20, 24);
    private static final Color FLOOR = new Color(176, 170, 142);
    private static final Color ELEVATOR = new Color(196, 64, 196);
    private static final Color PLAYER = new Color(64, 180, 255);
    private static final Color ENEMY = new Color(220, 48, 48);

    private final World world;
    private final int originX;
    private final int originZ;
    private final Set<HallsExplorationGenerator.Cell> cells;
    private final Set<UUID> participants;
    private final boolean showEnemies;
    private final int scale;

    private HallsFloorMapRenderer(World world,
                                  int originX,
                                  int originZ,
                                  Set<HallsExplorationGenerator.Cell> cells,
                                  Set<UUID> participants,
                                  boolean showEnemies) {
        super(false);
        this.world = world;
        this.originX = originX;
        this.originZ = originZ;
        this.cells = cells == null ? Set.of() : Set.copyOf(cells);
        this.participants = participants == null ? Set.of() : Set.copyOf(participants);
        this.showEnemies = showEnemies;
        this.scale = mapScale(this.cells, originX, originZ);
    }

    static ItemStack create(JavaPlugin plugin,
                            Player owner,
                            World world,
                            int originX,
                            int originZ,
                            Set<HallsExplorationGenerator.Cell> cells,
                            Set<UUID> participants,
                            boolean showEnemies) {
        MapView view = Bukkit.createMap(world);
        for (MapRenderer renderer : view.getRenderers()) {
            view.removeRenderer(renderer);
        }
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setScale(MapView.Scale.CLOSEST);
        view.addRenderer(new HallsFloorMapRenderer(world, originX, originZ, cells, participants, showEnemies));
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta != null) {
            meta.setMapView(view);
            meta.displayName(Component.text(showEnemies ? "Tactical Floor Map" : "Floor Map", NamedTextColor.GREEN));
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "hoc_elevator_compass"),
                    PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                canvas.setPixelColor(x, y, BACKGROUND);
            }
        }
        for (HallsExplorationGenerator.Cell cell : cells) {
            drawPoint(canvas, cell.x(), cell.z(), FLOOR, 1);
        }
        drawPoint(canvas, originX, originZ, ELEVATOR, 2);
        for (UUID playerId : participants) {
            Player participant = Bukkit.getPlayer(playerId);
            if (participant != null && participant.getWorld().equals(world)) {
                drawPoint(canvas, participant.getLocation().getBlockX(), participant.getLocation().getBlockZ(), PLAYER, 2);
            }
        }
        if (showEnemies) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("omgames_hoc_monster")) {
                    drawPoint(canvas, entity.getLocation().getBlockX(), entity.getLocation().getBlockZ(), ENEMY, 1);
                }
            }
        }
    }

    private void drawPoint(MapCanvas canvas, int worldX, int worldZ, Color color, int radius) {
        int x = 64 + (worldX - originX) / scale;
        int y = 64 + (worldZ - originZ) / scale;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int px = x + dx;
                int py = y + dy;
                if (px >= 0 && px < 128 && py >= 0 && py < 128) {
                    canvas.setPixelColor(px, py, color);
                }
            }
        }
    }

    private static int mapScale(Set<HallsExplorationGenerator.Cell> cells, int originX, int originZ) {
        int max = 32;
        for (HallsExplorationGenerator.Cell cell : cells) {
            max = Math.max(max, Math.max(Math.abs(cell.x() - originX), Math.abs(cell.z() - originZ)));
        }
        return Math.max(1, (int) Math.ceil(max / 56.0));
    }
}
