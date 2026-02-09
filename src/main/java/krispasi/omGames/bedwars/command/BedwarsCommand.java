package krispasi.omGames.bedwars.command;

import java.util.Arrays;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.setup.BedwarsSetupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class BedwarsCommand implements CommandExecutor, TabCompleter {
    private final BedwarsManager bedwarsManager;
    private final BedwarsSetupManager setupManager;

    public BedwarsCommand(BedwarsManager bedwarsManager, BedwarsSetupManager setupManager) {
        this.bedwarsManager = bedwarsManager;
        this.setupManager = setupManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("omgames.bw.start")
                && !sender.hasPermission("omgames.bw.setup")
                && !sender.hasPermission("omgames.bw.reload")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            bedwarsManager.openMapSelect(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("stop")) {
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            bedwarsManager.stopSession(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("omgames.bw.reload")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            bedwarsManager.loadArenas();
            bedwarsManager.loadCustomItems();
            bedwarsManager.loadShopConfig();
            bedwarsManager.loadQuickBuy();
            sender.sendMessage(Component.text("BedWars configs reloaded.", NamedTextColor.GREEN));
            if (bedwarsManager.getActiveSession() != null && bedwarsManager.getActiveSession().isActive()) {
                sender.sendMessage(Component.text("Running match keeps its current arena state.", NamedTextColor.YELLOW));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("setup")) {
            if (!sender.hasPermission("omgames.bw.setup") && !sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (bedwarsManager.getActiveSession() != null && bedwarsManager.getActiveSession().isActive()) {
                sender.sendMessage(Component.text("Stop the current BedWars match before setup.", NamedTextColor.RED));
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("new")) {
                setupManager.createArena(player, args[2]);
                return true;
            }
            if (args.length == 2) {
                if (args[1].equalsIgnoreCase("new")) {
                    sender.sendMessage(Component.text("Usage: /bw setup new <arena>", NamedTextColor.YELLOW));
                    return true;
                }
                setupManager.showSetupList(player, args[1]);
                return true;
            }
            if (args.length >= 3) {
                String arenaId = args[1];
                String target = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                setupManager.applySetup(player, arenaId, target);
                return true;
            }
        }

        sender.sendMessage(Component.text("Usage: /bw start | /bw stop | /bw reload | /bw setup new <arena> | /bw setup <arena> [key]", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("start", "stop", "reload", "setup")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            List<String> options = new java.util.ArrayList<>();
            options.add("new");
            for (var arena : bedwarsManager.getArenas()) {
                options.add(arena.getId());
            }
            return options.stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup") && !args[1].equalsIgnoreCase("new")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return setupManager.getSetupKeys(args[1]).stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        return List.of();
    }
}
