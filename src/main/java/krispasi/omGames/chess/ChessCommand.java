package krispasi.omGames.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ChessCommand implements CommandExecutor, TabCompleter {
    private final ChessManager chessManager;

    public ChessCommand(ChessManager chessManager) {
        this.chessManager = chessManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(usage());
            return true;
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        ChessManager.Result result;
        switch (root) {
            case "board" -> {
                if (!requireOp(sender)) {
                    return true;
                }
                result = handleBoard(args);
            }
            case "match" -> {
                if (!requireOp(sender)) {
                    return true;
                }
                result = handleMatch(sender, args);
            }
            case "resign" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can resign from a chess match.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.resign(player);
            }
            case "draw" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can vote for a chess draw.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.voteDraw(player);
            }
            case "undo" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can undo chess moves.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.undo(player, sender.isOp());
            }
            case "redo" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can redo chess moves.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.redo(player, sender.isOp());
            }
            case "checkmate" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can run a chess checkmate check.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.checkmate(player);
            }
            default -> {
                sender.sendMessage(usage());
                return true;
            }
        }
        sender.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    private ChessManager.Result handleBoard(String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("build")) {
            if (args.length != 5) {
                return ChessManager.Result.fail("Usage: /chess board build <x> <y> <z>");
            }
            Integer x = parseInt(args[2]);
            Integer y = parseInt(args[3]);
            Integer z = parseInt(args[4]);
            if (x == null || y == null || z == null) {
                return ChessManager.Result.fail("Board coordinates must be whole numbers.");
            }
            return chessManager.buildBoard(null, x, y, z);
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("blocks")) {
            if (args.length == 3 && args[2].equalsIgnoreCase("reset")) {
                return chessManager.resetPalette();
            }
            if (args.length != 5) {
                return ChessManager.Result.fail("Usage: /chess board blocks <b1> <b2> <b3> | /chess board blocks reset");
            }
            Material b1 = parseBlock(args[2]);
            Material b2 = parseBlock(args[3]);
            Material b3 = parseBlock(args[4]);
            if (b1 == null || b2 == null || b3 == null) {
                return ChessManager.Result.fail("Each board palette entry must be a valid block.");
            }
            return chessManager.setPalette(b1, b2, b3);
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("reset")) {
            return chessManager.resetBoard();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            return chessManager.removeBoard(args[2]);
        }
        return ChessManager.Result.fail("Usage: /chess board build <x> <y> <z> | /chess board blocks <b1> <b2> <b3> | /chess board blocks reset | /chess board reset | /chess board remove <timestamp|*>");
    }

    private ChessManager.Result handleMatch(CommandSender sender, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("print_log")) {
            return chessManager.printRecentLog(sender);
        }
        if (args.length >= 2 && (args[1].equalsIgnoreCase("white") || args[1].equalsIgnoreCase("black"))) {
            if (args.length < 3 || args.length > 5) {
                return ChessManager.Result.fail("Usage: /chess match " + args[1].toLowerCase(Locale.ROOT)
                        + " <player> [player] [player]");
            }
            ChessSide side = ChessSide.fromKey(args[1]);
            List<Player> players = new ArrayList<>();
            for (int i = 2; i < args.length; i++) {
                Player player = Bukkit.getPlayerExact(args[i]);
                if (player == null) {
                    return ChessManager.Result.fail("Player " + args[i] + " is not online.");
                }
                if (!players.contains(player)) {
                    players.add(player);
                }
            }
            return chessManager.setTeam(side, players);
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("start")) {
            return chessManager.startMatch();
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("test")) {
            return chessManager.enableTestMode();
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("settings")) {
            boolean value;
            if (args[3].equalsIgnoreCase("true")) {
                value = true;
            } else if (args[3].equalsIgnoreCase("false")) {
                value = false;
            } else {
                return ChessManager.Result.fail("Chess setting value must be true or false.");
            }
            return chessManager.setSetting(args[2], value);
        }
        return ChessManager.Result.fail("Usage: /chess match <white|black> <players...> | /chess match start | /chess match test | /chess match print_log | /chess match settings <setting> <true|false>");
    }

    private boolean requireOp(CommandSender sender) {
        if (sender.isOp()) {
            return true;
        }
        sender.sendMessage(Component.text("Only OP players can use this chess command.", NamedTextColor.RED));
        return false;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Material parseBlock(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        Material material = Material.matchMaterial(normalized);
        return material != null && material.isBlock() ? material : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], "board", "match", "resign", "draw", "undo", "redo", "checkmate");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("board")) {
            return filter(args[1], "build", "blocks", "reset", "remove");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("board") && args[1].equalsIgnoreCase("blocks")) {
            return filter(args[2], "reset", "minecraft:smooth_quartz", "minecraft:coal_block", "minecraft:smooth_basalt");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("board") && args[1].equalsIgnoreCase("remove")) {
            List<String> options = new ArrayList<>();
            options.add("*");
            options.addAll(chessManager.getBoardTimestamps());
            String input = args[2].toLowerCase(Locale.ROOT);
            return options.stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("match")) {
            return filter(args[1], "white", "black", "start", "settings", "test", "print_log");
        }
        if (args.length >= 3 && args.length <= 5 && args[0].equalsIgnoreCase("match")
                && (args[1].equalsIgnoreCase("white") || args[1].equalsIgnoreCase("black"))) {
            String input = args[args.length - 1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("match") && args[1].equalsIgnoreCase("settings")) {
            return filter(args[2], "do_movement_check", "visualize_movement_check", "do_endgame_checks", "allow_undo", "show_annotation");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("match") && args[1].equalsIgnoreCase("settings")) {
            return filter(args[3], "true", "false");
        }
        return List.of();
    }

    private List<String> filter(String input, String... options) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return Stream.of(options)
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }

    private Component usage() {
        return Component.text(
                "Usage: /chess board build <x> <y> <z> | /chess board remove <timestamp|*> | /chess match <white|black|start|settings|test|print_log> | /chess resign | /chess draw | /chess undo | /chess redo | /chess checkmate",
                NamedTextColor.YELLOW
        );
    }
}
