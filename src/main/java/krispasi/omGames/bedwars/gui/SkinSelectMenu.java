package krispasi.omGames.bedwars.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.skin.BedwarsSkinOption;
import krispasi.omGames.bedwars.skin.BedwarsSkinSelection;
import krispasi.omGames.shared.SKIN_TYPE;
import krispasi.omGames.shared.Skin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;

public class SkinSelectMenu implements InventoryHolder {
    private static final int SIZE = 54;
    private static final int BACK_SLOT = 45;
    private static final int CLEAR_SLOT = 53;

    private final BedwarsManager bedwarsManager;
    private final UUID viewerId;
    private final SKIN_TYPE type;
    private final List<BedwarsSkinOption> options;
    private final Map<String, Skin> skins;
    private final Inventory inventory;
    private final Map<Integer, BedwarsSkinOption> optionSlots = new HashMap<>();

    public SkinSelectMenu(BedwarsManager bedwarsManager,
                          Player player,
                          SKIN_TYPE type,
                          List<BedwarsSkinOption> options,
                          Map<String, Skin> skins) {
        this.bedwarsManager = bedwarsManager;
        this.viewerId = player.getUniqueId();
        this.type = type;
        this.options = options != null ? options : List.of();
        this.skins = skins != null ? skins : Map.of();
        String title = formatTypeName(type) + " Skins";
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text(title, NamedTextColor.GOLD));
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
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.getUniqueId().equals(viewerId)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == BACK_SLOT) {
            new SkinTypeMenu(bedwarsManager, player, skins).open(player);
            return;
        }
        if (slot == CLEAR_SLOT) {
            clearSelection();
            player.sendMessage(Component.text("Skin cleared.", NamedTextColor.GRAY));
            new SkinSelectMenu(bedwarsManager, player, type, options, skins).open(player);
            return;
        }
        BedwarsSkinOption option = optionSlots.get(slot);
        if (option == null) {
            return;
        }
        applySelection(option);
        player.sendMessage(Component.text("Selected skin: ", NamedTextColor.GREEN)
                .append(option.skin().name()));
        new SkinSelectMenu(bedwarsManager, player, type, options, skins).open(player);
    }

    private void build() {
        int slot = 0;
        for (BedwarsSkinOption option : options) {
            if (slot >= BACK_SLOT) {
                break;
            }
            ItemStack item = buildOptionItem(option);
            inventory.setItem(slot, item);
            optionSlots.put(slot, option);
            slot++;
        }
        inventory.setItem(BACK_SLOT, buildBackButton());
        inventory.setItem(CLEAR_SLOT, buildClearButton());
    }

    private ItemStack buildOptionItem(BedwarsSkinOption option) {
        Material material = resolveTypeIcon(type);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(option.skin().name());
        List<Component> lore = new ArrayList<>(option.skin().getLore());
        BedwarsSkinSelection current = bedwarsManager.getSkinSelection(viewerId, type);
        if (current != null && current.modelId() != null
                && current.modelId().equalsIgnoreCase(option.modelId())) {
            lore.add(Component.text("Selected", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("Click to select", NamedTextColor.DARK_GRAY));
        }
        if (type == SKIN_TYPE.HAT) {
            lore.add(Component.text("Replaces helmet skin", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        NamespacedKey modelKey = NamespacedKey.fromString(normalizeModelId(option.modelId()));
        if (modelKey != null) {
            meta.setItemModel(modelKey);
        }
        if (option.hasEquipmentModel()) {
            EquippableComponent equippable = resolveEquippableComponent(item, meta);
            if (equippable != null) {
                NamespacedKey equipKey = NamespacedKey.fromString(normalizeModelId(option.equipmentModelId()));
                if (equipKey != null) {
                    equippable.setModel(equipKey);
                }
                meta.setEquippable(equippable);
            }
        }
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Back", NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildClearButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Clear " + formatTypeName(type) + " Skin", NamedTextColor.RED));
        meta.lore(List.of(Component.text("Use default model", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private void applySelection(BedwarsSkinOption option) {
        BedwarsSkinSelection selection = new BedwarsSkinSelection(
                normalizeModelId(option.modelId()),
                normalizeModelId(option.equipmentModelId())
        );
        bedwarsManager.setSkinSelection(viewerId, type, selection);
        if (type == SKIN_TYPE.HAT) {
            bedwarsManager.clearSkinSelection(viewerId, SKIN_TYPE.HELMET);
        } else if (type == SKIN_TYPE.HELMET) {
            bedwarsManager.clearSkinSelection(viewerId, SKIN_TYPE.HAT);
        }
    }

    private void clearSelection() {
        bedwarsManager.clearSkinSelection(viewerId, type);
        if (type == SKIN_TYPE.HAT) {
            bedwarsManager.clearSkinSelection(viewerId, SKIN_TYPE.HELMET);
        }
    }

    private Material resolveTypeIcon(SKIN_TYPE type) {
        return switch (type) {
            case SWORD -> Material.DIAMOND_SWORD;
            case SPEAR -> Material.NETHERITE_SPEAR;
            case AXE -> Material.DIAMOND_AXE;
            case HELMET -> Material.DIAMOND_HELMET;
            case CHESTPLATE -> Material.DIAMOND_CHESTPLATE;
            case LEGGINGS -> Material.DIAMOND_LEGGINGS;
            case BOOTS -> Material.DIAMOND_BOOTS;
            case ELYTRA -> Material.ELYTRA;
            case BOW -> Material.BOW;
            case PUNCHING_STICK -> Material.STICK;
            case HAT -> Material.LEATHER_HELMET;
            default -> Material.CHEST;
        };
    }

    private String formatTypeName(SKIN_TYPE type) {
        return switch (type) {
            case SWORD -> "Sword";
            case SPEAR -> "Spear";
            case AXE -> "Axe";
            case HELMET -> "Helmet";
            case CHESTPLATE -> "Chestplate";
            case LEGGINGS -> "Leggings";
            case BOOTS -> "Boots";
            case ELYTRA -> "Elytra";
            case BOW -> "Bow";
            case PUNCHING_STICK -> "Punching Stick";
            case HAT -> "Hat";
            default -> type.name();
        };
    }

    private EquippableComponent resolveEquippableComponent(ItemStack item, ItemMeta meta) {
        if (meta != null) {
            EquippableComponent equippable = meta.getEquippable();
            if (equippable != null) {
                return equippable;
            }
        }
        ItemMeta defaults = item != null ? Bukkit.getItemFactory().getItemMeta(item.getType()) : null;
        return defaults != null ? defaults.getEquippable() : null;
    }

    private String normalizeModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return modelId;
        }
        String trimmed = modelId.trim();
        if (trimmed.contains(":")) {
            return trimmed;
        }
        return "om:" + trimmed;
    }
}
