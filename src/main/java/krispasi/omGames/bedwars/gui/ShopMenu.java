package krispasi.omGames.bedwars.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.shop.ShopCategory;
import krispasi.omGames.bedwars.shop.ShopCategoryType;
import krispasi.omGames.bedwars.shop.ShopConfig;
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

public class ShopMenu implements InventoryHolder {
    private static final int CATEGORY_ROW_SIZE = 9;
    private final GameSession session;
    private final ShopConfig config;
    private final ShopCategoryType categoryType;
    private final TeamColor team;
    private final UUID viewerId;
    private final Inventory inventory;
    private final Map<Integer, ShopItemDefinition> itemSlots = new HashMap<>();
    private final Map<Integer, ShopCategoryType> categorySlots = new HashMap<>();

    public ShopMenu(GameSession session, ShopConfig config, ShopCategoryType categoryType, Player viewer) {
        this.session = session;
        this.config = config;
        this.categoryType = categoryType;
        this.team = session.getTeam(viewer.getUniqueId());
        this.viewerId = viewer.getUniqueId();
        ShopCategory category = config.getCategory(categoryType);
        int size = category != null ? category.getSize() : 54;
        String title = category != null ? category.getTitle() : categoryType.defaultTitle();
        this.inventory = Bukkit.createInventory(this, size, Component.text(title, NamedTextColor.GOLD));
        build(category);
    }

    public void open(Player player) {
        player.openInventory(inventory);
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
        ShopCategoryType targetCategory = categorySlots.get(event.getRawSlot());
        if (targetCategory != null && targetCategory != categoryType) {
            new ShopMenu(session, config, targetCategory, player).open(player);
            return;
        }
        ShopItemDefinition item = itemSlots.get(event.getRawSlot());
        if (item != null) {
            session.handleShopPurchase(player, item);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void build(ShopCategory category) {
        buildCategoryRow();
        if (category == null) {
            return;
        }
        for (Map.Entry<Integer, String> entry : category.getEntries().entrySet()) {
            ShopItemDefinition item = config.getItem(entry.getValue());
            if (item == null) {
                continue;
            }
            int slot = entry.getKey();
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, item.createDisplayItem(team));
            itemSlots.put(slot, item);
        }
    }

    private void buildCategoryRow() {
        int slot = 0;
        for (ShopCategoryType type : ShopCategoryType.ordered()) {
            ShopCategory category = config.getCategory(type);
            if (category == null) {
                continue;
            }
            ItemStack icon = new ItemStack(category.getIcon() != null ? category.getIcon() : Material.CHEST);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(category.getTitle(), NamedTextColor.YELLOW));
            if (type == categoryType) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            icon.setItemMeta(meta);
            if (slot < CATEGORY_ROW_SIZE) {
                inventory.setItem(slot, icon);
                categorySlots.put(slot, type);
            }
            slot++;
            if (slot >= CATEGORY_ROW_SIZE) {
                break;
            }
        }
    }
}
