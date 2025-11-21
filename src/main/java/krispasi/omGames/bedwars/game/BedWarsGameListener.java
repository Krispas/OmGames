package krispasi.omGames.bedwars.game;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class BedWarsGameListener implements Listener {
    private final BedWarsMatchManager matchManager;

    public BedWarsGameListener(BedWarsMatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!matchManager.isRunning()) return;
        Player player = event.getPlayer();
        BedWarsMatchManager.BedWarsMatch match = matchManager.getActiveMatch();
        if (match == null || !match.isBedWarsWorld(player.getWorld())) return;

        Block block = event.getBlock();
        if (!match.canBreak(player, block)) {
            player.sendMessage(ChatColor.RED + "You can't break the core map blocks.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!matchManager.isRunning()) return;
        Player player = event.getPlayer();
        BedWarsMatchManager.BedWarsMatch match = matchManager.getActiveMatch();
        if (match == null || !match.isBedWarsWorld(player.getWorld())) return;

        if (!match.canPlace(event.getBlockPlaced().getLocation())) {
            player.sendMessage(ChatColor.RED + "No building near generators.");
            event.setCancelled(true);
            return;
        }
        match.recordPlacement(event.getBlockPlaced().getLocation());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!matchManager.isRunning()) return;
        Player player = event.getEntity();
        BedWarsMatchManager.BedWarsMatch match = matchManager.getActiveMatch();
        if (match == null || !match.isBedWarsWorld(player.getWorld())) return;

        event.setKeepInventory(true);
        event.getDrops().clear();
        match.handleDeath(player);
    }

    @EventHandler
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!matchManager.isRunning()) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        Entity victim = event.getEntity();
        BedWarsMatchManager.BedWarsMatch match = matchManager.getActiveMatch();
        if (match == null || !match.isBedWarsWorld(attacker.getWorld())) return;

        if (match.isFriendlyFire(attacker, victim)) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "Friendly fire is off.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!matchManager.isRunning()) return;
        BedWarsMatchManager.BedWarsMatch match = matchManager.getActiveMatch();
        if (match == null || !match.isBedWarsWorld(event.getPlayer().getWorld())) return;
        match.handleQuit(event.getPlayer().getUniqueId());
    }
}
