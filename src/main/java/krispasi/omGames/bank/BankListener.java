package krispasi.omGames.bank;

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

public final class BankListener implements Listener {
    private final BankManager bankManager;
    private final JavaPlugin plugin;

    public BankListener(BankManager bankManager, JavaPlugin plugin) {
        this.bankManager = bankManager;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        safeHandle("onInventoryClick", () -> {
            Inventory topInventory = event.getView().getTopInventory();
            if (!(topInventory.getHolder() instanceof BankInventoryMenu menu)) {
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
            if (topInventory.getHolder() instanceof BankInventoryMenu) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPromptChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!bankManager.hasPrompt(player)) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> bankManager.handlePromptInput(player, message));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bankManager.cancelPrompt(event.getPlayer());
    }

    private void safeHandle(String context, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Bank error in " + context, ex);
        }
    }
}
