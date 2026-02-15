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
import krispasi.omGames.bedwars.model.EventSettings;
import krispasi.omGames.bedwars.model.ShopLocation;
import krispasi.omGames.bedwars.model.ShopType;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopCost;
import krispasi.omGames.bedwars.shop.ShopCategoryType;
import krispasi.omGames.bedwars.shop.ShopItemBehavior;
import krispasi.omGames.bedwars.shop.ShopItemDefinition;
import krispasi.omGames.bedwars.upgrade.TeamUpgradeState;
import krispasi.omGames.bedwars.upgrade.TeamUpgradeType;
import krispasi.omGames.bedwars.upgrade.TrapType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
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

public class GameSession {
    public static final String ITEM_SHOP_TAG = "bw_item_shop";
    public static final String UPGRADES_SHOP_TAG = "bw_upgrades_shop";
    public static final String BED_BUG_TAG = "bw_bed_bug";
    public static final String DREAM_DEFENDER_TAG = "bw_dream_defender";
    public static final String CRYSTAL_TAG = "bw_crystal";
    public static final String CRYSTAL_EXPLOSION_TAG = "bw_crystal_exploded";
    public static final String HAPPY_GHAST_TAG = "bw_happy_ghast";
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
    private static final int START_COUNTDOWN_SECONDS = 5;
    private static final int RESPAWN_DELAY_SECONDS = 5;
    private static final int RESPAWN_PROTECTION_SECONDS = 5;
    private static final int DEFAULT_BASE_RADIUS = 8;
    private static final int HEAL_POOL_INTERVAL_TICKS = 20;
    private static final int HEAL_POOL_DURATION_TICKS = 60;
    private static final int TRAP_CHECK_INTERVAL_TICKS = 20;
    private static final int TRAP_MAX_COUNT = 3;
    private static final int TRAP_BLINDNESS_SECONDS = 8;
    private static final int TRAP_SLOW_SECONDS = 8;
    private static final int TRAP_COUNTER_SECONDS = 15;
    private static final int TRAP_FATIGUE_SECONDS = 10;
    private static final long REGEN_DELAY_MILLIS = 4000L;
    private static final long REGEN_INTERVAL_MILLIS = 3000L;

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
    private final Set<TeamColor> teamsInMatch = EnumSet.noneOf(TeamColor.class);
    private final Set<UUID> shopNpcIds = new HashSet<>();
    private final Map<TeamColor, TeamUpgradeState> teamUpgrades = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, BlockPoint> baseCenters = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Set<UUID>> baseOccupants = new EnumMap<>(TeamColor.class);
    private final Map<UUID, Integer> armorTiers = new HashMap<>();
    private final Map<UUID, Integer> pickaxeTiers = new HashMap<>();
    private final Map<UUID, Integer> axeTiers = new HashMap<>();
    private final Set<UUID> shearsUnlocked = new HashSet<>();
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
    private Double previousBorderSize;
    private Location previousBorderCenter;
    private Double previousBorderDamageAmount;
    private Double previousBorderDamageBuffer;
    private Integer previousBorderWarningDistance;

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

    public boolean isRunning() {
        return state == GameState.RUNNING;
    }

    public boolean isActive() {
        return state == GameState.STARTING || state == GameState.RUNNING;
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

    public boolean handleShopPurchase(Player player, ShopItemDefinition item) {
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return false;
        }
        ShopCost cost = item.getCost();
        if (cost == null || !cost.isValid() || !hasResources(player, cost)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        if (!applyPurchase(player, item)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        removeResources(player, cost);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        return true;
    }

    public boolean handleUpgradePurchase(Player player, TeamUpgradeType type) {
        if (!isActive() || !isParticipant(player.getUniqueId()) || !isInArenaWorld(player.getWorld())) {
            return false;
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

    private boolean applyPurchase(Player player, ShopItemDefinition item) {
        TeamColor team = getTeam(player.getUniqueId());
        ShopItemBehavior behavior = item.getBehavior();
        return switch (behavior) {
            case BLOCK, UTILITY, POTION -> {
                giveItem(player, item.createPurchaseItem(team));
                yield true;
            }
            case SWORD -> {
                if (item.getMaterial() != Material.WOODEN_SWORD) {
                    removeItems(player, WOODEN_SWORD_ONLY);
                }
                giveItem(player, item.createPurchaseItem(team));
                if (team != null && hasTeamUpgrade(team, TeamUpgradeType.SHARPNESS)) {
                    applySharpness(player);
                }
                yield true;
            }
            case BOW -> {
                removeItems(player, BOW_MATERIALS);
                giveItem(player, item.createPurchaseItem(team));
                yield true;
            }
            case CROSSBOW -> {
                if (countItem(player, Material.CROSSBOW) >= 3) {
                    yield false;
                }
                giveItem(player, item.createPurchaseItem(team));
                yield true;
            }
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
        prepareWorld();
        updateTeamsInMatch();
        initializeTeamUpgrades();
        initializeBaseCenters();
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
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            double maxHealth = player.getMaxHealth();
            player.setHealth(Math.max(1.0, maxHealth));
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
        clearCombat(player.getUniqueId());
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
            respawnGracePlayers.add(player.getUniqueId());
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

    private void scheduleTierUpgrade(int tier, int delaySeconds) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            safeRun("tierUpgrade", () -> {
                if (state != GameState.RUNNING || generatorManager == null) {
                    return;
                }
                generatorManager.setDiamondTier(tier);
                generatorManager.setEmeraldTier(tier);
                generatorManager.refresh();
                broadcast(Component.text("Generators " + toRoman(tier) + "!", NamedTextColor.GOLD));
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
                startSuddenDeathBorderShrink();
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
        double targetSize = 6.0;
        border.setCenter(center.x() + 0.5, center.z() + 0.5);
        border.setSize(Math.max(targetSize, 1.0), remainingSeconds);
    }

    private void scheduleRespawn(Player player, TeamColor team) {
        Location spawn = arena.getSpawn(team);
        if (spawn == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        boolean allowRespawnAfterBedBreak = respawnGracePlayers.contains(playerId);
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
            if (state != GameState.RUNNING || (getBedState(team) == BedState.DESTROYED && !allowRespawnAfterBedBreak)) {
                eliminatedPlayers.add(playerId);
                setSpectator(player);
                removeRespawnProtection(playerId);
                cancelRespawnCountdown(playerId);
                respawnGracePlayers.remove(playerId);
                respawnTasks.remove(player.getUniqueId());
                checkTeamEliminated(team);
                return;
            }
            player.teleport(spawn);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.getInventory().clear();
            giveStarterKit(player, team);
            applyPermanentItems(player, team);
            grantRespawnProtection(player);
            cancelRespawnCountdown(playerId);
            respawnGracePlayers.remove(playerId);
            respawnTasks.remove(player.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> applyPermanentItems(player, team),
                    1L);
        }), RESPAWN_DELAY_SECONDS * 20L);
        respawnTasks.put(player.getUniqueId(), task);
        showTitle(player, Component.text("Respawning in " + RESPAWN_DELAY_SECONDS, NamedTextColor.YELLOW), Component.empty());
    }

    private void eliminatePlayer(Player player, TeamColor team) {
        eliminatedPlayers.add(player.getUniqueId());
        respawnGracePlayers.remove(player.getUniqueId());
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
        pendingRespawns.clear();
        respawnGracePlayers.clear();
        if (generatorManager != null) {
            generatorManager.stop();
            generatorManager = null;
        }
        frozenPlayers.clear();
        sidebarTask = null;
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
                    || entity.getScoreboardTags().contains(HAPPY_GHAST_TAG)) {
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
        lastCombatTimes.clear();
        lastDamagers.clear();
        lastDamagerTimes.clear();
        lastDamageTimes.clear();
        lastRegenTimes.clear();
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
        applyTeamUpgrades(player, team);
        player.updateInventory();
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
        krispasi.omGames.bedwars.model.EventSettings events = arena.getEventSettings();
        if (elapsedSeconds < events.getTier2Delay()) {
            return new EventInfo("Generators II", (int) (events.getTier2Delay() - elapsedSeconds));
        }
        if (elapsedSeconds < events.getTier3Delay()) {
            return new EventInfo("Generators III", (int) (events.getTier3Delay() - elapsedSeconds));
        }
        if (elapsedSeconds < events.getBedDestructionDelay()) {
            return new EventInfo("Beds Destroyed", (int) (events.getBedDestructionDelay() - elapsedSeconds));
        }
        if (elapsedSeconds < events.getSuddenDeathDelay()) {
            return new EventInfo("Sudden Death", (int) (events.getSuddenDeathDelay() - elapsedSeconds));
        }
        if (elapsedSeconds < events.getGameEndDelay()) {
            return new EventInfo("Game End", (int) (events.getGameEndDelay() - elapsedSeconds));
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
