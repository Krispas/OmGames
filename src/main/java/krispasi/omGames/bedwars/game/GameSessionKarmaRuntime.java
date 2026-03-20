package krispasi.omGames.bedwars.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import krispasi.omGames.bedwars.karma.BedwarsKarmaEventConfig;
import krispasi.omGames.bedwars.karma.BedwarsKarmaService;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.upgrade.TeamUpgradeType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Match-scoped BedWars karma runtime.
 */
class GameSessionKarmaRuntime {
    static final String KARMA_ANVIL_TAG = "bw_karma_anvil";
    private static final int WOODOO_DOLL_TEMP_KARMA = 10;
    private static final int BROKEN_MIRROR_TEMP_KARMA = 1;
    private static final int KARMA_COST_PER_EVENT = 2;
    private static final int WEAKNESS_DURATION_TICKS = 30 * 20;
    private static final int SLOWNESS_DURATION_TICKS = 10 * 20;
    private static final int COMBUSTION_TICKS = 30 * 20;
    private static final Set<Material> FIRE_MATERIALS = EnumSet.of(Material.FIRE, Material.SOUL_FIRE);

    private final GameSession session;
    private final BedwarsKarmaService service;
    private final Map<UUID, Integer> temporaryKarma = new HashMap<>();
    private final Map<UUID, BukkitTask> playerCheckTasks = new HashMap<>();
    private final Set<UUID> activeKarmaAnvils = new java.util.HashSet<>();
    private JavaPlugin plugin;

    GameSessionKarmaRuntime(GameSession session, BedwarsKarmaService service) {
        this.session = session;
        this.service = service;
    }

    void start(JavaPlugin plugin) {
        this.plugin = plugin;
        cancelPlayerChecks();
        if (plugin == null) {
            return;
        }
        for (UUID playerId : new ArrayList<>(session.getAssignments().keySet())) {
            scheduleNextCheck(playerId);
        }
    }

    void stop() {
        cancelPlayerChecks();
        clearActiveAnvils();
        plugin = null;
    }

    void reset() {
        stop();
        temporaryKarma.clear();
    }

    void handleParticipantJoined(UUID playerId) {
        if (playerId == null || plugin == null || !session.isRunning()) {
            return;
        }
        applyExistingBrokenMirrorKarma(playerId);
        scheduleNextCheck(playerId);
    }

    void handleParticipantRemoved(UUID playerId) {
        if (playerId == null) {
            return;
        }
        temporaryKarma.remove(playerId);
        cancelPlayerCheck(playerId);
    }

    int getTemporaryKarma(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return Math.max(0, temporaryKarma.getOrDefault(playerId, 0));
    }

    int getPermanentKarma(UUID playerId) {
        return service != null ? service.getPermanentKarma(playerId) : 0;
    }

    int getTotalKarma(UUID playerId) {
        long total = (long) getTemporaryKarma(playerId) + getPermanentKarma(playerId);
        if (total <= 0L) {
            return 0;
        }
        if (total >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) total;
    }

    int addTemporaryKarma(UUID playerId, int amount) {
        if (playerId == null || amount == 0) {
            return getTemporaryKarma(playerId);
        }
        int current = getTemporaryKarma(playerId);
        int updated = clampToNonNegativeInt((long) current + amount);
        if (updated <= 0) {
            temporaryKarma.remove(playerId);
            return 0;
        }
        temporaryKarma.put(playerId, updated);
        return updated;
    }

    boolean handleWoodooDollHit(Player attacker, Player victim) {
        if (attacker == null || victim == null || !session.isRunning()) {
            return false;
        }
        UUID attackerId = attacker.getUniqueId();
        UUID victimId = victim.getUniqueId();
        if (!session.isParticipant(attackerId) || !session.isParticipant(victimId)) {
            return false;
        }
        TeamColor attackerTeam = session.getTeam(attackerId);
        TeamColor victimTeam = session.getTeam(victimId);
        if (attackerTeam == null || victimTeam == null || attackerTeam == victimTeam) {
            return false;
        }
        int totalTemporary = addTemporaryKarma(victimId, WOODOO_DOLL_TEMP_KARMA);
        attacker.sendMessage(Component.text("Woodoo Doll applied 10 temporary karma to "
                + victim.getName() + ".", NamedTextColor.DARK_RED));
        victim.sendMessage(Component.text(attacker.getName()
                + " hexed you with a Woodoo Doll. Temporary karma: " + totalTemporary + ".",
                NamedTextColor.RED));
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_WITCH_THROW, 1.0f, 1.15f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.3f);
        return true;
    }

    void applyBrokenMirrorLevel(TeamColor ownerTeam) {
        if (ownerTeam == null) {
            return;
        }
        for (Map.Entry<UUID, TeamColor> entry : session.getAssignments().entrySet()) {
            UUID playerId = entry.getKey();
            TeamColor playerTeam = entry.getValue();
            if (playerId == null || playerTeam == null || playerTeam == ownerTeam) {
                continue;
            }
            addTemporaryKarma(playerId, BROKEN_MIRROR_TEMP_KARMA);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text("Broken Mirror increased your temporary karma.",
                        NamedTextColor.RED));
            }
        }
    }

    int triggerEventsForEligiblePlayers() {
        int triggered = 0;
        for (UUID playerId : new ArrayList<>(session.getAssignments().keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (!isEligiblePlayer(playerId, player)) {
                continue;
            }
            if (triggerRandomEvent(player)) {
                triggered++;
            }
        }
        return triggered;
    }

    boolean isKarmaAnvil(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(KARMA_ANVIL_TAG);
    }

    void clearTrackedAnvil(UUID entityId) {
        if (entityId != null) {
            activeKarmaAnvils.remove(entityId);
        }
    }

    private void runScheduledCheck(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (!isEligiblePlayer(playerId, player)) {
            return;
        }
        BedwarsKarmaEventConfig config = session.getBedwarsManager().getKarmaEventConfig();
        if (ThreadLocalRandom.current().nextDouble() >= config.baseRollChance()) {
            return;
        }
        int totalKarma = getTotalKarma(playerId);
        if (totalKarma <= 0) {
            return;
        }
        double chance = Math.min(1.0, totalKarma * config.perKarmaChance());
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            triggerRandomEvent(player);
        }
    }

    private void scheduleNextCheck(UUID playerId) {
        if (playerId == null || plugin == null || !session.isRunning() || !session.isParticipant(playerId)) {
            return;
        }
        cancelPlayerCheck(playerId);
        BedwarsKarmaEventConfig config = session.getBedwarsManager().getKarmaEventConfig();
        long minDelayTicks = config.minCheckDelayTicks();
        long maxDelayTicks = config.maxCheckDelayTicks();
        long delayTicks = maxDelayTicks <= minDelayTicks
                ? minDelayTicks
                : ThreadLocalRandom.current().nextLong(minDelayTicks, maxDelayTicks + 1L);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                session.safeRun("karmaCheck", () -> {
                    playerCheckTasks.remove(playerId);
                    runScheduledCheck(playerId);
                    scheduleNextCheck(playerId);
                }), delayTicks);
        playerCheckTasks.put(playerId, task);
    }

    private boolean isEligiblePlayer(UUID playerId, Player player) {
        if (playerId == null || player == null || !player.isOnline()) {
            return false;
        }
        if (!session.isRunning() || !session.isParticipant(playerId)) {
            return false;
        }
        if (!session.isInArenaWorld(player.getWorld()) || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (session.hasRespawnProtection(playerId)) {
            return false;
        }
        return getTotalKarma(playerId) > 0;
    }

    private boolean triggerRandomEvent(Player player) {
        if (player == null) {
            return false;
        }
        List<KarmaEventType> events = new ArrayList<>(List.of(KarmaEventType.values()));
        Collections.shuffle(events);
        for (KarmaEventType type : events) {
            if (!applyEvent(type, player)) {
                continue;
            }
            spendKarma(player.getUniqueId(), KARMA_COST_PER_EVENT);
            player.sendMessage(Component.text("Karma Event: " + type.displayName, NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.9f, 1.1f);
            return true;
        }
        return false;
    }

    private boolean applyEvent(KarmaEventType type, Player player) {
        if (type == null || player == null || !player.isOnline()) {
            return false;
        }
        return switch (type) {
            case EXPLOSION -> triggerExplosion(player);
            case LIGHTNING_STRIKE -> triggerLightningStrike(player);
            case WEAKNESS -> applyWeakness(player);
            case ANVIL_FALL -> triggerAnvilFall(player);
            case SLOWNESS -> applySlowness(player);
            case CONFUSION -> applyConfusion(player);
            case SPONTANEOUS_COMBUSTION -> applyCombustion(player);
        };
    }

    private boolean triggerExplosion(Player player) {
        Location location = player.getLocation().clone().add(0.0, 0.1, 0.0);
        player.getWorld().spawn(location, TNTPrimed.class, tnt -> tnt.setFuseTicks(80));
        player.getWorld().playSound(location, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
        return true;
    }

    private boolean triggerLightningStrike(Player player) {
        Location location = player.getLocation();
        LightningStrike strike = player.getWorld().strikeLightning(location);
        if (strike == null) {
            return false;
        }
        if (plugin != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                clearNearbyFire(location);
                player.setFireTicks(0);
            });
        } else {
            clearNearbyFire(location);
            player.setFireTicks(0);
        }
        return true;
    }

    private boolean applyWeakness(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, WEAKNESS_DURATION_TICKS, 0, true, true, true), true);
        return true;
    }

    private boolean triggerAnvilFall(Player player) {
        Location base = player.getLocation();
        for (int y = 1; y <= 10; y++) {
            Block block = base.getWorld().getBlockAt(base.getBlockX(), base.getBlockY() + y, base.getBlockZ());
            if (!block.getType().isAir()) {
                return false;
            }
        }
        Location spawn = new Location(base.getWorld(), base.getX(), base.getY() + 10.0, base.getZ(), base.getYaw(), base.getPitch());
        BlockData anvilData = Material.ANVIL.createBlockData();
        FallingBlock falling = base.getWorld().spawnFallingBlock(spawn, anvilData);
        falling.addScoreboardTag(KARMA_ANVIL_TAG);
        falling.setDropItem(false);
        falling.setHurtEntities(true);
        try {
            falling.setCancelDrop(true);
        } catch (NoSuchMethodError ignored) {
        }
        activeKarmaAnvils.add(falling.getUniqueId());
        if (plugin != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                activeKarmaAnvils.remove(falling.getUniqueId());
                if (falling.isValid()) {
                    falling.remove();
                }
            }, 80L);
        }
        return true;
    }

    private boolean applySlowness(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOWNESS_DURATION_TICKS, 0, true, true, true), true);
        return true;
    }

    private boolean applyConfusion(Player player) {
        Location location = player.getLocation().clone();
        location.setYaw((float) ThreadLocalRandom.current().nextDouble(-180.0, 180.0));
        location.setPitch((float) ThreadLocalRandom.current().nextDouble(-90.0, 90.0));
        player.teleport(location);
        return true;
    }

    private boolean applyCombustion(Player player) {
        player.setFireTicks(Math.max(player.getFireTicks(), COMBUSTION_TICKS));
        return true;
    }

    private void applyExistingBrokenMirrorKarma(UUID playerId) {
        TeamColor playerTeam = session.getTeam(playerId);
        if (playerTeam == null) {
            return;
        }
        int inheritedKarma = 0;
        for (TeamColor team : TeamColor.values()) {
            if (team == playerTeam) {
                continue;
            }
            inheritedKarma += Math.max(0, session.getUpgradeTier(team, TeamUpgradeType.BROKEN_MIRROR));
        }
        if (inheritedKarma > 0) {
            addTemporaryKarma(playerId, inheritedKarma);
        }
    }

    private void clearNearbyFire(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block block = location.getWorld().getBlockAt(
                            location.getBlockX() + dx,
                            location.getBlockY() + dy,
                            location.getBlockZ() + dz);
                    if (FIRE_MATERIALS.contains(block.getType())) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void spendKarma(UUID playerId, int amount) {
        if (playerId == null || amount <= 0) {
            return;
        }
        int temporary = getTemporaryKarma(playerId);
        int temporarySpent = Math.min(temporary, amount);
        if (temporarySpent > 0) {
            addTemporaryKarma(playerId, -temporarySpent);
        }
        int remaining = amount - temporarySpent;
        if (remaining > 0 && service != null) {
            service.spendPermanentKarma(playerId, remaining);
        }
    }

    private int clampToNonNegativeInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private void cancelPlayerChecks() {
        for (BukkitTask task : playerCheckTasks.values()) {
            task.cancel();
        }
        playerCheckTasks.clear();
    }

    private void cancelPlayerCheck(UUID playerId) {
        BukkitTask task = playerCheckTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void clearActiveAnvils() {
        for (UUID entityId : new ArrayList<>(activeKarmaAnvils)) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                entity.remove();
            }
        }
        activeKarmaAnvils.clear();
    }

    private enum KarmaEventType {
        EXPLOSION("Explosion"),
        LIGHTNING_STRIKE("Lightning Strike"),
        WEAKNESS("Weakness"),
        ANVIL_FALL("Anvil Fall"),
        SLOWNESS("Slowness"),
        CONFUSION("Confusion"),
        SPONTANEOUS_COMBUSTION("Spontaneous Combustion");

        private final String displayName;

        KarmaEventType(String displayName) {
            this.displayName = displayName;
        }
    }
}
