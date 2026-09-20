package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

final class HallsSessionBossRuntime {
    private static final String BOSS_TAG = "omgames_hoc_boss";
    private static final double HIT_FLASH_RADIUS = 1.8;
    private static final float MIN_MELEE_ATTACK_COOLDOWN = 0.88f;
    private static final long MELEE_COOLDOWN_MESSAGE_TICKS = 10L;

    private final JavaPlugin plugin;
    private final World world;
    private final Set<UUID> participants;
    private final Map<String, HallsBossType> bossTypes;
    private final Predicate<UUID> aliveParticipantPredicate;
    private final BiFunction<String, Location, Boolean> monsterSpawner;
    private final BlockSetter blockSetter;
    private final Runnable bossMinionClearCallback;
    private final Runnable defeatedCallback;
    private final NamespacedKey bossIdKey;
    private final Random random = new Random();
    private final Map<UUID, Long> cooldownMessageTicks = new java.util.HashMap<>();

    private ActiveBoss activeBoss;
    private BossBar bossBar;
    private BukkitTask tickTask;
    private BukkitTask attackTask;
    private BukkitTask defeatedOpenTask;

    HallsSessionBossRuntime(JavaPlugin plugin,
                            World world,
                            Set<UUID> participants,
                            Map<String, HallsBossType> bossTypes,
                            Predicate<UUID> aliveParticipantPredicate,
                            BiFunction<String, Location, Boolean> monsterSpawner,
                            BlockSetter blockSetter,
                            Runnable bossMinionClearCallback,
                            Runnable defeatedCallback) {
        this.plugin = plugin;
        this.world = world;
        this.participants = participants;
        this.bossTypes = bossTypes == null ? Map.of() : Map.copyOf(bossTypes);
        this.aliveParticipantPredicate = aliveParticipantPredicate == null ? id -> true : aliveParticipantPredicate;
        this.monsterSpawner = monsterSpawner == null ? (id, location) -> false : monsterSpawner;
        this.blockSetter = blockSetter == null ? (x, y, z, material, face) -> { } : blockSetter;
        this.bossMinionClearCallback = bossMinionClearCallback == null ? () -> { } : bossMinionClearCallback;
        this.defeatedCallback = defeatedCallback == null ? () -> { } : defeatedCallback;
        this.bossIdKey = new NamespacedKey(plugin, "hoc_boss_id");
    }

    void prepare(String bossId, Location location, DoorSeal seal) {
        clear();
        HallsBossType type = bossTypes.get(normalizeId(bossId));
        if (type == null || location == null || !world.equals(location.getWorld())) {
            return;
        }
        List<UUID> displayIds = spawnDisplays(type, location, 0.0f, 0.0);
        Interaction hitbox = world.spawn(location.clone().add(0.0, 0.1, 0.0), Interaction.class, entity -> {
            entity.setInteractionWidth(5.0f);
            entity.setInteractionHeight(5.0f);
            entity.setResponsive(true);
            entity.setPersistent(false);
            entity.addScoreboardTag(BOSS_TAG);
            entity.getPersistentDataContainer().set(bossIdKey, PersistentDataType.STRING, type.id());
        });
        activeBoss = new ActiveBoss(type, location.clone(), displayIds, hitbox.getUniqueId(), seal,
                scaledHealth(type), scaledHealth(type));
        bossBar = Bukkit.createBossBar(type.name(), BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setProgress(1.0);
        bossBar.setVisible(false);
        refreshBossBarPlayers();
    }

    boolean isBossEntity(Entity entity) {
        return entity != null && activeBoss != null
                && (entity.getUniqueId().equals(activeBoss.hitboxId()) || activeBoss.displayIds().contains(entity.getUniqueId()));
    }

    boolean exitUnlocked() {
        return activeBoss == null || activeBoss.defeated();
    }

    boolean handleAttack(Player player, Entity entity, double damage) {
        if (player == null || entity == null || activeBoss == null || !isBossEntity(entity)) {
            return false;
        }
        if (!participants.contains(player.getUniqueId()) || !aliveParticipantPredicate.test(player.getUniqueId())) {
            player.sendActionBar(Component.text("Ghosts cannot harm the boss.", NamedTextColor.GRAY));
            return true;
        }
        if (!activeBoss.active()) {
            activate(player);
            return true;
        }
        float attackCooldown = player.getAttackCooldown();
        if (attackCooldown < MIN_MELEE_ATTACK_COOLDOWN) {
            sendCooldownMessage(player);
            world.spawnParticle(Particle.SMOKE, entity.getLocation().clone().add(0.0, 1.2, 0.0),
                    6, 0.25, 0.25, 0.25, 0.01);
            return true;
        }
        damage(Math.max(1.0, damage), player.getLocation());
        return true;
    }

    boolean handleProjectileHit(Player shooter, Entity entity, double damage) {
        if (shooter == null || entity == null || activeBoss == null || !isBossEntity(entity) || !activeBoss.active()) {
            return false;
        }
        if (!participants.contains(shooter.getUniqueId()) || !aliveParticipantPredicate.test(shooter.getUniqueId())) {
            return true;
        }
        damage(Math.max(1.0, damage), entity.getLocation());
        return true;
    }

    String debugStatus() {
        if (activeBoss == null) {
            return "none";
        }
        return activeBoss.type().id() + " " + Math.max(0, (int) Math.ceil(activeBoss.health()))
                + "/" + (int) Math.ceil(activeBoss.maxHealth())
                + (activeBoss.active() ? " active" : " dormant");
    }

    void clear() {
        cancelTasks();
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (activeBoss != null) {
            for (UUID displayId : activeBoss.displayIds()) {
                Entity display = Bukkit.getEntity(displayId);
                if (display != null) {
                    display.remove();
                }
            }
            Entity hitbox = Bukkit.getEntity(activeBoss.hitboxId());
            if (hitbox != null) {
                hitbox.remove();
            }
            activeBoss = null;
        }
    }

    private void activate(Player player) {
        if (activeBoss == null || activeBoss.active()) {
            return;
        }
        activeBoss.setActive(true);
        sealEntrance();
        if (bossBar != null) {
            bossBar.setVisible(true);
        }
        world.playSound(activeBoss.location(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.7f);
        for (UUID playerId : participants) {
            Player participant = Bukkit.getPlayer(playerId);
            if (participant != null && participant.getWorld().equals(world)) {
                participant.sendTitle(activeBoss.type().name(), "The entrance seals behind you.", 10, 60, 10);
            }
        }
        new RetractAnimation().runTaskTimer(plugin, 1L, 1L);
        startTicking();
    }

    private void damage(double amount, Location source) {
        if (activeBoss == null || !activeBoss.active()) {
            return;
        }
        activeBoss.setHealth(activeBoss.health() - amount);
        updateBossBar();
        Location center = activeBoss.location().clone().add(0.0, 2.5, 0.0);
        world.spawnParticle(Particle.CRIT, center, 18, HIT_FLASH_RADIUS, 1.4, HIT_FLASH_RADIUS, 0.05);
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.9f, 0.9f);
        if (source != null) {
            Vector knock = center.toVector().subtract(source.toVector());
            if (knock.lengthSquared() > 0.01) {
                animateDisplays(activeBoss.yaw(), 0.0, knock.normalize().multiply(0.08));
            }
        }
        if (activeBoss.health() <= 0.0) {
            defeat();
        }
    }

    private void defeat() {
        if (activeBoss == null) {
            return;
        }
        cancelTasks();
        bossMinionClearCallback.run();
        Location center = activeBoss.location().clone().add(0.0, 2.2, 0.0);
        world.spawnParticle(Particle.EXPLOSION, center, 2, 0.4, 0.4, 0.4, 0.0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, 100, 2.4, 1.8, 2.4, 0.08);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.75f);
        for (UUID displayId : activeBoss.displayIds()) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null) {
                display.remove();
            }
        }
        Entity hitbox = Bukkit.getEntity(activeBoss.hitboxId());
        if (hitbox != null) {
            hitbox.remove();
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        DoorSeal seal = activeBoss.seal();
        activeBoss.setDefeated(true);
        activeBoss = null;
        defeatedOpenTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            defeatedOpenTask = null;
            openEntrance(seal);
            defeatedCallback.run();
        }, 60L);
    }

    private void startTicking() {
        if (tickTask == null) {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
        scheduleNextAttack(30L);
    }

    private void tick() {
        if (activeBoss == null || !activeBoss.active()) {
            return;
        }
        refreshBossBarPlayers();
        activeBoss.setIdleTicks(activeBoss.idleTicks() + 1);
        double bob = Math.sin(activeBoss.idleTicks() / 8.0) * 0.04;
        animateDisplays(activeBoss.yaw(), bob, new Vector());
        Location core = activeBoss.location().clone().add(0.0, 2.45 + bob, 0.0);
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, core, 2, 1.9, 1.5, 1.9, 0.0);
        if (activeBoss.idleTicks() % 8 == 0) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, core, 8, 2.0, 1.4, 2.0, 0.04);
        }
    }

    private void scheduleNextAttack(long delayTicks) {
        if (attackTask != null) {
            attackTask.cancel();
        }
        attackTask = Bukkit.getScheduler().runTaskLater(plugin, this::runNextAttack, Math.max(1L, delayTicks));
    }

    private void runNextAttack() {
        if (activeBoss == null || !activeBoss.active()) {
            return;
        }
        Attack attack = chooseAttack();
        switch (attack) {
            case SPAWN -> runSpawnAttack();
            case JUMP -> runJumpAttack();
            case X_BLAST -> runXBlastAttack();
        }
    }

    private Attack chooseAttack() {
        if (activeBoss.lastAttack() == Attack.JUMP && activeBoss.health() <= activeBoss.maxHealth() * 0.5
                && random.nextDouble() < 0.70) {
            return Attack.JUMP;
        }
        List<Attack> attacks = new ArrayList<>(List.of(Attack.SPAWN, Attack.JUMP, Attack.X_BLAST));
        if (activeBoss.lastAttack() != null && attacks.size() > 1) {
            attacks.remove(activeBoss.lastAttack());
        }
        return attacks.get(random.nextInt(attacks.size()));
    }

    private void runSpawnAttack() {
        HallsBossType.Overdrive config = activeBoss.type().overdrive();
        activeBoss.setLastAttack(Attack.SPAWN);
        new TimedAttack(config.spawnChargeTicks(), () -> {
            activeBoss.setYaw(activeBoss.yaw() + 18.0f);
            Location center = activeBoss.location().clone().add(0.0, 2.7, 0.0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, center, 5, 1.9, 1.4, 1.9, 0.04);
            animateDisplays(activeBoss.yaw(), 0.1, new Vector());
        }, () -> {
            int count = config.minSpawnCount() + random.nextInt(config.maxSpawnCount() - config.minSpawnCount() + 1);
            for (int i = 0; i < count; i++) {
                String monsterId = weightedMonster(activeBoss.type().weightedSpawnPool());
                Location spawn = activeBoss.location().clone().add(randomOffset(3.5), 0.2, randomOffset(3.5));
                monsterSpawner.apply(monsterId, spawn);
                world.spawnParticle(Particle.ELECTRIC_SPARK, spawn.clone().add(0.0, 0.8, 0.0), 16, 0.4, 0.5, 0.4, 0.02);
            }
            world.playSound(activeBoss.location(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.3f, 1.25f);
            scheduleNextAttack(config.spawnCooldownTicks());
        });
    }

    private void runJumpAttack() {
        HallsBossType.Overdrive config = activeBoss.type().overdrive();
        activeBoss.setLastAttack(Attack.JUMP);
        new TimedAttack(config.jumpReadyTicks(), () -> {
            world.spawnParticle(Particle.DUST_PLUME, activeBoss.location().clone().add(0.0, 0.15, 0.0),
                    10, 1.7, 0.05, 1.7, 0.03);
            animateDisplays(activeBoss.yaw(), -0.12, new Vector());
        }, () -> new JumpSlam(config).runTaskTimer(plugin, 1L, 1L));
    }

    private void runXBlastAttack() {
        HallsBossType.Overdrive config = activeBoss.type().overdrive();
        activeBoss.setLastAttack(Attack.X_BLAST);
        new TimedAttack(config.xBlastMoveTicks(), () -> {
            activeBoss.setYaw(activeBoss.yaw() + 24.0f);
            animateDisplays(activeBoss.yaw(), 0.08, new Vector());
        }, () -> new TimedAttack(config.xBlastChargeTicks(), () -> renderXBlastWarning(false), () -> {
            fireXBlast(config.xBlastDamage());
            scheduleNextAttack(config.xBlastCooldownTicks());
        }));
    }

    private void renderXBlastWarning(boolean damaging) {
        if (activeBoss == null) {
            return;
        }
        Location center = activeBoss.location().clone().add(0.0, 1.0, 0.0);
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            for (double distance = 1.0; distance <= 12.0; distance += 0.75) {
                Location point = center.clone().add(face.getModX() * distance, 0.0, face.getModZ() * distance);
                world.spawnParticle(damaging ? Particle.FLAME : Particle.DUST_PLUME, point, 1, 0.08, 0.08, 0.08, 0.0);
            }
        }
    }

    private void fireXBlast(double damage) {
        renderXBlastWarning(true);
        world.playSound(activeBoss.location(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.7f);
        Location center = activeBoss.location().clone().add(0.0, 1.0, 0.0);
        for (Player player : alivePlayers()) {
            Location playerLocation = player.getLocation();
            double dx = Math.abs(playerLocation.getX() - center.getX());
            double dz = Math.abs(playerLocation.getZ() - center.getZ());
            boolean inNorthSouthBeam = dx <= 1.25 && dz <= 12.5;
            boolean inEastWestBeam = dz <= 1.25 && dx <= 12.5;
            if (Math.abs(playerLocation.getY() - center.getY()) <= 2.8 && (inNorthSouthBeam || inEastWestBeam)) {
                player.damage(damage);
            }
        }
    }

    private List<Player> alivePlayers() {
        List<Player> players = new ArrayList<>();
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world) && aliveParticipantPredicate.test(playerId)) {
                players.add(player);
            }
        }
        return players;
    }

    private void sealEntrance() {
        if (activeBoss == null || activeBoss.seal() == null) {
            return;
        }
        DoorSeal seal = activeBoss.seal();
        for (int x = seal.minX(); x <= seal.maxX(); x++) {
            for (int y = seal.y(); y <= seal.y() + 3; y++) {
                for (int z = seal.minZ(); z <= seal.maxZ(); z++) {
                    blockSetter.setBlock(x, y, z, seal.material(), null);
                }
            }
        }
        world.spawnParticle(Particle.SMOKE, seal.center(world), 60, 1.5, 1.2, 0.3, 0.04);
        world.playSound(seal.center(world), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.55f);
    }

    private void openEntrance(DoorSeal seal) {
        if (seal == null) {
            return;
        }
        for (int x = seal.minX(); x <= seal.maxX(); x++) {
            for (int y = seal.y(); y <= seal.y() + 3; y++) {
                for (int z = seal.minZ(); z <= seal.maxZ(); z++) {
                    blockSetter.setBlock(x, y, z, Material.AIR, null);
                }
            }
        }
        world.spawnParticle(Particle.ELECTRIC_SPARK, seal.center(world), 70, 1.5, 1.0, 0.35, 0.05);
        world.playSound(seal.center(world), Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.8f);
    }

    private List<HallsBossType.DisplayPart> displayParts(HallsBossType type) {
        if (!type.itemModel().isBlank()) {
            return List.of(new HallsBossType.DisplayPart(type.displayMaterial(), 0.0, 2.5, 0.0, 5.0, 5.0, 5.0));
        }
        return type.displayParts().isEmpty()
                ? List.of(new HallsBossType.DisplayPart(type.displayMaterial(), -2.5, 0.0, -2.5, 5.0, 5.0, 5.0))
                : type.displayParts();
    }

    private List<UUID> spawnDisplays(HallsBossType type, Location base, float yaw, double yOffset) {
        List<HallsBossType.DisplayPart> parts = displayParts(type);
        List<UUID> ids = new ArrayList<>();
        if (!type.itemModel().isBlank()) {
            HallsBossType.DisplayPart part = parts.get(0);
            ItemStack stack = new ItemStack(type.displayMaterial());
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                NamespacedKey model = NamespacedKey.fromString(type.itemModel());
                if (model != null) {
                    meta.setItemModel(model);
                    stack.setItemMeta(meta);
                }
            }
            Location location = displayLocation(base, part, yOffset, new Vector());
            ItemDisplay display = world.spawn(location, ItemDisplay.class, entity -> {
                entity.setItemStack(stack);
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setPersistent(false);
                entity.setInterpolationDelay(1);
                entity.setTeleportDuration(2);
                entity.setTransformation(HallsDisplayTransforms.centeredBlock(
                        part.scaleX(), part.scaleY(), part.scaleZ(),
                        new Quaternionf().rotateY((float) Math.toRadians(yaw))));
                entity.addScoreboardTag(BOSS_TAG);
            });
            ids.add(display.getUniqueId());
            return ids;
        }
        for (HallsBossType.DisplayPart part : parts) {
            Location location = displayLocation(base, part, yOffset, new Vector());
            BlockDisplay display = world.spawn(location, BlockDisplay.class, entity -> {
                entity.setBlock(part.material().createBlockData());
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setPersistent(false);
                entity.setInterpolationDelay(1);
                entity.setTeleportDuration(2);
                entity.setTransformation(HallsDisplayTransforms.centeredBlock(
                        part.scaleX(), part.scaleY(), part.scaleZ(),
                        new Quaternionf().rotateY((float) Math.toRadians(yaw))));
                entity.addScoreboardTag(BOSS_TAG);
            });
            ids.add(display.getUniqueId());
        }
        return ids;
    }

    private void animateDisplays(float yaw, double yOffset, Vector offset) {
        if (activeBoss == null) {
            return;
        }
        List<HallsBossType.DisplayPart> parts = displayParts(activeBoss.type());
        int index = 0;
        for (UUID displayId : activeBoss.displayIds()) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity == null) {
                index++;
                continue;
            }
            HallsBossType.DisplayPart part = parts.get(Math.min(index, parts.size() - 1));
            Location target = displayLocation(activeBoss.location(), part, yOffset, offset);
            entity.teleport(target);
            if (entity instanceof Display display) {
                display.setInterpolationDelay(1);
                display.setTeleportDuration(2);
                display.setTransformation(HallsDisplayTransforms.centeredBlock(
                        part.scaleX(), part.scaleY(), part.scaleZ(),
                        new Quaternionf().rotateY((float) Math.toRadians(yaw))));
            }
            index++;
        }
    }

    private Location displayLocation(Location base, HallsBossType.DisplayPart part, double yOffset, Vector animationOffset) {
        Vector offset = animationOffset == null ? new Vector() : animationOffset;
        return base.clone().add(
                part.offsetX() + offset.getX(),
                part.offsetY() + yOffset + offset.getY() + part.scaleY() * 0.5,
                part.offsetZ() + offset.getZ());
    }

    private void refreshBossBarPlayers() {
        if (bossBar == null) {
            return;
        }
        for (UUID playerId : participants) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.getWorld().equals(world) && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    private void updateBossBar() {
        if (activeBoss == null || bossBar == null) {
            return;
        }
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, activeBoss.health() / activeBoss.maxHealth())));
        bossBar.setTitle(activeBoss.type().name() + " " + Math.max(0, (int) Math.ceil(activeBoss.health())) + " HP");
    }

    private double scaledHealth(HallsBossType type) {
        int aliveCount = Math.max(1, participants.size());
        return type.health() * Math.pow(type.multiplayerHpBoost(), Math.max(0, aliveCount - 1));
    }

    private String weightedMonster(Map<String, Integer> pool) {
        if (pool.isEmpty()) {
            return "zombie";
        }
        int total = pool.values().stream().mapToInt(Integer::intValue).sum();
        int roll = random.nextInt(Math.max(1, total));
        for (Map.Entry<String, Integer> entry : pool.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return pool.keySet().iterator().next();
    }

    private double randomOffset(double radius) {
        return (random.nextDouble() - 0.5) * radius * 2.0;
    }

    private void cancelTasks() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (attackTask != null) {
            attackTask.cancel();
            attackTask = null;
        }
        if (defeatedOpenTask != null) {
            defeatedOpenTask.cancel();
            defeatedOpenTask = null;
        }
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    interface BlockSetter {
        void setBlock(int x, int y, int z, Material material, BlockFace facing);
    }

    record DoorSeal(int minX, int maxX, int y, int minZ, int maxZ, Material material) {
        Location center(World world) {
            return new Location(world, (minX + maxX) / 2.0 + 0.5, y + 1.5, (minZ + maxZ) / 2.0 + 0.5);
        }
    }

    private enum Attack {
        SPAWN,
        JUMP,
        X_BLAST
    }

    private final class TimedAttack implements Runnable {
        private final int durationTicks;
        private final Runnable tickAction;
        private final Runnable finishedAction;
        private int ticks;
        private BukkitTask task;

        private TimedAttack(int durationTicks, Runnable tickAction, Runnable finishedAction) {
            this.durationTicks = Math.max(1, durationTicks);
            this.tickAction = tickAction;
            this.finishedAction = finishedAction;
            this.task = Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
        }

        @Override
        public void run() {
            if (activeBoss == null || !activeBoss.active()) {
                task.cancel();
                return;
            }
            if (ticks++ < durationTicks) {
                tickAction.run();
                return;
            }
            task.cancel();
            finishedAction.run();
        }
    }

    private final class JumpSlam extends org.bukkit.scheduler.BukkitRunnable {
        private final HallsBossType.Overdrive config;
        private int ticks;

        private JumpSlam(HallsBossType.Overdrive config) {
            this.config = config;
        }

        @Override
        public void run() {
            if (activeBoss == null || !activeBoss.active()) {
                cancel();
                return;
            }
            ticks++;
            if (ticks <= 10) {
                animateDisplays(activeBoss.yaw(), ticks / 10.0 * 3.0, new Vector());
                return;
            }
            if (ticks <= 18) {
                animateDisplays(activeBoss.yaw(), (18 - ticks) / 8.0 * 3.0, new Vector());
                return;
            }
            world.playSound(activeBoss.location(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.55f);
            new Shockwave(config.shockwaveDamage(), config.shockwaveSpeedBlocksPerSecond()).runTaskTimer(plugin, 1L, 2L);
            scheduleNextAttack(config.jumpCooldownTicks());
            cancel();
        }
    }

    private final class Shockwave extends org.bukkit.scheduler.BukkitRunnable {
        private final double damage;
        private final double radiusStep;
        private double radius = 1.0;
        private final Set<UUID> hit = new java.util.HashSet<>();

        private Shockwave(double damage, double speedBlocksPerSecond) {
            this.damage = damage;
            this.radiusStep = Math.max(0.05, speedBlocksPerSecond * 2.0 / 20.0);
        }

        @Override
        public void run() {
            if (activeBoss == null) {
                cancel();
                return;
            }
            Location center = activeBoss.location().clone().add(0.0, 0.08, 0.0);
            for (double angle = 0.0; angle < Math.PI * 2.0; angle += Math.PI / 18.0) {
                Location point = center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
                world.spawnParticle(Particle.DUST_PLUME, point, 1, 0.03, 0.02, 0.03, 0.0);
            }
            for (Player player : alivePlayers()) {
                if (hit.contains(player.getUniqueId())) {
                    continue;
                }
                double horizontal = Math.hypot(player.getLocation().getX() - center.getX(), player.getLocation().getZ() - center.getZ());
                boolean jumped = player.getLocation().getY() - Math.floor(player.getLocation().getY()) > 0.38 || player.getVelocity().getY() > 0.12;
                if (Math.abs(horizontal - radius) <= 0.85 && !jumped) {
                    hit.add(player.getUniqueId());
                    player.damage(damage);
                }
            }
            radius += radiusStep;
            if (radius > 13.0) {
                cancel();
            }
        }
    }

    private void sendCooldownMessage(Player player) {
        long now = world.getFullTime();
        Long previous = cooldownMessageTicks.get(player.getUniqueId());
        if (previous != null && now - previous < MELEE_COOLDOWN_MESSAGE_TICKS) {
            return;
        }
        cooldownMessageTicks.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text("Time your swings to damage the boss.", NamedTextColor.GRAY));
    }

    private final class RetractAnimation extends org.bukkit.scheduler.BukkitRunnable {
        private int ticks;

        @Override
        public void run() {
            if (activeBoss == null || ticks++ > 20) {
                cancel();
                return;
            }
            animateDisplays(activeBoss.yaw() + ticks * 8.0f, -ticks / 20.0, new Vector());
            world.spawnParticle(Particle.SMOKE, activeBoss.location().clone().add(0.0, 0.5, 0.0),
                    8, 1.4, 0.2, 1.4, 0.03);
        }
    }

    private static final class ActiveBoss {
        private final HallsBossType type;
        private final Location location;
        private final List<UUID> displayIds;
        private final UUID hitboxId;
        private final DoorSeal seal;
        private final double maxHealth;
        private double health;
        private boolean active;
        private boolean defeated;
        private float yaw;
        private int idleTicks;
        private Attack lastAttack;

        private ActiveBoss(HallsBossType type,
                           Location location,
                           List<UUID> displayIds,
                           UUID hitboxId,
                           DoorSeal seal,
                           double health,
                           double maxHealth) {
            this.type = type;
            this.location = location;
            this.displayIds = List.copyOf(displayIds);
            this.hitboxId = hitboxId;
            this.seal = seal;
            this.health = health;
            this.maxHealth = maxHealth;
        }

        private HallsBossType type() {
            return type;
        }

        private Location location() {
            return location;
        }

        private List<UUID> displayIds() {
            return displayIds;
        }

        private UUID hitboxId() {
            return hitboxId;
        }

        private DoorSeal seal() {
            return seal;
        }

        private double health() {
            return health;
        }

        private void setHealth(double health) {
            this.health = health;
        }

        private double maxHealth() {
            return maxHealth;
        }

        private boolean active() {
            return active;
        }

        private boolean defeated() {
            return defeated;
        }

        private void setDefeated(boolean defeated) {
            this.defeated = defeated;
        }

        private void setActive(boolean active) {
            this.active = active;
        }

        private float yaw() {
            return yaw;
        }

        private void setYaw(float yaw) {
            this.yaw = yaw;
        }

        private int idleTicks() {
            return idleTicks;
        }

        private void setIdleTicks(int idleTicks) {
            this.idleTicks = idleTicks;
        }

        private Attack lastAttack() {
            return lastAttack;
        }

        private void setLastAttack(Attack lastAttack) {
            this.lastAttack = lastAttack;
        }
    }
}
