package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class HallsOfCarnageCommand implements CommandExecutor, TabCompleter {
    private static final String MANAGE_PERMISSION = "omgames.hoc.manage";

    private final HallsOfCarnageManager manager;

    public HallsOfCarnageCommand(HallsOfCarnageManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(usage());
            return true;
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        HallsOfCarnageManager.Result result;
        switch (root) {
            case "menu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can open the Halls menu.", NamedTextColor.RED));
                    return true;
                }
                manager.openMainMenu(player);
                return true;
            }
            case "scenarios" -> {
                sendScenarios(sender);
                return true;
            }
            case "scenario" -> {
                if (!requireOp(sender)) {
                    return true;
                }
                sendScenarioDebug(sender, args);
                return true;
            }
            case "sessions" -> {
                sendSessions(sender);
                return true;
            }
            case "top" -> {
                sendLeaderboard(sender);
                return true;
            }
            case "shame" -> {
                handleShame(sender, args);
                return true;
            }
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can teleport to the Halls lobby.", NamedTextColor.RED));
                    return true;
                }
                result = manager.teleportToLobby(player);
            }
            case "start" -> {
                if (!requireOp(sender)) {
                    return true;
                }
                result = handleStart(sender, args);
            }
            case "stop" -> {
                if (!requireOp(sender)) {
                    return true;
                }
                if (args.length != 2) {
                    result = HallsOfCarnageManager.Result.fail("Usage: /hoc stop <session_id|*>");
                } else {
                    result = manager.stopSession(args[1]);
                }
            }
            case "floor" -> {
                if (!requireOp(sender)) {
                    return true;
                }
                if (args.length != 3) {
                    result = HallsOfCarnageManager.Result.fail("Usage: /hoc floor <session_id> <floor>");
                } else {
                    result = manager.forceSessionFloor(args[1], args[2]);
                }
            }
            case "give" -> {
                if (!requireOp(sender)) {
                    return true;
                }
                result = handleGive(sender, args);
            }
            case "reload" -> {
                if (!requireOp(sender)) {
                    return true;
                }
                result = manager.reload();
            }
            case "reset" -> {
                if (!requireOp(sender)) {
                    return true;
                }
                result = manager.resetGameResources(args.length == 2 && args[1].equalsIgnoreCase("confirm"));
            }
            case "lobby" -> {
                if (!requireOp(sender)) {
                    return true;
                }
                result = handleLobby(sender, args);
            }
            default -> {
                sender.sendMessage(usage());
                return true;
            }
        }
        sender.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    private HallsOfCarnageManager.Result handleLobby(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return HallsOfCarnageManager.Result.fail("Only players can configure the Halls lobby.");
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("setspawn")) {
            return manager.setLobbySpawn(player);
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("spawnMenuVillager")) {
            Float yaw = null;
            if (args.length >= 3) {
                try {
                    yaw = Float.parseFloat(args[2]);
                } catch (NumberFormatException ex) {
                    return HallsOfCarnageManager.Result.fail("Villager rotation must be a number.");
                }
            }
            return manager.spawnMenuVillager(player, yaw);
        }
        return HallsOfCarnageManager.Result.fail("Usage: /hoc lobby setspawn | /hoc lobby spawnMenuVillager [rotation]");
    }

    private HallsOfCarnageManager.Result handleStart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player initiator)) {
            return HallsOfCarnageManager.Result.fail("Only players can prepare a Halls scenario.");
        }
        if (args.length < 2) {
            return HallsOfCarnageManager.Result.fail("Usage: /hoc start <scenario> [player...]");
        }
        List<Player> players = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            Player player = Bukkit.getPlayerExact(args[i]);
            if (player == null) {
                return HallsOfCarnageManager.Result.fail("Player " + args[i] + " is not online.");
            }
            if (!players.contains(player)) {
                players.add(player);
            }
        }
        return manager.startScenario(initiator, args[1], players);
    }

    private HallsOfCarnageManager.Result handleGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return HallsOfCarnageManager.Result.fail("Only players can receive Halls items.");
        }
        if (args.length < 2 || args.length > 3) {
            return HallsOfCarnageManager.Result.fail("Usage: /hoc give <item> [amount]");
        }
        int amount = 1;
        if (args.length == 3) {
            Integer parsed = parseInt(args[2]);
            if (parsed == null || parsed <= 0) {
                return HallsOfCarnageManager.Result.fail("Amount must be a positive whole number.");
            }
            amount = parsed;
        }
        return manager.giveItem(player, args[1], amount);
    }

    private void handleShame(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Usage: /hoc shame <player>", NamedTextColor.YELLOW));
                return;
            }
            sender.sendMessage(Component.text("Your shame: " + manager.getShame(player.getUniqueId()), NamedTextColor.AQUA));
            return;
        }
        if ((args.length == 4) && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("add"))) {
            if (!requireOp(sender)) {
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            Integer amount = parseInt(args[3]);
            if (amount == null) {
                sender.sendMessage(Component.text("Shame amount must be a whole number.", NamedTextColor.RED));
                return;
            }
            int value = args[1].equalsIgnoreCase("set")
                    ? manager.setShame(target.getUniqueId(), amount)
                    : manager.addShame(target.getUniqueId(), amount);
            sender.sendMessage(Component.text(target.getName() + " shame is now " + value + ".", NamedTextColor.GREEN));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID playerId = target.getUniqueId();
        String name = target.getName() == null ? args[1] : target.getName();
        sender.sendMessage(Component.text(name + " shame: " + manager.getShame(playerId), NamedTextColor.AQUA));
    }

    private void sendScenarios(CommandSender sender) {
        List<HallsScenario> scenarios = manager.getScenarios();
        if (scenarios.isEmpty()) {
            sender.sendMessage(Component.text("No Halls scenarios are loaded.", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("Halls scenarios:", NamedTextColor.GOLD));
        for (HallsScenario scenario : scenarios) {
            sender.sendMessage(Component.text("- " + scenario.id() + " (" + scenario.name() + ", "
                    + scenario.floorCount() + " floors, " + scenario.minPlayers() + "-" + scenario.maxPlayers()
                    + " players)", NamedTextColor.YELLOW));
            for (HallsScenario.FloorDefinition floor : scenario.floors()) {
                sender.sendMessage(Component.text("  " + floorLabel(floor) + ": " + floor.kind()
                        + ", " + floor.levelType() + ", rooms " + floor.rooms(), NamedTextColor.GRAY));
                if (floor.trappedRooms() > 0 || floor.holes() > 0) {
                    sender.sendMessage(Component.text("    traps: " + floor.trappedRooms() + " rooms, "
                            + floor.minTrapsPerRoom() + "-" + floor.maxTrapsPerRoom()
                            + " per room; holes " + floor.holes(), NamedTextColor.DARK_GRAY));
                }
            }
        }
    }

    private void sendScenarioDebug(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /hoc scenario <scenario>", NamedTextColor.RED));
            return;
        }
        HallsScenario scenario = manager.getScenario(args[1]);
        if (scenario == null) {
            sender.sendMessage(Component.text("Unknown Halls scenario: " + args[1] + ".", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("Parsed Halls scenario debug: " + scenario.id(), NamedTextColor.GOLD));
        for (String line : scenario.debugLines()) {
            sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
    }

    private String floorLabel(HallsScenario.FloorDefinition floor) {
        if (floor.firstFloor() == floor.lastFloor()) {
            return "floor " + floor.firstFloor();
        }
        return "floors " + floor.firstFloor() + "-" + floor.lastFloor();
    }

    private void sendLeaderboard(CommandSender sender) {
        List<HallsShameService.ShameEntry> entries = manager.getShameLeaderboard(10);
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("No Halls shame has been recorded yet.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Lowest Halls shame:", NamedTextColor.AQUA));
        int rank = 1;
        for (HallsShameService.ShameEntry entry : entries) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.playerId());
            String name = player.getName() == null ? entry.playerId().toString().substring(0, 8) : player.getName();
            sender.sendMessage(Component.text(rank++ + ". " + name + ": " + entry.shame(), NamedTextColor.GRAY));
        }
    }

    private void sendSessions(CommandSender sender) {
        List<HallsSession> sessions = manager.getActiveSessions();
        if (sessions.isEmpty()) {
            sender.sendMessage(Component.text("No Halls sessions are active.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Active Halls sessions:", NamedTextColor.GOLD));
        for (HallsSession session : sessions) {
            HallsConfig.BlockPoint origin = session.origin();
            sender.sendMessage(Component.text("- " + session.id() + ": " + session.scenario().name()
                    + " (floor " + session.currentFloor() + ", " + session.activeLevelTypeId()
                    + ", rooms " + session.activeGeneratedRooms() + "/" + session.activeTargetRooms()
                    + ", " + session.participants().size() + " players, origin "
                    + origin.x() + " " + origin.y() + " " + origin.z() + ")", NamedTextColor.YELLOW));
        }
    }

    private boolean requireOp(CommandSender sender) {
        if (sender.isOp() || sender.hasPermission(MANAGE_PERMISSION)) {
            return true;
        }
        sender.sendMessage(Component.text("Only OP players can use this Halls command.", NamedTextColor.RED));
        return false;
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Component usage() {
        return Component.text("Usage: /hoc menu | /hoc scenarios | /hoc scenario <scenario> | /hoc sessions | /hoc top | /hoc shame [player] | /hoc shame <set|add> <player> <amount> | /hoc tp | /hoc start <scenario> [player...] | /hoc stop <session_id|*> | /hoc floor <session_id> <floor> | /hoc give <item> [amount] | /hoc lobby <setspawn|spawnMenuVillager> | /hoc reload | /hoc reset confirm", NamedTextColor.YELLOW);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], "menu", "scenarios", "scenario", "sessions", "top", "shame", "tp", "start", "stop", "floor", "give", "lobby", "reload", "reset");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(args[1], manager.getItemIds().toArray(String[]::new));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return filter(args[1], "confirm");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("scenario")) {
            return filter(args[1], manager.getScenarios().stream().map(HallsScenario::id).toArray(String[]::new));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return filter(args[1], manager.getScenarios().stream().map(HallsScenario::id).toArray(String[]::new));
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("start")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lobby")) {
            return filter(args[1], "setspawn", "spawnMenuVillager");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            List<String> options = new ArrayList<>();
            options.add("*");
            options.addAll(manager.getActiveSessions().stream().map(session -> Integer.toString(session.id())).toList());
            return filter(args[1], options.toArray(String[]::new));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("floor")) {
            return filter(args[1], manager.getActiveSessions().stream()
                    .map(session -> Integer.toString(session.id()))
                    .toArray(String[]::new));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("floor")) {
            HallsSession session = manager.getActiveSessions().stream()
                    .filter(candidate -> Integer.toString(candidate.id()).equals(args[1]))
                    .findFirst()
                    .orElse(null);
            if (session == null) {
                return List.of();
            }
            List<String> floors = new ArrayList<>();
            for (int floor = 1; floor <= session.scenario().floorCount(); floor++) {
                floors.add(Integer.toString(floor));
            }
            return filter(args[2], floors.toArray(String[]::new));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("shame")) {
            return filter(args[1], "set", "add");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("shame")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("add"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    private List<String> filter(String input, String... options) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return Stream.of(options)
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
