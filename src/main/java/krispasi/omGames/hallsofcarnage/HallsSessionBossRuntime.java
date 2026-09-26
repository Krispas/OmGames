package krispasi.omGames.hallsofcarnage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
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
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

final class HallsSessionBossRuntime {
    private static final String BOSS_TAG = "omgames_hoc_boss";
    private static final double HIT_FLASH_RADIUS = 1.8;

    private final JavaPlugin plugin;
    private final World world;
    private final Set<UUID> participants;
    private final Map<String, HallsBossType> bossTypes;
    private final Predicate<UUID> aliveParticipantPredicate;
    private final MinionSpawner monsterSpawner;
    private final BossDropper bossDropper;
    private final BlockSetter blockSetter;
    private final Runnable bossMinionClearCallback;
    private final Runnable defeatedCallback;
    private final NamespacedKey bossIdKey;
    private final Random random = new Random();

    private ActiveBoss activeBoss;
    private BossBar bossBar;
    private BukkitTask tickTask;
    private BukkitTask attackTask;
    private BukkitTask defeatedOpenTask;
    private int activeFloorDifficulty = 40;

    HallsSessionBossRuntime(JavaPlugin plugin,
                            World world,
                            Set<UUID> participants,
                            Map<String, HallsBossType> bossTypes,
                            Predicate<UUID> aliveParticipantPredicate,
                            MinionSpawner monsterSpawner,
                            BossDropper bossDropper,
                            BlockSetter blockSetter,
                            Runnable bossMinionClearCallback,
                            Runnable defeatedCallback) {
        this.plugin = plugin;
        this.world = world;
        this.participants = participants;
        this.bossTypes = bossTypes == null ? Map.of() : Map.copyOf(bossTypes);
        this.aliveParticipantPredicate = aliveParticipantPredicate == null ? id -> true : aliveParticipantPredicate;
        this.monsterSpawner = monsterSpawner == null ? (id, location) -> null : monsterSpawner;
        this.bossDropper = bossDropper == null ? (location, randomScrap) -> { } : bossDropper;
        this.blockSetter = blockSetter == null ? (x, y, z, material, face) -> { } : blockSetter;
        this.bossMinionClearCallback = bossMinionClearCallback == null ? () -> { } : bossMinionClearCallback;
        this.defeatedCallback = defeatedCallback == null ? () -> { } : defeatedCallback;
        this.bossIdKey = new NamespacedKey(plugin, "hoc_boss_id");
    }

    void prepare(String bossId, Location location, DoorSeal seal, int floorDifficulty) {
        clear();
        HallsBossType type = bossTypes.get(normalizeId(bossId));
        if (type == null || location == null || !world.equals(location.getWorld())) {
            return;
        }
        activeFloorDifficulty = Math.max(0, floorDifficulty);
        List<UUID> displayIds = spawnDisplays(type, location, 0.0f, 0.0);
        Interaction hitbox = world.spawn(location.clone().add(0.0, 0.1, 0.0), Interaction.class, entity -> {
            setHitboxSize(entity, type.overdrive().initialScaleMultiplier());
            entity.setResponsive(true);
            entity.setPersistent(false);
            entity.addScoreboardTag(BOSS_TAG);
            entity.getPersistentDataContainer().set(bossIdKey, PersistentDataType.STRING, type.id());
        });
        activeBoss = new ActiveBoss(type, location.clone(), displayIds, hitbox.getUniqueId(), seal,
                scaledHealth(type), scaledHealth(type));
        activeBoss.setScaleMultiplier(type.overdrive().initialScaleMultiplier());
        animateDisplays(activeBoss.yaw(), 0.0, new Vector());
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

    AttackResult handleAttack(Player player, Entity entity, double damage) {
        if (player == null || entity == null || activeBoss == null || !isBossEntity(entity)) {
            return AttackResult.unhandledResult();
        }
        if (!participants.contains(player.getUniqueId()) || !aliveParticipantPredicate.test(player.getUniqueId())) {
            player.sendActionBar(Component.text("Ghosts cannot harm the boss.", NamedTextColor.GRAY));
            return AttackResult.handledResult();
        }
        if (!activeBoss.active()) {
            activate(player);
            return AttackResult.handledResult();
        }
        return damage(Math.max(1.0, damage), player.getLocation(), false)
                ? AttackResult.damagedResult()
                : AttackResult.handledResult();
    }

    AttackResult handleProjectileHit(Player shooter, Entity entity, double damage) {
        if (shooter == null || entity == null || activeBoss == null || !isBossEntity(entity) || !activeBoss.active()) {
            return AttackResult.unhandledResult();
        }
        if (!participants.contains(shooter.getUniqueId()) || !aliveParticipantPredicate.test(shooter.getUniqueId())) {
            return AttackResult.handledResult();
        }
        return damage(Math.max(1.0, damage), entity.getLocation(), false)
                ? AttackResult.damagedResult()
                : AttackResult.handledResult();
    }

    boolean handleAreaDamage(Player source, Location center, double radius, double damage) {
        if (source == null || center == null || activeBoss == null || !activeBoss.active()
                || !participants.contains(source.getUniqueId()) || !aliveParticipantPredicate.test(source.getUniqueId())
                || radius <= 0.0 || damage <= 0.0) {
            return false;
        }
        Location bossCenter = activeBoss.location().clone().add(0.0, 2.5, 0.0);
        double effectiveRadius = radius + 2.5;
        if (bossCenter.distanceSquared(center) > effectiveRadius * effectiveRadius) {
            return false;
        }
        damage(damage, center, false);
        return true;
    }

    void applyPoison(Player source, int durationTicks, int amplifier) {
        if (source == null || activeBoss == null || !activeBoss.active()
                || !participants.contains(source.getUniqueId()) || !aliveParticipantPredicate.test(source.getUniqueId())
                || durationTicks <= 0) {
            return;
        }
        int pulses = Math.max(1, durationTicks / 20);
        double pulseDamage = Math.max(1.0, 1.0 + amplifier);
        new org.bukkit.scheduler.BukkitRunnable() {
            private int remaining = pulses;

            @Override
            public void run() {
                if (activeBoss == null || !activeBoss.active() || remaining-- <= 0) {
                    cancel();
                    return;
                }
                Location center = activeBoss.location().clone().add(0.0, 2.4, 0.0);
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, center, 35, 1.7, 1.1, 1.7, 0.03);
                damage(pulseDamage, source.getLocation(), true);
            }
        }.runTaskTimer(plugin, 20L, 20L);
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

    private boolean damage(double amount, Location source, boolean bypassInvulnerability) {
        if (activeBoss == null || !activeBoss.active()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (!bypassInvulnerability && now < activeBoss.invulnerableUntilMillis()) {
            updateInvulnerabilityFeedback(now);
            return false;
        }
        pruneBossMinions();
        if (activeBoss.hasLivingMinions()) {
            Location center = activeBoss.location().clone().add(0.0, 2.5, 0.0);
            world.spawnParticle(Particle.ENCHANT, center, 35, 2.1, 1.5, 2.1, 0.05);
            world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 0.55f, 0.45f);
            return false;
        }
        if (!bypassInvulnerability) {
            activeBoss.setInvulnerableUntilMillis(now + activeBoss.type().directHitInvulnerabilityMillis());
            updateInvulnerabilityFeedback(now);
        }
        activeBoss.setHealth(activeBoss.health() - amount);
        checkEnrage();
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
        return true;
    }

    record AttackResult(boolean handled, boolean damaged) {
        private static AttackResult unhandledResult() {
            return new AttackResult(false, false);
        }

        private static AttackResult handledResult() {
            return new AttackResult(true, false);
        }

        private static AttackResult damagedResult() {
            return new AttackResult(true, true);
        }
    }

    private void defeat() {
        if (activeBoss == null) {
            return;
        }
        cancelTasks();
        bossMinionClearCallback.run();
        Location center = activeBoss.location().clone().add(0.0, 2.2, 0.0);
        bossDropper.drop(center, activeBoss.type().drops().randomScrap());
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
        scheduleNextAttackUnscaled(30L);
    }

    private void tick() {
        if (activeBoss == null || !activeBoss.active()) {
            return;
        }
        pruneBossMinions();
        tickBossMinionCooldowns();
        tickSummonReadiness();
        refreshBossBarPlayers();
        updateBossBar();
        updateInvulnerabilityFeedback(System.currentTimeMillis());
        activeBoss.setIdleTicks(activeBoss.idleTicks() + 1);
        double bob = Math.sin(activeBoss.idleTicks() / 8.0) * 0.04;
        animateDisplays("idle", activeBoss.idleTicks(), activeBoss.yaw(), bob, new Vector());
        Location core = activeBoss.location().clone().add(0.0, 2.45 + bob, 0.0);
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, core, 2, 1.9, 1.5, 1.9, 0.0);
        if (activeBoss.idleTicks() % 8 == 0) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, core, 8, 2.0, 1.4, 2.0, 0.04);
        }
    }

    private void scheduleNextAttack(long delayTicks) {
        scheduleNextAttackUnscaled(scaledAttackCooldownTicks(delayTicks));
    }

    private void scheduleNextAttackUnscaled(long delayTicks) {
        if (attackTask != null) {
            attackTask.cancel();
        }
        attackTask = Bukkit.getScheduler().runTaskLater(plugin, this::runNextAttack, Math.max(1L, delayTicks));
    }

    private long scaledAttackCooldownTicks(long baseTicks) {
        return Math.max(1L, Math.round(baseTicks * attackCooldownMultiplier(activeFloorDifficulty)));
    }

    static double attackCooldownMultiplier(int difficulty) {
        if (difficulty <= 20) {
            return Math.max(0.1, 0.7 + (difficulty - 20) * 0.015);
        }
        if (difficulty <= 40) {
            return 0.7 + (difficulty - 20) * 0.015;
        }
        return 1.0 + (difficulty - 40) * 0.0075;
    }

    private void runNextAttack() {
        if (activeBoss == null || !activeBoss.active()) {
            return;
        }
        Attack attack = chooseAttack();
        switch (attack) {
            case SPAWN -> {
                if (isArchaicGuard()) {
                    runArchaicSpawnAttack();
                } else {
                    runSpawnAttack();
                }
            }
            case JUMP -> {
                if (isArchaicGuard()) {
                    runArchaicShockwaveAttack();
                } else {
                    runJumpAttack();
                }
            }
            case X_BLAST -> runXBlastAttack();
            case MISSILE -> runMissileAttack();
            case WALLS -> runWallAttack();
            case REPOSITION -> runRepositionAttack();
        }
    }

    private Attack chooseAttack() {
        if (isArchaicGuard()) {
            List<Attack> attacks = new ArrayList<>(List.of(Attack.MISSILE, Attack.JUMP, Attack.WALLS, Attack.SPAWN, Attack.REPOSITION));
            if (activeBoss.lastAttack() != null && attacks.size() > 1) {
                attacks.remove(activeBoss.lastAttack());
            }
            return attacks.get(random.nextInt(attacks.size()));
        }
        List<Attack> attacks = new ArrayList<>(List.of(Attack.SPAWN, Attack.JUMP, Attack.X_BLAST));
        if (activeBoss.lastAttack() != null && attacks.size() > 1) {
            attacks.remove(activeBoss.lastAttack());
        }
        if (attacks.contains(Attack.SPAWN)
                && activeBoss.summonReadyTicks() >= 100
                && canSpawnMinions(activeBoss.type().overdrive())) {
            attacks.add(Attack.SPAWN);
            attacks.add(Attack.SPAWN);
        }
        return attacks.get(random.nextInt(attacks.size()));
    }

    private boolean isArchaicGuard() {
        return activeBoss != null && normalizeId(activeBoss.type().ai()).equals("archaic_guard");
    }

    private void runSpawnAttack() {
        HallsBossType.Overdrive config = activeBoss.type().overdrive();
        pruneBossMinions();
        if (!canSpawnMinions(config)) {
            scheduleNextAttack(config.spawnCooldownTicks());
            return;
        }
        activeBoss.setLastAttack(Attack.SPAWN);
        activeBoss.setSummonReadyTicks(0);
        activeBoss.setAttackAnimationTicks(0);
        new TimedAttack(config.spawnChargeTicks(), () -> {
            activeBoss.setYaw(activeBoss.yaw() + 18.0f);
            Location center = activeBoss.location().clone().add(0.0, 2.7, 0.0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, center, 5, 1.9, 1.4, 1.9, 0.04);
            animateDisplays("spawn_charge", activeBoss.attackAnimationTicks(), activeBoss.yaw(), 0.1, new Vector());
        }, () -> {
            int count = spawnCount(config);
            for (int i = 0; i < count; i++) {
                String monsterId = weightedMonster(activeBoss.type().weightedSpawnPool());
                if (!canSpawnMonsterType(config, monsterId)) {
                    continue;
                }
                Location spawn = activeBoss.location().clone().add(randomOffset(3.5), 0.2, randomOffset(3.5));
                UUID minionId = monsterSpawner.spawn(monsterId, spawn);
                if (minionId != null) {
                    activeBoss.registerMinion(minionId, monsterId);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, spawn.clone().add(0.0, 0.8, 0.0), 16, 0.4, 0.5, 0.4, 0.02);
                }
            }
            world.playSound(activeBoss.location(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.3f, activeBoss.enraged() ? 0.75f : 1.05f);
            scheduleNextAttack(config.spawnCooldownTicks());
        });
    }

    private void runJumpAttack() {
        HallsBossType.Overdrive config = activeBoss.type().overdrive();
        activeBoss.setLastAttack(Attack.JUMP);
        runJumpChain(config, jumpChainCount(config));
    }

    private void runJumpChain(HallsBossType.Overdrive config, int remaining) {
        if (activeBoss == null || !activeBoss.active()) {
            return;
        }
        activeBoss.setAttackAnimationTicks(0);
        new TimedAttack(config.jumpReadyTicks(), () -> {
            world.spawnParticle(Particle.DUST_PLUME, activeBoss.location().clone().add(0.0, 0.15, 0.0),
                    10, 1.7, 0.05, 1.7, 0.03);
            animateDisplays("jump_ready", activeBoss.attackAnimationTicks(), activeBoss.yaw(), -0.12, new Vector());
        }, () -> new JumpSlam(config, remaining).runTaskTimer(plugin, 1L, 1L));
    }

    private void runXBlastAttack() {
        HallsBossType.Overdrive config = activeBoss.type().overdrive();
        activeBoss.setLastAttack(Attack.X_BLAST);
        activeBoss.setXBlastYaw(random.nextFloat() * 360.0f);
        runXBlastChain(config, activeBoss.enraged() ? config.enragedXBlastChains() : 1);
    }

    private void runXBlastChain(HallsBossType.Overdrive config, int remaining) {
        if (activeBoss == null || !activeBoss.active()) {
            return;
        }
        activeBoss.setAttackAnimationTicks(0);
        new TimedAttack(config.xBlastMoveTicks(), () -> {
            activeBoss.setYaw(activeBoss.yaw() + 24.0f);
            animateDisplays("x_blast_move", activeBoss.attackAnimationTicks(), activeBoss.yaw(), 0.08, new Vector());
        }, () -> new TimedAttack(config.xBlastChargeTicks(), () -> renderXBlastWarning(false), () -> {
            fireXBlast(config.xBlastDamage());
            if (remaining > 1) {
                runXBlastChain(config, remaining - 1);
            } else {
                scheduleNextAttack(config.xBlastCooldownTicks());
            }
        }));
    }

    private void runMissileAttack() {
        HallsBossType.ArchaicGuard config = activeBoss.type().archaicGuard();
        activeBoss.setLastAttack(Attack.MISSILE);
        runMissileChain(config, activeBoss.enraged() ? 2 : 1);
    }

    private void runMissileChain(HallsBossType.ArchaicGuard config, int remaining) {
        if (activeBoss == null || !activeBoss.active()) {
            return;
        }
        activeBoss.setAttackAnimationTicks(0);
        final Location[] target = {nearestAlivePlayerLocation()};
        new TimedAttack(config.missileAimTicks(), () -> {
            activeBoss.setYaw(activeBoss.yaw() + 6.0f);
            Location next = nearestAlivePlayerLocation();
            if (next != null) {
                target[0] = next;
            }
            renderMissileTelegraph(target[0], false);
            animateDisplays("missile_aim", activeBoss.attackAnimationTicks(), activeBoss.yaw(), 0.05, new Vector());
        }, () -> {
            Location locked = target[0] == null ? activeBoss.location().clone() : target[0].clone();
            new TimedAttack(config.missileLockTicks(), () -> {
                renderMissileTelegraph(locked, true);
                animateDisplays("missile_lock", activeBoss.attackAnimationTicks(), activeBoss.yaw(), 0.08, new Vector());
            }, () -> {
                fireMissile(locked, config.missileRadius(), config.missileDamage());
                if (remaining > 1) {
                    runMissileChain(config, remaining - 1);
                } else {
                    scheduleNextAttack(config.missileCooldownTicks());
                }
            });
        });
    }

    private void renderMissileTelegraph(Location target, boolean locked) {
        if (target == null) {
            return;
        }
        Particle particle = locked ? Particle.FLAME : Particle.CRIT;
        world.spawnParticle(particle, target.clone().add(0.0, 0.15, 0.0), locked ? 18 : 9,
                locked ? 0.55 : 0.25, 0.04, locked ? 0.55 : 0.25, 0.01);
        if (activeBoss != null) {
            Location face = activeBoss.location().clone().add(0.0, 2.8, 0.0);
            Vector delta = target.toVector().subtract(face.toVector());
            if (delta.lengthSquared() > 0.01) {
                Vector step = delta.normalize().multiply(0.55);
                Location point = face.clone();
                for (int i = 0; i < 10; i++) {
                    point.add(step);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.03, 0.03, 0.03, 0.0);
                }
            }
        }
    }

    private void fireMissile(Location target, double radius, double damage) {
        world.spawnParticle(Particle.EXPLOSION, target, 2, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticle(Particle.FLAME, target, 70, radius * 0.35, 0.25, radius * 0.35, 0.04);
        world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 1.0f);
        double radiusSquared = radius * radius;
        for (Player player : alivePlayers()) {
            if (player.getLocation().distanceSquared(target) <= radiusSquared) {
                player.damage(damage);
            }
        }
    }

    private void runArchaicShockwaveAttack() {
        HallsBossType.ArchaicGuard config = activeBoss.type().archaicGuard();
        activeBoss.setLastAttack(Attack.JUMP);
        runArchaicShockwaveChain(config, archaicShockwaveCount(config));
    }

    private void runArchaicShockwaveChain(HallsBossType.ArchaicGuard config, int remaining) {
        if (activeBoss == null || !activeBoss.active()) {
            return;
        }
        activeBoss.setAttackAnimationTicks(0);
        new TimedAttack(config.shockwaveChargeTicks(), () -> {
            Location ground = activeBoss.location().clone().add(0.0, 0.15, 0.0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, ground, 12, 0.6, 0.08, 0.6, 0.05);
            animateDisplays("shockwave_charge", activeBoss.attackAnimationTicks(), activeBoss.yaw(), -0.05, new Vector());
        }, () -> {
            world.playSound(activeBoss.location(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.65f);
            new Shockwave(config.shockwaveDamage(), config.shockwaveSpeedBlocksPerSecond()).runTaskTimer(plugin, 1L, 2L);
            if (remaining > 1) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> runArchaicShockwaveChain(config, remaining - 1), 20L);
            } else {
                scheduleNextAttack(config.shockwaveCooldownTicks());
            }
        });
    }

    private void runWallAttack() {
        HallsBossType.ArchaicGuard config = activeBoss.type().archaicGuard();
        activeBoss.setLastAttack(Attack.WALLS);
        runWallChain(config, archaicWallCount(config));
    }

    private void runWallChain(HallsBossType.ArchaicGuard config, int remaining) {
        if (activeBoss == null || !activeBoss.active()) {
            return;
        }
        activeBoss.setAttackAnimationTicks(0);
        double rotation = random.nextDouble() * 360.0;
        double safeDegrees = activeBoss.enraged() ? config.enragedWallSafeDegrees() : config.normalWallSafeDegrees();
        new TimedAttack(config.wallChargeTicks(), () -> {
            world.spawnParticle(Particle.EXPLOSION, activeBoss.location().clone().add(0.0, 2.2, 0.0),
                    1, 1.8, 1.1, 1.8, 0.0);
            renderWallWarning(rotation, safeDegrees, false);
            animateDisplays("wall_charge", activeBoss.attackAnimationTicks(), activeBoss.yaw() + activeBoss.attackAnimationTicks() * 5.0f,
                    0.04, new Vector());
        }, () -> {
            new WallWave(config.wallDamage(), config.wallSpeedBlocksPerSecond(), rotation, safeDegrees).runTaskTimer(plugin, 1L, 2L);
            if (remaining > 1) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> runWallChain(config, remaining - 1), config.wallGapTicks());
            } else {
                scheduleNextAttack(config.wallCooldownTicks());
            }
        });
    }

    private void renderWallWarning(double rotationDegrees, double safeDegrees, boolean damaging) {
        if (activeBoss == null) {
            return;
        }
        Location center = activeBoss.location().clone().add(0.0, 0.12, 0.0);
        for (double radius = 2.0; radius <= 11.5; radius += 1.5) {
            for (double degrees = 0.0; degrees < 360.0; degrees += 8.0) {
                if (isSafeWallAngle(degrees, rotationDegrees, safeDegrees)) {
                    continue;
                }
                double radians = Math.toRadians(degrees);
                Location point = center.clone().add(Math.cos(radians) * radius, 0.0, Math.sin(radians) * radius);
                world.spawnParticle(damaging ? Particle.FLAME : Particle.DUST_PLUME, point, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private void runArchaicSpawnAttack() {
        HallsBossType.ArchaicGuard config = activeBoss.type().archaicGuard();
        activeBoss.setLastAttack(Attack.SPAWN);
        activeBoss.setAttackAnimationTicks(0);
        new TimedAttack(config.spawnRiseTicks(), () -> {
            double progress = activeBoss.attackAnimationTicks() / (double) config.spawnRiseTicks();
            animateDisplays("spawn_slam", activeBoss.attackAnimationTicks(), activeBoss.yaw(), Math.sin(progress * Math.PI) * 2.0, new Vector());
            world.spawnParticle(Particle.CLOUD, activeBoss.location().clone().add(0.0, 0.15, 0.0), 6, 1.5, 0.08, 1.5, 0.02);
        }, () -> {
            int count = config.minSpawnCount() + random.nextInt(config.maxSpawnCount() - config.minSpawnCount() + 1);
            for (int i = 0; i < count; i++) {
                String monsterId = weightedMonster(activeBoss.type().archaicSpawnPool(activeBoss.enraged()));
                Location spawn = activeBoss.location().clone().add(randomOffset(4.0), 5.0, randomOffset(4.0));
                world.spawnParticle(Particle.EXPLOSION, spawn, 1, 0.35, 0.35, 0.35, 0.0);
                UUID minionId = monsterSpawner.spawn(monsterId, spawn);
                if (minionId != null) {
                    activeBoss.registerMinion(minionId, monsterId);
                    if (!activeBoss.enraged() && normalizeId(monsterId).equals("bedrock_walker")) {
                        halveMonsterHealth(minionId);
                    }
                }
            }
            world.playSound(activeBoss.location(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.1f, 0.6f);
            scheduleNextAttack(config.spawnCooldownTicks());
        });
    }

    private void runRepositionAttack() {
        HallsBossType.ArchaicGuard config = activeBoss.type().archaicGuard();
        activeBoss.setLastAttack(Attack.REPOSITION);
        Location from = activeBoss.location().clone();
        Location target = randomBossArenaLocation(config.repositionRadius());
        PotionEffectType effect = activeBoss.enraged() ? PotionEffectType.WITHER : PotionEffectType.POISON;
        new RepositionMove(from, target, config, effect).runTaskTimer(plugin, 1L, 1L);
    }

    private void renderXBlastWarning(boolean damaging) {
        if (activeBoss == null) {
            return;
        }
        Location center = activeBoss.location().clone().add(0.0, 1.0, 0.0);
        for (Vector axis : xBlastAxes()) {
            for (double distance = 1.0; distance <= 12.0; distance += 0.75) {
                Location positive = center.clone().add(axis.clone().multiply(distance));
                Location negative = center.clone().add(axis.clone().multiply(-distance));
                world.spawnParticle(damaging ? Particle.FLAME : Particle.DUST_PLUME, positive, 1, 0.08, 0.08, 0.08, 0.0);
                world.spawnParticle(damaging ? Particle.FLAME : Particle.DUST_PLUME, negative, 1, 0.08, 0.08, 0.08, 0.0);
            }
        }
    }

    private List<Vector> xBlastAxes() {
        if (activeBoss == null) {
            return List.of(new Vector(1.0, 0.0, 0.0), new Vector(0.0, 0.0, 1.0));
        }
        double radians = Math.toRadians(activeBoss.xBlastYaw());
        Vector first = new Vector(Math.cos(radians), 0.0, Math.sin(radians));
        Vector second = new Vector(-first.getZ(), 0.0, first.getX());
        return List.of(first, second);
    }

    private boolean isInsideXBlastBeam(Location playerLocation, Location center) {
        Vector relative = playerLocation.toVector().subtract(center.toVector());
        for (Vector axis : xBlastAxes()) {
            double along = relative.dot(axis);
            if (Math.abs(along) > 12.5) {
                continue;
            }
            Vector closest = axis.clone().multiply(along);
            Vector perpendicular = relative.clone().subtract(closest);
            if (perpendicular.length() <= 1.25) {
                return true;
            }
        }
        return false;
    }

    private void fireXBlast(double damage) {
        renderXBlastWarning(true);
        world.playSound(activeBoss.location(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.7f);
        Location center = activeBoss.location().clone().add(0.0, 1.0, 0.0);
        for (Player player : alivePlayers()) {
            Location playerLocation = player.getLocation();
            if (Math.abs(playerLocation.getY() - center.getY()) <= 2.8 && isInsideXBlastBeam(playerLocation, center)) {
                player.damage(damage);
            }
        }
    }

    private void checkEnrage() {
        if (activeBoss == null || activeBoss.enraged() || activeBoss.health() > activeBoss.maxHealth() * enrageThreshold()) {
            return;
        }
        activeBoss.setEnraged(true);
        activeBoss.setAttackGeneration(activeBoss.attackGeneration() + 1);
        if (attackTask != null) {
            attackTask.cancel();
            attackTask = null;
        }
        updateHitboxSize();
        world.playSound(activeBoss.location(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.3f, isArchaicGuard() ? 0.45f : 0.7f);
        for (UUID playerId : participants) {
            Player participant = Bukkit.getPlayer(playerId);
            if (participant != null && participant.getWorld().equals(world)) {
                participant.sendTitle(isArchaicGuard() ? "Ancient Protocol" : "Overdrive",
                        isArchaicGuard() ? "The guard vents corrupted fumes." : "The spawner overclocks.",
                        5, 45, 10);
            }
        }
        world.spawnParticle(isArchaicGuard() ? Particle.SCULK_SOUL : Particle.ELECTRIC_SPARK,
                activeBoss.location().clone().add(0.0, 2.3, 0.0), 100, 2.3, 1.7, 2.3, 0.08);
        new EnrageAnimation(activeBoss.type().overdrive()).runTaskTimer(plugin, 1L, 1L);
        scheduleNextAttackUnscaled(50L);
    }

    private double enrageThreshold() {
        if (isArchaicGuard()) {
            return activeBoss.type().archaicGuard().phaseThreshold();
        }
        return 0.5;
    }

    private void pruneBossMinions() {
        if (activeBoss == null) {
            return;
        }
        for (Map.Entry<UUID, String> entry : Map.copyOf(activeBoss.minions()).entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity != null && entity.isValid() && !entity.isDead()) {
                continue;
            }
            activeBoss.removeMinion(entry.getKey());
            activeBoss.startMinionCooldown(entry.getValue(), minionCooldownTicks(activeBoss.type().overdrive()));
        }
        if (activeBoss.enraged() && activeBoss.awaitingGroupClear() && !activeBoss.hasLivingMinions()) {
            activeBoss.setAwaitingGroupClear(false);
            activeBoss.setGroupCooldownTicks(minionCooldownTicks(activeBoss.type().overdrive()));
        }
    }

    private void tickBossMinionCooldowns() {
        if (activeBoss == null) {
            return;
        }
        activeBoss.tickMinionCooldowns();
        activeBoss.setGroupCooldownTicks(Math.max(0, activeBoss.groupCooldownTicks() - 1));
    }

    private void tickSummonReadiness() {
        if (activeBoss == null) {
            return;
        }
        if (canSpawnMinions(activeBoss.type().overdrive())) {
            activeBoss.setSummonReadyTicks(activeBoss.summonReadyTicks() + 1);
        } else {
            activeBoss.setSummonReadyTicks(0);
        }
    }

    private boolean canSpawnMinions(HallsBossType.Overdrive config) {
        if (activeBoss == null || config.maxAliveMinions() <= 0) {
            return false;
        }
        if (activeBoss.hasLivingMinions() && activeBoss.minions().size() >= config.maxAliveMinions()) {
            return false;
        }
        if (activeBoss.enraged()) {
            return !activeBoss.awaitingGroupClear() && activeBoss.groupCooldownTicks() <= 0 && !activeBoss.hasLivingMinions();
        }
        return true;
    }

    private boolean canSpawnMonsterType(HallsBossType.Overdrive config, String monsterId) {
        if (activeBoss == null || activeBoss.minions().size() >= config.maxAliveMinions()) {
            return false;
        }
        return activeBoss.enraged() || activeBoss.minionCooldownTicks(normalizeId(monsterId)) <= 0;
    }

    private int spawnCount(HallsBossType.Overdrive config) {
        int room = Math.max(0, config.maxAliveMinions() - activeBoss.minions().size());
        if (room <= 0) {
            return 0;
        }
        if (activeBoss.enraged()) {
            activeBoss.setAwaitingGroupClear(true);
            return room;
        }
        int rolled = config.minSpawnCount() + random.nextInt(config.maxSpawnCount() - config.minSpawnCount() + 1);
        return Math.min(room, rolled);
    }

    private int minionCooldownTicks(HallsBossType.Overdrive config) {
        int cooldown = config.minionRespawnCooldownTicks();
        if (activeBoss != null && activeBoss.enraged()) {
            cooldown = (int) Math.round(cooldown * config.lowHealthMinionCooldownMultiplier());
        }
        return Math.max(1, cooldown);
    }

    private int jumpChainCount(HallsBossType.Overdrive config) {
        int min = activeBoss != null && activeBoss.enraged() ? config.enragedShockwaveChainMin() : config.normalShockwaveChainMin();
        int max = activeBoss != null && activeBoss.enraged() ? config.enragedShockwaveChainMax() : config.normalShockwaveChainMax();
        return min + random.nextInt(max - min + 1);
    }

    private int archaicShockwaveCount(HallsBossType.ArchaicGuard config) {
        int min = activeBoss != null && activeBoss.enraged() ? config.enragedShockwaveMin() : config.normalShockwaveMin();
        int max = activeBoss != null && activeBoss.enraged() ? config.enragedShockwaveMax() : config.normalShockwaveMax();
        return min + random.nextInt(max - min + 1);
    }

    private int archaicWallCount(HallsBossType.ArchaicGuard config) {
        int min = activeBoss != null && activeBoss.enraged() ? config.enragedWallMin() : config.normalWallMin();
        int max = activeBoss != null && activeBoss.enraged() ? config.enragedWallMax() : config.normalWallMax();
        return min + random.nextInt(max - min + 1);
    }

    private Location nearestAlivePlayerLocation() {
        if (activeBoss == null) {
            return null;
        }
        Player nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : alivePlayers()) {
            double distance = player.getLocation().distanceSquared(activeBoss.location());
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = player;
            }
        }
        return nearest == null ? null : nearest.getLocation().clone();
    }

    private Location randomBossArenaLocation(double radius) {
        if (activeBoss == null) {
            return null;
        }
        Location origin = activeBoss.location();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = Math.max(2.0, radius * (0.35 + random.nextDouble() * 0.65));
        return origin.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
    }

    private void halveMonsterHealth(UUID minionId) {
        Entity entity = Bukkit.getEntity(minionId);
        if (entity instanceof org.bukkit.entity.LivingEntity living) {
            org.bukkit.attribute.AttributeInstance maxHealth = living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                double next = Math.max(1.0, maxHealth.getBaseValue() * 0.5);
                maxHealth.setBaseValue(next);
                living.setHealth(Math.min(living.getHealth(), next));
            }
        }
    }

    private boolean isSafeWallAngle(double angleDegrees, double rotationDegrees, double safeDegrees) {
        double normalized = Math.floorMod((int) Math.round(angleDegrees - rotationDegrees), 360);
        double segment = normalized % 60.0;
        double distance = Math.min(segment, 60.0 - segment);
        return distance <= safeDegrees * 0.5;
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
            return List.of(new HallsBossType.DisplayPart("core", type.displayMaterial(), 0.0, 2.5, 0.0, 5.0, 5.0, 5.0));
        }
        return type.displayParts().isEmpty()
                ? List.of(new HallsBossType.DisplayPart("core", type.displayMaterial(), -2.5, 0.0, -2.5, 5.0, 5.0, 5.0))
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
        animateDisplays("", 0, yaw, yOffset, offset);
    }

    private void animateDisplays(String animationId, int tick, float yaw, double yOffset, Vector offset) {
        if (activeBoss == null) {
            return;
        }
        List<HallsBossType.DisplayPart> parts = displayParts(activeBoss.type());
        String normalizedAnimationId = normalizeId(animationId);
        playAnimationSounds(activeBoss.type(), normalizedAnimationId, tick);
        int index = 0;
        for (UUID displayId : activeBoss.displayIds()) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity == null) {
                index++;
                continue;
            }
            HallsBossType.DisplayPart part = parts.get(Math.min(index, parts.size() - 1));
            AnimationPose pose = animationPose(activeBoss.type(), normalizedAnimationId, part.id(), tick);
            Vector combinedOffset = (offset == null ? new Vector() : offset.clone())
                    .add(new Vector(pose.offsetX(), pose.offsetY(), pose.offsetZ()));
            Location target = displayLocation(activeBoss.location(), part, yOffset, combinedOffset);
            entity.teleport(target);
            if (entity instanceof Display display) {
                display.setInterpolationDelay(1);
                display.setTeleportDuration(2);
                display.setTransformation(HallsDisplayTransforms.centeredBlock(
                        part.scaleX() * pose.scaleX() * activeBoss.scaleMultiplier(),
                        part.scaleY() * pose.scaleY() * activeBoss.scaleMultiplier(),
                        part.scaleZ() * pose.scaleZ() * activeBoss.scaleMultiplier(),
                        new Quaternionf().rotateY((float) Math.toRadians(yaw + pose.yawOffset()))));
            }
            index++;
        }
    }

    private AnimationPose animationPose(HallsBossType type, String animationId, String partId, int tick) {
        HallsBossType.Animation animation = type.animations().get(animationId);
        if (animation == null) {
            return AnimationPose.IDENTITY;
        }
        AnimationPose global = interpolate(animation.frames(), animation.loop(), tick);
        List<HallsBossType.Keyframe> partFrames = animation.partFrames().get(normalizeId(partId));
        if (partFrames == null || partFrames.isEmpty()) {
            return global;
        }
        return global.plus(interpolate(partFrames, animation.loop(), tick));
    }

    private AnimationPose interpolate(List<HallsBossType.Keyframe> frames, boolean loop, int tick) {
        if (frames == null || frames.isEmpty()) {
            return AnimationPose.IDENTITY;
        }
        int adjustedTick = Math.max(0, tick);
        HallsBossType.Keyframe lastFrame = frames.getLast();
        if (loop && lastFrame.tick() > 0) {
            adjustedTick %= lastFrame.tick();
        }
        HallsBossType.Keyframe previous = frames.getFirst();
        HallsBossType.Keyframe next = frames.getLast();
        for (HallsBossType.Keyframe frame : frames) {
            if (frame.tick() <= adjustedTick) {
                previous = frame;
            }
            if (frame.tick() >= adjustedTick) {
                next = frame;
                break;
            }
        }
        if (previous == next || next.tick() <= previous.tick()) {
            return AnimationPose.from(previous);
        }
        double progress = (adjustedTick - previous.tick()) / (double) (next.tick() - previous.tick());
        return AnimationPose.lerp(previous, next, progress);
    }

    private void playAnimationSounds(HallsBossType type, String animationId, int tick) {
        if (type == null || activeBoss == null || animationId.isBlank()) {
            return;
        }
        HallsBossType.Animation animation = type.animations().get(animationId);
        if (animation == null) {
            return;
        }
        playFrameSounds(animation.frames(), animation.loop(), tick);
    }

    private void playFrameSounds(List<HallsBossType.Keyframe> frames, boolean loop, int tick) {
        if (frames == null || frames.isEmpty()) {
            return;
        }
        int adjustedTick = tick <= 1 ? 0 : tick;
        HallsBossType.Keyframe lastFrame = frames.getLast();
        if (loop && lastFrame.tick() > 0) {
            adjustedTick %= lastFrame.tick();
        }
        for (HallsBossType.Keyframe frame : frames) {
            if (frame.tick() == adjustedTick && !frame.sounds().isEmpty()) {
                Location location = activeBoss.location().clone().add(0.0, 2.5, 0.0);
                for (HallsBossType.SoundCue sound : frame.sounds()) {
                    world.playSound(location, sound.sound(), sound.volume(), sound.pitch());
                }
                return;
            }
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
        boolean shielded = activeBoss.hasLivingMinions();
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, activeBoss.health() / activeBoss.maxHealth())));
        bossBar.setColor(shielded ? BarColor.WHITE : BarColor.RED);
        String shield = shielded ? " - Shielded" : "";
        bossBar.setTitle(activeBoss.type().name() + " " + Math.max(0, (int) Math.ceil(activeBoss.health())) + " HP" + shield);
    }

    private void updateHitboxSize() {
        if (activeBoss == null) {
            return;
        }
        Entity hitbox = Bukkit.getEntity(activeBoss.hitboxId());
        if (hitbox instanceof Interaction interaction) {
            setHitboxSize(interaction, activeBoss.scaleMultiplier());
        }
    }

    private void setHitboxSize(Interaction entity, double scaleMultiplier) {
        float size = (float) Math.max(1.0, 5.0 * scaleMultiplier);
        entity.setInteractionWidth(size);
        entity.setInteractionHeight(size);
    }

    private void updateInvulnerabilityFeedback(long nowMillis) {
        if (activeBoss == null) {
            return;
        }
        boolean invulnerable = nowMillis < activeBoss.invulnerableUntilMillis();
        Entity hitbox = Bukkit.getEntity(activeBoss.hitboxId());
        if (hitbox != null) {
            hitbox.setGlowing(invulnerable);
        }
        for (UUID displayId : activeBoss.displayIds()) {
            Entity display = Bukkit.getEntity(displayId);
            if (display != null) {
                display.setGlowing(invulnerable);
            }
        }
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

    interface MinionSpawner {
        UUID spawn(String monsterId, Location location);
    }

    interface BossDropper {
        void drop(Location location, int randomScrap);
    }

    record DoorSeal(int minX, int maxX, int y, int minZ, int maxZ, Material material) {
        Location center(World world) {
            return new Location(world, (minX + maxX) / 2.0 + 0.5, y + 1.5, (minZ + maxZ) / 2.0 + 0.5);
        }
    }

    private enum Attack {
        SPAWN,
        JUMP,
        X_BLAST,
        MISSILE,
        WALLS,
        REPOSITION
    }

    private final class TimedAttack implements Runnable {
        private final int durationTicks;
        private final Runnable tickAction;
        private final Runnable finishedAction;
        private final int generation;
        private int ticks;
        private BukkitTask task;

        private TimedAttack(int durationTicks, Runnable tickAction, Runnable finishedAction) {
            this.durationTicks = Math.max(1, durationTicks);
            this.tickAction = tickAction;
            this.finishedAction = finishedAction;
            this.generation = activeBoss == null ? 0 : activeBoss.attackGeneration();
            this.task = Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
        }

        @Override
        public void run() {
            if (activeBoss == null || !activeBoss.active() || activeBoss.attackGeneration() != generation) {
                task.cancel();
                return;
            }
            if (ticks++ < durationTicks) {
                activeBoss.setAttackAnimationTicks(activeBoss.attackAnimationTicks() + 1);
                tickAction.run();
                return;
            }
            task.cancel();
            finishedAction.run();
        }
    }

    private final class JumpSlam extends org.bukkit.scheduler.BukkitRunnable {
        private final HallsBossType.Overdrive config;
        private final int remaining;
        private int ticks;

        private JumpSlam(HallsBossType.Overdrive config, int remaining) {
            this.config = config;
            this.remaining = remaining;
        }

        @Override
        public void run() {
            if (activeBoss == null || !activeBoss.active()) {
                cancel();
                return;
            }
            ticks++;
            if (ticks <= 10) {
                animateDisplays("jump_rise", ticks, activeBoss.yaw(), ticks / 10.0 * 3.0, new Vector());
                return;
            }
            if (ticks <= 18) {
                animateDisplays("jump_fall", ticks - 10, activeBoss.yaw(), (18 - ticks) / 8.0 * 3.0, new Vector());
                return;
            }
            world.playSound(activeBoss.location(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, activeBoss.enraged() ? 0.45f : 0.55f);
            new Shockwave(config.shockwaveDamage(), config.shockwaveSpeedBlocksPerSecond()).runTaskTimer(plugin, 1L, 2L);
            if (remaining > 1) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> runJumpChain(config, remaining - 1), 12L);
            } else {
                scheduleNextAttack(config.jumpCooldownTicks());
            }
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
                double yFraction = player.getLocation().getY() - Math.floor(player.getLocation().getY());
                boolean jumped = !player.isOnGround() || yFraction > 0.08 || player.getVelocity().getY() > 0.02;
                if (Math.abs(horizontal - radius) <= 0.6 && !jumped) {
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

    private final class WallWave extends org.bukkit.scheduler.BukkitRunnable {
        private final double damage;
        private final double radiusStep;
        private final double rotationDegrees;
        private final double safeDegrees;
        private final Set<UUID> hit = new java.util.HashSet<>();
        private double radius = 1.0;

        private WallWave(double damage, double speedBlocksPerSecond, double rotationDegrees, double safeDegrees) {
            this.damage = damage;
            this.radiusStep = Math.max(0.05, speedBlocksPerSecond * 2.0 / 20.0);
            this.rotationDegrees = rotationDegrees;
            this.safeDegrees = safeDegrees;
        }

        @Override
        public void run() {
            if (activeBoss == null) {
                cancel();
                return;
            }
            Location center = activeBoss.location().clone().add(0.0, 0.12, 0.0);
            for (double degrees = 0.0; degrees < 360.0; degrees += 5.0) {
                if (isSafeWallAngle(degrees, rotationDegrees, safeDegrees)) {
                    continue;
                }
                double radians = Math.toRadians(degrees);
                Location point = center.clone().add(Math.cos(radians) * radius, 0.0, Math.sin(radians) * radius);
                world.spawnParticle(Particle.FLAME, point, 1, 0.03, 0.05, 0.03, 0.0);
                world.spawnParticle(Particle.DUST_PLUME, point, 1, 0.03, 0.02, 0.03, 0.0);
            }
            for (Player player : alivePlayers()) {
                if (hit.contains(player.getUniqueId())) {
                    continue;
                }
                Vector relative = player.getLocation().toVector().subtract(center.toVector());
                double horizontal = Math.hypot(relative.getX(), relative.getZ());
                if (Math.abs(horizontal - radius) > 0.65) {
                    continue;
                }
                double angle = Math.toDegrees(Math.atan2(relative.getZ(), relative.getX()));
                if (isSafeWallAngle(angle, rotationDegrees, safeDegrees)) {
                    continue;
                }
                hit.add(player.getUniqueId());
                player.damage(damage);
            }
            radius += radiusStep;
            if (radius > 13.0) {
                cancel();
            }
        }
    }

    private final class RepositionMove extends org.bukkit.scheduler.BukkitRunnable {
        private final Location from;
        private final Location target;
        private final HallsBossType.ArchaicGuard config;
        private final PotionEffectType effect;
        private final int generation;
        private int ticks;

        private RepositionMove(Location from, Location target, HallsBossType.ArchaicGuard config, PotionEffectType effect) {
            this.from = from == null ? activeBoss.location().clone() : from;
            this.target = target == null ? activeBoss.location().clone() : target;
            this.config = config;
            this.effect = effect;
            this.generation = activeBoss == null ? 0 : activeBoss.attackGeneration();
        }

        @Override
        public void run() {
            if (activeBoss == null || !activeBoss.active() || activeBoss.attackGeneration() != generation) {
                cancel();
                return;
            }
            ticks++;
            double progress = Math.min(1.0, ticks / (double) config.repositionTicks());
            double eased = 1.0 - Math.pow(1.0 - progress, 2.0);
            activeBoss.location().setX(from.getX() + (target.getX() - from.getX()) * eased);
            activeBoss.location().setY(from.getY() + (target.getY() - from.getY()) * eased);
            activeBoss.location().setZ(from.getZ() + (target.getZ() - from.getZ()) * eased);
            activeBoss.setYaw(activeBoss.yaw() + 10.0f);
            animateDisplays("reposition", ticks, activeBoss.yaw(), 0.18, new Vector());
            Location cloud = activeBoss.location().clone().add(0.0, 0.1, 0.0);
            if (ticks == 1 || ticks % 10 == 0) {
                HallsPoisonClouds.spawn(plugin, world, null, cloud, config.cloudRadius(), config.cloudDurationTicks(), 10,
                        effect, config.cloudEffectTicks(), 0, 0.0,
                        living -> living instanceof Player player
                                && participants.contains(player.getUniqueId())
                                && aliveParticipantPredicate.test(player.getUniqueId()));
            }
            if (ticks >= config.repositionTicks()) {
                world.playSound(activeBoss.location(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.75f);
                scheduleNextAttack(config.repositionCooldownTicks());
                cancel();
            }
        }
    }

    private final class RetractAnimation extends org.bukkit.scheduler.BukkitRunnable {
        private int ticks;

        @Override
        public void run() {
            if (activeBoss == null || ticks++ > 20) {
                cancel();
                return;
            }
            animateDisplays("retract", ticks, activeBoss.yaw() + ticks * 8.0f, -ticks / 20.0, new Vector());
            world.spawnParticle(Particle.SMOKE, activeBoss.location().clone().add(0.0, 0.5, 0.0),
                    8, 1.4, 0.2, 1.4, 0.03);
        }
    }

    private final class EnrageAnimation extends org.bukkit.scheduler.BukkitRunnable {
        private final HallsBossType.Overdrive config;
        private int ticks;

        private EnrageAnimation(HallsBossType.Overdrive config) {
            this.config = config;
        }

        @Override
        public void run() {
            if (activeBoss == null || !activeBoss.active()) {
                cancel();
                return;
            }
            ticks++;
            double progress = Math.min(1.0, ticks / 40.0);
            double scale = config.initialScaleMultiplier()
                    + (config.enragedScaleMultiplier() - config.initialScaleMultiplier()) * progress;
            activeBoss.setScaleMultiplier(scale);
            updateHitboxSize();
            activeBoss.setYaw(activeBoss.yaw() + 16.0f);
            animateDisplays("idle", activeBoss.idleTicks(), activeBoss.yaw(), Math.sin(ticks / 3.0) * 0.08, new Vector());
            Location center = activeBoss.location().clone().add(0.0, 2.5, 0.0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, center, 16, 2.2, 1.8, 2.2, 0.08);
            world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, center, 5, 2.3, 1.8, 2.3, 0.0);
            if (ticks % 10 == 0) {
                world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.55f + ticks / 100.0f);
            }
            if (ticks >= 40) {
                activeBoss.setScaleMultiplier(config.enragedScaleMultiplier());
                updateHitboxSize();
                world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.25f);
                cancel();
            }
        }
    }

    private record AnimationPose(double offsetX,
                                 double offsetY,
                                 double offsetZ,
                                 double yawOffset,
                                 double scaleX,
                                 double scaleY,
                                 double scaleZ) {
        private static final AnimationPose IDENTITY = new AnimationPose(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

        private AnimationPose plus(AnimationPose other) {
            if (other == null) {
                return this;
            }
            return new AnimationPose(
                    offsetX + other.offsetX,
                    offsetY + other.offsetY,
                    offsetZ + other.offsetZ,
                    yawOffset + other.yawOffset,
                    scaleX * other.scaleX,
                    scaleY * other.scaleY,
                    scaleZ * other.scaleZ
            );
        }

        private static AnimationPose from(HallsBossType.Keyframe frame) {
            return new AnimationPose(frame.offsetX(), frame.offsetY(), frame.offsetZ(), frame.yawOffset(),
                    frame.scaleX(), frame.scaleY(), frame.scaleZ());
        }

        private static AnimationPose lerp(HallsBossType.Keyframe from, HallsBossType.Keyframe to, double progress) {
            double clamped = Math.max(0.0, Math.min(1.0, progress));
            return new AnimationPose(
                    lerp(from.offsetX(), to.offsetX(), clamped),
                    lerp(from.offsetY(), to.offsetY(), clamped),
                    lerp(from.offsetZ(), to.offsetZ(), clamped),
                    lerp(from.yawOffset(), to.yawOffset(), clamped),
                    lerp(from.scaleX(), to.scaleX(), clamped),
                    lerp(from.scaleY(), to.scaleY(), clamped),
                    lerp(from.scaleZ(), to.scaleZ(), clamped)
            );
        }

        private static double lerp(double from, double to, double progress) {
            return from + (to - from) * progress;
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
        private int attackAnimationTicks;
        private long invulnerableUntilMillis;
        private Attack lastAttack;
        private final Map<UUID, String> minions = new HashMap<>();
        private final Map<String, Integer> minionCooldownTicks = new HashMap<>();
        private boolean enraged;
        private boolean awaitingGroupClear;
        private int groupCooldownTicks;
        private int summonReadyTicks;
        private double scaleMultiplier = 1.0;
        private float xBlastYaw;
        private int attackGeneration;

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

        private int attackAnimationTicks() {
            return attackAnimationTicks;
        }

        private void setAttackAnimationTicks(int attackAnimationTicks) {
            this.attackAnimationTicks = attackAnimationTicks;
        }

        private long invulnerableUntilMillis() {
            return invulnerableUntilMillis;
        }

        private void setInvulnerableUntilMillis(long invulnerableUntilMillis) {
            this.invulnerableUntilMillis = invulnerableUntilMillis;
        }

        private Attack lastAttack() {
            return lastAttack;
        }

        private void setLastAttack(Attack lastAttack) {
            this.lastAttack = lastAttack;
        }

        private Map<UUID, String> minions() {
            return minions;
        }

        private void registerMinion(UUID entityId, String monsterId) {
            if (entityId != null) {
                minions.put(entityId, normalizeId(monsterId));
            }
        }

        private void removeMinion(UUID entityId) {
            minions.remove(entityId);
        }

        private boolean hasLivingMinions() {
            return !minions.isEmpty();
        }

        private void startMinionCooldown(String monsterId, int ticks) {
            String normalized = normalizeId(monsterId);
            if (!normalized.isBlank()) {
                minionCooldownTicks.put(normalized, Math.max(1, ticks));
            }
        }

        private int minionCooldownTicks(String monsterId) {
            return minionCooldownTicks.getOrDefault(normalizeId(monsterId), 0);
        }

        private void tickMinionCooldowns() {
            for (Map.Entry<String, Integer> entry : Map.copyOf(minionCooldownTicks).entrySet()) {
                int next = entry.getValue() - 1;
                if (next <= 0) {
                    minionCooldownTicks.remove(entry.getKey());
                } else {
                    minionCooldownTicks.put(entry.getKey(), next);
                }
            }
        }

        private boolean enraged() {
            return enraged;
        }

        private void setEnraged(boolean enraged) {
            this.enraged = enraged;
        }

        private boolean awaitingGroupClear() {
            return awaitingGroupClear;
        }

        private void setAwaitingGroupClear(boolean awaitingGroupClear) {
            this.awaitingGroupClear = awaitingGroupClear;
        }

        private int groupCooldownTicks() {
            return groupCooldownTicks;
        }

        private void setGroupCooldownTicks(int groupCooldownTicks) {
            this.groupCooldownTicks = Math.max(0, groupCooldownTicks);
        }

        private int summonReadyTicks() {
            return summonReadyTicks;
        }

        private void setSummonReadyTicks(int summonReadyTicks) {
            this.summonReadyTicks = Math.max(0, summonReadyTicks);
        }

        private double scaleMultiplier() {
            return scaleMultiplier;
        }

        private void setScaleMultiplier(double scaleMultiplier) {
            this.scaleMultiplier = Math.max(0.05, scaleMultiplier);
        }

        private float xBlastYaw() {
            return xBlastYaw;
        }

        private void setXBlastYaw(float xBlastYaw) {
            this.xBlastYaw = xBlastYaw;
        }

        private int attackGeneration() {
            return attackGeneration;
        }

        private void setAttackGeneration(int attackGeneration) {
            this.attackGeneration = Math.max(0, attackGeneration);
        }
    }
}
