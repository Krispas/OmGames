package krispasi.omGames.random;

import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class RandomListener implements Listener {
    private final RandomGifManager gifManager;
    private final JavaPlugin plugin;

    public RandomListener(RandomGifManager gifManager, JavaPlugin plugin) {
        this.gifManager = gifManager;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        safeHandle("onInventoryClick", () -> {
            Inventory topInventory = event.getView().getTopInventory();
            if (!(topInventory.getHolder() instanceof RandomInventoryMenu menu)) {
                return;
            }
            if (event.getRawSlot() >= topInventory.getSize()) {
                event.setCancelled(true);
                return;
            }
            menu.handleClick(event);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        safeHandle("onInventoryDrag", () -> {
            Inventory topInventory = event.getView().getTopInventory();
            if (topInventory.getHolder() instanceof RandomInventoryMenu) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPromptChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!gifManager.hasPrompt(player)) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> gifManager.handlePromptInput(player, message));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        gifManager.cancelPrompt(event.getPlayer());
    }

    private void safeHandle(String context, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Random GIF error in " + context, ex);
        }
    }
}
