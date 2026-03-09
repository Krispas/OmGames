package krispasi.omGames.egghunt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class EggHuntSession {
    private static final int COUNTDOWN_SECONDS = 10;
    private static final long TICKS_PER_SECOND = 20L;
    private static final long COLLISION_INTERVAL_TICKS = 2L;
    private static final double PICKUP_RADIUS_SQUARED = 0.25;
    private static final String EGG_MODEL = "om:eggpoint";

    public enum State {
        COUNTDOWN,
        RUNNING,
        FINISHED,
        ABORTED
    }

    private final EggHuntManager manager;
    private final JavaPlugin plugin;
    private final Location startLocation;
    private final LinkedHashMap<UUID, String> participantNames = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Integer> scores = new LinkedHashMap<>();
    private final List<EggHuntPoint> eggPoints;
    private final LinkedHashMap<UUID, EggHuntPoint> activeEggs = new LinkedHashMap<>();
    private final ItemStack eggDisplayItem;
    private final int totalTimerSeconds;
    private BukkitTask countdownTask;
    private BukkitTask timerTask;
    private BukkitTask eggTask;
    private int countdownRemaining = COUNTDOWN_SECONDS;
    private int secondsRemaining;
    private float rotationDegrees = 0.0f;
    private State state = State.COUNTDOWN;

    public EggHuntSession(
            EggHuntManager manager,
            Location startLocation,
            Collection<Player> participants,
            List<EggHuntPoint> eggPoints,
            int timerSeconds
    ) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
        this.startLocation = startLocation.clone();
        this.eggPoints = new ArrayList<>(eggPoints);
        this.totalTimerSeconds = timerSeconds;
        this.secondsRemaining = timerSeconds;
        this.eggDisplayItem = createEggDisplayItem();
        for (Player participant : participants) {
            participantNames.put(participant.getUniqueId(), participant.getName());
            scores.put(participant.getUniqueId(), 0);
        }
    }

    public void start() {
        moveParticipantsToStart();
        manager.syncSidebar(this);
        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.COUNTDOWN) {
                    cancel();
                    return;
                }
                countdownRemaining--;
                if (countdownRemaining <= 0) {
                    cancel();
                    beginRunning();
                    return;
                }
                manager.syncSidebar(EggHuntSession.this);
            }
        }.runTaskTimer(plugin, TICKS_PER_SECOND, TICKS_PER_SECOND);
    }

    public void abort() {
        if (state == State.ABORTED || state == State.FINISHED) {
            return;
        }
        state = State.ABORTED;
        cancelTasks();
        clearEggDisplays();
    }

    public boolean isMovementLocked(UUID playerId) {
        return state == State.COUNTDOWN && participantNames.containsKey(playerId);
    }

    public Location getLockedLocation() {
        return startLocation.clone();
    }

    public Map<UUID, String> getParticipantNames() {
        return Map.copyOf(participantNames);
    }

    public Map<UUID, Integer> getScores() {
        return Map.copyOf(scores);
    }

    public State getState() {
        return state;
    }

    public int getCountdownRemaining() {
        return countdownRemaining;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public int getRemainingEggCount() {
        return activeEggs.isEmpty() && state != State.RUNNING ? eggPoints.size() : activeEggs.size();
    }

    public int getConfiguredTimerSeconds() {
        return totalTimerSeconds;
    }

    public void handlePlayerJoin(Player player) {
        if (player == null || !isMovementLocked(player.getUniqueId())) {
            return;
        }
        teleportToStart(player);
    }

    private void beginRunning() {
        if (state != State.COUNTDOWN) {
            return;
        }
        state = State.RUNNING;
        spawnEggDisplays();
        manager.syncSidebar(this);
        if (activeEggs.isEmpty()) {
            finish();
            return;
        }

        eggTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.RUNNING) {
                    cancel();
                    return;
                }
                tickEggDisplays();
                checkForCollections();
            }
        }.runTaskTimer(plugin, 0L, COLLISION_INTERVAL_TICKS);

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != State.RUNNING) {
                    cancel();
                    return;
                }
                secondsRemaining--;
                manager.syncSidebar(EggHuntSession.this);
                if (secondsRemaining <= 0) {
                    finish();
                }
            }
        }.runTaskTimer(plugin, TICKS_PER_SECOND, TICKS_PER_SECOND);

        for (UUID playerId : participantNames.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.sendMessage(Component.text("Egg Hunt started. Collect as many eggs as you can.", NamedTextColor.GOLD));
        }
    }

    private void moveParticipantsToStart() {
        for (UUID playerId : participantNames.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            teleportToStart(player);
            player.sendMessage(Component.text("Egg Hunt starts in 10 seconds.", NamedTextColor.YELLOW));
        }
    }

    private void teleportToStart(Player player) {
        Location target = startLocation.clone();
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        player.teleport(target);
        player.setVelocity(new Vector());
    }

    private void spawnEggDisplays() {
        activeEggs.clear();
        for (EggHuntPoint point : eggPoints) {
            if (!manager.isSpawnablePoint(point, startLocation)) {
                continue;
            }
            Location location = point.toLocation();
            if (location == null || location.getWorld() == null) {
                continue;
            }
            Location displayLocation = location.clone().add(0.0, 0.75, 0.0);
            ItemDisplay display = displayLocation.getWorld().spawn(displayLocation, ItemDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setInvulnerable(true);
                entity.setGravity(false);
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setShadowRadius(0.0f);
                entity.setShadowStrength(0.0f);
                entity.setDisplayWidth(0.9f);
                entity.setDisplayHeight(0.9f);
                entity.setItemStack(eggDisplayItem.clone());
                entity.setTransformation(new Transformation(
                        new Vector3f(),
                        new Quaternionf(),
                        new Vector3f(1.0f, 1.0f, 1.0f),
                        new Quaternionf()));
            });
            activeEggs.put(display.getUniqueId(), point);
        }
    }

    private void tickEggDisplays() {
        rotationDegrees = (rotationDegrees + 12.0f) % 360.0f;
        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(rotationDegrees));
        Transformation transformation = new Transformation(
                new Vector3f(),
                rotation,
                new Vector3f(1.0f, 1.0f, 1.0f),
                new Quaternionf());
        for (UUID displayId : new ArrayList<>(activeEggs.keySet())) {
            if (!(Bukkit.getEntity(displayId) instanceof ItemDisplay display) || !display.isValid()) {
                activeEggs.remove(displayId);
                continue;
            }
            display.setTransformation(transformation);
        }
        if (activeEggs.isEmpty()) {
            finish();
        }
    }

    private void checkForCollections() {
        for (UUID playerId : participantNames.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            UUID collectedEggId = findCollectedEgg(player);
            if (collectedEggId == null) {
                continue;
            }
            collectEgg(player, collectedEggId);
            if (activeEggs.isEmpty()) {
                finish();
                return;
            }
        }
    }

    private UUID findCollectedEgg(Player player) {
        Location playerCenter = player.getLocation().clone().add(0.0, 1.0, 0.0);
        for (UUID displayId : new ArrayList<>(activeEggs.keySet())) {
            if (!(Bukkit.getEntity(displayId) instanceof ItemDisplay display) || !display.isValid()) {
                activeEggs.remove(displayId);
                continue;
            }
            if (!display.getWorld().equals(player.getWorld())) {
                continue;
            }
            if (display.getLocation().distanceSquared(playerCenter) <= PICKUP_RADIUS_SQUARED) {
                return displayId;
            }
        }
        return null;
    }

    private void collectEgg(Player player, UUID displayId) {
        activeEggs.remove(displayId);
        if (Bukkit.getEntity(displayId) instanceof ItemDisplay display) {
            display.remove();
        }
        scores.computeIfPresent(player.getUniqueId(), (ignored, score) -> score + 1);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        manager.syncSidebar(this);
    }

    private void finish() {
        if (state == State.FINISHED || state == State.ABORTED) {
            return;
        }
        state = State.FINISHED;
        cancelTasks();
        clearEggDisplays();
        manager.completeSession(this);
        for (UUID playerId : participantNames.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            player.sendMessage(Component.text("Egg Hunt finished.", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
    }

    private void clearEggDisplays() {
        for (UUID displayId : new ArrayList<>(activeEggs.keySet())) {
            if (Bukkit.getEntity(displayId) instanceof ItemDisplay display) {
                display.remove();
            }
        }
        activeEggs.clear();
    }

    private void cancelTasks() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (eggTask != null) {
            eggTask.cancel();
            eggTask = null;
        }
    }

    private ItemStack createEggDisplayItem() {
        ItemStack stack = new ItemStack(Material.EGG);
        ItemMeta meta = stack.getItemMeta();
        NamespacedKey itemModel = NamespacedKey.fromString(EGG_MODEL);
        if (itemModel != null) {
            meta.setItemModel(itemModel);
        }
        meta.displayName(Component.text("Egg", NamedTextColor.GOLD));
        meta.setHideTooltip(true);
        stack.setItemMeta(meta);
        return stack;
    }
}
