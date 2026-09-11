package krispasi.omGames.hallsofcarnage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class HallsMainMenu {
    public static final String ACTION_NEW = "new";
    public static final String ACTION_LOAD = "load";
    public static final String ACTION_BACK = "back";
    public static final String ACTION_SCENARIO = "scenario";
    public static final String ACTION_DIFFICULTY = "difficulty";
    public static final String ACTION_SAVE = "save";
    public static final String ACTION_TOGGLE_PLAYER = "toggle_player";
    public static final String ACTION_PLAY = "play";

    private HallsMainMenu() {
    }

    public static void openMain(JavaPlugin plugin,
                                Player player,
                                List<HallsShameService.ShameEntry> leaderboard,
                                int saveCount) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.MAIN, null), 27,
                Component.text("Halls of Carnage", NamedTextColor.DARK_RED));
        inventory.setItem(11, item(plugin, Material.MAP, "New Campaign", NamedTextColor.GOLD,
                List.of("Choose scenario and difficulty."), ACTION_NEW, null));
        inventory.setItem(15, item(plugin, Material.CHEST, "Load Save", NamedTextColor.AQUA,
                List.of(saveCount + " save" + (saveCount == 1 ? "" : "s") + " available."), ACTION_LOAD, null));
        inventory.setItem(22, leaderboardItem(leaderboard));
        player.openInventory(inventory);
    }

    public static void openScenarios(JavaPlugin plugin, Player player, List<HallsScenario> scenarios) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.SCENARIOS, null), 54,
                Component.text("Choose Scenario", NamedTextColor.DARK_RED));
        int slot = 10;
        for (HallsScenario scenario : scenarios) {
            if (slot >= 44) {
                break;
            }
            inventory.setItem(slot, scenarioItem(plugin, scenario));
            slot = nextContentSlot(slot);
        }
        inventory.setItem(49, item(plugin, Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), ACTION_BACK, null));
        player.openInventory(inventory);
    }

    public static void openDifficulty(JavaPlugin plugin, Player player, String scenarioId) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.DIFFICULTY, scenarioId), 27,
                Component.text("Choose Difficulty", NamedTextColor.DARK_RED));
        inventory.setItem(11, difficultyItem(plugin, "normal", "Normal", Material.IRON_SWORD, 1.0));
        inventory.setItem(13, difficultyItem(plugin, "hard", "Hard", Material.DIAMOND_SWORD, 1.5));
        inventory.setItem(15, difficultyItem(plugin, "extreme", "Extreme", Material.NETHERITE_SWORD, 2.0));
        inventory.setItem(22, item(plugin, Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), ACTION_BACK, null));
        player.openInventory(inventory);
    }

    public static void openSaves(JavaPlugin plugin, Player player, List<HallsSaveData> saves) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.SAVES, null), 54,
                Component.text("Load Save", NamedTextColor.DARK_RED));
        int slot = 10;
        for (HallsSaveData save : saves) {
            if (slot >= 44) {
                break;
            }
            inventory.setItem(slot, saveItem(plugin, save));
            slot = nextContentSlot(slot);
        }
        if (saves.isEmpty()) {
            inventory.setItem(22, item(plugin, Material.BARRIER, "No Saves", NamedTextColor.GRAY,
                    List.of("No save file includes you."), null, null));
        }
        inventory.setItem(49, item(plugin, Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), ACTION_BACK, null));
        player.openInventory(inventory);
    }

    public static void openSettings(JavaPlugin plugin,
                                    Player player,
                                    HallsScenario scenario,
                                    String difficultyName,
                                    double difficultyMultiplier,
                                    List<PlayerChoice> choices,
                                    boolean loadedSave,
                                    boolean canPlay) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.SETTINGS, null), 54,
                Component.text("Session Settings", NamedTextColor.DARK_RED));
        inventory.setItem(4, item(plugin, Material.OAK_SIGN, scenario.name(), NamedTextColor.GOLD,
                List.of("Difficulty: " + difficultyName + " x" + difficultyMultiplier,
                        "Players: " + scenario.minPlayers() + "-" + scenario.maxPlayers(),
                        loadedSave ? "Loaded save" : "New campaign"), null, null));
        int slot = 10;
        for (PlayerChoice choice : choices) {
            if (slot >= 35) {
                break;
            }
            inventory.setItem(slot, playerItem(plugin, choice));
            slot = nextContentSlot(slot);
        }
        inventory.setItem(45, item(plugin, Material.ARROW, "Back", NamedTextColor.GRAY, List.of(), ACTION_BACK, null));
        inventory.setItem(49, item(plugin, canPlay ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                "Play", canPlay ? NamedTextColor.GREEN : NamedTextColor.RED,
                canPlay ? List.of("Start this session.") : List.of("Missing required players or invalid player count."),
                ACTION_PLAY, null));
        player.openInventory(inventory);
    }

    public static boolean isMenu(Inventory inventory) {
        return holder(inventory) != null;
    }

    public static MenuHolder holder(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof MenuHolder holder ? holder : null;
    }

    public static String action(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "hoc_menu_action"), PersistentDataType.STRING);
    }

    public static String value(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "hoc_menu_value"), PersistentDataType.STRING);
    }

    private static ItemStack scenarioItem(JavaPlugin plugin, HallsScenario scenario) {
        List<String> lore = new ArrayList<>();
        lore.add("Scenario difficulty: " + scenario.difficulty());
        lore.add("Players: " + scenario.minPlayers() + "-" + scenario.maxPlayers());
        lore.add("Floors: " + scenario.floorCount());
        lore.addAll(scenario.description());
        return item(plugin, Material.MAP, scenario.name(), NamedTextColor.GOLD, lore, ACTION_SCENARIO, scenario.id());
    }

    private static ItemStack difficultyItem(JavaPlugin plugin, String id, String name, Material material, double multiplier) {
        return item(plugin, material, name, NamedTextColor.YELLOW,
                List.of("Scenario difficulty multiplier: x" + multiplier), ACTION_DIFFICULTY, id);
    }

    private static ItemStack saveItem(JavaPlugin plugin, HallsSaveData save) {
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(save.savedAt()));
        return item(plugin, Material.WRITABLE_BOOK, save.displayName(), NamedTextColor.AQUA,
                List.of("Difficulty: " + save.difficultyId() + " x" + save.difficultyMultiplier(),
                        "Players: " + save.participants().size(),
                        "Saved: " + date,
                        "Left-click to load.",
                        "Shift-right-click to delete."), ACTION_SAVE, save.file().getName());
    }

    private static ItemStack playerItem(JavaPlugin plugin, PlayerChoice choice) {
        Material material = choice.required() ? Material.NAME_TAG : (choice.selected() ? Material.LIME_DYE : Material.GRAY_DYE);
        NamedTextColor color = choice.online() ? (choice.selected() ? NamedTextColor.GREEN : NamedTextColor.GRAY) : NamedTextColor.RED;
        List<String> lore = new ArrayList<>();
        lore.add(choice.online() ? "In lobby" : "Not available in lobby");
        if (choice.required()) {
            lore.add("Required by loaded save.");
        } else {
            lore.add(choice.selected() ? "Selected" : "Not selected");
        }
        return item(plugin, material, choice.name(), color, lore,
                choice.required() ? null : ACTION_TOGGLE_PLAYER, choice.playerId().toString());
    }

    private static ItemStack leaderboardItem(List<HallsShameService.ShameEntry> leaderboard) {
        ItemStack item = new ItemStack(Material.SOUL_LANTERN);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Lowest Shame", NamedTextColor.AQUA));
        List<Component> lore = new ArrayList<>();
        if (leaderboard.isEmpty()) {
            lore.add(Component.text("No shame recorded yet.", NamedTextColor.GRAY));
        } else {
            int rank = 1;
            for (HallsShameService.ShameEntry entry : leaderboard) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.playerId());
                String name = player.getName() == null ? entry.playerId().toString().substring(0, 8) : player.getName();
                lore.add(Component.text(rank++ + ". " + name + ": " + entry.shame(), NamedTextColor.GRAY));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
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
        meta.displayName(Component.text(name, color));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (action != null) {
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "hoc_menu_action"),
                    PersistentDataType.STRING, action);
        }
        if (value != null) {
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "hoc_menu_value"),
                    PersistentDataType.STRING, value);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static int nextContentSlot(int slot) {
        int next = slot + 1;
        return next % 9 == 8 ? next + 2 : next;
    }

    public enum MenuType {
        MAIN,
        SCENARIOS,
        DIFFICULTY,
        SAVES,
        SETTINGS
    }

    public record MenuHolder(MenuType type, String context) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public record PlayerChoice(UUID playerId, String name, boolean selected, boolean required, boolean online) {
    }
}
