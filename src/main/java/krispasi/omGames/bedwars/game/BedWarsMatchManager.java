package krispasi.omGames.bedwars.game;

import krispasi.omGames.bedwars.config.BedWarsConfigService;
import krispasi.omGames.bedwars.model.BaseGeneratorSettings;
import krispasi.omGames.bedwars.model.BedWarsMap;
import krispasi.omGames.bedwars.model.GeneratorSettings;
import krispasi.omGames.bedwars.model.TeamConfig;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BedWarsMatchManager {
    public enum Mode { SOLO, DOUBLES }

    private final Plugin plugin;
    private final BedWarsConfigService configService;
    private BedWarsMatch activeMatch;

    public BedWarsMatchManager(Plugin plugin, BedWarsConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    public synchronized boolean startMatch(BedWarsMap map, Mode mode, Collection<Player> pool) {
        return startMatch(map, mode, pool, null);
    }

    public synchronized boolean startMatch(BedWarsMap map, Mode mode, Collection<Player> pool, Map<UUID, String> manualTeams) {
        if (map == null || pool == null) return false;
        if (activeMatch != null) {
            plugin.getLogger().warning("A BedWars match is already running. Stop it first.");
            return false;
        }

        List<Player> players = pool.stream()
                .filter(p -> p.getWorld().getName().equalsIgnoreCase(map.getWorldName()))
                .collect(Collectors.toList());
        if (players.isEmpty()) {
            plugin.getLogger().warning("No players available in the BedWars world to start the match.");
            return false;
        }

        try {
            activeMatch = new BedWarsMatch(map, mode, players, manualTeams);
            activeMatch.begin();
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not start BedWars: " + ex.getMessage());
            activeMatch = null;
            return false;
        }
    }

    public synchronized void stopMatch(String reason) {
        if (activeMatch == null) return;
        try {
            activeMatch.end(reason);
        } catch (Exception ex) {
            plugin.getLogger().warning("Error while stopping BedWars: " + ex.getMessage());
        } finally {
            activeMatch = null;
        }
    }

    public synchronized BedWarsMatch getActiveMatch() {
        return activeMatch;
    }

    public boolean isRunning() {
        return activeMatch != null;
    }

    public Mode parseMode(String raw) {
        if (raw == null) return Mode.SOLO;
        if (raw.equalsIgnoreCase("doubles")) return Mode.DOUBLES;
        return Mode.SOLO;
    }

    public class BedWarsMatch {
        private final BedWarsMap map;
        private final Mode mode;
        private final Map<String, TeamState> teams = new HashMap<>();
        private final Map<UUID, String> playerTeams = new HashMap<>();
        private final Set<Location> placedBlocks = ConcurrentHashMap.newKeySet();
        private final List<BlockState> bedSnapshots = new ArrayList<>();
        private final List<BukkitTask> runningTasks = new ArrayList<>();
        private final Map<String, BukkitTask> baseGenerators = new HashMap<>();
        private BukkitTask diamondTask;
        private BukkitTask emeraldTask;

        public BedWarsMatch(BedWarsMap map, Mode mode, List<Player> players, Map<UUID, String> manualAssignments) {
            this.map = map;
            this.mode = mode;
            prepareTeams(players, manualAssignments);
        }

        public void begin() {
            plugin.getLogger().info("Starting BedWars on map " + map.getName() + " with mode " + mode);

            World world = Bukkit.getWorld(map.getWorldName());
            if (world == null) {
                throw new IllegalStateException("BedWars world not loaded: " + map.getWorldName());
            }

            retargetWorld(world);

            equipPlayers();
            setupBeds();
            startGenerators();
        }

        public void end(String reason) {
            cancelTasks();

            for (UUID id : new HashSet<>(playerTeams.keySet())) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                try {
                    p.setGameMode(GameMode.SURVIVAL);
                    p.getInventory().clear();
                    Location lobby = map.getCenter().clone();
                    lobby.setY(map.getLobbyHeight());
                    p.teleport(lobby);
                    p.sendMessage(ChatColor.YELLOW + "BedWars ended" + (reason != null ? ": " + reason : ""));
                } catch (Exception ignored) {
                }
            }

            placedBlocks.forEach(loc -> {
                try {
                    Block block = loc.getBlock();
                    block.setType(Material.AIR, false);
                } catch (Exception ignored) {
                }
            });
            placedBlocks.clear();

            for (BlockState state : bedSnapshots) {
                try {
                    state.update(true, false);
                } catch (Exception ignored) {
                }
            }
            bedSnapshots.clear();
            teams.clear();
            playerTeams.clear();
        }

        private void retargetWorld(World world) {
            try {
                if (map.getCenter().getWorld() == null) {
                    map.getCenter().setWorld(world);
                }
            } catch (Exception ignored) {
            }

            for (TeamState team : teams.values()) {
                try {
                    if (team.getSpawn().getWorld() == null) team.getSpawn().setWorld(world);
                    if (team.getBed().getWorld() == null) team.getBed().setWorld(world);
                    if (team.getGenerator().getWorld() == null) team.getGenerator().setWorld(world);
                } catch (Exception ignored) {
                }
            }

            try {
                map.getDiamondGens().forEach(loc -> { if (loc.getWorld() == null) loc.setWorld(world); });
                map.getEmeraldGens().forEach(loc -> { if (loc.getWorld() == null) loc.setWorld(world); });
            } catch (Exception ignored) {
            }
        }

        private void prepareTeams(List<Player> players, Map<UUID, String> manualAssignments) {
            List<TeamConfig> configs = new ArrayList<>(map.getTeams().values());
            if (configs.isEmpty()) throw new IllegalStateException("Map has no team setups");

            for (TeamConfig cfg : configs) {
                teams.put(cfg.getName().toLowerCase(), new TeamState(cfg));
            }

            int perTeam = mode == Mode.DOUBLES ? 2 : 1;
            int capacity = perTeam * teams.size();

            // apply manual assignments first so admins get the exact teams they picked
            if (manualAssignments != null && !manualAssignments.isEmpty()) {
                for (Map.Entry<UUID, String> entry : manualAssignments.entrySet()) {
                    if (playerTeams.containsKey(entry.getKey())) continue;
                    TeamState team = teams.get(entry.getValue());
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (team == null || player == null) continue;
                    if (team.getMembers().size() >= perTeam) continue; // don't overfill
                    team.addMember(entry.getKey());
                    playerTeams.put(entry.getKey(), team.getName());
                }
            }

            Collections.shuffle(players);
            int teamIndex = 0;
            List<TeamState> roster = new ArrayList<>(teams.values());
            int assigned = 0;
            for (Player player : players) {
                if (assigned >= capacity) break; // too many players for this mode
                if (playerTeams.containsKey(player.getUniqueId())) continue;
                if (roster.isEmpty()) break;
                TeamState team = roster.get(teamIndex % roster.size());
                if (team.getMembers().size() >= perTeam) {
                    teamIndex++;
                    team = roster.get(teamIndex % roster.size());
                }
                team.addMember(player.getUniqueId());
                playerTeams.put(player.getUniqueId(), team.getName());
                assigned++;
                if (team.getMembers().size() >= perTeam) teamIndex++;
            }
        }

        private void equipPlayers() {
            for (Map.Entry<UUID, String> entry : playerTeams.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                TeamState team = teams.get(entry.getValue());
                if (player == null || team == null) continue;
                try {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.getInventory().clear();
                    player.setHealth(20.0);
                    player.setFoodLevel(20);
                    player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                    giveLeatherArmor(player, team.getDyeColor());
                    player.teleport(team.getSpawn());
                } catch (Exception ignored) {
                }
            }
        }

        private void giveLeatherArmor(Player player, DyeColor color) {
            ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
            ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
            ItemStack[] pieces = new ItemStack[]{helmet, chest, legs, boots};
            for (ItemStack piece : pieces) {
                LeatherArmorMeta meta = (LeatherArmorMeta) piece.getItemMeta();
                if (meta != null) {
                    meta.setColor(color.getColor());
                    meta.setUnbreakable(true);
                    piece.setItemMeta(meta);
                }
            }
            player.getInventory().setArmorContents(new ItemStack[]{boots, legs, chest, helmet});
        }

        private void setupBeds() {
            for (TeamState team : teams.values()) {
                try {
                    Block bedBlock = team.getBed().getBlock();
                    bedSnapshots.add(bedBlock.getState());
                    Location partner = findSecondBedBlock(bedBlock.getLocation());
                    if (partner != null) {
                        bedSnapshots.add(partner.getBlock().getState());
                        team.setBedPartner(partner);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        private Location findSecondBedBlock(Location primary) {
            try {
                Block base = primary.getBlock();
                for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                    Block neighbor = base.getRelative(face);
                    if (neighbor.getType().name().endsWith("_BED")) {
                        return neighbor.getLocation();
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        private void startGenerators() {
            BaseGeneratorSettings baseSettings = configService.getBaseGeneratorSettings();
            GeneratorSettings diamondSettings = configService.getDiamondSettings();
            GeneratorSettings emeraldSettings = configService.getEmeraldSettings();

            for (TeamState team : teams.values()) {
                if (team.getMembers().isEmpty()) continue;
                BukkitTask task = startBaseGenerator(team, baseSettings);
                if (task != null) baseGenerators.put(team.getName(), task);
            }

            diamondTask = startSharedGenerator(map.getDiamondGens(), Material.DIAMOND, diamondSettings);
            emeraldTask = startSharedGenerator(map.getEmeraldGens(), Material.EMERALD, emeraldSettings);
        }

        private BukkitTask startBaseGenerator(TeamState team, BaseGeneratorSettings settings) {
            long[] ironTicks = new long[]{settings.getTier1IronInterval(), settings.getTier2IronInterval(), settings.getTier3IronInterval()};
            long[] goldTicks = new long[]{settings.getTier1GoldInterval(), settings.getTier2GoldInterval(), settings.getTier3GoldInterval()};
            final BukkitTask[] holder = new BukkitTask[1];
            int[] tier = new int[]{0};

            Runnable restart = () -> {
                try {
                    if (holder[0] != null) holder[0].cancel();
                } catch (Exception ignored) {
                }

                holder[0] = new BukkitRunnable() {
                    private long tick = 0;
                    @Override
                    public void run() {
                        tick += ironTicks[tier[0]];
                        if (!team.isAlive() || team.getMembers().isEmpty()) return;
                        try {
                            World world = team.getGenerator().getWorld();
                            world.dropItemNaturally(team.getGenerator(), new ItemStack(Material.IRON_INGOT));
                            if (tick % goldTicks[tier[0]] == 0) {
                                world.dropItemNaturally(team.getGenerator(), new ItemStack(Material.GOLD_INGOT));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }.runTaskTimer(plugin, 0L, Math.max(1L, ironTicks[tier[0]]));
                runningTasks.add(holder[0]);
            };

            restart.run();

            scheduleUpgrade(settings.getTier2UpgradeSeconds(), () -> { tier[0] = 1; restart.run(); });
            scheduleUpgrade(settings.getTier3UpgradeSeconds(), () -> { tier[0] = 2; restart.run(); });
            return holder[0];
        }

        private void scheduleUpgrade(long seconds, Runnable action) {
            if (seconds <= 0) return;
            BukkitTask upgrade = new BukkitRunnable() {
                @Override
                public void run() {
                    try { action.run(); } catch (Exception ignored) {}
                }
            }.runTaskLater(plugin, seconds * 20L);
            runningTasks.add(upgrade);
        }

        private BukkitTask startSharedGenerator(Set<Location> spots, Material drop, GeneratorSettings settings) {
            if (spots.isEmpty()) return null;
            long[] intervals = new long[]{settings.getTier1Interval(), settings.getTier2Interval(), settings.getTier3Interval()};
            final BukkitTask[] holder = new BukkitTask[1];

            Runnable restart = () -> {
                try {
                    if (holder[0] != null) holder[0].cancel();
                } catch (Exception ignored) {
                }
                holder[0] = new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Location loc : spots) {
                            try {
                                loc.getWorld().dropItemNaturally(loc, new ItemStack(drop));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, Math.max(1L, intervals[0]));
                runningTasks.add(holder[0]);
            };

            restart.run();
            scheduleUpgrade(settings.getTier2UpgradeSeconds(), () -> { intervals[0] = settings.getTier2Interval(); restart.run(); });
            scheduleUpgrade(settings.getTier3UpgradeSeconds(), () -> { intervals[0] = settings.getTier3Interval(); restart.run(); });
            return holder[0];
        }

        private void cancelTasks() {
            try {
                baseGenerators.values().forEach(task -> { if (task != null) task.cancel(); });
            } catch (Exception ignored) {
            }
            baseGenerators.clear();

            try { if (diamondTask != null) diamondTask.cancel(); } catch (Exception ignored) {}
            try { if (emeraldTask != null) emeraldTask.cancel(); } catch (Exception ignored) {}

            try {
                runningTasks.forEach(task -> { if (task != null) task.cancel(); });
            } catch (Exception ignored) {
            }
            runningTasks.clear();
        }

        public void recordPlacement(Location location) {
            placedBlocks.add(location);
        }

        public boolean canBreak(Player player, Block block) {
            if (block == null) return false;
            if (!block.getWorld().getName().equalsIgnoreCase(map.getWorldName())) return false;

            if (block.getType().name().endsWith("_BED")) {
                handleBedBreak(player, block.getLocation());
                return true;
            }

            return placedBlocks.contains(block.getLocation());
        }

        private void handleBedBreak(Player player, Location location) {
            for (TeamState team : teams.values()) {
                if (!team.isBedBlock(location)) continue;
                if (!team.isBedIntact()) return;
                team.setBedIntact(false);
                try {
                    Block main = team.getBed().getBlock();
                    bedSnapshots.add(main.getState());
                    main.setType(Material.AIR);
                    if (team.getBedPartner() != null) {
                        Block partner = team.getBedPartner().getBlock();
                        bedSnapshots.add(partner.getState());
                        partner.setType(Material.AIR);
                    }
                } catch (Exception ignored) {
                }
                Bukkit.broadcastMessage(ChatColor.RED + team.getName() + " bed destroyed by " + player.getName());
                return;
            }
        }

        public boolean canPlace(Location location) {
            if (location == null) return false;
            if (!location.getWorld().getName().equalsIgnoreCase(map.getWorldName())) return false;

            double baseRadiusSq = Math.pow(configService.getBaseGenRadius(), 2);
            double advRadiusSq = Math.pow(configService.getAdvancedGenRadius(), 2);

            for (TeamState team : teams.values()) {
                if (location.distanceSquared(team.getGenerator()) <= baseRadiusSq) return false;
            }
            for (Location diamond : map.getDiamondGens()) {
                if (location.distanceSquared(diamond) <= advRadiusSq) return false;
            }
            for (Location emerald : map.getEmeraldGens()) {
                if (location.distanceSquared(emerald) <= advRadiusSq) return false;
            }
            return true;
        }

        public void handleDeath(Player player) {
            String teamName = playerTeams.get(player.getUniqueId());
            if (teamName == null) return;
            TeamState team = teams.get(teamName);
            if (team == null) return;

            player.setGameMode(GameMode.SPECTATOR);
            Location hover = map.getCenter().clone();
            hover.setY(map.getLobbyHeight());
            player.teleport(hover);
            player.sendMessage(ChatColor.YELLOW + "Respawning in " + configService.getRespawnDelay() + "s...");

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!team.isBedIntact()) {
                        eliminate(player, team);
                        return;
                    }
                    try {
                        player.setGameMode(GameMode.SURVIVAL);
                        player.teleport(team.getSpawn());
                        player.getInventory().clear();
                        player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                        giveLeatherArmor(player, team.getDyeColor());
                    } catch (Exception ignored) {
                    }
                }
            }.runTaskLater(plugin, configService.getRespawnDelay() * 20L);
        }

        private void eliminate(Player player, TeamState team) {
            try {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(ChatColor.RED + "You are out. Your bed is gone.");
            } catch (Exception ignored) {
            }
            team.getMembers().remove(player.getUniqueId());
            playerTeams.remove(player.getUniqueId());
            checkWinCondition();
        }

        public void handleQuit(UUID playerId) {
            String teamName = playerTeams.remove(playerId);
            if (teamName == null) return;
            TeamState team = teams.get(teamName);
            if (team == null) return;
            team.getMembers().remove(playerId);
            checkWinCondition();
        }

        public boolean isFriendlyFire(Player damager, Entity target) {
            if (!(target instanceof Player victim)) return false;
            String a = playerTeams.get(damager.getUniqueId());
            String b = playerTeams.get(victim.getUniqueId());
            if (a == null || b == null) return false;
            return a.equalsIgnoreCase(b);
        }

        private void checkWinCondition() {
            List<TeamState> alive = teams.values().stream()
                    .filter(t -> !t.getMembers().isEmpty())
                    .toList();
            if (alive.size() <= 1) {
                TeamState winner = alive.isEmpty() ? null : alive.get(0);
                Bukkit.broadcastMessage(ChatColor.GREEN + "BedWars over! Winner: " + (winner != null ? winner.getName() : "None"));
                stopMatch("round complete");
            }
        }

        public boolean isBedWarsWorld(World world) {
            return world != null && world.getName().equalsIgnoreCase(map.getWorldName());
        }
    }

    public static class TeamState {
        private final TeamConfig config;
        private final Set<UUID> members = new HashSet<>();
        private boolean bedIntact = true;
        private Location bedPartner;

        public TeamState(TeamConfig config) {
            this.config = config;
        }

        public void addMember(UUID uuid) {
            members.add(uuid);
        }

        public String getName() {
            return config.getName();
        }

        public Location getSpawn() {
            return config.getSpawn();
        }

        public Location getBed() {
            return config.getBed();
        }

        public Location getGenerator() {
            return config.getGenerator();
        }

        public DyeColor getDyeColor() {
            try {
                return DyeColor.valueOf(config.getName().toUpperCase());
            } catch (Exception ignored) {
                return DyeColor.WHITE;
            }
        }

        public Set<UUID> getMembers() {
            return members;
        }

        public boolean isBedIntact() {
            return bedIntact;
        }

        public void setBedIntact(boolean bedIntact) {
            this.bedIntact = bedIntact;
        }

        public boolean isAlive() {
            return !members.isEmpty();
        }

        public void setBedPartner(Location bedPartner) {
            this.bedPartner = bedPartner;
        }

        public Location getBedPartner() {
            return bedPartner;
        }

        public boolean isBedBlock(Location loc) {
            if (loc == null) return false;
            return loc.equals(config.getBed()) || (bedPartner != null && bedPartner.equals(loc));
        }
    }
}
