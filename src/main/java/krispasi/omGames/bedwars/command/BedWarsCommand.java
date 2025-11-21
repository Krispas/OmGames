package krispasi.omGames.bedwars.command;

import krispasi.omGames.bedwars.config.BedWarsConfigService;
import krispasi.omGames.bedwars.menu.BedWarsMenuFactory;
import krispasi.omGames.bedwars.model.BedWarsMap;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class BedWarsCommand implements CommandExecutor {
    private final BedWarsMenuFactory menuFactory;
    private final BedWarsConfigService configService;

    public BedWarsCommand(BedWarsMenuFactory menuFactory, BedWarsConfigService configService) {
        this.menuFactory = menuFactory;
        this.configService = configService;
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
            openStartMenu(player);
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Usage: /bw start");
        return true;
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
            configService.getMaps();
        }
    }
}
