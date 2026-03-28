package krispasi.omGames.bedwars.gui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.skin.BedwarsSkinOption;
import krispasi.omGames.bedwars.skin.BedwarsSkinSelection;
import krispasi.omGames.shared.SKIN_TYPE;
import krispasi.omGames.shared.Skin;
import krispasi.omGames.shared.SkinEquipment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SkinTypeMenu implements InventoryHolder {
    private static final int SIZE = 27;
    private static final int[] TYPE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };
    private static final List<SKIN_TYPE> ORDERED_TYPES = List.of(
            SKIN_TYPE.SWORD,
            SKIN_TYPE.SPEAR,
            SKIN_TYPE.AXE,
            SKIN_TYPE.LEGGINGS,
            SKIN_TYPE.BOOTS,
            SKIN_TYPE.ELYTRA,
            SKIN_TYPE.BOW,
            SKIN_TYPE.PUNCHING_STICK,
            SKIN_TYPE.HAT
    );

    private final BedwarsManager bedwarsManager;
    private final UUID viewerId;
    private final Map<String, Skin> skins;
    private final Map<SKIN_TYPE, List<BedwarsSkinOption>> skinsByType;
    private final Map<Integer, SKIN_TYPE> slotTypes = new HashMap<>();
    private final Inventory inventory;

    public SkinTypeMenu(BedwarsManager bedwarsManager, Player player, Map<String, Skin> skins) {
        this.bedwarsManager = bedwarsManager;
        this.viewerId = player.getUniqueId();
        this.skins = skins != null ? skins : Map.of();
        this.skinsByType = buildSkinMap(this.skins);
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Skin Preferences", NamedTextColor.GOLD));
        build();
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public boolean hasSkins() {
        return skinsByType.values().stream().anyMatch(list -> list != null && !list.isEmpty());
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
        SKIN_TYPE type = slotTypes.get(event.getRawSlot());
        if (type == null) {
            return;
        }
        List<BedwarsSkinOption> options = skinsByType.get(type);
        if (options == null || options.isEmpty()) {
            player.sendMessage(Component.text("No skins available for that category.", NamedTextColor.RED));
            return;
        }
        new SkinSelectMenu(bedwarsManager, player, type, options, skins).open(player);
    }

    private void build() {
        int slotIndex = 0;
        for (SKIN_TYPE type : ORDERED_TYPES) {
            List<BedwarsSkinOption> options = skinsByType.get(type);
            if (options == null || options.isEmpty()) {
                continue;
            }
            if (slotIndex >= TYPE_SLOTS.length) {
                break;
            }
            int slot = TYPE_SLOTS[slotIndex++];
            ItemStack icon = buildTypeIcon(type, options);
            inventory.setItem(slot, icon);
            slotTypes.put(slot, type);
        }
    }

    private ItemStack buildTypeIcon(SKIN_TYPE type, List<BedwarsSkinOption> options) {
        ItemStack icon = new ItemStack(resolveTypeIcon(type));
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(formatTypeName(type), NamedTextColor.YELLOW));
        List<Component> lore = new ArrayList<>();
        BedwarsSkinSelection selection = bedwarsManager.getSkinSelection(viewerId, type);
        String selectedName = resolveSelectedName(selection, options);
        if (selectedName != null) {
            lore.add(Component.text("Selected: " + selectedName, NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("Selected: Default", NamedTextColor.GRAY));
        }
        lore.add(Component.text("Click to view skins", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        icon.setItemMeta(meta);
        return icon;
    }

    private String resolveSelectedName(BedwarsSkinSelection selection, List<BedwarsSkinOption> options) {
        if (selection == null || selection.modelId() == null || selection.modelId().isBlank()) {
            return null;
        }
        if (options == null) {
            return null;
        }
        for (BedwarsSkinOption option : options) {
            if (selection.modelId().equalsIgnoreCase(option.modelId())) {
                String plain = PlainTextComponentSerializer.plainText().serialize(option.skin().name());
                return plain != null && !plain.isBlank() ? plain : option.modelId();
            }
        }
        return selection.modelId();
    }

    private Map<SKIN_TYPE, List<BedwarsSkinOption>> buildSkinMap(Map<String, Skin> source) {
        Map<SKIN_TYPE, List<BedwarsSkinOption>> result = new EnumMap<>(SKIN_TYPE.class);
        for (Map.Entry<String, Skin> entry : source.entrySet()) {
            String modelId = entry.getKey();
            if (modelId == null || modelId.isBlank()) {
                continue;
            }
            Skin skin = entry.getValue();
            if (skin == null || skin.type() == null || !ORDERED_TYPES.contains(skin.type())) {
                continue;
            }
            String equipmentModel = skin instanceof SkinEquipment equipment ? equipment.equipment() : null;
            result.computeIfAbsent(skin.type(), ignored -> new ArrayList<>())
                    .add(new BedwarsSkinOption(modelId, skin, equipmentModel));
        }
        return result;
    }

    private Material resolveTypeIcon(SKIN_TYPE type) {
        return switch (type) {
            case SWORD -> Material.DIAMOND_SWORD;
            case SPEAR -> Material.NETHERITE_SPEAR;
            case AXE -> Material.DIAMOND_AXE;
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
            case SWORD -> "Swords";
            case SPEAR -> "Spears";
            case AXE -> "Axes";
            case LEGGINGS -> "Leggings";
            case BOOTS -> "Boots";
            case ELYTRA -> "Elytra";
            case BOW -> "Bows";
            case PUNCHING_STICK -> "Punching Sticks";
            case HAT -> "Hats";
            default -> type.name();
        };
    }
}
