package krispasi.omGames.bank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankCardsMenu implements BankInventoryMenu {
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int CREATE_CARD_SLOT = 10;
    private static final int CARD_START_SLOT = 18;

    private final BankManager manager;
    private final UUID accountId;
    private final Inventory inventory;
    private final Map<Integer, String> cardSlots = new HashMap<>();

    public BankCardsMenu(BankManager manager, UUID accountId) {
        this.manager = manager;
        this.accountId = accountId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Bank Cards", NamedTextColor.GOLD));
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
        if (slot == BACK_SLOT) {
            manager.openAccountMenu(player, accountId);
            return;
        }
        if (slot == CREATE_CARD_SLOT) {
            send(player, manager.createCard(player, accountId));
            refresh();
            return;
        }
        String cardId = cardSlots.get(slot);
        if (cardId != null) {
            BankCard card = manager.getCard(cardId);
            if (card != null) {
                send(player, manager.setCardFrozen(card.cardId(), !card.frozen()));
                refresh();
            }
        }
    }

    private void refresh() {
        inventory.clear();
        cardSlots.clear();
        inventory.setItem(CREATE_CARD_SLOT, BankMenuItems.item(
                Material.PAPER,
                Component.text("+ Credit Card", NamedTextColor.GREEN),
                List.of(Component.text("Creates a new card item.", NamedTextColor.GRAY))
        ));
        int slot = CARD_START_SLOT;
        for (BankCard card : manager.listCards(accountId)) {
            if (slot >= SIZE) {
                break;
            }
            Material material = card.frozen() ? Material.REDSTONE_BLOCK : Material.LIME_CONCRETE;
            NamedTextColor color = card.frozen() ? NamedTextColor.RED : NamedTextColor.GREEN;
            inventory.setItem(slot, BankMenuItems.item(
                    material,
                    Component.text("Card " + shortId(card.cardId()), color),
                    List.of(
                            Component.text("Owner: " + card.ownerName(), NamedTextColor.GRAY),
                            Component.text("Status: " + (card.frozen() ? "Frozen" : "Active"), color),
                            Component.text("Click to toggle freeze.", NamedTextColor.DARK_GRAY)
                    )
            ));
            cardSlots.put(slot, card.cardId());
            slot++;
        }
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to account.", NamedTextColor.GRAY))
        ));
    }

    private void send(Player player, BankManager.Result result) {
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private String shortId(String id) {
        return id == null || id.length() <= 10 ? String.valueOf(id) : id.substring(0, 10);
    }
}
