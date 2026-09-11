package krispasi.omGames.hallsofcarnage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
            "hallsOfCarnage/level/camps/camp_1.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_1.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_2.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_3.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_4.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_5.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_6.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_7.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_8.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_9.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_10.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_11.txt",
            "hallsOfCarnage/level/howling_corridors/exploration_12.txt",
            "hallsOfCarnage/level/frozen_halls/exploration_1.txt",
            "hallsOfCarnage/level/frozen_halls/exploration_2.txt",
            "hallsOfCarnage/level/frozen_halls/exploration_3.txt",
            "hallsOfCarnage/level/frozen_halls/exploration_4.txt",
            "hallsOfCarnage/level/frozen_halls/exploration_5.txt",
            "hallsOfCarnage/level/frozen_halls/exploration_6.txt",
            "hallsOfCarnage/level/frozen_halls/exploration_7.txt",
            "hallsOfCarnage/level/frozen_halls/exploration_8.txt",
            "hallsOfCarnage/level/frozen_halls/exploration_9.txt",
            "hallsOfCarnage/level/frozen_halls/exploration_10.txt",
            "hallsOfCarnage/level/deep_crypt/exploration_1.txt",
            "hallsOfCarnage/level/deep_crypt/exploration_2.txt",
            "hallsOfCarnage/level/deep_crypt/exploration_3.txt",
            "hallsOfCarnage/level/deep_crypt/exploration_4.txt",
            "hallsOfCarnage/level/deep_crypt/exploration_5.txt",
            "hallsOfCarnage/level/deep_crypt/exploration_6.txt",
            "hallsOfCarnage/level/deep_crypt/exploration_7.txt",
            "hallsOfCarnage/level/deep_crypt/exploration_8.txt",
            "hallsOfCarnage/level/deep_crypt/exploration_9.txt",
            "hallsOfCarnage/level/deep_crypt/exploration_10.txt",
            "hallsOfCarnage/level_type/howling_corridors.txt",
            "hallsOfCarnage/level_type/frozen_halls.txt",
            "hallsOfCarnage/level_type/deep_crypt.txt",
            "hallsOfCarnage/modifiers/shared.yml",
            "hallsOfCarnage/modifiers/frozen_halls.yml",
            "hallsOfCarnage/modifiers/deep_crypt.yml",
            "hallsOfCarnage/breakables/barrel.txt",
            "hallsOfCarnage/breakables/chest.txt",
            "hallsOfCarnage/breakables/ender_chest.txt",
            "hallsOfCarnage/breakables/table.txt",
            "hallsOfCarnage/breakables/chair.txt",
            "hallsOfCarnage/breakables/stool.txt",
            "hallsOfCarnage/breakables/radiator.txt",
            "hallsOfCarnage/breakables/metal_barrel.txt",
            "hallsOfCarnage/breakable_loot_pools/common.yml",
            "hallsOfCarnage/breakable_loot_pools/rare.yml",
            "hallsOfCarnage/traps/hole.txt",
            "hallsOfCarnage/traps/bear_trap.txt",
            "hallsOfCarnage/traps/proximity_mine.txt",
            "hallsOfCarnage/traps/swinging_blade.txt",
            "hallsOfCarnage/traps/wall_spikes.txt",
            "hallsOfCarnage/traps/falling_ice.txt",
            "hallsOfCarnage/traps/poison_darts.txt",
            "hallsOfCarnage/monsters/zombie.txt",
            "hallsOfCarnage/monsters/creeper.txt",
            "hallsOfCarnage/monsters/creaking.txt",
            "hallsOfCarnage/monsters/slime_medium.txt",
            "hallsOfCarnage/monsters/zombie_vanguard.txt",
            "hallsOfCarnage/monsters/skeleton.txt",
            "hallsOfCarnage/monsters/cave_spider.txt",
            "hallsOfCarnage/monsters/stray.txt",
            "hallsOfCarnage/monsters/bogged.txt",
            "hallsOfCarnage/monsters/husk.txt",
            "hallsOfCarnage/monsters/breeze.txt",
            "hallsOfCarnage/monsters/vindicator.txt",
            "hallsOfCarnage/monsters/silverfish.txt",
            "hallsOfCarnage/monsters/pillager.txt",
            "hallsOfCarnage/monsters/witch.txt",
            "hallsOfCarnage/monsters/wither_skeleton.txt",
            "hallsOfCarnage/monsters/warden.txt",
            "hallsOfCarnage/items/weapons/vagabonds_club.txt",
            "hallsOfCarnage/items/weapons/rusty_sword.txt",
            "hallsOfCarnage/items/weapons/echo_blade.txt",
            "hallsOfCarnage/items/weapons/miner_pick.txt",
            "hallsOfCarnage/items/ranged/short_bow.txt",
            "hallsOfCarnage/items/ranged/storm_crossbow.txt",
            "hallsOfCarnage/items/armors/padded_armor.txt",
            "hallsOfCarnage/items/armors/reinforced_chestplate.txt",
            "hallsOfCarnage/items/food/stale_bread.txt",
            "hallsOfCarnage/items/food/raw_mycelia.txt",
            "hallsOfCarnage/items/food/cooked_mycelia.txt",
            "hallsOfCarnage/items/food/ember_stew.txt",
            "hallsOfCarnage/items/food/golden_jerky.txt",
            "hallsOfCarnage/items/food/hearty_mycelia_stew.txt",
            "hallsOfCarnage/items/food/fleetfoot_ration.txt",
            "hallsOfCarnage/items/food/stonehide_chowder.txt",
            "hallsOfCarnage/items/utility/smoke_bomb.txt",
            "hallsOfCarnage/items/utility/warding_totem.txt",
            "hallsOfCarnage/items/blueprints/cooking_pot_blueprint.txt",
            "hallsOfCarnage/items/blueprints/weapon_bench_blueprint.txt",
            "hallsOfCarnage/items/blueprints/armory_blueprint.txt",
            "hallsOfCarnage/items/blueprints/grindstone_blueprint.txt",
            "hallsOfCarnage/items/blueprints/storage_locker_small_blueprint.txt",
            "hallsOfCarnage/items/blueprints/storage_locker_medium_blueprint.txt",
            "hallsOfCarnage/items/blueprints/storage_locker_large_blueprint.txt",
            "hallsOfCarnage/items/blueprints/elevator_drill_blueprint.txt",
            "hallsOfCarnage/items/blueprints/scanner_blueprint.txt",
            "hallsOfCarnage/items/blueprints/bounty_board_blueprint.txt",
            "hallsOfCarnage/items/blueprints/mycelia_farm_blueprint.txt",
            "hallsOfCarnage/items/blueprints/sculk_purifier_small_blueprint.txt",
            "hallsOfCarnage/items/blueprints/sculk_purifier_medium_blueprint.txt",
            "hallsOfCarnage/items/blueprints/sculk_purifier_large_blueprint.txt",
            "hallsOfCarnage/buildings/cooking_pot.yml",
            "hallsOfCarnage/buildings/weapon_bench.yml",
            "hallsOfCarnage/buildings/armory.yml",
            "hallsOfCarnage/buildings/mycelia_farm.yml",
            "hallsOfCarnage/buildings/storage_locker_small.yml",
            "hallsOfCarnage/buildings/storage_locker_medium.yml",
            "hallsOfCarnage/buildings/storage_locker_large.yml",
            "hallsOfCarnage/buildings/grindstone.yml",
            "hallsOfCarnage/buildings/elevator_drill.yml",
            "hallsOfCarnage/buildings/scanner.yml",
            "hallsOfCarnage/buildings/bounty_board.yml",
            "hallsOfCarnage/buildings/sculk_purifier_small.yml",
            "hallsOfCarnage/buildings/sculk_purifier_medium.yml",
            "hallsOfCarnage/buildings/sculk_purifier_large.yml"
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
    private final Map<UUID, PendingSession> pendingSessions = new HashMap<>();
    private HallsConfig config;
    private List<HallsScenario> scenarios = List.of();
    private Map<String, HallsLevelType> levelTypes = Map.of();
    private Map<String, HallsBreakableType> breakableTypes = Map.of();
    private Map<String, HallsItemType> itemTypes = Map.of();
    private Map<String, HallsTrapType> trapTypes = Map.of();
    private Map<String, HallsMonsterType> monsterTypes = Map.of();
    private Map<String, HallsModifierType> modifierTypes = Map.of();
    private Map<String, HallsBuildingType> buildingTypes = Map.of();
    private int nextSessionId = 1;

    private record DifficultyOption(String id, String name, double multiplier) {
    }

    private record PendingSession(String scenarioId,
                                  DifficultyOption difficulty,
                                  File saveFile,
                                  java.util.LinkedHashSet<UUID> selectedPlayers) {
        boolean loading() {
            return saveFile != null;
        }
    }

    private static final DifficultyOption NORMAL_DIFFICULTY = new DifficultyOption("normal", "Normal", 1.0);
    private static final Map<String, DifficultyOption> DIFFICULTIES = Map.of(
            "normal", NORMAL_DIFFICULTY,
            "hard", new DifficultyOption("hard", "Hard", 1.5),
            "extreme", new DifficultyOption("extreme", "Extreme", 2.0)
    );

    public HallsOfCarnageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.menuVillagerKey = new org.bukkit.NamespacedKey(plugin, "hoc_menu_villager");
        this.shameService = new HallsShameService(plugin);
    }

    public void load() {
        ensureDefaultFiles();
        config = HallsConfig.load(getConfigFile());
        scenarios = HallsScenarioLoader.loadScenarios(plugin, getScenariosFolder());
        levelTypes = HallsLevelTypeLoader.loadLevelTypes(plugin, getLevelTypesFolder());
        breakableTypes = HallsBreakableTypeLoader.loadBreakableTypes(plugin, getBreakablesFolder());
        itemTypes = HallsItemTypeLoader.loadItemTypes(plugin, getItemsFolder());
        trapTypes = HallsTrapTypeLoader.loadTrapTypes(plugin, getTrapsFolder());
        monsterTypes = HallsMonsterTypeLoader.loadMonsterTypes(plugin, getMonstersFolder());
        modifierTypes = HallsModifierTypeLoader.loadModifierTypes(plugin, getModifiersFolder());
        buildingTypes = HallsBuildingTypeLoader.loadBuildingTypes(plugin, getBuildingsFolder());
        shameService.load();
        applyWorldRules();
        spawnConfiguredMenuVillager();
        plugin.getLogger().info("Loaded " + scenarios.size() + " Halls of Carnage scenarios and "
                + levelTypes.size() + " level types, " + breakableTypes.size() + " breakable types, "
                + itemTypes.size() + " item types, " + trapTypes.size() + " trap types, "
                + monsterTypes.size() + " monster types, " + modifierTypes.size() + " modifiers, "
                + buildingTypes.size() + " buildings.");
    }

    public void shutdown() {
        stopAllSessions(false);
        shameService.shutdown();
    }

    public Result reload() {
        scenarios = List.of();
        config = HallsConfig.load(getConfigFile());
        scenarios = HallsScenarioLoader.loadScenarios(plugin, getScenariosFolder());
        levelTypes = HallsLevelTypeLoader.loadLevelTypes(plugin, getLevelTypesFolder());
        breakableTypes = HallsBreakableTypeLoader.loadBreakableTypes(plugin, getBreakablesFolder());
        itemTypes = HallsItemTypeLoader.loadItemTypes(plugin, getItemsFolder());
        trapTypes = HallsTrapTypeLoader.loadTrapTypes(plugin, getTrapsFolder());
        monsterTypes = HallsMonsterTypeLoader.loadMonsterTypes(plugin, getMonstersFolder());
        modifierTypes = HallsModifierTypeLoader.loadModifierTypes(plugin, getModifiersFolder());
        buildingTypes = HallsBuildingTypeLoader.loadBuildingTypes(plugin, getBuildingsFolder());
        applyWorldRules();
        spawnConfiguredMenuVillager();
        return Result.ok("Reloaded Halls of Carnage. Scenarios: " + scenarios.size()
                + ", level types: " + levelTypes.size() + ", breakables: " + breakableTypes.size()
                + ", items: " + itemTypes.size() + ", traps: " + trapTypes.size()
                + ", monsters: " + monsterTypes.size() + ", modifiers: " + modifierTypes.size()
                + ", buildings: " + buildingTypes.size() + ".");
    }

    public Result resetGameResources(boolean confirmed) {
        if (!confirmed) {
            return Result.fail("This deletes Halls scenario/level/level_type/modifier/breakable/trap/monster/item/building files and recopies bundled defaults. Use /hoc reset confirm.");
        }
        if (!activeSessions.isEmpty()) {
            return Result.fail("Stop active Halls sessions before resetting game resources.");
        }
        File folder = getDataFolder();
        try {
            deleteGameResourceFolder(new File(folder, "scenarios"));
            deleteGameResourceFolder(new File(folder, "level"));
            deleteGameResourceFolder(new File(folder, "level_type"));
            deleteGameResourceFolder(new File(folder, "modifiers"));
            deleteGameResourceFolder(new File(folder, "breakables"));
            deleteGameResourceFolder(new File(folder, "breakable_loot_pools"));
            deleteGameResourceFolder(new File(folder, "traps"));
            deleteGameResourceFolder(new File(folder, "monsters"));
            deleteGameResourceFolder(new File(folder, "items"));
            deleteGameResourceFolder(new File(folder, "buildings"));
        } catch (IOException ex) {
            return Result.fail("Failed to delete Halls game resources: " + ex.getMessage());
        }
        for (String resource : RESOURCE_FILES) {
            copyResourceIfMissing(resource, new File(folder, resource.substring("hallsOfCarnage/".length())));
        }
        scenarios = HallsScenarioLoader.loadScenarios(plugin, getScenariosFolder());
        levelTypes = HallsLevelTypeLoader.loadLevelTypes(plugin, getLevelTypesFolder());
        breakableTypes = HallsBreakableTypeLoader.loadBreakableTypes(plugin, getBreakablesFolder());
        itemTypes = HallsItemTypeLoader.loadItemTypes(plugin, getItemsFolder());
        trapTypes = HallsTrapTypeLoader.loadTrapTypes(plugin, getTrapsFolder());
        monsterTypes = HallsMonsterTypeLoader.loadMonsterTypes(plugin, getMonstersFolder());
        modifierTypes = HallsModifierTypeLoader.loadModifierTypes(plugin, getModifiersFolder());
        buildingTypes = HallsBuildingTypeLoader.loadBuildingTypes(plugin, getBuildingsFolder());
        return Result.ok("Reset Halls game resources from bundled defaults. Scenarios: " + scenarios.size()
                + ", level types: " + levelTypes.size() + ", breakables: " + breakableTypes.size()
                + ", items: " + itemTypes.size() + ", traps: " + trapTypes.size()
                + ", monsters: " + monsterTypes.size() + ", modifiers: " + modifierTypes.size()
                + ", buildings: " + buildingTypes.size() + ".");
    }

    public List<HallsScenario> getScenarios() {
        return scenarios;
    }

    public List<HallsSession> getActiveSessions() {
        return activeSessions.values().stream()
                .sorted(java.util.Comparator.comparingInt(HallsSession::id))
                .toList();
    }

    public List<String> getItemIds() {
        return itemTypes.keySet().stream().sorted().toList();
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
        HallsMainMenu.openMain(plugin, player, shameService.getLeaderboard(10), savesFor(player).size());
    }

    public boolean handleMainMenuClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        HallsMainMenu.MenuHolder holder = HallsMainMenu.holder(event.getInventory());
        if (holder == null) {
            return false;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return true;
        }
        String action = HallsMainMenu.action(plugin, event.getCurrentItem());
        String value = HallsMainMenu.value(plugin, event.getCurrentItem());
        if (action == null) {
            return true;
        }
        switch (action) {
            case HallsMainMenu.ACTION_NEW -> HallsMainMenu.openScenarios(plugin, player, scenarios);
            case HallsMainMenu.ACTION_LOAD -> HallsMainMenu.openSaves(plugin, player, savesFor(player));
            case HallsMainMenu.ACTION_BACK -> openBack(player, holder);
            case HallsMainMenu.ACTION_SCENARIO -> HallsMainMenu.openDifficulty(plugin, player, value);
            case HallsMainMenu.ACTION_DIFFICULTY -> {
                DifficultyOption difficulty = DIFFICULTIES.getOrDefault(normalizeId(value), NORMAL_DIFFICULTY);
                PendingSession pending = new PendingSession(holder.context(), difficulty, null,
                        new java.util.LinkedHashSet<>(List.of(player.getUniqueId())));
                pendingSessions.put(player.getUniqueId(), pending);
                openSessionSettings(player, pending);
            }
            case HallsMainMenu.ACTION_SAVE -> {
                HallsSaveData save = saveByName(player, value);
                if (save == null) {
                    player.sendActionBar(Component.text("That save is no longer available.", NamedTextColor.RED));
                    HallsMainMenu.openSaves(plugin, player, savesFor(player));
                    return true;
                }
                if (event.isShiftClick() && event.isRightClick()) {
                    Result result = deleteSave(player, save);
                    player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
                    HallsMainMenu.openSaves(plugin, player, savesFor(player));
                    return true;
                }
                DifficultyOption difficulty = DIFFICULTIES.getOrDefault(save.difficultyId(),
                        new DifficultyOption(save.difficultyId(), save.difficultyId(), save.difficultyMultiplier()));
                PendingSession pending = new PendingSession(save.scenarioId(), difficulty, save.file(),
                        new java.util.LinkedHashSet<>(save.participants()));
                pendingSessions.put(player.getUniqueId(), pending);
                openSessionSettings(player, pending);
            }
            case HallsMainMenu.ACTION_TOGGLE_PLAYER -> {
                PendingSession pending = pendingSessions.get(player.getUniqueId());
                UUID target = parseUuid(value);
                if (pending != null && target != null && !pending.loading()) {
                    java.util.LinkedHashSet<UUID> selected = new java.util.LinkedHashSet<>(pending.selectedPlayers());
                    if (selected.contains(target)) {
                        if (!target.equals(player.getUniqueId())) {
                            selected.remove(target);
                        }
                    } else {
                        selected.add(target);
                    }
                    pending = new PendingSession(pending.scenarioId(), pending.difficulty(), pending.saveFile(), selected);
                    pendingSessions.put(player.getUniqueId(), pending);
                    openSessionSettings(player, pending);
                }
            }
            case HallsMainMenu.ACTION_PLAY -> {
                Result result = startPendingSession(player);
                player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            default -> {
            }
        }
        return true;
    }

    private void openBack(Player player, HallsMainMenu.MenuHolder holder) {
        switch (holder.type()) {
            case SCENARIOS, SAVES -> openMainMenu(player);
            case DIFFICULTY -> HallsMainMenu.openScenarios(plugin, player, scenarios);
            case SETTINGS -> {
                PendingSession pending = pendingSessions.get(player.getUniqueId());
                if (pending != null && pending.loading()) {
                    HallsMainMenu.openSaves(plugin, player, savesFor(player));
                } else {
                    HallsMainMenu.openDifficulty(plugin, player, pending == null ? "" : pending.scenarioId());
                }
            }
            default -> openMainMenu(player);
        }
    }

    private void openSessionSettings(Player host, PendingSession pending) {
        HallsScenario scenario = getScenario(pending.scenarioId());
        if (scenario == null) {
            host.sendActionBar(Component.text("That scenario is no longer loaded.", NamedTextColor.RED));
            openMainMenu(host);
            return;
        }
        List<HallsMainMenu.PlayerChoice> choices = pending.loading()
                ? loadedSaveChoices(pending)
                : newCampaignChoices(host, pending);
        int selected = (int) choices.stream().filter(HallsMainMenu.PlayerChoice::selected).count();
        boolean canPlay = selected >= scenario.minPlayers() && selected <= scenario.maxPlayers()
                && choices.stream().filter(HallsMainMenu.PlayerChoice::selected).allMatch(HallsMainMenu.PlayerChoice::online);
        HallsMainMenu.openSettings(plugin, host, scenario, pending.difficulty().name(),
                pending.difficulty().multiplier(), choices, pending.loading(), canPlay);
    }

    private List<HallsMainMenu.PlayerChoice> newCampaignChoices(Player host, PendingSession pending) {
        List<HallsMainMenu.PlayerChoice> choices = new ArrayList<>();
        for (Player candidate : Bukkit.getOnlinePlayers().stream()
                .filter(player -> isHallsWorld(player.getWorld()))
                .sorted(Comparator.comparing(Player::getName))
                .toList()) {
            boolean selected = pending.selectedPlayers().contains(candidate.getUniqueId());
            choices.add(new HallsMainMenu.PlayerChoice(candidate.getUniqueId(), candidate.getName(),
                    selected, candidate.getUniqueId().equals(host.getUniqueId()), !isActiveSessionParticipant(candidate)));
        }
        return choices;
    }

    private List<HallsMainMenu.PlayerChoice> loadedSaveChoices(PendingSession pending) {
        HallsSaveData save = HallsSaveData.load(pending.saveFile());
        if (save == null) {
            return List.of();
        }
        List<HallsMainMenu.PlayerChoice> choices = new ArrayList<>();
        for (UUID playerId : save.participants()) {
            Player online = Bukkit.getPlayer(playerId);
            HallsSaveData.PlayerState state = save.players().get(playerId);
            String name = online == null ? (state == null ? playerId.toString().substring(0, 8) : state.name()) : online.getName();
            boolean available = online != null && isHallsWorld(online.getWorld()) && !isActiveSessionParticipant(online);
            choices.add(new HallsMainMenu.PlayerChoice(playerId, name, true, true, available));
        }
        return choices;
    }

    private Result startPendingSession(Player host) {
        PendingSession pending = pendingSessions.get(host.getUniqueId());
        if (pending == null) {
            return Result.fail("No Halls session is being configured.");
        }
        Result result;
        if (pending.loading()) {
            HallsSaveData save = HallsSaveData.load(pending.saveFile());
            result = startLoadedSave(host, save);
        } else {
            List<Player> players = pending.selectedPlayers().stream()
                    .map(Bukkit::getPlayer)
                    .filter(player -> player != null && isHallsWorld(player.getWorld()))
                    .toList();
            result = startScenarioInternal(host, pending.scenarioId(), players, pending.difficulty(), null);
        }
        if (result.success()) {
            pendingSessions.remove(host.getUniqueId());
            host.closeInventory();
        } else {
            openSessionSettings(host, pending);
        }
        return result;
    }

    private Result startLoadedSave(Player host, HallsSaveData save) {
        if (save == null) {
            return Result.fail("That save file could not be loaded.");
        }
        List<Player> players = new ArrayList<>();
        for (UUID playerId : save.participants()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !isHallsWorld(player.getWorld())) {
                HallsSaveData.PlayerState state = save.players().get(playerId);
                String name = state == null ? playerId.toString().substring(0, 8) : state.name();
                return Result.fail(name + " must be online in the Halls lobby to load this save.");
            }
            players.add(player);
        }
        DifficultyOption difficulty = new DifficultyOption(save.difficultyId(), save.difficultyId(), save.difficultyMultiplier());
        return startScenarioInternal(host, save.scenarioId(), players, difficulty, save);
    }

    private List<HallsSaveData> savesFor(Player player) {
        if (player == null) {
            return List.of();
        }
        File folder = getSavesFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return List.of();
        }
        List<HallsSaveData> saves = new ArrayList<>();
        for (File file : files) {
            HallsSaveData save = HallsSaveData.load(file);
            if (save != null && save.participants().contains(player.getUniqueId())) {
                saves.add(save);
            }
        }
        saves.sort(Comparator.comparingLong(HallsSaveData::savedAt).reversed());
        return saves;
    }

    private HallsSaveData saveByName(Player player, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        for (HallsSaveData save : savesFor(player)) {
            if (save.file().getName().equals(fileName)) {
                return save;
            }
        }
        return null;
    }

    private Result deleteSave(Player player, HallsSaveData save) {
        if (player == null || save == null || !save.participants().contains(player.getUniqueId())) {
            return Result.fail("That save is no longer available.");
        }
        try {
            Path savesRoot = getSavesFolder().getCanonicalFile().toPath();
            Path savePath = save.file().getCanonicalFile().toPath();
            if (!savePath.startsWith(savesRoot) || savePath.equals(savesRoot)) {
                return Result.fail("Refusing to delete a save outside the Halls saves folder.");
            }
            Files.deleteIfExists(savePath);
            PendingSession pending = pendingSessions.get(player.getUniqueId());
            if (pending != null && pending.saveFile() != null
                    && pending.saveFile().getCanonicalFile().toPath().equals(savePath)) {
                pendingSessions.remove(player.getUniqueId());
            }
            return Result.ok("Deleted Halls save " + save.displayName() + ".");
        } catch (IOException ex) {
            return Result.fail("Failed to delete Halls save: " + ex.getMessage());
        }
    }

    public boolean isMenuVillager(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(menuVillagerKey, PersistentDataType.BYTE);
    }

    public boolean isSessionEntity(Entity entity) {
        return entity != null && activeSessions.values().stream().anyMatch(session -> session.isSessionEntity(entity));
    }

    public boolean isSessionMonster(Entity entity) {
        return entity != null && activeSessions.values().stream().anyMatch(session -> session.isSessionMonster(entity));
    }

    public boolean registerSplitMonster(Entity entity) {
        if (entity == null) {
            return false;
        }
        return activeSessions.values().stream().anyMatch(session -> session.registerSplitMonster(entity));
    }

    public void handleSessionMonsterDeath(org.bukkit.entity.LivingEntity entity, Player killer) {
        if (entity == null) {
            return;
        }
        for (HallsSession session : activeSessions.values()) {
            if (session.isSessionMonster(entity)) {
                session.handleMonsterDeath(entity, killer);
                return;
            }
        }
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
        return isSessionEntity(entity);
    }

    public boolean handlePhysicsDropPickup(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.handlePhysicsDropPickup(player, entity);
    }

    public boolean handleCampInteract(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.handleCampInteract(player, entity);
    }

    public boolean handleCampInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.handleCampInventoryClick(event);
    }

    public boolean handlePlayerDroppedItem(Player player, org.bukkit.entity.Item itemDrop) {
        if (player == null || itemDrop == null) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.handlePlayerDroppedItem(player, itemDrop);
    }

    public boolean handleItemConsume(Player player, org.bukkit.inventory.ItemStack item) {
        if (player == null || item == null) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.handleItemConsume(player, item);
    }

    public boolean handleUtilityUse(Player player, org.bukkit.inventory.ItemStack item) {
        if (player == null || item == null) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.handleUtilityUse(player, item);
    }

    public boolean handleElevatorButton(Player player, org.bukkit.block.Block block) {
        if (player == null) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.handleElevatorButton(player, block);
    }

    public boolean handleElevatorChestInteract(Player player, org.bukkit.block.Block block) {
        if (player == null) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.handleElevatorChestInteract(player, block);
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

    public void handlePlayerMove(Player player) {
        if (player == null) {
            return;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        if (session != null) {
            session.handlePlayerMove(player);
        }
    }

    public boolean handlePlayerDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return false;
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.handlePlayerDamage(event);
    }

    public boolean blocksEating(Player player) {
        Integer sessionId = player == null ? null : playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session != null && session.blocksEating(player);
    }

    public int forcedFoodLevel(Player player) {
        Integer sessionId = player == null ? null : playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        return session == null ? 20 : session.forcedFoodLevel(player);
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
        return startScenarioInternal(initiator, scenarioId, requestedPlayers, NORMAL_DIFFICULTY, null);
    }

    private Result startScenarioInternal(Player initiator,
                                         String scenarioId,
                                         List<Player> requestedPlayers,
                                         DifficultyOption difficulty,
                                         HallsSaveData saveData) {
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
        UUID hostId = initiator != null && players.contains(initiator)
                ? initiator.getUniqueId()
                : (saveData == null ? players.getFirst().getUniqueId() : saveData.hostId());
        boolean hostIsParticipant = false;
        for (Player player : players) {
            if (player.getUniqueId().equals(hostId)) {
                hostIsParticipant = true;
                break;
            }
        }
        if (!hostIsParticipant) {
            hostId = players.getFirst().getUniqueId();
        }
        DifficultyOption selectedDifficulty = difficulty == null ? NORMAL_DIFFICULTY : difficulty;
        HallsSession session = new HallsSession(plugin, sessionId, scenario, world, config.sessionOrigin(slot),
                getDataFolder(), levelTypes, breakableTypes, itemTypes, trapTypes, monsterTypes, modifierTypes,
                buildingTypes, hostId, selectedDifficulty.id(), selectedDifficulty.multiplier(), saveData, players);
        try {
            session.start();
        } catch (IOException ex) {
            session.stop(null);
            return Result.fail("Failed to build Halls floor: " + ex.getMessage());
        }
        activeSessions.put(sessionId, session);
        for (Player player : players) {
            playerSessions.put(player.getUniqueId(), sessionId);
        }
        return Result.ok((saveData == null ? "Started" : "Loaded") + " Halls session " + sessionId
                + " for " + scenario.name() + " with "
                + players.size() + " player" + (players.size() == 1 ? "" : "s") + ".");
    }

    public Result leaveSession(Player player) {
        if (player == null) {
            return Result.fail("Only players can leave a Halls session.");
        }
        Integer sessionId = playerSessions.get(player.getUniqueId());
        if (sessionId == null) {
            return Result.fail("You are not in an active Halls session.");
        }
        HallsSession session = activeSessions.get(sessionId);
        if (session == null) {
            playerSessions.remove(player.getUniqueId());
            return Result.fail("Your Halls session is no longer active.");
        }
        if (!session.isHost(player)) {
            return Result.fail("Only the session host can end this Halls run.");
        }
        if (!session.canSaveAndLeave()) {
            return Result.fail("/hoc leave can only save from the start floor or a camp floor.");
        }
        session.save("host-leave");
        stopSession(sessionId, true);
        return Result.ok("Saved and ended Halls session " + sessionId + ".");
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

    public Result forceSessionFloor(String rawSessionId, String rawFloor) {
        int sessionId;
        int floor;
        try {
            sessionId = Integer.parseInt(rawSessionId);
            floor = Integer.parseInt(rawFloor);
        } catch (NumberFormatException ex) {
            return Result.fail("Usage: /hoc floor <session_id> <floor>");
        }
        HallsSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Result.fail("No active Halls session has id " + sessionId + ".");
        }
        if (session.isTransitioning()) {
            return Result.fail("Halls session " + sessionId + " is already transitioning.");
        }
        if (floor < 1 || floor > session.scenario().floorCount()) {
            return Result.fail("Floor must be between 1 and " + session.scenario().floorCount() + ".");
        }
        if (!session.forceBuildFloor(floor)) {
            return Result.fail("Could not rebuild Halls session " + sessionId + ".");
        }
        return Result.ok("Rebuilt Halls session " + sessionId + " at floor " + floor + " ("
                + session.activeLevelTypeId() + ", rooms " + session.activeGeneratedRooms()
                + "/" + session.activeTargetRooms() + ").");
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

    private void deleteGameResourceFolder(File folder) throws IOException {
        if (!folder.exists()) {
            return;
        }
        Path root = getDataFolder().getCanonicalFile().toPath();
        Path target = folder.getCanonicalFile().toPath();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("Refusing to delete outside the Halls data folder.");
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private File getLevelTypesFolder() {
        return new File(getDataFolder(), "level_type");
    }

    public Result giveItem(Player player, String rawItemId, int amount) {
        if (player == null) {
            return Result.fail("Only players can receive Halls items.");
        }
        String itemId = normalizeId(rawItemId);
        Integer sessionId = playerSessions.get(player.getUniqueId());
        HallsSession session = sessionId == null ? null : activeSessions.get(sessionId);
        if (HallsSession.isScrapId(itemId)) {
            if (session == null) {
                return Result.fail("You must be in an active Halls session to deposit test scrap.");
            }
            if (!session.addStoredScrap(itemId, amount)) {
                return Result.fail("Unknown Halls scrap type: " + rawItemId + ".");
            }
            return Result.ok("Deposited " + amount + " " + itemId.replace('_', ' ') + " into the elevator storage.");
        }
        HallsItemType type = itemTypes.get(itemId);
        if (type == null) {
            return Result.fail("Unknown Halls item: " + rawItemId + ".");
        }
        ItemStack item = HallsItemFactory.create(plugin, type, amount);
        if (tryEquipEmptyArmorSlot(player.getInventory(), item)) {
            return Result.ok("Gave and equipped " + type.name() + ".");
        }
        int slot = firstAvailableHotbarSlot(player.getInventory());
        if (slot < 0) {
            return Result.fail("Your hotbar is full.");
        }
        player.getInventory().setItem(slot, item);
        return Result.ok("Gave " + type.name() + ".");
    }

    private boolean tryEquipEmptyArmorSlot(PlayerInventory inventory, ItemStack item) {
        EquipmentSlot slot = armorSlot(item);
        if (slot == null || inventory.getItem(slot) != null) {
            return false;
        }
        inventory.setItem(slot, item);
        return true;
    }

    private EquipmentSlot armorSlot(ItemStack item) {
        return switch (item.getType()) {
            case LEATHER_HELMET, CHAINMAIL_HELMET, IRON_HELMET, GOLDEN_HELMET, DIAMOND_HELMET, NETHERITE_HELMET,
                 TURTLE_HELMET -> EquipmentSlot.HEAD;
            case LEATHER_CHESTPLATE, CHAINMAIL_CHESTPLATE, IRON_CHESTPLATE, GOLDEN_CHESTPLATE, DIAMOND_CHESTPLATE,
                 NETHERITE_CHESTPLATE, ELYTRA -> EquipmentSlot.CHEST;
            case LEATHER_LEGGINGS, CHAINMAIL_LEGGINGS, IRON_LEGGINGS, GOLDEN_LEGGINGS, DIAMOND_LEGGINGS,
                 NETHERITE_LEGGINGS -> EquipmentSlot.LEGS;
            case LEATHER_BOOTS, CHAINMAIL_BOOTS, IRON_BOOTS, GOLDEN_BOOTS, DIAMOND_BOOTS, NETHERITE_BOOTS -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private int firstAvailableHotbarSlot(PlayerInventory inventory) {
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

    private File getBreakablesFolder() {
        return new File(getDataFolder(), "breakables");
    }

    private File getItemsFolder() {
        return new File(getDataFolder(), "items");
    }

    private File getSavesFolder() {
        return new File(getDataFolder(), "saves");
    }

    private File getTrapsFolder() {
        return new File(getDataFolder(), "traps");
    }

    private File getMonstersFolder() {
        return new File(getDataFolder(), "monsters");
    }

    private File getModifiersFolder() {
        return new File(getDataFolder(), "modifiers");
    }

    private File getBuildingsFolder() {
        return new File(getDataFolder(), "buildings");
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
