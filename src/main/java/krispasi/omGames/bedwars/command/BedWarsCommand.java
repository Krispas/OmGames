package krispasi.omGames.bedwars.command;

import krispasi.omGames.bedwars.config.BedWarsConfigService;
import krispasi.omGames.bedwars.game.BedWarsMatchManager;
import krispasi.omGames.bedwars.menu.BedWarsMenuFactory;
import krispasi.omGames.bedwars.model.BedWarsMap;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BedWarsCommand implements CommandExecutor, TabCompleter {
    private final BedWarsMenuFactory menuFactory;
    private final BedWarsConfigService configService;
    private final BedWarsMatchManager matchManager;

    public BedWarsCommand(BedWarsMenuFactory menuFactory, BedWarsConfigService configService, BedWarsMatchManager matchManager) {
        this.menuFactory = menuFactory;
        this.configService = configService;
        this.matchManager = matchManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can start BedWars setup.");
            return true;
        }

        if (!player.hasPermission("omgames.bw")) {
            player.sendMessage(ChatColor.RED + "You need omgames.bw to do that.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("start")) {
            if (args.length >= 2) {
                handleDirectStart(player, args);
            } else {
                openStartMenu(player);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            if (configService.getLogger() != null && matchManager.isRunning()) {
                matchManager.stopMatch("stopped by admin");
                player.sendMessage(ChatColor.YELLOW + "BedWars stopped.");
            } else {
                player.sendMessage(ChatColor.RED + "No BedWars match is running.");
            }
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Usage: /bw start");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // keep auto-complete light so BedWars command is discoverable
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("start");
            options.add("stop");
            return options;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return new ArrayList<>(configService.getMaps().keySet());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            List<String> modes = new ArrayList<>();
            modes.add("solo");
            modes.add("doubles");
            return modes;
        }
        return Collections.emptyList();
    }

    private void openStartMenu(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(configService.getDimensionName())) {
            player.sendMessage(ChatColor.RED + "Stay in the BedWars dimension before running /bw.");
            return;
        }

        Map<String, BedWarsMap> maps = configService.getMaps();
        if (maps.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No BedWars maps found in bedwars.yml.");
            return;
        }

        try {
            player.openInventory(menuFactory.buildMapMenu(maps));
        } catch (Exception ex) {
            player.sendMessage(ChatColor.RED + "Could not open the BedWars menu. Check console.");
            // log the root cause so we can debug without crashing anything
            configService.getLogger().warning("BedWars menu failed: " + ex.getMessage());
        }
    }

    private void handleDirectStart(Player player, String[] args) {
        if (!player.getWorld().getName().equalsIgnoreCase(configService.getDimensionName())) {
            player.sendMessage(ChatColor.RED + "Stay in the BedWars dimension before running /bw.");
            return;
        }
        String mapName = args[1];
        BedWarsMap map = configService.getMap(mapName).orElse(null);
        if (map == null) {
            player.sendMessage(ChatColor.RED + "Unknown map: " + mapName);
            return;
        }

        BedWarsMatchManager.Mode mode = BedWarsMatchManager.Mode.SOLO;
        if (args.length >= 3) {
            mode = matchManager.parseMode(args[2]);
        }

        if (matchManager.isRunning()) {
            player.sendMessage(ChatColor.RED + "A BedWars round is already running.");
            return;
        }

        if (matchManager.startMatch(map, mode, player.getWorld().getPlayers())) {
            player.sendMessage(ChatColor.GREEN + "BedWars starting on " + map.getName() + " in " + mode + " mode.");
        } else {
            player.sendMessage(ChatColor.RED + "Could not start BedWars. Check console for details.");
        }
    }
}
