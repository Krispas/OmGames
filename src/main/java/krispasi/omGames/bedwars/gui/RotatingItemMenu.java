package krispasi.omGames.bedwars.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopItemBehavior;
import krispasi.omGames.bedwars.shop.ShopItemDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Inventory UI for choosing which rotating items are active for a match.
 * <p>Lets admins toggle up to two rotating items, used when the session is in manual mode.</p>
 */
public class RotatingItemMenu implements InventoryHolder {
    private static final int INVENTORY_SIZE = 54;
    private static final int BACK_SLOT = 53;

    private final GameSession session;
    private final UUID viewerId;
    private final Inventory inventory;
    private final Map<Integer, String> itemSlots = new HashMap<>();

    public RotatingItemMenu(GameSession session, Player viewer) {
        this.session = session;
        this.viewerId = viewer.getUniqueId();
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE,
                Component.text("Rotating Items", NamedTextColor.GOLD));
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
        String itemId = itemSlots.get(slot);
        if (itemId == null) {
            return;
        }
        session.setRotatingMode(GameSession.RotatingSelectionMode.MANUAL);
        boolean changed = session.toggleManualRotatingItem(itemId);
        if (!changed) {
            player.sendMessage(Component.text("You can only select 2 rotating items.", NamedTextColor.RED));
        }
        build();
    }

    private void build() {
        inventory.clear();
        itemSlots.clear();

        ShopConfig config = session.getBedwarsManager().getShopConfig();
        List<String> candidates = session.getRotatingCandidateIds();
        int slot = 0;
        for (String id : candidates) {
            if (slot >= BACK_SLOT) {
                break;
            }
            ShopItemDefinition definition = config != null ? config.getItem(id) : null;
            if (definition == null) {
                continue;
            }
            ItemStack item = definition.createDisplayItem(null);
            item = decorateSelection(item, definition, session.getManualRotatingItemIds().contains(id));
            inventory.setItem(slot, item);
            itemSlots.put(slot, id);
            slot++;
        }
        inventory.setItem(BACK_SLOT, buildBackItem());
    }

    private ItemStack decorateSelection(ItemStack item, ShopItemDefinition definition, boolean selected) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<Component> lore = meta != null && meta.lore() != null
                ? new ArrayList<>(meta.lore())
                : new ArrayList<>();
        if (definition.getBehavior() == ShopItemBehavior.UPGRADE) {
            lore.add(Component.text("Upgrade", NamedTextColor.DARK_GRAY));
        }
        if (selected) {
            lore.add(Component.text("Selected", NamedTextColor.GREEN));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(Component.text("Click to select", NamedTextColor.GRAY));
        }
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
}
