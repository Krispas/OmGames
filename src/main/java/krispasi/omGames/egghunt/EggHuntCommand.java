package krispasi.omGames.egghunt;

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

public class EggHuntCommand implements CommandExecutor, TabCompleter {
    private final EggHuntManager eggHuntManager;

    public EggHuntCommand(EggHuntManager eggHuntManager) {
        this.eggHuntManager = eggHuntManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            sender.sendMessage(Component.text("Only OP players can use /egghunt.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(usage());
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        EggHuntManager.Result result;
        switch (action) {
            case "add" -> {
                result = eggHuntManager.addPoint(player);
            }
            case "prepare" -> result = eggHuntManager.prepareSidebar();
            case "timer" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /egghunt timer <seconds>", NamedTextColor.YELLOW));
                    return true;
                }
                int seconds;
                try {
                    seconds = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Timer must be a whole number of seconds.", NamedTextColor.RED));
                    return true;
                }
                result = eggHuntManager.setTimerSeconds(seconds);
            }
            case "start" -> result = eggHuntManager.start(player);
            case "clear" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /egghunt clear <near/all>", NamedTextColor.YELLOW));
                    return true;
                }
                String mode = args[1].toLowerCase(Locale.ROOT);
                if (mode.equals("near")) {
                    result = eggHuntManager.clearPointsNear(player);
                } else if (mode.equals("all")) {
                    result = eggHuntManager.clearAllPoints();
                } else {
                    sender.sendMessage(Component.text("Usage: /egghunt clear <near/all>", NamedTextColor.YELLOW));
                    return true;
                }
            }
            default -> {
                sender.sendMessage(usage());
                return true;
            }
        }

        sender.sendMessage(Component.text(
                result.message(),
                result.success() ? NamedTextColor.GREEN : NamedTextColor.RED
        ));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("add", "prepare", "timer", "start", "clear")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("timer")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("30", "60", "120", "180", "300")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("near", "all")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        return List.of();
    }

    private Component usage() {
        return Component.text(
                "Usage: /egghunt add | /egghunt prepare | /egghunt timer <seconds> | /egghunt start | /egghunt clear <near/all>",
                NamedTextColor.YELLOW
        );
    }
}
