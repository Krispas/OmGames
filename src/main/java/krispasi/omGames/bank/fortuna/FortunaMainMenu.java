package krispasi.omGames.bank.fortuna;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class FortunaMainMenu implements FortunaInventoryMenu {
    private static final int SIZE = 54;
    private static final int NEW_MATCH_SLOT = 10;
    private static final int GIVE_MAPS_SLOT = 12;
    private static final int REFRESH_DISPLAY_SLOT = 14;
    private static final int CLEAN_DISPLAY_SLOT = 15;
    private static final int SUMMARY_SLOT = 16;
    private static final int MATCH_START_SLOT = 18;

    private final FortunaManager manager;
    private final Inventory inventory;
    private final Map<Integer, Integer> matchSlots = new HashMap<>();

    public FortunaMainMenu(FortunaManager manager) {
        this.manager = manager;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Fortuna", NamedTextColor.GOLD));
        refresh();
    }

    public void open(Player player) {
        refresh();
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == NEW_MATCH_SLOT) {
            player.closeInventory();
            manager.beginNewMatchPrompt(player);
            return;
        }
        if (slot == GIVE_MAPS_SLOT) {
            send(player, manager.giveDisplayMaps(player));
            refresh();
            return;
        }
        if (slot == REFRESH_DISPLAY_SLOT) {
            manager.refreshDisplay();
            send(player, FortunaManager.Result.ok("Fortuna display maps refreshed."));
            refresh();
            return;
        }
        if (slot == CLEAN_DISPLAY_SLOT) {
            send(player, manager.cleanDisplay());
            refresh();
            return;
        }
        Integer matchId = matchSlots.get(slot);
        if (matchId != null) {
            new FortunaMatchMenu(manager, matchId).open(player);
        }
    }

    private void refresh() {
        inventory.clear();
        matchSlots.clear();

        inventory.setItem(NEW_MATCH_SLOT, item(
                Material.EMERALD_BLOCK,
                Component.text("+ New Match", NamedTextColor.GREEN),
                List.of(
                        Component.text("Create a scheduled betting match.", NamedTextColor.GRAY),
                        Component.text("Values are entered in chat.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(GIVE_MAPS_SLOT, item(
                Material.FILLED_MAP,
                Component.text("Display Maps", NamedTextColor.AQUA),
                buildDisplayMapLore()
        ));
        inventory.setItem(REFRESH_DISPLAY_SLOT, item(
                Material.COMPASS,
                Component.text("Refresh Display", NamedTextColor.YELLOW),
                List.of(Component.text("Re-sends all six display maps.", NamedTextColor.GRAY))
        ));
        inventory.setItem(CLEAN_DISPLAY_SLOT, item(
                Material.WHITE_CONCRETE,
                Component.text("Clean Board", NamedTextColor.WHITE),
                List.of(
                        Component.text("Shows a clean Fortuna screen now.", NamedTextColor.GRAY),
                        Component.text("Does not delete saved matches.", NamedTextColor.DARK_GRAY)
                )
        ));
        inventory.setItem(SUMMARY_SLOT, buildSummaryItem());

        List<FortunaMatch> matches = manager.getMatchesForMenu();
        int slot = MATCH_START_SLOT;
        for (FortunaMatch match : matches) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot, buildMatchItem(match));
            matchSlots.put(slot, match.getId());
            slot++;
        }
    }

    private List<Component> buildDisplayMapLore() {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("3x2 display, map ids:", NamedTextColor.GRAY));
        lore.add(Component.text(manager.getMapDisplay().getMapIds().toString(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Click to receive the six map items.", NamedTextColor.GRAY));
        return lore;
    }

    private ItemStack buildSummaryItem() {
        int upcoming = manager.countMatches(FortunaMatchStatus.UPCOMING);
        int active = manager.countMatches(FortunaMatchStatus.ACTIVE);
        int finished = manager.countMatches(FortunaMatchStatus.FINISHED);
        return item(
                Material.GOLD_INGOT,
                Component.text("Fortuna Board", NamedTextColor.GOLD),
                List.of(
                        Component.text("Upcoming: " + upcoming, NamedTextColor.GRAY),
                        Component.text("Active: " + active, NamedTextColor.GREEN),
                        Component.text("Finished: " + finished, NamedTextColor.DARK_GRAY)
                )
        );
    }

    private ItemStack buildMatchItem(FortunaMatch match) {
        Material material = switch (match.getStatus()) {
            case ACTIVE -> Material.LIME_CONCRETE;
            case FINISHED -> Material.BOOK;
            case UPCOMING -> Material.CLOCK;
        };
        NamedTextColor color = switch (match.getStatus()) {
            case ACTIVE -> NamedTextColor.GREEN;
            case FINISHED -> NamedTextColor.GOLD;
            case UPCOMING -> NamedTextColor.AQUA;
        };
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Status: " + match.getStatus().displayName(), color));
        lore.add(Component.text("Time: " + match.scheduledLabel(), NamedTextColor.GRAY));
        lore.add(Component.text("1: " + formatOdds(match.getHomeOdds())
                + " | X: " + formatOdds(match.getDrawOdds())
                + " | 2: " + formatOdds(match.getAwayOdds()), NamedTextColor.YELLOW));
        if (match.getStatus() == FortunaMatchStatus.FINISHED) {
            lore.add(Component.text("Result: " + match.resultLabel(), NamedTextColor.GREEN));
        }
        lore.add(Component.text("Click to manage.", NamedTextColor.DARK_GRAY));
        return item(material, Component.text("#" + match.getId() + " " + match.label(), color), lore);
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void send(Player player, FortunaManager.Result result) {
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private String formatOdds(double odds) {
        return String.format(Locale.US, "%.2f", odds);
    }
}
