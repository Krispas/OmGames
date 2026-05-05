package krispasi.omGames.bedwars.gui;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import krispasi.omGames.OmVeinsAPI;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.timecapsule.TimeCapsuleSerialization;
import krispasi.omGames.bedwars.timecapsule.TimeCapsuleService;
import krispasi.omGames.shared.Skin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class LobbyMenuVillagerMenu implements InventoryHolder {
    private static final int SIZE = 27;
    private static final int PLAY_SLOT = 10;
    private static final int QUICK_BUY_SLOT = 12;
    private static final int TIME_CAPSULE_SLOT = 14;
    private static final int SKINS_SLOT = 16;
    private static final int TIME_CAPSULE_SIZE = 27;

    private final BedwarsManager bedwarsManager;
    private final Inventory inventory;

    public LobbyMenuVillagerMenu(BedwarsManager bedwarsManager) {
        this.bedwarsManager = bedwarsManager;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("BedWars Lobby", NamedTextColor.DARK_PURPLE));
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
        int slot = event.getRawSlot();
        if (slot == PLAY_SLOT) {
            bedwarsManager.openMapSelect(player);
            return;
        }
        if (slot == QUICK_BUY_SLOT) {
            bedwarsManager.openQuickBuyEditor(player);
            return;
        }
        if (slot == TIME_CAPSULE_SLOT) {
            openLatestTimeCapsule(player);
            return;
        }
        if (slot == SKINS_SLOT) {
            openSkins(player);
        }
    }

    private void build() {
        inventory.setItem(PLAY_SLOT, createItem(Material.NETHER_STAR, "Play BedWars", "Open game setup menu"));
        inventory.setItem(QUICK_BUY_SLOT, createItem(Material.CHEST, "Quick Buy", "Open quick buy editor"));
        inventory.setItem(TIME_CAPSULE_SLOT, createItem(Material.ENDER_CHEST, "Time Capsule", "View your latest capsule"));
        inventory.setItem(SKINS_SLOT, createItem(Material.LEATHER_HELMET, "Skins", "Open BedWars skins"));
    }

    private ItemStack createItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.DARK_PURPLE));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private void openLatestTimeCapsule(Player player) {
        List<TimeCapsuleService.VisibleTimeCapsule> capsules = bedwarsManager.getTimeCapsuleService()
                .getCurrentCapsulesByCreator(player.getUniqueId()).stream()
                .filter(capsule -> capsule.queueType().key().equalsIgnoreCase("normal"))
                .toList();
        if (capsules.isEmpty()) {
            player.sendMessage(Component.text("You do not have a current Time Capsule.", NamedTextColor.RED));
            return;
        }
        TimeCapsuleService.VisibleTimeCapsule latest = capsules.getFirst();
        ItemStack[] contents;
        try {
            contents = TimeCapsuleSerialization.deserialize(latest.contentsBase64(), TIME_CAPSULE_SIZE);
        } catch (IOException | IllegalArgumentException ex) {
            player.sendMessage(Component.text("Your Time Capsule could not be opened.", NamedTextColor.RED));
            return;
        }
        new TimeCapsuleViewMenu(contents).open(player);
    }

    private void openSkins(Player player) {
        if (!OmVeinsAPI.isInitialized()) {
            player.sendMessage(Component.text("OmVeins skins are not available right now.", NamedTextColor.RED));
            return;
        }
        GameSession session = bedwarsManager.getActiveSession();
        if (session != null && session.isActive()
                && session.isParticipant(player.getUniqueId())
                && !session.isLobby()) {
            player.sendMessage(Component.text("You can only change skins in the lobby.", NamedTextColor.RED));
            return;
        }
        Map<String, Skin> skins = OmVeinsAPI.getPlayerSkins(player);
        if (skins == null || skins.isEmpty()) {
            player.sendMessage(Component.text("You do not have any skins to choose from.", NamedTextColor.GRAY));
            return;
        }
        SkinTypeMenu menu = new SkinTypeMenu(bedwarsManager, player, skins);
        if (!menu.hasSkins()) {
            player.sendMessage(Component.text("No BedWars-compatible skins found.", NamedTextColor.GRAY));
            return;
        }
        menu.open(player);
    }
}
