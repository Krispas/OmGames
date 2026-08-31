package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class HallsOfCarnageManager {
    private static final String CONFIG_RESOURCE = "halls-of-carnage.yml";
    private static final String DATA_FOLDER_NAME = "HallsOfCarnage";
    private static final String MENU_VILLAGER_TAG = "omgames_hoc_menu_villager";
    private static final String[] RESOURCE_FILES = {
            "hallsOfCarnage/scenarios/UntoldDepths.txt",
            "hallsOfCarnage/level/special/start_floor.txt",
            "hallsOfCarnage/level/special/final_floor_1.txt",
            "hallsOfCarnage/level_type/howling_corridors.txt",
            "hallsOfCarnage/modifiers/shared.yml",
            "hallsOfCarnage/modifiers/frozen_halls.yml",
            "hallsOfCarnage/modifiers/deep_crypt.yml"
    };

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    private final JavaPlugin plugin;
    private final org.bukkit.NamespacedKey menuVillagerKey;
    private final HallsShameService shameService;
    private final Map<Integer, HallsSession> activeSessions = new HashMap<>();
    private final Map<UUID, Integer> playerSessions = new HashMap<>();
    private final Map<Integer, BukkitTask> disconnectGraceTasks = new HashMap<>();
    private HallsConfig config;
    private List<HallsScenario> scenarios = List.of();
    private int nextSessionId = 1;

    public HallsOfCarnageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.menuVillagerKey = new org.bukkit.NamespacedKey(plugin, "hoc_menu_villager");
        this.shameService = new HallsShameService(plugin);
    }

    public void load() {
        ensureDefaultFiles();
        config = HallsConfig.load(getConfigFile());
        scenarios = HallsScenarioLoader.loadScenarios(plugin, getScenariosFolder());
        shameService.load();
        applyWorldRules();
        spawnConfiguredMenuVillager();
        plugin.getLogger().info("Loaded " + scenarios.size() + " Halls of Carnage scenarios.");
    }

    public void shutdown() {
        stopAllSessions(false);
        shameService.shutdown();
    }

    public Result reload() {
        scenarios = List.of();
        config = HallsConfig.load(getConfigFile());
        scenarios = HallsScenarioLoader.loadScenarios(plugin, getScenariosFolder());
        applyWorldRules();
        spawnConfiguredMenuVillager();
        return Result.ok("Reloaded Halls of Carnage. Scenarios: " + scenarios.size() + ".");
    }

    public List<HallsScenario> getScenarios() {
        return scenarios;
    }

    public List<HallsSession> getActiveSessions() {
        return activeSessions.values().stream()
                .sorted(java.util.Comparator.comparingInt(HallsSession::id))
                .toList();
    }

    public HallsScenario getScenario(String id) {
        if (id == null) {
            return null;
        }
        String normalized = normalizeId(id);
        return scenarios.stream()
                .filter(scenario -> scenario.id().equalsIgnoreCase(normalized) || scenario.name().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public Result teleportToLobby(Player player) {
        Location spawn = getLobbySpawn();
        if (player == null || spawn == null) {
            return Result.fail("Halls lobby world is not loaded.");
        }
        player.teleport(spawn);
        prepareLobbyPlayer(player);
        return Result.ok("Teleported to the Halls of Carnage lobby.");
    }

    public Result setLobbySpawn(Player player) {
        if (player == null) {
            return Result.fail("Only a player can set the Halls lobby spawn.");
        }
        saveLocation("lobby.spawn", player.getLocation());
        config = HallsConfig.load(getConfigFile());
        prepareLobbyPlayer(player);
        return Result.ok("Halls lobby spawn set to your current location.");
    }

    public Result spawnMenuVillager(Player player, Float yawOverride) {
        if (player == null) {
            return Result.fail("Only a player can spawn the Halls menu villager.");
        }
        Location location = player.getLocation().clone();
        if (yawOverride != null) {
            location.setYaw(yawOverride);
        }
        saveLocation("lobby.menu-villager", location);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(getConfigFile());
        yaml.set("lobby.menu-villager.enabled", true);
        try {
            yaml.save(getConfigFile());
        } catch (IOException ex) {
            return Result.fail("Failed to save Halls menu villager location: " + ex.getMessage());
        }
        config = HallsConfig.load(getConfigFile());
        spawnConfiguredMenuVillager();
        return Result.ok("Halls menu villager spawned.");
    }

    public void openMainMenu(Player player) {
        if (player == null) {
            return;
        }
        HallsMainMenu.open(player, scenarios, shameService.getLeaderboard(10));
    }

    public boolean isMenuVillager(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(menuVillagerKey, PersistentDataType.BYTE);
    }

    public boolean isSessionEntity(Entity entity) {
        return entity != null && activeSessions.values().stream().anyMatch(session -> session.isSessionEntity(entity));
    }

    public boolean isActiveSessionParticipant(Player player) {
        if (player == null) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        return sessionId != null && activeSessions.containsKey(sessionId);
    }

    public boolean isLockedInventorySlotItem(org.bukkit.inventory.ItemStack item) {
        return HallsSession.isLockedSlotItem(plugin, item);
    }

    public boolean handleSessionEntityAttack(Player player, Entity entity) {
        if (entity == null) {
            return false;
        }
        for (HallsSession session : activeSessions.values()) {
            if (session.handleBreakableAttack(player, entity)) {
                return true;
            }
        }
        return false;
    }

    public boolean handleElevatorButton(Player player, org.bukkit.block.Block block) {
        if (player == null) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.handleElevatorButton(player, block);
    }

    public boolean handleScrapDeposit(Player player, org.bukkit.block.Block block) {
        if (player == null) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.handleScrapDeposit(player, block);
    }

    public void pushOutOfSessionProps(Player player) {
        for (HallsSession session : activeSessions.values()) {
            session.pushOutOfSessionProps(player);
        }
    }

    public boolean isHallsWorld(World world) {
        World lobbyWorld = config == null ? null : config.resolveLobbyWorld();
        return world != null && lobbyWorld != null && world.equals(lobbyWorld);
    }

    public Location getLobbySpawn() {
        return config == null ? null : config.lobbySpawn();
    }

    public void prepareLobbyPlayer(Player player) {
        if (player == null || !isHallsWorld(player.getWorld())) {
            return;
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        Location spawn = getLobbySpawn();
        if (spawn != null) {
            player.setRespawnLocation(spawn, true);
        }
    }

    public int getShame(UUID playerId) {
        return shameService.getShame(playerId);
    }

    public int setShame(UUID playerId, int value) {
        return shameService.setShame(playerId, value);
    }

    public int addShame(UUID playerId, int delta) {
        return shameService.addShame(playerId, delta);
    }

    public List<HallsShameService.ShameEntry> getShameLeaderboard(int limit) {
        return shameService.getLeaderboard(limit);
    }

    public Result startScenario(Player initiator, String scenarioId, List<Player> requestedPlayers) {
        HallsScenario scenario = getScenario(scenarioId);
        if (scenario == null) {
            return Result.fail("Unknown Halls scenario: " + scenarioId + ".");
        }
        List<Player> players = new ArrayList<>();
        if (requestedPlayers == null || requestedPlayers.isEmpty()) {
            if (initiator != null) {
                players.add(initiator);
            }
        } else {
            players.addAll(requestedPlayers);
        }
        if (players.size() < scenario.minPlayers() || players.size() > scenario.maxPlayers()) {
            return Result.fail("Scenario " + scenario.name() + " requires " + scenario.minPlayers()
                    + "-" + scenario.maxPlayers() + " players.");
        }
        if (players.size() > config.maxPlayers()) {
            return Result.fail("Halls sessions support at most " + config.maxPlayers() + " players.");
        }
        for (Player player : players) {
            if (playerSessions.containsKey(player.getUniqueId())) {
                return Result.fail(player.getName() + " is already in Halls session "
                        + playerSessions.get(player.getUniqueId()) + ".");
            }
        }
        World world = config.resolveLobbyWorld();
        if (world == null) {
            return Result.fail("Halls world is not loaded.");
        }
        int sessionId = nextSessionId++;
        int slot = firstFreeSessionSlot();
        HallsSession session = new HallsSession(plugin, sessionId, scenario, world, config.sessionOrigin(slot), getDataFolder(), players);
        try {
            session.start();
        } catch (IOException ex) {
            session.stop(null);
            return Result.fail("Failed to build Halls start floor: " + ex.getMessage());
        }
        activeSessions.put(sessionId, session);
        for (Player player : players) {
            playerSessions.put(player.getUniqueId(), sessionId);
        }
        return Result.ok("Started Halls session " + sessionId + " for " + scenario.name() + " with "
                + players.size() + " player" + (players.size() == 1 ? "" : "s") + ".");
    }

    public Result stopSession(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) {
            return Result.fail("Usage: /hoc stop <session_id|*>");
        }
        if (rawSessionId.equals("*")) {
            int stopped = activeSessions.size();
            stopAllSessions(true);
            return Result.ok("Stopped " + stopped + " Halls session" + (stopped == 1 ? "" : "s") + ".");
        }
        int sessionId;
        try {
            sessionId = Integer.parseInt(rawSessionId);
        } catch (NumberFormatException ex) {
            return Result.fail("Session id must be a whole number or *.");
        }
        HallsSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Result.fail("No active Halls session has id " + sessionId + ".");
        }
        stopSession(sessionId, true);
        return Result.ok("Stopped Halls session " + sessionId + ".");
    }

    public void handlePlayerQuit(Player player) {
        if (player == null) {
            return;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        if (sessionId == null || disconnectGraceTasks.containsKey(sessionId)) {
            return;
        }
        HallsSession session = activeSessions.get(sessionId);
        if (session == null) {
            return;
        }
        long delayTicks = Math.max(1L, config.disconnectGraceSeconds()) * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            disconnectGraceTasks.remove(sessionId);
            HallsSession current = activeSessions.get(sessionId);
            if (current != null && current.participants().stream().noneMatch(id -> Bukkit.getPlayer(id) != null)) {
                stopSession(sessionId, true);
            }
        }, delayTicks);
        disconnectGraceTasks.put(sessionId, task);
    }

    public void handlePlayerJoin(Player player) {
        if (player == null) {
            return;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        if (sessionId != null) {
            BukkitTask task = disconnectGraceTasks.remove(sessionId);
            if (task != null) {
                task.cancel();
            }
            HallsSession session = activeSessions.get(sessionId);
            if (session != null) {
                session.handlePlayerJoin(player);
            }
        } else {
            HallsSession.clearLockedInventoryBarriers(plugin, player);
        }
        prepareLobbyPlayer(player);
    }

    private void ensureDefaultFiles() {
        File folder = getDataFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        copyResourceIfMissing(CONFIG_RESOURCE, new File(folder, CONFIG_RESOURCE));
        for (String resource : RESOURCE_FILES) {
            copyResourceIfMissing(resource, new File(folder, resource.substring("hallsOfCarnage/".length())));
        }
    }

    private void copyResourceIfMissing(String resourcePath, File target) {
        if (target.exists()) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (InputStream input = plugin.getResource(resourcePath)) {
            if (input == null) {
                return;
            }
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save Halls resource " + resourcePath + ": " + ex.getMessage());
        }
    }

    private void applyWorldRules() {
        World world = config == null ? null : config.resolveLobbyWorld();
        if (world == null) {
            return;
        }
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        for (Player player : world.getPlayers()) {
            prepareLobbyPlayer(player);
        }
    }

    private int firstFreeSessionSlot() {
        int slot = 0;
        while (true) {
            HallsConfig.BlockPoint candidate = config.sessionOrigin(slot);
            boolean used = activeSessions.values().stream().anyMatch(session -> session.origin().equals(candidate));
            if (!used) {
                return slot;
            }
            slot++;
        }
    }

    private void stopSession(int sessionId, boolean teleportPlayers) {
        HallsSession session = activeSessions.remove(sessionId);
        if (session == null) {
            return;
        }
        BukkitTask task = disconnectGraceTasks.remove(sessionId);
        if (task != null) {
            task.cancel();
        }
        Location fallback = teleportPlayers ? getLobbySpawn() : null;
        session.stop(fallback);
        for (UUID playerId : session.participants()) {
            playerSessions.remove(playerId);
        }
    }

    private void stopAllSessions(boolean teleportPlayers) {
        for (Integer sessionId : new ArrayList<>(activeSessions.keySet())) {
            stopSession(sessionId, teleportPlayers);
        }
        for (BukkitTask task : disconnectGraceTasks.values()) {
            task.cancel();
        }
        disconnectGraceTasks.clear();
        playerSessions.clear();
    }

    private void spawnConfiguredMenuVillager() {
        if (config == null || !config.menuVillagerEnabled()) {
            return;
        }
        Location location = config.menuVillagerLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        removeMenuVillagers(location.getWorld());
        Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        villager.customName(Component.text("Halls of Carnage", NamedTextColor.DARK_RED));
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setProfession(Villager.Profession.CLERIC);
        villager.addScoreboardTag(MENU_VILLAGER_TAG);
        villager.getPersistentDataContainer().set(menuVillagerKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void removeMenuVillagers(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(MENU_VILLAGER_TAG) || isMenuVillager(entity)) {
                entity.remove();
            }
        }
    }

    private void saveLocation(String path, Location location) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(getConfigFile());
        yaml.set("lobby.world", location.getWorld().getKey().asString());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        yaml.set(path + ".pitch", location.getPitch());
        try {
            yaml.save(getConfigFile());
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save Halls config: " + ex.getMessage());
        }
    }

    private File getDataFolder() {
        return new File(plugin.getDataFolder(), DATA_FOLDER_NAME);
    }

    private File getConfigFile() {
        return new File(getDataFolder(), CONFIG_RESOURCE);
    }

    private File getScenariosFolder() {
        return new File(getDataFolder(), "scenarios");
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
