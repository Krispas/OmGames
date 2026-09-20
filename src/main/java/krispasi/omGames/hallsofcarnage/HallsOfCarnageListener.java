package krispasi.omGames.hallsofcarnage;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
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
            player.setFoodLevel(manager.forcedFoodLevel(player));
            player.setSaturation(manager.forcedFoodLevel(player) < 20 ? 0.0f : 20.0f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        if (!manager.isHallsWorld(event.getPlayer().getWorld())) {
            return;
        }
        if (manager.blocksEating(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("The sculk suppresses your hunger.", NamedTextColor.DARK_AQUA));
            return;
        }
        manager.handleItemConsume(event.getPlayer(), event.getItem());
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
            return;
        }
        manager.handlePlayerDamage(event);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (manager.isSessionMonster(event.getEntity())) {
            manager.handleSessionMonsterDeath(event.getEntity(), event.getEntity().getKiller());
            event.getDrops().clear();
            event.setDroppedExp(0);
        } else if (manager.handleTrapPufferfishDeath(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SLIME_SPLIT) {
            manager.registerSplitMonster(event.getEntity());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (manager.isSessionMonster(event.getEntity())) {
            if (event.getEntity().getType() == EntityType.ZOMBIE
                    && event.getTransformedEntity().getType() == EntityType.DROWNED
                    && manager.registerTransformedMonster(event.getEntity(), event.getTransformedEntity())) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        if (manager.isResearchCrateCarrier(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Set the research crate down first.", NamedTextColor.LIGHT_PURPLE));
            return;
        }
        if (manager.handleSessionEntityAttack(event.getPlayer(), event.getAttacked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            manager.handleSessionFriendlyFire(event);
            manager.handleSessionMonsterAttack(event);
            Player shooter = manager.sessionProjectileShooter(event.getDamager());
            if (shooter != null) {
                manager.handleSessionWeaponHit(shooter, event.getEntity(), event);
            }
            return;
        }
        if (manager.handleSessionFriendlyFire(event)) {
            return;
        }
        if (manager.handleSessionEntityAttack(player, event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        manager.handleSessionWeaponHit(player, event.getEntity(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            manager.handleSessionRangedShot(player, event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        manager.handleSessionItemDamage(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.HAND
                && manager.handleResearchCrateInteract(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() == EquipmentSlot.HAND
                && manager.handleBlueprintDistilleryInteract(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() == EquipmentSlot.HAND
                && manager.handleLibraryVentInteract(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() == EquipmentSlot.HAND
                && manager.handleTrapInteract(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() == EquipmentSlot.HAND
                && manager.isResearchCrateCarrier(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Set the research crate down first.", NamedTextColor.LIGHT_PURPLE));
            return;
        }
        if (event.getHand() == EquipmentSlot.HAND
                && manager.handlePhysicsDropPickup(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() == EquipmentSlot.HAND
                && manager.handleCampInteract(event.getPlayer(), event.getRightClicked())) {
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

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!manager.isHallsWorld(event.getPlayer().getWorld())) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.HOPPER
                && manager.handleResearchCrateDeposit(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && manager.handleResearchCrateBlockInteract(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (manager.isResearchCrateCarrier(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Set the research crate down first.", NamedTextColor.LIGHT_PURPLE));
            return;
        }
        manager.ensureSessionRangedAmmo(event.getPlayer(), event.getItem());
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && manager.handleUtilityUse(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
            return;
        }
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && manager.handleFoodUse(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() == Material.SMITHING_TABLE) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock().getType() == Material.IRON_BARS
                && manager.handleVentGateInteract(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock().getType() == Material.CHEST
                && manager.handleElevatorChestInteract(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
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
        if (manager.handleMainMenuClick(event)) {
            return;
        }
        if (manager.handleRecipeBookClick(event)) {
            return;
        }
        if (manager.handleCampInventoryClick(event)) {
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        manager.handleCampInventoryClose(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && manager.isResearchCrateCarrier(player)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Set the research crate down first.", NamedTextColor.LIGHT_PURPLE));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (manager.isResearchCrateCarrier(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Set the research crate down first.", NamedTextColor.LIGHT_PURPLE));
            return;
        }
        if (manager.isLockedInventorySlotItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        manager.handlePlayerDroppedItem(event.getPlayer(), event.getItemDrop());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && manager.handleResearchCrateSneak(event.getPlayer())) {
            event.getPlayer().sendActionBar(Component.text("Research crate dropped.", NamedTextColor.LIGHT_PURPLE));
        }
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
