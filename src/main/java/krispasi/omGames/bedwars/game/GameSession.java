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
import java.util.Locale;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.generator.GeneratorInfo;
import krispasi.omGames.bedwars.generator.GeneratorManager;
import krispasi.omGames.bedwars.generator.GeneratorType;
import krispasi.omGames.bedwars.gui.ShopMenu;
import krispasi.omGames.bedwars.gui.TeamPickMenu;
import krispasi.omGames.bedwars.gui.UpgradeShopMenu;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.BedLocation;
import krispasi.omGames.bedwars.model.BedState;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.EventSettings;
import krispasi.omGames.bedwars.model.ShopLocation;
import krispasi.omGames.bedwars.model.ShopType;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.item.TeamSelectItemData;
import krispasi.omGames.bedwars.item.CustomItemData;
import krispasi.omGames.bedwars.item.CustomItemDefinition;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopCost;
import krispasi.omGames.bedwars.shop.ShopCategoryType;
import krispasi.omGames.bedwars.shop.ShopItemBehavior;
import krispasi.omGames.bedwars.shop.ShopItemDefinition;
import krispasi.omGames.bedwars.shop.ShopItemLimit;
import krispasi.omGames.bedwars.shop.ShopItemLimitScope;
import krispasi.omGames.bedwars.upgrade.TeamUpgradeState;
import krispasi.omGames.bedwars.upgrade.TeamUpgradeType;
import krispasi.omGames.bedwars.upgrade.TrapType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Runtime state machine for a single BedWars match.
 * <p>Tracks team assignments, beds, generators, placed blocks, upgrades, traps,
 * and per-player tool tiers.</p>
 * <p>Enforces match rules such as respawn timing, combat restrictions, sudden death,
 * and world border behavior.</p>
 * <p>Creates and tears down shop NPCs, scoreboards, and other match-scoped entities.</p>
 * @see krispasi.omGames.bedwars.game.GameState
 */
public class GameSession {
    public enum RotatingSelectionMode {
        TWO_RANDOM,
        ONE_RANDOM,
        MANUAL
    }

    public static final String ITEM_SHOP_TAG = "bw_item_shop";
    public static final String UPGRADES_SHOP_TAG = "bw_upgrades_shop";
    public static final String BED_BUG_TAG = "bw_bed_bug";
    public static final String DREAM_DEFENDER_TAG = "bw_dream_defender";
    public static final String CRYSTAL_TAG = "bw_crystal";
    public static final String CRYSTAL_EXPLOSION_TAG = "bw_crystal_exploded";
    public static final String HAPPY_GHAST_TAG = "bw_happy_ghast";
    public static final String CREEPING_CREEPER_TAG = "bw_creeping_creeper";
    public static final String GARRY_TAG = "bw_garry";
    public static final String GARRY_WIFE_TAG = "bw_garry_wife";
    public static final String GARRY_JR_TAG = "bw_garry_jr";
    private static final Set<Material> SWORD_MATERIALS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.DIAMOND_SWORD,
            Material.MACE,
            Material.NETHERITE_SPEAR
    );
    private static final Set<Material> WOODEN_SWORD_ONLY = EnumSet.of(Material.WOODEN_SWORD);
    private static final Set<Material> BOW_MATERIALS = EnumSet.of(Material.BOW);
    private static final Set<Material> CROSSBOW_MATERIALS = EnumSet.of(Material.CROSSBOW);
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
    private static final Set<Material> ATTACK_MATERIALS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.DIAMOND_SWORD,
            Material.MACE,
            Material.NETHERITE_SPEAR,
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE,
            Material.TRIDENT
    );
    private static final int START_COUNTDOWN_SECONDS = 5;
    private static final int RESPAWN_DELAY_SECONDS = 5;
    private static final int RESPAWN_PROTECTION_SECONDS = 5;
    private static final int DEFAULT_BASE_RADIUS = 8;
    private static final int HEAL_POOL_INTERVAL_TICKS = 20;
    private static final int HEAL_POOL_DURATION_TICKS = 60;
    private static final int TRAP_CHECK_INTERVAL_TICKS = 20;
    private static final int TRAP_MAX_COUNT = 3;
    private static final int TRAP_BLINDNESS_SECONDS = 8;
    private static final int GARRY_RESPAWN_SECONDS = 10;
    private static final int DEFAULT_CENTER_RADIUS = 32;
    private static final int TRAP_SLOW_SECONDS = 8;
    private static final int TRAP_COUNTER_SECONDS = 15;
    private static final int TRAP_FATIGUE_SECONDS = 10;
    private static final int LOBBY_REGEN_DURATION_TICKS = 60;
    private static final long REGEN_DELAY_MILLIS = 4000L;
    private static final long REGEN_INTERVAL_MILLIS = 3000L;
    private static final double SUDDEN_DEATH_BORDER_TARGET_SIZE = 6.0;
    private static final double WORLD_BORDER_DAMAGE_BUFFER = 0.0;
    private static final double WORLD_BORDER_DAMAGE_AMOUNT = 2.0;
    private static final int WORLD_BORDER_WARNING_DISTANCE = 1;

    private final BedwarsManager bedwarsManager;
    private final Arena arena;
    private final Map<UUID, TeamColor> assignments = new HashMap<>();
    private final Map<TeamColor, BedState> bedStates = new EnumMap<>(TeamColor.class);
    private final Map<BlockPoint, TeamColor> bedBlocks = new HashMap<>();
    private final Set<BlockPoint> placedBlocks = new HashSet<>();
    private final Map<BlockPoint, ItemStack> placedBlockItems = new HashMap<>();
    private final Set<Long> forcedChunks = new HashSet<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Set<UUID> eliminatedPlayers = new HashSet<>();
    private final Set<TeamColor> eliminatedTeams = new HashSet<>();
    private final Set<UUID> pendingRespawns = new HashSet<>();
    private final Set<UUID> respawnGracePlayers = new HashSet<>();
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnCountdownTasks = new HashMap<>();
    private final Map<UUID, Long> respawnProtectionEnds = new HashMap<>();
    private final Map<UUID, BukkitTask> respawnProtectionTasks = new HashMap<>();
    private final Map<UUID, Inventory> fakeEnderChests = new HashMap<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private final Map<UUID, Scoreboard> activeScoreboards = new HashMap<>();
    private final Map<UUID, List<String>> sidebarLines = new HashMap<>();
    private final Set<String> rotatingItemIds = new HashSet<>();
    private final List<String> manualRotatingItemIds = new ArrayList<>();
    private RotatingSelectionMode rotatingMode = RotatingSelectionMode.TWO_RANDOM;
    private final Map<UUID, Integer> killCounts = new HashMap<>();
    private final Map<TeamColor, Map<String, Integer>> teamPurchaseCounts = new EnumMap<>(TeamColor.class);
    private final Map<UUID, Map<String, Integer>> playerPurchaseCounts = new HashMap<>();
    private final Set<UUID> editorPlayers = new HashSet<>();
    private final Set<TeamColor> teamsInMatch = EnumSet.noneOf(TeamColor.class);
    private final Set<UUID> shopNpcIds = new HashSet<>();
    private final Map<TeamColor, TeamUpgradeState> teamUpgrades = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, BlockPoint> baseCenters = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Set<UUID>> baseOccupants = new EnumMap<>(TeamColor.class);
    private final Map<UUID, Long> trapImmunityEnds = new HashMap<>();
    private final Map<UUID, BukkitTask> trapImmunityTasks = new HashMap<>();
    private final Map<UUID, Integer> armorTiers = new HashMap<>();
    private final Map<UUID, Integer> pickaxeTiers = new HashMap<>();
    private final Map<UUID, Integer> axeTiers = new HashMap<>();
    private final Set<UUID> shearsUnlocked = new HashSet<>();
    private final Set<UUID> shieldUnlocked = new HashSet<>();
    private boolean garryUnlocked;
    private boolean garryWifeAlive;
    private boolean garryJrAlive;
    private UUID garryId;
    private UUID garryWifeId;
    private UUID garryJrId;
    private BukkitTask garryRespawnTask;
    private boolean suddenDeathActive;
    private boolean tier2Triggered;
    private boolean tier3Triggered;
    private boolean bedDestructionTriggered;
    private boolean gameEndTriggered;
    private final Map<UUID, Long> lastCombatTimes = new HashMap<>();
    private final Map<UUID, UUID> lastDamagers = new HashMap<>();
    private final Map<UUID, Long> lastDamagerTimes = new HashMap<>();
    private final Map<UUID, Long> lastDamageTimes = new HashMap<>();
    private final Map<UUID, Long> lastRegenTimes = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private GeneratorManager generatorManager;
    private GameState state = GameState.IDLE;
    private JavaPlugin plugin;
    private BukkitTask sidebarTask;
    private long matchStartMillis;
    private int startCountdownRemaining;
    private int maxTeamSize = 4;
    private boolean teamPickEnabled;
    private BukkitTask lobbyCountdownTask;
    private boolean lobbyCountdownPaused;
    private int lobbyCountdownRemaining;
    private UUID lobbyInitiatorId;
    private Double previousBorderSize;
    private Location previousBorderCenter;
    private Double previousBorderDamageAmount;
    private Double previousBorderDamageBuffer;
    private Integer previousBorderWarningDistance;
    private boolean statsEnabled = true;

    public GameSession(BedwarsManager bedwarsManager, Arena arena) {
        this.bedwarsManager = bedwarsManager;
        this.arena = arena;
    }

    public Arena getArena() {
        return arena;
    }

    public BedwarsManager getBedwarsManager() {
        return bedwarsManager;
    }

    public GameState getState() {
        return state;
    }

    public boolean isStarting() {
        return state == GameState.STARTING;
    }

    public boolean isLobby() {
        return state == GameState.LOBBY;
    }

    public boolean isLobbyInitiator(UUID playerId) {
        return playerId != null && playerId.equals(lobbyInitiatorId);
    }

    public boolean isRunning() {
        return state == GameState.RUNNING;
    }

    public boolean isActive() {
        return state == GameState.LOBBY || state == GameState.STARTING || state == GameState.RUNNING;
    }

    public boolean isStatsEnabled() {
        return statsEnabled;
    }

    public void setStatsEnabled(boolean statsEnabled) {
        this.statsEnabled = statsEnabled;
    }

    public void applyUpgradesTo(Player player) {
        if (player == null) {
            return;
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return;
        }
        applyTeamUpgrades(player, team);
    }

    public void assignTeam(UUID playerId, TeamColor team) {
        if (team == null) {
            assignments.remove(playerId);
        } else {
            assignments.put(playerId, team);
        }
        if (state == GameState.LOBBY) {
            updateTeamsInMatch();
        }
    }

    public TeamColor getTeam(UUID playerId) {
        return assignments.get(playerId);
    }

    public Map<UUID, TeamColor> getAssignments() {
        return Collections.unmodifiableMap(assignments);
    }

    public int getMaxTeamSize() {
        return maxTeamSize;
    }

    public void setMaxTeamSize(int size) {
        maxTeamSize = Math.max(1, Math.min(4, size));
    }

    public boolean isTeamPickEnabled() {
        return teamPickEnabled;
    }

    public void setTeamPickEnabled(boolean enabled) {
        teamPickEnabled = enabled;
    }

    public int getTeamMemberCount(TeamColor team) {
        if (team == null) {
            return 0;
        }
        int count = 0;
        for (TeamColor assigned : assignments.values()) {
            if (team.equals(assigned)) {
                count++;
            }
        }
        return count;
    }

    public boolean isTeamFull(TeamColor team) {
        return team != null && getTeamMemberCount(team) >= maxTeamSize;
    }

    public int getAssignedCount() {
        return assignments.size();
    }

    public boolean isParticipant(UUID playerId) {
        return assignments.containsKey(playerId);
    }

    public int getKillCount(UUID playerId) {
        return killCounts.getOrDefault(playerId, 0);
    }

    public void addKill(UUID playerId) {
        if (playerId == null || !isParticipant(playerId)) {
            return;
        }
        killCounts.merge(playerId, 1, Integer::sum);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            updateSidebarForPlayer(player);
        }
    }

    public boolean isEditor(UUID playerId) {
        return playerId != null && editorPlayers.contains(playerId);
    }

    public boolean isTeamInMatch(TeamColor team) {
        return team != null && teamsInMatch.contains(team);
    }

    public boolean removeParticipant(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!isParticipant(playerId)) {
            return false;
        }
        assignments.remove(playerId);
        eliminatedPlayers.remove(playerId);
        pendingRespawns.remove(playerId);
        respawnGracePlayers.remove(playerId);
        frozenPlayers.remove(playerId);
        armorTiers.remove(playerId);
        pickaxeTiers.remove(playerId);
        axeTiers.remove(playerId);
        shearsUnlocked.remove(playerId);
        shieldUnlocked.remove(playerId);
        killCounts.remove(playerId);
        playerPurchaseCounts.remove(playerId);
        lastCombatTimes.remove(playerId);
        lastDamagers.remove(playerId);
        lastDamagerTimes.remove(playerId);
        lastDamageTimes.remove(playerId);
        lastRegenTimes.remove(playerId);
        cancelRespawnCountdown(playerId);
        removeRespawnProtection(playerId);
        BukkitTask respawnTask = respawnTasks.remove(playerId);
        if (respawnTask != null) {
            respawnTask.cancel();
        }
        restoreSidebar(playerId);
        clearUpgradeEffects(player);
        fakeEnderChests.remove(playerId);
        if (state == GameState.LOBBY) {
            updateTeamsInMatch();
        }
        return true;
    }

    public boolean forceJoin(Player player, TeamColor team) {
        if (player == null || team == null) {
            return false;
        }
        if (!isActive()) {
            return false;
        }
        if (state != GameState.LOBBY && !teamsInMatch.contains(team)) {
            return false;
        }
        Location spawn = null;
        if (state != GameState.LOBBY) {
            spawn = arena.getSpawn(team);
            if (spawn == null) {
                return false;
            }
        }
        UUID playerId = player.getUniqueId();
        removeEditor(player);
        assignments.put(playerId, team);
        eliminatedPlayers.remove(playerId);
        pendingRespawns.remove(playerId);
        respawnGracePlayers.remove(playerId);
        frozenPlayers.remove(playerId);
        cancelRespawnCountdown(playerId);
        removeRespawnProtection(playerId);
        BukkitTask respawnTask = respawnTasks.remove(playerId);
        if (respawnTask != null) {
            respawnTask.cancel();
        }
        applyLobbyBuffs(player);
        double maxHealth = player.getMaxHealth();
        player.setHealth(Math.max(1.0, maxHealth));

        if (state == GameState.LOBBY) {
            updateTeamsInMatch();
            Location lobby = arena.getMapLobbyLocation();
            if (lobby == null) {
                lobby = arena.getLobbyLocation();
            }
            if (lobby != null) {
                player.teleport(lobby);
            }
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return true;
        }

        player.teleport(spawn);
        player.getInventory().clear();
        giveStarterKit(player, team);
        applyPermanentItemsWithShield(player, team);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        if (state == GameState.STARTING) {
            frozenPlayers.add(playerId);
        } else if (state == GameState.RUNNING) {
            grantRespawnProtection(player);
            if (statsEnabled) {
                bedwarsManager.getStatsService().addGamePlayed(playerId);
            }
        }
        hideEditorsFrom(player);
        updateSidebarForPlayer(player);
        return true;
    }

    public boolean reviveBed(TeamColor team) {
        if (team == null) {
            return false;
        }
        if (!teamsInMatch.contains(team)) {
            return false;
        }
        if (getBedState(team) == BedState.ALIVE) {
            return false;
        }
        BedLocation location = arena.getBeds().get(team);
        World world = arena.getWorld();
        if (location == null || world == null) {
            return false;
        }
        bedStates.put(team, BedState.ALIVE);
        bedBlocks.put(location.head(), team);
        bedBlocks.put(location.foot(), team);
        restoreBed(world, team, location);
        eliminatedTeams.remove(team);
        return true;
    }

    public String skipNextPhase() {
        if (state != GameState.RUNNING) {
            return null;
        }
        if (!tier2Triggered) {
            triggerTierUpgrade(2);
            return "Generators II";
        }
        if (!tier3Triggered) {
            triggerTierUpgrade(3);
            return "Generators III";
        }
        if (!bedDestructionTriggered) {
            triggerBedDestructionEvent();
            return "Beds Destroyed";
        }
        if (!suddenDeathActive) {
            triggerSuddenDeath();
            return "Sudden Death";
        }
        if (!gameEndTriggered) {
            triggerGameEnd();
            return "Game End";
        }
        return null;
    }

    public void addEditor(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        editorPlayers.add(playerId);
        if (state == GameState.RUNNING) {
            applyEditorInvisibility(player);
            hideEditorFromParticipants(player);
        }
    }

    public void removeEditor(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!editorPlayers.remove(playerId)) {
            return;
        }
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        showEditorToParticipants(player);
    }

    public boolean isInCombat(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long last = lastCombatTimes.get(playerId);
        if (last == null) {
            return false;
        }
        return System.currentTimeMillis() - last <= 15000L;
    }

    public UUID getRecentDamager(UUID victimId) {
        if (victimId == null) {
            return null;
        }
        Long last = lastDamagerTimes.get(victimId);
        if (last == null || System.currentTimeMillis() - last > 15000L) {
            return null;
        }
        return lastDamagers.get(victimId);
    }

    public void recordCombat(UUID attackerId, UUID victimId) {
        long now = System.currentTimeMillis();
        if (attackerId != null) {
            lastCombatTimes.put(attackerId, now);
        }
        if (victimId != null) {
            lastCombatTimes.put(victimId, now);
            if (attackerId != null) {
                lastDamagers.put(victimId, attackerId);
                lastDamagerTimes.put(victimId, now);
            }
        }
    }

    public void recordDamage(UUID playerId) {
        if (playerId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        lastDamageTimes.put(playerId, now);
        lastRegenTimes.remove(playerId);
    }

    public void clearCombat(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastCombatTimes.remove(playerId);
        lastDamagers.remove(playerId);
        lastDamagerTimes.remove(playerId);
    }

    public int getPickaxeTier(UUID playerId) {
        return pickaxeTiers.getOrDefault(playerId, 0);
    }

    public int getArmorTier(UUID playerId) {
        return armorTiers.getOrDefault(playerId, 0);
    }

    public int getAxeTier(UUID playerId) {
        return axeTiers.getOrDefault(playerId, 0);
    }

    public boolean hasShieldUnlocked(UUID playerId) {
        return playerId != null && shieldUnlocked.contains(playerId);
    }

    public void syncToolTiers(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!isActive() || !isParticipant(playerId)) {
            return;
        }
        if (!isInArenaWorld(player.getWorld())) {
            return;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return;
        }
        int pickaxeTier = resolveToolTier(player, config, ShopItemBehavior.PICKAXE);
        int axeTier = resolveToolTier(player, config, ShopItemBehavior.AXE);
        updateToolTier(pickaxeTiers, playerId, pickaxeTier);
        updateToolTier(axeTiers, playerId, axeTier);
        if (hasInventoryItem(player, Material.SHEARS)) {
            shearsUnlocked.add(playerId);
        } else {
            shearsUnlocked.remove(playerId);
        }
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

    public boolean isInsideMap(BlockPoint point) {
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        if (corner1 == null || corner2 == null) {
            return true;
        }
        int minX = Math.min(corner1.x(), corner2.x());
        int maxX = Math.max(corner1.x(), corner2.x());
        int minY = Math.min(corner1.y(), corner2.y());
        int maxY = Math.max(corner1.y(), corner2.y());
        int minZ = Math.min(corner1.z(), corner2.z());
        int maxZ = Math.max(corner1.z(), corner2.z());
        return point.x() >= minX && point.x() <= maxX
                && point.y() >= minY && point.y() <= maxY
                && point.z() >= minZ && point.z() <= maxZ;
    }

    public void recordPlacedBlock(BlockPoint point) {
        recordPlacedBlock(point, null);
    }

    public void recordPlacedBlock(BlockPoint point, ItemStack item) {
        if (point == null) {
            return;
        }
        placedBlocks.add(point);
        if (item == null || item.getType() == Material.AIR) {
            placedBlockItems.remove(point);
            return;
        }
        ItemStack stored = item.clone();
        stored.setAmount(1);
        placedBlockItems.put(point, stored);
    }

    public void removePlacedBlock(BlockPoint point) {
        placedBlocks.remove(point);
        placedBlockItems.remove(point);
    }

    public ItemStack removePlacedBlockItem(BlockPoint point) {
        placedBlocks.remove(point);
        return placedBlockItems.remove(point);
    }

    public boolean isPendingRespawn(UUID playerId) {
        return pendingRespawns.contains(playerId);
    }

    public boolean isEliminated(UUID playerId) {
        return eliminatedPlayers.contains(playerId);
    }

    public void openFakeEnderChest(Player player) {
        Inventory inventory = getFakeEnderChest(player);
        player.openInventory(inventory);
    }

    public Inventory getFakeEnderChest(Player player) {
        return fakeEnderChests.computeIfAbsent(player.getUniqueId(),
                id -> Bukkit.createInventory(player, 27, Component.text("Ender Chest")));
    }

    public void openShop(Player player, ShopType type) {
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return;
        }
        if (type == ShopType.UPGRADES) {
            new UpgradeShopMenu(this, player).open(player);
            return;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            player.sendMessage(Component.text("Shop config is not loaded.", NamedTextColor.RED));
            return;
        }
        new ShopMenu(this, config, ShopCategoryType.QUICK_BUY, player).open(player);
    }

    public void openTeamPickMenu(Player player) {
        if (player == null) {
            return;
        }
        if (!isLobby() || !teamPickEnabled || !isInArenaWorld(player.getWorld())) {
            return;
        }
        new TeamPickMenu(this, player).open(player);
    }

    public boolean handleShopPurchase(Player player, ShopItemDefinition item) {
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return false;
        }
        if (!isRotatingItemAvailable(item)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(Component.text("This rotating item is not available in this match.", NamedTextColor.RED));
            return false;
        }
        if (item.getBehavior() == ShopItemBehavior.UPGRADE) {
            return handleUpgradePurchaseInternal(player, item.getUpgradeType());
        }
        ShopCost cost = item.getCost();
        if (cost == null || !cost.isValid() || !hasResources(player, cost)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        if (!canPurchaseLimitedItem(player, item)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(Component.text("Purchase limit reached.", NamedTextColor.RED));
            return false;
        }
        if (!applyPurchase(player, item)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        removeResources(player, cost);
        recordLimitedPurchase(player, item);
        if (item.getBehavior() == ShopItemBehavior.SHIELD) {
            equipShieldOffhand(player, getTeam(player.getUniqueId()));
            player.updateInventory();
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        return true;
    }

    public boolean handleUpgradePurchase(Player player, TeamUpgradeType type) {
        return handleUpgradePurchaseInternal(player, type);
    }

    private boolean handleUpgradePurchaseInternal(Player player, TeamUpgradeType type) {
        if (player == null || type == null) {
            return false;
        }
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return false;
        }
        if (!isRotatingUpgradeAvailable(type)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(Component.text("This rotating upgrade is not available in this match.", NamedTextColor.RED));
            return false;
        }
        if (type == TeamUpgradeType.GARRY) {
            return handleGarryUpgradePurchase(player);
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        TeamUpgradeState state = getUpgradeState(team);
        int currentTier = state.getTier(type);
        int cost = type.nextCost(currentTier);
        if (cost < 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        ShopCost shopCost = new ShopCost(Material.DIAMOND, cost);
        if (!hasResources(player, shopCost)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        int nextTier = currentTier + 1;
        state.setTier(type, nextTier);
        removeResources(player, shopCost);
        if (type == TeamUpgradeType.FORGE && generatorManager != null) {
            generatorManager.setBaseForgeTier(team, nextTier);
        }
        applyTeamUpgradeEffects(team);
        announceTeamUpgrade(team, player, type, nextTier);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        return true;
    }

    public boolean handleTrapPurchase(Player player, TrapType trap) {
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return false;
        }
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        TeamUpgradeState state = getUpgradeState(team);
        int cost = getTrapCost(team);
        if (cost < 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        ShopCost shopCost = new ShopCost(Material.DIAMOND, cost);
        if (!hasResources(player, shopCost)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        state.addTrap(trap);
        removeResources(player, shopCost);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        return true;
    }

    public boolean isGarryUnlocked() {
        return garryUnlocked;
    }

    public boolean isGarryWifeAlive() {
        return garryWifeAlive;
    }

    public boolean isGarryJrAlive() {
        return garryJrAlive;
    }

    public int getGarryNextCost() {
        if (suddenDeathActive) {
            return -1;
        }
        if (!garryUnlocked) {
            return 4;
        }
        if (!garryWifeAlive) {
            return garryJrAlive ? 8 : 6;
        }
        if (!garryJrAlive) {
            return 8;
        }
        return -1;
    }

    public String getGarryNextName() {
        if (suddenDeathActive) {
            return "Garry (Disabled)";
        }
        if (!garryUnlocked) {
            return "Garry";
        }
        if (!garryWifeAlive) {
            return "Garry's Wife";
        }
        if (!garryJrAlive) {
            return "Garry Jr.";
        }
        return "Garry";
    }

    private boolean handleGarryUpgradePurchase(Player player) {
        if (suddenDeathActive) {
            player.sendMessage(Component.text("Garry upgrades are disabled during Sudden Death.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        int cost = getGarryNextCost();
        if (cost <= 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        ShopCost shopCost = new ShopCost(Material.DIAMOND, cost);
        if (!hasResources(player, shopCost)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        org.bukkit.entity.Warden warden;
        String name;
        if (!garryUnlocked) {
            warden = spawnGarryWarden(GARRY_TAG, "Garry", "garry");
            if (warden == null) {
                player.sendMessage(Component.text("Garry cannot spawn here.", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return false;
            }
            garryUnlocked = true;
            garryId = warden.getUniqueId();
            name = "Garry";
        } else if (!garryWifeAlive) {
            warden = spawnGarryWarden(GARRY_WIFE_TAG, "Garry's Wife", "garry_wife");
            if (warden == null) {
                player.sendMessage(Component.text("Garry's Wife cannot spawn here.", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return false;
            }
            garryWifeAlive = true;
            garryWifeId = warden.getUniqueId();
            name = "Garry's Wife";
        } else if (!garryJrAlive) {
            warden = spawnGarryWarden(GARRY_JR_TAG, "Garry Jr.", "garry_jr");
            if (warden == null) {
                player.sendMessage(Component.text("Garry Jr. cannot spawn here.", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return false;
            }
            garryJrAlive = true;
            garryJrId = warden.getUniqueId();
            name = "Garry Jr.";
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        removeResources(player, shopCost);
        broadcast(Component.text(player.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" summoned ", NamedTextColor.GRAY))
                .append(Component.text(name, NamedTextColor.RED)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        return true;
    }

    public void handleGarryDeath(org.bukkit.entity.Entity entity, Player killer) {
        if (entity == null) {
            return;
        }
        String killerName = killer != null ? killer.getName() : "unknown";
        if (entity.getScoreboardTags().contains(GARRY_TAG)) {
            garryId = null;
            if (!garryUnlocked || suddenDeathActive) {
                return;
            }
            broadcast(Component.text("Garry has been killed by " + killerName
                    + " and will respawn in " + GARRY_RESPAWN_SECONDS + "s.", NamedTextColor.RED));
            scheduleGarryRespawn();
            return;
        }
        if (entity.getScoreboardTags().contains(GARRY_WIFE_TAG)) {
            garryWifeAlive = false;
            garryWifeId = null;
            broadcast(Component.text("Garry's Wife has been killed by " + killerName + ".", NamedTextColor.RED));
            return;
        }
        if (entity.getScoreboardTags().contains(GARRY_JR_TAG)) {
            garryJrAlive = false;
            garryJrId = null;
            broadcast(Component.text("Garry Jr. has been killed by " + killerName + ".", NamedTextColor.RED));
        }
    }

    public void updateGarryFamilyHealthDisplay(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        String name = resolveGarryFamilyName(entity);
        if (name == null) {
            return;
        }
        double current = Math.max(0.0, living.getHealth());
        double max = Math.max(0.0, living.getMaxHealth());
        Component title = Component.text(name, NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Health: ", NamedTextColor.GRAY))
                .append(Component.text(formatHealthValue(current), NamedTextColor.GREEN))
                .append(Component.text("/", NamedTextColor.DARK_GRAY))
                .append(Component.text(formatHealthValue(max), NamedTextColor.GREEN));
        living.customName(title);
        living.setCustomNameVisible(true);
    }

    private void scheduleGarryRespawn() {
        if (garryRespawnTask != null) {
            garryRespawnTask.cancel();
        }
        garryRespawnTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> safeRun("garryRespawn", () -> {
            if (state != GameState.RUNNING || suddenDeathActive) {
                return;
            }
            org.bukkit.entity.Warden warden = spawnGarryWarden(GARRY_TAG, "Garry", "garry");
            if (warden != null) {
                garryId = warden.getUniqueId();
            }
        }), GARRY_RESPAWN_SECONDS * 20L);
    }

    private void despawnGarryWardens() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(GARRY_TAG)
                    || entity.getScoreboardTags().contains(GARRY_WIFE_TAG)
                    || entity.getScoreboardTags().contains(GARRY_JR_TAG)) {
                entity.remove();
            }
        }
        garryId = null;
        garryWifeId = null;
        garryJrId = null;
        garryWifeAlive = false;
        garryJrAlive = false;
        if (garryRespawnTask != null) {
            garryRespawnTask.cancel();
            garryRespawnTask = null;
        }
    }

    private org.bukkit.entity.Warden spawnGarryWarden(String tag, String name, String customId) {
        World world = arena.getWorld();
        BlockPoint center = arena.getCenter();
        if (world == null || center == null) {
            return null;
        }
        Location spawn = center.toLocation(world);
        spawn.setY(center.y() + 1.0);
        org.bukkit.entity.Warden warden = world.spawn(spawn, org.bukkit.entity.Warden.class, entity -> {
            entity.setRemoveWhenFarAway(false);
            entity.customName(Component.text(name, NamedTextColor.RED));
            entity.setCustomNameVisible(true);
            entity.addScoreboardTag(tag);
        });
        CustomItemDefinition custom = bedwarsManager.getCustomItemConfig().getItem(customId);
        applyCustomEntityStats(warden, custom);
        applyGarryFamilyHealthOverride(warden);
        applyGarryFamilyRangeOverride(warden);
        updateGarryFamilyHealthDisplay(warden);
        return warden;
    }

    private void applyGarryFamilyHealthOverride(org.bukkit.entity.Warden warden) {
        if (warden == null) {
            return;
        }
        double health = bedwarsManager.getGarryFamilyHealth();
        if (health <= 0.0) {
            return;
        }
        warden.setMaxHealth(health);
        warden.setHealth(Math.min(health, warden.getMaxHealth()));
    }

    private void applyGarryFamilyRangeOverride(org.bukkit.entity.Warden warden) {
        if (warden == null) {
            return;
        }
        double range = bedwarsManager.getGarryFamilyRange();
        if (range <= 0.0) {
            return;
        }
        applyAttribute(warden, "GENERIC_FOLLOW_RANGE", range);
    }

    private void applyCustomEntityStats(LivingEntity entity, CustomItemDefinition custom) {
        if (entity == null || custom == null) {
            return;
        }
        double health = custom.getHealth();
        if (health > 0.0) {
            entity.setMaxHealth(health);
            entity.setHealth(Math.min(health, entity.getMaxHealth()));
        }
        double speed = custom.getSpeed();
        if (speed > 0.0) {
            applyAttribute(entity, "GENERIC_MOVEMENT_SPEED", speed);
            applyAttribute(entity, "GENERIC_FLYING_SPEED", speed);
        }
        double range = custom.getRange();
        if (range > 0.0) {
            applyAttribute(entity, "GENERIC_FOLLOW_RANGE", range);
        }
    }

    private String resolveGarryFamilyName(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getScoreboardTags().contains(GARRY_TAG)) {
            return "Garry";
        }
        if (entity.getScoreboardTags().contains(GARRY_WIFE_TAG)) {
            return "Garry's Wife";
        }
        if (entity.getScoreboardTags().contains(GARRY_JR_TAG)) {
            return "Garry Jr.";
        }
        return null;
    }

    private String formatHealthValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void applyAttribute(LivingEntity entity, String attributeName, double value) {
        if (entity == null || attributeName == null || attributeName.isBlank()) {
            return;
        }
        try {
            org.bukkit.attribute.Attribute attribute = org.bukkit.attribute.Attribute.valueOf(attributeName);
            org.bukkit.attribute.AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(value);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private boolean canPurchaseLimitedItem(Player player, ShopItemDefinition item) {
        ShopItemLimit limit = item.getLimit();
        if (limit == null || !limit.isValid()) {
            return true;
        }
        ShopItemLimitScope scope = limit.scope();
        String itemId = item.getId();
        if (scope == ShopItemLimitScope.PLAYER) {
            Map<String, Integer> counts = playerPurchaseCounts.get(player.getUniqueId());
            int current = counts != null ? counts.getOrDefault(itemId, 0) : 0;
            return current < limit.amount();
        }
        if (scope == ShopItemLimitScope.TEAM) {
            TeamColor team = getTeam(player.getUniqueId());
            if (team == null) {
                return false;
            }
            Map<String, Integer> counts = teamPurchaseCounts.get(team);
            int current = counts != null ? counts.getOrDefault(itemId, 0) : 0;
            return current < limit.amount();
        }
        return true;
    }

    private void recordLimitedPurchase(Player player, ShopItemDefinition item) {
        ShopItemLimit limit = item.getLimit();
        if (limit == null || !limit.isValid()) {
            return;
        }
        ShopItemLimitScope scope = limit.scope();
        String itemId = item.getId();
        if (scope == ShopItemLimitScope.PLAYER) {
            playerPurchaseCounts
                    .computeIfAbsent(player.getUniqueId(), key -> new HashMap<>())
                    .merge(itemId, 1, Integer::sum);
            return;
        }
        if (scope == ShopItemLimitScope.TEAM) {
            TeamColor team = getTeam(player.getUniqueId());
            if (team == null) {
                return;
            }
            teamPurchaseCounts
                    .computeIfAbsent(team, key -> new HashMap<>())
                    .merge(itemId, 1, Integer::sum);
        }
    }

    public int getUpgradeTier(TeamColor team, TeamUpgradeType type) {
        if (team == null) {
            return 0;
        }
        return getUpgradeState(team).getTier(type);
    }

    public int getTrapCost(TeamColor team) {
        if (team == null) {
            return -1;
        }
        TeamUpgradeState state = getUpgradeState(team);
        int count = state.getTrapCount();
        if (count >= TRAP_MAX_COUNT) {
            return -1;
        }
        return switch (count) {
            case 0 -> 1;
            case 1 -> 2;
            default -> 4;
        };
    }

    public List<TrapType> getActiveTraps(TeamColor team) {
        if (team == null) {
            return List.of();
        }
        return getUpgradeState(team).getTraps();
    }

    public void grantTrapImmunity(UUID playerId, int seconds) {
        if (playerId == null) {
            return;
        }
        clearTrapImmunity(playerId);
        if (seconds <= 0) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + seconds * 1000L;
        trapImmunityEnds.put(playerId, expiresAt);
        if (plugin == null) {
            return;
        }
        long delayTicks = Math.max(1L, seconds * 20L);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                safeRun("trapImmunityExpire", () -> {
                    Long end = trapImmunityEnds.get(playerId);
                    if (end == null || end > System.currentTimeMillis()) {
                        return;
                    }
                    trapImmunityEnds.remove(playerId);
                    trapImmunityTasks.remove(playerId);
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(Component.text("Magic Milk expired. Traps can trigger again.", NamedTextColor.RED));
                    }
                }), delayTicks);
        trapImmunityTasks.put(playerId, task);
    }

    public void clearTrapImmunity(UUID playerId) {
        if (playerId == null) {
            return;
        }
        trapImmunityEnds.remove(playerId);
        BukkitTask task = trapImmunityTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public boolean hasTrapImmunity(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long expiresAt = trapImmunityEnds.get(playerId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            trapImmunityEnds.remove(playerId);
            return false;
        }
        return true;
    }

    private boolean applyPurchase(Player player, ShopItemDefinition item) {
        TeamColor team = getTeam(player.getUniqueId());
        ShopItemBehavior behavior = item.getBehavior();
        return switch (behavior) {
            case BLOCK, UTILITY, POTION -> {
                giveItem(player, createPurchaseItem(item, team));
                if (behavior == ShopItemBehavior.UTILITY
                        && team != null
                        && hasTeamUpgrade(team, TeamUpgradeType.FIRE_ASPECT)
                        && ATTACK_MATERIALS.contains(item.getMaterial())) {
                    applyFireAspect(player);
                }
                yield true;
            }
            case SWORD -> {
                if (item.getMaterial() != Material.WOODEN_SWORD) {
                    removeItems(player, WOODEN_SWORD_ONLY);
                }
                giveItem(player, createPurchaseItem(item, team));
                if (team != null && hasTeamUpgrade(team, TeamUpgradeType.SHARPNESS)) {
                    applySharpness(player);
                }
                if (team != null && hasTeamUpgrade(team, TeamUpgradeType.FIRE_ASPECT)) {
                    applyFireAspect(player);
                }
                yield true;
            }
            case BOW -> {
                removeItems(player, BOW_MATERIALS);
                giveItem(player, createPurchaseItem(item, team));
                yield true;
            }
            case CROSSBOW -> {
                if (countItem(player, Material.CROSSBOW) >= 3) {
                    yield false;
                }
                giveItem(player, createPurchaseItem(item, team));
                yield true;
            }
            case ARMOR -> applyTierUpgrade(player, item, armorTiers, ShopItemBehavior.ARMOR);
            case PICKAXE -> applyTierUpgrade(player, item, pickaxeTiers, ShopItemBehavior.PICKAXE);
            case AXE -> applyTierUpgrade(player, item, axeTiers, ShopItemBehavior.AXE);
            case SHEARS -> applyShears(player, item, team);
            case SHIELD -> applyShield(player);
            case UPGRADE -> handleUpgradePurchaseInternal(player, item.getUpgradeType());
        };
    }

    private ItemStack createPurchaseItem(ShopItemDefinition item, TeamColor team) {
        ItemStack stack = item.createPurchaseItem(team);
        applyCustomItemMetadata(stack, item);
        return stack;
    }

    private void applyCustomItemMetadata(ItemStack item, ShopItemDefinition definition) {
        if (item == null || definition == null) {
            return;
        }
        String customId = definition.getCustomItemId();
        if (customId == null || customId.isBlank()) {
            return;
        }
        CustomItemDefinition custom = bedwarsManager.getCustomItemConfig().getItem(customId);
        if (custom == null) {
            return;
        }
        if (custom.getUses() > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return;
            }
            CustomItemData.setUses(meta, custom.getUses());
            updateUsesLore(meta, custom.getUses());
            item.setItemMeta(meta);
        }
    }

    private void updateUsesLore(ItemMeta meta, int uses) {
        if (meta == null) {
            return;
        }
        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }
        Component line = Component.text("Uses: " + uses, NamedTextColor.GRAY);
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            Component existing = lore.get(i);
            if (existing == null) {
                continue;
            }
            String plain = PlainTextComponentSerializer.plainText().serialize(existing);
            if (plain != null && plain.toLowerCase(Locale.ROOT).startsWith("uses:")) {
                lore.set(i, line);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            lore.add(line);
        }
        meta.lore(lore);
    }

    public void startLobby(JavaPlugin plugin, Player initiator, int lobbySeconds) {
        this.plugin = plugin;
        stopInternal();
        resetState();
        state = GameState.LOBBY;
        lobbyInitiatorId = initiator != null ? initiator.getUniqueId() : null;
        prepareWorld();
        updateTeamsInMatch();
        initializeTeamUpgrades();
        initializeBaseCenters();
        initializeBeds();
        applyBedLayout();

        lobbyCountdownRemaining = Math.max(0, lobbySeconds);
        lobbyCountdownPaused = false;
        startCountdownRemaining = lobbyCountdownRemaining;

        Location lobby = resolveMapLobbyLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInArenaWorld(player.getWorld())) {
                continue;
            }
            if (lobby != null) {
                player.teleport(lobby);
                player.setRespawnLocation(lobby, true);
            }
            player.getInventory().clear();
            applyLobbyBuffs(player);
            double maxHealth = player.getMaxHealth();
            player.setHealth(Math.max(1.0, maxHealth));
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            if (isLobbyInitiator(player.getUniqueId())) {
                giveLobbyControlItems(player);
            }
            if (teamPickEnabled) {
                giveTeamSelectItem(player);
            }
        }

        startLobbyCountdown();
        startSidebarUpdates();
    }

    public void start(JavaPlugin plugin, Player initiator) {
        start(plugin, initiator, START_COUNTDOWN_SECONDS);
    }

    public void start(JavaPlugin plugin, Player initiator, int countdownSeconds) {
        this.plugin = plugin;
        stopInternal();
        resetState();
        state = GameState.STARTING;
        prepareWorld();
        updateTeamsInMatch();
        initializeTeamUpgrades();
        initializeBaseCenters();
        initializeBeds();
        applyBedLayout();
        rollRotatingItems();
        spawnShops();
        startCountdownRemaining = Math.max(0, countdownSeconds);

        Location respawnLobby = resolveMapLobbyLocation();
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
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            double maxHealth = player.getMaxHealth();
            player.setHealth(Math.max(1.0, maxHealth));
            giveStarterKit(player, team);
            applyPermanentItemsWithShield(player, team);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            frozenPlayers.add(player.getUniqueId());
            player.sendMessage(Component.text("You are on the ").append(team.displayComponent()).append(Component.text(" team.")));
            if (respawnLobby != null) {
                player.setRespawnLocation(respawnLobby, true);
            }
        }

        startCountdown(countdownSeconds);
        startSidebarUpdates();
    }

    public int getLobbyCountdownRemaining() {
        return lobbyCountdownRemaining;
    }

    public boolean isLobbyCountdownPaused() {
        return lobbyCountdownPaused;
    }

    public void toggleLobbyCountdownPause() {
        if (lobbyCountdownTask == null) {
            return;
        }
        lobbyCountdownPaused = !lobbyCountdownPaused;
    }

    public void skipLobbyCountdown() {
        if (lobbyCountdownTask == null) {
            return;
        }
        lobbyCountdownRemaining = 0;
        lobbyCountdownPaused = false;
        beginMatchFromLobby();
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
        clearCombat(player.getUniqueId());
        clearTrapImmunity(player.getUniqueId());
        removeRespawnProtection(player.getUniqueId());
        downgradeTools(player.getUniqueId());
        removeItems(player, PICKAXE_MATERIALS);
        removeItems(player, AXE_MATERIALS);
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
        Location lobby = resolveMapLobbyLocation();
        if (getBedState(team) == BedState.DESTROYED && eliminatedPlayers.contains(playerId)) {
            setSpectator(player);
            if (lobby != null) {
                player.teleport(lobby);
            }
            return;
        }
        if (!pendingRespawns.contains(playerId)) {
            return;
        }
        setSpectator(player);
        if (lobby != null) {
            player.teleport(lobby);
        }
        boolean allowRespawnAfterBedBreak = respawnGracePlayers.contains(playerId);
        if (getBedState(team) == BedState.DESTROYED && !allowRespawnAfterBedBreak) {
            pendingRespawns.remove(playerId);
            eliminatePlayer(player, team);
            return;
        }
        scheduleRespawn(player, team, RESPAWN_DELAY_SECONDS, allowRespawnAfterBedBreak);
    }

    public void handleBedDestroyed(TeamColor team, Player breaker) {
        if (team == null || getBedState(team) == BedState.DESTROYED) {
            return;
        }
        if (statsEnabled && breaker != null && isParticipant(breaker.getUniqueId())) {
            bedwarsManager.getStatsService().addBedBroken(breaker.getUniqueId());
        }
        destroyBed(team);
        broadcast(Component.text("The ", NamedTextColor.RED)
                .append(team.displayComponent())
                .append(Component.text(" bed was destroyed!", NamedTextColor.RED)));
        playSoundToParticipants(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);

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

    public boolean triggerRespawnBeacon(Player activator, int delaySeconds) {
        if (activator == null || !isRunning()) {
            return false;
        }
        UUID activatorId = activator.getUniqueId();
        if (!isParticipant(activatorId)) {
            return false;
        }
        TeamColor team = getTeam(activatorId);
        if (team == null) {
            return false;
        }
        if (getBedState(team) != BedState.DESTROYED) {
            activator.sendMessage(Component.text("Your bed is still alive.", NamedTextColor.RED));
            return false;
        }
        if (getTeamMemberCount(team) != 2) {
            activator.sendMessage(Component.text("Respawn Beacon can only be used in 2-player teams.", NamedTextColor.RED));
            return false;
        }
        UUID targetId = null;
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() != team) {
                continue;
            }
            UUID playerId = entry.getKey();
            if (playerId.equals(activatorId)) {
                continue;
            }
            if (eliminatedPlayers.contains(playerId)) {
                targetId = playerId;
                break;
            }
        }
        if (targetId == null) {
            activator.sendMessage(Component.text("No eliminated teammate to respawn.", NamedTextColor.RED));
            return false;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            activator.sendMessage(Component.text("Your teammate must be online to respawn.", NamedTextColor.RED));
            return false;
        }
        eliminatedPlayers.remove(targetId);
        respawnGracePlayers.add(targetId);
        setSpectator(target);
        Location lobby = arena.getLobbyLocation();
        if (lobby != null) {
            target.teleport(lobby);
        }
        scheduleRespawn(target, team, delaySeconds, true);
        return true;
    }

    private void playSoundToParticipants(Sound sound, float volume, float pitch) {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    public void handlePlayerEliminated(Player player) {
        TeamColor team = getTeam(player.getUniqueId());
        if (team == null) {
            return;
        }
        eliminatePlayer(player, team);
    }

    private void startCountdown(int seconds) {
        if (seconds <= 0) {
            beginRunning();
            return;
        }
        BukkitTask task = new BukkitRunnable() {
            private int remaining = seconds;

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

    private void startLobbyCountdown() {
        if (lobbyCountdownTask != null) {
            lobbyCountdownTask.cancel();
        }
        lobbyCountdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                safeRun("lobbyCountdown", () -> {
                    if (state != GameState.LOBBY) {
                        cancel();
                        return;
                    }
                    if (lobbyCountdownPaused) {
                        return;
                    }
                    if (lobbyCountdownRemaining <= 0) {
                        showTitleAll(Component.text("GO!", NamedTextColor.GREEN), Component.empty());
                        beginMatchFromLobby();
                        cancel();
                        return;
                    }
                    startCountdownRemaining = lobbyCountdownRemaining;
                    showTitleAll(Component.text("Starting in " + lobbyCountdownRemaining, NamedTextColor.YELLOW), Component.empty());
                    lobbyCountdownRemaining--;
                });
            }
        }.runTaskTimer(plugin, 0L, 20L);
        tasks.add(lobbyCountdownTask);
    }

    private void beginMatchFromLobby() {
        if (lobbyCountdownTask != null) {
            lobbyCountdownTask.cancel();
            lobbyCountdownTask = null;
        }
        Player initiator = lobbyInitiatorId != null ? Bukkit.getPlayer(lobbyInitiatorId) : null;
        if (initiator == null) {
            initiator = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        }
        if (initiator == null) {
            stop();
            return;
        }
        start(plugin, initiator, 0);
    }

    private void beginRunning() {
        state = GameState.RUNNING;
        frozenPlayers.clear();
        matchStartMillis = System.currentTimeMillis();
        startCountdownRemaining = 0;
        if (statsEnabled) {
            for (UUID playerId : assignments.keySet()) {
                bedwarsManager.getStatsService().addGamePlayed(playerId);
            }
        }
        generatorManager = new GeneratorManager(plugin, arena, this);
        syncForgeTiers();
        generatorManager.start();
        startUpgradeTasks();
        startRegenTask();
        scheduleGameEvents();
    }

    private void scheduleGameEvents() {
        krispasi.omGames.bedwars.model.EventSettings events = arena.getEventSettings();
        scheduleTierUpgrade(2, events.getTier2Delay());
        scheduleTierUpgrade(3, events.getTier3Delay());
        scheduleBedDestruction(events.getBedDestructionDelay());
        scheduleSuddenDeath(events.getSuddenDeathDelay());
        scheduleGameEnd(events.getGameEndDelay());
    }

    private void triggerTierUpgrade(int tier) {
        if (state != GameState.RUNNING || generatorManager == null) {
            return;
        }
        int clamped = Math.max(1, Math.min(3, tier));
        if (clamped <= 1) {
            return;
        }
        if (clamped == 2 && tier2Triggered) {
            return;
        }
        if (clamped == 3 && tier3Triggered) {
            return;
        }
        if (clamped >= 2) {
            tier2Triggered = true;
        }
        if (clamped >= 3) {
            tier3Triggered = true;
        }
        generatorManager.setDiamondTier(clamped);
        generatorManager.setEmeraldTier(clamped);
        generatorManager.refresh();
        broadcast(Component.text("Generators " + toRoman(clamped) + "!", NamedTextColor.GOLD));
    }

    private void scheduleTierUpgrade(int tier, int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("tierUpgrade", () -> {
                triggerTierUpgrade(tier);
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
                triggerBedDestructionEvent();
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleSuddenDeath(int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("suddenDeath", () -> {
                triggerSuddenDeath();
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void scheduleGameEnd(int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("gameEnd", () -> {
                triggerGameEnd();
            });
        }, delaySeconds * 20L);
        tasks.add(task);
    }

    private void triggerBedDestructionEvent() {
        if (state != GameState.RUNNING || bedDestructionTriggered) {
            return;
        }
        bedDestructionTriggered = true;
        triggerBedDestruction();
    }

    private void triggerSuddenDeath() {
        if (state != GameState.RUNNING || suddenDeathActive) {
            return;
        }
        suddenDeathActive = true;
        despawnGarryWardens();
        showTitleAll(Component.text("Sudden Death!", NamedTextColor.RED),
                Component.text("Final battle begins.", NamedTextColor.GRAY));
        broadcast(Component.text("Sudden Death has begun!", NamedTextColor.RED));
        startSuddenDeathBorderShrink();
    }

    private void triggerGameEnd() {
        if (state != GameState.RUNNING || gameEndTriggered) {
            return;
        }
        gameEndTriggered = true;
        List<TeamColor> aliveTeams = getAliveTeams();
        if (aliveTeams.size() == 1) {
            bedwarsManager.endSession(this, aliveTeams.get(0));
            return;
        }
        bedwarsManager.endSession(this, null);
    }

    private void startSuddenDeathBorderShrink() {
        World world = arena.getWorld();
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        BlockPoint center = arena.getCenter();
        if (world == null || corner1 == null || corner2 == null || center == null) {
            return;
        }
        EventSettings events = arena.getEventSettings();
        int remainingSeconds = Math.max(1, events.getGameEndDelay() - events.getSuddenDeathDelay());
        WorldBorder border = world.getWorldBorder();
        double targetSize = SUDDEN_DEATH_BORDER_TARGET_SIZE;
        border.setCenter(center.x() + 0.5, center.z() + 0.5);
        border.setSize(Math.max(targetSize, 1.0), remainingSeconds);
        border.setDamageBuffer(WORLD_BORDER_DAMAGE_BUFFER);
        border.setDamageAmount(WORLD_BORDER_DAMAGE_AMOUNT);
        border.setWarningDistance(WORLD_BORDER_WARNING_DISTANCE);
    }

    private void scheduleRespawn(Player player, TeamColor team) {
        scheduleRespawn(player, team, RESPAWN_DELAY_SECONDS, false);
    }

    private void scheduleRespawn(Player player, TeamColor team, int delaySeconds, boolean allowRespawnAfterBedBreak) {
        Location spawn = arena.getSpawn(team);
        if (spawn == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        BukkitTask existing = respawnTasks.remove(player.getUniqueId());
        if (existing != null) {
            existing.cancel();
        }
        BukkitTask existingCountdown = respawnCountdownTasks.remove(player.getUniqueId());
        if (existingCountdown != null) {
            existingCountdown.cancel();
        }
        startRespawnCountdown(player, delaySeconds);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> safeRun("respawn", () -> {
            if (state != GameState.RUNNING || (getBedState(team) == BedState.DESTROYED && !allowRespawnAfterBedBreak)) {
                eliminatedPlayers.add(playerId);
                pendingRespawns.remove(playerId);
                setSpectator(player);
                removeRespawnProtection(playerId);
                cancelRespawnCountdown(playerId);
                respawnGracePlayers.remove(playerId);
                respawnTasks.remove(player.getUniqueId());
                checkTeamEliminated(team);
                return;
            }
            pendingRespawns.remove(playerId);
            player.teleport(spawn);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.getInventory().clear();
            giveStarterKit(player, team);
            applyPermanentItemsWithShield(player, team);
            grantRespawnProtection(player);
            cancelRespawnCountdown(playerId);
            respawnGracePlayers.remove(playerId);
            respawnTasks.remove(player.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> applyPermanentItemsWithShield(player, team),
                    1L);
        }), Math.max(0, delaySeconds) * 20L);
        respawnTasks.put(player.getUniqueId(), task);
        showTitle(player, Component.text("Respawning in " + delaySeconds, NamedTextColor.YELLOW), Component.empty());
    }

    private void eliminatePlayer(Player player, TeamColor team) {
        UUID playerId = player.getUniqueId();
        eliminatedPlayers.add(playerId);
        pendingRespawns.remove(playerId);
        respawnGracePlayers.remove(playerId);
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
        removeHealPoolEffects(team);
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
        clearContainerInventories(world);
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
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
          clearUpgradeEffects(player);
          if (lobby != null) {
              player.teleport(lobby);
              player.setRespawnLocation(lobby, true);
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

    private void prepareWorld() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        forceLoadMap(world);
        clearDroppedItems(world);
        applyWorldBorder(world);
    }

    private void forceLoadMap(World world) {
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        if (corner1 == null || corner2 == null) {
            return;
        }
        int minX = Math.min(corner1.x(), corner2.x());
        int maxX = Math.max(corner1.x(), corner2.x());
        int minZ = Math.min(corner1.z(), corner2.z());
        int maxZ = Math.max(corner1.z(), corner2.z());
        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                world.setChunkForceLoaded(chunkX, chunkZ, true);
                world.getChunkAt(chunkX, chunkZ);
                forcedChunks.add(chunkKey(chunkX, chunkZ));
            }
        }
    }

    private void releaseForcedChunks() {
        if (forcedChunks.isEmpty()) {
            return;
        }
        World world = arena.getWorld();
        if (world == null) {
            forcedChunks.clear();
            return;
        }
        for (long key : forcedChunks) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            world.setChunkForceLoaded(chunkX, chunkZ, false);
        }
        forcedChunks.clear();
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
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
        clearEditors();
        releaseForcedChunks();
        restoreWorldBorder();
        despawnShops();
        despawnSummons();
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
        for (BukkitTask task : trapImmunityTasks.values()) {
            task.cancel();
        }
        trapImmunityTasks.clear();
        trapImmunityEnds.clear();
        pendingRespawns.clear();
        respawnGracePlayers.clear();
        if (generatorManager != null) {
            generatorManager.stop();
            generatorManager = null;
        }
        frozenPlayers.clear();
        sidebarTask = null;
        if (lobbyCountdownTask != null) {
            lobbyCountdownTask.cancel();
        }
        lobbyCountdownTask = null;
        lobbyCountdownPaused = false;
        lobbyCountdownRemaining = 0;
        lobbyInitiatorId = null;
        matchStartMillis = 0L;
        startCountdownRemaining = 0;
    }

    private void applyWorldBorder(World world) {
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        if (world == null || corner1 == null || corner2 == null) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        if (previousBorderSize == null) {
            previousBorderSize = border.getSize();
            previousBorderCenter = border.getCenter();
            previousBorderDamageAmount = border.getDamageAmount();
            previousBorderDamageBuffer = border.getDamageBuffer();
            previousBorderWarningDistance = border.getWarningDistance();
        }
        int minX = Math.min(corner1.x(), corner2.x());
        int maxX = Math.max(corner1.x(), corner2.x());
        int minZ = Math.min(corner1.z(), corner2.z());
        int maxZ = Math.max(corner1.z(), corner2.z());
        double centerX = (minX + maxX) / 2.0 + 0.5;
        double centerZ = (minZ + maxZ) / 2.0 + 0.5;
        double sizeX = (maxX - minX) + 1.0;
        double sizeZ = (maxZ - minZ) + 1.0;
        double size = Math.max(sizeX, sizeZ) + 2.0;
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        border.setDamageBuffer(WORLD_BORDER_DAMAGE_BUFFER);
        border.setDamageAmount(WORLD_BORDER_DAMAGE_AMOUNT);
        border.setWarningDistance(WORLD_BORDER_WARNING_DISTANCE);
    }

    private void restoreWorldBorder() {
        World world = arena.getWorld();
        if (world == null || previousBorderSize == null || previousBorderCenter == null) {
            return;
        }
        WorldBorder border = world.getWorldBorder();
        border.setCenter(previousBorderCenter);
        border.setSize(previousBorderSize);
        if (previousBorderDamageAmount != null) {
            border.setDamageAmount(previousBorderDamageAmount);
        }
        if (previousBorderDamageBuffer != null) {
            border.setDamageBuffer(previousBorderDamageBuffer);
        }
        if (previousBorderWarningDistance != null) {
            border.setWarningDistance(previousBorderWarningDistance);
        }
        previousBorderSize = null;
        previousBorderCenter = null;
        previousBorderDamageAmount = null;
        previousBorderDamageBuffer = null;
        previousBorderWarningDistance = null;
    }

    private void despawnSummons() {
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(BED_BUG_TAG)
                    || entity.getScoreboardTags().contains(DREAM_DEFENDER_TAG)
                    || entity.getScoreboardTags().contains(CRYSTAL_TAG)
                    || entity.getScoreboardTags().contains(HAPPY_GHAST_TAG)
                    || entity.getScoreboardTags().contains(CREEPING_CREEPER_TAG)
                    || entity.getScoreboardTags().contains(GARRY_TAG)
                    || entity.getScoreboardTags().contains(GARRY_WIFE_TAG)
                    || entity.getScoreboardTags().contains(GARRY_JR_TAG)) {
                entity.remove();
            }
        }
    }

    private void clearContainerInventories(World world) {
        BlockPoint corner1 = arena.getCorner1();
        BlockPoint corner2 = arena.getCorner2();
        if (corner1 == null || corner2 == null) {
            for (Chunk chunk : world.getLoadedChunks()) {
                clearChunkContainers(chunk);
            }
            return;
        }
        int minX = Math.min(corner1.x(), corner2.x());
        int maxX = Math.max(corner1.x(), corner2.x());
        int minZ = Math.min(corner1.z(), corner2.z());
        int maxZ = Math.max(corner1.z(), corner2.z());
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                clearChunkContainers(chunk);
            }
        }
    }

    private void clearChunkContainers(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Container container) {
                container.getInventory().clear();
            }
        }
    }

    private void resetState() {
        clearEditors();
        eliminatedPlayers.clear();
        eliminatedTeams.clear();
        placedBlocks.clear();
        placedBlockItems.clear();
        forcedChunks.clear();
        bedStates.clear();
        bedBlocks.clear();
        fakeEnderChests.clear();
        previousScoreboards.clear();
        activeScoreboards.clear();
        sidebarLines.clear();
        rotatingItemIds.clear();
        killCounts.clear();
        teamPurchaseCounts.clear();
        playerPurchaseCounts.clear();
        respawnProtectionEnds.clear();
        respawnProtectionTasks.clear();
        teamsInMatch.clear();
        shopNpcIds.clear();
        teamUpgrades.clear();
        baseCenters.clear();
        baseOccupants.clear();
        armorTiers.clear();
        pickaxeTiers.clear();
        axeTiers.clear();
        shearsUnlocked.clear();
        shieldUnlocked.clear();
        lastCombatTimes.clear();
        lastDamagers.clear();
        lastDamagerTimes.clear();
        lastDamageTimes.clear();
        lastRegenTimes.clear();
        trapImmunityEnds.clear();
        trapImmunityTasks.clear();
        garryUnlocked = false;
        garryWifeAlive = false;
        garryJrAlive = false;
        garryId = null;
        garryWifeId = null;
        garryJrId = null;
        if (garryRespawnTask != null) {
            garryRespawnTask.cancel();
            garryRespawnTask = null;
        }
        suddenDeathActive = false;
        tier2Triggered = false;
        tier3Triggered = false;
        bedDestructionTriggered = false;
        gameEndTriggered = false;
    }

    public Set<String> getRotatingItemIds() {
        return Collections.unmodifiableSet(rotatingItemIds);
    }

    public RotatingSelectionMode getRotatingMode() {
        return rotatingMode;
    }

    public void setRotatingMode(RotatingSelectionMode mode) {
        if (mode == null) {
            return;
        }
        rotatingMode = mode;
        if (rotatingMode == RotatingSelectionMode.MANUAL) {
            syncManualRotatingSelection();
        } else {
            rotatingItemIds.clear();
        }
    }

    public RotatingSelectionMode cycleRotatingMode() {
        RotatingSelectionMode[] values = RotatingSelectionMode.values();
        int next = (rotatingMode.ordinal() + 1) % values.length;
        setRotatingMode(values[next]);
        return rotatingMode;
    }

    public List<String> getManualRotatingItemIds() {
        return Collections.unmodifiableList(manualRotatingItemIds);
    }

    public boolean toggleManualRotatingItem(String id) {
        String normalized = normalizeItemId(id);
        if (normalized == null) {
            return false;
        }
        if (!isRotatingCandidate(normalized)) {
            return false;
        }
        if (manualRotatingItemIds.remove(normalized)) {
            syncManualRotatingSelection();
            return true;
        }
        if (manualRotatingItemIds.size() >= 2) {
            return false;
        }
        manualRotatingItemIds.add(normalized);
        syncManualRotatingSelection();
        return true;
    }

    public void seedManualRotatingItemsIfEmpty() {
        if (!manualRotatingItemIds.isEmpty()) {
            return;
        }
        List<String> candidates = getRotatingCandidateIds();
        if (candidates.isEmpty()) {
            return;
        }
        Collections.shuffle(candidates);
        int count = Math.min(2, candidates.size());
        for (int i = 0; i < count; i++) {
            manualRotatingItemIds.add(candidates.get(i));
        }
        syncManualRotatingSelection();
    }

    public List<String> getRotatingCandidateIds() {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return List.of();
        }
        krispasi.omGames.bedwars.shop.ShopCategory category = config.getCategory(ShopCategoryType.ROTATING);
        if (category == null || category.getEntries().isEmpty()) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        for (String id : category.getEntries().values()) {
            String normalized = normalizeItemId(id);
            if (normalized != null && !candidates.contains(normalized)) {
                candidates.add(normalized);
            }
        }
        return candidates;
    }

    public boolean isRotatingItemAvailable(ShopItemDefinition item) {
        if (item == null) {
            return false;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return true;
        }
        krispasi.omGames.bedwars.shop.ShopCategory category = config.getCategory(ShopCategoryType.ROTATING);
        if (category == null || category.getEntries().isEmpty()) {
            return true;
        }
        boolean rotating = category.getEntries().containsValue(item.getId());
        if (!rotating) {
            return true;
        }
        if (rotatingItemIds.isEmpty()) {
            return true;
        }
        return rotatingItemIds.contains(item.getId());
    }

    public boolean isRotatingUpgradeAvailable(TeamUpgradeType type) {
        if (type == null) {
            return true;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return true;
        }
        krispasi.omGames.bedwars.shop.ShopCategory category = config.getCategory(ShopCategoryType.ROTATING);
        if (category == null || category.getEntries().isEmpty()) {
            return true;
        }
        boolean hasRotating = false;
        for (String id : category.getEntries().values()) {
            ShopItemDefinition definition = config.getItem(id);
            if (definition == null || definition.getBehavior() != ShopItemBehavior.UPGRADE) {
                continue;
            }
            if (definition.getUpgradeType() != type) {
                continue;
            }
            hasRotating = true;
            if (rotatingItemIds.isEmpty() || rotatingItemIds.contains(definition.getId())) {
                return true;
            }
        }
        return !hasRotating;
    }

    private void rollRotatingItems() {
        rotatingItemIds.clear();
        List<String> candidates = getRotatingCandidateIds();
        if (candidates.isEmpty()) {
            return;
        }
        if (rotatingMode == RotatingSelectionMode.MANUAL) {
            List<String> selected = new ArrayList<>();
            for (String id : manualRotatingItemIds) {
                String normalized = normalizeItemId(id);
                if (normalized != null && candidates.contains(normalized) && !selected.contains(normalized)) {
                    selected.add(normalized);
                }
            }
            if (selected.isEmpty()) {
                selected.addAll(pickRandom(candidates, 2));
            }
            rotatingItemIds.addAll(selected.subList(0, Math.min(2, selected.size())));
            return;
        }
        int target = rotatingMode == RotatingSelectionMode.ONE_RANDOM ? 1 : 2;
        rotatingItemIds.addAll(pickRandom(candidates, target));
    }

    private List<String> pickRandom(List<String> candidates, int count) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int target = Math.max(1, Math.min(count, candidates.size()));
        if (candidates.size() <= target) {
            return new ArrayList<>(candidates);
        }
        List<String> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, target));
    }

    private boolean isRotatingCandidate(String id) {
        String normalized = normalizeItemId(id);
        if (normalized == null) {
            return false;
        }
        return getRotatingCandidateIds().contains(normalized);
    }

    private void syncManualRotatingSelection() {
        if (rotatingMode != RotatingSelectionMode.MANUAL) {
            return;
        }
        rotatingItemIds.clear();
        for (String id : manualRotatingItemIds) {
            String normalized = normalizeItemId(id);
            if (normalized != null) {
                rotatingItemIds.add(normalized);
            }
        }
    }

    private String normalizeItemId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    public int getRemainingLimit(UUID playerId, ShopItemDefinition item) {
        if (playerId == null || item == null) {
            return -1;
        }
        ShopItemLimit limit = item.getLimit();
        if (limit == null || !limit.isValid()) {
            return -1;
        }
        int current = 0;
        if (limit.scope() == ShopItemLimitScope.PLAYER) {
            Map<String, Integer> counts = playerPurchaseCounts.get(playerId);
            current = counts != null ? counts.getOrDefault(item.getId(), 0) : 0;
        } else if (limit.scope() == ShopItemLimitScope.TEAM) {
            TeamColor team = getTeam(playerId);
            if (team == null) {
                return 0;
            }
            Map<String, Integer> counts = teamPurchaseCounts.get(team);
            current = counts != null ? counts.getOrDefault(item.getId(), 0) : 0;
        }
        int remaining = limit.amount() - current;
        return Math.max(0, remaining);
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
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        clearUpgradeEffects(player);
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
        equipBaseArmor(player, team);
    }

    private void giveTeamSelectItem(Player player) {
        ItemStack item = new ItemStack(Material.RED_BED);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pick a Team", NamedTextColor.YELLOW));
        meta.lore(List.of(
                Component.text("Right click to choose your team.", NamedTextColor.GRAY)
        ));
        TeamSelectItemData.apply(meta);
        item.setItemMeta(meta);
        placeLobbyItem(player, item, 4);
    }

    private void giveLobbyControlItems(Player player) {
        if (player == null) {
            return;
        }
        ItemStack manage = new ItemStack(Material.WHITE_BED);
        ItemMeta manageMeta = manage.getItemMeta();
        manageMeta.displayName(Component.text("Manage Teams", NamedTextColor.YELLOW));
        manageMeta.lore(List.of(
                Component.text("Right click to edit teams.", NamedTextColor.GRAY)
        ));
        krispasi.omGames.bedwars.item.LobbyControlItemData.apply(manageMeta, "manage");
        manage.setItemMeta(manageMeta);

        ItemStack pause = new ItemStack(Material.RED_CONCRETE);
        ItemMeta pauseMeta = pause.getItemMeta();
        pauseMeta.displayName(Component.text("Pause Timer", NamedTextColor.RED));
        pauseMeta.lore(List.of(
                Component.text("Right click to pause/resume.", NamedTextColor.GRAY)
        ));
        krispasi.omGames.bedwars.item.LobbyControlItemData.apply(pauseMeta, "pause");
        pause.setItemMeta(pauseMeta);

        ItemStack skip = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta skipMeta = skip.getItemMeta();
        skipMeta.displayName(Component.text("Skip Timer", NamedTextColor.GREEN));
        skipMeta.lore(List.of(
                Component.text("Right click to start.", NamedTextColor.GRAY)
        ));
        krispasi.omGames.bedwars.item.LobbyControlItemData.apply(skipMeta, "skip");
        skip.setItemMeta(skipMeta);

        placeLobbyItem(player, manage, 0);
        placeLobbyItem(player, pause, 1);
        placeLobbyItem(player, skip, 2);
    }

    private void placeLobbyItem(Player player, ItemStack item, int slot) {
        if (player == null || item == null) {
            return;
        }
        if (slot >= 0 && slot < 9 && player.getInventory().getItem(slot) == null) {
            player.getInventory().setItem(slot, item);
            return;
        }
        player.getInventory().addItem(item);
    }

    private ItemStack colorLeatherArmor(Material material, org.bukkit.Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
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
        equipBaseArmor(player, team);
        if (armorTier > 0) {
            equipArmor(player, team, config, armorTier);
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
        if (shieldUnlocked.contains(playerId)) {
            giveShield(player, team);
        }
        applyTeamUpgrades(player, team);
        player.updateInventory();
    }

    private void applyPermanentItemsWithShield(Player player, TeamColor team) {
        applyPermanentItems(player, team);
        equipShieldOffhand(player, team);
    }

    private void equipBaseArmor(Player player, TeamColor team) {
        if (team == null) {
            return;
        }
        org.bukkit.Color color = team.dyeColor().getColor();
        player.getInventory().setHelmet(colorLeatherArmor(Material.LEATHER_HELMET, color));
        player.getInventory().setChestplate(colorLeatherArmor(Material.LEATHER_CHESTPLATE, color));
        player.getInventory().setLeggings(colorLeatherArmor(Material.LEATHER_LEGGINGS, color));
        player.getInventory().setBoots(colorLeatherArmor(Material.LEATHER_BOOTS, color));
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
        if (behavior == ShopItemBehavior.PICKAXE || behavior == ShopItemBehavior.AXE) {
            if (tier != current + 1) {
                return false;
            }
        } else if (tier <= current) {
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

    private boolean applyShield(Player player) {
        UUID playerId = player.getUniqueId();
        if (shieldUnlocked.contains(playerId)) {
            return false;
        }
        shieldUnlocked.add(playerId);
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

    private int resolveToolTier(Player player, ShopConfig config, ShopItemBehavior behavior) {
        if (player == null || config == null) {
            return 0;
        }
        Map<Integer, ShopItemDefinition> tiered = config.getTieredItems(behavior);
        if (tiered == null || tiered.isEmpty()) {
            return 0;
        }
        Map<Material, Integer> materialTiers = new EnumMap<>(Material.class);
        for (Map.Entry<Integer, ShopItemDefinition> entry : tiered.entrySet()) {
            Integer tier = entry.getKey();
            ShopItemDefinition definition = entry.getValue();
            if (tier == null || tier <= 0 || definition == null) {
                continue;
            }
            Material material = definition.getMaterial();
            if (material == null) {
                continue;
            }
            materialTiers.merge(material, tier, Math::max);
        }
        if (materialTiers.isEmpty()) {
            return 0;
        }
        int foundTier = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null) {
                continue;
            }
            Integer tier = materialTiers.get(item.getType());
            if (tier != null) {
                foundTier = Math.max(foundTier, tier);
            }
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null) {
            Integer tier = materialTiers.get(main.getType());
            if (tier != null) {
                foundTier = Math.max(foundTier, tier);
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null) {
            Integer tier = materialTiers.get(offhand.getType());
            if (tier != null) {
                foundTier = Math.max(foundTier, tier);
            }
        }
        return foundTier;
    }

    private void updateToolTier(Map<UUID, Integer> map, UUID playerId, int tier) {
        if (playerId == null) {
            return;
        }
        if (tier <= 0) {
            map.remove(playerId);
        } else {
            map.put(playerId, tier);
        }
    }

    private boolean hasInventoryItem(Player player, Material material) {
        if (player == null || material == null) {
            return false;
        }
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                return true;
            }
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() == material) {
            return true;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand != null && offhand.getType() == material;
    }

    private void equipArmor(Player player, TeamColor team, ShopConfig config, int tier) {
        ShopItemDefinition definition = config.getTieredItem(ShopItemBehavior.ARMOR, tier).orElse(null);
        if (definition == null) {
            return;
        }
        String base = armorBase(definition.getMaterial());
        Material leggings = Material.matchMaterial(base + "_LEGGINGS");
        Material boots = Material.matchMaterial(base + "_BOOTS");
        if (leggings == null || boots == null) {
            return;
        }
        org.bukkit.Color color = team.dyeColor().getColor();
        player.getInventory().setHelmet(colorLeatherArmor(Material.LEATHER_HELMET, color));
        player.getInventory().setChestplate(colorLeatherArmor(Material.LEATHER_CHESTPLATE, color));
        if ("LEATHER".equals(base)) {
            player.getInventory().setLeggings(colorLeatherArmor(leggings, color));
            player.getInventory().setBoots(colorLeatherArmor(boots, color));
        } else {
            player.getInventory().setLeggings(makeUnbreakable(new ItemStack(leggings)));
            player.getInventory().setBoots(makeUnbreakable(new ItemStack(boots)));
        }
    }

    private ItemStack makeUnbreakable(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
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

    private void giveShield(Player player, TeamColor team) {
        if (countItem(player, Material.SHIELD) > 0) {
            return;
        }
        ItemStack shield = createShieldItem(team);
        if (shield != null) {
            giveItem(player, shield);
        }
    }

    private ItemStack createShieldItem(TeamColor team) {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return new ItemStack(Material.SHIELD);
        }
        ShopItemDefinition definition = config.getFirstByBehavior(ShopItemBehavior.SHIELD);
        if (definition != null) {
            return definition.createPurchaseItem(team);
        }
        return new ItemStack(Material.SHIELD);
    }

    private void equipShieldOffhand(Player player, TeamColor team) {
        if (player == null) {
            return;
        }
        if (!shieldUnlocked.contains(player.getUniqueId())) {
            return;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == Material.SHIELD) {
            return;
        }
        ItemStack shield = removeShieldFromStorage(player);
        if (shield == null) {
            shield = createShieldItem(team);
        }
        if (shield == null) {
            shield = new ItemStack(Material.SHIELD);
        }
        if (offhand != null && !offhand.getType().isAir()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(offhand);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.getInventory().setItemInOffHand(shield);
    }

    private ItemStack removeShieldFromStorage(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.SHIELD) {
                continue;
            }
            contents[i] = null;
            player.getInventory().setStorageContents(contents);
            return item;
        }
        return null;
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

    private int countItem(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == material) {
            count += offhand.getAmount();
        }
        return count;
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
        if (!isInArenaWorld(player.getWorld())) {
            clearUpgradeEffects(player);
        } else if (isParticipant(player.getUniqueId())) {
            applyTeamUpgrades(player, getTeam(player.getUniqueId()));
        }
        updateSidebarForPlayer(player);
    }

    public void handlePlayerQuit(UUID playerId) {
        cancelRespawnCountdown(playerId);
        removeRespawnProtection(playerId);
        clearTrapImmunity(playerId);
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
        if (state == GameState.LOBBY) {
            Location lobby = arena.getMapLobbyLocation();
            if (lobby == null) {
                lobby = arena.getLobbyLocation();
            }
            if (lobby != null) {
                player.teleport(lobby);
            }
            player.getInventory().clear();
            if (isLobbyInitiator(playerId)) {
                giveLobbyControlItems(player);
            }
            if (teamPickEnabled) {
                giveTeamSelectItem(player);
            }
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            applyLobbyBuffs(player);
            syncToolTiers(player);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
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
            applyPermanentItemsWithShield(player, team);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            frozenPlayers.add(playerId);
            syncToolTiers(player);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }

        if (state != GameState.RUNNING) {
            return;
        }

        Location lobby = resolveMapLobbyLocation();
        if (eliminatedPlayers.contains(playerId)) {
            setSpectator(player);
            if (lobby != null) {
                player.teleport(lobby);
            }
            syncToolTiers(player);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }

        if (pendingRespawns.contains(playerId)) {
            setSpectator(player);
            if (lobby != null) {
                player.teleport(lobby);
            }
            boolean allowRespawnAfterBedBreak = respawnGracePlayers.contains(playerId);
            if (getBedState(team) == BedState.DESTROYED && !allowRespawnAfterBedBreak) {
                pendingRespawns.remove(playerId);
                eliminatePlayer(player, team);
                syncToolTiers(player);
                hideEditorsFrom(player);
                updateSidebarForPlayer(player);
                return;
            }
            scheduleRespawn(player, team, RESPAWN_DELAY_SECONDS, allowRespawnAfterBedBreak);
            syncToolTiers(player);
            hideEditorsFrom(player);
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
        applyPermanentItemsWithShield(player, team);
        syncToolTiers(player);
        hideEditorsFrom(player);
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
        if (state == GameState.LOBBY) {
            applyLobbyBuffsToLobbyPlayers();
        }
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
        lines.add(ChatColor.GOLD + "Kills: " + ChatColor.WHITE + getKillCount(player.getUniqueId()));
        lines.add(ChatColor.AQUA + "Made by Krispasi");
        return lines;
    }

    private Location resolveMapLobbyLocation() {
        Location mapLobby = arena.getMapLobbyLocation();
        if (mapLobby != null) {
            return mapLobby;
        }
        return arena.getLobbyLocation();
    }

    private String buildEventLine() {
        if (state == GameState.LOBBY) {
            if (lobbyCountdownPaused) {
                return ChatColor.YELLOW + "Countdown Paused";
            }
            return ChatColor.YELLOW + "Starting in " + lobbyCountdownRemaining + "s";
        }
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
        krispasi.omGames.bedwars.model.EventSettings events = arena.getEventSettings();
        if (!tier2Triggered) {
            return new EventInfo("Generators II", remainingSeconds(events.getTier2Delay(), elapsedSeconds));
        }
        if (!tier3Triggered) {
            return new EventInfo("Generators III", remainingSeconds(events.getTier3Delay(), elapsedSeconds));
        }
        if (!bedDestructionTriggered) {
            return new EventInfo("Beds Destroyed", remainingSeconds(events.getBedDestructionDelay(), elapsedSeconds));
        }
        if (!suddenDeathActive) {
            return new EventInfo("Sudden Death", remainingSeconds(events.getSuddenDeathDelay(), elapsedSeconds));
        }
        if (!gameEndTriggered) {
            return new EventInfo("Game End", remainingSeconds(events.getGameEndDelay(), elapsedSeconds));
        }
        return new EventInfo("Game End", 0);
    }

    private int remainingSeconds(int targetSeconds, long elapsedSeconds) {
        return (int) Math.max(0L, (long) targetSeconds - elapsedSeconds);
    }

    private String formatTime(int totalSeconds) {
        int minutes = Math.max(0, totalSeconds) / 60;
        int seconds = Math.max(0, totalSeconds) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private record EventInfo(String label, int secondsRemaining) {
    }

    private void startRespawnCountdown(Player player) {
        startRespawnCountdown(player, RESPAWN_DELAY_SECONDS);
    }

    private void startRespawnCountdown(Player player, int delaySeconds) {
        BukkitTask task = new BukkitRunnable() {
            private int remaining = Math.max(0, delaySeconds);

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

    private void initializeTeamUpgrades() {
        teamUpgrades.clear();
        for (TeamColor team : teamsInMatch) {
            teamUpgrades.put(team, new TeamUpgradeState());
        }
    }

    private void initializeBaseCenters() {
        baseCenters.clear();
        baseOccupants.clear();
        Map<TeamColor, BlockPoint> spawns = arena.getSpawns();
        for (TeamColor team : teamsInMatch) {
            BlockPoint spawn = spawns.get(team);
            if (spawn != null) {
                baseCenters.put(team, spawn);
                baseOccupants.put(team, new HashSet<>());
            }
        }
        for (GeneratorInfo generator : arena.getGenerators()) {
            if (generator.type() != GeneratorType.BASE || generator.team() == null) {
                continue;
            }
            if (!teamsInMatch.contains(generator.team())) {
                continue;
            }
            if (baseCenters.containsKey(generator.team())) {
                continue;
            }
            baseCenters.put(generator.team(), generator.location());
            baseOccupants.put(generator.team(), new HashSet<>());
        }
    }

    private TeamUpgradeState getUpgradeState(TeamColor team) {
        if (team == null) {
            return new TeamUpgradeState();
        }
        return teamUpgrades.computeIfAbsent(team, key -> new TeamUpgradeState());
    }

    private boolean hasTeamUpgrade(TeamColor team, TeamUpgradeType type) {
        return team != null && getUpgradeState(team).getTier(type) > 0;
    }

    private void syncForgeTiers() {
        if (generatorManager == null) {
            return;
        }
        Map<TeamColor, Integer> tiers = new EnumMap<>(TeamColor.class);
        for (TeamColor team : teamsInMatch) {
            tiers.put(team, getUpgradeState(team).getTier(TeamUpgradeType.FORGE));
        }
        generatorManager.setBaseForgeTiers(tiers);
    }

    private int getBaseEffectRadius() {
        int radius = arena.getBaseRadius();
        if (radius > 0) {
            return radius;
        }
        int fallback = arena.getBaseGeneratorRadius();
        return fallback > 0 ? fallback : DEFAULT_BASE_RADIUS;
    }

    private void startUpgradeTasks() {
        BukkitTask healTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> safeRun("healPool", this::applyHealPoolTick),
                0L,
                HEAL_POOL_INTERVAL_TICKS);
        tasks.add(healTask);
        BukkitTask trapTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> safeRun("trapCheck", this::checkTrapTriggers),
                0L,
                TRAP_CHECK_INTERVAL_TICKS);
        tasks.add(trapTask);
        BukkitTask garryRadiusTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> safeRun("garryCenterRadius", this::enforceGarryCenterRadius),
                0L,
                10L);
        tasks.add(garryRadiusTask);
    }

    private void enforceGarryCenterRadius() {
        if (state != GameState.RUNNING) {
            return;
        }
        World world = arena.getWorld();
        BlockPoint center = arena.getCenter();
        if (world == null || center == null) {
            return;
        }
        int configured = arena.getCenterRadius();
        int radius = configured > 0 ? configured : DEFAULT_CENTER_RADIUS;
        if (radius <= 0) {
            return;
        }
        double radiusSquared = radius * radius;
        double centerX = center.x() + 0.5;
        double centerZ = center.z() + 0.5;
        Location centerLocation = new Location(world, centerX, center.y() + 1.0, centerZ);
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (!(entity instanceof org.bukkit.entity.Warden warden)) {
                continue;
            }
            if (!isGarryFamilyWarden(warden)) {
                continue;
            }
            Location location = warden.getLocation();
            double dx = location.getX() - centerX;
            double dz = location.getZ() - centerZ;
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared <= radiusSquared) {
                continue;
            }
            if (distanceSquared <= 0.0001) {
                warden.teleport(centerLocation);
            } else {
                double distance = Math.sqrt(distanceSquared);
                double scale = Math.max(0.0, (radius - 0.2) / distance);
                double clampedX = centerX + dx * scale;
                double clampedZ = centerZ + dz * scale;
                Location clamped = new Location(world, clampedX, location.getY(), clampedZ,
                        location.getYaw(), location.getPitch());
                warden.teleport(clamped);
            }
            warden.setTarget(null);
        }
    }

    private boolean isGarryFamilyWarden(org.bukkit.entity.Warden warden) {
        return warden != null
                && (warden.getScoreboardTags().contains(GARRY_TAG)
                || warden.getScoreboardTags().contains(GARRY_WIFE_TAG)
                || warden.getScoreboardTags().contains(GARRY_JR_TAG));
    }

    private void startRegenTask() {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> safeRun("customRegen", this::applyRegenTick),
                20L,
                20L);
        tasks.add(task);
    }

    private void applyRegenTick() {
        if (state != GameState.RUNNING) {
            return;
        }
        long now = System.currentTimeMillis();
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double maxHealth = player.getMaxHealth();
            if (player.getHealth() >= maxHealth) {
                continue;
            }
            long lastDamage = lastDamageTimes.getOrDefault(playerId, 0L);
            if (now - lastDamage < REGEN_DELAY_MILLIS) {
                continue;
            }
            long lastRegen = lastRegenTimes.getOrDefault(playerId, 0L);
            if (now - lastRegen < REGEN_INTERVAL_MILLIS) {
                continue;
            }
            double newHealth = Math.min(maxHealth, player.getHealth() + 2.0);
            player.setHealth(newHealth);
            lastRegenTimes.put(playerId, now);
        }
    }

    private void applyHealPoolTick() {
        if (state != GameState.RUNNING) {
            return;
        }
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        int radius = getBaseEffectRadius();
        int radiusSquared = radius * radius;
        for (TeamColor team : teamsInMatch) {
            TeamUpgradeState upgrades = teamUpgrades.get(team);
            if (upgrades == null || upgrades.getTier(TeamUpgradeType.HEAL_POOL) <= 0) {
                continue;
            }
            BlockPoint base = baseCenters.get(team);
            if (base == null) {
                continue;
            }
            for (UUID playerId : assignments.keySet()) {
                if (assignments.get(playerId) != team) {
                    continue;
                }
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                if (!isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                Location location = player.getLocation();
                int dx = location.getBlockX() - base.x();
                int dz = location.getBlockZ() - base.z();
                if (dx * dx + dz * dz <= radiusSquared) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                            HEAL_POOL_DURATION_TICKS,
                            0,
                            true,
                            false,
                            true));
                }
            }
        }
    }

    private void checkTrapTriggers() {
        if (state != GameState.RUNNING) {
            return;
        }
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        int radius = getBaseEffectRadius();
        int radiusSquared = radius * radius;
        for (TeamColor team : teamsInMatch) {
            TeamUpgradeState upgrades = teamUpgrades.get(team);
            if (upgrades == null) {
                continue;
            }
            BlockPoint base = baseCenters.get(team);
            if (base == null) {
                continue;
            }
            Set<UUID> previous = baseOccupants.computeIfAbsent(team, key -> new HashSet<>());
            Set<UUID> current = new HashSet<>();
            for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
                if (entry.getValue() == null || entry.getValue() == team) {
                    continue;
                }
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) {
                    continue;
                }
                if (!isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (hasTrapImmunity(entry.getKey())) {
                    continue;
                }
                Location location = player.getLocation();
                int dx = location.getBlockX() - base.x();
                int dz = location.getBlockZ() - base.z();
                if (dx * dx + dz * dz <= radiusSquared) {
                    current.add(entry.getKey());
                }
            }
            TrapType triggeredTrap = null;
            Player intruder = null;
            if (!current.isEmpty() && upgrades.getTrapCount() > 0) {
                for (UUID playerId : current) {
                    if (!previous.contains(playerId)) {
                        intruder = Bukkit.getPlayer(playerId);
                        triggeredTrap = upgrades.pollTrap();
                        break;
                    }
                }
            }
            if (triggeredTrap != null && intruder != null) {
                triggerTrap(team, triggeredTrap, intruder);
            }
            previous.clear();
            previous.addAll(current);
        }
    }

    private void triggerTrap(TeamColor team, TrapType trap, Player intruder) {
        intruder.removePotionEffect(PotionEffectType.INVISIBILITY);
        switch (trap) {
            case ITS_A_TRAP -> {
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                        TRAP_BLINDNESS_SECONDS * 20,
                        0,
                        true,
                        false,
                        true));
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        TRAP_SLOW_SECONDS * 20,
                        1,
                        true,
                        false,
                        true));
            }
            case COUNTER_OFFENSIVE -> applyCounterOffensive(team);
            case ALARM -> showTrapTitle(team, "Alarm Trap!");
            case MINER_FATIGUE -> intruder.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                    TRAP_FATIGUE_SECONDS * 20,
                    0,
                    true,
                    false,
                    true));
            default -> {
            }
        }
        announceTrap(team, trap.displayName());
    }

    private void applyCounterOffensive(TeamColor team) {
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    TRAP_COUNTER_SECONDS * 20,
                    0,
                    true,
                    false,
                    true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
                    TRAP_COUNTER_SECONDS * 20,
                    0,
                    true,
                    false,
                    true));
        }
    }

    private void announceTrap(TeamColor team, String name) {
        Component message = Component.text("Trap triggered: " + name, NamedTextColor.RED);
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.sendMessage(message);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        }
    }

    private void announceTeamUpgrade(TeamColor team, Player buyer, TeamUpgradeType type, int tier) {
        if (team == null || buyer == null || type == null) {
            return;
        }
        String upgradeName = type.tierName(tier);
        Component message = Component.text(buyer.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" purchased ", NamedTextColor.GRAY))
                .append(Component.text(upgradeName, NamedTextColor.GREEN));
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void showTrapTitle(TeamColor team, String name) {
        Title title = Title.title(
                Component.text(name, NamedTextColor.RED),
                Component.text("Enemy detected", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
        );
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.showTitle(title);
            }
        }
    }

    private void applyTeamUpgradeEffects(TeamColor team) {
        if (team == null) {
            return;
        }
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                applyTeamUpgrades(player, team);
            }
        }
    }

    private void applyTeamUpgrades(Player player, TeamColor team) {
        if (team == null || player == null) {
            return;
        }
        if (!isInArenaWorld(player.getWorld())) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        TeamUpgradeState upgrades = getUpgradeState(team);
        int protectionTier = upgrades.getTier(TeamUpgradeType.PROTECTION);
        if (protectionTier > 0) {
            applyProtection(player, protectionTier);
        }
        if (upgrades.getTier(TeamUpgradeType.SHARPNESS) > 0) {
            applySharpness(player);
        }
        int hasteTier = upgrades.getTier(TeamUpgradeType.HASTE);
        if (hasteTier > 0) {
            applyHaste(player, hasteTier);
        }
        int featherTier = upgrades.getTier(TeamUpgradeType.FEATHER_FALLING);
        if (featherTier > 0) {
            applyFeatherFalling(player, featherTier);
        }
        int thornsTier = upgrades.getTier(TeamUpgradeType.THORNS);
        if (thornsTier > 0) {
            applyThorns(player, thornsTier);
        }
        if (upgrades.getTier(TeamUpgradeType.FIRE_ASPECT) > 0) {
            applyFireAspect(player);
        }
    }

    private void applyProtection(Player player, int level) {
        if (level <= 0) {
            return;
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null) {
                continue;
            }
            ItemMeta meta = piece.getItemMeta();
            if (meta.getEnchantLevel(Enchantment.PROTECTION) >= level) {
                continue;
            }
            meta.addEnchant(Enchantment.PROTECTION, level, true);
            piece.setItemMeta(meta);
            armor[i] = piece;
            changed = true;
        }
        if (changed) {
            player.getInventory().setArmorContents(armor);
        }
    }

    private void applySharpness(Player player) {
        applyEnchantment(player, SWORD_MATERIALS, Enchantment.SHARPNESS, 1);
    }

    private void applyHaste(Player player, int tier) {
        int amplifier = Math.max(0, tier - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,
                Integer.MAX_VALUE,
                amplifier,
                true,
                false,
                true));
    }

    private void applyFeatherFalling(Player player, int level) {
        if (level <= 0) {
            return;
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null) {
                continue;
            }
            if (!piece.getType().name().endsWith("_BOOTS")) {
                continue;
            }
            ItemMeta meta = piece.getItemMeta();
            if (meta.getEnchantLevel(Enchantment.FEATHER_FALLING) >= level) {
                continue;
            }
            meta.addEnchant(Enchantment.FEATHER_FALLING, level, true);
            piece.setItemMeta(meta);
            armor[i] = piece;
            changed = true;
        }
        if (changed) {
            player.getInventory().setArmorContents(armor);
        }
    }

    private void applyThorns(Player player, int level) {
        if (level <= 0) {
            return;
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null) {
                continue;
            }
            ItemMeta meta = piece.getItemMeta();
            if (meta.getEnchantLevel(Enchantment.THORNS) >= level) {
                continue;
            }
            meta.addEnchant(Enchantment.THORNS, level, true);
            piece.setItemMeta(meta);
            armor[i] = piece;
            changed = true;
        }
        if (changed) {
            player.getInventory().setArmorContents(armor);
        }
    }

    private void applyFireAspect(Player player) {
        applyEnchantment(player, ATTACK_MATERIALS, Enchantment.FIRE_ASPECT, 1);
    }

    private void applyEnchantment(Player player, Set<Material> materials, Enchantment enchantment, int level) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !materials.contains(item.getType())) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta.getEnchantLevel(enchantment) >= level) {
                continue;
            }
            meta.addEnchant(enchantment, level, true);
            item.setItemMeta(meta);
            contents[i] = item;
            changed = true;
        }
        if (changed) {
            player.getInventory().setStorageContents(contents);
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && materials.contains(offhand.getType())) {
            ItemMeta meta = offhand.getItemMeta();
            if (meta.getEnchantLevel(enchantment) < level) {
                meta.addEnchant(enchantment, level, true);
                offhand.setItemMeta(meta);
                player.getInventory().setItemInOffHand(offhand);
            }
        }
    }

    private void removeHealPoolEffects(TeamColor team) {
        for (UUID playerId : assignments.keySet()) {
            if (assignments.get(playerId) != team) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.REGENERATION);
            }
        }
    }

    private void clearUpgradeEffects(Player player) {
        player.removePotionEffect(PotionEffectType.HASTE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
    }

    private void applyEditorInvisibility(Player player) {
        if (player == null) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                false
        ));
    }

    private void applyLobbyBuffsToLobbyPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            applyLobbyBuffs(player);
        }
    }

    private void applyLobbyBuffs(Player player) {
        if (player == null) {
            return;
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.REGENERATION,
                LOBBY_REGEN_DURATION_TICKS,
                0,
                false,
                false,
                false
        ), true);
    }

    private void hideEditorFromParticipants(Player editor) {
        if (plugin == null || editor == null) {
            return;
        }
        UUID editorId = editor.getUniqueId();
        for (UUID playerId : assignments.keySet()) {
            if (playerId.equals(editorId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.hidePlayer(plugin, editor);
            }
        }
    }

    private void hideEditorsFrom(Player viewer) {
        if (plugin == null || viewer == null) {
            return;
        }
        for (UUID editorId : editorPlayers) {
            Player editor = Bukkit.getPlayer(editorId);
            if (editor != null && !editor.getUniqueId().equals(viewer.getUniqueId())) {
                viewer.hidePlayer(plugin, editor);
            }
        }
    }

    private void showEditorToParticipants(Player editor) {
        if (plugin == null || editor == null) {
            return;
        }
        UUID editorId = editor.getUniqueId();
        for (UUID playerId : assignments.keySet()) {
            if (playerId.equals(editorId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.showPlayer(plugin, editor);
            }
        }
    }

    private void clearEditors() {
        if (editorPlayers.isEmpty()) {
            return;
        }
        if (plugin != null) {
            for (UUID editorId : new HashSet<>(editorPlayers)) {
                Player editor = Bukkit.getPlayer(editorId);
                if (editor != null) {
                    if (editor.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                        editor.removePotionEffect(PotionEffectType.INVISIBILITY);
                    }
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        viewer.showPlayer(plugin, editor);
                    }
                }
            }
        }
        editorPlayers.clear();
    }

    private void spawnShops() {
        despawnShops();
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (ShopLocation main : arena.getMainShops().values()) {
            if (main != null) {
                spawnShopNpc(world, main, ShopType.ITEM);
            }
        }
        for (ShopLocation upgrades : arena.getUpgradeShops().values()) {
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
        World world = arena.getWorld();
        if (world == null) {
            return;
        }
        for (org.bukkit.entity.Entity entity : world.getEntitiesByClass(Villager.class)) {
            if (entity.getScoreboardTags().contains(ITEM_SHOP_TAG)
                    || entity.getScoreboardTags().contains(UPGRADES_SHOP_TAG)) {
                entity.remove();
            }
        }
    }

    private void spawnShopNpc(World world, ShopLocation location, ShopType type) {
        Location spawn = location.toLocation(world);
        clearExistingShopNpc(world, spawn);
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

    private void clearExistingShopNpc(World world, Location spawn) {
        if (world == null || spawn == null) {
            return;
        }
        int blockX = spawn.getBlockX();
        int blockY = spawn.getBlockY();
        int blockZ = spawn.getBlockZ();
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(spawn, 0.6, 0.6, 0.6)) {
            if (!(entity instanceof Villager villager)) {
                continue;
            }
            Location location = villager.getLocation();
            if (location.getBlockX() == blockX
                    && location.getBlockY() == blockY
                    && location.getBlockZ() == blockZ) {
                villager.remove();
            }
        }
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
