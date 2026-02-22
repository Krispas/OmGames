package krispasi.omGames.bedwars.setup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.generator.GeneratorType;
import krispasi.omGames.bedwars.generator.GeneratorSettings;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.EventSettings;
import krispasi.omGames.bedwars.model.ShopType;
import krispasi.omGames.bedwars.model.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Implements the {@code /bw setup} workflow for creating and editing arenas.
 * <p>Writes player locations, generator settings, and event times to {@code bedwars.yml}.</p>
 * <p>Also provides status output to guide setup completion.</p>
 */
public class BedwarsSetupManager {
    private static final String ARENAS_PATH = "arenas";
    private static final String KEY_WORLD = "world";
    private static final String KEY_CENTER = "center";
    private static final String KEY_CORNER_1 = "corner_1";
    private static final String KEY_CORNER_2 = "corner_2";
    private static final String KEY_BASE_RADIUS = "base-radius";
    private static final String KEY_LOBBY_HEIGHT = "lobby-height";
    private static final String KEY_BEDS = "beds";
    private static final String KEY_GENERATORS = "generators";
    private static final String KEY_BASE_GENERATORS = "Base_Generators";
    private static final String KEY_SPAWNS = "Spawns";
    private static final String KEY_SHOPS = "Shops";
    private static final String KEY_ANTI_BUILD = "anti-build";
    private static final String KEY_GENERATOR_SETTINGS = "generator-settings";
    private static final String KEY_EVENT_TIMES = "event-times";

    private final JavaPlugin plugin;
    private final BedwarsManager bedwarsManager;
    private final Map<UUID, PendingSetup> pendingBeds = new HashMap<>();

    public BedwarsSetupManager(JavaPlugin plugin, BedwarsManager bedwarsManager) {
        this.plugin = plugin;
        this.bedwarsManager = bedwarsManager;
    }

    public void showSetupList(Player player, String arenaId) {
        YamlConfiguration config = loadConfig();
        ConfigurationSection arena = getOrCreateArena(config, arenaId, false);
        if (arena == null) {
            player.sendMessage(Component.text("Arena not found: " + arenaId, NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("Setup for " + arenaId + ":", NamedTextColor.GOLD));
        sendStatusLine(player, "world", arena.getString(KEY_WORLD));
        sendStatusLine(player, "center", arena.getString(KEY_CENTER));
        sendStatusLine(player, KEY_CORNER_1, arena.getString(KEY_CORNER_1));
        sendStatusLine(player, KEY_CORNER_2, arena.getString(KEY_CORNER_2));
        sendStatusLine(player, KEY_BASE_RADIUS, arena.contains(KEY_BASE_RADIUS)
                ? String.valueOf(arena.getInt(KEY_BASE_RADIUS)) : null);
        sendStatusLine(player, "lobby-height", arena.contains(KEY_LOBBY_HEIGHT)
                ? String.valueOf(arena.getInt(KEY_LOBBY_HEIGHT)) : null);
        ConfigurationSection antiBuild = getSectionAnyCase(arena, KEY_ANTI_BUILD);
        sendStatusLine(player, "anti-build.base-generator-radius",
                antiBuild != null && antiBuild.contains("base-generator-radius")
                        ? String.valueOf(antiBuild.getInt("base-generator-radius"))
                        : null);
        sendStatusLine(player, "anti-build.advanced-generator-radius",
                antiBuild != null && antiBuild.contains("advanced-generator-radius")
                        ? String.valueOf(antiBuild.getInt("advanced-generator-radius"))
                        : null);

        ConfigurationSection beds = arena.getConfigurationSection(KEY_BEDS);
        for (TeamColor team : TeamColor.ordered()) {
            String value = beds != null ? beds.getString(team.key()) : null;
            sendStatusLine(player, "bed." + team.key(), value);
        }

        ConfigurationSection spawns = getSectionAnyCase(arena, KEY_SPAWNS);
        for (TeamColor team : TeamColor.ordered()) {
            String value = spawns != null ? spawns.getString("base_" + team.key()) : null;
            sendStatusLine(player, "spawn." + team.key(), value);
        }

        ConfigurationSection baseGenerators = getSectionAnyCase(arena, KEY_BASE_GENERATORS);
        ConfigurationSection generators = arena.getConfigurationSection(KEY_GENERATORS);
        for (TeamColor team : TeamColor.ordered()) {
            String value = findBaseGeneratorValue(baseGenerators, generators, team);
            sendStatusLine(player, "base-gen." + team.key(), value);
        }

        if (generators != null) {
            List<String> diamondKeys = findGeneratorKeys(generators, "diamond_");
            for (String key : diamondKeys) {
                sendStatusLine(player, "generator.diamond." + key.substring("diamond_".length()),
                        generators.getString(key));
            }
            List<String> emeraldKeys = findGeneratorKeys(generators, "emerald_");
            for (String key : emeraldKeys) {
                sendStatusLine(player, "generator.emerald." + key.substring("emerald_".length()),
                        generators.getString(key));
            }
        }

        ConfigurationSection shops = getSectionAnyCase(arena, KEY_SHOPS);
        for (TeamColor team : TeamColor.ordered()) {
            ConfigurationSection teamSection = shops != null ? shops.getConfigurationSection(team.key()) : null;
            sendStatusLine(player, "shop." + team.key() + ".main",
                    teamSection != null ? teamSection.getString("main") : null);
            sendStatusLine(player, "shop." + team.key() + ".upgrades",
                    teamSection != null ? teamSection.getString("upgrades") : null);
        }

        player.sendMessage(Component.text("Use /bw setup " + arenaId + " <key> to save your current location.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Beds: run the same command again to set the foot if needed.", NamedTextColor.DARK_GRAY));
    }

    public void createArena(Player player, String arenaId) {
        YamlConfiguration config = loadConfig();
        ConfigurationSection arenas = config.getConfigurationSection(ARENAS_PATH);
        if (arenas == null) {
            arenas = config.createSection(ARENAS_PATH);
        }
        if (arenas.getConfigurationSection(arenaId) != null) {
            player.sendMessage(Component.text("Arena already exists: " + arenaId, NamedTextColor.RED));
            showSetupList(player, arenaId);
            return;
        }
        ConfigurationSection arena = arenas.createSection(arenaId);
        Location location = player.getLocation();
        arena.set(KEY_WORLD, location.getWorld().getName());
        arena.set(KEY_CENTER, formatPoint(location));
        arena.set(KEY_CORNER_1, "");
        arena.set(KEY_CORNER_2, "");
        arena.set(KEY_BASE_RADIUS, 0);
        arena.set(KEY_LOBBY_HEIGHT, location.getBlockY());
        ConfigurationSection antiBuild = arena.createSection(KEY_ANTI_BUILD);
        antiBuild.set("base-generator-radius", 0);
        antiBuild.set("advanced-generator-radius", 0);
        ConfigurationSection beds = arena.createSection(KEY_BEDS);
        ConfigurationSection generators = arena.createSection(KEY_GENERATORS);
        ConfigurationSection spawns = arena.createSection(KEY_SPAWNS);
        ConfigurationSection baseGenerators = arena.createSection(KEY_BASE_GENERATORS);
        ConfigurationSection shops = arena.createSection(KEY_SHOPS);
        for (TeamColor team : TeamColor.ordered()) {
            beds.set(team.key(), "");
            spawns.set("base_" + team.key(), "");
            baseGenerators.set("base_gen_" + team.key(), "");
            ConfigurationSection teamShop = shops.createSection(team.key());
            teamShop.set("main", "");
            teamShop.set("upgrades", "");
        }
        for (int i = 1; i <= 4; i++) {
            generators.set("diamond_" + i, "");
            generators.set("emerald_" + i, "");
        }
        populateGeneratorSettings(arena);
        populateEventTimes(arena);
        saveAndReload(player, config);
        player.sendMessage(Component.text("Arena created: " + arenaId, NamedTextColor.GREEN));
        showSetupList(player, arenaId);
    }

    public void applySetup(Player player, String arenaId, String rawTarget) {
        String trimmed = rawTarget == null ? "" : rawTarget.trim();
        if (trimmed.isBlank()) {
            player.sendMessage(Component.text("Unknown setup key: " + rawTarget, NamedTextColor.RED));
            return;
        }
        Optional<SetupTarget> targetOptional = SetupTarget.parse(trimmed);
        Integer numericValue = null;
        if (targetOptional.isEmpty()) {
            String[] tokens = trimmed.split("\\s+");
            String keyPart = trimmed;
            if (tokens.length > 1) {
                Integer parsed = parseInteger(tokens[tokens.length - 1]);
                if (parsed != null) {
                    numericValue = parsed;
                    keyPart = String.join(" ", java.util.Arrays.copyOf(tokens, tokens.length - 1));
                }
            }
            targetOptional = SetupTarget.parse(keyPart);
        }
        if (targetOptional.isEmpty()) {
            player.sendMessage(Component.text("Unknown setup key: " + rawTarget, NamedTextColor.RED));
            return;
        }
        YamlConfiguration config = loadConfig();
        ConfigurationSection arena = getOrCreateArena(config, arenaId, false);
        if (arena == null) {
            player.sendMessage(Component.text("Arena not found: " + arenaId, NamedTextColor.RED));
            player.sendMessage(Component.text("Use /bw setup new " + arenaId + " to create it.", NamedTextColor.GRAY));
            return;
        }

        SetupTarget target = targetOptional.get();
        Location location = player.getLocation();

        if (target.kind() == SetupKind.BED) {
            PendingSetup pendingSetup = pendingBeds.get(player.getUniqueId());
            if (trySaveBedFromBlock(player, arena, target, location)) {
                pendingBeds.remove(player.getUniqueId());
                saveAndReload(player, config);
                player.sendMessage(Component.text("Bed saved for " + target.team().displayName() + ".", NamedTextColor.GREEN));
                return;
            }
            if (pendingSetup != null
                    && pendingSetup.arenaId().equalsIgnoreCase(arenaId)
                    && pendingSetup.target().equals(target)) {
                saveBed(arena, target.team(), pendingSetup.firstBedPoint(), location);
                pendingBeds.remove(player.getUniqueId());
                saveAndReload(player, config);
                player.sendMessage(Component.text("Bed saved for " + target.team().displayName() + ".", NamedTextColor.GREEN));
                return;
            }
            BlockPoint head = new BlockPoint(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            pendingBeds.put(player.getUniqueId(), new PendingSetup(arenaId, target, head));
            player.sendMessage(Component.text("Bed head saved. Run /bw setup " + arenaId + " " + target.label()
                    + " again for the foot.", NamedTextColor.YELLOW));
            return;
        }

        pendingBeds.remove(player.getUniqueId());
        switch (target.kind()) {
            case WORLD -> arena.set(KEY_WORLD, location.getWorld().getName());
            case CENTER -> arena.set(KEY_CENTER, formatPoint(location));
            case CORNER -> setCorner(arena, target.cornerIndex(), location);
            case BASE_RADIUS -> {
                if (!applyNumeric(arena, KEY_BASE_RADIUS, numericValue, player)) {
                    return;
                }
            }
            case ANTI_BUILD_BASE -> {
                if (!applyAntiBuildRadius(arena, "base-generator-radius", numericValue, player)) {
                    return;
                }
            }
            case ANTI_BUILD_ADVANCED -> {
                if (!applyAntiBuildRadius(arena, "advanced-generator-radius", numericValue, player)) {
                    return;
                }
            }
            case LOBBY_HEIGHT -> arena.set(KEY_LOBBY_HEIGHT, location.getBlockY());
            case SPAWN -> setSpawn(arena, target.team(), location);
            case BASE_GEN -> setBaseGenerator(arena, target.team(), location);
            case GENERATOR -> setGenerator(arena, target.generatorType(), target.generatorIndex(), location);
            case SHOP -> setShop(arena, target.team(), target.shopType(), location);
            default -> {
            }
        }

        saveAndReload(player, config);
        player.sendMessage(Component.text("Saved " + target.label() + ".", NamedTextColor.GREEN));
    }

    public List<String> getSetupKeys(String arenaId) {
        List<String> keys = new ArrayList<>();
        keys.add("world");
        keys.add("center");
        keys.add(KEY_CORNER_1);
        keys.add(KEY_CORNER_2);
        keys.add(KEY_BASE_RADIUS);
        keys.add("anti-build.base-generator-radius");
        keys.add("anti-build.advanced-generator-radius");
        keys.add("lobby-height");
        for (TeamColor team : TeamColor.ordered()) {
            keys.add("bed." + team.key());
            keys.add("spawn." + team.key());
            keys.add("base-gen." + team.key());
            keys.add("shop." + team.key() + ".main");
            keys.add("shop." + team.key() + ".upgrades");
        }

        YamlConfiguration config = loadConfig();
        ConfigurationSection arena = getOrCreateArena(config, arenaId, false);
        if (arena != null) {
            ConfigurationSection generators = arena.getConfigurationSection(KEY_GENERATORS);
            if (generators != null) {
                keys.addAll(toGeneratorKeys(generators, "diamond_", "generator.diamond."));
                keys.addAll(toGeneratorKeys(generators, "emerald_", "generator.emerald."));
            }
        }
        keys.add("generator.diamond.1");
        keys.add("generator.emerald.1");
        return keys;
    }

    private void saveAndReload(Player player, YamlConfiguration config) {
        File file = getConfigFile();
        try {
            config.save(file);
        } catch (IOException ex) {
            player.sendMessage(Component.text("Failed to save bedwars.yml.", NamedTextColor.RED));
            plugin.getLogger().warning("Failed to save bedwars.yml: " + ex.getMessage());
            return;
        }
        bedwarsManager.loadArenas();
    }

    private void saveBed(ConfigurationSection arena, TeamColor team, BlockPoint head, Location footLocation) {
        ConfigurationSection beds = getOrCreateSection(arena, KEY_BEDS);
        BlockPoint foot = new BlockPoint(footLocation.getBlockX(), footLocation.getBlockY(), footLocation.getBlockZ());
        beds.set(team.key(), formatPoint(head) + ", " + formatPoint(foot));
    }

    private void setSpawn(ConfigurationSection arena, TeamColor team, Location location) {
        ConfigurationSection spawns = getOrCreateSection(arena, KEY_SPAWNS);
        spawns.set("base_" + team.key(), formatPoint(location));
    }

    private void setBaseGenerator(ConfigurationSection arena, TeamColor team, Location location) {
        ConfigurationSection baseGenerators = getOrCreateSection(arena, KEY_BASE_GENERATORS);
        baseGenerators.set("base_gen_" + team.key(), formatPoint(location));
    }

    private void setGenerator(ConfigurationSection arena, GeneratorType type, int index, Location location) {
        ConfigurationSection generators = getOrCreateSection(arena, KEY_GENERATORS);
        String key = (type == GeneratorType.DIAMOND ? "diamond_" : "emerald_") + index;
        generators.set(key, formatPoint(location));
    }

    private void setShop(ConfigurationSection arena, TeamColor team, ShopType shopType, Location location) {
        ConfigurationSection shops = getOrCreateSection(arena, KEY_SHOPS);
        ConfigurationSection teamSection = getOrCreateSection(shops, team.key());
        teamSection.set(shopType.configKey(), formatPointWithYaw(location));
    }

    private void setCorner(ConfigurationSection arena, Integer cornerIndex, Location location) {
        if (cornerIndex == null) {
            return;
        }
        if (cornerIndex == 1) {
            arena.set(KEY_CORNER_1, formatPoint(location));
        } else if (cornerIndex == 2) {
            arena.set(KEY_CORNER_2, formatPoint(location));
        }
    }

    private boolean applyNumeric(ConfigurationSection arena, String key, Integer value, Player player) {
        if (value == null) {
            player.sendMessage(Component.text("Provide a numeric value for " + key + ".", NamedTextColor.RED));
            return false;
        }
        arena.set(key, value);
        return true;
    }

    private boolean applyAntiBuildRadius(ConfigurationSection arena, String key, Integer value, Player player) {
        if (value == null) {
            player.sendMessage(Component.text("Provide a numeric value for anti-build.", NamedTextColor.RED));
            return false;
        }
        ConfigurationSection antiBuild = getOrCreateSection(arena, KEY_ANTI_BUILD);
        antiBuild.set(key, value);
        return true;
    }

    private void populateGeneratorSettings(ConfigurationSection arena) {
        GeneratorSettings defaults = GeneratorSettings.defaults();
        ConfigurationSection settings = arena.createSection(KEY_GENERATOR_SETTINGS);
        ConfigurationSection baseForge = settings.createSection("base-forge");
        ConfigurationSection iron = baseForge.createSection("iron");
        iron.set("intervals-seconds", toSecondsList(defaults.getBaseIronIntervals()));
        iron.set("amounts", toIntList(defaults.getBaseIronAmounts()));
        iron.set("caps", toIntList(defaults.getBaseIronCaps()));
        ConfigurationSection gold = baseForge.createSection("gold");
        gold.set("intervals-seconds", toSecondsList(defaults.getBaseGoldIntervals()));
        gold.set("amounts", toIntList(defaults.getBaseGoldAmounts()));
        gold.set("caps", toIntList(defaults.getBaseGoldCaps()));
        ConfigurationSection emerald = baseForge.createSection("emerald");
        emerald.set("intervals-seconds", toSecondsList(defaults.getBaseEmeraldIntervals()));
        emerald.set("amounts", toIntList(defaults.getBaseEmeraldAmounts()));
        emerald.set("caps", toIntList(defaults.getBaseEmeraldCaps()));

        ConfigurationSection diamond = settings.createSection("diamond");
        diamond.set("intervals-seconds", toSecondsList(defaults.getDiamondIntervals()));
        diamond.set("cap", defaults.getDiamondCap());

        ConfigurationSection advancedEmerald = settings.createSection("emerald");
        advancedEmerald.set("intervals-seconds", toSecondsList(defaults.getEmeraldIntervals()));
        advancedEmerald.set("cap", defaults.getEmeraldCap());

        long resourceDespawnSeconds = Math.round(defaults.getResourceDespawnMillis() / 1000.0);
        settings.set("resource-despawn-seconds", resourceDespawnSeconds);
    }

    private void populateEventTimes(ConfigurationSection arena) {
        EventSettings defaults = EventSettings.defaults();
        ConfigurationSection eventTimes = arena.createSection(KEY_EVENT_TIMES);
        eventTimes.set("tier-2", defaults.getTier2Delay());
        eventTimes.set("tier-3", defaults.getTier3Delay());
        eventTimes.set("bed-destruction", defaults.getBedDestructionDelay());
        eventTimes.set("sudden-death", defaults.getSuddenDeathDelay());
        eventTimes.set("game-end", defaults.getGameEndDelay());
    }

    private List<Double> toSecondsList(long[] ticks) {
        List<Double> values = new ArrayList<>(ticks.length);
        for (long tick : ticks) {
            values.add(tick / 20.0);
        }
        return values;
    }

    private List<Integer> toIntList(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int value : values) {
            list.add(value);
        }
        return list;
    }

    private YamlConfiguration loadConfig() {
        File file = getConfigFile();
        return YamlConfiguration.loadConfiguration(file);
    }

    private File getConfigFile() {
        return new File(plugin.getDataFolder(), "bedwars.yml");
    }

    private ConfigurationSection getOrCreateArena(YamlConfiguration config, String arenaId, boolean create) {
        ConfigurationSection arenas = config.getConfigurationSection(ARENAS_PATH);
        if (arenas == null) {
            if (!create) {
                return null;
            }
            arenas = config.createSection(ARENAS_PATH);
        }
        ConfigurationSection arena = arenas.getConfigurationSection(arenaId);
        if (arena == null && create) {
            arena = arenas.createSection(arenaId);
        }
        return arena;
    }

    private ConfigurationSection getOrCreateSection(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            section = parent.createSection(key);
        }
        return section;
    }

    private ConfigurationSection getSectionAnyCase(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section != null) {
            return section;
        }
        for (String child : parent.getKeys(false)) {
            if (child.equalsIgnoreCase(key)) {
                return parent.getConfigurationSection(child);
            }
        }
        return null;
    }

    private void sendStatusLine(Player player, String key, String value) {
        if (value == null || value.isBlank()) {
            player.sendMessage(Component.text(key + ": missing", NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text(key + ": " + value, NamedTextColor.GRAY));
        }
    }

    private String formatPoint(Location location) {
        return location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private String formatPoint(BlockPoint point) {
        return point.x() + " " + point.y() + " " + point.z();
    }

    private String formatPointWithYaw(Location location) {
        return String.format(Locale.ROOT, "%d %d %d %s",
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                yawToCardinal(location.getYaw()));
    }

    private String yawToCardinal(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized < 0.0f) {
            normalized += 360.0f;
        }
        int index = Math.round(normalized / 90.0f) % 4;
        return switch (index) {
            case 0 -> "SOUTH";
            case 1 -> "WEST";
            case 2 -> "NORTH";
            default -> "EAST";
        };
    }

    private String findBaseGeneratorValue(ConfigurationSection baseGenerators, ConfigurationSection generators, TeamColor team) {
        if (baseGenerators == null) {
            if (generators != null) {
                String fallback = "base_" + team.key();
                if (generators.contains(fallback)) {
                    return generators.getString(fallback);
                }
            }
            return null;
        }
        String key = "base_gen_" + team.key();
        if (baseGenerators.contains(key)) {
            return baseGenerators.getString(key);
        }
        key = "base_gem_" + team.key();
        if (baseGenerators.contains(key)) {
            return baseGenerators.getString(key);
        }
        key = "base_" + team.key();
        if (baseGenerators.contains(key)) {
            return baseGenerators.getString(key);
        }
        if (generators != null) {
            String fallback = "base_" + team.key();
            if (generators.contains(fallback)) {
                return generators.getString(fallback);
            }
        }
        return null;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> findGeneratorKeys(ConfigurationSection generators, String prefix) {
        List<String> keys = new ArrayList<>();
        for (String key : generators.getKeys(false)) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        keys.sort(String::compareToIgnoreCase);
        return keys;
    }

    private List<String> toGeneratorKeys(ConfigurationSection generators, String prefix, String outputPrefix) {
        List<String> keys = new ArrayList<>();
        for (String key : findGeneratorKeys(generators, prefix)) {
            keys.add(outputPrefix + key.substring(prefix.length()));
        }
        return keys;
    }

    private boolean trySaveBedFromBlock(Player player, ConfigurationSection arena, SetupTarget target, Location location) {
        Block block = location.getBlock();
        Bed bedData = block.getBlockData() instanceof Bed data ? data : null;
        if (bedData == null) {
            block = block.getRelative(org.bukkit.block.BlockFace.DOWN);
            bedData = block.getBlockData() instanceof Bed data ? data : null;
        }
        if (bedData == null) {
            return false;
        }
        Block headBlock = bedData.getPart() == Bed.Part.HEAD
                ? block
                : block.getRelative(bedData.getFacing());
        Block footBlock = bedData.getPart() == Bed.Part.FOOT
                ? block
                : block.getRelative(bedData.getFacing().getOppositeFace());
        BlockPoint head = new BlockPoint(headBlock.getX(), headBlock.getY(), headBlock.getZ());
        saveBed(arena, target.team(), head, footBlock.getLocation());
        player.sendMessage(Component.text("Detected bed blocks automatically.", NamedTextColor.DARK_GRAY));
        return true;
    }

    private enum SetupKind {
        WORLD,
        CENTER,
        CORNER,
        BASE_RADIUS,
        ANTI_BUILD_BASE,
        ANTI_BUILD_ADVANCED,
        LOBBY_HEIGHT,
        BED,
        SPAWN,
        BASE_GEN,
        GENERATOR,
        SHOP
    }

    private record SetupTarget(SetupKind kind,
                               TeamColor team,
                               GeneratorType generatorType,
                               Integer generatorIndex,
                               ShopType shopType,
                               Integer cornerIndex,
                               String label) {
        static Optional<SetupTarget> parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT)
                    .replace('_', '.')
                    .replace(' ', '.');
            if (normalized.equals(KEY_WORLD)) {
                return Optional.of(new SetupTarget(SetupKind.WORLD, null, null, null, null, null, "world"));
            }
            if (normalized.equals(KEY_CENTER)) {
                return Optional.of(new SetupTarget(SetupKind.CENTER, null, null, null, null, null, "center"));
            }
            if (normalized.equals("corner.1") || normalized.equals("corner1") || normalized.equals("corner_1")) {
                return Optional.of(new SetupTarget(SetupKind.CORNER, null, null, null, null, 1, KEY_CORNER_1));
            }
            if (normalized.equals("corner.2") || normalized.equals("corner2") || normalized.equals("corner_2")) {
                return Optional.of(new SetupTarget(SetupKind.CORNER, null, null, null, null, 2, KEY_CORNER_2));
            }
            if (normalized.equals("base-radius") || normalized.equals("base.radius") || normalized.equals("baseradius")) {
                return Optional.of(new SetupTarget(SetupKind.BASE_RADIUS, null, null, null, null, null, KEY_BASE_RADIUS));
            }
            if (isAntiBuildKey(normalized)) {
                SetupKind kind = isAdvancedAntiBuildKey(normalized)
                        ? SetupKind.ANTI_BUILD_ADVANCED
                        : SetupKind.ANTI_BUILD_BASE;
                String label = kind == SetupKind.ANTI_BUILD_ADVANCED
                        ? "anti-build.advanced-generator-radius"
                        : "anti-build.base-generator-radius";
                return Optional.of(new SetupTarget(kind, null, null, null, null, null, label));
            }
            if (normalized.equals("lobby-height") || normalized.equals("lobbyheight")) {
                return Optional.of(new SetupTarget(SetupKind.LOBBY_HEIGHT, null, null, null, null, null, "lobby-height"));
            }
            if (normalized.startsWith("bed.")) {
                TeamColor team = TeamColor.fromKey(normalized.substring("bed.".length()));
                if (team != null) {
                    return Optional.of(new SetupTarget(SetupKind.BED, team, null, null, null, null, "bed." + team.key()));
                }
            }
            if (normalized.startsWith("spawn.")) {
                TeamColor team = TeamColor.fromKey(normalized.substring("spawn.".length()));
                if (team != null) {
                    return Optional.of(new SetupTarget(SetupKind.SPAWN, team, null, null, null, null, "spawn." + team.key()));
                }
            }
            if (normalized.startsWith("base-gen.") || normalized.startsWith("basegen.") || normalized.startsWith("base.gen.")) {
                String key = normalized.substring(normalized.indexOf('.') + 1);
                TeamColor team = TeamColor.fromKey(key);
                if (team != null) {
                    return Optional.of(new SetupTarget(SetupKind.BASE_GEN, team, null, null, null, null, "base-gen." + team.key()));
                }
            }
            if (normalized.startsWith("generator.") || normalized.startsWith("diamond.") || normalized.startsWith("emerald.")) {
                String token = normalized.startsWith("generator.") ? normalized.substring("generator.".length()) : normalized;
                String[] parts = token.split("\\.");
                if (parts.length == 2) {
                    GeneratorType type = parseGeneratorType(parts[0]);
                    Integer index = parseIndex(parts[1]);
                    if (type != null && index != null) {
                        return Optional.of(new SetupTarget(SetupKind.GENERATOR, null, type, index, null, null,
                                "generator." + parts[0] + "." + index));
                    }
                }
            }
            if (normalized.startsWith("shop.")) {
                String[] parts = normalized.split("\\.");
                if (parts.length == 3) {
                    TeamColor team = TeamColor.fromKey(parts[1]);
                    ShopType shop = parseShopType(parts[2]);
                    if (team != null && shop != null) {
                        String label = "shop." + team.key() + "." + parts[2];
                        return Optional.of(new SetupTarget(SetupKind.SHOP, team, null, null, shop, null, label));
                    }
                }
            }
            return Optional.empty();
        }

        private static boolean isAntiBuildKey(String normalized) {
            return (normalized.startsWith("anti-build")
                    || normalized.startsWith("anti.build")
                    || normalized.startsWith("antibuild"))
                    && (normalized.contains("generator")
                    || normalized.contains("base-generator-radius")
                    || normalized.contains("advanced-generator-radius")
                    || normalized.contains("base.generator.radius")
                    || normalized.contains("advanced.generator.radius"));
        }

        private static boolean isAdvancedAntiBuildKey(String normalized) {
            return normalized.contains("advanced")
                    || normalized.contains("advanced-generator-radius")
                    || normalized.contains("advanced.generator.radius");
        }

        private static GeneratorType parseGeneratorType(String value) {
            return switch (value) {
                case "diamond" -> GeneratorType.DIAMOND;
                case "emerald" -> GeneratorType.EMERALD;
                default -> null;
            };
        }

        private static Integer parseIndex(String value) {
            try {
                int index = Integer.parseInt(value);
                return index > 0 ? index : null;
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private static ShopType parseShopType(String value) {
            return ShopType.fromKey(value);
        }
    }

    private record PendingSetup(String arenaId, SetupTarget target, BlockPoint firstBedPoint) {
    }
}
