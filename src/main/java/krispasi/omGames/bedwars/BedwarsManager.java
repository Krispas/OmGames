package krispasi.omGames.bedwars;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.config.BedwarsConfigLoader;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopConfigLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.FireworkEffect;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import krispasi.omGames.bedwars.gui.MapSelectMenu;
import krispasi.omGames.bedwars.gui.TeamAssignMenu;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.TeamColor;

public class BedwarsManager {
    private final JavaPlugin plugin;
    private Map<String, Arena> arenas = Map.of();
    private GameSession activeSession;
    private ShopConfig shopConfig = ShopConfig.empty();

    public BedwarsManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadArenas() {
        File configFile = new File(plugin.getDataFolder(), "bedwars.yml");
        BedwarsConfigLoader loader = new BedwarsConfigLoader(configFile, plugin.getLogger());
        arenas = loader.load();
        plugin.getLogger().info("Loaded " + arenas.size() + " BedWars arenas.");
    }

    public void loadShopConfig() {
        File configFile = new File(plugin.getDataFolder(), "shop.yml");
        ShopConfigLoader loader = new ShopConfigLoader(configFile, plugin.getLogger());
        shopConfig = loader.load();
        plugin.getLogger().info("Loaded BedWars shop config.");
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public Arena getArena(String id) {
        return arenas.get(id);
    }

    public GameSession getActiveSession() {
        return activeSession;
    }

    public ShopConfig getShopConfig() {
        return shopConfig;
    }

    public boolean isBedwarsWorld(String worldName) {
        for (Arena arena : arenas.values()) {
            if (arena.getWorldName().equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    public void openMapSelect(Player player) {
        if (arenas.isEmpty()) {
            player.sendMessage(Component.text("No arenas configured.", NamedTextColor.RED));
            return;
        }
        new MapSelectMenu(this).open(player);
    }

    public void openTeamAssignMenu(Player player, Arena arena) {
        GameSession session = new GameSession(this, arena);
        new TeamAssignMenu(this, session).open(player);
    }

    public void startSession(Player initiator, GameSession session) {
        if (session.getAssignedCount() == 0) {
            initiator.sendMessage(Component.text("Assign at least one player to a team.", NamedTextColor.RED));
            return;
        }

        World world = session.getArena().getWorld();
        if (world == null) {
            initiator.sendMessage(Component.text("World not loaded: " + session.getArena().getWorldName(), NamedTextColor.RED));
            return;
        }

        if (activeSession != null) {
            activeSession.stop();
        }

        activeSession = session;
        session.start(plugin, initiator);
        initiator.sendMessage(Component.text("BedWars started on " + session.getArena().getId() + ".", NamedTextColor.GREEN));
    }

    public void stopSession(Player initiator) {
        if (activeSession == null) {
            initiator.sendMessage(Component.text("No BedWars session is running.", NamedTextColor.RED));
            return;
        }
        activeSession.stop();
        activeSession = null;
        initiator.sendMessage(Component.text("BedWars session stopped.", NamedTextColor.YELLOW));
    }

    public void endSession(GameSession session, TeamColor winner) {
        if (session != activeSession) {
            return;
        }
        Component message;
        if (winner != null) {
            message = Component.text("Team ", NamedTextColor.GOLD)
                    .append(winner.displayComponent())
                    .append(Component.text(" wins!", NamedTextColor.GOLD));
        } else {
            message = Component.text("BedWars ended.", NamedTextColor.YELLOW);
        }
        for (UUID playerId : session.getAssignments().keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
                if (winner != null && winner.equals(session.getAssignments().get(playerId))) {
                    launchVictoryFirework(player, winner);
                }
            }
        }
        session.stop();
        activeSession = null;
    }

    public void shutdown() {
        if (activeSession != null) {
            activeSession.stop();
            activeSession = null;
        }
        clearDroppedItems();
    }

    private void clearDroppedItems() {
        for (Arena arena : arenas.values()) {
            World world = arena.getWorld();
            if (world == null) {
                continue;
            }
            for (Item item : world.getEntitiesByClass(Item.class)) {
                item.remove();
            }
        }
    }

    private void launchVictoryFirework(Player player, TeamColor team) {
        player.getWorld().spawn(player.getLocation(), Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            FireworkEffect effect = FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(team.dyeColor().getColor())
                    .flicker(true)
                    .trail(true)
                    .build();
            meta.addEffect(effect);
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        });
    }
}
