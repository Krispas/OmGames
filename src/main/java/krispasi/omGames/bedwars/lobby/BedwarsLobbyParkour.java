package krispasi.omGames.bedwars.lobby;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.stats.BedwarsStatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Lobby parkour runtime and config service.
 * <p>Maps pressure-plate checkpoints from commands and tracks timed runs in BedWars lobby worlds.</p>
 */
public class BedwarsLobbyParkour {
    private static final String CONFIG_ROOT = "lobby-parkour";
    private static final String CONFIG_WORLD = "world";
    private static final String CONFIG_START = "start";
    private static final String CONFIG_END = "end";
    private static final String CONFIG_CHECKPOINTS = "checkpoints";
    private static final long PLATE_COOLDOWN_MILLIS = 800L;
    private static final long CONTROL_ITEM_PLATE_LOCK_MILLIS = 3_000L;
    private static final int CONTROL_SLOT_EXIT = 7;
    private static final int CONTROL_SLOT_CHECKPOINT = 8;
    private static final String CONTROL_EXIT = "exit";
    private static final String CONTROL_CHECKPOINT = "checkpoint";

    private final BedwarsManager bedwarsManager;
    private final NamespacedKey controlItemKey;
    private final Map<UUID, RunState> runs = new HashMap<>();
    private final Map<UUID, Map<BlockPoint, Long>> plateTriggerCooldowns = new HashMap<>();
    private final Map<UUID, Long> plateLockUntil = new HashMap<>();

    private String worldName;
    private BlockPoint startPlate;
    private BlockPoint endPlate;
    private final Map<Integer, BlockPoint> checkpointPlates = new HashMap<>();

    public BedwarsLobbyParkour(BedwarsManager bedwarsManager) {
        this.bedwarsManager = bedwarsManager;
        this.controlItemKey = new NamespacedKey(bedwarsManager.getPlugin(), "bw_lobby_parkour_control");
    }

    public void load(File configFile) {
        runs.clear();
        checkpointPlates.clear();
        worldName = null;
        startPlate = null;
        endPlate = null;
        if (configFile == null || !configFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection(CONFIG_ROOT);
        if (section == null) {
            return;
        }
        worldName = trimToNull(section.getString(CONFIG_WORLD));
        startPlate = parsePoint(section.getString(CONFIG_START));
        endPlate = parsePoint(section.getString(CONFIG_END));
        ConfigurationSection checkpoints = section.getConfigurationSection(CONFIG_CHECKPOINTS);
        if (checkpoints == null) {
            return;
        }
        for (String key : checkpoints.getKeys(false)) {
            Integer index = parseInteger(key);
            if (index == null || index <= 0) {
                continue;
            }
            BlockPoint point = parsePoint(checkpoints.getString(key));
            if (point != null) {
                checkpointPlates.put(index, point);
            }
        }
    }

    public void shutdown() {
        runs.clear();
        plateTriggerCooldowns.clear();
        plateLockUntil.clear();
    }

    public List<ParkourTopEntry> getTopEntries(int limit) {
        int cappedLimit = Math.max(1, limit);
        List<BedwarsStatsService.TopParkourEntry> entries =
                bedwarsManager.getStatsService().getTopParkourTimes(cappedLimit);
        List<ParkourTopEntry> top = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < cappedLimit; i++) {
            BedwarsStatsService.TopParkourEntry entry = entries.get(i);
            top.add(new ParkourTopEntry(entry.playerId(), entry.timeMillis(), entry.checkpointUses()));
        }
        return top;
    }

    public String setStartPlate(Player player) {
        BlockPoint point = resolvePressurePlateUnder(player);
        if (point == null) {
            return "Stand on a pressure plate first.";
        }
        worldName = player.getWorld().getName();
        startPlate = point;
        if (!saveConfig()) {
            return "Failed to save bedwars.yml.";
        }
        return "Parkour start plate saved at " + formatPoint(point) + ".";
    }

    public String setEndPlate(Player player) {
        BlockPoint point = resolvePressurePlateUnder(player);
        if (point == null) {
            return "Stand on a pressure plate first.";
        }
        worldName = player.getWorld().getName();
        endPlate = point;
        if (!saveConfig()) {
            return "Failed to save bedwars.yml.";
        }
        return "Parkour end plate saved at " + formatPoint(point) + ".";
    }

    public String setCheckpointPlate(Player player, Integer checkpointIndex) {
        BlockPoint point = resolvePressurePlateUnder(player);
        if (point == null) {
            return "Stand on a pressure plate first.";
        }
        int index = checkpointIndex != null && checkpointIndex > 0 ? checkpointIndex : nextCheckpointIndex();
        worldName = player.getWorld().getName();
        checkpointPlates.put(index, point);
        if (!saveConfig()) {
            return "Failed to save bedwars.yml.";
        }
        return "Parkour checkpoint " + index + " saved at " + formatPoint(point) + ".";
    }

    public boolean handleInteract(PlayerInteractEvent event) {
        if (event == null) {
            return false;
        }
        Player player = event.getPlayer();
        if (!isPlayerInLobbyParkourContext(player)) {
            return false;
        }
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!rightClick) {
            return false;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        String control = getControlType(item);
        if (control == null) {
            return false;
        }
        event.setCancelled(true);
        RunState run = runs.get(player.getUniqueId());
        if (run == null) {
            return true;
        }
        if (CONTROL_EXIT.equals(control)) {
            applyPlateLock(player.getUniqueId());
            abortRun(player, run, true);
            return true;
        }
        if (CONTROL_CHECKPOINT.equals(control)) {
            applyPlateLock(player.getUniqueId());
            teleportToLastCheckpoint(player, run);
            return true;
        }
        return true;
    }

    public void handleMove(Player player, Location from, Location to) {
        if (player == null || to == null) {
            return;
        }
        if (!isPlayerInLobbyParkourContext(player)) {
            return;
        }
        if (from != null
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Block atFeet = to.getBlock();
        Block below = to.clone().subtract(0, 1, 0).getBlock();
        if (isPressurePlate(atFeet)) {
            handlePlateStep(player, new BlockPoint(atFeet.getX(), atFeet.getY(), atFeet.getZ()));
        }
        if (isPressurePlate(below)) {
            handlePlateStep(player, new BlockPoint(below.getX(), below.getY(), below.getZ()));
        }
    }

    public void handlePlatePress(Player player, Block plateBlock) {
        if (player == null || plateBlock == null) {
            return;
        }
        if (!isPlayerInLobbyParkourContext(player)) {
            return;
        }
        if (!isPressurePlate(plateBlock)) {
            return;
        }
        handlePlateStep(player, new BlockPoint(plateBlock.getX(), plateBlock.getY(), plateBlock.getZ()));
    }

    private void handlePlateStep(Player player, BlockPoint steppedPoint) {
        if (player == null || steppedPoint == null) {
            return;
        }
        if (!matchesParkourWorld(player.getWorld().getName())) {
            return;
        }
        if (isPlateLocked(player.getUniqueId())) {
            return;
        }
        if (startPlate != null && steppedPoint.equals(startPlate)) {
            if (isOnPlateCooldown(player.getUniqueId(), steppedPoint)) {
                return;
            }
            markPlateTriggered(player.getUniqueId(), steppedPoint);
            startRun(player);
            return;
        }
        RunState run = runs.get(player.getUniqueId());
        if (run == null) {
            return;
        }
        Integer checkpointIndex = findCheckpointIndex(steppedPoint);
        if (checkpointIndex != null) {
            if (isOnPlateCooldown(player.getUniqueId(), steppedPoint)) {
                return;
            }
            markPlateTriggered(player.getUniqueId(), steppedPoint);
            run.lastCheckpoint = checkpointPlates.get(checkpointIndex);
            run.lastCheckpointLabel = "checkpoint " + checkpointIndex;
            player.sendActionBar(Component.text("Checkpoint " + checkpointIndex + " reached.", NamedTextColor.GREEN));
            player.sendMessage(Component.text("Checkpoint " + checkpointIndex + " reached.", NamedTextColor.GREEN));
            return;
        }
        if (endPlate != null && steppedPoint.equals(endPlate)) {
            finishRun(player, run);
        }
    }

    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        RunState run = runs.remove(player.getUniqueId());
        plateTriggerCooldowns.remove(player.getUniqueId());
        plateLockUntil.remove(player.getUniqueId());
        if (run == null) {
            return;
        }
        restoreControlSlots(player, run);
    }

    private void startRun(Player player) {
        RunState existing = runs.remove(player.getUniqueId());
        if (existing != null) {
            restoreControlSlots(player, existing);
        }
        RunState run = new RunState();
        run.startedAt = System.currentTimeMillis();
        run.lastCheckpoint = startPlate;
        run.lastCheckpointLabel = "start";
        run.exitSlotBackup = cloneItem(player.getInventory().getItem(CONTROL_SLOT_EXIT));
        run.checkpointSlotBackup = cloneItem(player.getInventory().getItem(CONTROL_SLOT_CHECKPOINT));
        player.getInventory().setItem(CONTROL_SLOT_EXIT, createExitControlItem());
        player.getInventory().setItem(CONTROL_SLOT_CHECKPOINT, createCheckpointControlItem());
        runs.put(player.getUniqueId(), run);
        player.sendMessage(Component.text("Parkour started! Reach the end plate.", NamedTextColor.AQUA));
        player.sendMessage(Component.text("Gold ingot: end run and return to start. Iron ingot: teleport to last checkpoint.",
                NamedTextColor.GRAY));
    }

    private void finishRun(Player player, RunState run) {
        runs.remove(player.getUniqueId());
        plateTriggerCooldowns.remove(player.getUniqueId());
        plateLockUntil.remove(player.getUniqueId());
        restoreControlSlots(player, run);
        long elapsed = Math.max(0L, System.currentTimeMillis() - run.startedAt);
        bedwarsManager.getStatsService().recordParkourFinish(player.getUniqueId(), elapsed, run.checkpointUses);
        player.sendMessage(Component.text("Parkour finished in " + formatElapsed(elapsed) + ".", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Checkpoints used: " + run.checkpointUses + ".", NamedTextColor.YELLOW));
    }

    private void abortRun(Player player, RunState run, boolean teleportToStart) {
        runs.remove(player.getUniqueId());
        plateTriggerCooldowns.remove(player.getUniqueId());
        plateLockUntil.remove(player.getUniqueId());
        restoreControlSlots(player, run);
        if (teleportToStart && startPlate != null) {
            Location start = toTeleportLocation(player, startPlate);
            if (start != null) {
                player.teleport(start);
            }
        }
        player.sendMessage(Component.text("Parkour run ended without time.", NamedTextColor.YELLOW));
    }

    private void teleportToLastCheckpoint(Player player, RunState run) {
        if (run.lastCheckpoint == null) {
            return;
        }
        Location target = toTeleportLocation(player, run.lastCheckpoint);
        if (target == null) {
            return;
        }
        run.checkpointUses++;
        player.teleport(target);
        player.sendMessage(Component.text(
                "Teleported to " + run.lastCheckpointLabel + ". Checkpoints used: " + run.checkpointUses + ".",
                NamedTextColor.YELLOW));
    }

    private Location toTeleportLocation(Player player, BlockPoint point) {
        if (player == null || point == null) {
            return null;
        }
        Location base = point.toLocation(player.getWorld());
        base.setY(base.getY() + 1.0);
        return base;
    }

    private ItemStack createExitControlItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Parkour Exit", NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Right-click to end run", NamedTextColor.GRAY));
            lore.add(Component.text("and return to start.", NamedTextColor.GRAY));
            meta.lore(lore);
            tagControl(meta, CONTROL_EXIT);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCheckpointControlItem() {
        ItemStack item = new ItemStack(Material.IRON_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Last Checkpoint", NamedTextColor.WHITE));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Right-click to teleport", NamedTextColor.GRAY));
            lore.add(Component.text("to your last checkpoint.", NamedTextColor.GRAY));
            meta.lore(lore);
            tagControl(meta, CONTROL_CHECKPOINT);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void tagControl(ItemMeta meta, String type) {
        if (meta == null || type == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(controlItemKey, PersistentDataType.STRING, type);
    }

    private String getControlType(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.get(controlItemKey, PersistentDataType.STRING);
    }

    private void restoreControlSlots(Player player, RunState run) {
        if (player == null || run == null) {
            return;
        }
        removeControlItems(player);
        player.getInventory().setItem(CONTROL_SLOT_EXIT, cloneItem(run.exitSlotBackup));
        player.getInventory().setItem(CONTROL_SLOT_CHECKPOINT, cloneItem(run.checkpointSlotBackup));
    }

    private void removeControlItems(Player player) {
        if (player == null) {
            return;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) {
                continue;
            }
            String control = getControlType(item);
            if (control == null) {
                continue;
            }
            contents[i] = null;
            changed = true;
        }
        if (changed) {
            player.getInventory().setStorageContents(contents);
        }
    }

    private Integer findCheckpointIndex(BlockPoint point) {
        if (point == null || checkpointPlates.isEmpty()) {
            return null;
        }
        for (Map.Entry<Integer, BlockPoint> entry : checkpointPlates.entrySet()) {
            if (point.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private int nextCheckpointIndex() {
        return checkpointPlates.keySet().stream()
                .max(Comparator.naturalOrder())
                .map(value -> value + 1)
                .orElse(1);
    }

    private boolean saveConfig() {
        File configFile = bedwarsManager.getBedwarsConfigFile("bedwars.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection(CONFIG_ROOT);
        if (section == null) {
            section = config.createSection(CONFIG_ROOT);
        }
        section.set(CONFIG_WORLD, worldName);
        section.set(CONFIG_START, formatPoint(startPlate));
        section.set(CONFIG_END, formatPoint(endPlate));
        ConfigurationSection checkpoints = section.getConfigurationSection(CONFIG_CHECKPOINTS);
        if (checkpoints == null) {
            checkpoints = section.createSection(CONFIG_CHECKPOINTS);
        } else {
            for (String key : checkpoints.getKeys(false)) {
                checkpoints.set(key, null);
            }
        }
        List<Map.Entry<Integer, BlockPoint>> ordered = new ArrayList<>(checkpointPlates.entrySet());
        ordered.sort(Map.Entry.comparingByKey());
        for (Map.Entry<Integer, BlockPoint> entry : ordered) {
            checkpoints.set(String.valueOf(entry.getKey()), formatPoint(entry.getValue()));
        }
        try {
            config.save(configFile);
            return true;
        } catch (IOException ex) {
            bedwarsManager.getPlugin().getLogger().warning("Failed to save bedwars.yml: " + ex.getMessage());
            return false;
        }
    }

    private BlockPoint resolvePressurePlateUnder(Player player) {
        if (player == null) {
            return null;
        }
        Block atFeet = player.getLocation().getBlock();
        if (isPressurePlate(atFeet)) {
            return new BlockPoint(atFeet.getX(), atFeet.getY(), atFeet.getZ());
        }
        Block below = player.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (!isPressurePlate(below)) {
            return null;
        }
        return new BlockPoint(below.getX(), below.getY(), below.getZ());
    }

    private boolean isPressurePlate(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getType().name();
        return name.endsWith("_PRESSURE_PLATE");
    }

    private boolean isPlayerInLobbyParkourContext(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (!bedwarsManager.isBedwarsWorld(player.getWorld().getName())) {
            return false;
        }
        GameSession session = bedwarsManager.getActiveSession();
        if (session == null) {
            return true;
        }
        if (!session.isInArenaWorld(player.getWorld())) {
            return true;
        }
        return !session.isRunning() || !session.isParticipant(player.getUniqueId());
    }

    private boolean matchesParkourWorld(String candidateWorld) {
        if (candidateWorld == null) {
            return false;
        }
        if (worldName == null || worldName.isBlank()) {
            return true;
        }
        return worldName.equalsIgnoreCase(candidateWorld);
    }

    private BlockPoint parsePoint(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BlockPoint.parse(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String formatPoint(BlockPoint point) {
        if (point == null) {
            return null;
        }
        return point.x() + " " + point.y() + " " + point.z();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer parseInteger(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ItemStack cloneItem(ItemStack item) {
        return item != null ? item.clone() : null;
    }

    private boolean isOnPlateCooldown(UUID playerId, BlockPoint point) {
        if (playerId == null || point == null) {
            return false;
        }
        Map<BlockPoint, Long> cooldowns = plateTriggerCooldowns.get(playerId);
        if (cooldowns == null) {
            return false;
        }
        Long lastTrigger = cooldowns.get(point);
        if (lastTrigger == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        return now - lastTrigger < PLATE_COOLDOWN_MILLIS;
    }

    private void markPlateTriggered(UUID playerId, BlockPoint point) {
        if (playerId == null || point == null) {
            return;
        }
        plateTriggerCooldowns
                .computeIfAbsent(playerId, ignored -> new HashMap<>())
                .put(point, System.currentTimeMillis());
    }

    private void applyPlateLock(UUID playerId) {
        if (playerId == null) {
            return;
        }
        plateLockUntil.put(playerId, System.currentTimeMillis() + CONTROL_ITEM_PLATE_LOCK_MILLIS);
    }

    private boolean isPlateLocked(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long until = plateLockUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            plateLockUntil.remove(playerId);
            return false;
        }
        return true;
    }

    private String formatElapsed(long elapsedMillis) {
        Duration duration = Duration.ofMillis(Math.max(0L, elapsedMillis));
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).toSeconds();
        long millis = duration.minusMinutes(minutes).minusSeconds(seconds).toMillis();
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%d:%02d.%03d", minutes, seconds, millis);
        }
        return String.format(Locale.ROOT, "%d.%03ds", seconds, millis);
    }

    private static final class RunState {
        private long startedAt;
        private BlockPoint lastCheckpoint;
        private String lastCheckpointLabel;
        private int checkpointUses;
        private ItemStack exitSlotBackup;
        private ItemStack checkpointSlotBackup;
    }

    public record ParkourTopEntry(UUID playerId, long timeMillis, int checkpointUses) {
    }
}
