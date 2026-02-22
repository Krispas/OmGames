package krispasi.omGames.bedwars.gui;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.shop.QuickBuyService;
import krispasi.omGames.bedwars.shop.ShopCategory;
import krispasi.omGames.bedwars.shop.ShopCategoryType;
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
 * Inventory UI for item purchases and quick-buy editing.
 * <p>Builds category tabs from {@link krispasi.omGames.bedwars.shop.ShopConfig} and
 * dispatches purchases to {@link krispasi.omGames.bedwars.game.GameSession}.</p>
 * <p>Supports Quick Buy customization via
 * {@link krispasi.omGames.bedwars.shop.QuickBuyService}.</p>
 */
public class ShopMenu implements InventoryHolder {
    private static final int CATEGORY_ROW_SIZE = 9;
    private final GameSession session;
    private final ShopConfig config;
    private final ShopCategoryType categoryType;
    private final TeamColor team;
    private final UUID viewerId;
    private final QuickBuyService quickBuyService;
    private final Inventory inventory;
    private final int customizeSlot;
    private final Map<Integer, ShopItemDefinition> itemSlots = new HashMap<>();
    private final Map<Integer, ShopCategoryType> categorySlots = new HashMap<>();

    public ShopMenu(GameSession session, ShopConfig config, ShopCategoryType categoryType, Player viewer) {
        this.session = session;
        this.config = config;
        this.categoryType = categoryType;
        this.team = session.getTeam(viewer.getUniqueId());
        this.viewerId = viewer.getUniqueId();
        this.quickBuyService = session.getBedwarsManager().getQuickBuyService();
        ShopCategory category = config.getCategory(categoryType);
        int size = category != null ? category.getSize() : 54;
        String title = category != null ? category.getTitle() : categoryType.defaultTitle();
        this.inventory = Bukkit.createInventory(this, size, Component.text(title, NamedTextColor.GOLD));
        this.customizeSlot = Math.max(0, size - 1);
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
        if (event.getRawSlot() == customizeSlot) {
            toggleCustomize(player);
            return;
        }
        ShopCategoryType targetCategory = categorySlots.get(event.getRawSlot());
        if (targetCategory != null && targetCategory != categoryType) {
            new ShopMenu(session, config, targetCategory, player).open(player);
            return;
        }
        if (isCustomizing()) {
            handleCustomizeClick(player, event);
            return;
        }
        ShopItemDefinition item = itemSlots.get(event.getRawSlot());
        if (item != null) {
            boolean purchased = session.handleShopPurchase(player, item);
            if (purchased && categoryType == ShopCategoryType.TOOLS) {
                ShopItemBehavior behavior = item.getBehavior();
                if (behavior == ShopItemBehavior.PICKAXE || behavior == ShopItemBehavior.AXE) {
                    new ShopMenu(session, config, categoryType, player).open(player);
                }
            }
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
        Map<Integer, String> entries = new LinkedHashMap<>(category.getEntries());
        if (categoryType == ShopCategoryType.TOOLS) {
            applyToolOverrides(entries);
        }
        if (categoryType == ShopCategoryType.QUICK_BUY && quickBuyService != null) {
            for (Map.Entry<Integer, String> entry : quickBuyService.getQuickBuy(viewerId).entrySet()) {
                entries.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<Integer, String> entry : entries.entrySet()) {
            ShopItemDefinition item = config.getItem(entry.getValue());
            if (item == null) {
                continue;
            }
            int slot = entry.getKey();
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            ItemStack display = item.createDisplayItem(team);
            if (item.getBehavior() == ShopItemBehavior.ARMOR) {
                display = decorateArmorDisplay(display);
            }
            inventory.setItem(slot, display);
            itemSlots.put(slot, item);
        }
        if (categoryType == ShopCategoryType.QUICK_BUY && isCustomizing()) {
            applySelectionMarker();
        }
    }

    private void applyToolOverrides(Map<Integer, String> entries) {
        int pickaxeSlot = -1;
        int axeSlot = -1;
        Map<Integer, String> toRemove = new HashMap<>();
        for (Map.Entry<Integer, String> entry : entries.entrySet()) {
            ShopItemDefinition item = config.getItem(entry.getValue());
            if (item == null) {
                continue;
            }
            if (item.getBehavior() == ShopItemBehavior.PICKAXE) {
                pickaxeSlot = pickaxeSlot == -1 ? entry.getKey() : Math.min(pickaxeSlot, entry.getKey());
                toRemove.put(entry.getKey(), entry.getValue());
            } else if (item.getBehavior() == ShopItemBehavior.AXE) {
                axeSlot = axeSlot == -1 ? entry.getKey() : Math.min(axeSlot, entry.getKey());
                toRemove.put(entry.getKey(), entry.getValue());
            }
        }
        for (Integer slot : toRemove.keySet()) {
            entries.remove(slot);
        }
        String pickaxeId = getNextToolItemId(ShopItemBehavior.PICKAXE, session.getPickaxeTier(viewerId));
        if (pickaxeId != null && pickaxeSlot >= 0) {
            entries.put(pickaxeSlot, pickaxeId);
        }
        String axeId = getNextToolItemId(ShopItemBehavior.AXE, session.getAxeTier(viewerId));
        if (axeId != null && axeSlot >= 0) {
            entries.put(axeSlot, axeId);
        }
    }

    private String getNextToolItemId(ShopItemBehavior behavior, int currentTier) {
        Map<Integer, ShopItemDefinition> tiered = config.getTieredItems(behavior);
        if (tiered == null || tiered.isEmpty()) {
            return null;
        }
        int maxTier = 0;
        for (Integer tier : tiered.keySet()) {
            if (tier != null) {
                maxTier = Math.max(maxTier, tier);
            }
        }
        if (maxTier <= 0) {
            return null;
        }
        int targetTier = Math.min(currentTier + 1, maxTier);
        ShopItemDefinition definition = tiered.get(targetTier);
        if (definition == null) {
            definition = tiered.get(maxTier);
        }
        return definition != null ? definition.getId() : null;
    }

    private ItemStack decorateArmorDisplay(ItemStack item) {
        if (item == null) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        java.util.List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new java.util.ArrayList<>();
        } else {
            lore = new java.util.ArrayList<>(lore);
        }
        String owned = getCurrentArmorName();
        if (owned != null) {
            lore.add(Component.text("Owned: " + owned, NamedTextColor.GRAY));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String getCurrentArmorName() {
        int tier = session.getArmorTier(viewerId);
        if (tier <= 0) {
            return "Leather Armor";
        }
        ShopItemDefinition current = config.getTieredItem(ShopItemBehavior.ARMOR, tier).orElse(null);
        if (current == null) {
            return "Tier " + tier;
        }
        String displayName = current.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return formatMaterialName(current.getMaterial());
    }

    private String formatMaterialName(Material material) {
        if (material == null) {
            return "Unknown";
        }
        String name = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        String[] parts = name.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private void buildCategoryRow() {
        int slot = 0;
        for (ShopCategoryType type : ShopCategoryType.ordered()) {
            ShopCategory category = config.getCategory(type);
            if (category == null) {
                continue;
            }
            if (customizeSlot < CATEGORY_ROW_SIZE && slot == customizeSlot) {
                slot++;
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
        inventory.setItem(customizeSlot, buildCustomizeButton());
    }

    private ItemStack buildCustomizeButton() {
        boolean active = isCustomizing();
        Material material = active ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Customize Quick Buy", NamedTextColor.YELLOW));
        meta.lore(java.util.List.of(
                Component.text(active ? "Status: ON" : "Status: OFF", active ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                Component.text("Click to toggle", NamedTextColor.DARK_GRAY)
        ));
        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private boolean isCustomizing() {
        return quickBuyService != null && quickBuyService.isEditing(viewerId);
    }

    private void toggleCustomize(Player player) {
        if (quickBuyService == null) {
            return;
        }
        boolean active = quickBuyService.toggleEditing(viewerId);
        String message = active ? "Quick Buy customization enabled." : "Quick Buy customization saved.";
        player.sendMessage(Component.text(message, active ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        new ShopMenu(session, config, categoryType, player).open(player);
    }

    private void handleCustomizeClick(Player player, InventoryClickEvent event) {
        if (quickBuyService == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < CATEGORY_ROW_SIZE) {
            return;
        }
        Integer pending = quickBuyService.getPendingSlot(viewerId);
        if (categoryType == ShopCategoryType.QUICK_BUY) {
            if (event.getClick().isRightClick()) {
                ShopItemDefinition item = itemSlots.get(slot);
                if (item != null) {
                    quickBuyService.removeQuickBuySlot(viewerId, slot);
                    quickBuyService.clearPendingSlot(viewerId);
                    player.sendMessage(Component.text("Cleared slot " + slot + ".", NamedTextColor.GRAY));
                    new ShopMenu(session, config, categoryType, player).open(player);
                }
                return;
            }
            if (pending == null) {
                quickBuyService.setPendingSlot(viewerId, slot);
                player.sendMessage(Component.text("Selected slot " + slot + ".", NamedTextColor.YELLOW));
                new ShopMenu(session, config, categoryType, player).open(player);
                return;
            }
            ShopItemDefinition item = itemSlots.get(slot);
            if (item != null) {
                quickBuyService.setQuickBuySlot(viewerId, pending, item.getId());
                quickBuyService.clearPendingSlot(viewerId);
                player.sendMessage(Component.text("Assigned " + item.getId() + " to slot " + pending + ".", NamedTextColor.GREEN));
                new ShopMenu(session, config, ShopCategoryType.QUICK_BUY, player).open(player);
                return;
            }
            quickBuyService.setPendingSlot(viewerId, slot);
            player.sendMessage(Component.text("Selected slot " + slot + ".", NamedTextColor.YELLOW));
            new ShopMenu(session, config, categoryType, player).open(player);
            return;
        }
        if (pending == null) {
            player.sendMessage(Component.text("Select a Quick Buy slot first.", NamedTextColor.RED));
            return;
        }
        ShopItemDefinition item = itemSlots.get(slot);
        if (item == null) {
            return;
        }
        quickBuyService.setQuickBuySlot(viewerId, pending, item.getId());
        quickBuyService.clearPendingSlot(viewerId);
        player.sendMessage(Component.text("Assigned " + item.getId() + " to slot " + pending + ".", NamedTextColor.GREEN));
        new ShopMenu(session, config, ShopCategoryType.QUICK_BUY, player).open(player);
    }

    private void applySelectionMarker() {
        Integer pending = quickBuyService.getPendingSlot(viewerId);
        if (pending == null || pending < 0 || pending >= inventory.getSize() || pending < CATEGORY_ROW_SIZE) {
            return;
        }
        ItemStack current = inventory.getItem(pending);
        if (current == null || current.getType() == Material.AIR) {
            ItemStack marker = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
            ItemMeta meta = marker.getItemMeta();
            meta.displayName(Component.text("Selected Slot", NamedTextColor.YELLOW));
            meta.lore(java.util.List.of(Component.text("Pick an item to assign", NamedTextColor.GRAY)));
            marker.setItemMeta(meta);
            inventory.setItem(pending, marker);
            return;
        }
        ItemMeta meta = current.getItemMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        current.setItemMeta(meta);
        inventory.setItem(pending, current);
    }
}
