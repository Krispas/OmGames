package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HallsCampRuntime {
    public interface ScrapAccount {
        boolean spend(Map<String, Integer> cost);
    }

    private final JavaPlugin plugin;
    private final World world;
    private final Map<String, HallsBuildingType> buildingTypes;
    private final Map<String, HallsItemType> itemTypes;
    private final Function<HallsItemType, ItemStack> itemFactory;
    private final ScrapAccount scrapAccount;
    private final Map<UUID, Plot> plotsByEntity = new HashMap<>();
    private final Map<Integer, Plot> plotsById = new HashMap<>();

    public HallsCampRuntime(JavaPlugin plugin,
                            World world,
                            Map<String, HallsBuildingType> buildingTypes,
                            Map<String, HallsItemType> itemTypes,
                            Function<HallsItemType, ItemStack> itemFactory,
                            ScrapAccount scrapAccount) {
        this.plugin = plugin;
        this.world = world;
        this.buildingTypes = buildingTypes == null ? Map.of() : Map.copyOf(buildingTypes);
        this.itemTypes = itemTypes == null ? Map.of() : Map.copyOf(itemTypes);
        this.itemFactory = itemFactory;
        this.scrapAccount = scrapAccount;
    }

    public void clear() {
        Set<Plot> plots = new HashSet<>(plotsById.values());
        plotsById.clear();
        plotsByEntity.clear();
        for (Plot plot : plots) {
            removeEntities(plot);
        }
    }

    public boolean isCampEntity(Entity entity) {
        return entity != null && plotsByEntity.containsKey(entity.getUniqueId());
    }

    public void addPlot(int worldX, int y, int worldZ, HallsCampLayout.BuildSpot spot) {
        Location location = new Location(world, worldX + 0.5, y, worldZ + 0.5);
        Interaction interaction = world.spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(Math.max(1.0f, spot.maxX() - spot.minX() + 1.0f));
            entity.setInteractionHeight(1.0f);
            entity.setResponsive(true);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_camp_plot");
        });
        Plot plot = new Plot(spot.id(), spot.size(), worldX, y, worldZ, spot.facing(), interaction.getUniqueId());
        plotsById.put(plot.id(), plot);
        plotsByEntity.put(interaction.getUniqueId(), plot);
    }

    public boolean handleInteract(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        Plot plot = plotsByEntity.get(entity.getUniqueId());
        if (plot == null) {
            return false;
        }
        if (plot.buildingId() == null) {
            return buildFromBlueprint(player, plot);
        }
        if (player.isSneaking()) {
            return upgrade(player, plot);
        }
        return useBuilding(player, plot);
    }

    private boolean buildFromBlueprint(Player player, Plot plot) {
        HallsItemType blueprint = heldItemType(player);
        if (blueprint == null || !blueprint.category().equals("blueprint")) {
            player.sendActionBar(Component.text("Hold a building blueprint for this plot.", NamedTextColor.YELLOW));
            return true;
        }
        HallsBuildingType building = buildingTypes.values().stream()
                .filter(type -> type.blueprint().equals(blueprint.id()))
                .findFirst()
                .orElse(null);
        if (building == null) {
            player.sendActionBar(Component.text("That blueprint is not recognized by this camp.", NamedTextColor.RED));
            return true;
        }
        if (!building.fits(plot.size())) {
            player.sendActionBar(Component.text("This plot is too small for " + building.name() + ".", NamedTextColor.RED));
            return true;
        }
        consumeHeld(player);
        setBuilding(plot, building, 1);
        player.sendMessage(Component.text("Built " + building.name() + ".", NamedTextColor.GREEN));
        world.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.25f);
        return true;
    }

    private boolean upgrade(Player player, Plot plot) {
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        if (building == null) {
            return true;
        }
        if (plot.level() >= 3) {
            player.sendActionBar(Component.text(building.name() + " is already level 3.", NamedTextColor.GRAY));
            return true;
        }
        HallsBuildingType.Level next = building.level(plot.level() + 1);
        if (scrapAccount != null && !scrapAccount.spend(next.upgradeCost())) {
            player.sendActionBar(Component.text("Not enough stored scrap to upgrade.", NamedTextColor.RED));
            return true;
        }
        setBuilding(plot, building, plot.level() + 1);
        player.sendMessage(Component.text("Upgraded " + building.name() + " to level " + plot.level() + ".", NamedTextColor.GREEN));
        world.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.1f);
        return true;
    }

    private boolean useBuilding(Player player, Plot plot) {
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        if (building == null) {
            return true;
        }
        if (!building.implemented()) {
            player.sendActionBar(Component.text(building.name() + " is decorative for now.", NamedTextColor.GRAY));
            return true;
        }
        List<String> giveItems = building.level(plot.level()).giveItems();
        if (giveItems.isEmpty()) {
            player.sendActionBar(Component.text(building.name() + " has no active output configured.", NamedTextColor.GRAY));
            return true;
        }
        int given = 0;
        for (String itemId : giveItems) {
            HallsItemType itemType = itemTypes.get(itemId);
            if (itemType == null) {
                continue;
            }
            int slot = firstAvailableHotbarSlot(player.getInventory());
            if (slot < 0) {
                break;
            }
            player.getInventory().setItem(slot, itemFactory.apply(itemType));
            given++;
        }
        if (given == 0) {
            player.sendActionBar(Component.text("Your hotbar is full.", NamedTextColor.RED));
            return true;
        }
        world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.35f);
        player.sendActionBar(Component.text(building.name() + " produced " + given + " item(s).", NamedTextColor.GREEN));
        return true;
    }

    private void setBuilding(Plot plot, HallsBuildingType building, int level) {
        removeDisplays(plot);
        plot.setBuilding(building.id(), level);
        for (HallsBuildingType.Part part : building.level(level).parts()) {
            double[] offset = rotatedOffset(part.offsetX(), part.offsetZ(), plot.facing());
            Location location = new Location(world, plot.x() + 0.5 + offset[0], plot.y() + part.offsetY(), plot.z() + 0.5 + offset[1]);
            BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
                entity.setBlock(blockData(part.material(), part.blockData()));
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setTransformation(new Transformation(
                        new Vector3f(),
                        partRotation(part, plot.facing()),
                        new Vector3f((float) part.scaleX(), (float) part.scaleY(), (float) part.scaleZ()),
                        new Quaternionf()));
                entity.setPersistent(false);
                entity.addScoreboardTag("omgames_hoc_camp_building");
            });
            plot.displayIds().add(display.getUniqueId());
            plotsByEntity.put(display.getUniqueId(), plot);
        }
    }

    private BlockData blockData(org.bukkit.Material material, String configured) {
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

    private Quaternionf partRotation(HallsBuildingType.Part part, BlockFace facing) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(yawDegrees(facing)))
                .rotateXYZ((float) Math.toRadians(part.rotationX()),
                        (float) Math.toRadians(part.rotationY()),
                        (float) Math.toRadians(part.rotationZ()));
    }

    private double[] rotatedOffset(double x, double z, BlockFace facing) {
        return switch (facing) {
            case EAST -> new double[]{-z, x};
            case SOUTH -> new double[]{-x, -z};
            case WEST -> new double[]{z, -x};
            default -> new double[]{x, z};
        };
    }

    private double yawDegrees(BlockFace facing) {
        return switch (facing) {
            case EAST -> 90.0;
            case SOUTH -> 180.0;
            case WEST -> 270.0;
            default -> 0.0;
        };
    }

    private HallsItemType heldItemType(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String itemId = item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "hoc_item_id"), PersistentDataType.STRING);
        return itemId == null ? null : itemTypes.get(itemId);
    }

    private void consumeHeld(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        held.setAmount(held.getAmount() - 1);
        player.getInventory().setItemInMainHand(held);
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

    private void removeEntities(Plot plot) {
        Entity interaction = Bukkit.getEntity(plot.interactionId());
        if (interaction != null) {
            interaction.remove();
        }
        removeDisplays(plot);
    }

    private void removeDisplays(Plot plot) {
        for (UUID displayId : List.copyOf(plot.displayIds())) {
            plotsByEntity.remove(displayId);
            Entity display = Bukkit.getEntity(displayId);
            if (display != null) {
                display.remove();
            }
        }
        plot.displayIds().clear();
    }

    private static final class Plot {
        private final int id;
        private final String size;
        private final int x;
        private final int y;
        private final int z;
        private final BlockFace facing;
        private final UUID interactionId;
        private final List<UUID> displayIds = new ArrayList<>();
        private String buildingId;
        private int level;

        private Plot(int id, String size, int x, int y, int z, BlockFace facing, UUID interactionId) {
            this.id = id;
            this.size = size;
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing == null ? BlockFace.NORTH : facing;
            this.interactionId = interactionId;
        }

        private int id() {
            return id;
        }

        private String size() {
            return size;
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

        private BlockFace facing() {
            return facing;
        }

        private UUID interactionId() {
            return interactionId;
        }

        private List<UUID> displayIds() {
            return displayIds;
        }

        private String buildingId() {
            return buildingId;
        }

        private int level() {
            return level;
        }

        private void setBuilding(String buildingId, int level) {
            this.buildingId = buildingId;
            this.level = level;
        }
    }
}
