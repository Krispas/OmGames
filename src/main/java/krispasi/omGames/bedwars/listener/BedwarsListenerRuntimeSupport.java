package krispasi.omGames.bedwars.listener;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.model.Arena;
import krispasi.omGames.bedwars.gui.*;
import krispasi.omGames.bedwars.item.*;
import krispasi.omGames.bedwars.model.*;
import krispasi.omGames.bedwars.shop.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.Particle.DustOptions;
import org.bukkit.attribute.*;
import org.bukkit.block.*;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.potion.*;
import org.bukkit.scheduler.*;
import org.bukkit.util.Vector;

/**
 * Main Bukkit listener for BedWars gameplay.
 * <p>Enforces match rules and handles GUI actions, custom items, combat, entity behavior,
 * and block placement during a {@link krispasi.omGames.bedwars.game.GameSession}.</p>
 * <p>Also applies custom projectile metadata and manages summon lifetimes.</p>
 */

abstract class BedwarsListenerRuntimeSupport extends BedwarsListenerCustomSupport {

    protected BedwarsListenerRuntimeSupport(BedwarsManager bedwarsManager) {
        super(bedwarsManager);
    }

    protected void scheduleMountHappyGhast(org.bukkit.entity.Entity entity, Player player) {
        if (entity == null || player == null) {
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!entity.isValid() || entity.isDead() || !player.isOnline()) {
                    return;
                }
                entity.addPassenger(player);
                entity.addScoreboardTag(HAPPY_GHAST_MOUNTED_TAG);
            }
        }.runTaskLater(bedwarsManager.getPlugin(), 1L);
    }

    protected void applyHappyGhastHarness(LivingEntity entity, TeamColor team) {
        if (entity == null) {
            return;
        }
        DyeColor dyeColor = team != null ? team.dyeColor() : null;
        invokeBooleanSetter(entity, "setHasHarness", true);
        invokeBooleanSetter(entity, "setHasCarryingHarness", true);
        invokeBooleanSetter(entity, "setHarnessed", true);
        invokeBooleanSetter(entity, "setHarness", true);
        invokeBooleanSetter(entity, "setSaddled", true);
        if (dyeColor != null) {
            invokeObjectSetter(entity, "setHarnessColor", DyeColor.class, dyeColor);
            invokeObjectSetter(entity, "setHarnessColour", DyeColor.class, dyeColor);
            invokeObjectSetter(entity, "setHarnessDyeColor", DyeColor.class, dyeColor);
        }
        ItemStack harness = createHarnessItem(team, dyeColor);
        if (harness != null) {
            invokeObjectSetter(entity, "setHarness", ItemStack.class, harness);
            invokeObjectSetter(entity, "setHarnessItem", ItemStack.class, harness);
            invokeObjectSetter(entity, "setCarryingHarnessItem", ItemStack.class, harness);
            invokeObjectSetter(entity, "setSaddle", ItemStack.class, harness);
            invokeObjectSetter(entity, "setHarnessMaterial", Material.class, harness.getType());
            invokeObjectSetter(entity, "setHarness", Material.class, harness.getType());
            applyHarnessEquipment(entity, harness);
        }
        applyHarnessByReflection(entity, harness, dyeColor);
        applyHappyGhastHarnessNms(entity, harness, dyeColor);
    }

    protected void scheduleHappyGhastHarness(LivingEntity entity, TeamColor team) {
        if (entity == null) {
            return;
        }
        bedwarsManager.getPlugin().getServer().getScheduler().runTaskLater(bedwarsManager.getPlugin(),
                () -> applyHappyGhastHarness(entity, team), 1L);
        bedwarsManager.getPlugin().getServer().getScheduler().runTaskLater(bedwarsManager.getPlugin(),
                () -> applyHappyGhastHarness(entity, team), 20L);
    }

    protected ItemStack createHarnessItem(TeamColor team, DyeColor dyeColor) {
        if (team != null) {
            Material teamMaterial = Material.matchMaterial(team.key().toUpperCase(Locale.ROOT) + "_HARNESS");
            if (teamMaterial != null) {
                return new ItemStack(teamMaterial);
            }
        }
        if (dyeColor != null) {
            Material dyeMaterial = Material.matchMaterial(dyeColor.name() + "_HARNESS");
            if (dyeMaterial != null) {
                return new ItemStack(dyeMaterial);
            }
        }
        Material material = Material.matchMaterial("WHITE_HARNESS");
        if (material == null) {
            material = Material.matchMaterial("HAPPY_GHAST_HARNESS");
        }
        if (material == null) {
            material = Material.matchMaterial("GHAST_HARNESS");
        }
        if (material != null) {
            return new ItemStack(material);
        }
        for (Material candidate : Material.values()) {
            if (candidate.name().endsWith("_HARNESS")) {
                return new ItemStack(candidate);
            }
        }
        return null;
    }

    protected void applyHarnessEquipment(LivingEntity entity, ItemStack harness) {
        if (entity == null || harness == null) {
            return;
        }
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setChestplate(harness);
        EquipmentSlot bodySlot = resolveEquipmentSlot("BODY");
        if (bodySlot != null) {
            equipment.setItem(bodySlot, harness);
        }
    }

    protected EquipmentSlot resolveEquipmentSlot(String name) {
        if (name == null) {
            return null;
        }
        try {
            return EquipmentSlot.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    protected void applyHarnessByReflection(LivingEntity entity, ItemStack harness, DyeColor dyeColor) {
        if (entity == null) {
            return;
        }
        Method[] methods = entity.getClass().getMethods();
        Method[] declared = entity.getClass().getDeclaredMethods();
        for (Method method : concatMethods(methods, declared)) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.contains("harness") && !name.contains("saddle")) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            Object arg = null;
            if (harness != null && param.isAssignableFrom(ItemStack.class)) {
                arg = harness;
            } else if (harness != null && param.isAssignableFrom(Material.class)) {
                arg = harness.getType();
            } else if (dyeColor != null && param.isAssignableFrom(DyeColor.class)) {
                arg = dyeColor;
            } else if (dyeColor != null && "org.bukkit.Color".equals(param.getName())) {
                arg = dyeColor.getColor();
            } else if (dyeColor != null && param == String.class) {
                arg = dyeColor.name();
            } else if (param == boolean.class) {
                arg = Boolean.TRUE;
            }
            if (arg == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(entity, arg);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    protected boolean invokeBooleanSetter(Object target, String methodName, boolean value) {
        if (target == null || methodName == null) {
            return false;
        }
        try {
            Method method = target.getClass().getMethod(methodName, boolean.class);
            method.invoke(target, value);
            return true;
        } catch (ReflectiveOperationException ignored) {
            try {
                Method method = target.getClass().getDeclaredMethod(methodName, boolean.class);
                method.setAccessible(true);
                method.invoke(target, value);
                return true;
            } catch (ReflectiveOperationException ignoredAgain) {
                return false;
            }
        }
    }

    protected boolean invokeObjectSetter(Object target, String methodName, Class<?> argType, Object value) {
        if (target == null || methodName == null || argType == null) {
            return false;
        }
        try {
            Method method = target.getClass().getMethod(methodName, argType);
            method.invoke(target, value);
            return true;
        } catch (ReflectiveOperationException ignored) {
            try {
                Method method = target.getClass().getDeclaredMethod(methodName, argType);
                method.setAccessible(true);
                method.invoke(target, value);
                return true;
            } catch (ReflectiveOperationException ignoredAgain) {
                return false;
            }
        }
    }

    protected Method[] concatMethods(Method[] first, Method[] second) {
        int firstLen = first != null ? first.length : 0;
        int secondLen = second != null ? second.length : 0;
        Method[] combined = new Method[firstLen + secondLen];
        if (firstLen > 0) {
            System.arraycopy(first, 0, combined, 0, firstLen);
        }
        if (secondLen > 0) {
            System.arraycopy(second, 0, combined, firstLen, secondLen);
        }
        return combined;
    }

    protected void applyHappyGhastHarnessNms(LivingEntity entity, ItemStack harness, DyeColor dyeColor) {
        if (entity == null) {
            return;
        }
        try {
            Method getHandle = entity.getClass().getMethod("getHandle");
            Object handle = getHandle.invoke(entity);
            if (handle == null) {
                return;
            }
            Object nmsStack = toNmsItem(harness);
            Object nmsDye = resolveNmsDyeColor(dyeColor);
            Method[] methods = concatMethods(handle.getClass().getMethods(), handle.getClass().getDeclaredMethods());
            for (Method method : methods) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("harness") && !name.contains("saddle")) {
                    continue;
                }
                if (method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> param = method.getParameterTypes()[0];
                Object arg = null;
                if (nmsStack != null && param.isInstance(nmsStack)) {
                    arg = nmsStack;
                } else if (nmsStack != null && param.getName().endsWith("ItemStack")) {
                    arg = nmsStack;
                } else if (nmsDye != null && param.isInstance(nmsDye)) {
                    arg = nmsDye;
                } else if (dyeColor != null && param == String.class) {
                    arg = dyeColor.name();
                } else if (param == boolean.class) {
                    arg = Boolean.TRUE;
                } else if (param == int.class) {
                    arg = 1;
                }
                if (arg == null) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(handle, arg);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            for (var field : handle.getClass().getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("harness") && !name.contains("saddle")) {
                    continue;
                }
                field.setAccessible(true);
                Class<?> type = field.getType();
                if (type == boolean.class) {
                    field.setBoolean(handle, true);
                } else if (type == int.class) {
                    field.setInt(handle, 1);
                } else if (nmsStack != null && type.isInstance(nmsStack)) {
                    field.set(handle, nmsStack);
                } else if (nmsDye != null && type.isInstance(nmsDye)) {
                    field.set(handle, nmsDye);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    protected Object toNmsItem(ItemStack item) {
        if (item == null) {
            return null;
        }
        try {
            String version = getCraftBukkitVersion();
            if (version == null) {
                return null;
            }
            Class<?> craftItem = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Method asNmsCopy = craftItem.getMethod("asNMSCopy", ItemStack.class);
            return asNmsCopy.invoke(null, item);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    protected Object resolveNmsDyeColor(DyeColor dyeColor) {
        if (dyeColor == null) {
            return null;
        }
        try {
            Class<?> dyeClass = Class.forName("net.minecraft.world.item.DyeColor");
            return Enum.valueOf((Class) dyeClass, dyeColor.name());
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    protected String getCraftBukkitVersion() {
        String name = Bukkit.getServer().getClass().getPackage().getName();
        int index = name.lastIndexOf('.');
        if (index < 0) {
            return null;
        }
        return name.substring(index + 1);
    }

    protected void spawnBedBug(GameSession session, TeamColor team, Location location, CustomItemDefinition custom) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Silverfish silverfish = location.getWorld().spawn(location, Silverfish.class, entity -> {
            entity.setRemoveWhenFarAway(true);
            entity.setPersistent(false);
            entity.setCanPickupItems(false);
            entity.addScoreboardTag(GameSession.BED_BUG_TAG);
            setSummonTeam(entity, team);
        });
        applySummonStats(silverfish, custom);
        Player target = findNearestEnemy(location, team, session, DEFENDER_TARGET_RANGE);
        if (target != null) {
            silverfish.setTarget(target);
        }
        int lifetimeSeconds = custom != null && custom.getLifetimeSeconds() > 0
                ? custom.getLifetimeSeconds()
                : 15;
        scheduleSummonDespawn(silverfish, lifetimeSeconds);
    }

    protected void startDefenderTargeting(IronGolem golem, TeamColor owner, GameSession session) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (golem == null || !golem.isValid() || golem.isDead()) {
                    cancelTask(golem);
                    return;
                }
                if (session == null || !session.isRunning()) {
                    golem.remove();
                    cancelTask(golem);
                    return;
                }
                Player target = findNearestEnemy(golem.getLocation(), owner, session, DEFENDER_TARGET_RANGE);
                if (target != null) {
                    golem.setTarget(target);
                }
            }

            private void cancelTask(IronGolem golem) {
                UUID id = golem != null ? golem.getUniqueId() : null;
                if (id != null) {
                    defenderTasks.remove(id);
                }
                cancel();
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 0L, 10L);
        defenderTasks.put(golem.getUniqueId(), task);
    }

    protected void scheduleSummonDespawn(org.bukkit.entity.Entity entity, int lifetimeSeconds) {
        if (entity == null || lifetimeSeconds <= 0) {
            return;
        }
        UUID entityId = entity.getUniqueId();
        cleanupSummonTracker(entityId);
        SummonNameplate nameplate = createSummonNameplate(entity);
        if (nameplate != null) {
            summonNameplates.put(entityId, nameplate);
        }
        BukkitTask task = new BukkitRunnable() {
            private int remaining = lifetimeSeconds;
            private int tickCounter = 0;

            @Override
            public void run() {
                if (!entity.isValid() || entity.isDead()) {
                    cleanupSummonTracker(entityId);
                    return;
                }
                if (isHappyGhast(entity)
                        && entity.getScoreboardTags().contains(HAPPY_GHAST_MOUNTED_TAG)
                        && entity.getPassengers().isEmpty()) {
                    entity.remove();
                    cleanupSummonTracker(entityId);
                    return;
                }
                updateSummonNameplates(entity, nameplate);
                if (tickCounter % 20 == 0) {
                    if (remaining <= 0) {
                        entity.remove();
                        cleanupSummonTracker(entityId);
                        return;
                    }
                    updateSummonName(entity, remaining);
                    sendSummonActionBar(entity, remaining);
                    remaining--;
                }
                tickCounter += 2;
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 0L, 2L);
        summonNameTasks.put(entityId, task);
    }

    protected void cleanupSummonTracker(UUID entityId) {
        if (entityId == null) {
            return;
        }
        BukkitTask task = summonNameTasks.remove(entityId);
        if (task != null) {
            task.cancel();
        }
        SummonNameplate nameplate = summonNameplates.remove(entityId);
        if (nameplate != null) {
            removeNameplate(nameplate);
        }
    }

    protected void updateSummonName(org.bukkit.entity.Entity entity, int remainingSeconds) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        String name = resolveSummonDisplayName(entity);
        if (name == null || name.isBlank()) {
            return;
        }
        TeamColor team = getSummonTeam(entity);
        Component title = team != null
                ? Component.text(team.displayName() + " " + name, team.textColor())
                : Component.text(name, NamedTextColor.WHITE);
        Component timer = buildSummonStatusLine(living, remainingSeconds);
        SummonNameplate nameplate = summonNameplates.get(entity.getUniqueId());
        if (nameplate != null) {
            living.customName(title);
            living.setCustomNameVisible(false);
            updateNameplateText(nameplate, title, timer);
        } else {
            living.customName(title.append(Component.newline()).append(timer));
            living.setCustomNameVisible(true);
        }
    }

    protected Component buildSummonStatusLine(LivingEntity entity, int remainingSeconds) {
        Component despawn = Component.text("Despawn: " + Math.max(0, remainingSeconds) + "s", NamedTextColor.GRAY);
        if (!isHappyGhast(entity)) {
            return despawn;
        }
        int currentHealth = (int) Math.ceil(Math.max(0.0, entity.getHealth()));
        int maxHealth = (int) Math.ceil(Math.max(entity.getMaxHealth(), entity.getHealth()));
        return Component.text(currentHealth + "/" + maxHealth + " HP", NamedTextColor.RED)
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(despawn);
    }

    protected void sendSummonActionBar(org.bukkit.entity.Entity entity, int remainingSeconds) {
        if (!isHappyGhast(entity)) {
            return;
        }
        for (org.bukkit.entity.Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player player) {
                player.sendActionBar(Component.text("Happy Ghast despawns in " + Math.max(0, remainingSeconds) + "s",
                        NamedTextColor.GRAY));
            }
        }
    }

    protected boolean isHappyGhast(org.bukkit.entity.Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(GameSession.HAPPY_GHAST_TAG);
    }

    protected SummonNameplate createSummonNameplate(org.bukkit.entity.Entity entity) {
        if (entity == null || entity.getWorld() == null) {
            return null;
        }
        Location base = entity.getLocation();
        double height = estimateEntityHeight(entity);
        ArmorStand nameStand = spawnNameStand(base.clone().add(0, height + SUMMON_NAME_OFFSET, 0));
        ArmorStand timerStand = spawnNameStand(base.clone().add(0, height + SUMMON_TIMER_OFFSET, 0));
        return new SummonNameplate(nameStand.getUniqueId(), timerStand.getUniqueId());
    }

    protected ArmorStand spawnNameStand(Location location) {
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setCustomNameVisible(true);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.addScoreboardTag(SUMMON_NAME_TAG);
        });
    }

    protected void updateSummonNameplates(org.bukkit.entity.Entity entity, SummonNameplate nameplate) {
        if (entity == null || nameplate == null) {
            return;
        }
        ArmorStand nameStand = getNameStand(nameplate.nameId());
        ArmorStand timerStand = getNameStand(nameplate.timerId());
        if (nameStand == null || timerStand == null) {
            return;
        }
        Location base = entity.getLocation();
        double height = estimateEntityHeight(entity);
        nameStand.teleport(base.clone().add(0, height + SUMMON_NAME_OFFSET, 0));
        timerStand.teleport(base.clone().add(0, height + SUMMON_TIMER_OFFSET, 0));
    }

    protected void updateNameplateText(SummonNameplate nameplate, Component title, Component timer) {
        ArmorStand nameStand = getNameStand(nameplate.nameId());
        if (nameStand != null) {
            nameStand.customName(title);
        }
        ArmorStand timerStand = getNameStand(nameplate.timerId());
        if (timerStand != null) {
            timerStand.customName(timer);
        }
    }

    protected ArmorStand getNameStand(UUID id) {
        if (id == null) {
            return null;
        }
        org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
        return entity instanceof ArmorStand stand ? stand : null;
    }

    protected void removeNameplate(SummonNameplate nameplate) {
        ArmorStand nameStand = getNameStand(nameplate.nameId());
        if (nameStand != null) {
            nameStand.remove();
        }
        ArmorStand timerStand = getNameStand(nameplate.timerId());
        if (timerStand != null) {
            timerStand.remove();
        }
    }

    protected double estimateEntityHeight(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return 1.6;
        }
        try {
            Method method = entity.getClass().getMethod("getHeight");
            Object value = method.invoke(entity);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 1.6;
    }

    protected String resolveSummonDisplayName(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getScoreboardTags().contains(GameSession.DREAM_DEFENDER_TAG)) {
            return DREAM_DEFENDER_NAME;
        }
        if (entity.getScoreboardTags().contains(GameSession.BED_BUG_TAG)) {
            return BED_BUG_NAME;
        }
        if (entity.getScoreboardTags().contains(GameSession.HAPPY_GHAST_TAG)) {
            return HAPPY_GHAST_NAME;
        }
        if (entity.getScoreboardTags().contains(GameSession.CREEPING_CREEPER_TAG)) {
            return CREEPING_CREEPER_NAME;
        }
        if (entity.getScoreboardTags().contains(PORTABLE_SHOPKEEPER_TAG)) {
            return PORTABLE_SHOPKEEPER_NAME;
        }
        return toDisplayName(entity.getType());
    }

    protected String toDisplayName(EntityType type) {
        if (type == null) {
            return "";
        }
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    protected Player findNearestEnemy(Location origin, TeamColor owner, GameSession session, double range) {
        if (origin == null || session == null) {
            return null;
        }
        double bestDistance = range * range;
        Player best = null;
        for (UUID playerId : session.getAssignments().keySet()) {
            TeamColor team = session.getTeam(playerId);
            if (team == null || team == owner) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!session.isInArenaWorld(player.getWorld()) || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(origin);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    protected void setSummonTeam(org.bukkit.entity.Entity entity, TeamColor team) {
        if (entity == null || team == null) {
            return;
        }
        entity.getPersistentDataContainer().set(summonTeamKey, PersistentDataType.STRING, team.key());
    }

    protected TeamColor getSummonTeam(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        String key = entity.getPersistentDataContainer().get(summonTeamKey, PersistentDataType.STRING);
        if (key == null) {
            return null;
        }
        return TeamColor.fromKey(key);
    }

    protected void setHappyGhastDriver(org.bukkit.entity.Entity entity, UUID playerId) {
        if (entity == null || playerId == null) {
            return;
        }
        entity.getPersistentDataContainer().set(happyGhastDriverKey, PersistentDataType.STRING, playerId.toString());
    }

    protected boolean isHappyGhastDriver(org.bukkit.entity.Entity entity, Player player) {
        if (entity == null || player == null) {
            return false;
        }
        String value = entity.getPersistentDataContainer().get(happyGhastDriverKey, PersistentDataType.STRING);
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID driverId = UUID.fromString(value);
            return driverId.equals(player.getUniqueId());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    protected boolean isSummon(org.bukkit.entity.Entity entity) {
        return entity != null
                && (entity.getScoreboardTags().contains(GameSession.BED_BUG_TAG)
                || entity.getScoreboardTags().contains(GameSession.DREAM_DEFENDER_TAG)
                || entity.getScoreboardTags().contains(GameSession.HAPPY_GHAST_TAG)
                || entity.getScoreboardTags().contains(GameSession.CREEPING_CREEPER_TAG)
                || entity.getScoreboardTags().contains(PORTABLE_SHOPKEEPER_TAG));
    }

    protected CustomItemDefinition getSummonDefinition(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getScoreboardTags().contains(GameSession.BED_BUG_TAG)) {
            return getCustomItem("bed_bug");
        }
        if (entity.getScoreboardTags().contains(GameSession.DREAM_DEFENDER_TAG)) {
            return getCustomItem("dream_defender");
        }
        if (entity.getScoreboardTags().contains(GameSession.HAPPY_GHAST_TAG)) {
            return getCustomItem("happy_ghast");
        }
        if (entity.getScoreboardTags().contains(GameSession.CREEPING_CREEPER_TAG)) {
            return getCustomItem("creeping_arrow");
        }
        if (entity.getScoreboardTags().contains(PORTABLE_SHOPKEEPER_TAG)) {
            return getCustomItem("portable_shopkeeper");
        }
        return null;
    }

    protected boolean isAllowedParticipantEnvironmentalDamage(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return false;
        }
        return cause == EntityDamageEvent.DamageCause.WORLD_BORDER
                || cause == EntityDamageEvent.DamageCause.FALL
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
                || cause == EntityDamageEvent.DamageCause.CAMPFIRE;
    }

    protected void applySummonStats(LivingEntity entity, CustomItemDefinition custom) {
        if (entity == null || custom == null) {
            return;
        }
        double health = custom.getHealth();
        if (health > 0.0) {
            entity.setMaxHealth(health);
            entity.setHealth(Math.min(health, entity.getMaxHealth()));
        }
        double speed = custom.getSpeed();
        if (speed > 0.0) {
            if (isHappyGhast(entity)) {
                applyHappyGhastSpeed(entity, speed);
            } else {
                applyEntitySpeed(entity, speed);
            }
        }
        double range = custom.getRange();
        if (range > 0.0) {
            applyEntityRange(entity, range);
        }
    }

    protected void applyHappyGhastSpeed(LivingEntity entity, double speedMultiplier) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Method getAttribute = LivingEntity.class.getMethod("getAttribute", attributeClass);
            Object movement = resolveAttribute(attributeClass, "GENERIC_MOVEMENT_SPEED");
            scaleAttributeValue(entity, getAttribute, movement, speedMultiplier);
            Object flying = resolveAttribute(attributeClass, "GENERIC_FLYING_SPEED");
            scaleAttributeValue(entity, getAttribute, flying, speedMultiplier);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    protected void applyEntitySpeed(LivingEntity entity, double speed) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Method getAttribute = LivingEntity.class.getMethod("getAttribute", attributeClass);
            Object movement = resolveAttribute(attributeClass, "GENERIC_MOVEMENT_SPEED");
            applyAttributeValue(entity, getAttribute, movement, speed);
            Object flying = resolveAttribute(attributeClass, "GENERIC_FLYING_SPEED");
            applyAttributeValue(entity, getAttribute, flying, speed);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    protected void scaleAttributeValue(LivingEntity entity,
                                       Method getAttribute,
                                       Object attribute,
                                       double multiplier) {
        if (entity == null || getAttribute == null || attribute == null) {
            return;
        }
        try {
            Object instance = getAttribute.invoke(entity, attribute);
            if (instance == null) {
                return;
            }
            Method getBaseValue = instance.getClass().getMethod("getBaseValue");
            Object current = getBaseValue.invoke(instance);
            if (!(current instanceof Number number)) {
                return;
            }
            Method setBaseValue = instance.getClass().getMethod("setBaseValue", double.class);
            setBaseValue.invoke(instance, Math.max(0.0, number.doubleValue() * multiplier));
        } catch (ReflectiveOperationException ignored) {
        }
    }

    protected void applyEntityRange(LivingEntity entity, double range) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Method getAttribute = LivingEntity.class.getMethod("getAttribute", attributeClass);
            Object followRange = resolveAttribute(attributeClass, "GENERIC_FOLLOW_RANGE");
            applyAttributeValue(entity, getAttribute, followRange, range);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    protected void applyEntityKnockbackResistance(LivingEntity entity, double resistance) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Method getAttribute = LivingEntity.class.getMethod("getAttribute", attributeClass);
            Object knockbackResistance = resolveAttribute(attributeClass, "KNOCKBACK_RESISTANCE");
            applyAttributeValue(entity, getAttribute, knockbackResistance, resistance);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    protected Object resolveAttribute(Class<?> attributeClass, String name) {
        if (attributeClass == null || name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(":")) {
            NamespacedKey key = NamespacedKey.fromString(normalized);
            if (key != null) {
                Attribute attribute = Registry.ATTRIBUTE.get(key);
                if (attribute != null && attributeClass.isInstance(attribute)) {
                    return attribute;
                }
            }
        }
        String keyName = normalized;
        if (keyName.startsWith("generic_")) {
            keyName = keyName.substring("generic_".length());
        } else if (keyName.startsWith("player_")) {
            keyName = keyName.substring("player_".length());
        }
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(keyName));
        if (attribute != null && attributeClass.isInstance(attribute)) {
            return attribute;
        }
        try {
            return Enum.valueOf((Class) attributeClass, name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    protected void applyAttributeValue(LivingEntity entity,
                                     Method getAttribute,
                                     Object attribute,
                                     double value) {
        if (entity == null || getAttribute == null || attribute == null) {
            return;
        }
        try {
            Object instance = getAttribute.invoke(entity, attribute);
            if (instance == null) {
                return;
            }
            Method setBaseValue = instance.getClass().getMethod("setBaseValue", double.class);
            setBaseValue.invoke(instance, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    protected EntityType resolveHappyGhastType() {
        try {
            return EntityType.valueOf("HAPPY_GHAST");
        } catch (IllegalArgumentException ex) {
            return EntityType.GHAST;
        }
    }

    protected CustomItemDefinition getCustomItem(String id) {
        CustomItemConfig config = bedwarsManager.getCustomItemConfig();
        if (config == null || id == null) {
            return null;
        }
        return config.getItem(id);
    }

    protected CustomItemDefinition getCustomEntity(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        PersistentDataContainer container = entity.getPersistentDataContainer();
        String customId = container.get(customProjectileKey, PersistentDataType.STRING);
        if (customId != null) {
            return getCustomItem(customId);
        }
        return getSummonDefinition(entity);
    }

    protected void consumeHeldItem(Player player, EquipmentSlot hand, ItemStack usedItem) {
        EquipmentSlot slot = hand != null ? hand : EquipmentSlot.HAND;
        ItemStack stack = slot == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!matchesCustomItem(stack, usedItem)) {
            return;
        }
        int amount = stack.getAmount();
        if (amount <= 1) {
            if (slot == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            return;
        }
        stack.setAmount(amount - 1);
        if (slot == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(stack);
        } else {
            player.getInventory().setItemInMainHand(stack);
        }
    }

    protected boolean matchesCustomItem(ItemStack stack, ItemStack usedItem) {
        if (stack == null || usedItem == null) {
            return false;
        }
        if (stack.getType() != usedItem.getType()) {
            return false;
        }
        String usedId = CustomItemData.getId(usedItem);
        String stackId = CustomItemData.getId(stack);
        if (usedId != null) {
            return usedId.equals(stackId);
        }
        return stackId == null;
    }

    protected ItemStack resolveInteractItem(PlayerInteractEvent event, Player player) {
        ItemStack item = event.getItem();
        if (item != null && item.getType() != Material.AIR) {
            return item;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == EquipmentSlot.OFF_HAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            return offhand != null && offhand.getType() != Material.AIR ? offhand : null;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        return main != null && main.getType() != Material.AIR ? main : null;
    }

    protected void refreshInvisibility(Player player, GameSession session) {
        if (!session.isParticipant(player.getUniqueId())) {
            return;
        }
        if (!session.isInArenaWorld(player.getWorld())) {
            showArmorForPlayer(player, session);
            return;
        }
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            hideArmorForPlayer(player, session);
        } else {
            showArmorForPlayer(player, session);
        }
    }

    protected void syncInvisibilityForViewer(Player viewer, GameSession session) {
        if (!session.isParticipant(viewer.getUniqueId())) {
            return;
        }
        if (!session.isInArenaWorld(viewer.getWorld())) {
            return;
        }
        for (UUID targetId : session.getAssignments().keySet()) {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || target.equals(viewer)) {
                continue;
            }
            if (!session.isInArenaWorld(target.getWorld())) {
                continue;
            }
            if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                viewer.sendEquipmentChange(target, buildInvisibleEquipment(target));
            } else {
                viewer.sendEquipmentChange(target, buildVisibleEquipment(target));
            }
        }
    }

    protected void hideArmorForPlayer(Player target, GameSession session) {
        sendArmorUpdate(target, session, buildInvisibleEquipment(target));
    }

    protected void showArmorForPlayer(Player target, GameSession session) {
        sendArmorUpdate(target, session, buildVisibleEquipment(target));
    }

    protected Map<EquipmentSlot, ItemStack> buildInvisibleEquipment(Player target) {
        Map<EquipmentSlot, ItemStack> hidden = new EnumMap<>(EquipmentSlot.class);
        hidden.put(EquipmentSlot.HEAD, new ItemStack(Material.AIR));
        hidden.put(EquipmentSlot.CHEST, new ItemStack(Material.AIR));
        hidden.put(EquipmentSlot.LEGS, new ItemStack(Material.AIR));
        hidden.put(EquipmentSlot.FEET, new ItemStack(Material.AIR));
        ItemStack mainHand = target.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() == Material.SHIELD) {
            hidden.put(EquipmentSlot.HAND, new ItemStack(Material.AIR));
        }
        ItemStack offHand = target.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == Material.SHIELD) {
            hidden.put(EquipmentSlot.OFF_HAND, new ItemStack(Material.AIR));
        }
        return hidden;
    }

    protected Map<EquipmentSlot, ItemStack> buildVisibleEquipment(Player target) {
        ItemStack[] armor = target.getInventory().getArmorContents();
        Map<EquipmentSlot, ItemStack> visible = new EnumMap<>(EquipmentSlot.class);
        visible.put(EquipmentSlot.FEET, cloneOrAir(armor, 0));
        visible.put(EquipmentSlot.LEGS, cloneOrAir(armor, 1));
        visible.put(EquipmentSlot.CHEST, cloneOrAir(armor, 2));
        visible.put(EquipmentSlot.HEAD, cloneOrAir(armor, 3));
        visible.put(EquipmentSlot.HAND, cloneOrAir(target.getInventory().getItemInMainHand()));
        visible.put(EquipmentSlot.OFF_HAND, cloneOrAir(target.getInventory().getItemInOffHand()));
        return visible;
    }

    protected ItemStack cloneOrAir(ItemStack[] armor, int index) {
        if (armor == null || index < 0 || index >= armor.length) {
            return new ItemStack(Material.AIR);
        }
        ItemStack item = armor[index];
        return cloneOrAir(item);
    }

    protected ItemStack cloneOrAir(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return new ItemStack(Material.AIR);
        }
        return item.clone();
    }

    protected void sendArmorUpdate(Player target, GameSession session, Map<EquipmentSlot, ItemStack> equipment) {
        for (UUID viewerId : session.getAssignments().keySet()) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || viewer.equals(target)) {
                continue;
            }
            if (!session.isInArenaWorld(viewer.getWorld())) {
                continue;
            }
            viewer.sendEquipmentChange(target, equipment);
        }
    }

    protected int placeBridgeBlocks(GameSession session,
                                  TeamColor team,
                                  Location location,
                                  Vector velocity,
                                  int width,
                                  int maxCount) {
        if (team == null || maxCount <= 0) {
            return 0;
        }
        Vector direction = velocity.clone();
        direction.setY(0);
        if (direction.lengthSquared() < 0.001) {
            direction = new Vector(0, 0, 1);
        } else {
            direction.normalize();
        }
        Location anchor = location.clone().subtract(direction.clone().multiply(1.2));
        Block base = anchor.getBlock().getRelative(0, -1, 0);
        if (base.getY() < base.getWorld().getMinHeight()) {
            return 0;
        }
        Vector right = new Vector(-direction.getZ(), 0, direction.getX());
        int half = width / 2;
        int placed = 0;
        int baseX = base.getX();
        int baseY = base.getY();
        int baseZ = base.getZ();
        for (int offset = -half; offset <= half && placed < maxCount; offset++) {
            int dx = (int) Math.round(right.getX() * offset);
            int dz = (int) Math.round(right.getZ() * offset);
            Block target = base.getWorld().getBlockAt(baseX + dx, baseY, baseZ + dz);
            if (!target.getType().isAir()) {
                continue;
            }
            BlockPoint point = new BlockPoint(target.getX(), target.getY(), target.getZ());
            if (!session.isInsideMap(point)) {
                continue;
            }
            if (session.isPlacementBlocked(point)) {
                continue;
            }
            target.setType(team.wool(), false);
            session.recordPlacedBlock(point, new ItemStack(team.wool()));
            placed++;
        }
        return placed;
    }

    protected void filterExplosionBlocks(GameSession session, List<Block> blocks, boolean limitedTypes) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        java.util.Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            BlockPoint point = new BlockPoint(block.getX(), block.getY(), block.getZ());
            if (!session.isPlacedBlock(point)) {
                iterator.remove();
                continue;
            }
            if (limitedTypes && !isFireballBreakable(block)) {
                iterator.remove();
                continue;
            }
            ItemStack drop = session.removePlacedBlockItem(point);
            dropPlacedBlock(block, drop);
            iterator.remove();
        }
    }

    protected boolean isFireballBreakable(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        if (Tag.WOOL.isTagged(type)) {
            return true;
        }
        if (Tag.PLANKS.isTagged(type) || Tag.LOGS.isTagged(type)) {
            return true;
        }
        if (type == Material.SMOOTH_BASALT) {
            return ThreadLocalRandom.current().nextDouble() < BASALT_BREAK_CHANCE;
        }
        return false;
    }

    protected boolean isWindChargeExplosion(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return false;
        }
        String type = entity.getType().name();
        return "WIND_CHARGE".equals(type) || "BREEZE_WIND_CHARGE".equals(type);
    }

    protected boolean isArmor(ItemStack item) {
        if (item == null) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    protected boolean isProtectedItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return isArmor(item)
                || TOOL_MATERIALS.contains(type)
                || WEAPON_MATERIALS.contains(type);
    }

    protected boolean isBlockedStorageItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.WOODEN_SWORD || type == Material.SHIELD || TOOL_MATERIALS.contains(type);
    }

    protected boolean isStorageInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        if (inventory.getHolder() instanceof Container) {
            return true;
        }
        return switch (inventory.getType()) {
            case CHEST, BARREL, SHULKER_BOX -> true;
            default -> false;
        };
    }

    protected boolean shouldBlockContainerMove(InventoryClickEvent event, Player player, Inventory topInventory) {
        if (event.getClickedInventory() == null) {
            return false;
        }
        if (event.getClick().isShiftClick()
                || event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (event.getClickedInventory().equals(event.getView().getBottomInventory())) {
                return isBlockedStorageItem(event.getCurrentItem());
            }
            return false;
        }
        if (event.getClickedInventory().equals(topInventory)) {
            int hotbar = event.getHotbarButton();
            if (hotbar >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbar);
                return isBlockedStorageItem(hotbarItem);
            }
            return isBlockedStorageItem(event.getCursor());
        }
        return false;
    }

    protected void scheduleEquipmentUnbreakable(Player player, GameSession session) {
        if (player == null || session == null) {
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                if (!session.isActive() || !session.isParticipant(player.getUniqueId())) {
                    return;
                }
                if (!session.isInArenaWorld(player.getWorld())) {
                    return;
                }
                enforceEquipmentUnbreakable(player);
            }
        }.runTask(bedwarsManager.getPlugin());
    }

    protected void scheduleToolTierSync(Player player, GameSession session) {
        if (player == null || session == null) {
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                if (!session.isActive() || !session.isParticipant(player.getUniqueId())) {
                    return;
                }
                if (!session.isInArenaWorld(player.getWorld())) {
                    return;
                }
                session.syncToolTiers(player);
            }
        }.runTask(bedwarsManager.getPlugin());
    }

    protected void enforceEquipmentUnbreakable(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean armorChanged = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (!isProtectedItem(piece)) {
                continue;
            }
            ItemStack updated = makeUnbreakable(piece);
            if (updated != piece) {
                armor[i] = updated;
                armorChanged = true;
            }
        }
        if (armorChanged) {
            player.getInventory().setArmorContents(armor);
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isProtectedItem(item)) {
                continue;
            }
            ItemStack updated = makeUnbreakable(item);
            if (updated != item) {
                contents[i] = updated;
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setStorageContents(contents);
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (isProtectedItem(offhand)) {
            player.getInventory().setItemInOffHand(makeUnbreakable(offhand));
        }
    }

    protected boolean isOutsideRunningBedwarsGame(Player player, GameSession session) {
        if (player == null || !bedwarsManager.isBedwarsWorld(player.getWorld().getName())) {
            return false;
        }
        if (session == null) {
            return true;
        }
        if (!session.isInArenaWorld(player.getWorld())) {
            return true;
        }
        return !session.isRunning() || !session.isParticipant(player.getUniqueId());
    }

    protected boolean isProtectedLobbyItemFrame(org.bukkit.entity.Entity entity, Player player, GameSession session) {
        if (!isItemFrameEntity(entity) || player == null) {
            return false;
        }
        return isOutsideRunningBedwarsGame(player, session) && !canEditProtectedBedwarsWorld(player, session);
    }

    protected boolean isItemFrameEntity(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return false;
        }
        EntityType type = entity.getType();
        return type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME;
    }

    protected boolean canEditProtectedBedwarsWorld(Player player, GameSession session) {
        if (player == null || !bedwarsManager.isBedwarsWorld(player.getWorld().getName())) {
            return false;
        }
        if (player.isOp()) {
            return true;
        }
        if (!bedwarsManager.isTemporaryCreator(player.getUniqueId())) {
            return false;
        }
        return session == null || !session.isInArenaWorld(player.getWorld());
    }

    protected void applyOutsideGameBedwarsBuffs(Player player) {
        if (player == null || !bedwarsManager.isBedwarsWorld(player.getWorld().getName())) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.REGENERATION,
                60,
                0,
                false,
                false,
                false
        ), true);
    }

    protected void restoreOutsideGameBedwarsLobbyState(Player player) {
        if (player == null) {
            return;
        }
        Location lobby = resolveArenaLobbyForWorld(player.getWorld());
        if (lobby != null) {
            player.teleport(lobby);
            player.setRespawnLocation(lobby, true);
        }
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGliding(false);
    }

    protected Location resolveArenaLobbyForWorld(World world) {
        Arena arena = resolveArenaForWorld(world);
        if (arena == null) {
            return null;
        }
        Location lobby = arena.getLobbyLocation();
        if (lobby != null) {
            return lobby;
        }
        return arena.getMapLobbyLocation();
    }

    protected Arena resolveArenaForWorld(World world) {
        if (world == null) {
            return null;
        }
        String worldName = world.getName();
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        for (Arena arena : bedwarsManager.getArenas()) {
            if (arena.getWorldName() != null && arena.getWorldName().equalsIgnoreCase(worldName)) {
                return arena;
            }
        }
        return null;
    }

    protected ItemStack makeUnbreakable(ItemStack item) {
        if (item == null) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.isUnbreakable()) {
            return item;
        }
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    protected boolean tryRescuePlayerFromVoidWithTotem(Player player, GameSession session) {
        return tryActivateTotemProtection(player, session, true);
    }

    protected boolean tryActivateTotemProtection(Player player, GameSession session, boolean voidRescue) {
        if (player == null || session == null) {
            return false;
        }
        Integer totemSlot = findTotemSlot(player);
        if (totemSlot == null) {
            return false;
        }
        Location safeSpawn = null;
        if (voidRescue) {
            TeamColor team = session.getTeam(player.getUniqueId());
            if (team == null) {
                return false;
            }
            Location spawn = session.getArena().getSpawn(team);
            if (spawn == null) {
                return false;
            }
            safeSpawn = spawn.clone().add(0.0, 1.0, 0.0);
        }
        consumeTotem(player, totemSlot);
        if (safeSpawn != null) {
            player.teleport(safeSpawn);
            voidTotemFallProtection.put(player.getUniqueId(),
                    System.currentTimeMillis() + VOID_TOTEM_FALL_PROTECTION_MILLIS);
        }
        applyTotemResurrectionEffects(player);
        session.recordDamage(player.getUniqueId());
        return true;
    }

    protected void applyTotemResurrectionEffects(Player player) {
        if (player == null) {
            return;
        }
        player.setHealth(Math.min(1.0, player.getMaxHealth()));
        player.setFallDistance(0.0f);
        player.setFireTicks(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 45 * 20, 1), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 5 * 20, 1), true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40 * 20, 0), true);
        player.playEffect(EntityEffect.TOTEM_RESURRECT);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
    }

    protected Integer findTotemSlot(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == Material.TOTEM_OF_UNDYING) {
            return -2;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() == Material.TOTEM_OF_UNDYING) {
            return -1;
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            if (stack != null && stack.getType() == Material.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return null;
    }

    protected void consumeTotem(Player player, int slot) {
        if (slot == -2) {
            consumeSingleItem(player, EquipmentSlot.OFF_HAND);
            return;
        }
        if (slot == -1) {
            consumeSingleItem(player, EquipmentSlot.HAND);
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack == null || stack.getType() != Material.TOTEM_OF_UNDYING) {
            return;
        }
        int amount = stack.getAmount();
        if (amount <= 1) {
            player.getInventory().setItem(slot, null);
            return;
        }
        stack.setAmount(amount - 1);
        player.getInventory().setItem(slot, stack);
    }

    protected boolean isLethalDamage(Player player, EntityDamageEvent event) {
        if (player == null || event == null) {
            return false;
        }
        return event.getFinalDamage() >= player.getHealth();
    }

    protected boolean consumeVoidTotemFallProtection(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long expiresAt = voidTotemFallProtection.get(playerId);
        if (expiresAt == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiresAt) {
            voidTotemFallProtection.remove(playerId);
            return false;
        }
        voidTotemFallProtection.remove(playerId);
        return true;
    }

    protected Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    protected double resolveAbyssalRiftDamage(EntityDamageEvent event) {
        if (event == null) {
            return 0.0;
        }
        double damage = Math.max(event.getFinalDamage(), event.getDamage());
        if (damage > 0.0) {
            return damage;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player) {
                return 1.0;
            }
            if (byEntity.getDamager() instanceof Projectile) {
                return 1.0;
            }
        }
        return 0.0;
    }

    protected void safeHandle(String context, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE, "BedWars error in " + context, ex);
        }
    }

    protected void dropPlacedBlock(Block block, ItemStack override) {
        Material type = block.getType();
        if (type == Material.AIR) {
            return;
        }
        block.setType(Material.AIR, false);
        ItemStack drop = override != null ? override.clone() : new ItemStack(type);
        drop.setAmount(1);
        block.getWorld().dropItemNaturally(block.getLocation(), drop);
    }

    protected void dropResourceItems(PlayerDeathEvent event, GameSession session) {
        Player player = event.getEntity();
        Map<Material, Integer> totals = new HashMap<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) {
                continue;
            }
            Material type = item.getType();
            if (!RESOURCE_MATERIALS.contains(type)) {
                continue;
            }
            totals.merge(type, item.getAmount(), Integer::sum);
            contents[i] = null;
        }
        player.getInventory().setContents(contents);
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && RESOURCE_MATERIALS.contains(offhand.getType())) {
            totals.merge(offhand.getType(), offhand.getAmount(), Integer::sum);
            player.getInventory().setItemInOffHand(null);
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && RESOURCE_MATERIALS.contains(cursor.getType())) {
            totals.merge(cursor.getType(), cursor.getAmount(), Integer::sum);
            player.setItemOnCursor(null);
        }
        if (player.getOpenInventory() != null) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top instanceof CraftingInventory crafting) {
                ItemStack[] matrix = crafting.getMatrix();
                boolean changed = false;
                for (int i = 0; i < matrix.length; i++) {
                    ItemStack item = matrix[i];
                    if (item == null || !RESOURCE_MATERIALS.contains(item.getType())) {
                        continue;
                    }
                    totals.merge(item.getType(), item.getAmount(), Integer::sum);
                    matrix[i] = null;
                    changed = true;
                }
                if (changed) {
                    crafting.setMatrix(matrix);
                }
            }
        }
        event.getDrops().clear();
        event.setKeepInventory(true);
        Player recipient = null;
        if (session != null
                && player.getLastDamageCause() != null
                && player.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.VOID) {
            UUID damagerId = session.getRecentDamager(player.getUniqueId());
            if (damagerId != null) {
                Player damager = Bukkit.getPlayer(damagerId);
                if (damager != null
                        && damager.isOnline()
                        && session.isParticipant(damagerId)
                        && session.isInArenaWorld(damager.getWorld())) {
                    recipient = damager;
                }
            }
        }
        if (recipient != null) {
            for (Map.Entry<Material, Integer> entry : totals.entrySet()) {
                int amount = entry.getValue();
                if (amount <= 0) {
                    continue;
                }
                Map<Integer, ItemStack> leftovers =
                        recipient.getInventory().addItem(new ItemStack(entry.getKey(), amount));
                for (ItemStack leftover : leftovers.values()) {
                    if (leftover != null) {
                        recipient.getWorld().dropItemNaturally(recipient.getLocation(), leftover);
                    }
                }
            }
            recipient.playSound(recipient.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            sendKillLootMessage(recipient, totals);
        } else {
            totals.forEach((material, amount) -> {
                if (amount > 0) {
                    player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(material, amount));
                }
            });
        }
    }

    protected void sendKillLootMessage(Player recipient, Map<Material, Integer> totals) {
        if (recipient == null || totals == null || totals.isEmpty()) {
            return;
        }
        Component message = Component.text("Loot: ", NamedTextColor.YELLOW);
        boolean first = true;
        for (Material material : List.of(Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD)) {
            int amount = totals.getOrDefault(material, 0);
            if (amount <= 0) {
                continue;
            }
            if (!first) {
                message = message.append(Component.text(", ", NamedTextColor.GRAY));
            }
            message = message.append(Component.text(amount + " " + formatResourceName(material),
                    resourceColor(material)));
            first = false;
        }
        if (first) {
            return;
        }
        recipient.sendMessage(message);
    }

    protected String formatResourceName(Material material) {
        return switch (material) {
            case IRON_INGOT -> "Iron";
            case GOLD_INGOT -> "Gold";
            case DIAMOND -> "Diamond";
            case EMERALD -> "Emerald";
            default -> material.name();
        };
    }

    protected NamedTextColor resourceColor(Material material) {
        return switch (material) {
            case IRON_INGOT -> NamedTextColor.GRAY;
            case GOLD_INGOT -> NamedTextColor.GOLD;
            case DIAMOND -> NamedTextColor.AQUA;
            case EMERALD -> NamedTextColor.GREEN;
            default -> NamedTextColor.GRAY;
        };
    }

    protected void depositHeldItem(Player player, Inventory target, EquipmentSlot hand) {
        if (player == null || target == null) {
            return;
        }
        EquipmentSlot slot = hand != null ? hand : EquipmentSlot.HAND;
        ItemStack stack = slot == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        if (isBlockedStorageItem(stack)) {
            return;
        }
        int originalAmount = stack.getAmount();
        Map<Integer, ItemStack> leftovers = target.addItem(stack.clone());
        int remaining = 0;
        for (ItemStack leftover : leftovers.values()) {
            if (leftover != null) {
                remaining += leftover.getAmount();
            }
        }
        if (remaining >= originalAmount) {
            return;
        }
        if (remaining <= 0) {
            if (slot == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            return;
        }
        stack.setAmount(remaining);
        if (slot == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(stack);
        } else {
            player.getInventory().setItemInMainHand(stack);
        }
    }

    protected void consumeSingleItem(Player player, EquipmentSlot hand) {
        EquipmentSlot slot = hand != null ? hand : EquipmentSlot.HAND;
        ItemStack stack = slot == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        int amount = stack.getAmount();
        if (amount <= 1) {
            if (slot == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            stack.setAmount(amount - 1);
            if (slot == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(stack);
            } else {
                player.getInventory().setItemInMainHand(stack);
            }
        }
    }

    protected boolean hasAnySword(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && SWORD_MATERIALS.contains(item.getType())) {
                return true;
            }
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && SWORD_MATERIALS.contains(main.getType())) {
            return true;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand != null && SWORD_MATERIALS.contains(offhand.getType());
    }

    protected boolean hasBetterSword(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() != Material.WOODEN_SWORD && SWORD_MATERIALS.contains(item.getType())) {
                return true;
            }
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() != Material.WOODEN_SWORD && SWORD_MATERIALS.contains(main.getType())) {
            return true;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand != null && offhand.getType() != Material.WOODEN_SWORD && SWORD_MATERIALS.contains(offhand.getType());
    }

    protected void removeWoodenSword(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.WOODEN_SWORD) {
                contents[i] = null;
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setStorageContents(contents);
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == Material.WOODEN_SWORD) {
            player.getInventory().setItemInOffHand(null);
        }
    }
}
