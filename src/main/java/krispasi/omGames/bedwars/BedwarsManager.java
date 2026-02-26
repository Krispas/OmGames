package krispasi.omGames.bedwars;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.config.BedwarsConfigLoader;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.item.CustomItemConfig;
import krispasi.omGames.bedwars.item.CustomItemConfigLoader;
import krispasi.omGames.bedwars.shop.QuickBuyService;
import krispasi.omGames.bedwars.stats.BedwarsLobbyLeaderboard;
import krispasi.omGames.bedwars.stats.BedwarsStatsService;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopConfigLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.FireworkEffect;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import krispasi.omGames.bedwars.gui.MapSelectMenu;
import krispasi.omGames.bedwars.gui.ShopMenu;
import krispasi.omGames.bedwars.gui.TeamAssignMenu;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.shop.ShopCategoryType;

/**
 * Service layer and coordinator for BedWars runtime.
 * <p>Loads arenas, shop configuration, and custom items, and manages quick-buy
 * data via {@link krispasi.omGames.bedwars.shop.QuickBuyService}.</p>
 * <p>Owns the active {@link krispasi.omGames.bedwars.game.GameSession} and handles
 * start, stop, and end-of-game cleanup.</p>
 * @see krispasi.omGames.bedwars.game.GameSession
 */
public class BedwarsManager {
    private static final int DEFAULT_CENTER_RADIUS = 32;
    private static final double DEFAULT_GARRY_FAMILY_DAMAGE = 12.0;
    private static final double DEFAULT_GARRY_FAMILY_HEALTH = 200.0;
    private static final double DEFAULT_GARRY_FAMILY_RANGE = 32.0;
    private static final double DEFAULT_GARRY_FAMILY_SPEED = 0.3;
    private final JavaPlugin plugin;
    private final QuickBuyService quickBuyService;
    private final BedwarsStatsService statsService;
    private final BedwarsLobbyLeaderboard lobbyLeaderboard;
    private Map<String, Arena> arenas = Map.of();
    private GameSession activeSession;
    private ShopConfig shopConfig = ShopConfig.empty();
    private CustomItemConfig customItemConfig = CustomItemConfig.empty();
    private double garryFamilyDamage = DEFAULT_GARRY_FAMILY_DAMAGE;
    private double garryFamilyHealth = DEFAULT_GARRY_FAMILY_HEALTH;
    private double garryFamilyRange = DEFAULT_GARRY_FAMILY_RANGE;
    private double garryFamilySpeed = DEFAULT_GARRY_FAMILY_SPEED;

    public BedwarsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.quickBuyService = new QuickBuyService(plugin);
        this.statsService = new BedwarsStatsService(plugin);
        this.lobbyLeaderboard = new BedwarsLobbyLeaderboard(plugin, statsService);
    }

    public File getBedwarsDataFolder() {
        File folder = new File(plugin.getDataFolder(), "Bedwars");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public File getBedwarsConfigFile(String name) {
        return new File(getBedwarsDataFolder(), name);
    }

    public void loadArenas() {
        File configFile = getBedwarsConfigFile("bedwars.yml");
        migrateBedwarsConfig(configFile);
        BedwarsConfigLoader loader = new BedwarsConfigLoader(configFile, plugin.getLogger());
        arenas = loader.load();
        plugin.getLogger().info("Loaded " + arenas.size() + " BedWars arenas.");
    }

    public void loadShopConfig() {
        File configFile = getBedwarsConfigFile("shop.yml");
        migrateShopConfig(configFile);
        ShopConfig baseConfig = new ShopConfigLoader(configFile, plugin.getLogger()).load();
        File rotatingFile = getBedwarsConfigFile("rotating-items.yml");
        if (rotatingFile.exists()) {
            migrateRotatingItemsConfig(rotatingFile);
            ShopConfig rotatingConfig = new ShopConfigLoader(rotatingFile, plugin.getLogger()).load();
            shopConfig = ShopConfig.merge(baseConfig, rotatingConfig);
            garryFamilyDamage = loadGarryFamilyDamage(rotatingFile);
            garryFamilyHealth = loadGarryFamilyHealth(rotatingFile);
            garryFamilyRange = loadGarryFamilyRange(rotatingFile);
            garryFamilySpeed = loadGarryFamilySpeed(rotatingFile);
        } else {
            shopConfig = baseConfig;
            garryFamilyDamage = DEFAULT_GARRY_FAMILY_DAMAGE;
            garryFamilyHealth = DEFAULT_GARRY_FAMILY_HEALTH;
            garryFamilyRange = DEFAULT_GARRY_FAMILY_RANGE;
            garryFamilySpeed = DEFAULT_GARRY_FAMILY_SPEED;
        }
        plugin.getLogger().info("Loaded BedWars shop config.");
    }

    public void loadCustomItems() {
        File configFile = getBedwarsConfigFile("custom-items.yml");
        migrateCustomItems(configFile);
        CustomItemConfigLoader loader = new CustomItemConfigLoader(configFile, plugin.getLogger());
        customItemConfig = loader.load();
        plugin.getLogger().info("Loaded BedWars custom items.");
    }

    public void loadQuickBuy() {
        quickBuyService.load();
    }

    public void loadStats() {
        statsService.load();
    }

    public void startLobbyLeaderboard() {
        lobbyLeaderboard.start();
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public Arena getArena(String id) {
        return arenas.get(id);
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public GameSession getActiveSession() {
        return activeSession;
    }

    public ShopConfig getShopConfig() {
        return shopConfig;
    }

    public CustomItemConfig getCustomItemConfig() {
        return customItemConfig;
    }

    public QuickBuyService getQuickBuyService() {
        return quickBuyService;
    }

    public BedwarsStatsService getStatsService() {
        return statsService;
    }

    public double getGarryFamilyDamage() {
        return garryFamilyDamage;
    }

    public double getGarryFamilyHealth() {
        return garryFamilyHealth;
    }

    public double getGarryFamilyRange() {
        return garryFamilyRange;
    }

    public double getGarryFamilySpeed() {
        return garryFamilySpeed;
    }

    public boolean isBedwarsWorld(String worldName) {
        for (Arena arena : arenas.values()) {
            if (arena.getWorldName().equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    public void openMapSelect(Player player) {
        openMapSelect(player, true);
    }

    public void openMapSelect(Player player, boolean statsEnabled) {
        if ( arenas.isEmpty()) {
            player.sendMessage(Component.text("No arenas configured.", NamedTextColor.RED));
            return;
        }
        new MapSelectMenu(this, statsEnabled).open(player);
    }

    public void openTeamAssignMenu(Player player, Arena arena) {
        openTeamAssignMenu(player, arena, true);
    }

    public void openTeamAssignMenu(Player player, Arena arena, boolean statsEnabled) {
        GameSession session = new GameSession(this, arena);
        session.setStatsEnabled(statsEnabled);
        new TeamAssignMenu(this, session).open(player);
    }

    public void openQuickBuyEditor(Player player) {
        if (player == null) {
            return;
        }
        if (shopConfig == null) {
            player.sendMessage(Component.text("Shop config is not loaded.", NamedTextColor.RED));
            return;
        }
        Arena arena = arenas.values().stream().findFirst().orElse(null);
        if (arena == null) {
            player.sendMessage(Component.text("No arenas configured.", NamedTextColor.RED));
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!quickBuyService.isEditing(playerId)) {
            quickBuyService.toggleEditing(playerId);
        }
        quickBuyService.clearPendingSlot(playerId);
        GameSession menuSession = new GameSession(this, arena);
        new ShopMenu(menuSession, shopConfig, ShopCategoryType.QUICK_BUY, player).open(player);
    }

    public void startSession(Player initiator, GameSession session) {
        if (session.getAssignedCount() == 0 && !session.isTeamPickEnabled()) {
            initiator.sendMessage(Component.text("Assign at least one player to a team or enable Team Pick.", NamedTextColor.RED));
            return;
        }

        World world = session.getArena().getWorld();
        if (world == null) {
            initiator.sendMessage(Component.text("World not loaded: " + session.getArena().getWorldName(), NamedTextColor.RED));
            return;
        }

        if (activeSession != null) {
            activeSession.stop();
        }

        activeSession = session;
        session.start(plugin, initiator);
        initiator.sendMessage(Component.text("BedWars started on " + session.getArena().getId() + ".", NamedTextColor.GREEN));
    }

    public void startLobby(Player initiator, GameSession session, int lobbySeconds) {
        if (session.getAssignedCount() == 0 && !session.isTeamPickEnabled()) {
            initiator.sendMessage(Component.text("Assign at least one player to a team or enable Team Pick.", NamedTextColor.RED));
            return;
        }

        World world = session.getArena().getWorld();
        if (world == null) {
            initiator.sendMessage(Component.text("World not loaded: " + session.getArena().getWorldName(), NamedTextColor.RED));
            return;
        }

        if (activeSession != null) {
            activeSession.stop();
        }

        activeSession = session;
        session.startLobby(plugin, initiator, lobbySeconds);
        initiator.sendMessage(Component.text("BedWars lobby started on " + session.getArena().getId() + ".", NamedTextColor.GREEN));
    }

    public void stopSession(Player initiator) {
        if (activeSession == null) {
            initiator.sendMessage(Component.text("No BedWars session is running.", NamedTextColor.RED));
            return;
        }
        activeSession.stop();
        activeSession = null;
        initiator.sendMessage(Component.text("BedWars session stopped.", NamedTextColor.YELLOW));
    }

    public void endSession(GameSession session, TeamColor winner) {
        if (session != activeSession) {
            return;
        }
        Component message;
        if (winner != null) {
            message = Component.text("Team ", NamedTextColor.GOLD)
                    .append(winner.displayComponent())
                    .append(Component.text(" wins!", NamedTextColor.GOLD));
        } else {
            message = Component.text("BedWars ended.", NamedTextColor.YELLOW);
        }
        for (UUID playerId : session.getAssignments().keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
                if (winner != null && winner.equals(session.getAssignments().get(playerId))) {
                    launchVictoryFirework(player, winner);
                }
            }
        }
        if (winner != null && session.isStatsEnabled()) {
            for (Map.Entry<UUID, TeamColor> entry : session.getAssignments().entrySet()) {
                if (winner.equals(entry.getValue())) {
                    statsService.addWin(entry.getKey());
                }
            }
        }
        session.stop();
        activeSession = null;
    }

    public void shutdown() {
        if (activeSession != null) {
            activeSession.stop();
            activeSession = null;
        }
        lobbyLeaderboard.stop();
        quickBuyService.shutdown();
        statsService.shutdown();
        clearDroppedItems();
    }

    private void clearDroppedItems() {
        for (Arena arena : arenas.values()) {
            World world = arena.getWorld();
            if (world == null) {
                continue;
            }
            for (Item item : world.getEntitiesByClass(Item.class)) {
                item.remove();
            }
        }
    }

    private void launchVictoryFirework(Player player, TeamColor team) {
        player.getWorld().spawn(player.getLocation(), Firework.class, firework -> {
            FireworkMeta meta = firework.getFireworkMeta();
            FireworkEffect effect = FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(team.dyeColor().getColor())
                    .flicker(true)
                    .trail(true)
                    .build();
            meta.addEffect(effect);
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        });
    }

    private void migrateShopConfig(File configFile) {
        if (!configFile.exists()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
        org.bukkit.configuration.ConfigurationSection item =
                findItemSection(config, "hardened_clay", "blocks");
        boolean changed = false;
        if (item != null) {
            String material = item.getString("material");
            if (material == null || material.equalsIgnoreCase("TERRACOTTA")) {
                item.set("material", "SMOOTH_BASALT");
                changed = true;
            }
            String displayName = item.getString("display-name");
            if (displayName == null || displayName.equalsIgnoreCase("Hardened Clay")) {
                item.set("display-name", "Smooth Basalt");
                changed = true;
            }
        }
        changed |= ensureShopItem(config, "mace", "melee", "MACE", 1,
                "GOLD", 12, "SWORD", "Mace");
        changed |= ensureShopItem(config, "netherite_spear", "melee", "NETHERITE_SWORD", 1,
                "EMERALD", 6, "SWORD", "Netherite Spear");
        changed |= ensureShopItem(config, "wind_charge", "utility", "WIND_CHARGE", 1,
                "GOLD", 2, "UTILITY", "Wind Charge");
        changed |= ensureShopEntry(config, "melee", "mace", 14);
        changed |= ensureShopEntry(config, "melee", "netherite_spear", 15);
        changed |= ensureShopEntry(config, "utility", "wind_charge", 24);
        changed |= ensureCategory(config, "miscellaneous", "Miscellaneous", "BREWING_STAND", 54);
        org.bukkit.configuration.ConfigurationSection misc =
                config.getConfigurationSection("shop.categories.miscellaneous");
        if (misc != null) {
            String icon = misc.getString("icon");
            if (icon == null || icon.equalsIgnoreCase("CHEST")) {
                misc.set("icon", "BREWING_STAND");
                changed = true;
            }
        }
        org.bukkit.configuration.ConfigurationSection magicMilk =
                findItemSection(config, "magic_milk", "utility");
        if (magicMilk != null) {
            String customItem = magicMilk.getString("custom-item");
            if (customItem == null || customItem.isBlank()) {
                magicMilk.set("custom-item", "magic_milk");
                changed = true;
            }
            java.util.List<String> lore = magicMilk.getStringList("lore");
            if (lore.isEmpty()) {
                magicMilk.set("lore", java.util.List.of("Grants temporary trap immunity."));
                changed = true;
            }
        }
        changed |= normalizeCostMaterials(config);
        if (!changed) {
            return;
        }
        try {
            config.save(configFile);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Failed to update shop.yml: " + ex.getMessage());
        }
    }

    private org.bukkit.configuration.ConfigurationSection findItemSection(
            org.bukkit.configuration.file.YamlConfiguration config,
            String id,
            String category) {
        String grouped = category != null ? "shop.items." + category + "." + id : null;
        if (grouped != null && config.isConfigurationSection(grouped)) {
            return config.getConfigurationSection(grouped);
        }
        String flat = "shop.items." + id;
        if (config.isConfigurationSection(flat)) {
            return config.getConfigurationSection(flat);
        }
        return null;
    }

    private boolean ensureShopItem(org.bukkit.configuration.file.YamlConfiguration config,
                                   String id,
                                   String category,
                                   String material,
                                   int amount,
                                   String costMaterial,
                                   int costAmount,
                                   String behavior,
                                   String displayName) {
        String base = resolveItemBasePath(config, category, id);
        if (config.contains(base)) {
            return false;
        }
        config.set(base + ".material", material);
        config.set(base + ".amount", amount);
        config.set(base + ".cost.material", costMaterial);
        config.set(base + ".cost.amount", costAmount);
        config.set(base + ".behavior", behavior);
        config.set(base + ".display-name", displayName);
        return true;
    }

    private String resolveItemBasePath(org.bukkit.configuration.file.YamlConfiguration config,
                                       String category,
                                       String id) {
        if (category != null && isGroupedItems(config)) {
            String groupPath = "shop.items." + category;
            if (!config.isConfigurationSection(groupPath)) {
                config.createSection(groupPath);
            }
            return groupPath + "." + id;
        }
        return "shop.items." + id;
    }

    private boolean isGroupedItems(org.bukkit.configuration.file.YamlConfiguration config) {
        org.bukkit.configuration.ConfigurationSection items =
                config.getConfigurationSection("shop.items");
        if (items == null) {
            return false;
        }
        for (String key : items.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (!section.isSet("material")) {
                return true;
            }
        }
        return false;
    }

    private boolean ensureShopEntry(org.bukkit.configuration.file.YamlConfiguration config,
                                    String category,
                                    String itemId,
                                    int desiredSlot) {
        String categoryPath = "shop.categories." + category;
        if (!config.isConfigurationSection(categoryPath)) {
            return false;
        }
        String entriesPath = categoryPath + ".entries";
        org.bukkit.configuration.ConfigurationSection entries =
                config.getConfigurationSection(entriesPath);
        if (entries == null) {
            entries = config.createSection(entriesPath);
        }
        if (entries.isSet(itemId)) {
            return false;
        }
        int size = config.getInt(categoryPath + ".size", 54);
        int slot = desiredSlot;
        if (slot < 0 || slot >= size || isSlotUsed(entries, slot)) {
            slot = findFirstFreeSlot(entries, size);
            if (slot < 0) {
                return false;
            }
        }
        entries.set(itemId, slot);
        return true;
    }

    private boolean isSlotUsed(org.bukkit.configuration.ConfigurationSection entries, int slot) {
        for (String key : entries.getKeys(false)) {
            if (entries.getInt(key, -1) == slot) {
                return true;
            }
        }
        return false;
    }

    private int findFirstFreeSlot(org.bukkit.configuration.ConfigurationSection entries, int size) {
        for (int i = 0; i < size; i++) {
            if (!isSlotUsed(entries, i)) {
                return i;
            }
        }
        return -1;
    }

    private boolean ensureCategory(org.bukkit.configuration.file.YamlConfiguration config,
                                   String key,
                                   String title,
                                   String icon,
                                   int size) {
        String base = "shop.categories." + key;
        if (config.isConfigurationSection(base)) {
            return false;
        }
        config.set(base + ".title", title);
        config.set(base + ".icon", icon);
        config.set(base + ".size", size);
        config.createSection(base + ".entries");
        return true;
    }

    private boolean normalizeCostMaterials(org.bukkit.configuration.file.YamlConfiguration config) {
        org.bukkit.configuration.ConfigurationSection items =
                config.getConfigurationSection("shop.items");
        if (items == null) {
            return false;
        }
        boolean changed = false;
        for (String key : items.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (section.isSet("material")) {
                changed |= normalizeCostMaterial(section);
                continue;
            }
            for (String nestedKey : section.getKeys(false)) {
                org.bukkit.configuration.ConfigurationSection nested = section.getConfigurationSection(nestedKey);
                if (nested == null || !nested.isSet("material")) {
                    continue;
                }
                changed |= normalizeCostMaterial(nested);
            }
        }
        return changed;
    }

    private boolean normalizeCostMaterial(org.bukkit.configuration.ConfigurationSection section) {
        org.bukkit.configuration.ConfigurationSection cost = section.getConfigurationSection("cost");
        if (cost == null) {
            return false;
        }
        String material = cost.getString("material");
        if (material == null || material.isBlank()) {
            return false;
        }
        String normalized = material.trim().toUpperCase(Locale.ROOT);
        if ("GOLD_INGOT".equals(normalized)) {
            cost.set("material", "GOLD");
            return true;
        }
        if ("IRON_INGOT".equals(normalized)) {
            cost.set("material", "IRON");
            return true;
        }
        return false;
    }

    private void migrateCustomItems(File configFile) {
        if (!configFile.exists()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
        boolean changed = false;
        org.bukkit.configuration.ConfigurationSection fireball =
                config.getConfigurationSection("custom-items.fireball");
        if (fireball != null) {
            double yield = fireball.getDouble("yield", -1.0);
            if (yield < 0.0 || Math.abs(yield - 2.2) < 0.0001) {
                fireball.set("yield", 3.2);
                changed = true;
            }
            if (!fireball.isSet("damage")) {
                fireball.set("damage", 4.0);
                changed = true;
            }
            if (!fireball.isSet("knockback")) {
                fireball.set("knockback", 1.6);
                changed = true;
            }
        }
        org.bukkit.configuration.ConfigurationSection customItems =
                config.getConfigurationSection("custom-items");
        if (customItems == null) {
            customItems = config.createSection("custom-items");
            changed = true;
        }
        org.bukkit.configuration.ConfigurationSection magicMilk =
                customItems.getConfigurationSection("magic_milk");
        if (magicMilk == null) {
            magicMilk = customItems.createSection("magic_milk");
            changed = true;
        }
        if (magicMilk.isSet("duration-seconds") && !magicMilk.isSet("lifetime-seconds")) {
            magicMilk.set("lifetime-seconds", magicMilk.getInt("duration-seconds"));
            changed = true;
        }
        changed |= ensureCustomItemValue(magicMilk, "type", "MAGIC_MILK");
        changed |= ensureCustomItemValue(magicMilk, "material", "MILK_BUCKET");
        changed |= ensureCustomItemValue(magicMilk, "lifetime-seconds", 30);
        if (!changed) {
            return;
        }
        try {
            config.save(configFile);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Failed to update custom-items.yml: " + ex.getMessage());
        }
    }

    private void migrateBedwarsConfig(File configFile) {
        if (!configFile.exists()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
        org.bukkit.configuration.ConfigurationSection arenasSection = config.getConfigurationSection("arenas");
        if (arenasSection == null) {
            return;
        }
        boolean changed = false;
        for (String arenaId : arenasSection.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection arena = arenasSection.getConfigurationSection(arenaId);
            if (arena == null) {
                continue;
            }
            if (!arena.isSet("center-radius")) {
                arena.set("center-radius", DEFAULT_CENTER_RADIUS);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        try {
            config.save(configFile);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Failed to update bedwars.yml: " + ex.getMessage());
        }
    }

    private boolean ensureCustomItemValue(org.bukkit.configuration.ConfigurationSection section,
                                          String key,
                                          Object value) {
        if (section == null || section.isSet(key)) {
            return false;
        }
        section.set(key, value);
        return true;
    }

    private void migrateRotatingItemsConfig(File configFile) {
        if (!configFile.exists()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
        boolean changed = false;
        if (!config.isSet("settings.garry-family-damage")) {
            config.set("settings.garry-family-damage", DEFAULT_GARRY_FAMILY_DAMAGE);
            changed = true;
        }
        if (!config.isSet("settings.garry-family-health")) {
            config.set("settings.garry-family-health", DEFAULT_GARRY_FAMILY_HEALTH);
            changed = true;
        }
        if (!config.isSet("settings.garry-family-range")) {
            config.set("settings.garry-family-range", DEFAULT_GARRY_FAMILY_RANGE);
            changed = true;
        }
        if (!config.isSet("settings.garry-family-speed")) {
            config.set("settings.garry-family-speed", DEFAULT_GARRY_FAMILY_SPEED);
            changed = true;
        }
        if (ensureShopItem(config, "totem_of_undying", "rotating", "TOTEM_OF_UNDYING", 1,
                "EMERALD", 2, "UTILITY", "Totem of Undying")) {
            changed = true;
        }
        changed |= ensureShopEntry(config, "rotating", "totem_of_undying", 17);
        org.bukkit.configuration.ConfigurationSection totem =
                findItemSection(config, "totem_of_undying", "rotating");
        if (totem != null) {
            if (!totem.isSet("lore")) {
                totem.set("lore", List.of("Saves you from death.", "Also rescues from the void."));
                changed = true;
            }
            if (!totem.isSet("limit.scope")) {
                totem.set("limit.scope", "PLAYER");
                changed = true;
            }
            if (!totem.isSet("limit.amount")) {
                totem.set("limit.amount", 2);
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        try {
            config.save(configFile);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Failed to update rotating-items.yml: " + ex.getMessage());
        }
    }

    private double loadGarryFamilyDamage(File rotatingFile) {
        if (rotatingFile == null || !rotatingFile.exists()) {
            return DEFAULT_GARRY_FAMILY_DAMAGE;
        }
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(rotatingFile);
        double configured = config.getDouble("settings.garry-family-damage", DEFAULT_GARRY_FAMILY_DAMAGE);
        if (configured < 0.0) {
            return DEFAULT_GARRY_FAMILY_DAMAGE;
        }
        return configured;
    }

    private double loadGarryFamilyHealth(File rotatingFile) {
        if (rotatingFile == null || !rotatingFile.exists()) {
            return DEFAULT_GARRY_FAMILY_HEALTH;
        }
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(rotatingFile);
        double configured = config.getDouble("settings.garry-family-health", DEFAULT_GARRY_FAMILY_HEALTH);
        if (configured <= 0.0) {
            return DEFAULT_GARRY_FAMILY_HEALTH;
        }
        return configured;
    }

    private double loadGarryFamilyRange(File rotatingFile) {
        if (rotatingFile == null || !rotatingFile.exists()) {
            return DEFAULT_GARRY_FAMILY_RANGE;
        }
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(rotatingFile);
        double configured = config.getDouble("settings.garry-family-range", DEFAULT_GARRY_FAMILY_RANGE);
        if (configured <= 0.0) {
            return DEFAULT_GARRY_FAMILY_RANGE;
        }
        return configured;
    }

    private double loadGarryFamilySpeed(File rotatingFile) {
        if (rotatingFile == null || !rotatingFile.exists()) {
            return DEFAULT_GARRY_FAMILY_SPEED;
        }
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(rotatingFile);
        double configured = config.getDouble("settings.garry-family-speed", DEFAULT_GARRY_FAMILY_SPEED);
        if (configured <= 0.0) {
            return DEFAULT_GARRY_FAMILY_SPEED;
        }
        return configured;
    }
}
