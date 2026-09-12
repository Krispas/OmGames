package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class HallsRecipeBookMenu {
    static final String ACTION_BUILDINGS = "buildings";
    static final String ACTION_CRAFTING = "crafting";
    static final String ACTION_BUILDING_DETAIL = "building_detail";
    static final String ACTION_BACK = "back";

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private HallsRecipeBookMenu() {
    }

    static void openIndex(JavaPlugin plugin, Player player, HallsScenario scenario) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.INDEX, null), 27,
                Component.text("Halls Recipes", NamedTextColor.DARK_RED));
        inventory.setItem(11, item(plugin, Material.BRICKS, "Buildings", NamedTextColor.GOLD,
                List.of("Blueprint locations for this run.", "Shows normal, rare, and level-specific pools."),
                ACTION_BUILDINGS, null));
        inventory.setItem(15, item(plugin, Material.CRAFTING_TABLE, "Crafting", NamedTextColor.AQUA,
                List.of("Station recipes and the building level that unlocks them."),
                ACTION_CRAFTING, null));
        inventory.setItem(22, item(plugin, Material.MAP, scenario.name(), NamedTextColor.GRAY,
                List.of("Scenario recipe book."), null, null));
        player.openInventory(inventory);
    }

    static void openBuildings(JavaPlugin plugin,
                              Player player,
                              HallsScenario scenario,
                              Map<String, HallsBuildingType> buildingTypes,
                              Map<String, HallsItemType> itemTypes) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.BUILDINGS, null), 54,
                Component.text("Building Blueprints", NamedTextColor.DARK_RED));
        List<String> allowed = scenario.allowedItems("buildings");
        List<HallsBuildingType> buildings = buildingTypes.values().stream()
                .filter(building -> allowed.isEmpty() || allowed.contains(building.id()))
                .sorted(java.util.Comparator.comparing(HallsBuildingType::name))
                .toList();
        int index = 0;
        for (HallsBuildingType building : buildings) {
            if (index >= CONTENT_SLOTS.length) {
                break;
            }
            inventory.setItem(CONTENT_SLOTS[index++], buildingItem(plugin, scenario, building, itemTypes));
        }
        if (index == 0) {
            inventory.setItem(22, item(plugin, Material.BARRIER, "No Buildings", NamedTextColor.GRAY,
                    List.of("This scenario does not expose building recipes."), null, null));
        }
        inventory.setItem(45, item(plugin, Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), ACTION_BACK, null));
        inventory.setItem(49, item(plugin, Material.CRAFTING_TABLE, "Crafting", NamedTextColor.AQUA,
                List.of("View station recipe unlocks."), ACTION_CRAFTING, null));
        player.openInventory(inventory);
    }

    static void openBuildingDetail(JavaPlugin plugin,
                                   Player player,
                                   HallsScenario scenario,
                                   HallsBuildingType building,
                                   Map<String, HallsItemType> itemTypes) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.BUILDING_DETAIL, building.id()), 27,
                Component.text(building.name(), NamedTextColor.DARK_RED));
        inventory.setItem(4, buildingItem(plugin, scenario, building, itemTypes));
        HallsItemType blueprint = itemTypes.get(building.blueprint());
        inventory.setItem(11, blueprint == null
                ? item(plugin, Material.PAPER, readable(building.blueprint()), NamedTextColor.AQUA,
                List.of("Blueprint item is not loaded."), null, null)
                : previewItem(plugin, blueprint, List.of("Find: " + blueprintLocations(scenario, building.blueprint()))));
        inventory.setItem(13, item(plugin, Material.OAK_SIGN, "Plot Size", NamedTextColor.YELLOW,
                List.of(capitalize(building.size()) + " plot or larger."), null, null));
        inventory.setItem(15, item(plugin, Material.SMITHING_TABLE, "Upgrade Costs", NamedTextColor.YELLOW,
                upgradeCostLore(building), null, null));
        inventory.setItem(22, item(plugin, Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), ACTION_BUILDINGS, null));
        player.openInventory(inventory);
    }

    static void openCrafting(JavaPlugin plugin,
                             Player player,
                             HallsScenario scenario,
                             Map<String, HallsBuildingType> buildingTypes,
                             Map<String, HallsItemType> itemTypes) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.CRAFTING, null), 54,
                Component.text("Crafting Recipes", NamedTextColor.DARK_RED));
        int index = 0;
        for (String stationId : List.of("cooking_pot", "weapon_bench", "armory")) {
            HallsBuildingType station = buildingTypes.get(stationId);
            if (station == null) {
                continue;
            }
            for (int level = 1; level <= 3; level++) {
                List<String> recipes = recipesUnlockedAt(scenario, stationId, level);
                for (String itemId : recipes) {
                    HallsItemType itemType = itemTypes.get(itemId);
                    if (itemType == null || index >= CONTENT_SLOTS.length) {
                        continue;
                    }
                    inventory.setItem(CONTENT_SLOTS[index++], recipeItem(plugin, itemType, station.name(), level));
                }
            }
        }
        if (index == 0) {
            inventory.setItem(22, item(plugin, Material.BARRIER, "No Recipes", NamedTextColor.GRAY,
                    List.of("This scenario has no station recipes."), null, null));
        }
        inventory.setItem(45, item(plugin, Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), ACTION_BACK, null));
        inventory.setItem(49, item(plugin, Material.BRICKS, "Buildings", NamedTextColor.GOLD,
                List.of("View blueprint locations."), ACTION_BUILDINGS, null));
        player.openInventory(inventory);
    }

    static boolean isMenu(Inventory inventory) {
        return holder(inventory) != null;
    }

    static MenuHolder holder(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof MenuHolder holder ? holder : null;
    }

    static String action(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "hoc_recipe_action"), PersistentDataType.STRING);
    }

    static String value(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "hoc_recipe_value"), PersistentDataType.STRING);
    }

    private static ItemStack buildingItem(JavaPlugin plugin,
                                          HallsScenario scenario,
                                          HallsBuildingType building,
                                          Map<String, HallsItemType> itemTypes) {
        HallsItemType blueprint = itemTypes.get(building.blueprint());
        Material material = blueprint == null ? Material.PAPER : blueprint.material();
        List<String> lore = new ArrayList<>();
        lore.add("Size: " + capitalize(building.size()));
        lore.add("Blueprint: " + (blueprint == null ? readable(building.blueprint()) : blueprint.name()));
        lore.add("Find: " + blueprintLocations(scenario, building.blueprint()));
        lore.add("Click for details.");
        return item(plugin, material, building.name(), NamedTextColor.GOLD, lore, ACTION_BUILDING_DETAIL, building.id());
    }

    private static ItemStack recipeItem(JavaPlugin plugin, HallsItemType type, String stationName, int level) {
        return previewItem(plugin, type, List.of("Station: " + stationName,
                "Unlocks at level " + level + ".",
                "Cost: " + formatCost(type.recipe())));
    }

    private static ItemStack previewItem(JavaPlugin plugin, HallsItemType type, List<String> extraLore) {
        ItemStack item = HallsItemFactory.create(plugin, type, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
            if (!lore.isEmpty() && extraLore != null && !extraLore.isEmpty()) {
                lore.add(Component.empty());
            }
            if (extraLore != null) {
                extraLore.stream()
                        .map(line -> Component.text(line, NamedTextColor.GRAY))
                        .forEach(lore::add);
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static List<String> recipesUnlockedAt(HallsScenario scenario, String stationId, int level) {
        List<String> current = scenario.craftingRecipes(stationId, level);
        if (level <= 1) {
            return current;
        }
        Set<String> previous = Set.copyOf(scenario.craftingRecipes(stationId, level - 1));
        return current.stream().filter(itemId -> !previous.contains(itemId)).toList();
    }

    private static List<String> upgradeCostLore(HallsBuildingType building) {
        List<String> lore = new ArrayList<>();
        for (int level = 2; level <= 3; level++) {
            lore.add("Level " + level + ": " + formatCost(building.level(level).upgradeCost()));
        }
        return lore;
    }

    private static String blueprintLocations(HallsScenario scenario, String blueprintId) {
        List<String> locations = new ArrayList<>();
        for (String rarity : List.of("normal", "rare")) {
            if (scenario.blueprintPool(rarity).contains(blueprintId)) {
                locations.add(rarity);
            }
        }
        for (Map.Entry<String, Map<String, List<String>>> levelType : scenario.levelTypeBlueprintPools().entrySet()) {
            for (String rarity : List.of("normal", "rare")) {
                if (levelType.getValue().getOrDefault(rarity, List.of()).contains(blueprintId)) {
                    locations.add(readable(levelType.getKey()) + " " + rarity);
                }
            }
        }
        return locations.isEmpty() ? "not listed in scenario pools" : String.join(", ", locations);
    }

    private static String formatCost(Map<String, Integer> cost) {
        if (cost == null || cost.isEmpty()) {
            return "free";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (entry.getValue() > 0) {
                parts.add(entry.getValue() + " " + readable(entry.getKey()));
            }
        }
        return parts.isEmpty() ? "free" : String.join(", ", parts);
    }

    private static ItemStack item(JavaPlugin plugin,
                                  Material material,
                                  String name,
                                  NamedTextColor color,
                                  List<String> lore,
                                  String action,
                                  String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (action != null) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_recipe_action"),
                        PersistentDataType.STRING, action);
            }
            if (value != null) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_recipe_value"),
                        PersistentDataType.STRING, value);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String readable(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String[] words = value.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    enum MenuType {
        INDEX,
        BUILDINGS,
        BUILDING_DETAIL,
        CRAFTING
    }

    record MenuHolder(MenuType type, String context) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
