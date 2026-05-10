package krispasi.omGames.bedwars.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import krispasi.omGames.bedwars.event.BedwarsMatchEventType;
import krispasi.omGames.bedwars.generator.GeneratorInfo;
import krispasi.omGames.bedwars.generator.GeneratorType;
import krispasi.omGames.bedwars.item.CustomItemConfig;
import krispasi.omGames.bedwars.item.CustomItemDefinition;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopItemDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

final class GameSessionRaptureRuntime {
    private static final int RAPTURE_EVENT_COUNT = 4;
    private static final int WARNING_SECONDS = 10;
    private static final int MIN_GAP_SECONDS = 60;
    private static final int POLLUTION_RADIUS = 10;
    private static final int POLLUTION_POISON_DURATION_TICKS = 10 * 20;
    private static final int NUKE_COUNTDOWN_CHAT_SECONDS = 15;
    private static final float DEFAULT_TACTICAL_NUKE_YIELD = 30.0f;
    private static final int DEFAULT_TACTICAL_NUKE_LIFETIME_SECONDS = 60;
    private static final double HEALTH_SCALE_HALF = 0.5;
    private static final double HEALTH_SCALE_DOUBLE = 2.0;

    private final GameSession session;
    private final Map<UUID, TeamColor> assignments;
    private final List<BukkitTask> sessionTasks;
    private final List<Location> pollutionCloudCenters = new ArrayList<>();
    private BukkitTask pollutionTask;
    private boolean pestilenceActive;
    private double healthScaleMultiplier = 1.0;

    GameSessionRaptureRuntime(GameSession session,
                              Map<UUID, TeamColor> assignments,
                              List<BukkitTask> sessionTasks) {
        this.session = session;
        this.assignments = assignments;
        this.sessionTasks = sessionTasks;
    }

    void start() {
        reset();
        if (session.plugin == null) {
            return;
        }
        List<RaptureOutcome> outcomes = rollOutcomes();
        List<Integer> triggers = buildTriggerTimelineSeconds();
        for (int i = 0; i < Math.min(outcomes.size(), triggers.size()); i++) {
            RaptureOutcome outcome = outcomes.get(i);
            int triggerAt = triggers.get(i);
            int warningAt = triggerAt - WARNING_SECONDS;
            if (warningAt > 0) {
                scheduleWarning(warningAt);
            }
            scheduleOutcome(outcome, triggerAt);
        }
    }

    void reset() {
        if (pollutionTask != null) {
            pollutionTask.cancel();
            pollutionTask = null;
        }
        pollutionCloudCenters.clear();
        pestilenceActive = false;
        healthScaleMultiplier = 1.0;
    }

    void applyTo(Player player) {
        if (player == null) {
            return;
        }
        if (pestilenceActive) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 0, true, false, true));
        }
        if (Math.abs(healthScaleMultiplier - 1.0) > 0.00001) {
            session.setAttributeToDefaultMultiplier(player, healthScaleMultiplier, "MAX_HEALTH", "GENERIC_MAX_HEALTH");
            session.clampPlayerHealthToMax(player);
        }
    }

    private List<RaptureOutcome> rollOutcomes() {
        List<RaptureOutcome> outcomes = new ArrayList<>(RAPTURE_EVENT_COUNT);
        outcomes.add(ThreadLocalRandom.current().nextBoolean() ? RaptureOutcome.FAMINE : RaptureOutcome.MELTDOWN);
        outcomes.add(ThreadLocalRandom.current().nextBoolean() ? RaptureOutcome.PESTILENCE : RaptureOutcome.POLLUTION);
        outcomes.add(ThreadLocalRandom.current().nextBoolean() ? RaptureOutcome.WAR : RaptureOutcome.CONQUEST);
        outcomes.add(ThreadLocalRandom.current().nextBoolean() ? RaptureOutcome.DEATH : RaptureOutcome.ETERNITY);
        return outcomes;
    }

    private List<Integer> buildTriggerTimelineSeconds() {
        int bedDestructionDelay = 0;
        if (session.getArena() != null && session.getArena().getEventSettings() != null) {
            bedDestructionDelay = session.resolveMatchPhaseDelaySeconds(session.getArena().getEventSettings().getBedDestructionDelay());
        }
        int latest = Math.max(120, bedDestructionDelay - 5);
        int[] anchors = new int[] {
                (int) Math.round(latest * 0.15),
                (int) Math.round(latest * 0.35),
                (int) Math.round(latest * 0.55),
                (int) Math.round(latest * 0.75)
        };
        List<Integer> result = new ArrayList<>(RAPTURE_EVENT_COUNT);
        int previous = 30;
        for (int i = 0; i < RAPTURE_EVENT_COUNT; i++) {
            int jitter = ThreadLocalRandom.current().nextInt(-15, 16);
            int candidate = anchors[i] + jitter;
            int min = previous + MIN_GAP_SECONDS;
            int max = latest - ((RAPTURE_EVENT_COUNT - i - 1) * MIN_GAP_SECONDS);
            if (max < min) {
                max = min;
            }
            candidate = Math.max(min, Math.min(max, candidate));
            result.add(candidate);
            previous = candidate;
        }
        return result;
    }

    private void scheduleWarning(int delaySeconds) {
        BukkitTask task = session.plugin.getServer().getScheduler().runTaskLater(session.plugin, () ->
                session.safeRun("raptureWarning", () -> {
                    if (!isRaptureRunning()) {
                        return;
                    }
                    session.playSoundToParticipants(Sound.ENTITY_WITHER_AMBIENT, 1.3f, 0.8f);
                    session.broadcast(Component.text("The anger of god is coming!", NamedTextColor.DARK_RED));
                }), delaySeconds * 20L);
        sessionTasks.add(task);
    }

    private void scheduleOutcome(RaptureOutcome outcome, int delaySeconds) {
        BukkitTask task = session.plugin.getServer().getScheduler().runTaskLater(session.plugin, () ->
                session.safeRun("raptureOutcome", () -> {
                    if (!isRaptureRunning() || outcome == null) {
                        return;
                    }
                    session.showTitleAll(Component.text(outcome.displayName, NamedTextColor.DARK_RED), Component.empty());
                    session.playSoundToParticipants(Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
                    outcome.execute(this);
                }), delaySeconds * 20L);
        sessionTasks.add(task);
    }

    private boolean isRaptureRunning() {
        return session.isRunning() && session.getActiveMatchEvent() == BedwarsMatchEventType.THE_RAPTURE;
    }

    private void executeFamine() {
        if (session.arena == null || session.arena.getWorld() == null) {
            return;
        }
        World world = session.arena.getWorld();
        List<BlockPoint> changed = new ArrayList<>();
        for (BlockPoint point : new ArrayList<>(session.placedBlocks)) {
            if (ThreadLocalRandom.current().nextDouble() > 0.5) {
                continue;
            }
            if (!session.isInsideMap(point)) {
                continue;
            }
            Block block = world.getBlockAt(point.x(), point.y(), point.z());
            if (block.getType() == Material.AIR) {
                continue;
            }
            block.setType(Material.SPAWNER, false);
            changed.add(point);
        }
        if (changed.isEmpty() || session.plugin == null) {
            return;
        }
        BukkitTask cleanupTask = session.plugin.getServer().getScheduler().runTaskLater(session.plugin, () ->
                session.safeRun("raptureFamineCleanup", () -> {
                    World currentWorld = session.arena.getWorld();
                    if (currentWorld == null) {
                        return;
                    }
                    for (BlockPoint point : changed) {
                        Block block = currentWorld.getBlockAt(point.x(), point.y(), point.z());
                        if (block.getType() == Material.SPAWNER) {
                            block.setType(Material.AIR, false);
                            session.removePlacedBlock(point);
                        }
                    }
                }), 10L * 20L);
        sessionTasks.add(cleanupTask);
    }

    private void executeMeltdown() {
        if (session.arena == null || session.arena.getWorld() == null) {
            return;
        }
        CustomItemDefinition tacticalNuke = resolveTacticalNukeDefinition();
        int radius = Math.max(1, Math.round(tacticalNuke != null && tacticalNuke.getYield() > 0.0f
                ? tacticalNuke.getYield()
                : DEFAULT_TACTICAL_NUKE_YIELD));
        int countdown = tacticalNuke != null && tacticalNuke.getLifetimeSeconds() > 0
                ? tacticalNuke.getLifetimeSeconds()
                : DEFAULT_TACTICAL_NUKE_LIFETIME_SECONDS;
        World world = session.arena.getWorld();
        for (GeneratorInfo generator : session.arena.getGenerators()) {
            if (generator.type() != GeneratorType.EMERALD) {
                continue;
            }
            Location center = new Location(world,
                    generator.location().x() + 0.5,
                    generator.location().y() + 1.0,
                    generator.location().z() + 0.5);
            armMeltdownNuke(center, radius, countdown);
        }
    }

    private CustomItemDefinition resolveTacticalNukeDefinition() {
        CustomItemConfig customConfig = session.bedwarsManager.getCustomItemConfig();
        return customConfig != null ? customConfig.getItem("tactical_nuke") : null;
    }

    private void armMeltdownNuke(Location center, int radius, int countdown) {
        if (center == null || center.getWorld() == null || session.plugin == null) {
            return;
        }
        World world = center.getWorld();
        Map<BlockPoint, String> originals = new HashMap<>();
        Map<BlockPoint, ItemStack[]> containerContents = new HashMap<>();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int minY = Math.max(world.getMinHeight(), cy - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, cy + radius);
        double radiusSquared = radius * radius;
        for (int x = cx - radius; x <= cx + radius; x++) {
            int dx = x - cx;
            for (int y = minY; y <= maxY; y++) {
                int dy = y - cy;
                for (int z = cz - radius; z <= cz + radius; z++) {
                    int dz = z - cz;
                    if ((dx * dx + dy * dy + dz * dz) > radiusSquared) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    Material blockType = block.getType();
                    if (blockType.isAir() || blockType == Material.RED_CONCRETE) {
                        continue;
                    }
                    BlockPoint point = new BlockPoint(x, y, z);
                    originals.put(point, block.getBlockData().getAsString());
                    if (block.getState() instanceof Container container) {
                        ItemStack[] contents = container.getInventory().getContents();
                        ItemStack[] snapshot = new ItemStack[contents.length];
                        for (int i = 0; i < contents.length; i++) {
                            ItemStack stack = contents[i];
                            snapshot[i] = stack != null ? stack.clone() : null;
                        }
                        containerContents.put(point, snapshot);
                    }
                    if (shouldHighlightNukeBlock(block)) {
                        block.setType(Material.RED_CONCRETE, false);
                    }
                }
            }
        }
        broadcastNukeTitle(countdown);
        startNukeBeaconEffect(center, countdown);
        BukkitTask task = new BukkitRunnable() {
            private int remaining = countdown;

            @Override
            public void run() {
                session.safeRun("raptureMeltdownNukeCountdown", () -> {
                    if (!isRaptureRunning()) {
                        restoreNukeBlocks(world, originals, containerContents, java.util.Set.of());
                        cancel();
                        return;
                    }
                    if (remaining <= 0) {
                        detonateTacticalNukeAt(center, radius, originals, containerContents);
                        cancel();
                        return;
                    }
                    broadcastNukeActionBar(remaining);
                    if (remaining <= NUKE_COUNTDOWN_CHAT_SECONDS) {
                        broadcastNukeCountdown(remaining, center);
                    }
                    remaining--;
                });
            }
        }.runTaskTimer(session.plugin, 0L, 20L);
        sessionTasks.add(task);
    }

    private boolean shouldHighlightNukeBlock(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        if (type.isAir() || type == Material.DIAMOND_BLOCK || type == Material.EMERALD_BLOCK) {
            return false;
        }
        String name = type.name();
        if (name.endsWith("_STAIRS") || name.endsWith("_SLAB") || name.endsWith("_BED")) {
            return false;
        }
        if (!type.isOccluding()) {
            return false;
        }
        World world = block.getWorld();
        if (block.getY() < world.getMaxHeight() - 1) {
            Block above = block.getRelative(org.bukkit.block.BlockFace.UP);
            if (!above.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private void broadcastNukeTitle(int countdown) {
        session.showTitleAll(Component.text("TACTICAL NUKE ACTIVATED", NamedTextColor.RED),
                Component.text("Explosion in " + formatNukeTime(countdown), NamedTextColor.YELLOW));
        session.broadcast(Component.text("Tactical nuke activated.", NamedTextColor.RED));
    }

    private void broadcastNukeActionBar(int seconds) {
        Component message = Component.text("TACTICAL NUKE: " + seconds + "s", NamedTextColor.RED);
        forEachActiveParticipant(player -> player.sendActionBar(message));
    }

    private void broadcastNukeCountdown(int seconds, Location origin) {
        session.broadcast(Component.text("Tactical Nuke: " + seconds + "s", NamedTextColor.RED));
        if (origin != null && origin.getWorld() != null) {
            session.playSoundToParticipants(Sound.BLOCK_NOTE_BLOCK_PLING, 2.0f, 1.2f);
        }
    }

    private String formatNukeTime(int seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        if (seconds >= 60) {
            int minutes = seconds / 60;
            int remainder = seconds % 60;
            return minutes + "m " + remainder + "s";
        }
        return seconds + "s";
    }

    private void startNukeBeaconEffect(Location origin, int durationSeconds) {
        if (origin == null || origin.getWorld() == null || session.plugin == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.RED, 1.7f);
        BukkitTask task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                session.safeRun("raptureMeltdownNukeBeacon", () -> {
                    if (!isRaptureRunning()) {
                        cancel();
                        return;
                    }
                    if (ticks >= durationSeconds * 20) {
                        cancel();
                        return;
                    }
                    Location base = origin.clone().add(0.5, 0.2, 0.5);
                    for (double y = 0; y <= 18; y += 0.6) {
                        base.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, y, 0),
                                2, 0.02, 0.02, 0.02, 0.0, dust);
                    }
                    ticks += 5;
                });
            }
        }.runTaskTimer(session.plugin, 0L, 5L);
        sessionTasks.add(task);
    }

    private void detonateTacticalNukeAt(Location center,
                                        int radius,
                                        Map<BlockPoint, String> originals,
                                        Map<BlockPoint, ItemStack[]> containerContents) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        java.util.Set<BlockPoint> removed = new java.util.HashSet<>();
        for (Map.Entry<BlockPoint, String> entry : originals.entrySet()) {
            BlockPoint point = entry.getKey();
            if (session.isPlacedBlock(point)) {
                Block block = world.getBlockAt(point.x(), point.y(), point.z());
                ItemStack drop = session.removePlacedBlockItem(point);
                if (drop != null && drop.getType() != Material.AIR) {
                    world.dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
                }
                block.setType(Material.AIR, false);
                removed.add(point);
            }
        }
        restoreNukeBlocks(world, originals, containerContents, removed);
        double radiusSquared = radius * radius;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player target) {
                if (!session.isParticipant(target.getUniqueId()) || target.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
            } else if (entity instanceof Villager villager) {
                if (villager.getScoreboardTags().contains(GameSession.ITEM_SHOP_TAG)
                        || villager.getScoreboardTags().contains(GameSession.UPGRADES_SHOP_TAG)) {
                    continue;
                }
            }
            if (entity.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            if (entity instanceof Player target) {
                target.setHealth(0.0);
            } else {
                entity.damage(1000.0);
            }
        }
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.7f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2, 0.5, 0.5, 0.5, 0.0);
        for (int i = 0; i < 30; i++) {
            double dx = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * radius;
            double dy = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * radius;
            double dz = (ThreadLocalRandom.current().nextDouble() * 2 - 1) * radius;
            if ((dx * dx + dy * dy + dz * dz) > radiusSquared) {
                continue;
            }
            world.spawnParticle(Particle.EXPLOSION, center.clone().add(dx, dy, dz),
                    1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    private void restoreNukeBlocks(World world,
                                   Map<BlockPoint, String> originals,
                                   Map<BlockPoint, ItemStack[]> containerContents,
                                   java.util.Set<BlockPoint> removed) {
        if (world == null || originals == null || originals.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPoint, String> entry : originals.entrySet()) {
            BlockPoint point = entry.getKey();
            if (removed != null && removed.contains(point)) {
                continue;
            }
            String data = entry.getValue();
            Block block = world.getBlockAt(point.x(), point.y(), point.z());
            BlockData blockData = data != null ? Bukkit.createBlockData(data) : null;
            if (blockData != null) {
                block.setBlockData(blockData, false);
            }
            if (containerContents != null && containerContents.containsKey(point)
                    && block.getState() instanceof Container container) {
                ItemStack[] snapshot = containerContents.get(point);
                if (snapshot != null) {
                    ItemStack[] restored = new ItemStack[snapshot.length];
                    for (int i = 0; i < snapshot.length; i++) {
                        ItemStack stack = snapshot[i];
                        restored[i] = stack != null ? stack.clone() : null;
                    }
                    container.getInventory().setContents(restored);
                    container.update(true, false);
                }
            }
        }
    }

    private void executePestilence() {
        pestilenceActive = true;
        forEachActiveParticipant(this::applyTo);
    }

    private void executePollution() {
        if (session.arena == null || session.arena.getWorld() == null || session.plugin == null) {
            return;
        }
        pollutionCloudCenters.clear();
        World world = session.arena.getWorld();
        for (GeneratorInfo generator : session.arena.getGenerators()) {
            if (generator.type() == GeneratorType.DIAMOND || generator.type() == GeneratorType.EMERALD) {
                pollutionCloudCenters.add(new Location(world,
                        generator.location().x() + 0.5,
                        generator.location().y() + 1.0,
                        generator.location().z() + 0.5));
            }
        }
        if (pollutionCloudCenters.isEmpty()) {
            return;
        }
        if (pollutionTask != null) {
            pollutionTask.cancel();
        }
        pollutionTask = new BukkitRunnable() {
            @Override
            public void run() {
                session.safeRun("rapturePollutionTick", () -> {
                    if (!isRaptureRunning()) {
                        cancel();
                        pollutionTask = null;
                        return;
                    }
                    for (Location center : pollutionCloudCenters) {
                        World cWorld = center.getWorld();
                        if (cWorld == null) {
                            continue;
                        }
                        cWorld.spawnParticle(Particle.SMOKE, center, 60, 4.5, 2.0, 4.5, 0.005);
                        cWorld.spawnParticle(Particle.OMINOUS_SPAWNING, center, 20, 3.0, 1.0, 3.0, 0.02);
                    }
                    forEachActiveParticipant(player -> {
                        Location playerLoc = player.getLocation();
                        for (Location center : pollutionCloudCenters) {
                            if (center.getWorld() != playerLoc.getWorld()) {
                                continue;
                            }
                            if (center.distanceSquared(playerLoc) <= POLLUTION_RADIUS * POLLUTION_RADIUS) {
                                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                                        POLLUTION_POISON_DURATION_TICKS,
                                        1,
                                        true,
                                        true,
                                        true), true);
                                break;
                            }
                        }
                    });
                });
            }
        }.runTaskTimer(session.plugin, 0L, 20L);
        sessionTasks.add(pollutionTask);
    }

    private void executeWar() {
        forEachAssignedPlayer((player, team) -> {
            grantItem(player, team, new ItemStack(Material.GOLDEN_APPLE, 10));
            grantItem(player, team, new ItemStack(Material.WIND_CHARGE, 20));
            grantItem(player, team, new ItemStack(Material.DIAMOND_SWORD, 1));
            grantItem(player, team, new ItemStack(Material.BOW, 1));
            grantItem(player, team, new ItemStack(Material.ARROW, 20));
            grantRotatingItem(player, team, "abyssal_rift", 1);
        });
    }

    private void executeConquest() {
        ItemStack upgradedBow = new ItemStack(Material.BOW, 1);
        upgradedBow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.POWER, 2);
        upgradedBow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PUNCH, 1);
        forEachAssignedPlayer((player, team) -> {
            grantItem(player, team, upgradedBow.clone());
            grantItem(player, team, new ItemStack(Material.ARROW, 20));
            grantRotatingItem(player, team, "bridge_builder", 2);
            grantItem(player, team, new ItemStack(Material.OBSIDIAN, 8));
            grantRotatingItem(player, team, "abyssal_rift_corruption", 1);
            grantRotatingItem(player, team, "abyssal_rift_regeneration", 1);
        });
    }

    private void executeDeath() {
        healthScaleMultiplier = HEALTH_SCALE_HALF;
        forEachActiveParticipant(player -> {
            applyTo(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 30 * 20, 2, true, true, true), true);
        });
    }

    private void executeEternity() {
        healthScaleMultiplier = HEALTH_SCALE_DOUBLE;
        forEachActiveParticipant(player -> {
            applyTo(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30 * 20, 1, true, true, true), true);
        });
    }

    private void grantRotatingItem(Player player, TeamColor team, String itemId, int amount) {
        if (itemId == null || amount <= 0) {
            return;
        }
        ShopConfig config = session.bedwarsManager.getShopConfig();
        if (config == null) {
            return;
        }
        ShopItemDefinition definition = config.getItem(itemId);
        if (definition == null) {
            return;
        }
        ItemStack stack = definition.createPurchaseItem(team);
        if (stack == null) {
            return;
        }
        stack.setAmount(Math.max(1, stack.getAmount() * amount));
        grantItem(player, team, stack);
    }

    private void grantItem(Player player, TeamColor team, ItemStack item) {
        if (player == null || item == null || team == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (shouldDropAtBase(player, playerId)) {
            dropAtBaseGenerator(team, item);
            return;
        }
        session.giveItem(player, item);
    }

    private boolean shouldDropAtBase(Player player, UUID playerId) {
        return player == null
                || !player.isOnline()
                || !session.isInArenaWorld(player.getWorld())
                || player.getGameMode() == GameMode.SPECTATOR
                || session.pendingRespawns.contains(playerId);
    }

    private void dropAtBaseGenerator(TeamColor team, ItemStack item) {
        if (team == null || item == null || session.arena == null || session.arena.getWorld() == null) {
            return;
        }
        Location drop = resolveTeamBaseGeneratorLocation(team);
        if (drop == null) {
            return;
        }
        session.arena.getWorld().dropItemNaturally(drop, item);
    }

    private Location resolveTeamBaseGeneratorLocation(TeamColor team) {
        if (team == null || session.arena == null || session.arena.getWorld() == null) {
            return null;
        }
        World world = session.arena.getWorld();
        for (GeneratorInfo generator : session.arena.getGenerators()) {
            if (generator.type() == GeneratorType.BASE && team == generator.team()) {
                return new Location(world,
                        generator.location().x() + 0.5,
                        generator.location().y() + 1.0,
                        generator.location().z() + 0.5);
            }
        }
        Location spawn = session.arena.getSpawn(team);
        return spawn != null ? spawn.clone() : null;
    }

    private void forEachActiveParticipant(java.util.function.Consumer<Player> action) {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null
                    || !player.isOnline()
                    || !session.isInArenaWorld(player.getWorld())
                    || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            action.accept(player);
        }
    }

    private void forEachAssignedPlayer(AssignedPlayerConsumer action) {
        Map<UUID, TeamColor> snapshot = new HashMap<>(assignments);
        for (Map.Entry<UUID, TeamColor> entry : snapshot.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            action.accept(player, entry.getValue());
        }
    }

    private enum RaptureOutcome {
        FAMINE("Famine") {
            @Override
            void execute(GameSessionRaptureRuntime runtime) {
                runtime.executeFamine();
            }
        },
        MELTDOWN("Meltdown") {
            @Override
            void execute(GameSessionRaptureRuntime runtime) {
                runtime.executeMeltdown();
            }
        },
        PESTILENCE("Pestilence") {
            @Override
            void execute(GameSessionRaptureRuntime runtime) {
                runtime.executePestilence();
            }
        },
        POLLUTION("Pollution") {
            @Override
            void execute(GameSessionRaptureRuntime runtime) {
                runtime.executePollution();
            }
        },
        WAR("War") {
            @Override
            void execute(GameSessionRaptureRuntime runtime) {
                runtime.executeWar();
            }
        },
        CONQUEST("Conquest") {
            @Override
            void execute(GameSessionRaptureRuntime runtime) {
                runtime.executeConquest();
            }
        },
        DEATH("Death") {
            @Override
            void execute(GameSessionRaptureRuntime runtime) {
                runtime.executeDeath();
            }
        },
        ETERNITY("Eternity") {
            @Override
            void execute(GameSessionRaptureRuntime runtime) {
                runtime.executeEternity();
            }
        };

        private final String displayName;

        RaptureOutcome(String displayName) {
            this.displayName = displayName;
        }

        abstract void execute(GameSessionRaptureRuntime runtime);
    }

    @FunctionalInterface
    private interface AssignedPlayerConsumer {
        void accept(Player player, TeamColor team);
    }
}
