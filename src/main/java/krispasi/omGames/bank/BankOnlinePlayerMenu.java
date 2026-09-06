package krispasi.omGames.bank;

import java.util.ArrayList;
import java.util.Comparator;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class BankOnlinePlayerMenu implements BankInventoryMenu {
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int PLAYER_START_SLOT = 9;

    private final BankManager manager;
    private final Inventory inventory;
    private final Map<Integer, UUID> playerSlots = new HashMap<>();

    public BankOnlinePlayerMenu(BankManager manager) {
        this.manager = manager;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Bank Online Players", NamedTextColor.GOLD));
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
        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            manager.openAdminMenu(admin);
            return;
        }
        UUID targetId = playerSlots.get(slot);
        if (targetId == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            admin.sendMessage(Component.text("That player is no longer online.", NamedTextColor.RED));
            refresh();
            return;
        }
        BankManager.Result result = manager.createAccount(target);
        admin.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (result.success()) {
            manager.openAccountMenu(admin, manager.playerAccountId(target.getUniqueId()));
        } else {
            refresh();
        }
    }

    private void refresh() {
        inventory.clear();
        playerSlots.clear();
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int slot = PLAYER_START_SLOT;
        for (Player player : players) {
            if (slot >= SIZE) {
                break;
            }
            inventory.setItem(slot, playerItem(player));
            playerSlots.put(slot, player.getUniqueId());
            slot++;
        }
        inventory.setItem(BACK_SLOT, BankMenuItems.item(
                Material.ARROW,
                Component.text("Back", NamedTextColor.YELLOW),
                List.of(Component.text("Return to Bank Admin.", NamedTextColor.GRAY))
        ));
    }

    private ItemStack playerItem(Player player) {
        BankAccount account = manager.getPlayerAccount(player.getUniqueId());
        NamedTextColor statusColor = account == null ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text(player.getName(), NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text(account == null ? "No bank account yet." : "Account already exists.", statusColor),
                Component.text("Click to create/open account.", NamedTextColor.DARK_GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }
}
