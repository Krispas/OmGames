package krispasi.omGames.bedwars.command;

import java.util.Arrays;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.setup.BedwarsSetupManager;
import krispasi.omGames.bedwars.stats.BedwarsPlayerStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
            BedwarsPlayerStats stats = bedwarsManager.getStatsService().getStats(player.getUniqueId());
            player.sendMessage(Component.text("BedWars Stats", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Wins: " + stats.getWins(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Kills: " + stats.getKills(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Deaths: " + stats.getDeaths(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Final Kills: " + stats.getFinalKills(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Final Deaths: " + stats.getFinalDeaths(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Games Played: " + stats.getGamesPlayed(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Beds Broken: " + stats.getBedsBroken(), NamedTextColor.YELLOW));
            return true;
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
        if (!player.isOp()) {
            sender.sendMessage(Component.text("Only OP can use this command (except /bw stats and /bw quick-buy).", NamedTextColor.RED));
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
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /bw tp <arena>", NamedTextColor.YELLOW));
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
                sender.sendMessage(Component.text("Usage: /bw tp <arena>", NamedTextColor.YELLOW));
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
        if (args[0].equalsIgnoreCase("out")) {
            if (!sender.hasPermission("omgames.bw.start")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            return handleGameOut(sender, player, args);
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

        sender.sendMessage(Component.text("Usage: /bw start | /bw test start | /bw stop | /bw tp <arena>|lobby | /bw out [player] | /bw game out [player] | /bw game join <team|spectate> [player] | /bw game spectate [player] | /bw game revive <team> | /bw quick_buy | /bw stats | /bw reload | /bw setup new <arena> | /bw setup <arena> [key]", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("start", "test", "stop", "tp", "out", "game", "quick_buy", "stats", "reload", "setup")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("start")
                    .filter(option -> option.startsWith(input))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("out")) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
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
        session.removeParticipant(target);
        session.addEditor(target);
        target.setGameMode(GameMode.CREATIVE);
        target.setAllowFlight(true);
        target.setFlying(true);
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
        session.removeEditor(target);
        session.removeParticipant(target);
        target.getInventory().clear();
        target.setGameMode(GameMode.SPECTATOR);
        Location spectate = resolveArenaLobbyLocation(session.getArena());
        if (spectate != null) {
            target.teleport(spectate);
        }
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
            Location activeLobby = resolveArenaLobbyLocation(session.getArena());
            if (activeLobby != null) {
                return activeLobby;
            }
        }
        for (Arena arena : bedwarsManager.getArenas()) {
            Location lobby = resolveArenaLobbyLocation(arena);
            if (lobby != null) {
                return lobby;
            }
        }
        World bedwarsWorld = Bukkit.getWorld("bedwars");
        if (bedwarsWorld != null) {
            return new Location(bedwarsWorld, 0.0, 73.0, 0.0);
        }
        return null;
    }

    private Location resolveArenaLobbyLocation(Arena arena) {
        if (arena == null) {
            return null;
        }
        World world = arena.getWorld();
        if (world != null) {
            return new Location(world, 0.0, 73.0, 0.0);
        }
        Location mapLobby = arena.getMapLobbyLocation();
        if (mapLobby != null) {
            return mapLobby;
        }
        return arena.getLobbyLocation();
    }
}
