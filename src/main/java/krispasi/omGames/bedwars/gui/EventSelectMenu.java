package krispasi.omGames.bedwars.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.event.BedwarsMatchEventConfig;
import krispasi.omGames.bedwars.event.BedwarsMatchEventType;
import krispasi.omGames.bedwars.game.GameSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Inventory UI for selecting the prestart match-event mode before the game starts.
 */
public class EventSelectMenu implements InventoryHolder {
    private static final int INVENTORY_SIZE = 54;
    private static final int[] EVENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int PREV_SLOT = 45;
    private static final int FORCE_RANDOM_SLOT = 46;
    private static final int AUTO_SLOT = 47;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final GameSession session;
    private final UUID viewerId;
    private final Inventory inventory;
    private final Map<Integer, BedwarsMatchEventType> eventSlots = new HashMap<>();
    private int page;

    public EventSelectMenu(GameSession session, Player viewer) {
        this.session = session;
        this.viewerId = viewer.getUniqueId();
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE,
                Component.text("Event Mode", NamedTextColor.GOLD));
        build();
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void handleClick(InventoryClickEvent event) {
        if (event.getRawSlot() >= inventory.getSize()) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.getUniqueId().equals(viewerId)) {
            return;
        }
        if (!player.hasPermission("omgames.bw.start")) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            new TeamAssignMenu(session.getBedwarsManager(), session).open(player);
            return;
        }
        if (slot == PREV_SLOT) {
            if (page > 0) {
                page--;
                build();
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            int lastPage = Math.max(0, (int) Math.ceil(BedwarsMatchEventType.values().length / (double) EVENT_SLOTS.length) - 1);
            if (page < lastPage) {
                page++;
                build();
            }
            return;
        }
        if (slot == FORCE_RANDOM_SLOT) {
            BedwarsMatchEventConfig config = session.getBedwarsManager().getMatchEventConfig();
            if (config == null || !config.hasEligibleEvents()) {
                return;
            }
            session.setForcedRandomMatchEvent(true);
            new TeamAssignMenu(session.getBedwarsManager(), session).open(player);
            return;
        }
        if (slot == AUTO_SLOT) {
            session.clearForcedMatchEventSelection();
            new TeamAssignMenu(session.getBedwarsManager(), session).open(player);
            return;
        }
        BedwarsMatchEventType type = eventSlots.get(slot);
        if (type == null) {
            return;
        }
        session.setForcedMatchEvent(type);
        new TeamAssignMenu(session.getBedwarsManager(), session).open(player);
    }

    private void build() {
        inventory.clear();
        eventSlots.clear();

        BedwarsMatchEventConfig config = session.getBedwarsManager().getMatchEventConfig();
        BedwarsMatchEventType forced = session.getForcedMatchEvent();
        boolean forcedRandom = session.isForcedRandomMatchEvent();
        BedwarsMatchEventType[] events = BedwarsMatchEventType.values();
        int totalPages = Math.max(1, (int) Math.ceil(events.length / (double) EVENT_SLOTS.length));
        if (page >= totalPages) {
            page = totalPages - 1;
        }
        int startIndex = page * EVENT_SLOTS.length;
        for (int i = 0; i < EVENT_SLOTS.length; i++) {
            int eventIndex = startIndex + i;
            if (eventIndex >= events.length) {
                break;
            }
            BedwarsMatchEventType type = events[eventIndex];
            int slot = EVENT_SLOTS[i];
            inventory.setItem(slot, buildEventItem(type, config, forced == type));
            eventSlots.put(slot, type);
        }
        inventory.setItem(PREV_SLOT, buildPageItem(Material.ARROW, "Previous", page > 0));
        inventory.setItem(NEXT_SLOT, buildPageItem(Material.ARROW, "Next", page < totalPages - 1));
        inventory.setItem(FORCE_RANDOM_SLOT, buildForceRandomItem(config, forcedRandom));
        inventory.setItem(AUTO_SLOT, buildAutoItem(config, forced == null && !forcedRandom));
        inventory.setItem(BACK_SLOT, buildBackItem());
    }

    private ItemStack buildPageItem(Material material, String label, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? material : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, enabled ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY));
        meta.lore(List.of(Component.text(enabled ? "Change page" : "No more pages", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildEventItem(BedwarsMatchEventType type, BedwarsMatchEventConfig config, boolean selected) {
        ItemStack item = new ItemStack(iconFor(type));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.displayName(), selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(type.subtitle(), NamedTextColor.GRAY));
        lore.add(Component.text("Weight: " + weightFor(config, type), NamedTextColor.DARK_GRAY));
        lore.add(Component.text(selected ? "Forced for this match" : "Click to force this event",
                selected ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildForceRandomItem(BedwarsMatchEventConfig config, boolean selected) {
        ItemStack item = new ItemStack(selected ? Material.NETHER_STAR : Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Force Random", selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        if (config == null || !config.hasEligibleEvents()) {
            lore.add(Component.text("No weighted events can roll right now", NamedTextColor.RED));
        } else {
            lore.add(Component.text("Chance: 100%", NamedTextColor.GRAY));
            lore.add(Component.text("Uses the weighted event list", NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.text(selected ? "Guaranteed random event for this match" : "Click to force a guaranteed random roll",
                selected ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildAutoItem(BedwarsMatchEventConfig config, boolean selected) {
        ItemStack item = new ItemStack(selected ? Material.COMPASS : Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Auto Random", selected ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        if (config == null || !config.hasEligibleEvents()) {
            lore.add(Component.text("No weighted events can roll right now", NamedTextColor.RED));
        } else {
            lore.add(Component.text("Chance: " + formatChance(config.chancePercent()), NamedTextColor.GRAY));
            lore.add(Component.text("Uses the weighted event list", NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.text(selected ? "Currently selected" : "Click to clear forced selection",
                selected ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildBackItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Back", NamedTextColor.YELLOW));
        meta.lore(List.of(Component.text("Return to team setup", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private Material iconFor(BedwarsMatchEventType type) {
        return switch (type) {
            case SPEEDRUN -> Material.SUGAR;
            case SPEEDRUN_ANY -> Material.CLOCK;
            case THE_RAPTURE -> Material.WITHER_ROSE;
            case BENEVOLENT_UPGRADES -> Material.ENCHANTED_BOOK;
            case LONG_ARMS -> Material.STICK;
            case MOON_BIG -> Material.RABBIT_FOOT;
            case BLOOD_MOON -> Material.REDSTONE;
            case FALLOUT -> Material.OMINOUS_BOTTLE;
            case CHAOS -> Material.FIREWORK_STAR;
            case IN_THIS_ECONOMY -> Material.IRON_INGOT;
            case APRIL_FOOLS -> Material.NOTE_BLOCK;
        };
    }

    private int weightFor(BedwarsMatchEventConfig config, BedwarsMatchEventType type) {
        if (config == null || type == null) {
            return 0;
        }
        return Math.max(0, config.weight(type));
    }

    private String formatChance(double chancePercent) {
        if (Math.abs(chancePercent - Math.rint(chancePercent)) < 0.0001) {
            return (int) Math.rint(chancePercent) + "%";
        }
        return String.format(java.util.Locale.US, "%.1f%%", chancePercent);
    }
}
