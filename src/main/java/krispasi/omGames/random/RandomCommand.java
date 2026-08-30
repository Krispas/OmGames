package krispasi.omGames.random;

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

public final class RandomCommand implements CommandExecutor, TabCompleter {
    private final RandomGifManager gifManager;

    public RandomCommand(RandomGifManager gifManager) {
        this.gifManager = gifManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("gif")) {
            sender.sendMessage(usage());
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the GIF GUI.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("omgames.random.gif")) {
            sender.sendMessage(Component.text("You do not have permission to manage GIF maps.", NamedTextColor.RED));
            return true;
        }
        gifManager.openGifMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("gif")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private Component usage() {
        return Component.text("Usage: /omgames gif", NamedTextColor.YELLOW);
    }
}
