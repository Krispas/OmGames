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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class HallsCampRuntime {
    public interface ScrapAccount {
        boolean canSpend(Map<String, Integer> cost);

        boolean spend(Map<String, Integer> cost);
    }

    public interface SculkAccount {
        boolean reduce(UUID playerId, double amount);
    }

    public interface TotemAccount {
        boolean applyHealthTotem(Player player, int plotId, int level);

        boolean applySpeedTotem(Player player, int plotId, int level);
    }

    public interface KeyAccount {
        int keys();

        boolean spendKey();
    }

    public interface ResearchAccount {
        int points();

        boolean isUnlocked(String nodeId);

        boolean isItemResearched(String itemId);

        boolean canUnlock(String nodeId);

        boolean unlock(String nodeId);
    }

    private final JavaPlugin plugin;
    private final World world;
    private final HallsScenario scenario;
    private final Map<String, HallsBuildingType> buildingTypes;
    private final Map<String, HallsItemType> itemTypes;
    private final Function<HallsItemType, ItemStack> itemFactory;
    private final ScrapAccount scrapAccount;
    private final SculkAccount sculkAccount;
    private final TotemAccount totemAccount;
    private final Function<Integer, List<String>> scanner;
    private final ResearchAccount researchAccount;
    private final Map<UUID, Plot> plotsByEntity = new HashMap<>();
    private final Map<Integer, Plot> plotsById = new HashMap<>();
    private final Map<UUID, Door> doorsByEntity = new HashMap<>();
    private final Map<Integer, Door> doorsById = new HashMap<>();
    private final Set<Integer> unlockedDoorIds = new HashSet<>();
    private final KeyAccount keyAccount;
    private HallsCampLayout layout;
    private int layoutStartX;
    private int layoutStartZ;

    public HallsCampRuntime(JavaPlugin plugin,
                            World world,
                            HallsScenario scenario,
                            Map<String, HallsBuildingType> buildingTypes,
                            Map<String, HallsItemType> itemTypes,
                            Function<HallsItemType, ItemStack> itemFactory,
                            ScrapAccount scrapAccount,
                            SculkAccount sculkAccount,
                            TotemAccount totemAccount,
                            Function<Integer, List<String>> scanner,
                            ResearchAccount researchAccount,
                            KeyAccount keyAccount) {
        this.plugin = plugin;
        this.world = world;
        this.scenario = scenario;
        this.buildingTypes = buildingTypes == null ? Map.of() : Map.copyOf(buildingTypes);
        this.itemTypes = itemTypes == null ? Map.of() : Map.copyOf(itemTypes);
        this.itemFactory = itemFactory;
        this.scrapAccount = scrapAccount;
        this.sculkAccount = sculkAccount;
        this.totemAccount = totemAccount;
        this.scanner = scanner;
        this.researchAccount = researchAccount;
        this.keyAccount = keyAccount;
    }

    public void clear() {
        Set<Plot> plots = new HashSet<>(plotsById.values());
        Set<Door> doors = new HashSet<>(doorsById.values());
        plotsById.clear();
        plotsByEntity.clear();
        doorsById.clear();
        doorsByEntity.clear();
        unlockedDoorIds.clear();
        layout = null;
        for (Plot plot : plots) {
            removeEntities(plot);
        }
        for (Door door : doors) {
            Entity interaction = Bukkit.getEntity(door.interactionId());
            if (interaction != null) {
                interaction.remove();
            }
        }
    }

    public boolean isCampEntity(Entity entity) {
        return entity != null && (plotsByEntity.containsKey(entity.getUniqueId())
                || doorsByEntity.containsKey(entity.getUniqueId()));
    }

    public void startLayout(HallsCampLayout layout, int startX, int y, int startZ, Set<Integer> unlockedDoors) {
        this.layout = layout;
        this.layoutStartX = startX;
        this.layoutStartZ = startZ;
        this.unlockedDoorIds.clear();
        if (unlockedDoors != null) {
            this.unlockedDoorIds.addAll(unlockedDoors);
        }
    }

    public void addPlot(double worldX, int y, double worldZ, HallsCampLayout.BuildSpot spot) {
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
        if (isStationPlot(plot)) {
            HallsBuildingType station = buildingTypes.get("camp_station");
            if (station != null) {
                setBuilding(plot, station, 1);
            }
        }
    }

    public void addDoor(int worldX, int y, int worldZ, HallsCampLayout.DoorCell cell) {
        Location location = new Location(world, worldX + 0.5, y, worldZ + 0.5);
        Interaction interaction = world.spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(1.0f);
            entity.setInteractionHeight(3.4f);
            entity.setResponsive(true);
            entity.setPersistent(false);
            entity.addScoreboardTag("omgames_hoc_camp_door");
        });
        Door door = new Door(cell.id(), cell.x(), cell.z(), worldX, y, worldZ, interaction.getUniqueId());
        doorsById.put(door.id(), door);
        doorsByEntity.put(interaction.getUniqueId(), door);
        if (unlockedDoorIds.contains(door.id()) || bothSidesReachable(door)) {
            unlockDoor(door, false);
        }
    }

    public List<PlotState> snapshot() {
        List<PlotState> states = new ArrayList<>();
        for (Plot plot : plotsById.values()) {
            if (plot.buildingId() == null) {
                continue;
            }
            states.add(new PlotState(plot.id(), plot.buildingId(), plot.level(),
                    plot.harvestRemaining(), plot.harvestUsed(), cloneStorage(plot.storageContents())));
        }
        return states;
    }

    public Set<Integer> unlockedDoors() {
        return Set.copyOf(unlockedDoorIds);
    }

    public void closeOpenViewers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(world)) {
                continue;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof StorageMenu menu) {
                saveStorageMenu(menu.plotId(), top);
                player.closeInventory();
            } else if (top.getHolder() instanceof CampMenu) {
                player.closeInventory();
            }
        }
    }

    public void restore(List<PlotState> states) {
        if (states == null || states.isEmpty()) {
            return;
        }
        for (PlotState state : states) {
            Plot plot = plotsById.get(state.plotId());
            if (isStationPlot(plot)) {
                continue;
            }
            HallsBuildingType building = buildingTypes.get(state.buildingId());
            if (plot == null || building == null || !building.fits(plot.size())) {
                continue;
            }
            int level = Math.max(1, Math.min(3, state.level()));
            setBuilding(plot, building, level);
            plot.setHarvestRemaining(state.harvestRemaining());
            plot.setHarvestUsed(state.harvestUsed());
            if (state.harvestRemaining() <= 0 && state.harvestUsed() <= 0 && defaultRunUses(building, level) > 0) {
                plot.setHarvestRemaining(defaultRunUses(building, level));
            }
            plot.setStorageContents(state.storageContents());
            HallsBuildingType.Level buildingLevel = building.level(level);
            if (plot.harvestRemaining() <= 0 && !buildingLevel.emptyParts().isEmpty()) {
                setDisplays(plot, building, buildingLevel.emptyParts());
            }
        }
    }

    public void refreshRunUses() {
        for (Plot plot : plotsById.values()) {
            if (plot.buildingId() == null) {
                continue;
            }
            HallsBuildingType building = buildingTypes.get(plot.buildingId());
            if (building == null) {
                continue;
            }
            int level = Math.max(1, Math.min(3, plot.level()));
            int uses = defaultRunUses(building, level);
            if (uses <= 0) {
                continue;
            }
            plot.setHarvestRemaining(uses);
            plot.setHarvestUsed(0);
            setDisplays(plot, building, building.level(level).parts());
        }
    }

    public boolean handleInteract(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        Plot plot = plotsByEntity.get(entity.getUniqueId());
        if (plot == null) {
            Door door = doorsByEntity.get(entity.getUniqueId());
            return door != null && handleDoorInteract(player, door, player.isSneaking());
        }
        if (plot.buildingId() == null && isStationPlot(plot)) {
            player.sendActionBar(Component.text("The camp station is not loaded.", NamedTextColor.RED));
            return true;
        }
        if (plot.buildingId() == null) {
            return buildFromBlueprint(player, plot);
        }
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        if (building != null && building.id().equals("mycelia_farm") && plot.harvestRemaining() > 0) {
            return harvestMycelia(player, plot, building);
        }
        playBuildingSound(player, building, BuildingSound.OPEN);
        openBuildingMenu(player, plot);
        return true;
    }

    private boolean handleDoorInteract(Player player, Door door, boolean unlockIntent) {
        if (unlockedDoorIds.contains(door.id())) {
            player.sendActionBar(Component.text("This camp door is already open.", NamedTextColor.GRAY));
            return true;
        }
        if (bothSidesReachable(door)) {
            unlockDoor(door, true);
            player.sendActionBar(Component.text("Opened redundant camp door.", NamedTextColor.GREEN));
            return true;
        }
        if (!isDoorReachable(door)) {
            player.sendActionBar(Component.text("Reach this door from an unlocked room first.", NamedTextColor.RED));
            return true;
        }
        if (!unlockIntent) {
            player.sendMessage(Component.text("Locked camp door. Shift-right-click to spend 1 key.", NamedTextColor.GOLD));
            for (String line : doorPlotSummary(door)) {
                player.sendMessage(Component.text(line, NamedTextColor.GRAY));
            }
            return true;
        }
        if (keyAccount == null || keyAccount.keys() <= 0) {
            player.sendActionBar(Component.text("The camp has no keys.", NamedTextColor.RED));
            return true;
        }
        if (!keyAccount.spendKey()) {
            player.sendActionBar(Component.text("Could not spend a camp key.", NamedTextColor.RED));
            return true;
        }
        unlockDoor(door, true);
        player.sendMessage(Component.text("Unlocked a camp door. Keys left: " + keyAccount.keys() + ".", NamedTextColor.GREEN));
        return true;
    }

    private void unlockDoor(Door door, boolean effects) {
        unlockedDoorIds.add(door.id());
        for (int dy = 0; dy < 3; dy++) {
            world.getBlockAt(door.worldX(), door.y() + dy, door.worldZ()).setType(Material.AIR, false);
        }
        Entity interaction = Bukkit.getEntity(door.interactionId());
        if (interaction != null) {
            interaction.remove();
        }
        doorsByEntity.remove(door.interactionId());
        if (effects) {
            Location location = new Location(world, door.worldX() + 0.5, door.y() + 1.0, door.worldZ() + 0.5);
            world.playSound(location, Sound.BLOCK_IRON_DOOR_OPEN, 0.9f, 0.8f);
            world.spawnParticle(org.bukkit.Particle.WAX_OFF, location, 24, 0.35, 0.75, 0.35, 0.02);
        }
    }

    private boolean isDoorReachable(Door door) {
        Set<Cell> reachable = reachableCampCells(Set.of());
        return adjacentOpenCells(door).stream().anyMatch(reachable::contains);
    }

    private boolean bothSidesReachable(Door door) {
        List<Cell> sides = adjacentOpenCells(door);
        if (sides.size() < 2) {
            return false;
        }
        Set<Cell> reachable = reachableCampCells(Set.of());
        int reachableSides = 0;
        for (Cell side : sides) {
            if (reachable.contains(side)) {
                reachableSides++;
            }
        }
        return reachableSides >= 2;
    }

    private List<String> doorPlotSummary(Door door) {
        Set<Cell> reachable = reachableCampCells(Set.of(door.id()));
        Set<Integer> hiddenPlotIds = new HashSet<>();
        for (Plot plot : plotsById.values()) {
            Cell cell = new Cell((int) Math.floor(plot.x() - layoutStartX), (int) Math.floor(plot.z() - layoutStartZ));
            if (reachable.contains(cell) && !reachableCampCells(Set.of()).contains(cell)) {
                hiddenPlotIds.add(plot.id());
            }
        }
        if (hiddenPlotIds.isEmpty()) {
            return List.of("No new build plots are visible beyond it.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Build plots beyond:");
        hiddenPlotIds.stream().sorted().forEach(id -> {
            Plot plot = plotsById.get(id);
            if (plot != null) {
                lines.add("- Plot " + id + " (" + plot.size() + ")");
            }
        });
        return lines;
    }

    private Set<Cell> reachableCampCells(Set<Integer> temporarilyOpenDoors) {
        Set<Cell> reachable = new HashSet<>();
        if (layout == null || layout.elevatorLink() == null) {
            return reachable;
        }
        ArrayList<Cell> queue = new ArrayList<>();
        Cell start = new Cell(layout.elevatorLink().x(), layout.elevatorLink().z());
        if (!isPassable(start.x(), start.z(), temporarilyOpenDoors)) {
            return reachable;
        }
        queue.add(start);
        reachable.add(start);
        for (int index = 0; index < queue.size(); index++) {
            Cell cell = queue.get(index);
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                Cell next = new Cell(cell.x() + face.getModX(), cell.z() + face.getModZ());
                if (reachable.contains(next) || !isPassable(next.x(), next.z(), temporarilyOpenDoors)) {
                    continue;
                }
                reachable.add(next);
                queue.add(next);
            }
        }
        return reachable;
    }

    private boolean isPassable(int x, int z, Set<Integer> temporarilyOpenDoors) {
        if (layout == null || x < 0 || z < 0 || x >= layout.width() || z >= layout.depth()) {
            return false;
        }
        char cell = layout.at(x, z);
        if (cell == 'X') {
            return false;
        }
        if (cell != 'D') {
            return true;
        }
        Door door = doorAt(x, z);
        return door != null && (unlockedDoorIds.contains(door.id()) || temporarilyOpenDoors.contains(door.id()));
    }

    private Door doorAt(int x, int z) {
        for (Door door : doorsById.values()) {
            if (door.x() == x && door.z() == z) {
                return door;
            }
        }
        return null;
    }

    private List<Cell> adjacentOpenCells(Door door) {
        List<Cell> cells = new ArrayList<>();
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            int x = door.x() + face.getModX();
            int z = door.z() + face.getModZ();
            if (layout != null && x >= 0 && z >= 0 && x < layout.width() && z < layout.depth() && layout.at(x, z) != 'X') {
                cells.add(new Cell(x, z));
            }
        }
        return cells;
    }

    public boolean handleInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof StorageMenu menu) {
            ItemStack clicked = event.getCurrentItem();
            ItemStack cursor = event.getCursor();
            if (event.isShiftClick()
                    || event.getClickedInventory() == event.getView().getTopInventory()
                    && event.getSlot() >= storageSlots(menu.plotId())
                    && (cursor != null && !cursor.getType().isAir()
                    || clicked == null || clicked.getType().isAir() || isLockedStorageFiller(clicked))) {
                event.setCancelled(true);
            }
            return true;
        }
        if (!(event.getInventory().getHolder() instanceof CampMenu menu)) {
            return false;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) {
            return true;
        }
        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "hoc_camp_action"), PersistentDataType.STRING);
        if (action == null) {
            return true;
        }
        Plot plot = plotsById.get(menu.plotId());
        if (plot == null || plot.buildingId() == null) {
            player.closeInventory();
            return true;
        }
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        if (building == null) {
            player.closeInventory();
            return true;
        }
        switch (action) {
            case "upgrade" -> {
                upgrade(player, plot);
                openBuildingMenu(player, plot);
            }
            case "destroy" -> {
                if (!isPermanentBuilding(building)) {
                    destroyBuilding(player, plot, building);
                }
                player.closeInventory();
            }
            case "craft" -> {
                String itemId = clicked.getItemMeta().getPersistentDataContainer()
                        .get(new NamespacedKey(plugin, "hoc_camp_item"), PersistentDataType.STRING);
                craftRecipe(player, plot, building, itemId);
                openBuildingMenu(player, plot);
            }
            case "station_home" -> openBuildingMenu(player, plot);
            case "craft_category" -> openCraftingCategory(player, plot, clicked, 0);
            case "craft_page" -> openCraftingCategory(player, plot, clicked, campPage(clicked));
            case "research" -> openResearchMenu(player, plot);
            case "research_node" -> {
                String nodeId = clicked.getItemMeta().getPersistentDataContainer()
                        .get(new NamespacedKey(plugin, "hoc_camp_item"), PersistentDataType.STRING);
                unlockResearch(player, nodeId);
                openResearchMenu(player, plot);
            }
            case "storage" -> openStorage(player, plot, building);
            case "purify" -> {
                activateSculkPurifier(player, plot, building);
                openBuildingMenu(player, plot);
            }
            case "grindstone" -> {
                activateGrindstone(player, plot, building);
                openBuildingMenu(player, plot);
            }
            case "forge" -> {
                activateForge(player, plot, building);
                openBuildingMenu(player, plot);
            }
            case "scanner" -> {
                activateScanner(player, plot);
                openBuildingMenu(player, plot);
            }
            case "health_totem" -> {
                activateHealthTotem(player, plot);
                openBuildingMenu(player, plot);
            }
            case "speed_totem" -> {
                activateSpeedTotem(player, plot);
                openBuildingMenu(player, plot);
            }
            default -> {
            }
        }
        return true;
    }

    public boolean handleInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof StorageMenu menu)) {
            return false;
        }
        saveStorageMenu(menu.plotId(), event.getInventory());
        return true;
    }

    private void saveStorageMenu(int plotId, Inventory inventory) {
        Plot plot = plotsById.get(plotId);
        if (plot == null || plot.buildingId() == null || !isStorageLocker(plot.buildingId())) {
            return;
        }
        int slots = storageOpenSlots(plot);
        ItemStack[] contents = new ItemStack[slots];
        for (int slot = 0; slot < slots; slot++) {
            ItemStack item = inventory.getItem(slot);
            contents[slot] = isLockedStorageFiller(item) ? null : cloneOrNull(item);
        }
        plot.setStorageContents(contents);
    }

    public int highestBuiltLevel(String buildingId) {
        int level = 0;
        for (Plot plot : plotsById.values()) {
            if (buildingId.equals(plot.buildingId())) {
                level = Math.max(level, plot.level());
            }
        }
        return level;
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
        initializeHarvest(plot, building);
        player.sendMessage(Component.text("Built " + building.name() + ".", NamedTextColor.GREEN));
        playBuildingSound(player, building, BuildingSound.BUILD);
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
        String blueprintCost = upgradeBlueprintCost(building);
        if (blueprintCost != null && countHotbarItem(player.getInventory(), blueprintCost) <= 0) {
            player.sendActionBar(Component.text("Missing " + itemName(blueprintCost) + " for this upgrade.",
                    NamedTextColor.RED));
            return true;
        }
        if (scrapAccount != null && !scrapAccount.spend(next.upgradeCost())) {
            player.sendActionBar(Component.text("Not enough stored scrap to upgrade.", NamedTextColor.RED));
            return true;
        }
        if (blueprintCost != null) {
            consumeItemIngredients(player.getInventory(), Map.of(blueprintCost, 1));
        }
        setBuilding(plot, building, plot.level() + 1);
        refreshHarvestForLevel(plot, building);
        player.sendMessage(Component.text("Upgraded " + building.name() + " to level " + plot.level() + ".", NamedTextColor.GREEN));
        playBuildingSound(player, building, BuildingSound.UPGRADE);
        return true;
    }

    private void openBuildingMenu(Player player, Plot plot) {
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        if (building == null) {
            return;
        }
        boolean permanent = isPermanentBuilding(building);
        Inventory inventory = Bukkit.createInventory(new CampMenu(plot.id(), "home", "", 0), permanent ? 54 : 27,
                Component.text(permanent ? building.name() : building.name() + " L" + plot.level(), NamedTextColor.DARK_GREEN));
        inventory.setItem(4, menuItem(Material.OAK_SIGN, building.name(), NamedTextColor.GREEN,
                permanent
                        ? List.of("Permanent camp station.", "Plot size: " + plot.size())
                        : List.of("Level " + plot.level(), "Plot size: " + plot.size()),
                null, null));
        List<String> recipes = scenario == null ? List.of() : scenario.craftingRecipes(building.id(), plot.level());
        int recipeIndex = 0;
        if (building.id().equals("camp_station")) {
            inventory.setItem(19, menuItem(Material.IRON_SWORD, "Weapons", NamedTextColor.YELLOW,
                    List.of("Craft researched weapons."), "craft_category", "weapon"));
            inventory.setItem(21, menuItem(Material.IRON_CHESTPLATE, "Armor", NamedTextColor.YELLOW,
                    List.of("Craft researched armor."), "craft_category", "armor"));
            inventory.setItem(23, menuItem(Material.BREWING_STAND, "Utilities", NamedTextColor.YELLOW,
                    List.of("Craft researched utility items."), "craft_category", "utility"));
            inventory.setItem(25, menuItem(Material.COOKED_BEEF, "Food", NamedTextColor.YELLOW,
                    List.of("Craft researched food."), "craft_category", "food"));
            inventory.setItem(31, menuItem(Material.ENCHANTING_TABLE, "Research", NamedTextColor.AQUA,
                    List.of("Points: " + researchPointsLabel(), "Unlocks future camp-station recipes."), "research", null));
        } else if (isCraftingStation(building) && !recipes.isEmpty()) {
            for (String itemId : recipes) {
                if (recipeIndex >= RECIPE_SLOTS.length) {
                    break;
                }
                HallsItemType itemType = itemTypes.get(itemId);
                if (itemType == null) {
                    continue;
                }
                inventory.setItem(RECIPE_SLOTS[recipeIndex++], recipeMenuItem(itemType));
            }
        } else if (isStorageLocker(building)) {
            int slots = storageSlots(building, plot.level());
            inventory.setItem(13, menuItem(Material.CHEST, "Open Storage", NamedTextColor.AQUA,
                    List.of("Slots: " + slots, "Stored items persist with this camp."), "storage", null));
        } else if (isSculkPurifier(building)) {
            inventory.setItem(13, menuItem(Material.CALIBRATED_SCULK_SENSOR, "Purify Sculk", NamedTextColor.AQUA,
                    List.of("Charges this run: " + plot.harvestRemaining(),
                            "Reduces your sculk pressure by " + formatStatAmount(purifyAmount(building, plot.level())) + "%."),
                    "purify", null));
        } else if (building.id().equals("grindstone")) {
            inventory.setItem(13, menuItem(Material.GRINDSTONE, "Sharpen Held Weapon", NamedTextColor.AQUA,
                    List.of("Charges this run: " + plot.harvestRemaining(),
                            "Adds +" + formatStatAmount(grindstoneDamageBonus(plot.level())) + " melee damage."),
                    "grindstone", null));
        } else if (building.id().equals("forge")) {
            inventory.setItem(13, menuItem(Material.ANVIL, "Repair Held Item", NamedTextColor.AQUA,
                    List.of("Charges this run: " + plot.harvestRemaining(),
                            "Repairs " + formatStatAmount(forgeRepairPercent(plot.level())) + "% durability."),
                    "forge", null));
        } else if (building.id().equals("scanner")) {
            inventory.setItem(13, menuItem(Material.LODESTONE, "Scan Deeper Floors", NamedTextColor.AQUA,
                    List.of("Reveals and locks modifiers for the next " + Math.max(1, Math.min(3, plot.level()))
                            + " exploration floor" + (plot.level() == 1 ? "." : "s.")),
                    "scanner", null));
        } else if (building.id().equals("health_totem")) {
            inventory.setItem(13, menuItem(Material.TOTEM_OF_UNDYING, "Receive Vitality", NamedTextColor.AQUA,
                    List.of("Charges this run: " + plot.harvestRemaining(),
                            "Adds +" + healthTotemBonus(plot.level()) + " max health to you for this run."),
                    "health_totem", null));
        } else if (building.id().equals("speed_totem")) {
            inventory.setItem(13, menuItem(Material.FEATHER, "Receive Swiftness", NamedTextColor.AQUA,
                    List.of("Charges this run: " + plot.harvestRemaining(),
                            "Adds +" + speedTotemPercent(plot.level()) + "% movement speed to you for this run."),
                    "speed_totem", null));
        } else if (building.id().equals("elevator_drill")) {
            inventory.setItem(13, menuItem(Material.POINTED_DRIPSTONE, "Drill Ready", NamedTextColor.AQUA,
                    List.of("Exploration coin quota multiplier: "
                            + formatStatAmount(elevatorDrillQuotaMultiplier(plot.level()) * 100.0) + "%.",
                            "Multiple drills stack multiplicatively."), null, null));
        } else if (building.id().equals("mycelia_farm")) {
            inventory.setItem(13, menuItem(Material.DEAD_BUSH, "Farm Empty", NamedTextColor.GRAY,
                    List.of("Upgrade or revisit after a future refresh."), null, null));
        } else if (!building.implemented()) {
            inventory.setItem(13, menuItem(Material.BARRIER, "Placeholder", NamedTextColor.GRAY,
                    List.of("This building has no active behavior yet."), null, null));
        } else {
            inventory.setItem(13, menuItem(Material.PAPER, "No Recipes", NamedTextColor.GRAY,
                    List.of("No scenario recipes are unlocked here."), null, null));
        }
        int upgradeSlot = permanent ? 49 : 22;
        int destroySlot = permanent ? 53 : 26;
        if (permanent) {
            inventory.setItem(upgradeSlot, menuItem(Material.LODESTONE, "Permanent Station", NamedTextColor.GRAY,
                    List.of("Always present in this camp."), null, null));
        } else if (plot.level() < 3) {
            HallsBuildingType.Level next = building.level(plot.level() + 1);
            inventory.setItem(upgradeSlot, menuItem(Material.SMITHING_TABLE, "Upgrade", NamedTextColor.YELLOW,
                    upgradeLore(building, plot), "upgrade", null));
        } else {
            inventory.setItem(upgradeSlot, menuItem(Material.SMITHING_TABLE, "Max Level", NamedTextColor.GRAY,
                    List.of("This building is already level 3."), null, null));
        }
        if (!permanent) {
            inventory.setItem(destroySlot, menuItem(Material.TNT, "Destroy", NamedTextColor.RED,
                    List.of("Removes the building.", "The blueprint is not returned."), "destroy", null));
        }
        player.openInventory(inventory);
    }

    private void openCraftingCategory(Player player, Plot plot, ItemStack clicked, int page) {
        String category = clicked.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "hoc_camp_item"), PersistentDataType.STRING);
        if (category == null || category.isBlank()) {
            openBuildingMenu(player, plot);
            return;
        }
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        if (building == null) {
            openBuildingMenu(player, plot);
            return;
        }
        List<String> recipes = scenario == null ? List.of() : scenario.craftingRecipes(building.id(), plot.level());
        List<HallsItemType> categoryRecipes = recipes.stream()
                .map(itemTypes::get)
                .filter(type -> type != null && type.category().equals(category))
                .toList();
        int maxPage = Math.max(0, (categoryRecipes.size() - 1) / RECIPE_SLOTS.length);
        int normalizedPage = Math.max(0, Math.min(maxPage, page));
        Inventory inventory = Bukkit.createInventory(new CampMenu(plot.id(), "craft", category, normalizedPage), 54,
                Component.text("Camp Station: " + categoryName(category), NamedTextColor.DARK_GREEN));
        inventory.setItem(4, menuItem(Material.OAK_SIGN, categoryName(category), NamedTextColor.GREEN,
                List.of("Page " + (normalizedPage + 1) + "/" + (maxPage + 1)), null, null));
        int start = normalizedPage * RECIPE_SLOTS.length;
        for (int i = 0; i < RECIPE_SLOTS.length && start + i < categoryRecipes.size(); i++) {
            inventory.setItem(RECIPE_SLOTS[i], recipeMenuItem(categoryRecipes.get(start + i)));
        }
        inventory.setItem(45, menuItem(Material.ARROW, "Back", NamedTextColor.GRAY, List.of("Return to station."), "station_home", null));
        if (normalizedPage > 0) {
            inventory.setItem(48, pageItem(Material.SPECTRAL_ARROW, "Previous Page", category, normalizedPage - 1));
        }
        if (normalizedPage < maxPage) {
            inventory.setItem(50, pageItem(Material.SPECTRAL_ARROW, "Next Page", category, normalizedPage + 1));
        }
        inventory.setItem(53, menuItem(Material.ENCHANTING_TABLE, "Research", NamedTextColor.AQUA,
                List.of("Points: " + researchPointsLabel()), "research", null));
        player.openInventory(inventory);
    }

    private ItemStack recipeMenuItem(HallsItemType type) {
        ItemStack preview = itemFactory.apply(type);
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            if (researchAccount != null && !researchAccount.isItemResearched(type.id())) {
                lore.add(Component.text("Research required.", NamedTextColor.RED));
            }
            lore.add(Component.text("Cost: " + formatCost(type.recipe()), NamedTextColor.GOLD));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_camp_action"),
                    PersistentDataType.STRING, "craft");
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_camp_item"),
                    PersistentDataType.STRING, type.id());
            preview.setItemMeta(meta);
        }
        return preview;
    }

    private void openResearchMenu(Player player, Plot plot) {
        Inventory inventory = Bukkit.createInventory(new CampMenu(plot.id(), "research", "", 0), 54,
                Component.text("Camp Station: Research", NamedTextColor.DARK_GREEN));
        inventory.setItem(4, menuItem(Material.EXPERIENCE_BOTTLE, "Research Points", NamedTextColor.AQUA,
                List.of("Available: " + researchPointsLabel()), null, null));
        List<HallsResearchNode> nodes = scenario == null ? List.of() : scenario.researchNodes().values().stream()
                .sorted(java.util.Comparator.comparingInt((HallsResearchNode node) -> node.prerequisites().size())
                        .thenComparing(HallsResearchNode::id))
                .toList();
        int[][] slotsByDepth = {
                {10, 19, 28, 37},
                {12, 21, 30, 39},
                {14, 23, 32, 41},
                {16, 25, 34, 43}
        };
        int[] usedByDepth = new int[slotsByDepth.length];
        int overflowIndex = 0;
        int[] overflowSlots = {46, 47, 48, 50, 51, 52};
        for (HallsResearchNode node : nodes) {
            int depth = Math.min(slotsByDepth.length - 1, researchDepth(node, new HashSet<>()));
            if (usedByDepth[depth] < slotsByDepth[depth].length) {
                inventory.setItem(slotsByDepth[depth][usedByDepth[depth]++], researchNodeItem(node));
            } else if (overflowIndex < overflowSlots.length) {
                inventory.setItem(overflowSlots[overflowIndex++], researchNodeItem(node));
            }
        }
        inventory.setItem(45, menuItem(Material.ARROW, "Back", NamedTextColor.GRAY, List.of("Return to station."), "station_home", null));
        player.openInventory(inventory);
    }

    private int researchDepth(HallsResearchNode node, Set<String> visiting) {
        if (node == null || node.prerequisites().isEmpty() || !visiting.add(node.id())) {
            return 0;
        }
        int depth = 0;
        for (String prerequisite : node.prerequisites()) {
            depth = Math.max(depth, 1 + researchDepth(scenario.researchNode(prerequisite), visiting));
        }
        visiting.remove(node.id());
        return depth;
    }

    private ItemStack researchNodeItem(HallsResearchNode node) {
        boolean unlocked = researchAccount != null && researchAccount.isUnlocked(node.id());
        boolean canUnlock = researchAccount != null && researchAccount.canUnlock(node.id());
        List<String> lore = new ArrayList<>();
        lore.add("Cost: " + node.cost() + " research");
        if (!node.prerequisites().isEmpty()) {
            lore.add("Requires: " + node.prerequisites().stream().map(this::researchName).collect(java.util.stream.Collectors.joining(", ")));
        }
        if (!node.unlocks().isEmpty()) {
            lore.add("Unlocks:");
            lore.addAll(node.unlocks().stream().map(item -> "- " + itemName(item)).toList());
        }
        lore.add(unlocked ? "Researched." : canUnlock ? "Click to research." : "Locked.");
        return menuItem(unlocked ? Material.LIME_STAINED_GLASS_PANE : canUnlock ? node.icon() : Material.GRAY_DYE,
                node.name(),
                unlocked ? NamedTextColor.GREEN : canUnlock ? NamedTextColor.YELLOW : NamedTextColor.GRAY,
                lore,
                unlocked ? null : "research_node",
                node.id());
    }

    private String researchName(String nodeId) {
        HallsResearchNode node = scenario == null ? null : scenario.researchNode(nodeId);
        return node == null ? nodeId.replace('_', ' ') : node.name();
    }

    private void craftRecipe(Player player, Plot plot, HallsBuildingType building, String itemId) {
        if (itemId == null || (scenario != null && !scenario.craftingRecipes(building.id(), plot.level()).contains(itemId))) {
            player.sendActionBar(Component.text("That recipe is not available here.", NamedTextColor.RED));
            return;
        }
        HallsItemType itemType = itemTypes.get(itemId);
        if (itemType == null) {
            player.sendActionBar(Component.text("That recipe is not loaded.", NamedTextColor.RED));
            return;
        }
        if (researchAccount != null && !researchAccount.isItemResearched(itemId)) {
            HallsResearchNode node = scenario == null ? null : scenario.researchNodeForItem(itemId);
            String label = node == null ? "Research required." : "Research " + node.name() + " first.";
            player.sendActionBar(Component.text(label, NamedTextColor.RED));
            return;
        }
        Map<String, Integer> scrapCost = scrapCost(itemType.recipe());
        Map<String, Integer> itemCost = itemCost(itemType.recipe());
        if (scrapAccount != null && !scrapAccount.canSpend(scrapCost)) {
            player.sendActionBar(Component.text("Not enough stored scrap.", NamedTextColor.RED));
            return;
        }
        if (!hasItemIngredients(player.getInventory(), itemCost)) {
            player.sendActionBar(Component.text("Missing ingredient items.", NamedTextColor.RED));
            return;
        }
        ItemStack crafted = itemFactory.apply(itemType);
        boolean willEquipArmor = canEquipEmptyArmorSlot(player.getInventory(), crafted);
        int outputSlot = willEquipArmor ? -1 : firstAvailableHotbarSlot(player.getInventory());
        if (!willEquipArmor && outputSlot < 0) {
            player.sendActionBar(Component.text("Your hotbar is full.", NamedTextColor.RED));
            return;
        }
        if (!itemCost.isEmpty()) {
            consumeItemIngredients(player.getInventory(), itemCost);
        }
        if (scrapAccount != null && !scrapAccount.spend(scrapCost)) {
            player.sendActionBar(Component.text("Not enough stored scrap.", NamedTextColor.RED));
            return;
        }
        if (willEquipArmor) {
            equipArmorSlot(player.getInventory(), crafted);
        } else {
            player.getInventory().setItem(outputSlot, crafted);
        }
        playBuildingSound(player, building, BuildingSound.USE);
        player.sendActionBar(Component.text("Crafted " + itemType.name() + ".", NamedTextColor.GREEN));
    }

    private void unlockResearch(Player player, String nodeId) {
        if (researchAccount == null || nodeId == null || nodeId.isBlank()) {
            player.sendActionBar(Component.text("Research is not available.", NamedTextColor.RED));
            return;
        }
        HallsResearchNode node = scenario == null ? null : scenario.researchNode(nodeId);
        if (node == null) {
            player.sendActionBar(Component.text("That research is not loaded.", NamedTextColor.RED));
            return;
        }
        if (researchAccount.isUnlocked(node.id())) {
            player.sendActionBar(Component.text("Already researched.", NamedTextColor.GRAY));
            return;
        }
        if (!researchAccount.canUnlock(node.id())) {
            player.sendActionBar(Component.text("Research prerequisites or points are missing.", NamedTextColor.RED));
            return;
        }
        if (researchAccount.unlock(node.id())) {
            world.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
            player.sendActionBar(Component.text("Researched " + node.name() + ".", NamedTextColor.GREEN));
        }
    }

    private boolean harvestMycelia(Player player, Plot plot, HallsBuildingType building) {
        HallsBuildingType.Level level = building.level(plot.level());
        List<String> harvestItems = level.harvestItems().isEmpty() ? level.giveItems() : level.harvestItems();
        int given = 0;
        for (String itemId : harvestItems) {
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
        if (given <= 0) {
            player.sendActionBar(Component.text("Your hotbar is full.", NamedTextColor.RED));
            return true;
        }
        plot.setHarvestRemaining(Math.max(0, plot.harvestRemaining() - 1));
        plot.setHarvestUsed(plot.harvestUsed() + 1);
        if (plot.harvestRemaining() <= 0) {
            setDisplays(plot, building, level.emptyParts().isEmpty() ? level.parts() : level.emptyParts());
        }
        playBuildingSound(player, building, BuildingSound.USE);
        player.sendActionBar(Component.text("Harvested " + given + " mycelia.", NamedTextColor.GREEN));
        return true;
    }

    private void setBuilding(Plot plot, HallsBuildingType building, int level) {
        removeDisplays(plot);
        plot.setBuilding(building.id(), level);
        setDisplays(plot, building, building.level(level).parts());
    }

    private void setDisplays(Plot plot, HallsBuildingType building, List<HallsBuildingType.Part> parts) {
        removeDisplays(plot);
        for (HallsBuildingType.Part part : parts) {
            double[] offset = rotatedOffset(part.offsetX(), part.offsetZ(), plot.facing());
            Location location = new Location(world,
                    plot.x() + 0.5 + offset[0],
                    plot.y() + part.offsetY(),
                    plot.z() + 0.5 + offset[1]);
            BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
                entity.setBlock(blockData(part.material(), part.blockData()));
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setTransformation(new Transformation(
                        centerOnDisplayOrigin(part.scaleX(), part.scaleZ(), plot.facing()),
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

    private void destroyBuilding(Player player, Plot plot, HallsBuildingType building) {
        if (isStorageLocker(building) && hasStoredItems(plot)) {
            player.sendActionBar(Component.text("Empty this locker before destroying it.", NamedTextColor.RED));
            return;
        }
        removeDisplays(plot);
        plot.clearBuilding();
        playBuildingSound(player, building, BuildingSound.DESTROY);
        player.sendMessage(Component.text("Destroyed " + building.name() + ".", NamedTextColor.RED));
    }

    private void initializeHarvest(Plot plot, HallsBuildingType building) {
        plot.setHarvestUsed(0);
        plot.setHarvestRemaining(defaultRunUses(building, plot.level()));
    }

    private void refreshHarvestForLevel(Plot plot, HallsBuildingType building) {
        HallsBuildingType.Level level = building.level(plot.level());
        plot.setHarvestRemaining(Math.max(0, defaultRunUses(building, plot.level()) - plot.harvestUsed()));
        if (plot.harvestRemaining() <= 0 && !level.emptyParts().isEmpty()) {
            setDisplays(plot, building, level.emptyParts());
        }
    }

    private boolean isCraftingStation(HallsBuildingType building) {
        return building.id().equals("cooking_pot")
                || building.id().equals("weapon_bench")
                || building.id().equals("armory")
                || building.id().equals("camp_station");
    }

    private boolean isPermanentBuilding(HallsBuildingType building) {
        return building != null && building.id().equals("camp_station");
    }

    private boolean isStationPlot(Plot plot) {
        return plot != null && plot.size().equals("station");
    }

    private String upgradeBlueprintCost(HallsBuildingType building) {
        if (building == null) {
            return null;
        }
        if (isCraftingStation(building) || isStorageLocker(building) || isSculkPurifier(building)) {
            return building.blueprint();
        }
        return null;
    }

    private List<String> upgradeLore(HallsBuildingType building, Plot plot) {
        HallsBuildingType.Level next = building.level(plot.level() + 1);
        List<String> lore = new ArrayList<>();
        lore.add("Cost: " + formatCost(next.upgradeCost()));
        String blueprintCost = upgradeBlueprintCost(building);
        if (blueprintCost != null) {
            lore.add("Blueprint: " + itemName(blueprintCost));
        }
        if (isCraftingStation(building)) {
            List<String> currentRecipes = scenario == null ? List.of() : scenario.craftingRecipes(building.id(), plot.level());
            List<String> nextRecipes = scenario == null ? List.of() : scenario.craftingRecipes(building.id(), plot.level() + 1);
            List<String> unlocked = nextRecipes.stream()
                    .filter(itemId -> !currentRecipes.contains(itemId))
                    .map(this::itemName)
                    .toList();
            if (!unlocked.isEmpty()) {
                lore.add("New recipes:");
                lore.addAll(unlocked.stream().map(name -> "- " + name).toList());
            } else {
                lore.add("No new recipes at this level.");
            }
        } else if (building.id().equals("mycelia_farm")) {
            List<String> harvestItems = next.harvestItems().isEmpty() ? next.giveItems() : next.harvestItems();
            lore.add("Harvest uses: " + next.harvestUses());
            if (!harvestItems.isEmpty()) {
                lore.add("Harvests: " + harvestItems.stream().map(this::itemName).collect(java.util.stream.Collectors.joining(", ")));
            }
        } else if (isStorageLocker(building)) {
            lore.add("Storage slots: " + storageSlots(building, plot.level()) + " -> " + storageSlots(building, plot.level() + 1));
        } else if (isSculkPurifier(building)) {
            lore.add("Purify amount: " + formatStatAmount(purifyAmount(building, plot.level()))
                    + "% -> " + formatStatAmount(purifyAmount(building, plot.level() + 1)) + "%");
        } else if (building.id().equals("grindstone")) {
            lore.add("Damage bonus: +" + formatStatAmount(grindstoneDamageBonus(plot.level()))
                    + " -> +" + formatStatAmount(grindstoneDamageBonus(plot.level() + 1)));
        } else if (building.id().equals("forge")) {
            lore.add("Repair: " + formatStatAmount(forgeRepairPercent(plot.level()))
                    + "% -> " + formatStatAmount(forgeRepairPercent(plot.level() + 1)) + "%");
        } else if (building.id().equals("scanner")) {
            lore.add("Scans floors: " + Math.max(1, Math.min(3, plot.level()))
                    + " -> " + Math.max(1, Math.min(3, plot.level() + 1)));
        } else if (building.id().equals("health_totem")) {
            lore.add("Max health: +" + healthTotemBonus(plot.level())
                    + " -> +" + healthTotemBonus(plot.level() + 1));
        } else if (building.id().equals("speed_totem")) {
            lore.add("Movement speed: +" + speedTotemPercent(plot.level())
                    + "% -> +" + speedTotemPercent(plot.level() + 1) + "%");
        } else if (building.id().equals("elevator_drill")) {
            lore.add("Quota multiplier: "
                    + formatStatAmount(elevatorDrillQuotaMultiplier(plot.level()) * 100.0) + "% -> "
                    + formatStatAmount(elevatorDrillQuotaMultiplier(plot.level() + 1) * 100.0) + "%");
        } else if (!next.giveItems().isEmpty()) {
            lore.add("Outputs: " + next.giveItems().stream().map(this::itemName).collect(java.util.stream.Collectors.joining(", ")));
        } else {
            lore.add("Improves the building display.");
        }
        return lore;
    }

    private String itemName(String itemId) {
        HallsItemType itemType = itemTypes.get(itemId);
        return itemType == null ? itemId.replace('_', ' ') : itemType.name();
    }

    private ItemStack menuItem(Material material,
                               String name,
                               NamedTextColor color,
                               List<String> loreLines,
                               String action,
                               String itemId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream()
                        .map(line -> Component.text(line, NamedTextColor.GRAY))
                        .toList());
            }
            if (action != null) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_camp_action"),
                        PersistentDataType.STRING, action);
            }
            if (itemId != null) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_camp_item"),
                        PersistentDataType.STRING, itemId);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack pageItem(Material material, String name, String category, int page) {
        ItemStack item = menuItem(material, name, NamedTextColor.YELLOW, List.of("Page " + (page + 1)),
                "craft_page", category);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_camp_page"),
                    PersistentDataType.INTEGER, page);
            item.setItemMeta(meta);
        }
        return item;
    }

    private int campPage(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer page = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "hoc_camp_page"), PersistentDataType.INTEGER);
        return page == null ? 0 : Math.max(0, page);
    }

    private String categoryName(String category) {
        return switch (category == null ? "" : category) {
            case "weapon" -> "Weapons";
            case "armor" -> "Armor";
            case "utility" -> "Utilities";
            case "food" -> "Food";
            default -> "Recipes";
        };
    }

    private String researchPointsLabel() {
        return Integer.toString(researchAccount == null ? 0 : researchAccount.points());
    }

    private String formatCost(Map<String, Integer> cost) {
        if (cost == null || cost.isEmpty()) {
            return "free";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (entry.getValue() > 0) {
                parts.add(entry.getValue() + " " + entry.getKey().replace('_', ' '));
            }
        }
        return parts.isEmpty() ? "free" : String.join(", ", parts);
    }

    private void openStorage(Player player, Plot plot, HallsBuildingType building) {
        int slots = storageSlots(building, plot.level());
        int openSlots = storageOpenSlots(plot);
        Inventory inventory = Bukkit.createInventory(new StorageMenu(plot.id()), storageInventorySize(openSlots),
                Component.text(building.name() + " Storage", NamedTextColor.DARK_GREEN));
        ItemStack[] stored = plot.storageContents();
        for (int slot = 0; slot < Math.min(openSlots, stored.length); slot++) {
            inventory.setItem(slot, cloneOrNull(stored[slot]));
        }
        ItemStack locked = menuItem(Material.GRAY_STAINED_GLASS_PANE, "Locked Slot", NamedTextColor.GRAY,
                List.of("Upgrade this locker for more storage."), null, null);
        markLockedStorageFiller(locked);
        for (int slot = slots; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null || inventory.getItem(slot).getType().isAir()) {
                inventory.setItem(slot, locked);
            }
        }
        playBuildingSound(player, building, BuildingSound.OPEN);
        player.openInventory(inventory);
    }

    private void activateSculkPurifier(Player player, Plot plot, HallsBuildingType building) {
        if (sculkAccount == null) {
            player.sendActionBar(Component.text("This purifier is not connected.", NamedTextColor.RED));
            return;
        }
        if (plot.harvestRemaining() <= 0) {
            player.sendActionBar(Component.text("This purifier is depleted for this run.", NamedTextColor.GRAY));
            return;
        }
        boolean affected = sculkAccount.reduce(player.getUniqueId(), purifyAmount(building, plot.level()));
        if (!affected) {
            player.sendActionBar(Component.text("No sculk pressure to purify.", NamedTextColor.GRAY));
            return;
        }
        plot.setHarvestRemaining(plot.harvestRemaining() - 1);
        plot.setHarvestUsed(plot.harvestUsed() + 1);
        world.spawnParticle(org.bukkit.Particle.WAX_OFF, player.getLocation().add(0.0, 1.0, 0.0),
                45, 1.2, 0.7, 1.2, 0.03);
        playBuildingSound(player, building, BuildingSound.USE);
        player.sendActionBar(Component.text("Purified your sculk pressure. Charges left: "
                + plot.harvestRemaining() + ".", NamedTextColor.AQUA));
    }

    private void activateGrindstone(Player player, Plot plot, HallsBuildingType building) {
        if (plot.harvestRemaining() <= 0) {
            player.sendActionBar(Component.text("This grindstone is depleted for this run.", NamedTextColor.GRAY));
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isHallsCategory(item, "weapon")) {
            player.sendActionBar(Component.text("Hold a Halls weapon to sharpen it.", NamedTextColor.RED));
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        double currentDamage = hallsStat(meta, "melee_damage");
        if (currentDamage <= 0.0) {
            player.sendActionBar(Component.text("That weapon has no melee damage stat.", NamedTextColor.RED));
            return;
        }
        double bonus = grindstoneDamageBonus(plot.level());
        double nextDamage = currentDamage + bonus;
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                new NamespacedKey(plugin, "hoc_melee_damage_grindstone"),
                nextDamage - 1.0,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.HAND
        ));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "hoc_stat_melee_damage"),
                PersistentDataType.DOUBLE,
                nextDamage);
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "hoc_grindstone_bonus"),
                PersistentDataType.DOUBLE,
                hallsDouble(meta, "hoc_grindstone_bonus") + bonus);
        addOrReplaceLoreLine(meta, "Grindstone bonus: +" + formatStatAmount(hallsDouble(meta, "hoc_grindstone_bonus")) + " damage");
        item.setItemMeta(meta);
        plot.setHarvestRemaining(plot.harvestRemaining() - 1);
        plot.setHarvestUsed(plot.harvestUsed() + 1);
        playBuildingSound(player, building, BuildingSound.USE);
        player.sendActionBar(Component.text("Sharpened weapon to " + formatStatAmount(nextDamage)
                + " melee damage.", NamedTextColor.GREEN));
    }

    private void activateForge(Player player, Plot plot, HallsBuildingType building) {
        if (plot.harvestRemaining() <= 0) {
            player.sendActionBar(Component.text("This forge is depleted for this run.", NamedTextColor.GRAY));
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof Damageable)) {
            player.sendActionBar(Component.text("Hold a damaged Halls item to repair it.", NamedTextColor.RED));
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !isHallsItem(meta)) {
            player.sendActionBar(Component.text("Hold a Halls item to repair it.", NamedTextColor.RED));
            return;
        }
        Damageable damageable = (Damageable) meta;
        int maxDamage = damageable.getMaxDamage();
        int currentDamage = damageable.getDamage();
        if (maxDamage <= 0 || currentDamage <= 0) {
            player.sendActionBar(Component.text("That item is already fully repaired.", NamedTextColor.GRAY));
            return;
        }
        int repair = Math.max(1, (int) Math.ceil(maxDamage * forgeRepairPercent(plot.level()) / 100.0));
        damageable.setDamage(Math.max(0, currentDamage - repair));
        item.setItemMeta((ItemMeta) damageable);
        plot.setHarvestRemaining(plot.harvestRemaining() - 1);
        plot.setHarvestUsed(plot.harvestUsed() + 1);
        playBuildingSound(player, building, BuildingSound.USE);
        player.sendActionBar(Component.text("Repaired " + Math.min(repair, currentDamage)
                + " durability. Charges left: " + plot.harvestRemaining() + ".", NamedTextColor.GREEN));
    }

    private void activateScanner(Player player, Plot plot) {
        if (scanner == null) {
            player.sendActionBar(Component.text("This scanner is not connected.", NamedTextColor.RED));
            return;
        }
        List<String> lines = scanner.apply(plot.level());
        if (lines.isEmpty()) {
            player.sendActionBar(Component.text("No upcoming floors found.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("Scanner results:", NamedTextColor.AQUA));
        for (String line : lines) {
            player.sendMessage(Component.text("- " + line, NamedTextColor.GRAY));
        }
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        playBuildingSound(player, building, BuildingSound.USE);
    }

    private void activateHealthTotem(Player player, Plot plot) {
        if (totemAccount == null) {
            player.sendActionBar(Component.text("This totem is not connected.", NamedTextColor.RED));
            return;
        }
        if (plot.harvestRemaining() <= 0) {
            player.sendActionBar(Component.text("This totem is depleted for this run.", NamedTextColor.GRAY));
            return;
        }
        if (!totemAccount.applyHealthTotem(player, plot.id(), plot.level())) {
            return;
        }
        plot.setHarvestRemaining(plot.harvestRemaining() - 1);
        plot.setHarvestUsed(plot.harvestUsed() + 1);
        world.spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0.0, 1.0, 0.0),
                10, 0.45, 0.55, 0.45, 0.02);
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        playBuildingSound(player, building, BuildingSound.USE);
        player.sendActionBar(Component.text("Vitality increased. Charges left: "
                + plot.harvestRemaining() + ".", NamedTextColor.GREEN));
    }

    private void activateSpeedTotem(Player player, Plot plot) {
        if (totemAccount == null) {
            player.sendActionBar(Component.text("This totem is not connected.", NamedTextColor.RED));
            return;
        }
        if (plot.harvestRemaining() <= 0) {
            player.sendActionBar(Component.text("This totem is depleted for this run.", NamedTextColor.GRAY));
            return;
        }
        if (!totemAccount.applySpeedTotem(player, plot.id(), plot.level())) {
            return;
        }
        plot.setHarvestRemaining(plot.harvestRemaining() - 1);
        plot.setHarvestUsed(plot.harvestUsed() + 1);
        world.spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation().add(0.0, 0.2, 0.0),
                24, 0.55, 0.1, 0.55, 0.03);
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        playBuildingSound(player, building, BuildingSound.USE);
        player.sendActionBar(Component.text("Speed increased. Charges left: "
                + plot.harvestRemaining() + ".", NamedTextColor.GREEN));
    }

    private boolean isStorageLocker(HallsBuildingType building) {
        return building != null && isStorageLocker(building.id());
    }

    private boolean isStorageLocker(String buildingId) {
        return buildingId != null && (buildingId.equals("storage_locker") || buildingId.startsWith("storage_locker_"));
    }

    private boolean isSculkPurifier(HallsBuildingType building) {
        return building != null && isSculkPurifierId(building.id());
    }

    private boolean isSculkPurifierId(String buildingId) {
        return buildingId != null && (buildingId.equals("sculk_purifier") || buildingId.startsWith("sculk_purifier_"));
    }

    private int storageSlots(HallsBuildingType building, int level) {
        return Math.max(1, Math.min(54, 9 * Math.max(1, Math.min(3, level))));
    }

    private int storageSlots(int plotId) {
        Plot plot = plotsById.get(plotId);
        if (plot == null || plot.buildingId() == null) {
            return 0;
        }
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        return building == null ? 0 : storageSlots(building, plot.level());
    }

    private int storageOpenSlots(Plot plot) {
        int slots = storageSlots(plot.id());
        ItemStack[] stored = plot.storageContents();
        for (int i = stored.length - 1; i >= slots; i--) {
            if (stored[i] != null && !stored[i].getType().isAir()) {
                return i + 1;
            }
        }
        return slots;
    }

    private int storageInventorySize(int slots) {
        return Math.max(9, Math.min(54, ((Math.max(1, slots) + 8) / 9) * 9));
    }

    private double purifyAmount(HallsBuildingType building, int level) {
        double base = switch (building.size()) {
            case "medium" -> 8.0;
            case "large" -> 12.0;
            default -> 5.0;
        };
        return base * Math.max(1, Math.min(3, level));
    }

    private double grindstoneDamageBonus(int level) {
        return Math.max(1, Math.min(3, level));
    }

    private double forgeRepairPercent(int level) {
        return 30.0 * Math.max(1, Math.min(3, level));
    }

    private int defaultRunUses(HallsBuildingType building, int level) {
        if (building == null) {
            return 0;
        }
        int configured = building.level(level).harvestUses();
        if (configured > 0) {
            return configured;
        }
        if (isSculkPurifierId(building.id())) {
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

    private int healthTotemBonus(int level) {
        return 2 * Math.max(1, Math.min(3, level));
    }

    private int speedTotemPercent(int level) {
        return 5 * Math.max(1, Math.min(3, level));
    }

    private double elevatorDrillQuotaMultiplier(int level) {
        return switch (Math.max(1, Math.min(3, level))) {
            case 1 -> 0.9;
            case 2 -> 0.8;
            default -> 0.7;
        };
    }

    private void playBuildingSound(Player player, HallsBuildingType building, BuildingSound sound) {
        if (player == null) {
            return;
        }
        String id = building == null ? "" : building.id();
        switch (sound) {
            case OPEN -> world.playSound(player.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.45f, 1.2f);
            case BUILD -> world.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.25f);
            case UPGRADE -> world.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.1f);
            case DESTROY -> world.playSound(player.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 0.7f, 1.1f);
            case USE -> {
                if (id.equals("cooking_pot") || id.equals("mycelia_farm")) {
                    world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.35f);
                } else if (id.equals("weapon_bench") || id.equals("armory")) {
                    world.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.25f);
                } else if (id.equals("grindstone")) {
                    world.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.8f, 1.0f);
                } else if (id.equals("forge")) {
                    world.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
                } else if (id.equals("scanner")) {
                    world.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.55f);
                } else if (id.equals("health_totem")) {
                    world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.0f);
                } else if (id.equals("speed_totem")) {
                    world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.55f);
                } else if (isSculkPurifierId(id)) {
                    world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.35f);
                } else if (isStorageLocker(id)) {
                    world.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.0f);
                } else {
                    world.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                }
            }
        }
    }

    private boolean hasStoredItems(Plot plot) {
        for (ItemStack item : plot.storageContents()) {
            if (item != null && !item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private ItemStack[] cloneStorage(ItemStack[] source) {
        if (source == null || source.length == 0) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = cloneOrNull(source[i]);
        }
        return copy;
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private String formatStatAmount(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private Map<String, Integer> scrapCost(Map<String, Integer> cost) {
        Map<String, Integer> scrap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (isScrapCost(entry.getKey())) {
                scrap.put(entry.getKey(), entry.getValue());
            }
        }
        return scrap;
    }

    private Map<String, Integer> itemCost(Map<String, Integer> cost) {
        Map<String, Integer> items = new HashMap<>();
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (!isScrapCost(entry.getKey())) {
                items.put(entry.getKey(), entry.getValue());
            }
        }
        return items;
    }

    private boolean isScrapCost(String key) {
        return key.equals("wood") || key.equals("wood_scrap")
                || key.equals("iron") || key.equals("iron_scrap")
                || key.equals("diamond") || key.equals("diamond_scrap")
                || key.equals("redstone") || key.equals("redstone_scrap");
    }

    private boolean hasItemIngredients(PlayerInventory inventory, Map<String, Integer> cost) {
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (countHotbarItem(inventory, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private int countHotbarItem(PlayerInventory inventory, String itemId) {
        int count = 0;
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (hasHallsItemId(item, itemId)) {
                count += Math.max(1, item.getAmount());
            }
        }
        return count;
    }

    private void consumeItemIngredients(PlayerInventory inventory, Map<String, Integer> cost) {
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 0; slot <= 8 && remaining > 0; slot++) {
                ItemStack item = inventory.getItem(slot);
                if (!hasHallsItemId(item, entry.getKey())) {
                    continue;
                }
                int take = Math.min(remaining, Math.max(1, item.getAmount()));
                remaining -= take;
                int newAmount = item.getAmount() - take;
                if (newAmount <= 0) {
                    inventory.setItem(slot, null);
                } else {
                    ItemStack remainingItem = item.clone();
                    remainingItem.setAmount(newAmount);
                    inventory.setItem(slot, remainingItem);
                }
            }
        }
    }

    private boolean hasHallsItemId(ItemStack item, String itemId) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        String actual = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "hoc_item_id"), PersistentDataType.STRING);
        return itemId.equals(actual);
    }

    private void markLockedStorageFiller(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_locked_storage_slot"),
                PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private boolean isLockedStorageFiller(ItemStack item) {
        return item != null
                && !item.getType().isAir()
                && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "hoc_locked_storage_slot"), PersistentDataType.BYTE);
    }

    private boolean isHallsCategory(ItemStack item, String category) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        String actual = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "hoc_item_category"), PersistentDataType.STRING);
        return category.equals(actual);
    }

    private boolean isHallsItem(ItemMeta meta) {
        return meta.getPersistentDataContainer().has(new NamespacedKey(plugin, "hoc_item_id"), PersistentDataType.STRING);
    }

    private double hallsStat(ItemMeta meta, String statId) {
        return hallsDouble(meta, "hoc_stat_" + statId);
    }

    private double hallsDouble(ItemMeta meta, String key) {
        Double value = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, key), PersistentDataType.DOUBLE);
        return value == null ? 0.0 : value;
    }

    private void addOrReplaceLoreLine(ItemMeta meta, String line) {
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        Component component = Component.text(line, NamedTextColor.DARK_AQUA);
        for (int i = 0; i < lore.size(); i++) {
            Component existing = lore.get(i);
            if (net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(existing).startsWith("Grindstone bonus:")) {
                lore.set(i, component);
                meta.lore(lore);
                return;
            }
        }
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(component);
        meta.lore(lore);
    }

    private boolean canEquipEmptyArmorSlot(PlayerInventory inventory, ItemStack item) {
        org.bukkit.inventory.EquipmentSlot slot = armorSlot(item);
        return slot != null && inventory.getItem(slot) == null;
    }

    private void equipArmorSlot(PlayerInventory inventory, ItemStack item) {
        org.bukkit.inventory.EquipmentSlot slot = armorSlot(item);
        if (slot == null) {
            return;
        }
        inventory.setItem(slot, item);
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

    private Vector3f centerOnDisplayOrigin(double scaleX, double scaleZ, BlockFace facing) {
        return switch (facing) {
            case EAST -> new Vector3f((float) (-scaleZ * 0.5), 0.0f, (float) (scaleX * 0.5));
            case SOUTH -> new Vector3f((float) (scaleX * 0.5), 0.0f, (float) (scaleZ * 0.5));
            case WEST -> new Vector3f((float) (scaleZ * 0.5), 0.0f, (float) (-scaleX * 0.5));
            default -> new Vector3f((float) (-scaleX * 0.5), 0.0f, (float) (-scaleZ * 0.5));
        };
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
        private final double x;
        private final int y;
        private final double z;
        private final BlockFace facing;
        private final UUID interactionId;
        private final List<UUID> displayIds = new ArrayList<>();
        private String buildingId;
        private int level;
        private int harvestRemaining;
        private int harvestUsed;
        private ItemStack[] storageContents = new ItemStack[0];

        private Plot(int id, String size, double x, int y, double z, BlockFace facing, UUID interactionId) {
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

        private double x() {
            return x;
        }

        private int y() {
            return y;
        }

        private double z() {
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

        private void clearBuilding() {
            this.buildingId = null;
            this.level = 0;
            this.harvestRemaining = 0;
            this.harvestUsed = 0;
            this.storageContents = new ItemStack[0];
        }

        private int harvestRemaining() {
            return harvestRemaining;
        }

        private void setHarvestRemaining(int harvestRemaining) {
            this.harvestRemaining = Math.max(0, harvestRemaining);
        }

        private int harvestUsed() {
            return harvestUsed;
        }

        private void setHarvestUsed(int harvestUsed) {
            this.harvestUsed = Math.max(0, harvestUsed);
        }

        private ItemStack[] storageContents() {
            return storageContents;
        }

        private void setStorageContents(ItemStack[] storageContents) {
            if (storageContents == null || storageContents.length == 0) {
                this.storageContents = new ItemStack[0];
                return;
            }
            this.storageContents = new ItemStack[storageContents.length];
            for (int i = 0; i < storageContents.length; i++) {
                this.storageContents[i] = storageContents[i] == null || storageContents[i].getType().isAir()
                        ? null
                        : storageContents[i].clone();
            }
        }
    }

    private static final int[] RECIPE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private enum BuildingSound {
        OPEN,
        BUILD,
        UPGRADE,
        DESTROY,
        USE
    }

    public record PlotState(int plotId, String buildingId, int level, int harvestRemaining, int harvestUsed,
                            ItemStack[] storageContents) {
        public PlotState {
            storageContents = storageContents == null ? new ItemStack[0] : storageContents.clone();
        }
    }

    private record Door(int id, int x, int z, int worldX, int y, int worldZ, UUID interactionId) {
    }

    private record Cell(int x, int z) {
    }

    private record CampMenu(int plotId, String view, String category, int page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record StorageMenu(int plotId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
