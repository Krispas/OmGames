package krispasi.omGames.bedwars.listener;

import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.ShopType;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.gui.MapSelectMenu;
import krispasi.omGames.bedwars.gui.ShopMenu;
import krispasi.omGames.bedwars.gui.TeamAssignMenu;
import krispasi.omGames.bedwars.gui.UpgradeShopMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.block.Action;
import org.bukkit.Material;
import org.bukkit.entity.Projectile;
import java.util.EnumSet;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;

public class BedwarsListener implements Listener {
    private final BedwarsManager bedwarsManager;
    private static final EnumSet<Material> RESOURCE_MATERIALS = EnumSet.of(
            Material.IRON_INGOT,
            Material.GOLD_INGOT,
            Material.DIAMOND,
            Material.EMERALD
    );

    public BedwarsListener(BedwarsManager bedwarsManager) {
        this.bedwarsManager = bedwarsManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        safeHandle("onInventoryClick", () -> {
            Inventory topInventory = event.getView().getTopInventory();
            if (topInventory.getHolder() instanceof MapSelectMenu menu) {
                menu.handleClick(event);
                return;
            }
            if (topInventory.getHolder() instanceof TeamAssignMenu menu) {
                menu.handleClick(event);
            }
            if (topInventory.getHolder() instanceof ShopMenu menu) {
                menu.handleClick(event);
                return;
            }
            if (topInventory.getHolder() instanceof UpgradeShopMenu) {
                event.setCancelled(true);
                return;
            }

            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                return;
            }
            if (event.getClick().isShiftClick() && isArmor(event.getCurrentItem())) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        safeHandle("onInventoryDrag", () -> {
            Inventory topInventory = event.getView().getTopInventory();
            if (topInventory.getHolder() instanceof MapSelectMenu
                    || topInventory.getHolder() instanceof TeamAssignMenu
                    || topInventory.getHolder() instanceof ShopMenu
                    || topInventory.getHolder() instanceof UpgradeShopMenu) {
                event.setCancelled(true);
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            for (int rawSlot : event.getRawSlots()) {
                if (event.getView().getSlotType(rawSlot) == InventoryType.SlotType.ARMOR) {
                    event.setCancelled(true);
                    return;
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderChestOpen(InventoryOpenEvent event) {
        safeHandle("onEnderChestOpen", () -> {
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }
            if (event.getInventory().getType() != InventoryType.ENDER_CHEST) {
                return;
            }
            String worldName = player.getWorld().getName();
            if (!bedwarsManager.isBedwarsWorld(worldName)) {
                return;
            }
            event.setCancelled(true);
            player.sendMessage(Component.text("Ender chests are disabled in BedWars.", NamedTextColor.RED));
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderChestInteract(PlayerInteractEvent event) {
        safeHandle("onEnderChestInteract", () -> {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENDER_CHEST) {
                return;
            }
            Player player = event.getPlayer();
            String worldName = player.getWorld().getName();
            if (!bedwarsManager.isBedwarsWorld(worldName)) {
                return;
            }
            event.setCancelled(true);
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isInArenaWorld(player.getWorld())) {
                player.sendMessage(Component.text("Ender chests are disabled in BedWars.", NamedTextColor.RED));
                return;
            }
            if (!session.isParticipant(player.getUniqueId())) {
                player.sendMessage(Component.text("Ender chests are disabled in BedWars.", NamedTextColor.RED));
                return;
            }
            session.openFakeEnderChest(player);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        safeHandle("onPlayerInteractEntity", () -> {
            if (!(event.getRightClicked() instanceof Villager villager)) {
                return;
            }
            if (!villager.getScoreboardTags().contains(GameSession.ITEM_SHOP_TAG)
                    && !villager.getScoreboardTags().contains(GameSession.UPGRADES_SHOP_TAG)) {
                return;
            }
            Player player = event.getPlayer();
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            event.setCancelled(true);
            ShopType type = villager.getScoreboardTags().contains(GameSession.UPGRADES_SHOP_TAG)
                    ? ShopType.UPGRADES
                    : ShopType.ITEM;
            session.openShop(player, type);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        safeHandle("onPlayerMove", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isStarting()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isParticipant(player.getUniqueId()) || !session.isFrozen(player.getUniqueId())) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) {
                return;
            }
            if (from.getBlockX() == to.getBlockX()
                    && from.getBlockY() == to.getBlockY()
                    && from.getBlockZ() == to.getBlockZ()) {
                return;
            }
            event.setTo(from);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        safeHandle("onBlockPlace", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (!session.isParticipant(player.getUniqueId())) {
                if (session.isActive()) {
                    event.setCancelled(true);
                }
                return;
            }
            if (!session.isRunning()) {
                event.setCancelled(true);
                return;
            }
            Block block = event.getBlockPlaced();
            BlockPoint point = new BlockPoint(block.getX(), block.getY(), block.getZ());
            if (session.isPlacementBlocked(point)) {
                event.setCancelled(true);
                return;
            }
            session.recordPlacedBlock(point);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        safeHandle("onBlockBreak", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (!session.isParticipant(player.getUniqueId())) {
                if (session.isActive()) {
                    event.setCancelled(true);
                }
                return;
            }
            if (!session.isRunning()) {
                event.setCancelled(true);
                return;
            }
            Block block = event.getBlock();
            BlockPoint point = new BlockPoint(block.getX(), block.getY(), block.getZ());
            TeamColor bedOwner = session.getBedOwner(point);
            if (bedOwner != null) {
                TeamColor breakerTeam = session.getTeam(player.getUniqueId());
                if (bedOwner == breakerTeam) {
                    event.setCancelled(true);
                    player.sendMessage(Component.text("You cannot break your own bed.", NamedTextColor.RED));
                    return;
                }
                event.setDropItems(false);
                session.handleBedDestroyed(bedOwner, player);
                return;
            }
            if (!session.isPlacedBlock(point)) {
                event.setCancelled(true);
                return;
            }
            dropPlacedBlock(block);
            session.removePlacedBlock(point);
            event.setCancelled(true);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        safeHandle("onEntityDamage", () -> {
            if (!(event.getEntity() instanceof Player victim)) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isInArenaWorld(victim.getWorld())) {
                return;
            }
            Player attacker = resolveAttacker(event);
            if (attacker != null
                    && session.isParticipant(attacker.getUniqueId())
                    && session.isInArenaWorld(attacker.getWorld())
                    && session.hasRespawnProtection(attacker.getUniqueId())) {
                session.removeRespawnProtection(attacker.getUniqueId());
            }
            if (attacker != null
                    && session.isParticipant(attacker.getUniqueId())
                    && session.isParticipant(victim.getUniqueId())) {
                TeamColor attackerTeam = session.getTeam(attacker.getUniqueId());
                TeamColor victimTeam = session.getTeam(victim.getUniqueId());
                if (attackerTeam != null && attackerTeam == victimTeam) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (!session.isRunning()) {
                event.setCancelled(true);
                return;
            }
            if (session.hasRespawnProtection(victim.getUniqueId())) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        safeHandle("onEntityDamageOther", () -> {
            if (event instanceof EntityDamageByEntityEvent) {
                return;
            }
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                if (!session.isRunning() || !session.isParticipant(player.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
                event.setDamage(1000.0);
                return;
            }
            if (!session.isRunning()) {
                event.setCancelled(true);
                return;
            }
            if (session.hasRespawnProtection(player.getUniqueId())) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        safeHandle("onPlayerDropItem", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isStarting()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isParticipant(player.getUniqueId()) || !session.isInArenaWorld(player.getWorld())) {
                return;
            }
            event.setCancelled(true);
        });
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        safeHandle("onPlayerDeath", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            Player player = event.getEntity();
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (session.isParticipant(player.getUniqueId())) {
                dropResourceItems(event);
            }
            session.handlePlayerDeath(player);
        });
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        safeHandle("onPlayerRespawn", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (session.isPendingRespawn(player.getUniqueId()) || session.isEliminated(player.getUniqueId())) {
                Location lobby = session.getArena().getLobbyLocation();
                if (lobby != null) {
                    event.setRespawnLocation(lobby);
                }
            }
            session.handleRespawn(player);
        });
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        safeHandle("onPlayerChangedWorld", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            session.handleWorldChange(event.getPlayer());
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        safeHandle("onPlayerJoin", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            session.handlePlayerJoin(event.getPlayer());
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        safeHandle("onPlayerQuit", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            session.handlePlayerQuit(event.getPlayer().getUniqueId());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        safeHandle("onPrepareCraft", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            for (org.bukkit.entity.HumanEntity viewer : event.getViewers()) {
                if (viewer instanceof Player player && session.isInArenaWorld(player.getWorld())) {
                    event.getInventory().setResult(null);
                    return;
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        safeHandle("onCraftItem", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            event.setCancelled(true);
        });
    }

    private boolean isArmor(ItemStack item) {
        if (item == null) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void safeHandle(String context, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE, "BedWars error in " + context, ex);
        }
    }

    private void dropPlacedBlock(Block block) {
        Material type = block.getType();
        if (type == Material.AIR) {
            return;
        }
        block.setType(Material.AIR, false);
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(type));
    }

    private void dropResourceItems(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Map<Material, Integer> totals = new HashMap<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) {
                continue;
            }
            Material type = item.getType();
            if (!RESOURCE_MATERIALS.contains(type)) {
                continue;
            }
            totals.merge(type, item.getAmount(), Integer::sum);
            contents[i] = null;
        }
        player.getInventory().setContents(contents);
        event.getDrops().clear();
        event.setKeepInventory(true);
        totals.forEach((material, amount) -> {
            if (amount > 0) {
                player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(material, amount));
            }
        });
    }
}
