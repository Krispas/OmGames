package krispasi.omGames.bedwars.command;

import java.util.Arrays;
import java.util.stream.Collectors;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.setup.BedwarsSetupManager;
import krispasi.omGames.bedwars.stats.BedwarsPlayerStats;
import krispasi.omGames.bedwars.stats.BedwarsStatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Command executor and tab completer for {@code /bw}.
 * <p>Checks permissions and routes start, stop, reload, and setup actions to
 * {@link krispasi.omGames.bedwars.BedwarsManager} and
 * {@link krispasi.omGames.bedwars.setup.BedwarsSetupManager}.</p>
 * @see krispasi.omGames.bedwars.setup.BedwarsSetupManager
 */
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
        if (args.length > 0 && args[0].equalsIgnoreCase("stats")) {
            return handleStatsCommand(sender, player, args);
        }
        if (args.length > 0 && isQuickBuyCommand(args[0])) {
            GameSession session = bedwarsManager.getActiveSession();
            if (session != null && session.isActive() && session.isParticipant(player.getUniqueId())) {
                player.sendMessage(Component.text("You cannot edit Quick Buy while in a BedWars match.", NamedTextColor.RED));
                return true;
            }
            bedwarsManager.openQuickBuyEditor(player);
            return true;
        }
        boolean temporaryCreator = bedwarsManager.isTemporaryCreator(player.getUniqueId());
        if (args.length > 0 && isCreatorCommand(args[0])) {
            return handleCreatorCommand(sender, player, args);
        }
        if (!player.isOp() && !temporaryCreator) {
            sender.sendMessage(Component.text("Only OP can use this command (except /bw stats and /bw quick-buy).", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("omgames.bw.start")
                && !sender.hasPermission("omgames.bw.setup")
                && !sender.hasPermission("omgames.bw.reload")
                && !temporaryCreator) {
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
        if (args[0].equalsIgnoreCase("test")) {
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2 || !args[1].equalsIgnoreCase("start")) {
                sender.sendMessage(Component.text("Usage: /bw test start", NamedTextColor.YELLOW));
                return true;
            }
            bedwarsManager.openMapSelect(player, false);
            return true;
        }
        if (args[0].equalsIgnoreCase("tp")) {
            if (!canUseMapSetupCommands(sender, player)) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /bw tp <arena>|lobby", NamedTextColor.YELLOW));
                return true;
            }
            if (args.length == 2 && args[1].equalsIgnoreCase("lobby")) {
                Location target = resolveBedwarsLobbyLocation();
                if (target == null) {
                    sender.sendMessage(Component.text("No BedWars world is loaded.", NamedTextColor.RED));
                    return true;
                }
                player.teleport(target);
                sender.sendMessage(Component.text("Teleported to BedWars lobby.", NamedTextColor.GREEN));
                return true;
            }
            String arenaName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
            if (arenaName.isBlank()) {
                sender.sendMessage(Component.text("Usage: /bw tp <arena>|lobby", NamedTextColor.YELLOW));
                return true;
            }
            Arena arena = findArena(arenaName);
            if (arena == null) {
                sender.sendMessage(Component.text("Arena not found: " + arenaName, NamedTextColor.RED));
                return true;
            }
            World world = arena.getWorld();
            if (world == null) {
                sender.sendMessage(Component.text("World not loaded: " + arena.getWorldName(), NamedTextColor.RED));
                return true;
            }
            Location target = resolveArenaLobbyLocation(arena);
            if (target == null && arena.getCenter() != null) {
                target = arena.getCenter().toLocation(world);
            }
            if (target == null) {
                sender.sendMessage(Component.text("Arena teleport location not set: " + arena.getId(), NamedTextColor.RED));
                return true;
            }
            player.teleport(target);
            sender.sendMessage(Component.text("Teleported to " + arena.getId() + " in " + world.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("lobby")) {
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            return handleLobbyParkourCommand(sender, player, args);
        }
        if (args[0].equalsIgnoreCase("out")) {
            sender.sendMessage(Component.text("Use /bw game out [player].", NamedTextColor.YELLOW));
            return true;
        }
        if (args[0].equalsIgnoreCase("game")) {
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (!player.isOp()) {
                sender.sendMessage(Component.text("Only OP can use /bw game commands.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /bw game out [player] | /bw game join <team|spectate> [player] | /bw game spectate [player] | /bw game revive <team> | /bw game skipphase", NamedTextColor.YELLOW));
                return true;
            }
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("out")) {
                return handleGameOut(sender, player, Arrays.copyOfRange(args, 1, args.length));
            }
            if (action.equals("spectate")) {
                return handleGameSpectate(sender, player, Arrays.copyOfRange(args, 1, args.length));
            }
            if (action.equals("join")) {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /bw game join <team|spectate> [player]", NamedTextColor.YELLOW));
                    return true;
                }
                if (args[2].equalsIgnoreCase("spectate")) {
                    return handleGameSpectate(sender, player, Arrays.copyOfRange(args, 2, args.length));
                }
                TeamColor team = TeamColor.fromKey(args[2]);
                if (team == null) {
                    sender.sendMessage(Component.text("Unknown team: " + args[2], NamedTextColor.RED));
                    return true;
                }
                Player target = args.length >= 4 ? Bukkit.getPlayer(args[3]) : player;
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                GameSession session = bedwarsManager.getActiveSession();
                if (session == null || !session.isActive()) {
                    sender.sendMessage(Component.text("No active BedWars session.", NamedTextColor.RED));
                    return true;
                }
                if (!session.forceJoin(target, team)) {
                    sender.sendMessage(Component.text("Could not add player to team " + team.displayName() + ".", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("Added " + target.getName() + " to " + team.displayName() + ".", NamedTextColor.GREEN));
                if (target != player) {
                    target.sendMessage(Component.text("You were added to " + team.displayName() + ".", NamedTextColor.GREEN));
                }
                return true;
            }
            if (action.equals("revive")) {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /bw game revive <team>", NamedTextColor.YELLOW));
                    return true;
                }
                TeamColor team = TeamColor.fromKey(args[2]);
                if (team == null) {
                    sender.sendMessage(Component.text("Unknown team: " + args[2], NamedTextColor.RED));
                    return true;
                }
                GameSession session = bedwarsManager.getActiveSession();
                if (session == null || !session.isActive()) {
                    sender.sendMessage(Component.text("No active BedWars session.", NamedTextColor.RED));
                    return true;
                }
                if (!session.reviveBed(team)) {
                    sender.sendMessage(Component.text("Could not revive " + team.displayName() + " bed.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text(team.displayName() + " bed revived.", NamedTextColor.GREEN));
                return true;
            }
            if (action.equals("skipphase")) {
                GameSession session = bedwarsManager.getActiveSession();
                if (session == null || !session.isActive()) {
                    sender.sendMessage(Component.text("No active BedWars session.", NamedTextColor.RED));
                    return true;
                }
                String phase = session.skipNextPhase();
                if (phase == null) {
                    sender.sendMessage(Component.text("No phase to skip.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("Skipped to " + phase + ".", NamedTextColor.GREEN));
                return true;
            }
            sender.sendMessage(Component.text("Usage: /bw game out [player] | /bw game join <team|spectate> [player] | /bw game spectate [player] | /bw game revive <team> | /bw game skipphase", NamedTextColor.YELLOW));
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
            if (!canUseMapSetupCommands(sender, player)) {
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

        sender.sendMessage(Component.text("Usage: /bw start | /bw test start | /bw stop | /bw tp <arena>|lobby | /bw lobby parkour <start|checkpoint [x]|end> | /bw game out [player] | /bw game join <team|spectate> [player] | /bw game spectate [player] | /bw game revive <team> | /bw quick_buy | /bw stats [user] | /bw stats modify <user> <stat|all> <+|-|set|+1|-1> [amount] | /bw reload | /bw setup new <arena> | /bw setup <arena> [key] | /bw creator add <user> | /bw creator remove <user>", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("start", "test", "stop", "tp", "lobby", "game", "quick_buy", "stats", "reload", "setup", "creator")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && isCreatorCommand(args[0])) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("add", "remove")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 3 && isCreatorCommand(args[0])) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.concat(
                            Stream.of("modify"),
                            Bukkit.getOnlinePlayers().stream().map(Player::getName))
                    .distinct()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("stats") && args[1].equalsIgnoreCase("modify")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("stats") && args[1].equalsIgnoreCase("modify")) {
            String input = args[3].toLowerCase(Locale.ROOT);
            return bedwarsManager.getStatsService().getModifiableStatKeys().stream()
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("stats") && args[1].equalsIgnoreCase("modify")) {
            String input = args[4].toLowerCase(Locale.ROOT);
            return Stream.of("+", "-", "set", "+1", "-1", "add", "remove")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("stats") && args[1].equalsIgnoreCase("modify")) {
            String input = args[5].toLowerCase(Locale.ROOT);
            return Stream.of("1", "5", "10", "50", "100")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lobby")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("parkour")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("lobby")
                && args[1].equalsIgnoreCase("parkour")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Stream.of("start", "checkpoint", "end")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 4
                && args[0].equalsIgnoreCase("lobby")
                && args[1].equalsIgnoreCase("parkour")
                && args[2].equalsIgnoreCase("checkpoint")) {
            String input = args[3].toLowerCase(Locale.ROOT);
            return Stream.of("1", "2", "3", "4", "5", "6", "7", "8")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("start")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("game")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("out", "join", "spectate", "revive", "skipphase")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("join")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Stream.concat(
                            Stream.of("spectate"),
                            Stream.of(TeamColor.values()).map(TeamColor::key))
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("join")) {
            String input = args[3].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("spectate")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("revive")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Stream.of(TeamColor.values())
                    .map(TeamColor::key)
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("out")) {
            String input = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            List<String> options = new java.util.ArrayList<>();
            options.add("lobby");
            bedwarsManager.getArenas().forEach(arena -> options.add(arena.getId()));
            return options.stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
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

    private boolean handleCreatorCommand(CommandSender sender, Player player, String[] args) {
        if (!player.isOp()) {
            sender.sendMessage(Component.text("Only OP can use /bw creator.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("omgames.bw.setup") && !sender.hasPermission("omgames.bw.start")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /bw creator add <user> | /bw creator remove <user>", NamedTextColor.YELLOW));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = resolveKnownPlayer(args[2]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
            return true;
        }
        String targetName = resolvePlayerName(target, args[2]);
        if (action.equals("add")) {
            if (!bedwarsManager.addTemporaryCreator(target.getUniqueId())) {
                sender.sendMessage(Component.text(targetName + " already has temporary creator access.", NamedTextColor.YELLOW));
                return true;
            }
            sender.sendMessage(Component.text("Granted temporary BedWars creator access to " + targetName + " until restart.", NamedTextColor.GREEN));
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage(Component.text("You can now use /bw setup and /bw tp until restart.", NamedTextColor.GREEN));
            }
            return true;
        }
        if (action.equals("remove")) {
            if (!bedwarsManager.removeTemporaryCreator(target.getUniqueId())) {
                sender.sendMessage(Component.text(targetName + " does not have temporary creator access.", NamedTextColor.YELLOW));
                return true;
            }
            sender.sendMessage(Component.text("Removed temporary BedWars creator access from " + targetName + ".", NamedTextColor.GREEN));
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage(Component.text("Your temporary BedWars creator access was removed.", NamedTextColor.YELLOW));
            }
            return true;
        }
        sender.sendMessage(Component.text("Usage: /bw creator add <user> | /bw creator remove <user>", NamedTextColor.YELLOW));
        return true;
    }

    private boolean canUseMapSetupCommands(CommandSender sender, Player player) {
        return bedwarsManager.isTemporaryCreator(player.getUniqueId())
                || sender.hasPermission("omgames.bw.setup")
                || sender.hasPermission("omgames.bw.start");
    }

    private boolean isCreatorCommand(String command) {
        return command != null
                && (command.equalsIgnoreCase("creater") || command.equalsIgnoreCase("creator"));
    }

    private OfflinePlayer resolveKnownPlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(player -> player.getName() != null && player.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private String resolvePlayerName(OfflinePlayer player, String fallback) {
        if (player != null && player.getName() != null && !player.getName().isBlank()) {
            return player.getName();
        }
        return fallback;
    }

    private Arena findArena(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (Arena arena : bedwarsManager.getArenas()) {
            if (arena.getId().equalsIgnoreCase(trimmed)) {
                return arena;
            }
        }
        return bedwarsManager.getArena(trimmed);
    }

    private boolean handleStatsCommand(CommandSender sender, Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("modify")) {
            return handleStatsModifyCommand(sender, player, args);
        }
        if (args.length > 2) {
            sender.sendMessage(Component.text("Usage: /bw stats [user] | /bw stats modify <user> <stat|all> <+|-|set|+1|-1> [amount]",
                    NamedTextColor.YELLOW));
            return true;
        }
        OfflinePlayer target = player;
        String targetName = player.getName();
        if (args.length == 2) {
            target = resolveKnownPlayer(args[1]);
            if (target == null || target.getUniqueId() == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
            targetName = resolvePlayerName(target, args[1]);
        }
        BedwarsPlayerStats stats = bedwarsManager.getStatsService().getStats(target.getUniqueId());
        sendStatsView(player, targetName, stats);
        return true;
    }

    private boolean handleStatsModifyCommand(CommandSender sender, Player player, String[] args) {
        if (!player.isOp()) {
            sender.sendMessage(Component.text("Only OP can use /bw stats modify.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(Component.text("Usage: /bw stats modify <user> <stat|all> <+|-|set|+1|-1> [amount]",
                    NamedTextColor.YELLOW));
            return true;
        }
        String targetName = args[2];
        OfflinePlayer target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(targetName);
        }
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return true;
        }
        ParsedStatOperation parsed = parseStatOperation(args[4], args.length >= 6 ? args[5] : null);
        if (parsed == null) {
            sender.sendMessage(Component.text("Usage: /bw stats modify <user> <stat|all> <+|-|set|+1|-1> [amount]",
                    NamedTextColor.YELLOW));
            return true;
        }
        List<BedwarsStatsService.StatChange> changes = bedwarsManager.getStatsService()
                .modifyStats(target.getUniqueId(), args[3], parsed.operation(), parsed.amount());
        if (changes.isEmpty()) {
            String supported = String.join(", ", bedwarsManager.getStatsService().getModifiableStatKeys());
            sender.sendMessage(Component.text("Unknown stat. Available: " + supported, NamedTextColor.RED));
            return true;
        }
        String summary = changes.stream()
                .map(change -> change.statKey() + ": " + change.before() + " -> " + change.after())
                .collect(Collectors.joining(", "));
        String targetDisplayName = target.getName() != null ? target.getName() : targetName;
        sender.sendMessage(Component.text("Updated stats for " + targetDisplayName + ": " + summary, NamedTextColor.GREEN));
        if (target.isOnline() && target.getPlayer() != null && target.getPlayer() != player) {
            target.getPlayer().sendMessage(Component.text("Your BedWars stats were updated by " + player.getName() + ".",
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    private ParsedStatOperation parseStatOperation(String operationToken, String amountToken) {
        if (operationToken == null || operationToken.isBlank()) {
            return null;
        }
        String token = operationToken.trim().toLowerCase(Locale.ROOT);
        if (token.equals("set") || token.equals("=")) {
            Long amount = parseLong(amountToken);
            if (amount == null) {
                return null;
            }
            return new ParsedStatOperation(BedwarsStatsService.StatOperation.SET, amount);
        }
        if (token.equals("+") || token.equals("add") || token.equals("plus")) {
            Long amount = amountToken == null ? 1L : parseLong(amountToken);
            if (amount == null) {
                return null;
            }
            return new ParsedStatOperation(BedwarsStatsService.StatOperation.ADD, safeAbs(amount));
        }
        if (token.equals("-") || token.equals("remove") || token.equals("sub") || token.equals("subtract")) {
            Long amount = amountToken == null ? 1L : parseLong(amountToken);
            if (amount == null) {
                return null;
            }
            return new ParsedStatOperation(BedwarsStatsService.StatOperation.SUBTRACT, safeAbs(amount));
        }
        if ((token.startsWith("+") || token.startsWith("-")) && token.length() > 1) {
            BedwarsStatsService.StatOperation op = token.startsWith("-")
                    ? BedwarsStatsService.StatOperation.SUBTRACT
                    : BedwarsStatsService.StatOperation.ADD;
            Long amount = parseLong(token.substring(1));
            if (amount == null) {
                return null;
            }
            if (amountToken != null) {
                Long override = parseLong(amountToken);
                if (override == null) {
                    return null;
                }
                amount = override;
            }
            return new ParsedStatOperation(op, safeAbs(amount));
        }
        return null;
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void sendStatsView(Player viewer, String targetName, BedwarsPlayerStats stats) {
        String bestTime = stats.getParkourBestTimeMillis() > 0L ? formatElapsed(stats.getParkourBestTimeMillis()) : "N/A";
        viewer.sendMessage(Component.text("BedWars Stats: " + targetName, NamedTextColor.GOLD));
        viewer.sendMessage(Component.text("Wins: " + stats.getWins(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Kills: " + stats.getKills(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Deaths: " + stats.getDeaths(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("KDR: " + formatRatio(stats.getKillDeathRatio()), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Final Kills: " + stats.getFinalKills(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Final Deaths: " + stats.getFinalDeaths(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("FKDR: " + formatRatio(stats.getFinalKillDeathRatio()), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Games Played: " + stats.getGamesPlayed(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Beds Broken: " + stats.getBedsBroken(), NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Parkour Best Time: " + bestTime, NamedTextColor.YELLOW));
        viewer.sendMessage(Component.text("Parkour Best Checkpoint Uses: " + stats.getParkourBestCheckpointUses(), NamedTextColor.YELLOW));
    }

    private long safeAbs(long value) {
        if (value == Long.MIN_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.abs(value);
    }

    private String formatElapsed(long elapsedMillis) {
        long millis = Math.max(0L, elapsedMillis);
        long minutes = millis / 60_000L;
        long seconds = (millis % 60_000L) / 1_000L;
        long ms = millis % 1_000L;
        if (minutes > 0L) {
            return String.format(Locale.ROOT, "%d:%02d.%03d", minutes, seconds, ms);
        }
        return String.format(Locale.ROOT, "%d.%03ds", seconds, ms);
    }

    private String formatRatio(double ratio) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0, ratio));
    }

    private record ParsedStatOperation(BedwarsStatsService.StatOperation operation, long amount) {
    }

    private boolean handleGameOut(CommandSender sender, Player caller, String[] args) {
        Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : caller;
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        GameSession session = bedwarsManager.getActiveSession();
        if (session == null || !session.isActive()) {
            sender.sendMessage(Component.text("No active BedWars session.", NamedTextColor.RED));
            return true;
        }
        session.removeLockedCommandSpectator(target.getUniqueId());
        session.removeParticipant(target);
        session.addEditor(target);
        target.setGameMode(GameMode.CREATIVE);
        target.setAllowFlight(true);
        target.setFlying(true);
        Location editorLobby = resolveArenaLobbyLocation(session.getArena());
        if (editorLobby != null) {
            target.teleport(editorLobby);
        }
        sender.sendMessage(Component.text("Removed " + target.getName() + " from the match.", NamedTextColor.YELLOW));
        if (target != caller) {
            target.sendMessage(Component.text("You were removed from the match.", NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean handleGameSpectate(CommandSender sender, Player caller, String[] args) {
        Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : caller;
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        GameSession session = bedwarsManager.getActiveSession();
        if (session == null || !session.isActive()) {
            sender.sendMessage(Component.text("No active BedWars session.", NamedTextColor.RED));
            return true;
        }
        if (!session.isInArenaWorld(caller.getWorld())) {
            sender.sendMessage(Component.text("You must be in the active BedWars world to use /bw game spectate.", NamedTextColor.RED));
            return true;
        }
        session.removeEditor(target);
        session.removeParticipant(target);
        session.addLockedCommandSpectator(target.getUniqueId());
        target.getInventory().clear();
        target.setGameMode(GameMode.SPECTATOR);
        Location spectate = resolveArenaLobbyLocation(session.getArena());
        if (spectate != null) {
            target.teleport(spectate);
        }
        session.refreshSidebar(target);
        sender.sendMessage(Component.text("Set " + target.getName() + " to spectator.", NamedTextColor.YELLOW));
        if (target != caller) {
            target.sendMessage(Component.text("You were set to spectator.", NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean isQuickBuyCommand(String arg) {
        if (arg == null) {
            return false;
        }
        return arg.equalsIgnoreCase("quick_buy")
                || arg.equalsIgnoreCase("quick-buy")
                || arg.equalsIgnoreCase("quck-buy")
                || arg.equalsIgnoreCase("quck_buy");
    }

    private Location resolveBedwarsLobbyLocation() {
        GameSession session = bedwarsManager.getActiveSession();
        if (session != null && session.getArena() != null) {
            World activeWorld = session.getArena().getWorld();
            if (activeWorld != null) {
                return new Location(activeWorld, 0.0, 73.0, 0.0);
            }
        }
        World bedwarsWorld = Bukkit.getWorld("bedwars");
        if (bedwarsWorld != null) {
            return new Location(bedwarsWorld, 0.0, 73.0, 0.0);
        }
        for (Arena arena : bedwarsManager.getArenas()) {
            World world = arena.getWorld();
            if (world != null) {
                return new Location(world, 0.0, 73.0, 0.0);
            }
        }
        return null;
    }

    private Location resolveArenaLobbyLocation(Arena arena) {
        if (arena == null) {
            return null;
        }
        Location mapLobby = arena.getMapLobbyLocation();
        if (mapLobby != null) {
            return mapLobby;
        }
        return arena.getLobbyLocation();
    }

    private boolean handleLobbyParkourCommand(CommandSender sender, Player player, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("parkour")) {
            sender.sendMessage(Component.text("Usage: /bw lobby parkour <start|checkpoint [x]|end>", NamedTextColor.YELLOW));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /bw lobby parkour <start|checkpoint [x]|end>", NamedTextColor.YELLOW));
            return true;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        String message;
        if (action.equals("start")) {
            message = bedwarsManager.getLobbyParkour().setStartPlate(player);
        } else if (action.equals("end")) {
            message = bedwarsManager.getLobbyParkour().setEndPlate(player);
        } else if (action.equals("checkpoint")) {
            Integer checkpoint = null;
            if (args.length >= 4) {
                try {
                    checkpoint = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Checkpoint index must be a number.", NamedTextColor.RED));
                    return true;
                }
                if (checkpoint <= 0) {
                    sender.sendMessage(Component.text("Checkpoint index must be greater than 0.", NamedTextColor.RED));
                    return true;
                }
            }
            message = bedwarsManager.getLobbyParkour().setCheckpointPlate(player, checkpoint);
        } else {
            sender.sendMessage(Component.text("Usage: /bw lobby parkour <start|checkpoint [x]|end>", NamedTextColor.YELLOW));
            return true;
        }
        NamedTextColor color = message.toLowerCase(Locale.ROOT).startsWith("parkour")
                ? NamedTextColor.GREEN
                : NamedTextColor.RED;
        sender.sendMessage(Component.text(message, color));
        return true;
    }
}
