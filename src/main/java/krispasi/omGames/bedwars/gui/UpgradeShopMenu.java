package krispasi.omGames.bedwars.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.upgrade.TeamUpgradeType;
import krispasi.omGames.bedwars.upgrade.TrapType;
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
 * Inventory UI for team upgrades and traps.
 * <p>Shows current tiers from {@link krispasi.omGames.bedwars.upgrade.TeamUpgradeState} and
 * purchases {@link krispasi.omGames.bedwars.upgrade.TeamUpgradeType} or
 * {@link krispasi.omGames.bedwars.upgrade.TrapType}.</p>
 * <p>Displays queued traps and costs based on team state.</p>
 */
public class UpgradeShopMenu implements InventoryHolder {
    private static final int INVENTORY_SIZE = 54;
    private static final int PROTECTION_SLOT = 10;
    private static final int SHARPNESS_SLOT = 11;
    private static final int HASTE_SLOT = 12;
    private static final int FORGE_SLOT = 13;
    private static final int HEAL_POOL_SLOT = 14;
    private static final int FEATHER_FALLING_SLOT = 15;
    private static final int THORNS_SLOT = 16;
    private static final int FIRE_ASPECT_SLOT = 19;
    private static final int TRAP_SLOT_START = 28;

    private final GameSession session;
    private final TeamColor team;
    private final UUID viewerId;
    private final Inventory inventory;
    private final Map<Integer, TeamUpgradeType> upgradeSlots = new HashMap<>();
    private final Map<Integer, TrapType> trapSlots = new HashMap<>();

    public UpgradeShopMenu(GameSession session, Player viewer) {
        this.session = session;
        this.team = session.getTeam(viewer.getUniqueId());
        this.viewerId = viewer.getUniqueId();
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE,
                Component.text("Upgrades Shop", NamedTextColor.GOLD));
        build();
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
        TeamUpgradeType upgrade = upgradeSlots.get(event.getRawSlot());
        if (upgrade != null) {
            if (session.handleUpgradePurchase(player, upgrade)) {
                build();
            }
            return;
        }
        TrapType trap = trapSlots.get(event.getRawSlot());
        if (trap != null && session.handleTrapPurchase(player, trap)) {
            build();
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void build() {
        inventory.clear();
        upgradeSlots.clear();
        trapSlots.clear();

        setUpgrade(PROTECTION_SLOT, TeamUpgradeType.PROTECTION);
        setUpgrade(SHARPNESS_SLOT, TeamUpgradeType.SHARPNESS);
        setUpgrade(HASTE_SLOT, TeamUpgradeType.HASTE);
        setUpgrade(FORGE_SLOT, TeamUpgradeType.FORGE);
        setUpgrade(HEAL_POOL_SLOT, TeamUpgradeType.HEAL_POOL);
        setUpgrade(FEATHER_FALLING_SLOT, TeamUpgradeType.FEATHER_FALLING);
        setUpgrade(THORNS_SLOT, TeamUpgradeType.THORNS);
        setUpgrade(FIRE_ASPECT_SLOT, TeamUpgradeType.FIRE_ASPECT);

        int slot = TRAP_SLOT_START;
        for (TrapType trap : TrapType.values()) {
            setTrap(slot, trap);
            slot++;
        }
    }

    private void setUpgrade(int slot, TeamUpgradeType type) {
        if (!session.isRotatingUpgradeAvailable(type)) {
            inventory.setItem(slot, new ItemStack(Material.AIR));
            return;
        }
        inventory.setItem(slot, buildUpgradeItem(type));
        upgradeSlots.put(slot, type);
    }

    private void setTrap(int slot, TrapType trap) {
        inventory.setItem(slot, buildTrapItem(trap));
        trapSlots.put(slot, trap);
    }

    private ItemStack buildUpgradeItem(TeamUpgradeType type) {
        if (!session.isRotatingUpgradeAvailable(type)) {
            ItemStack item = new ItemStack(type.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(type.displayName(), NamedTextColor.RED));
            List<Component> lore = new ArrayList<>();
            for (String line : type.description()) {
                lore.add(Component.text(line, NamedTextColor.GRAY));
            }
            lore.add(Component.text(" ", NamedTextColor.DARK_GRAY));
            lore.add(Component.text("Not in rotation", NamedTextColor.RED));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }
        int tier = session.getUpgradeTier(team, type);
        int maxTier = type.maxTier();
        boolean maxed = tier >= maxTier;
        int nextTier = Math.min(tier + 1, maxTier);

        ItemStack item = new ItemStack(type.icon());
        ItemMeta meta = item.getItemMeta();

        String title = maxed ? type.tierName(tier) : type.tierName(nextTier);
        meta.displayName(Component.text(title, maxed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
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
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildTrapItem(TrapType trap) {
        int cost = session.getTrapCost(team);
        boolean full = cost < 0;
        ItemStack item = new ItemStack(trap.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(trap.displayName(), NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        for (String line : trap.description()) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        lore.add(Component.text(" ", NamedTextColor.DARK_GRAY));
        if (full) {
            lore.add(Component.text("No trap slots available", NamedTextColor.RED));
        } else {
            lore.add(Component.text("Cost: " + cost + " Diamonds", NamedTextColor.YELLOW));
            lore.add(Component.text("Triggers in your base", NamedTextColor.GRAY));
        }
        lore.add(Component.text("Traps: " + session.getActiveTraps(team).size() + "/3", NamedTextColor.GRAY));
        String queue = formatTrapQueue(session.getActiveTraps(team));
        if (!queue.isEmpty()) {
            lore.add(Component.text("Queued: " + queue, NamedTextColor.GRAY));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private String formatTrapQueue(List<TrapType> traps) {
        if (traps.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (TrapType trap : traps) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(trap.displayName());
        }
        return builder.toString();
    }
}
