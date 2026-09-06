package krispasi.omGames.bank;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import krispasi.omGames.bank.fortuna.FortunaCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class BankCommand implements CommandExecutor, TabCompleter {
    private final BankManager bankManager;
    private final FortunaCommand fortunaCommand;

    public BankCommand(BankManager bankManager, FortunaCommand fortunaCommand) {
        this.bankManager = bankManager;
        this.fortunaCommand = fortunaCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("fortuna")) {
            return fortunaCommand.onCommand(sender, command, label, args);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("admin")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can open the Bank admin GUI.", NamedTextColor.RED));
                return true;
            }
            if (!sender.hasPermission("omgames.bank.admin")) {
                sender.sendMessage(Component.text("You do not have permission to manage Bank.", NamedTextColor.RED));
                return true;
            }
            bankManager.openAdminMenu(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("atm")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can open the ATM GUI.", NamedTextColor.RED));
                return true;
            }
            if (!sender.hasPermission("omgames.bank.admin")) {
                sender.sendMessage(Component.text("You do not have permission to open Bank admin test menus.", NamedTextColor.RED));
                return true;
            }
            bankManager.openAtm(player);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("register_items")) {
            if (!sender.hasPermission("omgames.bank.admin")) {
                sender.sendMessage(Component.text("You do not have permission to register Bank items.", NamedTextColor.RED));
                return true;
            }
            BankManager.Result result = bankManager.registerOmVeinsItems();
            sender.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(usage());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("admin", "atm", "fortuna", "register_items")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private Component usage() {
        return Component.text("Usage: /bank admin | /bank atm | /bank fortuna | /bank register_items", NamedTextColor.YELLOW);
    }
}
