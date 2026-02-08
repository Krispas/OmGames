package krispasi.omGames.bedwars.game;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.generator.GeneratorInfo;
import krispasi.omGames.bedwars.generator.GeneratorManager;
import krispasi.omGames.bedwars.generator.GeneratorType;
import krispasi.omGames.bedwars.gui.ShopMenu;
import krispasi.omGames.bedwars.gui.UpgradeShopMenu;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.BedLocation;
import krispasi.omGames.bedwars.model.BedState;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.ShopLocation;
import krispasi.omGames.bedwars.model.ShopType;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopCost;
import krispasi.omGames.bedwars.shop.ShopCategoryType;
import krispasi.omGames.bedwars.shop.ShopItemBehavior;
import krispasi.omGames.bedwars.shop.ShopItemDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class GameSession {
    public static final String ITEM_SHOP_TAG = "bw_item_shop";
    public static final String UPGRADES_SHOP_TAG = "bw_upgrades_shop";
    private static final Set<Material> SWORD_MATERIALS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.DIAMOND_SWORD
    );
    private static final Set<Material> BOW_MATERIALS = EnumSet.of(Material.BOW);
    private static final Set<Material> PICKAXE_MATERIALS = EnumSet.of(
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE
    );
    private static final Set<Material> AXE_MATERIALS = EnumSet.of(
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE
    );
    private static final int START_COUNTDOWN_SECONDS = 5;
    private static final int RESPAWN_DELAY_SECONDS = 5;
    private static final int RESPAWN_PROTECTION_SECONDS = 5;
    private static final int TIER_1_DELAY = 360;
    private static final int TIER_2_DELAY = 720;
    private static final int TIER_3_DELAY = 1080;
    private static final int BED_DESTRUCTION_DELAY = 1440;
    private static final int SUDDEN_DEATH_DELAY = 1800;
    private static final int GAME_END_DELAY = 3000;

    private final BedwarsManager bedwarsManager;
    private final Arena arena;
    private final Map<UUID, TeamColor> assignments = new HashMap<>();
    private final Map<TeamColor, BedState> bedStates = new EnumMap<>(TeamColor.class);
    private final Map<BlockPoint, TeamColor> bedBlocks = new HashMap<>();
    private final Set<BlockPoint> placedBlocks = new HashSet<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<TeamColor> eliminatedTeams = new HashSet<>();
    private final Set<UUID> pendingRespawns = new HashSet<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnCountdownTasks = new HashMap<>();
    private final Map<UUID, Long> respawnProtectionEnds = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnProtectionTasks = new HashMap<>();
    private final Map<UUID, Inventory> fakeEnderChests = new HashMap<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private final Map<UUID, Scoreboard> activeScoreboards = new HashMap<>();
    private final Map<UUID, List<String>> sidebarLines = new HashMap<>();
    private final Set<TeamColor> teamsInMatch = EnumSet.noneOf(TeamColor.class);
    private final Set<UUID> shopNpcIds = new HashSet<>();
    private final Map<UUID, Integer> armorTiers = new HashMap<>();
    private final Map<UUID, Integer> pickaxeTiers = new HashMap<>();
    private final Map<UUID, Integer> axeTiers = new HashMap<>();
    private final Map<UUID, Integer> bowTiers = new HashMap<>();
    private final Set<UUID> shearsUnlocked = new HashSet<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private GeneratorManager generatorManager;
    private GameState state = GameState.IDLE;
    private JavaPlugin plugin;
    private BukkitTask sidebarTask;
    private long matchStartMillis;
    private int startCountdownRemaining;

    public GameSession(BedwarsManager bedwarsManager, Arena arena) {
        this.bedwarsManager = bedwarsManager;
        this.arena = arena;
    }

    public Arena getArena() {
        return arena;
    }

    public GameState getState() {
        return state;
    }

    public boolean isStarting() {
        return state == GameState.STARTING;
    }

    public boolean isRunning() {
        return state == GameState.RUNNING;
    }

    public boolean isActive() {
        return state == GameState.STARTING || state == GameState.RUNNING;
    }

    public void assignTeam(UUID playerId, TeamColor team) {
        if (team == null) {
            assignments.remove(playerId);
        } else {
            assignments.put(playerId, team);
        }
    }

    public TeamColor getTeam(UUID playerId) {
        return assignments.get(playerId);
    }

    public Map<UUID, TeamColor> getAssignments() {
        return Collections.unmodifiableMap(assignments);
    }

    public int getAssignedCount() {
        return assignments.size();
    }

    public boolean isParticipant(UUID playerId) {
        return assignments.containsKey(playerId);
    }

    public boolean isFrozen(UUID playerId) {
        return frozenPlayers.contains(playerId);
    }

    public boolean isInArenaWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(arena.getWorldName());
    }

    public BedState getBedState(TeamColor team) {
        return bedStates.getOrDefault(team, BedState.DESTROYED);
    }

    public TeamColor getBedOwner(BlockPoint point) {
        return bedBlocks.get(point);
    }

    public boolean isPlacedBlock(BlockPoint point) {
        return placedBlocks.contains(point);
    }

    public boolean isPlacementBlocked(BlockPoint point) {
        int baseRadius = arena.getBaseGeneratorRadius();
        int advancedRadius = arena.getAdvancedGeneratorRadius();
        if (baseRadius <= 0 && advancedRadius <= 0) {
            return false;
        }
        for (GeneratorInfo generator : arena.getGenerators()) {
            int radius = generator.type() == GeneratorType.BASE ? baseRadius : advancedRadius;
            if (radius <= 0) {
                continue;
            }
            if (isWithinRadius(point, generator.location(), radius)) {
                return true;
            }
        }
        return false;
    }

    public void recordPlacedBlock(BlockPoint point) {
        placedBlocks.add(point);
    }

    public void removePlacedBlock(BlockPoint point) {
        placedBlocks.remove(point);
    }

    public boolean isPendingRespawn(UUID playerId) {
        return pendingRespawns.contains(playerId);
    }

    public boolean isEliminated(UUID playerId) {
        return eliminatedPlayers.contains(playerId);
    }

    public void openFakeEnderChest(Player player) {
        Inventory inventory = fakeEnderChests.computeIfAbsent(player.getUniqueId(),
                id -> Bukkit.createInventory(player, 27, Component.text("Ender Chest")));
        player.openInventory(inventory);
    }

    public void openShop(Player player, ShopType type) {
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return;
        }
        if (type == ShopType.UPGRADES) {
            new UpgradeShopMenu().open(player);
            return;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            player.sendMessage(Component.text("Shop config is not loaded.", NamedTextColor.RED));
            return;
        }
        new ShopMenu(this, config, ShopCategoryType.QUICK_BUY, player).open(player);
    }

    public void handleShopPurchase(Player player, ShopItemDefinition item) {
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return;
        }
        ShopCost cost = item.getCost();
        if (cost == null || !cost.isValid() || !hasResources(player, cost)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (!applyPurchase(player, item)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        removeResources(player, cost);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    private boolean applyPurchase(Player player, ShopItemDefinition item) {
        TeamColor team = getTeam(player.getUniqueId());
        ShopItemBehavior behavior = item.getBehavior();
        return switch (behavior) {
            case BLOCK, UTILITY, POTION -> {
                giveItem(player, item.createPurchaseItem(team));
                yield true;
            }
            case SWORD -> {
                if (SWORD_MATERIALS.contains(item.getMaterial())) {
                    removeSwords(player);
                }
                giveItem(player, item.createPurchaseItem(team));
                yield true;
            }
            case BOW -> applyTierUpgrade(player, item, bowTiers, ShopItemBehavior.BOW);
            case ARMOR -> applyTierUpgrade(player, item, armorTiers, ShopItemBehavior.ARMOR);
            case PICKAXE -> applyTierUpgrade(player, item, pickaxeTiers, ShopItemBehavior.PICKAXE);
            case AXE -> applyTierUpgrade(player, item, axeTiers, ShopItemBehavior.AXE);
            case SHEARS -> applyShears(player, item, team);
        };
    }

    public void start(JavaPlugin plugin, Player initiator) {
        this.plugin = plugin;
        stopInternal();
        resetState();
        state = GameState.STARTING;
        updateTeamsInMatch();
        initializeBeds();
        applyBedLayout();
        spawnShops();
        startCountdownRemaining = START_COUNTDOWN_SECONDS;

        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            TeamColor team = entry.getValue();
            if (player == null || team == null) {
                continue;
            }
            Location spawn = arena.getSpawn(team);
            if (spawn == null) {
                initiator.sendMessage(Component.text("Missing spawn for team " + team.displayName() + ".", NamedTextColor.RED));
                continue;
            }
            player.teleport(spawn);
            player.getInventory().clear();
            giveStarterKit(player, team);
            applyPermanentItems(player, team);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            frozenPlayers.add(player.getUniqueId());
            player.sendMessage(Component.text("You are on the ").append(team.displayComponent()).append(Component.text(" team.")));
        }

        startCountdown();
        startSidebarUpdates();
    }

    public void stop() {
        stopInternal();
        cleanupWorld();
        resetState();
        state = GameState.IDLE;
    }

    public void handlePlayerDeath(Player player) {
        if (!isRunning() || !isParticipant(player.getUniqueId())) {
            return;
        }
        removeRespawnProtection(player.getUniqueId());
        downgradeTools(player.getUniqueId());
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return;
        }
        if (getBedState(team) == BedState.ALIVE) {
            pendingRespawns.add(player.getUniqueId());
        } else {
            eliminatePlayer(player, team);
        }
    }

    public void handleRespawn(Player player) {
        UUID playerId = player.getUniqueId();
        if (!isActive() || !isParticipant(playerId)) {
            return;
        }
        TeamColor team = getTeam(playerId);
        if (team == null) {
            return;
        }
        if (getBedState(team) == BedState.DESTROYED && eliminatedPlayers.contains(playerId)) {
            setSpectator(player);
            Location lobby = arena.getLobbyLocation();
            if (lobby != null) {
                player.teleport(lobby);
            }
            return;
        }
        if (!pendingRespawns.remove(playerId)) {
            return;
        }
        setSpectator(player);
        Location lobby = arena.getLobbyLocation();
        if (lobby != null) {
            player.teleport(lobby);
        }
        scheduleRespawn(player, team);
    }

    public void handleBedDestroyed(TeamColor team, Player breaker) {
        if (team == null || getBedState(team) == BedState.DESTROYED) {
            return;
        }
        destroyBed(team);
        broadcast(Component.text("The ", NamedTextColor.RED)
                .append(team.displayComponent())
                .append(Component.text(" bed was destroyed!", NamedTextColor.RED)));

        Title title = Title.title(
                Component.text("Your Bed was destroyed!", NamedTextColor.RED),
                Component.text("You will no longer respawn.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
        );
        for (UUID playerId : assignments.keySet()) {
            if (team.equals(assignments.get(playerId))) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.showTitle(title);
                }
            }
        }
        checkTeamEliminated(team);
    }

    public void handlePlayerEliminated(Player player) {
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return;
        }
        eliminatePlayer(player, team);
    }

    private void startCountdown() {
        BukkitTask task = new BukkitRunnable() {
            private int remaining = START_COUNTDOWN_SECONDS;

            @Override
            public void run() {
                safeRun("startCountdown", () -> {
                    if (state != GameState.STARTING) {
                        cancel();
                        return;
                    }
                    if (remaining <= 0) {
                        showTitleAll(Component.text("GO!", NamedTextColor.GREEN), Component.empty());
                        beginRunning();
                        cancel();
                        return;
                    }
                    startCountdownRemaining = remaining;
                    showTitleAll(Component.text("Starting in " + remaining, NamedTextColor.YELLOW), Component.empty());
                    remaining--;
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);
        tasks.add(task);
    }

    private void beginRunning() {
        state = GameState.RUNNING;
        frozenPlayers.clear();
        matchStartMillis = System.currentTimeMillis();
        startCountdownRemaining = 0;
        generatorManager = new GeneratorManager(plugin, arena);
        generatorManager.start();
        scheduleGameEvents();
    }

    private void scheduleGameEvents() {
        scheduleAnnouncement(Component.text("Generator I", NamedTextColor.AQUA), TIER_1_DELAY);
        scheduleTierUpgrade(2, TIER_2_DELAY);
        scheduleTierUpgrade(3, TIER_3_DELAY);
        scheduleBedDestruction(BED_DESTRUCTION_DELAY);
        scheduleSuddenDeath(SUDDEN_DEATH_DELAY);
        scheduleGameEnd(GAME_END_DELAY);
    }

    private void scheduleTierUpgrade(int tier, int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("tierUpgrade", () -> {
                if (state != GameState.RUNNING || generatorManager == null) {
                    return;
                }
                generatorManager.setDiamondTier(tier);
                generatorManager.setEmeraldTier(tier);
                generatorManager.refresh();
                broadcast(Component.text("Generator " + toRoman(tier) + "!", NamedTextColor.GOLD));
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleAnnouncement(Component message, int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("announcement", () -> {
                if (state != GameState.RUNNING) {
                    return;
                }
                broadcast(message);
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleBedDestruction(int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("bedDestruction", () -> {
                if (state != GameState.RUNNING) {
                    return;
                }
                triggerBedDestruction();
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleSuddenDeath(int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("suddenDeath", () -> {
                if (state != GameState.RUNNING) {
                    return;
                }
                showTitleAll(Component.text("Sudden Death!", NamedTextColor.RED),
                        Component.text("Final battle begins.", NamedTextColor.GRAY));
                broadcast(Component.text("Sudden Death has begun!", NamedTextColor.RED));
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleGameEnd(int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("gameEnd", () -> {
                if (state != GameState.RUNNING) {
                    return;
                }
                List<TeamColor> aliveTeams = getAliveTeams();
                if (aliveTeams.size() == 1) {
                    bedwarsManager.endSession(this, aliveTeams.get(0));
                    return;
                }
                bedwarsManager.endSession(this, null);
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleRespawn(Player player, TeamColor team) {
        Location spawn = arena.getSpawn(team);
        if (spawn == null) {
            return;
        }
        BukkitTask existing = respawnTasks.remove(player.getUniqueId());
        if (existing != null) {
            existing.cancel();
        }
        BukkitTask existingCountdown = respawnCountdownTasks.remove(player.getUniqueId());
        if (existingCountdown != null) {
            existingCountdown.cancel();
        }
        startRespawnCountdown(player);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> safeRun("respawn", () -> {
            if (state != GameState.RUNNING || getBedState(team) == BedState.DESTROYED) {
                eliminatedPlayers.add(player.getUniqueId());
                setSpectator(player);
                removeRespawnProtection(player.getUniqueId());
                cancelRespawnCountdown(player.getUniqueId());
                respawnTasks.remove(player.getUniqueId());
                checkTeamEliminated(team);
                return;
            }
            player.teleport(spawn);
            player.getInventory().clear();
            giveStarterKit(player, team);
            applyPermanentItems(player, team);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            grantRespawnProtection(player);
            cancelRespawnCountdown(player.getUniqueId());
            respawnTasks.remove(player.getUniqueId());
        }), RESPAWN_DELAY_SECONDS * 20L);
        respawnTasks.put(player.getUniqueId(), task);
        showTitle(player, Component.text("Respawning in " + RESPAWN_DELAY_SECONDS, NamedTextColor.YELLOW), Component.empty());
    }

    private void eliminatePlayer(Player player, TeamColor team) {
        eliminatedPlayers.add(player.getUniqueId());
        setSpectator(player);
        checkTeamEliminated(team);
    }

    private void checkTeamEliminated(TeamColor team) {
        if (eliminatedTeams.contains(team)) {
            return;
        }
        if (!isTeamAlive(team)) {
            eliminatedTeams.add(team);
            broadcast(Component.text("Team ", NamedTextColor.RED)
                    .append(team.displayComponent())
                    .append(Component.text(" has been eliminated!", NamedTextColor.RED)));
            checkForWin();
        }
    }

    private void checkForWin() {
        List<TeamColor> aliveTeams = getAliveTeams();
        if (aliveTeams.size() == 1) {
            bedwarsManager.endSession(this, aliveTeams.get(0));
        }
    }

    private List<TeamColor> getAliveTeams() {
        List<TeamColor> aliveTeams = new ArrayList<>();
        for (TeamColor team : teamsInMatch) {
            if (isTeamAlive(team)) {
                aliveTeams.add(team);
            }
        }
        return aliveTeams;
    }

    private boolean isTeamAlive(TeamColor team) {
        if (!hasPlayers(team)) {
            return false;
        }
        if (getBedState(team) == BedState.ALIVE) {
            return true;
        }
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() == team && !eliminatedPlayers.contains(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPlayers(TeamColor team) {
        for (TeamColor value : assignments.values()) {
            if (value == team) {
                return true;
            }
        }
        return false;
    }

    private void initializeBeds() {
        bedStates.clear();
        bedBlocks.clear();
        for (Map.Entry<TeamColor, BedLocation> entry : arena.getBeds().entrySet()) {
            if (!teamsInMatch.contains(entry.getKey())) {
                continue;
            }
            bedStates.put(entry.getKey(), BedState.ALIVE);
            bedBlocks.put(entry.getValue().head(), entry.getKey());
            bedBlocks.put(entry.getValue().foot(), entry.getKey());
        }
    }

    private void destroyBed(TeamColor team) {
        bedStates.put(team, BedState.DESTROYED);
        BedLocation location = arena.getBeds().get(team);
        if (location == null) {
            return;
        }
        bedBlocks.remove(location.head());
        bedBlocks.remove(location.foot());
        removeBedBlocks(location);
    }

    private void removeBedBlocks(BedLocation location) {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        world.getBlockAt(location.head().x(), location.head().y(), location.head().z()).setType(Material.AIR, false);
        world.getBlockAt(location.foot().x(), location.foot().y(), location.foot().z()).setType(Material.AIR, false);
    }

    private void triggerBedDestruction() {
        boolean changed = false;
        for (TeamColor team : teamsInMatch) {
            if (getBedState(team) == BedState.ALIVE) {
                destroyBed(team);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        showTitleAll(Component.text("Beds Destroyed!", NamedTextColor.RED),
                Component.text("No more respawns.", NamedTextColor.GRAY));
        broadcast(Component.text("All beds have been destroyed!", NamedTextColor.RED));
        for (TeamColor team : teamsInMatch) {
            checkTeamEliminated(team);
        }
    }

    private void cleanupWorld() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        clearDroppedItems(world);
        despawnShops();
        for (BlockPoint point : placedBlocks) {
            world.getBlockAt(point.x(), point.y(), point.z()).setType(Material.AIR, false);
        }
        restoreBeds();
        Location lobby = arena.getLobbyLocation();
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            if (lobby != null) {
                player.teleport(lobby);
            }
        }
    }

    private void restoreBeds() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (Map.Entry<TeamColor, BedLocation> entry : arena.getBeds().entrySet()) {
            restoreBed(world, entry.getKey(), entry.getValue());
        }
    }

    private void applyBedLayout() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (Map.Entry<TeamColor, BedLocation> entry : arena.getBeds().entrySet()) {
            if (teamsInMatch.contains(entry.getKey())) {
                restoreBed(world, entry.getKey(), entry.getValue());
            } else {
                removeBedBlocks(entry.getValue());
            }
        }
    }

    private void clearDroppedItems(World world) {
        for (Item item : world.getEntitiesByClass(Item.class)) {
            item.remove();
        }
    }

    private void restoreBed(World world, TeamColor team, BedLocation bed) {
        BlockPoint head = bed.head();
        BlockPoint foot = bed.foot();
        int dx = head.x() - foot.x();
        int dz = head.z() - foot.z();
        org.bukkit.block.BlockFace facing = switch (dx + "," + dz) {
            case "1,0" -> org.bukkit.block.BlockFace.EAST;
            case "-1,0" -> org.bukkit.block.BlockFace.WEST;
            case "0,1" -> org.bukkit.block.BlockFace.SOUTH;
            case "0,-1" -> org.bukkit.block.BlockFace.NORTH;
            default -> org.bukkit.block.BlockFace.NORTH;
        };

        Block headBlock = world.getBlockAt(head.x(), head.y(), head.z());
        headBlock.setType(team.bed(), false);
        Bed headData = (Bed) team.bed().createBlockData();
        headData.setPart(Bed.Part.HEAD);
        headData.setFacing(facing);
        headBlock.setBlockData(headData, false);

        Block footBlock = world.getBlockAt(foot.x(), foot.y(), foot.z());
        footBlock.setType(team.bed(), false);
        Bed footData = (Bed) team.bed().createBlockData();
        footData.setPart(Bed.Part.FOOT);
        footData.setFacing(facing);
        footBlock.setBlockData(footData, false);
    }

    private void stopInternal() {
        state = GameState.ENDING;
        despawnShops();
        closeFakeEnderChests();
        clearSidebars();
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        for (BukkitTask task : respawnTasks.values()) {
            task.cancel();
        }
        respawnTasks.clear();
        for (BukkitTask task : respawnCountdownTasks.values()) {
            task.cancel();
        }
        respawnCountdownTasks.clear();
        for (BukkitTask task : respawnProtectionTasks.values()) {
            task.cancel();
        }
        respawnProtectionTasks.clear();
        respawnProtectionEnds.clear();
        pendingRespawns.clear();
        if (generatorManager != null) {
            generatorManager.stop();
            generatorManager = null;
        }
        frozenPlayers.clear();
        sidebarTask = null;
        matchStartMillis = 0L;
        startCountdownRemaining = 0;
    }

    private void resetState() {
        eliminatedPlayers.clear();
        eliminatedTeams.clear();
        placedBlocks.clear();
        bedStates.clear();
        bedBlocks.clear();
        fakeEnderChests.clear();
        previousScoreboards.clear();
        activeScoreboards.clear();
        sidebarLines.clear();
        respawnProtectionEnds.clear();
        respawnProtectionTasks.clear();
        teamsInMatch.clear();
        shopNpcIds.clear();
        armorTiers.clear();
        pickaxeTiers.clear();
        axeTiers.clear();
        bowTiers.clear();
        shearsUnlocked.clear();
    }

    private void closeFakeEnderChests() {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            Inventory open = player.getOpenInventory().getTopInventory();
            if (fakeEnderChests.containsValue(open)) {
                player.closeInventory();
            }
        }
        for (Inventory inventory : fakeEnderChests.values()) {
            inventory.clear();
        }
    }

    private void showTitleAll(Component title, Component subtitle) {
        Title message = Title.title(title, subtitle, Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO));
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.showTitle(message);
            }
        }
    }

    private void showTitle(Player player, Component title, Component subtitle) {
        Title message = Title.title(title, subtitle, Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO));
        player.showTitle(message);
    }

    private void broadcast(Component message) {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void setSpectator(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    public boolean hasRespawnProtection(UUID playerId) {
        Long end = respawnProtectionEnds.get(playerId);
        if (end == null) {
            return false;
        }
        if (System.currentTimeMillis() >= end) {
            removeRespawnProtection(playerId);
            return false;
        }
        return true;
    }

    public void removeRespawnProtection(UUID playerId) {
        respawnProtectionEnds.remove(playerId);
        BukkitTask task = respawnProtectionTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void grantRespawnProtection(Player player) {
        UUID playerId = player.getUniqueId();
        removeRespawnProtection(playerId);
        respawnProtectionEnds.put(playerId,
                System.currentTimeMillis() + RESPAWN_PROTECTION_SECONDS * 1000L);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> removeRespawnProtection(playerId),
                RESPAWN_PROTECTION_SECONDS * 20L);
        respawnProtectionTasks.put(playerId, task);
    }

    private void giveStarterKit(Player player, TeamColor team) {
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        player.getInventory().addItem(sword);
        org.bukkit.Color color = team.dyeColor().getColor();
        player.getInventory().setHelmet(colorLeatherArmor(Material.LEATHER_HELMET, color));
        player.getInventory().setChestplate(colorLeatherArmor(Material.LEATHER_CHESTPLATE, color));
        player.getInventory().setLeggings(colorLeatherArmor(Material.LEATHER_LEGGINGS, color));
        player.getInventory().setBoots(colorLeatherArmor(Material.LEATHER_BOOTS, color));
    }

    private ItemStack colorLeatherArmor(Material material, org.bukkit.Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        item.setItemMeta(meta);
        return item;
    }

    private void applyPermanentItems(Player player, TeamColor team) {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null || team == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        int armorTier = armorTiers.getOrDefault(playerId, 0);
        if (armorTier > 0) {
            equipArmor(player, team, config, armorTier);
        }
        int bowTier = bowTiers.getOrDefault(playerId, 0);
        if (bowTier > 0) {
            equipTieredItem(player, team, config, ShopItemBehavior.BOW, bowTier, BOW_MATERIALS);
        }
        int pickaxeTier = pickaxeTiers.getOrDefault(playerId, 0);
        if (pickaxeTier > 0) {
            equipTieredItem(player, team, config, ShopItemBehavior.PICKAXE, pickaxeTier, PICKAXE_MATERIALS);
        }
        int axeTier = axeTiers.getOrDefault(playerId, 0);
        if (axeTier > 0) {
            equipTieredItem(player, team, config, ShopItemBehavior.AXE, axeTier, AXE_MATERIALS);
        }
        if (shearsUnlocked.contains(playerId)) {
            giveShears(player, team);
        }
    }

    private boolean applyTierUpgrade(Player player,
                                     ShopItemDefinition item,
                                     Map<UUID, Integer> tierMap,
                                     ShopItemBehavior behavior) {
        int tier = item.getTier();
        if (tier <= 0) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        int current = tierMap.getOrDefault(playerId, 0);
        if (tier <= current) {
            return false;
        }
        tierMap.put(playerId, tier);
        applyPermanentItems(player, getTeam(playerId));
        return true;
    }

    private boolean applyShears(Player player, ShopItemDefinition item, TeamColor team) {
        UUID playerId = player.getUniqueId();
        if (shearsUnlocked.contains(playerId)) {
            return false;
        }
        shearsUnlocked.add(playerId);
        giveItem(player, item.createPurchaseItem(team));
        return true;
    }

    private void equipTieredItem(Player player,
                                 TeamColor team,
                                 ShopConfig config,
                                 ShopItemBehavior behavior,
                                 int tier,
                                 Set<Material> removeSet) {
        ShopItemDefinition definition = config.getTieredItem(behavior, tier).orElse(null);
        if (definition == null) {
            return;
        }
        removeItems(player, removeSet);
        giveItem(player, definition.createPurchaseItem(team));
    }

    private void equipArmor(Player player, TeamColor team, ShopConfig config, int tier) {
        ShopItemDefinition definition = config.getTieredItem(ShopItemBehavior.ARMOR, tier).orElse(null);
        if (definition == null) {
            return;
        }
        String base = armorBase(definition.getMaterial());
        Material helmet = Material.matchMaterial(base + "_HELMET");
        Material chestplate = Material.matchMaterial(base + "_CHESTPLATE");
        Material leggings = Material.matchMaterial(base + "_LEGGINGS");
        Material boots = Material.matchMaterial(base + "_BOOTS");
        if (helmet == null || chestplate == null || leggings == null || boots == null) {
            return;
        }
        if ("LEATHER".equals(base)) {
            org.bukkit.Color color = team.dyeColor().getColor();
            player.getInventory().setHelmet(colorLeatherArmor(helmet, color));
            player.getInventory().setChestplate(colorLeatherArmor(chestplate, color));
            player.getInventory().setLeggings(colorLeatherArmor(leggings, color));
            player.getInventory().setBoots(colorLeatherArmor(boots, color));
            return;
        }
        player.getInventory().setHelmet(new ItemStack(helmet));
        player.getInventory().setChestplate(new ItemStack(chestplate));
        player.getInventory().setLeggings(new ItemStack(leggings));
        player.getInventory().setBoots(new ItemStack(boots));
    }

    private String armorBase(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            return name.substring(0, name.length() - "_HELMET".length());
        }
        if (name.endsWith("_CHESTPLATE")) {
            return name.substring(0, name.length() - "_CHESTPLATE".length());
        }
        if (name.endsWith("_LEGGINGS")) {
            return name.substring(0, name.length() - "_LEGGINGS".length());
        }
        if (name.endsWith("_BOOTS")) {
            return name.substring(0, name.length() - "_BOOTS".length());
        }
        return name;
    }

    private void giveShears(Player player, TeamColor team) {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return;
        }
        if (player.getInventory().contains(Material.SHEARS)) {
            return;
        }
        ShopItemDefinition definition = config.getFirstByBehavior(ShopItemBehavior.SHEARS);
        if (definition != null) {
            giveItem(player, definition.createPurchaseItem(team));
        } else {
            giveItem(player, new ItemStack(Material.SHEARS));
        }
    }

    private void downgradeTools(UUID playerId) {
        downgradeTier(pickaxeTiers, playerId);
        downgradeTier(axeTiers, playerId);
    }

    private void downgradeTier(Map<UUID, Integer> map, UUID playerId) {
        int tier = map.getOrDefault(playerId, 0);
        if (tier > 1) {
            map.put(playerId, tier - 1);
        }
    }

    private boolean hasResources(Player player, ShopCost cost) {
        if (cost == null || !cost.isValid()) {
            return true;
        }
        int remaining = cost.amount();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != cost.material()) {
                continue;
            }
            remaining -= item.getAmount();
            if (remaining <= 0) {
                return true;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == cost.material()) {
            remaining -= offhand.getAmount();
        }
        return remaining <= 0;
    }

    private void removeResources(Player player, ShopCost cost) {
        if (cost == null || !cost.isValid()) {
            return;
        }
        int remaining = cost.amount();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != cost.material()) {
                continue;
            }
            int amount = item.getAmount();
            if (amount <= remaining) {
                remaining -= amount;
                contents[i] = null;
            } else {
                item.setAmount(amount - remaining);
                remaining = 0;
            }
            if (remaining <= 0) {
                break;
            }
        }
        player.getInventory().setStorageContents(contents);
        if (remaining > 0) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand != null && offhand.getType() == cost.material()) {
                int amount = offhand.getAmount();
                if (amount <= remaining) {
                    player.getInventory().setItemInOffHand(null);
                } else {
                    offhand.setAmount(amount - remaining);
                }
            }
        }
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void removeSwords(Player player) {
        removeItems(player, SWORD_MATERIALS);
    }

    private void removeItems(Player player, Set<Material> materials) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && materials.contains(item.getType())) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && materials.contains(offhand.getType())) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    public void handleWorldChange(Player player) {
        updateSidebarForPlayer(player);
    }

    public void handlePlayerQuit(UUID playerId) {
        cancelRespawnCountdown(playerId);
        removeRespawnProtection(playerId);
        BukkitTask task = respawnTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        restoreSidebar(playerId);
    }

    public void handlePlayerJoin(Player player) {
        if (!isActive()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!isParticipant(playerId)) {
            return;
        }
        TeamColor team = getTeam(playerId);
        if (team == null) {
            return;
        }

        if (state == GameState.STARTING) {
            Location spawn = arena.getSpawn(team);
            if (spawn != null) {
                player.teleport(spawn);
            }
            player.getInventory().clear();
            giveStarterKit(player, team);
            applyPermanentItems(player, team);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            frozenPlayers.add(playerId);
            updateSidebarForPlayer(player);
            return;
        }

        if (state != GameState.RUNNING) {
            return;
        }

        if (eliminatedPlayers.contains(playerId)) {
            setSpectator(player);
            Location lobby = arena.getLobbyLocation();
            if (lobby != null) {
                player.teleport(lobby);
            }
            updateSidebarForPlayer(player);
            return;
        }

        if (pendingRespawns.remove(playerId)) {
            setSpectator(player);
            Location lobby = arena.getLobbyLocation();
            if (lobby != null) {
                player.teleport(lobby);
            }
            scheduleRespawn(player, team);
            updateSidebarForPlayer(player);
            return;
        }

        Location spawn = arena.getSpawn(team);
        if (spawn != null) {
            player.teleport(spawn);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        grantRespawnProtection(player);
        applyPermanentItems(player, team);
        updateSidebarForPlayer(player);
    }

    private void startSidebarUpdates() {
        if (sidebarTask != null) {
            sidebarTask.cancel();
        }
        sidebarTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> safeRun("sidebarUpdate", this::updateSidebars),
                0L,
                20L);
        tasks.add(sidebarTask);
    }

    private void updateSidebars() {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                updateSidebarForPlayer(player);
            }
        }
    }

    private void updateSidebarForPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        if (!isActive() || !isParticipant(playerId) || !isInArenaWorld(player.getWorld())) {
            restoreSidebar(playerId);
            return;
        }
        Scoreboard scoreboard = activeScoreboards.get(playerId);
        if (scoreboard == null) {
            previousScoreboards.putIfAbsent(playerId, player.getScoreboard());
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective(
                    "bedwars",
                    "dummy",
                    Component.text("BED WARS", NamedTextColor.GOLD)
            );
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            activeScoreboards.put(playerId, scoreboard);
        }
        player.setScoreboard(scoreboard);
        updateTeamColors(scoreboard);
        updateSidebarLines(player, scoreboard);
    }

    private void restoreSidebar(UUID playerId) {
        Scoreboard previous = previousScoreboards.remove(playerId);
        Scoreboard active = activeScoreboards.remove(playerId);
        List<String> lines = sidebarLines.remove(playerId);
        if (active != null && lines != null) {
            for (String line : lines) {
                active.resetScores(line);
            }
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.setScoreboard(previous != null
                    ? previous
                    : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void clearSidebars() {
        for (UUID playerId : new HashSet<>(activeScoreboards.keySet())) {
            restoreSidebar(playerId);
        }
    }

    private void updateSidebarLines(Player player, Scoreboard scoreboard) {
        Objective objective = scoreboard.getObjective("bedwars");
        if (objective == null) {
            objective = scoreboard.registerNewObjective(
                    "bedwars",
                    "dummy",
                    Component.text("BED WARS", NamedTextColor.GOLD)
            );
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        List<String> previous = sidebarLines.get(player.getUniqueId());
        if (previous != null) {
            for (String line : previous) {
                scoreboard.resetScores(line);
            }
        }
        List<String> lines = buildSidebarLines(player);
        int score = lines.size();
        for (String line : lines) {
            objective.getScore(line).setScore(score--);
        }
        sidebarLines.put(player.getUniqueId(), lines);
    }

    private void updateTeamColors(Scoreboard scoreboard) {
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith("bw_") && !teamsInMatch.contains(teamFromName(team.getName()))) {
                team.unregister();
            }
        }
        for (TeamColor teamColor : teamsInMatch) {
            String teamName = "bw_" + teamColor.key();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            team.color(teamColor.textColor());
            team.setPrefix("");
            team.setSuffix("");
            for (String entry : new HashSet<>(team.getEntries())) {
                team.removeEntry(entry);
            }
        }
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            TeamColor teamColor = entry.getValue();
            if (teamColor == null || !teamsInMatch.contains(teamColor)) {
                continue;
            }
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target == null) {
                continue;
            }
            Team team = scoreboard.getTeam("bw_" + teamColor.key());
            if (team != null) {
                team.addEntry(target.getName());
            }
        }
    }

    private TeamColor teamFromName(String teamName) {
        if (teamName == null || !teamName.startsWith("bw_")) {
            return null;
        }
        return TeamColor.fromKey(teamName.substring(3));
    }

    private List<String> buildSidebarLines(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.DARK_GRAY + " ");
        lines.add(buildEventLine());
        lines.add(ChatColor.DARK_GRAY + "  ");

        TeamColor playerTeam = getTeam(player.getUniqueId());
        for (TeamColor team : teamsInMatch) {
            lines.add(buildTeamLine(team, playerTeam));
        }

        lines.add(ChatColor.DARK_GRAY + "   ");
        lines.add(ChatColor.AQUA + "Made by Krispasi");
        return lines;
    }

    private String buildEventLine() {
        if (state == GameState.STARTING) {
            return ChatColor.YELLOW + "Starting in " + startCountdownRemaining + "s";
        }
        if (state != GameState.RUNNING) {
            return ChatColor.GRAY + "Waiting...";
        }
        EventInfo info = getNextEventInfo();
        return ChatColor.AQUA + info.label() + ChatColor.GRAY + " in " + formatTime(info.secondsRemaining());
    }

    private String buildTeamLine(TeamColor team, TeamColor playerTeam) {
        String status = getTeamStatus(team);
        String you = team == playerTeam ? ChatColor.GRAY + " YOU" : "";
        return team.chatColor()
                + team.shortName()
                + " "
                + team.displayName()
                + ChatColor.WHITE
                + ": "
                + status
                + you;
    }

    private String getTeamStatus(TeamColor team) {
        if (getBedState(team) == BedState.ALIVE) {
            return ChatColor.GREEN + "Alive";
        }
        int alive = countAlivePlayers(team);
        if (alive > 0) {
            return ChatColor.YELLOW + String.valueOf(alive);
        }
        return ChatColor.RED + "X";
    }

    private int countAlivePlayers(TeamColor team) {
        int count = 0;
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() == team && !eliminatedPlayers.contains(entry.getKey())) {
                count++;
            }
        }
        return count;
    }

    private EventInfo getNextEventInfo() {
        long elapsedSeconds = matchStartMillis > 0
                ? (System.currentTimeMillis() - matchStartMillis) / 1000L
                : 0L;
        if (elapsedSeconds < TIER_1_DELAY) {
            return new EventInfo("Generator I", (int) (TIER_1_DELAY - elapsedSeconds));
        }
        if (elapsedSeconds < TIER_2_DELAY) {
            return new EventInfo("Generator II", (int) (TIER_2_DELAY - elapsedSeconds));
        }
        if (elapsedSeconds < TIER_3_DELAY) {
            return new EventInfo("Generator III", (int) (TIER_3_DELAY - elapsedSeconds));
        }
        if (elapsedSeconds < BED_DESTRUCTION_DELAY) {
            return new EventInfo("Beds Destroyed", (int) (BED_DESTRUCTION_DELAY - elapsedSeconds));
        }
        if (elapsedSeconds < SUDDEN_DEATH_DELAY) {
            return new EventInfo("Sudden Death", (int) (SUDDEN_DEATH_DELAY - elapsedSeconds));
        }
        if (elapsedSeconds < GAME_END_DELAY) {
            return new EventInfo("Game End", (int) (GAME_END_DELAY - elapsedSeconds));
        }
        return new EventInfo("Game End", 0);
    }

    private String formatTime(int totalSeconds) {
        int minutes = Math.max(0, totalSeconds) / 60;
        int seconds = Math.max(0, totalSeconds) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private record EventInfo(String label, int secondsRemaining) {
    }

    private void startRespawnCountdown(Player player) {
        BukkitTask task = new BukkitRunnable() {
            private int remaining = RESPAWN_DELAY_SECONDS;

            @Override
            public void run() {
                safeRun("respawnCountdown", () -> {
                    if (state != GameState.RUNNING || !player.isOnline()) {
                        cancelRespawnCountdown(player.getUniqueId());
                        return;
                    }
                    if (remaining <= 0) {
                        cancelRespawnCountdown(player.getUniqueId());
                        return;
                    }
                    player.sendActionBar(Component.text("Respawning in " + remaining + "s", NamedTextColor.YELLOW));
                    remaining--;
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);
        respawnCountdownTasks.put(player.getUniqueId(), task);
    }

    private void cancelRespawnCountdown(UUID playerId) {
        BukkitTask task = respawnCountdownTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void safeRun(String context, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "BedWars error in " + context, ex);
            } else {
                Bukkit.getLogger().log(Level.SEVERE, "BedWars error in " + context, ex);
            }
        }
    }

    private void updateTeamsInMatch() {
        teamsInMatch.clear();
        for (TeamColor team : assignments.values()) {
            if (team != null) {
                teamsInMatch.add(team);
            }
        }
    }

    private void spawnShops() {
        despawnShops();
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (TeamColor team : teamsInMatch) {
            ShopLocation main = arena.getShop(team, ShopType.ITEM);
            if (main != null) {
                spawnShopNpc(world, main, ShopType.ITEM);
            }
            ShopLocation upgrades = arena.getShop(team, ShopType.UPGRADES);
            if (upgrades != null) {
                spawnShopNpc(world, upgrades, ShopType.UPGRADES);
            }
        }
    }

    private void despawnShops() {
        for (UUID id : shopNpcIds) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        shopNpcIds.clear();
    }

    private void spawnShopNpc(World world, ShopLocation location, ShopType type) {
        Location spawn = location.toLocation(world);
        Villager villager = world.spawn(spawn, Villager.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setCollidable(false);
            entity.setSilent(true);
            entity.setRemoveWhenFarAway(false);
            entity.setPersistent(true);
            entity.customName(Component.text(type.displayName(), NamedTextColor.YELLOW));
            entity.setCustomNameVisible(true);
            entity.addScoreboardTag(type == ShopType.ITEM ? ITEM_SHOP_TAG : UPGRADES_SHOP_TAG);
        });
        shopNpcIds.add(villager.getUniqueId());
    }

    private boolean isWithinRadius(BlockPoint a, BlockPoint b, int radius) {
        int dx = a.x() - b.x();
        int dz = a.z() - b.z();
        return dx * dx + dz * dz <= radius * radius;
    }

    private String toRoman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(tier);
        };
    }
}
