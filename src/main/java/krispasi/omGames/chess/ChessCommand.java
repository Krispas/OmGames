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
            if (sender instanceof Player player) {
                if (!canOpenMenu(player)) return true;
                chessManager.openMenu(player);
                return true;
            }
            sender.sendMessage(usage());
            return true;
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        ChessManager.Result result;
        switch (root) {
            case "board" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                result = handleBoard(sender, args);
            }
            case "match" -> {
                result = handleMatch(sender, args);
            }
            case "timer" -> {
                result = handleTimerAlias(sender, args);
            }
            case "menu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can open the chess menu.", NamedTextColor.RED));
                    return true;
                }
                if (!canOpenMenu(player)) return true;
                chessManager.openMenu(player);
                return true;
            }
            case "log" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                result = handleLog(sender, args);
            }
            case "resign" -> {
                if (!requireOpponent(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this chess command.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.resign(player);
            }
            case "draw" -> {
                if (!requireOpponent(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this chess command.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.voteDraw(player);
            }
            case "undo" -> {
                if (!requireOpponent(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this chess command.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.undo(player, sender.isOp());
            }
            case "redo" -> {
                if (!requireOpponent(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this chess command.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.redo(player, sender.isOp());
            }
            case "backward" -> {
                if (!requireOpponent(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this chess command.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.backward(player);
            }
            case "forward" -> {
                if (!requireOpponent(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this chess command.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.forward(player);
            }
            case "checkmate" -> {
                if (!requireOpponent(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this chess command.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.checkmate(player);
            }
            case "pause" -> {
                if (!requireOpponent(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this chess command.", NamedTextColor.RED));
                    return true;
                }
                result = chessManager.togglePause(player, sender.isOp() && args.length == 2 ? args[1] : null);
            }
            default -> {
                sender.sendMessage(usage());
                return true;
            }
        }
        sender.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    private boolean canOpenMenu(Player player) {
        if (chessManager.isOpponent(player)) {
            player.sendMessage(Component.text("You cannot open the chess menu while playing a match.", NamedTextColor.RED));
            return false;
        }
        if (player.getWorld() == null || !"bedwars_lobby".equals(player.getWorld().getName())) {
            player.sendMessage(Component.text("The chess menu is only available in minecraft:bedwars_lobby.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private ChessManager.Result handleBoard(CommandSender sender, String[] args) {
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
        if (args.length == 4 && args[1].equalsIgnoreCase("reset") && args[2].equalsIgnoreCase("player")) {
            Player target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                return ChessManager.Result.fail("Player " + args[3] + " is not online.");
            }
            return chessManager.resetPlayer(target);
        }
        if ((args.length == 2 || args.length == 3) && args[1].equalsIgnoreCase("reset")) {
            return chessManager.resetBoard(sender, args.length == 3 ? args[2] : null);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            return chessManager.removeBoard(args[2]);
        }
        return ChessManager.Result.fail("Usage: /chess board build <x> <y> <z> | /chess board blocks <b1> <b2> <b3> | /chess board blocks reset | /chess board reset [board|*] | /chess board reset player <player> | /chess board remove <board|*>");
    }

    private ChessManager.Result handleMatch(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("timer")) {
            return handleMatchTimer(sender, args);
        }
        if ((args.length == 2 || args.length == 3) && args[1].equalsIgnoreCase("cancel")) {
            if (!sender.isOp()) {
                return ChessManager.Result.fail("This command requires admin permission.");
            }
            return chessManager.cancelMatch(sender, args.length == 3 ? args[2] : null);
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
        if ((args.length == 2 || args.length == 3) && args[1].equalsIgnoreCase("start")) {
            return chessManager.startMatch(sender, args.length == 3 ? args[2] : null);
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("spectate")) {
            if (!(sender instanceof Player player)) {
                return ChessManager.Result.fail("Only players can spectate chess matches.");
            }
            return chessManager.spectate(player, args.length >= 3 ? args[2] : null);
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("test")) {
            if (!sender.isOp()) {
                return ChessManager.Result.fail("This command requires admin permission.");
            }
            if (args.length < 3 || !(args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("off"))) {
                return ChessManager.Result.fail("Usage: /chess match test <on|off> [match]");
            }
            if (args.length > 4) {
                return ChessManager.Result.fail("Usage: /chess match test <on|off> [match]");
            }
            return chessManager.setTestMode(sender, args.length == 4 ? args[3] : null, args[2].equalsIgnoreCase("on"));
        }
        if ((args.length == 5 || args.length == 6) && args[1].equalsIgnoreCase("settings") && args[2].equalsIgnoreCase("figure_style")
                && args[3].equalsIgnoreCase(";")) {
            String target = args.length == 6 && sender.isOp() ? args[5] : null;
            if (args.length == 6 && !sender.isOp()) {
                return ChessManager.Result.fail("This command requires admin permission.");
            }
            return chessManager.setFigureStyle(sender, target, args[4]);
        }
        if ((args.length == 4 || args.length == 5) && args[1].equalsIgnoreCase("settings") && args[2].equalsIgnoreCase("figure_style")) {
            String target = args.length == 5 && sender.isOp() ? args[4] : null;
            if (args.length == 5 && !sender.isOp()) {
                return ChessManager.Result.fail("This command requires admin permission.");
            }
            return chessManager.setFigureStyle(sender, target, args[3]);
        }
        if ((args.length == 4 || args.length == 5) && args[1].equalsIgnoreCase("settings")) {
            String setting = args[2].toLowerCase(Locale.ROOT);
            if (isAdminOnlySetting(setting) && !sender.isOp()) {
                return ChessManager.Result.fail("This command requires admin permission.");
            }
            String target = args.length == 5 && sender.isOp() ? args[4] : null;
            boolean value;
            if (args[3].equalsIgnoreCase("true")) {
                value = true;
            } else if (args[3].equalsIgnoreCase("false")) {
                value = false;
            } else {
                return ChessManager.Result.fail("Chess setting value must be true or false.");
            }
            return chessManager.setSetting(sender, target, args[2], value);
        }
        return ChessManager.Result.fail("Usage: /chess match <white|black|start|spectate|timer|settings|test|cancel>");
    }

    private ChessManager.Result handleLog(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("print")) {
            if (args.length < 2) {
                return ChessManager.Result.fail("Usage: /chess log print [timestamp|*]");
            }
            return chessManager.printLog(sender, args.length >= 3 ? joinArgs(args, 2) : "*");
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("delete")) {
            return chessManager.deleteLog(joinArgs(args, 2));
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("search")) {
            List<String> players = new ArrayList<>();
            for (int i = 2; i < args.length; i++) {
                if (!args[i].equals("*")) {
                    players.add(args[i]);
                }
            }
            return chessManager.searchLogs(sender, players);
        }
        return ChessManager.Result.fail("Usage: /chess log print [timestamp|*] | /chess log delete <timestamp|*> | /chess log search <player> [player...]");
    }

    private String joinArgs(String[] args, int start) {
        List<String> parts = new ArrayList<>();
        for (int i = start; i < args.length; i++) {
            parts.add(args[i]);
        }
        return String.join(" ", parts);
    }

    private ChessManager.Result handleMatchTimer(CommandSender sender, String[] args) {
        if (args.length == 2 || (args.length >= 3 && args[2].equalsIgnoreCase("off"))) {
            String target = args.length == 4 && sender.isOp() ? args[3] : null;
            if (args.length > 4 || args.length == 4 && !sender.isOp()) {
                return ChessManager.Result.fail("Usage: /chess match timer off [match]");
            }
            return chessManager.setTimer(sender, target, ChessManager.ChessTimerConfig.off());
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("time")) {
            int index = 3;
            Long initialMillis = 10L * 60_000L;
            if (index < args.length && !args[index].equalsIgnoreCase("check") && !args[index].equalsIgnoreCase("move")
                    && !(sender.isOp() && isKnownTarget(args[index]))) {
                initialMillis = parseDurationMillis(args[index], 60_000L);
                index++;
            }
            if (initialMillis == null || initialMillis <= 0L) {
                return ChessManager.Result.fail("Timer time must be a positive duration.");
            }
            long checkBonusMillis = 0L;
            long moveBonusMillis = 0L;
            while (index < args.length && (args[index].equalsIgnoreCase("check") || args[index].equalsIgnoreCase("move"))) {
                boolean checkBonus = args[index].equalsIgnoreCase("check");
                index++;
                if (index >= args.length) {
                    return ChessManager.Result.fail("Timer bonus must be a valid duration.");
                }
                Long parsedBonus = parseDurationMillis(args[index], 1000L);
                if (parsedBonus == null || parsedBonus < 0L) {
                    return ChessManager.Result.fail("Timer bonus must be a valid duration.");
                }
                if (checkBonus) {
                    checkBonusMillis = parsedBonus;
                } else {
                    moveBonusMillis = parsedBonus;
                }
                index++;
            }
            String target = null;
            if (index < args.length && sender.isOp()) {
                target = args[index++];
            }
            if (index != args.length) {
                return ChessManager.Result.fail("Usage: /chess match timer off | /chess match timer time [duration] [move <duration>] [check <duration>] [match]");
            }
            return chessManager.setTimer(sender, target, new ChessManager.ChessTimerConfig(true, initialMillis, checkBonusMillis, moveBonusMillis));
        }
        return ChessManager.Result.fail("Usage: /chess match timer off | /chess match timer time [duration] [move <duration>] [check <duration>] [match]");
    }

    private ChessManager.Result handleTimerAlias(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return ChessManager.Result.fail("Usage: /chess timer [match|*] <off|time|move|check> ...");
        }
        int index = 1;
        String target = null;
        if (args[index].equals("*") || isKnownTarget(args[index])) {
            target = args[index++];
        }
        if (index >= args.length) {
            return ChessManager.Result.fail("Usage: /chess timer [match|*] <off|time|move|check> ...");
        }
        if (args[index].equalsIgnoreCase("off")) {
            return chessManager.setTimer(sender, target, ChessManager.ChessTimerConfig.off());
        }
        Long initialMillis = 10L * 60_000L;
        long checkBonusMillis = 0L;
        long moveBonusMillis = 0L;
        if (args[index].equalsIgnoreCase("time")) {
            index++;
            if (index < args.length && !args[index].equalsIgnoreCase("check") && !args[index].equalsIgnoreCase("move")) {
                initialMillis = parseDurationMillis(args[index++], 60_000L);
            }
        }
        while (index < args.length && (args[index].equalsIgnoreCase("check") || args[index].equalsIgnoreCase("move"))) {
            boolean checkBonus = args[index].equalsIgnoreCase("check");
            index++;
            if (index >= args.length) {
                return ChessManager.Result.fail("Timer bonus must be a valid duration.");
            }
            Long parsedBonus = parseDurationMillis(args[index++], 1000L);
            if (parsedBonus == null || parsedBonus < 0L) {
                return ChessManager.Result.fail("Timer bonus must be a valid duration.");
            }
            if (checkBonus) {
                checkBonusMillis = parsedBonus;
            } else {
                moveBonusMillis = parsedBonus;
            }
        }
        if (index != args.length || initialMillis == null || initialMillis <= 0L) {
            return ChessManager.Result.fail("Usage: /chess timer [match|*] <off|time|move|check> ...");
        }
        return chessManager.setTimer(sender, target, new ChessManager.ChessTimerConfig(true, initialMillis, checkBonusMillis, moveBonusMillis));
    }

    private Long parseDurationMillis(String value, long defaultUnitMillis) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        long unit = defaultUnitMillis;
        char last = text.charAt(text.length() - 1);
        if (Character.isLetter(last)) {
            unit = switch (last) {
                case 's' -> 1000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                default -> -1L;
            };
            text = text.substring(0, text.length() - 1);
        }
        if (unit <= 0L || text.isBlank()) {
            return null;
        }
        try {
            double amount = Double.parseDouble(text);
            return Math.round(amount * unit);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.isOp()) {
            return true;
        }
        sender.sendMessage(Component.text("This command requires admin permission.", NamedTextColor.RED));
        return false;
    }

    private boolean requireOpponent(CommandSender sender) {
        if (sender.isOp()) {
            return true;
        }
        if (sender instanceof Player player && chessManager.isOpponent(player)) {
            return true;
        }
        sender.sendMessage(Component.text("This command requires opponent permission.", NamedTextColor.RED));
        return false;
    }

    private boolean isAdminOnlySetting(String setting) {
        return setting.equals("do_endgame_checks")
                || setting.equals("do_movement_check")
                || setting.equals("show_annotation");
    }

    private boolean isKnownTarget(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return chessManager.getBoardTimestamps().contains(value) || chessManager.getActiveMatchTimestamps().contains(value);
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
            return filter(args[0], "menu", "board", "match", "timer", "log", "pause", "resign", "draw", "undo", "redo", "backward", "forward", "checkmate");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("board")) {
            return filter(args[1], "build", "blocks", "reset", "remove");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("board") && args[1].equalsIgnoreCase("reset")) {
            List<String> options = new ArrayList<>();
            options.add("player");
            options.add("*");
            options.addAll(chessManager.getBoardTimestamps());
            return filter(args[2], options.toArray(String[]::new));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("board") && args[1].equalsIgnoreCase("reset")
                && args[2].equalsIgnoreCase("player")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[3].toLowerCase(Locale.ROOT)))
                    .toList();
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
            return filter(args[1], "white", "black", "start", "spectate", "settings", "timer", "test", "cancel");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("match") && args[1].equalsIgnoreCase("start")) {
            return filter(args[2], chessManager.getBoardTimestamps().toArray(String[]::new));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("match") && args[1].equalsIgnoreCase("cancel")) {
            List<String> options = new ArrayList<>();
            options.add("*");
            options.addAll(chessManager.getActiveMatchTimestamps());
            return filter(args[2], options.toArray(String[]::new));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("match") && args[1].equalsIgnoreCase("spectate")) {
            List<String> options = new ArrayList<>();
            options.add("*");
            options.addAll(chessManager.getActiveMatchTimestamps());
            return filter(args[2], options.toArray(String[]::new));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("match") && args[1].equalsIgnoreCase("test")) {
            return filter(args[2], "on", "off");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("match") && args[1].equalsIgnoreCase("test")) {
            List<String> options = new ArrayList<>();
            options.add("*");
            options.addAll(chessManager.getActiveMatchTimestamps());
            options.addAll(chessManager.getBoardTimestamps());
            return filter(args[3], options.toArray(String[]::new));
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
            return filter(args[2], "do_movement_check", "visualize_movement_check", "do_endgame_checks", "allow_undo", "show_annotation", "figure_style");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("match") && args[1].equalsIgnoreCase("settings") && args[2].equalsIgnoreCase("figure_style")) {
            return filter(args[3], "default", "flat");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("match") && args[1].equalsIgnoreCase("settings")) {
            return filter(args[3], "true", "false");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("log")) {
            return filter(args[1], "print", "delete", "search");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("log")
                && (args[1].equalsIgnoreCase("print") || args[1].equalsIgnoreCase("delete"))) {
            List<String> options = new ArrayList<>();
            options.add("*");
            options.addAll(chessManager.getMatchLogTimestamps());
            return filter(args[2], options.toArray(String[]::new));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("match") && args[1].equalsIgnoreCase("timer")) {
            return filter(args[2], "off", "time");
        }
        if (args[0].equalsIgnoreCase("match") && args.length >= 4 && args[1].equalsIgnoreCase("timer") && args[2].equalsIgnoreCase("time")) {
            String previous = args[args.length - 2];
            if (previous.equalsIgnoreCase("check") || previous.equalsIgnoreCase("move")) {
                return filter(args[args.length - 1], "10s", "s", "m", "h");
            }
            if (args[args.length - 1].matches("\\d+")) {
                return filter(args[args.length - 1], args[args.length - 1] + "s", args[args.length - 1] + "m", args[args.length - 1] + "h");
            }
            return filter(args[args.length - 1], "10m", "move", "check");
        }
        if (args[0].equalsIgnoreCase("timer")) {
            if (args.length == 2) {
                List<String> options = new ArrayList<>();
                options.add("*");
                options.add("off");
                options.add("time");
                options.add("move");
                options.add("check");
                options.addAll(chessManager.getActiveMatchTimestamps());
                return filter(args[1], options.toArray(String[]::new));
            }
            String previous = args[args.length - 2];
            if (previous.equalsIgnoreCase("check") || previous.equalsIgnoreCase("move") || previous.equalsIgnoreCase("time")) {
                return filter(args[args.length - 1], "10s", "10m", "s", "m", "h");
            }
            if (args[args.length - 1].matches("\\d+")) {
                return filter(args[args.length - 1], args[args.length - 1] + "s", args[args.length - 1] + "m", args[args.length - 1] + "h");
            }
            return filter(args[args.length - 1], "time", "move", "check", "off");
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
                "Usage: /chess [menu] | /chess board build <x> <y> <z> | /chess board reset [board|*] | /chess board reset player <player> | /chess match <white|black|start|spectate|timer|settings|test|cancel> | /chess timer [match|*] <off|time|move|check> | /chess log <print|delete|search> | /chess pause | /chess resign | /chess draw | /chess undo | /chess redo | /chess backward | /chess forward | /chess checkmate",
                NamedTextColor.YELLOW
        );
    }
}
