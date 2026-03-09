package krispasi.omGames.egghunt;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EggHuntListener implements Listener {
    private static final double LOCK_EPSILON = 0.001;

    private final EggHuntManager eggHuntManager;

    public EggHuntListener(EggHuntManager eggHuntManager) {
        this.eggHuntManager = eggHuntManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!eggHuntManager.isMovementLocked(player)) {
            return;
        }
        Location target = eggHuntManager.getLockedLocation(player);
        Location to = event.getTo();
        if (target == null || to == null || samePosition(target, to)) {
            return;
        }
        Location corrected = target.clone();
        corrected.setYaw(to.getYaw());
        corrected.setPitch(to.getPitch());
        event.setTo(corrected);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        eggHuntManager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        eggHuntManager.handlePlayerQuit(event.getPlayer());
    }

    private boolean samePosition(Location first, Location second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        if (!first.getWorld().equals(second.getWorld())) {
            return false;
        }
        return Math.abs(first.getX() - second.getX()) < LOCK_EPSILON
                && Math.abs(first.getY() - second.getY()) < LOCK_EPSILON
                && Math.abs(first.getZ() - second.getZ()) < LOCK_EPSILON;
    }
}
