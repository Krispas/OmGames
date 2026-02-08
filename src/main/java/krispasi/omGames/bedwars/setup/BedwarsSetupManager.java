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
import krispasi.omGames.bedwars.model.BlockPoint;
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

public class BedwarsSetupManager {
    private static final String ARENAS_PATH = "arenas";
    private static final String KEY_WORLD = "world";
    private static final String KEY_CENTER = "center";
    private static final String KEY_LOBBY_HEIGHT = "lobby-height";
    private static final String KEY_BEDS = "beds";
    private static final String KEY_GENERATORS = "generators";
    private static final String KEY_BASE_GENERATORS = "Base_Generators";
    private static final String KEY_SPAWNS = "Spawns";
    private static final String KEY_SHOPS = "Shops";

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
        sendStatusLine(player, "lobby-height", arena.contains(KEY_LOBBY_HEIGHT)
                ? String.valueOf(arena.getInt(KEY_LOBBY_HEIGHT)) : null);

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
        arena.set(KEY_LOBBY_HEIGHT, location.getBlockY());
        ConfigurationSection antiBuild = arena.createSection("anti-build");
        antiBuild.set("base-generator-radius", 0);
        antiBuild.set("advanced-generator-radius", 0);
        arena.createSection(KEY_BEDS);
        arena.createSection(KEY_GENERATORS);
        arena.createSection(KEY_SPAWNS);
        arena.createSection(KEY_BASE_GENERATORS);
        arena.createSection(KEY_SHOPS);
        saveAndReload(player, config);
        player.sendMessage(Component.text("Arena created: " + arenaId, NamedTextColor.GREEN));
        showSetupList(player, arenaId);
    }

    public void applySetup(Player player, String arenaId, String rawTarget) {
        Optional<SetupTarget> targetOptional = SetupTarget.parse(rawTarget);
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
        LOBBY_HEIGHT,
        BED,
        SPAWN,
        BASE_GEN,
        GENERATOR,
        SHOP
    }

    private record SetupTarget(SetupKind kind, TeamColor team, GeneratorType generatorType, Integer generatorIndex,
                               ShopType shopType, String label) {
        static Optional<SetupTarget> parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT)
                    .replace('_', '.')
                    .replace(' ', '.');
            if (normalized.equals(KEY_WORLD)) {
                return Optional.of(new SetupTarget(SetupKind.WORLD, null, null, null, null, "world"));
            }
            if (normalized.equals(KEY_CENTER)) {
                return Optional.of(new SetupTarget(SetupKind.CENTER, null, null, null, null, "center"));
            }
            if (normalized.equals("lobby-height") || normalized.equals("lobbyheight")) {
                return Optional.of(new SetupTarget(SetupKind.LOBBY_HEIGHT, null, null, null, null, "lobby-height"));
            }
            if (normalized.startsWith("bed.")) {
                TeamColor team = TeamColor.fromKey(normalized.substring("bed.".length()));
                if (team != null) {
                    return Optional.of(new SetupTarget(SetupKind.BED, team, null, null, null, "bed." + team.key()));
                }
            }
            if (normalized.startsWith("spawn.")) {
                TeamColor team = TeamColor.fromKey(normalized.substring("spawn.".length()));
                if (team != null) {
                    return Optional.of(new SetupTarget(SetupKind.SPAWN, team, null, null, null, "spawn." + team.key()));
                }
            }
            if (normalized.startsWith("base-gen.") || normalized.startsWith("basegen.") || normalized.startsWith("base.gen.")) {
                String key = normalized.substring(normalized.indexOf('.') + 1);
                TeamColor team = TeamColor.fromKey(key);
                if (team != null) {
                    return Optional.of(new SetupTarget(SetupKind.BASE_GEN, team, null, null, null, "base-gen." + team.key()));
                }
            }
            if (normalized.startsWith("generator.") || normalized.startsWith("diamond.") || normalized.startsWith("emerald.")) {
                String token = normalized.startsWith("generator.") ? normalized.substring("generator.".length()) : normalized;
                String[] parts = token.split("\\.");
                if (parts.length == 2) {
                    GeneratorType type = parseGeneratorType(parts[0]);
                    Integer index = parseIndex(parts[1]);
                    if (type != null && index != null) {
                        return Optional.of(new SetupTarget(SetupKind.GENERATOR, null, type, index, null,
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
                        return Optional.of(new SetupTarget(SetupKind.SHOP, team, null, null, shop, label));
                    }
                }
            }
            return Optional.empty();
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
