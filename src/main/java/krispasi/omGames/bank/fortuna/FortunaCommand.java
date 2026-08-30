package krispasi.omGames.bank.fortuna;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class FortunaCommand implements CommandExecutor, TabCompleter {
    private final FortunaManager fortunaManager;

    public FortunaCommand(FortunaManager fortunaManager) {
        this.fortunaManager = fortunaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("fortuna")) {
            sender.sendMessage(usage());
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the Fortuna GUI.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("omgames.fortuna.manage")) {
            sender.sendMessage(Component.text("You do not have permission to manage Fortuna.", NamedTextColor.RED));
            return true;
        }
        fortunaManager.openFortunaMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("fortuna")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private Component usage() {
        return Component.text("Usage: /bank fortuna", NamedTextColor.YELLOW);
    }
}
