package krispasi.omGames.hallsofcarnage;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

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
        if (!manager.isHallsWorld(event.getPlayer().getWorld())
                || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.SMITHING_TABLE) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!HallsMainMenu.isMenu(event.getInventory())) {
            return;
        }
        event.setCancelled(true);
    }
}
