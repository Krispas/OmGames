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
import krispasi.omGames.bedwars.shop.ShopItemLimit;
import krispasi.omGames.bedwars.shop.ShopItemLimitScope;
import krispasi.omGames.bedwars.upgrade.TeamUpgradeType;
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
            if (purchased) {
                new ShopMenu(session, config, categoryType, player).open(player);
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
                if (QuickBuyService.isEmptyMarker(entry.getValue())) {
                    entries.remove(entry.getKey());
                } else {
                    entries.put(entry.getKey(), entry.getValue());
                }
            }
            applyQuickBuyTierOverrides(entries);
        }
        entries.entrySet().removeIf(entry -> {
            ShopItemDefinition item = config.getItem(entry.getValue());
            return item != null && !session.isRotatingItemAvailable(item);
        });
        if (categoryType == ShopCategoryType.ROTATING) {
            java.util.Set<String> rotating = session.getRotatingItemIds();
            if (!rotating.isEmpty()) {
                entries.entrySet().removeIf(entry -> !rotating.contains(entry.getValue()));
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
            if (item.getBehavior() == ShopItemBehavior.UPGRADE && item.getUpgradeType() != null) {
                display = buildUpgradeDisplay(item);
            }
            if (item.getBehavior() == ShopItemBehavior.ARMOR) {
                display = decorateArmorDisplay(display);
            }
            if (item.getBehavior() == ShopItemBehavior.SHIELD) {
                display = decorateShieldDisplay(display);
            }
            display = applyLimitLore(display, item);
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

    private ItemStack decorateShieldDisplay(ItemStack item) {
        if (item == null) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (!session.hasShieldUnlocked(viewerId)) {
            return item;
        }
        java.util.List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new java.util.ArrayList<>();
        } else {
            lore = new java.util.ArrayList<>(lore);
        }
        lore.add(Component.text("Owned", NamedTextColor.GREEN));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void applyQuickBuyTierOverrides(Map<Integer, String> entries) {
        for (Map.Entry<Integer, String> entry : entries.entrySet()) {
            ShopItemDefinition item = config.getItem(entry.getValue());
            if (item == null) {
                continue;
            }
            ShopItemBehavior behavior = item.getBehavior();
            String replacement = null;
            if (behavior == ShopItemBehavior.PICKAXE) {
                replacement = getNextToolItemId(ShopItemBehavior.PICKAXE, session.getPickaxeTier(viewerId));
            } else if (behavior == ShopItemBehavior.AXE) {
                replacement = getNextToolItemId(ShopItemBehavior.AXE, session.getAxeTier(viewerId));
            } else if (behavior == ShopItemBehavior.ARMOR) {
                replacement = getNextToolItemId(ShopItemBehavior.ARMOR, session.getArmorTier(viewerId));
            }
            if (replacement != null) {
                entry.setValue(replacement);
            }
        }
    }

    private ItemStack applyLimitLore(ItemStack item, ShopItemDefinition definition) {
        if (item == null || definition == null) {
            return item;
        }
        ShopItemLimit limit = definition.getLimit();
        if (limit == null || !limit.isValid()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        java.util.List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new java.util.ArrayList<>();
        } else {
            lore = new java.util.ArrayList<>(lore);
        }
        String scope = limit.scope() == ShopItemLimitScope.TEAM ? "team" : "player";
        lore.add(Component.text("Limit: " + limit.amount() + " per " + scope, NamedTextColor.GRAY));
        int remaining = session.getRemainingLimit(viewerId, definition);
        if (remaining >= 0) {
            lore.add(Component.text("Remaining: " + remaining,
                    remaining > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
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

    private ItemStack buildUpgradeDisplay(ShopItemDefinition item) {
        TeamUpgradeType type = item.getUpgradeType();
        if (type == null) {
            return item.createDisplayItem(team);
        }
        if (type == TeamUpgradeType.GARRY) {
            return buildGarryDisplay(type);
        }
        int tier = session.getUpgradeTier(team, type);
        int maxTier = type.maxTier();
        boolean maxed = tier >= maxTier;
        int nextTier = Math.min(tier + 1, maxTier);

        ItemStack display = new ItemStack(type.icon());
        ItemMeta meta = display.getItemMeta();
        String title = maxed ? type.tierName(tier) : type.tierName(nextTier);
        meta.displayName(Component.text(title, maxed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        for (String line : type.description()) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        lore.add(Component.text(" ", NamedTextColor.DARK_GRAY));
        if (tier > 0) {
            lore.add(Component.text("Current: " + type.tierName(tier), NamedTextColor.GRAY));
        }
        if (maxed) {
            lore.add(Component.text("Maxed", NamedTextColor.GREEN));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(Component.text("Cost: " + type.nextCost(tier) + " Diamonds", NamedTextColor.YELLOW));
            lore.add(Component.text("Click to purchase", NamedTextColor.GRAY));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack buildGarryDisplay(TeamUpgradeType type) {
        ItemStack display = new ItemStack(type.icon());
        ItemMeta meta = display.getItemMeta();
        String nextName = session.getGarryNextName();
        int cost = session.getGarryNextCost();
        boolean available = cost > 0;
        boolean maxed = cost <= 0 && session.isGarryUnlocked()
                && session.isGarryWifeAlive()
                && session.isGarryJrAlive();
        String title = nextName != null ? nextName : type.displayName();
        meta.displayName(Component.text(title, maxed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        for (String line : type.description()) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        lore.add(Component.text(" ", NamedTextColor.DARK_GRAY));
        if (session.isGarryUnlocked()) {
            String active = "Garry";
            if (session.isGarryWifeAlive() && session.isGarryJrAlive()) {
                active = "Garry, Wife, Jr.";
            } else if (session.isGarryWifeAlive()) {
                active = "Garry, Wife";
            } else if (session.isGarryJrAlive()) {
                active = "Garry, Jr.";
            }
            lore.add(Component.text("Active: " + active, NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("Active: none", NamedTextColor.GRAY));
        }
        if (maxed) {
            lore.add(Component.text("All wardens active", NamedTextColor.GREEN));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else if (available) {
            lore.add(Component.text("Cost: " + cost + " Diamonds", NamedTextColor.YELLOW));
            lore.add(Component.text("Click to purchase", NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("Not available", NamedTextColor.RED));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
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
                String override = quickBuyService.getQuickBuy(viewerId).get(slot);
                if (override != null) {
                    quickBuyService.removeQuickBuySlot(viewerId, slot);
                    quickBuyService.clearPendingSlot(viewerId);
                    player.sendMessage(Component.text("Restored slot " + slot + ".", NamedTextColor.GRAY));
                    new ShopMenu(session, config, categoryType, player).open(player);
                    return;
                }
                quickBuyService.setQuickBuySlot(viewerId, slot, QuickBuyService.EMPTY_MARKER);
                quickBuyService.clearPendingSlot(viewerId);
                player.sendMessage(Component.text("Cleared slot " + slot + ".", NamedTextColor.GRAY));
                new ShopMenu(session, config, categoryType, player).open(player);
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
