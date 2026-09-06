package krispasi.omGames.bank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class BankAtmDepositMenu implements BankInventoryMenu {
    private static final int SIZE = 27;
    private static final int ALL_SLOT = 10;
    private static final int BACK_SLOT = 18;
    private static final int OPTION_START_SLOT = 11;

    private final BankManager manager;
    private final String cardId;
    private final Inventory inventory;
    private final Map<Integer, String> creditSlots = new HashMap<>();

    public BankAtmDepositMenu(BankManager manager, String cardId) {
        this.manager = manager;
        this.cardId = cardId;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("ATM Deposit", NamedTextColor.GOLD));
        refresh(null);
    }

    public void open(Player player) {
        refresh(player);
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
            manager.openAtm(player, cardId);
            return;
        }
        BankManager.Result result = null;
        if (slot == ALL_SLOT) {
            result = manager.depositHeldCredits(player, cardId);
        } else {
            String creditId = creditSlots.get(slot);
            if (creditId != null) {
                result = manager.depositCreditType(player, cardId, creditId);
            }
        }
        if (result != null) {
            player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (result.success()) {
                manager.openAtm(player, cardId);
            } else {
                refresh(player);
            }
        }
    }

    private void refresh(Player player) {
        inventory.clear();
        creditSlots.clear();

        List<BankManager.CreditDepositOption> options = player == null ? List.of() : manager.getDepositOptions(player);
        long total = 0L;
        for (BankManager.CreditDepositOption option : options) {
            total += option.total();
        }
        inventory.setItem(ALL_SLOT, BankMenuItems.item(
                Material.HOPPER,
                Component.text("Deposit All", NamedTextColor.GREEN),
                List.of(
                        Component.text("Total: " + total, NamedTextColor.GRAY),
                        Component.text("Deposits every recognized credit item.", NamedTextColor.DARK_GRAY)
                )
        ));

        int slot = OPTION_START_SLOT;
        for (BankManager.CreditDepositOption option : options) {
            if (slot >= 17) {
                break;
            }
            inventory.setItem(slot, BankMenuItems.item(
                    Material.GOLD_INGOT,
                    Component.text(option.creditId(), NamedTextColor.GOLD),
                    List.of(
                            Component.text("Value each: " + option.value(), NamedTextColor.GRAY),
                            Component.text("Amount: " + option.amount(), NamedTextColor.GRAY),
                            Component.text("Deposit total: " + option.total(), NamedTextColor.GREEN),
                            Component.text("Click to deposit only this type.", NamedTextColor.DARK_GRAY)
                    )
            ));
            creditSlots.put(slot, option.creditId());
            slot++;
        }

        if (options.isEmpty()) {
            inventory.setItem(13, BankMenuItems.item(
                    Material.BARRIER,
                    Component.text("No Credits Found", NamedTextColor.RED),
                    List.of(Component.text("Your inventory has no recognized OmVeins credits.", NamedTextColor.GRAY))
            ));
        }
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to ATM.", NamedTextColor.GRAY))
        ));
    }
}
