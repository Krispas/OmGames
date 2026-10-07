package krispasi.omGames.hallsofcarnage;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

final class HallsCampSpecialBuildingSupport {
    interface PlotAccess {
        int id();
        int level();
        int points();
        void setPoints(int points);
        String buildingId();
    }

    private final JavaPlugin plugin;
    private final HallsScenario scenario;
    private final Map<String, HallsItemType> itemTypes;
    private final Map<String, HallsBuildingType> buildingTypes;
    private final Function<HallsItemType, ItemStack> itemFactory;
    private final HallsCampRuntime.ScrapAccount scrapAccount;

    HallsCampSpecialBuildingSupport(JavaPlugin plugin,
                                    HallsScenario scenario,
                                    Map<String, HallsItemType> itemTypes,
                                    Map<String, HallsBuildingType> buildingTypes,
                                    Function<HallsItemType, ItemStack> itemFactory,
                                    HallsCampRuntime.ScrapAccount scrapAccount) {
        this.plugin = plugin;
        this.scenario = scenario;
        this.itemTypes = itemTypes;
        this.buildingTypes = buildingTypes;
        this.itemFactory = itemFactory;
        this.scrapAccount = scrapAccount;
    }

    void depositBlueprintPoints(Player player, PlotAccess plot) {
        HallsItemType held = heldItemType(player);
        if (held == null || !held.category().equals("blueprint")) {
            player.sendActionBar(Component.text("Hold a blueprint to distill it.", NamedTextColor.RED));
            return;
        }
        int points = held.rarity().equals("rare") ? 2 : 1;
        consumeHeld(player);
        plot.setPoints(plot.points() + points);
        playUseSound(player, plot);
        player.sendActionBar(Component.text("Blueprint distilled. Points: " + plot.points() + ".", NamedTextColor.GREEN));
    }

    void openBlueprintFabricationMenu(Player player, PlotAccess plot, int[] slots) {
        Inventory inventory = Bukkit.createInventory(new HallsCampRuntime.CampMenu(plot.id(), "blueprint_fabricate", "", 0), 54,
                Component.text("Research Table", NamedTextColor.DARK_GREEN));
        inventory.setItem(4, menuItem(Material.EXPERIENCE_BOTTLE, "Blueprint Points", NamedTextColor.AQUA,
                List.of("Available: " + plot.points()), null, null));
        List<HallsItemType> blueprints = itemTypes.values().stream()
                .filter(type -> type.category().equals("blueprint"))
                .filter(type -> scenario == null || scenario.allowedItems("blueprint").contains(type.id()))
                .filter(type -> plot.level() >= 3 || !type.rarity().equals("rare"))
                .sorted(java.util.Comparator.comparing(HallsItemType::rarity).thenComparing(HallsItemType::id))
                .toList();
        int index = 0;
        for (HallsItemType blueprint : blueprints) {
            if (index >= slots.length) {
                break;
            }
            int cost = blueprintFabricationCost(plot.level(), blueprint.rarity().equals("rare"));
            inventory.setItem(slots[index++], menuItem(blueprint.material(), blueprint.name(), NamedTextColor.AQUA,
                    List.of("Cost: " + cost + " blueprint points."), "blueprint_fabricate", blueprint.id()));
        }
        inventory.setItem(45, menuItem(Material.ARROW, "Back", NamedTextColor.GRAY, List.of("Return to building."), "station_home", null));
        player.openInventory(inventory);
    }

    void fabricateBlueprint(Player player, PlotAccess plot, String itemId) {
        HallsItemType blueprint = itemTypes.get(itemId);
        if (blueprint == null || !blueprint.category().equals("blueprint")) {
            return;
        }
        if (blueprint.rarity().equals("rare") && plot.level() < 3) {
            player.sendActionBar(Component.text("Rare fabrication requires level 3.", NamedTextColor.RED));
            return;
        }
        int cost = blueprintFabricationCost(plot.level(), blueprint.rarity().equals("rare"));
        int slot = HallsInventorySupport.firstAvailableHotbarSlot(player.getInventory());
        if (slot < 0) {
            player.sendActionBar(Component.text("Your hotbar is full.", NamedTextColor.RED));
            return;
        }
        if (plot.points() < cost) {
            player.sendActionBar(Component.text("Not enough blueprint points.", NamedTextColor.RED));
            return;
        }
        plot.setPoints(plot.points() - cost);
        player.getInventory().setItem(slot, itemFactory.apply(blueprint));
        playUseSound(player, plot);
    }

    void convertScrap(Player player, PlotAccess plot, String pair) {
        if (scrapAccount == null || pair == null || !pair.contains(":")) {
            return;
        }
        String[] parts = pair.split(":", 2);
        int ratio = alchemyRatio(plot.level());
        if (scrapAccount.amount(parts[0]) < ratio || !scrapAccount.spend(Map.of(parts[0], ratio))) {
            player.sendActionBar(Component.text("Not enough stored scrap.", NamedTextColor.RED));
            return;
        }
        scrapAccount.add(parts[1], 1);
        playUseSound(player, plot);
        player.sendActionBar(Component.text("Converted scrap.", NamedTextColor.GREEN));
    }

    void deconstructHeldItem(Player player, PlotAccess plot) {
        HallsItemType held = heldItemType(player);
        if (held == null || held.recipe().isEmpty()) {
            player.sendActionBar(Component.text("Hold a crafted Halls item with a recipe.", NamedTextColor.RED));
            return;
        }
        Map<String, Integer> scrap = scrapCost(held.recipe());
        if (scrap.isEmpty()) {
            player.sendActionBar(Component.text("That item has no scrap recipe to recover.", NamedTextColor.RED));
            return;
        }
        consumeHeld(player);
        int percent = deconstructorRefundPercent(plot.level());
        for (Map.Entry<String, Integer> entry : scrap.entrySet()) {
            double exact = entry.getValue() * percent / 100.0;
            int refund = (int) Math.floor(exact);
            if (Math.random() < exact - refund) {
                refund++;
            }
            if (refund > 0 && scrapAccount != null) {
                scrapAccount.add(entry.getKey(), refund);
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.8f, 0.8f);
        player.sendActionBar(Component.text("Item deconstructed.", NamedTextColor.GREEN));
    }

    static int blueprintFabricationCost(int level, boolean rare) {
        int base = Math.max(1, Math.min(3, level)) == 1 ? 5 : 3;
        return rare ? base * 2 : base;
    }

    static int alchemyRatio(int level) {
        return switch (Math.max(1, Math.min(3, level))) {
            case 1 -> 4;
            case 2 -> 3;
            default -> 2;
        };
    }

    static int deconstructorRefundPercent(int level) {
        return switch (Math.max(1, Math.min(3, level))) {
            case 1 -> 30;
            case 2 -> 40;
            default -> 50;
        };
    }

    private HallsItemType heldItemType(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String itemId = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "hoc_item_id"), PersistentDataType.STRING);
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

    private Map<String, Integer> scrapCost(Map<String, Integer> cost) {
        Map<String, Integer> scrap = new java.util.HashMap<>();
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (isScrapCost(entry.getKey())) {
                scrap.put(entry.getKey(), entry.getValue());
            }
        }
        return scrap;
    }

    private boolean isScrapCost(String key) {
        return key.equals("wood") || key.equals("wood_scrap")
                || key.equals("iron") || key.equals("iron_scrap")
                || key.equals("diamond") || key.equals("diamond_scrap")
                || key.equals("redstone") || key.equals("redstone_scrap");
    }

    private void playUseSound(Player player, PlotAccess plot) {
        String id = plot.buildingId();
        if (id.equals("alchemy_cauldron")) {
            player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.1f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.35f);
        }
    }

    private String itemName(String itemId) {
        HallsItemType itemType = itemTypes.get(itemId);
        return itemType == null ? itemId.replace('_', ' ') : itemType.name();
    }

    private ItemStack menuItem(Material material, String name, NamedTextColor color, List<String> loreLines, String action, String itemId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color));
            if (loreLines != null && !loreLines.isEmpty()) {
                meta.lore(loreLines.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
            }
            if (action != null) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_camp_action"), PersistentDataType.STRING, action);
            }
            if (itemId != null) {
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "hoc_camp_item"), PersistentDataType.STRING, itemId);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
