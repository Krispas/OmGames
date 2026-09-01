package krispasi.omGames.hallsofcarnage;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class HallsOfCarnageListener implements Listener {
    private final HallsOfCarnageManager manager;

    public HallsOfCarnageListener(HallsOfCarnageManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        manager.prepareLobbyPlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (manager.isHallsWorld(event.getPlayer().getWorld())) {
            manager.pushOutOfSessionProps(event.getPlayer());
            manager.handlePlayerMove(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (manager.isHallsWorld(event.getPlayer().getWorld()) && manager.getLobbySpawn() != null) {
            event.setRespawnLocation(manager.getLobbySpawn());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && manager.isHallsWorld(player.getWorld())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onNaturalRegen(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player
                && manager.isHallsWorld(player.getWorld())
                && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (manager.isMenuVillager(entity)
                || (manager.isSessionEntity(entity) && !(event instanceof EntityDamageByEntityEvent))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        if (manager.handleSessionEntityAttack(event.getPlayer(), event.getAttacked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (manager.handleSessionEntityAttack(player, event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND
                && manager.handlePhysicsDropPickup(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        if (!manager.isMenuVillager(event.getRightClicked())) {
            if (manager.isSessionEntity(event.getRightClicked())) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        manager.openMainMenu(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!manager.isHallsWorld(event.getPlayer().getWorld()) || event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() == Material.SMITHING_TABLE) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock().getType() == Material.STONE_BUTTON
                && manager.handleElevatorButton(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock().getType() == Material.HOPPER
                && manager.handleScrapDeposit(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (HallsMainMenu.isMenu(event.getInventory())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)
                || !manager.isActiveSessionParticipant(player)
                || !manager.isHallsWorld(player.getWorld())) {
            return;
        }
        if (manager.isLockedInventorySlotItem(event.getCurrentItem()) || manager.isLockedInventorySlotItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()) {
            if (!isAllowedShiftClick(event)) {
                event.setCancelled(true);
                sendInventoryLimitActionBar(player);
            }
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory && isBlockedPlayerInventorySlot(event.getSlot())) {
            event.setCancelled(true);
            sendInventoryLimitActionBar(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (manager.isLockedInventorySlotItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        manager.handlePlayerDroppedItem(event.getPlayer(), event.getItemDrop());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !manager.isActiveSessionParticipant(player)
                || !manager.isHallsWorld(player.getWorld())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize && isBlockedPlayerInventorySlot(event.getView().convertSlot(rawSlot))) {
                event.setCancelled(true);
                sendInventoryLimitActionBar(player);
                return;
            }
        }
    }

    private void sendInventoryLimitActionBar(Player player) {
        player.sendActionBar(Component.text("Use hotbar slots only in Halls.", NamedTextColor.RED));
    }

    private boolean isBlockedPlayerInventorySlot(int slot) {
        return slot >= 9 && slot <= 35;
    }

    private boolean isAllowedShiftClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
            return true;
        }
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return event.getSlot() >= 0 && event.getSlot() <= 8;
        }
        return true;
    }
}
