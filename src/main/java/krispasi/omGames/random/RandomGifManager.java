package krispasi.omGames.random;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class RandomGifManager {
    private static final String CONFIG_FILE_NAME = "gifs.yml";
    private static final String GIF_FOLDER_NAME = "gifs";
    private static final double ACTIVE_RANGE_BLOCKS = 20.0d;
    private static final double ACTIVE_RANGE_SQUARED = ACTIVE_RANGE_BLOCKS * ACTIVE_RANGE_BLOCKS;

    private final JavaPlugin plugin;
    private final Map<Integer, RandomGifBinding> bindingsByBaseMapId = new LinkedHashMap<>();
    private final Map<Integer, RandomGifBinding> bindingByMapId = new HashMap<>();
    private final Map<UUID, RandomGifPromptSession> prompts = new HashMap<>();
    private final Map<Integer, Set<UUID>> lastViewerIdsByMapId = new HashMap<>();
    private BukkitTask animationTask;
    private long tickCounter;

    public RandomGifManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        stopAnimationTask();
        bindingsByBaseMapId.clear();
        bindingByMapId.clear();
        lastViewerIdsByMapId.clear();
        ensureFolders();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(getConfigFile());
        for (Map<?, ?> row : config.getMapList("gifs")) {
            Object rawMapId = row.containsKey("base-map-id") ? row.get("base-map-id") : row.get("map-id");
            Integer mapId = asInt(rawMapId);
            int width = clampSize(asInt(row.get("width")), 1);
            int height = clampSize(asInt(row.get("height")), 1);
            String fileName = normalizeGifFileName(asString(row.get("file")));
            if (mapId == null || fileName == null) {
                continue;
            }
            File file = getGifFile(fileName);
            if (!file.isFile()) {
                plugin.getLogger().warning("Random GIF file is missing: " + fileName);
                continue;
            }
            try {
                addBinding(new RandomGifBinding(fileName, mapId, width, height, RandomGifImage.load(file, width, height)));
            } catch (IOException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load Random GIF " + fileName + ".", ex);
            }
        }

        installRenderers();
        startAnimationTask();
        plugin.getLogger().info("Loaded " + bindingsByBaseMapId.size() + " Random GIF map link"
                + (bindingsByBaseMapId.size() == 1 ? "" : "s") + ".");
    }

    public void shutdown() {
        stopAnimationTask();
        prompts.clear();
        lastViewerIdsByMapId.clear();
    }

    public Result reload() {
        load();
        return Result.ok("Reloaded " + bindingsByBaseMapId.size() + " GIF map link"
                + (bindingsByBaseMapId.size() == 1 ? "" : "s") + ".");
    }

    public void openGifMenu(Player player) {
        if (player == null) {
            return;
        }
        new RandomGifMainMenu(this).open(player);
    }

    public List<File> getGifFilesForMenu() {
        ensureFolders();
        File[] files = getGifFolder().listFiles(file -> file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".gif"));
        if (files == null || files.length == 0) {
            return List.of();
        }
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparing(file -> file.getName().toLowerCase(Locale.ROOT)));
        return sorted;
    }

    public List<RandomGifBinding> getBindingsForMenu() {
        List<RandomGifBinding> bindings = new ArrayList<>(bindingsByBaseMapId.values());
        bindings.sort(Comparator.comparingInt(RandomGifBinding::baseMapId));
        return bindings;
    }

    public int getAvailableGifCount() {
        return getGifFilesForMenu().size();
    }

    public String getGifFolderDisplayPath() {
        return "plugins/OmGames/Random/" + GIF_FOLDER_NAME;
    }

    public void beginMapIdPrompt(Player player, String fileName, RandomGifSize size) {
        String normalized = normalizeGifFileName(fileName);
        if (player == null || normalized == null || size == null) {
            return;
        }
        prompts.put(player.getUniqueId(), new RandomGifPromptSession(normalized, size));
        sendPrompt(player, "Zadej prvni map id pro " + normalized + " (" + size.label()
                + ", " + size.mapCount() + " map). Napis cancel pro zruseni.");
    }

    public boolean hasPrompt(Player player) {
        return player != null && prompts.containsKey(player.getUniqueId());
    }

    public void cancelPrompt(Player player) {
        if (player != null) {
            prompts.remove(player.getUniqueId());
        }
    }

    public void handlePromptInput(Player player, String message) {
        if (player == null) {
            return;
        }
        RandomGifPromptSession session = prompts.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        String input = message == null ? "" : message.trim();
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("zrusit")) {
            prompts.remove(player.getUniqueId());
            player.sendMessage(Component.text("GIF input cancelled.", NamedTextColor.YELLOW));
            openGifMenu(player);
            return;
        }

        Integer mapId = asInt(input);
        if (mapId == null || mapId < 0) {
            sendPrompt(player, "Map id musi byt cele cislo 0 nebo vetsi. Zadej map id znovu.");
            return;
        }

        Result result = createBinding(session.fileName(), mapId, session.size().width(), session.size().height());
        player.sendMessage(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        if (result.success()) {
            prompts.remove(player.getUniqueId());
            openGifMenu(player);
        } else {
            sendPrompt(player, "Zadej map id znovu, nebo napis cancel.");
        }
    }

    public Result createBinding(String fileName, int baseMapId, int width, int height) {
        String normalized = normalizeGifFileName(fileName);
        if (normalized == null) {
            return Result.fail("Invalid GIF file name.");
        }
        width = clampSize(width, 1);
        height = clampSize(height, 1);
        File file = getGifFile(normalized);
        if (!file.isFile()) {
            return Result.fail("GIF file does not exist: " + normalized + ".");
        }
        List<Integer> mapIds = buildMapIds(baseMapId, width, height);
        for (int mapId : mapIds) {
            MapView view = Bukkit.getMap(mapId);
            if (view == null) {
                return Result.fail("Map id " + mapId + " does not exist. Create maps " + mapIds + " first.");
            }
            RandomGifBinding existing = bindingByMapId.get(mapId);
            if (existing != null && existing.baseMapId() != baseMapId) {
                return Result.fail("Map id " + mapId + " is already used by GIF map " + existing.baseMapId() + ".");
            }
        }

        RandomGifImage image;
        try {
            image = RandomGifImage.load(file, width, height);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load Random GIF " + normalized + ".", ex);
            return Result.fail("Failed to load GIF " + normalized + ".");
        }

        removeBindingInternal(baseMapId);
        addBinding(new RandomGifBinding(normalized, baseMapId, width, height, image));
        save();
        for (int mapId : mapIds) {
            installRenderer(mapId);
        }
        return Result.ok("Linked " + normalized + " to " + width + "x" + height
                + " GIF maps starting at id " + baseMapId + ".");
    }

    public Result removeBinding(int baseMapId) {
        RandomGifBinding removed = removeBindingInternal(baseMapId);
        if (removed == null) {
            return Result.fail("GIF map link not found.");
        }
        save();
        return Result.ok("Removed GIF map link starting at map id " + baseMapId + ".");
    }

    public Result giveMap(Player player, int baseMapId) {
        if (player == null) {
            return Result.fail("Only players can receive maps.");
        }
        RandomGifBinding binding = bindingsByBaseMapId.get(baseMapId);
        if (binding == null) {
            return Result.fail("GIF map link not found.");
        }
        int index = 0;
        for (int mapId : binding.mapIds()) {
            giveSingleMap(player, binding, mapId, index++);
        }
        return Result.ok("Gave " + binding.sizeLabel() + " GIF maps starting at id " + baseMapId + ".");
    }

    BufferedImage getCurrentTileFrame(int mapId) {
        RandomGifBinding binding = bindingByMapId.get(mapId);
        return binding == null ? null : binding.tileFrame(mapId);
    }

    private void giveSingleMap(Player player, RandomGifBinding binding, int mapId, int index) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        ItemMeta rawMeta = item.getItemMeta();
        if (rawMeta instanceof MapMeta meta) {
            meta.setMapId(mapId);
            meta.displayName(Component.text("GIF Map " + mapId + " (" + binding.sizeLabel() + ")",
                    NamedTextColor.LIGHT_PURPLE));
            meta.lore(List.of(
                    Component.text("GIF: " + binding.fileName(), NamedTextColor.GRAY),
                    Component.text("Tile: " + (index + 1) + "/" + binding.mapIds().size(), NamedTextColor.GRAY),
                    Component.text("Place maps left-to-right, top-to-bottom.", NamedTextColor.DARK_GRAY)
            ));
            item.setItemMeta(meta);
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            if (leftover != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void startAnimationTask() {
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAnimations, 1L, 1L);
    }

    private void stopAnimationTask() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    private void tickAnimations() {
        tickCounter++;
        if (bindingsByBaseMapId.isEmpty()) {
            return;
        }

        Map<Integer, Set<UUID>> nearbyViewerIds = scanNearbyViewerIds();
        for (RandomGifBinding binding : bindingsByBaseMapId.values()) {
            Set<UUID> viewers = nearbyViewerIds.getOrDefault(binding.baseMapId(), Set.of());
            Set<UUID> previousViewers = lastViewerIdsByMapId.getOrDefault(binding.baseMapId(), Set.of());
            if (viewers.isEmpty()) {
                binding.deactivate();
                lastViewerIdsByMapId.remove(binding.baseMapId());
                continue;
            }

            boolean shouldSend = binding.activate(tickCounter);
            if (binding.advanceIfDue(tickCounter)) {
                shouldSend = true;
            }
            if (hasNewViewer(viewers, previousViewers)) {
                shouldSend = true;
            }
            if (shouldSend) {
                sendMaps(binding, viewers);
            }
            lastViewerIdsByMapId.put(binding.baseMapId(), Set.copyOf(viewers));
        }
    }

    private Map<Integer, Set<UUID>> scanNearbyViewerIds() {
        Map<Integer, Set<UUID>> result = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            List<Player> players = world.getPlayers();
            if (players.isEmpty()) {
                continue;
            }
            for (ItemFrame frame : world.getEntitiesByClass(ItemFrame.class)) {
                Integer mapId = readMapId(frame.getItem());
                RandomGifBinding binding = mapId == null ? null : bindingByMapId.get(mapId);
                if (binding == null) {
                    continue;
                }
                for (Player player : players) {
                    if (player.getLocation().distanceSquared(frame.getLocation()) <= ACTIVE_RANGE_SQUARED) {
                        result.computeIfAbsent(binding.baseMapId(), ignored -> new HashSet<>()).add(player.getUniqueId());
                    }
                }
            }
        }
        return result;
    }

    private Integer readMapId(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP) {
            return null;
        }
        ItemMeta rawMeta = item.getItemMeta();
        if (!(rawMeta instanceof MapMeta meta) || !meta.hasMapId()) {
            return null;
        }
        return meta.getMapId();
    }

    private boolean hasNewViewer(Set<UUID> viewers, Set<UUID> previousViewers) {
        for (UUID viewer : viewers) {
            if (!previousViewers.contains(viewer)) {
                return true;
            }
        }
        return false;
    }

    private void sendMaps(RandomGifBinding binding, Set<UUID> viewerIds) {
        List<MapView> views = new ArrayList<>();
        for (int mapId : binding.mapIds()) {
            MapView view = Bukkit.getMap(mapId);
            if (view != null) {
                views.add(view);
            }
        }
        for (UUID viewerId : viewerIds) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player != null && player.isOnline()) {
                for (MapView view : views) {
                    player.sendMap(view);
                }
            }
        }
    }

    private void installRenderers() {
        for (RandomGifBinding binding : bindingsByBaseMapId.values()) {
            for (int mapId : binding.mapIds()) {
                installRenderer(mapId);
            }
        }
    }

    private void installRenderer(int mapId) {
        MapView view = Bukkit.getMap(mapId);
        if (view == null) {
            plugin.getLogger().warning("Random GIF map id " + mapId
                    + " does not exist. Create the map in game before using the GIF display.");
            return;
        }
        for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
            view.removeRenderer(renderer);
        }
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(false);
        view.addRenderer(new RandomGifMapRenderer(this, mapId));
    }

    private void removeGifRenderers(int mapId) {
        MapView view = Bukkit.getMap(mapId);
        if (view == null) {
            return;
        }
        for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
            if (renderer instanceof RandomGifMapRenderer) {
                view.removeRenderer(renderer);
            }
        }
    }

    private void ensureFolders() {
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Failed to create Random data folder.");
        }
        File gifFolder = getGifFolder();
        if (!gifFolder.exists() && !gifFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create Random GIF folder.");
        }
    }

    private void save() {
        ensureFolders();
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RandomGifBinding binding : getBindingsForMenu()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("base-map-id", binding.baseMapId());
            row.put("width", binding.width());
            row.put("height", binding.height());
            row.put("map-ids", binding.mapIds());
            row.put("file", binding.fileName());
            rows.add(row);
        }
        config.set("gifs", rows);
        try {
            config.save(getConfigFile());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save Random GIF config.", ex);
        }
    }

    private File getDataFolder() {
        return new File(plugin.getDataFolder(), "Random");
    }

    private File getGifFolder() {
        return new File(getDataFolder(), GIF_FOLDER_NAME);
    }

    private File getConfigFile() {
        return new File(getDataFolder(), CONFIG_FILE_NAME);
    }

    private File getGifFile(String fileName) {
        return new File(getGifFolder(), fileName);
    }

    private String normalizeGifFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String normalized = new File(fileName.trim()).getName();
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".gif")) {
            return null;
        }
        return normalized;
    }

    private String asString(Object value) {
        return value instanceof String string && !string.isBlank() ? string.trim() : null;
    }

    private Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void addBinding(RandomGifBinding binding) {
        bindingsByBaseMapId.put(binding.baseMapId(), binding);
        for (int mapId : binding.mapIds()) {
            bindingByMapId.put(mapId, binding);
        }
    }

    private RandomGifBinding removeBindingInternal(int baseMapId) {
        RandomGifBinding removed = bindingsByBaseMapId.remove(baseMapId);
        if (removed == null) {
            return null;
        }
        lastViewerIdsByMapId.remove(removed.baseMapId());
        for (int mapId : removed.mapIds()) {
            bindingByMapId.remove(mapId);
            removeGifRenderers(mapId);
        }
        return removed;
    }

    private List<Integer> buildMapIds(int baseMapId, int width, int height) {
        List<Integer> ids = new ArrayList<>();
        for (int index = 0; index < width * height; index++) {
            ids.add(baseMapId + index);
        }
        return ids;
    }

    private int clampSize(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(1, Math.min(4, value));
    }

    private void sendPrompt(Player player, String message) {
        player.sendMessage(Component.text("[GIF] " + message, NamedTextColor.YELLOW));
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
