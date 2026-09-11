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
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
        int reduceAll(double amount);
    }

    private final JavaPlugin plugin;
    private final World world;
    private final HallsScenario scenario;
    private final Map<String, HallsBuildingType> buildingTypes;
    private final Map<String, HallsItemType> itemTypes;
    private final Function<HallsItemType, ItemStack> itemFactory;
    private final ScrapAccount scrapAccount;
    private final SculkAccount sculkAccount;
    private final Map<UUID, Plot> plotsByEntity = new HashMap<>();
    private final Map<Integer, Plot> plotsById = new HashMap<>();

    public HallsCampRuntime(JavaPlugin plugin,
                            World world,
                            HallsScenario scenario,
                            Map<String, HallsBuildingType> buildingTypes,
                            Map<String, HallsItemType> itemTypes,
                            Function<HallsItemType, ItemStack> itemFactory,
                            ScrapAccount scrapAccount,
                            SculkAccount sculkAccount) {
        this.plugin = plugin;
        this.world = world;
        this.scenario = scenario;
        this.buildingTypes = buildingTypes == null ? Map.of() : Map.copyOf(buildingTypes);
        this.itemTypes = itemTypes == null ? Map.of() : Map.copyOf(itemTypes);
        this.itemFactory = itemFactory;
        this.scrapAccount = scrapAccount;
        this.sculkAccount = sculkAccount;
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

    public void restore(List<PlotState> states) {
        if (states == null || states.isEmpty()) {
            return;
        }
        for (PlotState state : states) {
            Plot plot = plotsById.get(state.plotId());
            HallsBuildingType building = buildingTypes.get(state.buildingId());
            if (plot == null || building == null || !building.fits(plot.size())) {
                continue;
            }
            int level = Math.max(1, Math.min(3, state.level()));
            setBuilding(plot, building, level);
            plot.setHarvestRemaining(state.harvestRemaining());
            plot.setHarvestUsed(state.harvestUsed());
            plot.setStorageContents(state.storageContents());
            HallsBuildingType.Level buildingLevel = building.level(level);
            if (plot.harvestRemaining() <= 0 && !buildingLevel.emptyParts().isEmpty()) {
                setDisplays(plot, building, buildingLevel.emptyParts());
            }
        }
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
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        if (building != null && building.id().equals("mycelia_farm") && plot.harvestRemaining() > 0) {
            return harvestMycelia(player, plot, building);
        }
        openBuildingMenu(player, plot);
        return true;
    }

    public boolean handleInventoryClick(InventoryClickEvent event) {
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
                destroyBuilding(player, plot, building);
                player.closeInventory();
            }
            case "craft" -> {
                String itemId = clicked.getItemMeta().getPersistentDataContainer()
                        .get(new NamespacedKey(plugin, "hoc_camp_item"), PersistentDataType.STRING);
                craftRecipe(player, plot, building, itemId);
                openBuildingMenu(player, plot);
            }
            case "storage" -> openStorage(player, plot, building);
            case "purify" -> {
                activateSculkPurifier(player, plot, building);
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
        Plot plot = plotsById.get(menu.plotId());
        if (plot != null && plot.buildingId() != null && isStorageLocker(plot.buildingId())) {
            plot.setStorageContents(event.getInventory().getContents());
        }
        return true;
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
        refreshHarvestForLevel(plot, building);
        player.sendMessage(Component.text("Upgraded " + building.name() + " to level " + plot.level() + ".", NamedTextColor.GREEN));
        world.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.1f);
        return true;
    }

    private void openBuildingMenu(Player player, Plot plot) {
        HallsBuildingType building = buildingTypes.get(plot.buildingId());
        if (building == null) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(new CampMenu(plot.id()), 27,
                Component.text(building.name() + " L" + plot.level(), NamedTextColor.DARK_GREEN));
        inventory.setItem(4, menuItem(Material.OAK_SIGN, building.name(), NamedTextColor.GREEN,
                List.of("Level " + plot.level(), "Plot size: " + plot.size()), null, null));
        List<String> recipes = scenario == null ? List.of() : scenario.craftingRecipes(building.id(), plot.level());
        int recipeIndex = 0;
        if (isCraftingStation(building) && !recipes.isEmpty()) {
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
                    List.of("Reduces party sculk pressure by " + formatStatAmount(purifyAmount(building, plot.level())) + "%."),
                    "purify", null));
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
        if (plot.level() < 3) {
            HallsBuildingType.Level next = building.level(plot.level() + 1);
            inventory.setItem(22, menuItem(Material.SMITHING_TABLE, "Upgrade", NamedTextColor.YELLOW,
                    upgradeLore(building, plot), "upgrade", null));
        } else {
            inventory.setItem(22, menuItem(Material.SMITHING_TABLE, "Max Level", NamedTextColor.GRAY,
                    List.of("This building is already level 3."), null, null));
        }
        inventory.setItem(26, menuItem(Material.TNT, "Destroy", NamedTextColor.RED,
                List.of("Removes the building.", "The blueprint is not returned."), "destroy", null));
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
        world.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.8f, 1.25f);
        player.sendActionBar(Component.text("Crafted " + itemType.name() + ".", NamedTextColor.GREEN));
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
        world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.35f);
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
        world.playSound(player.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 0.7f, 1.1f);
        player.sendMessage(Component.text("Destroyed " + building.name() + ".", NamedTextColor.RED));
    }

    private void initializeHarvest(Plot plot, HallsBuildingType building) {
        HallsBuildingType.Level level = building.level(plot.level());
        plot.setHarvestUsed(0);
        plot.setHarvestRemaining(level.harvestUses());
    }

    private void refreshHarvestForLevel(Plot plot, HallsBuildingType building) {
        HallsBuildingType.Level level = building.level(plot.level());
        plot.setHarvestRemaining(Math.max(0, level.harvestUses() - plot.harvestUsed()));
        if (plot.harvestRemaining() <= 0 && !level.emptyParts().isEmpty()) {
            setDisplays(plot, building, level.emptyParts());
        }
    }

    private boolean isCraftingStation(HallsBuildingType building) {
        return building.id().equals("cooking_pot")
                || building.id().equals("weapon_bench")
                || building.id().equals("armory");
    }

    private List<String> upgradeLore(HallsBuildingType building, Plot plot) {
        HallsBuildingType.Level next = building.level(plot.level() + 1);
        List<String> lore = new ArrayList<>();
        lore.add("Cost: " + formatCost(next.upgradeCost()));
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
        Inventory inventory = Bukkit.createInventory(new StorageMenu(plot.id()), slots,
                Component.text(building.name() + " Storage", NamedTextColor.DARK_GREEN));
        ItemStack[] stored = plot.storageContents();
        for (int slot = 0; slot < Math.min(slots, stored.length); slot++) {
            inventory.setItem(slot, cloneOrNull(stored[slot]));
        }
        player.openInventory(inventory);
    }

    private void activateSculkPurifier(Player player, Plot plot, HallsBuildingType building) {
        if (sculkAccount == null) {
            player.sendActionBar(Component.text("This purifier is not connected.", NamedTextColor.RED));
            return;
        }
        int affected = sculkAccount.reduceAll(purifyAmount(building, plot.level()));
        if (affected <= 0) {
            player.sendActionBar(Component.text("No sculk pressure to purify.", NamedTextColor.GRAY));
            return;
        }
        world.spawnParticle(org.bukkit.Particle.WAX_OFF, player.getLocation().add(0.0, 1.0, 0.0),
                45, 1.2, 0.7, 1.2, 0.03);
        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.35f);
        player.sendActionBar(Component.text("Purified sculk pressure for " + affected + " player"
                + (affected == 1 ? "" : "s") + ".", NamedTextColor.AQUA));
    }

    private boolean isStorageLocker(HallsBuildingType building) {
        return building != null && isStorageLocker(building.id());
    }

    private boolean isStorageLocker(String buildingId) {
        return buildingId != null && buildingId.startsWith("storage_locker_");
    }

    private boolean isSculkPurifier(HallsBuildingType building) {
        return building != null && building.id().startsWith("sculk_purifier_");
    }

    private int storageSlots(HallsBuildingType building, int level) {
        int baseRows = switch (building.size()) {
            case "medium" -> 2;
            case "large" -> 3;
            default -> 1;
        };
        return Math.max(9, Math.min(54, baseRows * Math.max(1, Math.min(3, level)) * 9));
    }

    private double purifyAmount(HallsBuildingType building, int level) {
        double base = switch (building.size()) {
            case "medium" -> 18.0;
            case "large" -> 30.0;
            default -> 10.0;
        };
        return base * Math.max(1, Math.min(3, level));
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

    private static final int[] RECIPE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25};

    public record PlotState(int plotId, String buildingId, int level, int harvestRemaining, int harvestUsed,
                            ItemStack[] storageContents) {
        public PlotState {
            storageContents = storageContents == null ? new ItemStack[0] : storageContents.clone();
        }
    }

    private record CampMenu(int plotId) implements InventoryHolder {
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
