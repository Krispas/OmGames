package krispasi.omGames.bedwars.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.item.CustomItemData;
import krispasi.omGames.bedwars.model.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class LockpickTargetMenu implements InventoryHolder {
    private static final int INVENTORY_SIZE = 27;
    private static final String LOCKPICK_ID = "lockpick";

    private final GameSession session;
    private final UUID viewerId;
    private final Block chestBlock;
    private final TeamColor ownerTeam;
    private final Inventory inventory;
    private final Map<Integer, UUID> targetSlots = new HashMap<>();

    public LockpickTargetMenu(GameSession session, Player viewer, Block chestBlock, TeamColor ownerTeam) {
        this.session = session;
        this.viewerId = viewer.getUniqueId();
        this.chestBlock = chestBlock;
        this.ownerTeam = ownerTeam;
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE,
                Component.text("Lockpick Target", NamedTextColor.GOLD));
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
        if (!session.isRunning() || !session.isParticipant(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        UUID targetPlayerId = targetSlots.get(event.getRawSlot());
        if (targetPlayerId == null) {
            return;
        }
        ItemStack consumed = consumeOneLockpick(player);
        if (consumed == null) {
            player.sendMessage(Component.text("You need a Lockpick to start this.", NamedTextColor.RED));
            return;
        }
        boolean started = session.beginEnderChestLockpick(player, chestBlock, targetPlayerId);
        if (!started) {
            restoreLockpick(player, consumed);
            return;
        }
        player.closeInventory();
    }

    private void build() {
        inventory.clear();
        targetSlots.clear();

        List<UUID> members = new ArrayList<>();
        for (Map.Entry<UUID, TeamColor> entry : session.getAssignments().entrySet()) {
            if (entry.getValue() == ownerTeam) {
                members.add(entry.getKey());
            }
        }
        members.sort(Comparator.comparing(this::resolvePlayerName, String.CASE_INSENSITIVE_ORDER));
        if (members.isEmpty()) {
            inventory.setItem(13, createEmptyStateItem());
            return;
        }

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < members.size() && i < slots.length; i++) {
            UUID playerId = members.get(i);
            int slot = slots[i];
            inventory.setItem(slot, createTargetItem(playerId));
            targetSlots.put(slot, playerId);
        }
    }

    private ItemStack createTargetItem(UUID playerId) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof SkullMeta meta)) {
            return item;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        meta.setOwningPlayer(offlinePlayer);
        meta.displayName(Component.text(resolvePlayerName(playerId), NamedTextColor.YELLOW));
        meta.lore(List.of(
                Component.text("Team: ", NamedTextColor.GRAY).append(ownerTeam.displayComponent()),
                Component.text("Unlock time: 20s", NamedTextColor.GRAY),
                Component.text("Access duration: 60s", NamedTextColor.GRAY),
                Component.text("Click to start lockpicking", NamedTextColor.DARK_GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptyStateItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("No targets", NamedTextColor.RED));
        meta.lore(List.of(Component.text("No players are assigned to this team right now.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private String resolvePlayerName(UUID playerId) {
        if (playerId == null) {
            return "Unknown";
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        return offline.getName() != null ? offline.getName() : playerId.toString();
    }

    private ItemStack consumeOneLockpick(Player player) {
        if (player == null) {
            return null;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isLockpick(stack)) {
                continue;
            }
            return takeOne(inventory, slot, stack);
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (!isLockpick(offHand)) {
            return null;
        }
        ItemStack removed = offHand.clone();
        removed.setAmount(1);
        if (offHand.getAmount() <= 1) {
            inventory.setItemInOffHand(null);
        } else {
            offHand.setAmount(offHand.getAmount() - 1);
            inventory.setItemInOffHand(offHand);
        }
        return removed;
    }

    private ItemStack takeOne(PlayerInventory inventory, int slot, ItemStack stack) {
        ItemStack removed = stack.clone();
        removed.setAmount(1);
        if (stack.getAmount() <= 1) {
            inventory.setItem(slot, null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            inventory.setItem(slot, stack);
        }
        return removed;
    }

    private boolean isLockpick(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        String id = CustomItemData.getId(stack);
        return id != null && id.equalsIgnoreCase(LOCKPICK_ID);
    }

    private void restoreLockpick(Player player, ItemStack item) {
        if (player == null || item == null || item.getType() == Material.AIR) {
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }
}
