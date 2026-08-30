package krispasi.omGames.bank.fortuna;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

public final class FortunaMatchMenu implements FortunaInventoryMenu {
    private static final int SIZE = 45;
    private static final int SUMMARY_SLOT = 4;
    private static final int ODDS_SLOT = 20;
    private static final int START_SLOT = 22;
    private static final int FINISH_HOME_SLOT = 29;
    private static final int FINISH_DRAW_SLOT = 31;
    private static final int FINISH_AWAY_SLOT = 33;
    private static final int BACK_SLOT = 36;
    private static final int DELETE_SLOT = 44;

    private final FortunaManager manager;
    private final int matchId;
    private final Inventory inventory;

    public FortunaMatchMenu(FortunaManager manager, int matchId) {
        this.manager = manager;
        this.matchId = matchId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Fortuna - Match", NamedTextColor.GOLD));
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
        FortunaMatch match = manager.getMatch(matchId);
        if (match == null) {
            new FortunaMainMenu(manager).open(player);
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            new FortunaMainMenu(manager).open(player);
            return;
        }
        if (slot == ODDS_SLOT) {
            player.closeInventory();
            manager.beginOddsPrompt(player, matchId);
            return;
        }
        if (slot == START_SLOT) {
            send(player, manager.startMatch(matchId));
            refresh();
            return;
        }
        if (slot == FINISH_HOME_SLOT) {
            send(player, manager.finishMatch(matchId, FortunaOutcome.HOME));
            refresh();
            return;
        }
        if (slot == FINISH_DRAW_SLOT) {
            send(player, manager.finishMatch(matchId, FortunaOutcome.DRAW));
            refresh();
            return;
        }
        if (slot == FINISH_AWAY_SLOT) {
            send(player, manager.finishMatch(matchId, FortunaOutcome.AWAY));
            refresh();
            return;
        }
        if (slot == DELETE_SLOT) {
            if (!event.isShiftClick()) {
                player.sendMessage(Component.text("Shift-click Delete Match to confirm.", NamedTextColor.YELLOW));
                return;
            }
            FortunaManager.Result result = manager.deleteMatch(matchId);
            send(player, result);
            if (result.success()) {
                new FortunaMainMenu(manager).open(player);
            } else {
                refresh();
            }
        }
    }

    private void refresh() {
        inventory.clear();
        FortunaMatch match = manager.getMatch(matchId);
        if (match == null) {
            inventory.setItem(SUMMARY_SLOT, item(
                    Material.BARRIER,
                    Component.text("Match not found", NamedTextColor.RED),
                    List.of(Component.text("Use back to return.", NamedTextColor.GRAY))
            ));
            inventory.setItem(BACK_SLOT, backItem());
            return;
        }

        inventory.setItem(SUMMARY_SLOT, buildSummaryItem(match));
        inventory.setItem(ODDS_SLOT, buildOddsItem(match));
        inventory.setItem(START_SLOT, buildStartItem(match));
        inventory.setItem(FINISH_HOME_SLOT, buildFinishItem(match, FortunaOutcome.HOME, match.getHomeName(), Material.LIME_DYE));
        inventory.setItem(FINISH_DRAW_SLOT, buildFinishItem(match, FortunaOutcome.DRAW, "Draw", Material.LIGHT_BLUE_DYE));
        inventory.setItem(FINISH_AWAY_SLOT, buildFinishItem(match, FortunaOutcome.AWAY, match.getAwayName(), Material.RED_DYE));
        inventory.setItem(BACK_SLOT, backItem());
        inventory.setItem(DELETE_SLOT, deleteItem(match));
    }

    private ItemStack buildSummaryItem(FortunaMatch match) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Status: " + match.getStatus().displayName(), statusColor(match)));
        lore.add(Component.text("Scheduled: " + match.scheduledLabel(), NamedTextColor.GRAY));
        lore.add(Component.text("Home: " + match.getHomeName(), NamedTextColor.GREEN));
        lore.add(Component.text("Away: " + match.getAwayName(), NamedTextColor.RED));
        if (match.getStatus() == FortunaMatchStatus.FINISHED) {
            lore.add(Component.text("Result: " + match.resultLabel(), NamedTextColor.GOLD));
        }
        return item(Material.PAPER, Component.text("#" + match.getId() + " " + match.label(), NamedTextColor.WHITE), lore);
    }

    private ItemStack buildOddsItem(FortunaMatch match) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Current odds:", NamedTextColor.GRAY));
        lore.add(Component.text("1 " + formatOdds(match.getHomeOdds())
                + " | X " + formatOdds(match.getDrawOdds())
                + " | 2 " + formatOdds(match.getAwayOdds()), NamedTextColor.YELLOW));
        lore.add(Component.text("Home: " + trend(match, FortunaOutcome.HOME), NamedTextColor.GREEN));
        lore.add(Component.text("Draw: " + trend(match, FortunaOutcome.DRAW), NamedTextColor.AQUA));
        lore.add(Component.text("Away: " + trend(match, FortunaOutcome.AWAY), NamedTextColor.RED));
        lore.add(Component.text("Click to change odds.", NamedTextColor.DARK_GRAY));
        return item(Material.GOLD_NUGGET, Component.text("Change Odds", NamedTextColor.YELLOW), lore);
    }

    private ItemStack buildStartItem(FortunaMatch match) {
        if (match.getStatus() == FortunaMatchStatus.ACTIVE) {
            return item(Material.LIME_CONCRETE, Component.text("Active Game", NamedTextColor.GREEN),
                    List.of(Component.text("This match is already active.", NamedTextColor.GRAY)));
        }
        if (match.getStatus() == FortunaMatchStatus.FINISHED) {
            return item(Material.GRAY_CONCRETE, Component.text("Game Finished", NamedTextColor.DARK_GRAY),
                    List.of(Component.text("Finished matches cannot be activated.", NamedTextColor.GRAY)));
        }
        return item(Material.LIME_CONCRETE, Component.text("Start Game", NamedTextColor.GREEN),
                List.of(Component.text("Moves this match to active games.", NamedTextColor.GRAY)));
    }

    private ItemStack buildFinishItem(FortunaMatch match, FortunaOutcome outcome, String label, Material material) {
        boolean canFinish = match.getStatus() == FortunaMatchStatus.ACTIVE;
        NamedTextColor color = canFinish ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(canFinish ? "Ends the active game." : "Only active games can be ended.",
                canFinish ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY));
        if (match.getStatus() == FortunaMatchStatus.FINISHED && match.getResult() == outcome) {
            lore.add(Component.text("Current result.", NamedTextColor.GREEN));
        }
        return item(canFinish ? material : Material.GRAY_DYE, Component.text("Finish: " + label, color), lore);
    }

    private ItemStack backItem() {
        return item(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to Fortuna panel.", NamedTextColor.GRAY)));
    }

    private ItemStack deleteItem(FortunaMatch match) {
        return item(Material.REDSTONE_BLOCK, Component.text("Delete Match", NamedTextColor.RED),
                List.of(
                        Component.text("Shift-click to delete #" + match.getId() + ".", NamedTextColor.GRAY),
                        Component.text("This cannot be undone from the GUI.", NamedTextColor.DARK_GRAY)
                ));
    }

    private NamedTextColor statusColor(FortunaMatch match) {
        return switch (match.getStatus()) {
            case ACTIVE -> NamedTextColor.GREEN;
            case FINISHED -> NamedTextColor.GOLD;
            case UPCOMING -> NamedTextColor.AQUA;
        };
    }

    private String trend(FortunaMatch match, FortunaOutcome outcome) {
        List<Double> values = new ArrayList<>();
        for (FortunaOddsPoint point : match.getOddsHistory()) {
            values.add(switch (outcome) {
                case HOME -> point.homeOdds();
                case DRAW -> point.drawOdds();
                case AWAY -> point.awayOdds();
            });
        }
        if (values.isEmpty()) {
            values.add(switch (outcome) {
                case HOME -> match.getHomeOdds();
                case DRAW -> match.getDrawOdds();
                case AWAY -> match.getAwayOdds();
            });
        }
        return FortunaTextCharts.sparkline(values, 18);
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
