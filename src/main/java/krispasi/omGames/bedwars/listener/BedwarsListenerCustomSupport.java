package krispasi.omGames.bedwars.listener;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.event.BedwarsMatchEventType;
import krispasi.omGames.bedwars.game.GameSession;
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

abstract class BedwarsListenerCustomSupport {
    protected final BedwarsManager bedwarsManager;
    protected static final double TNT_DAMAGE_MULTIPLIER = 0.6;
    protected static final EnumSet<Material> RESOURCE_MATERIALS = EnumSet.of(
            Material.IRON_INGOT,
            Material.GOLD_INGOT,
            Material.DIAMOND,
            Material.EMERALD
    );
    protected static final EnumSet<Material> SWORD_MATERIALS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.DIAMOND_SWORD
    );
    protected static final Set<Material> WEAPON_MATERIALS = materialSet(
            "WOODEN_SWORD",
            "STONE_SWORD",
            "IRON_SWORD",
            "DIAMOND_SWORD",
            "BOW",
            "CROSSBOW",
            "MACE",
            "NETHERITE_SPEAR",
            "TRIDENT",
            "SHIELD"
    );
    protected static final EnumSet<Material> TOOL_MATERIALS = EnumSet.of(
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE,
            Material.SHEARS
    );
    protected static final double DEFENDER_SPAWN_Y_OFFSET = 1.0;
    protected static final double BED_BUG_SPAWN_Y_OFFSET = 1.0;
    protected static final double CRYSTAL_SPAWN_Y_OFFSET = 1.0;
    protected static final double CREEPING_CREEPER_SPAWN_Y_OFFSET = 1.0;
    protected static final double HAPPY_GHAST_SPAWN_Y_OFFSET = 1.5;
    protected static final double BASALT_BREAK_CHANCE = 0.35;
    protected static final double DEFENDER_TARGET_RANGE = 16.0;
    protected static final double SUMMON_NAME_OFFSET = 0.7;
    protected static final double SUMMON_TIMER_OFFSET = 0.45;
    protected static final String SUMMON_NAME_TAG = "bw_summon_nameplate";
    protected static final String HAPPY_GHAST_MOUNTED_TAG = "bw_happy_ghast_mounted";
    protected static final long FIREBALL_COOLDOWN_MILLIS = 500L;
    protected static final long FLAMETHROWER_COOLDOWN_MILLIS = 120L;
    protected static final double FLAMETHROWER_DEFAULT_RANGE = 6.0;
    protected static final double FLAMETHROWER_DEFAULT_SPREAD = 0.6;
    protected static final double FLAMETHROWER_HALF_ANGLE_DEGREES = 38.0;
    protected static final int FLAMETHROWER_FIRE_TICKS = 60;
    protected static final long LUNGING_SPEAR_MOVEMENT_COOLDOWN_MILLIS = 5_000L;
    protected static final long LUNGING_SPEAR_EVENT_LINK_WINDOW_MILLIS = 50L;
    protected static final long BLOCKED_LUNGING_SPEAR_VELOCITY_WINDOW_MILLIS = 250L;
    protected static final long VOID_TOTEM_FALL_PROTECTION_MILLIS = 5_000L;
    protected static final int GIGANTIFY_GROWTH_TICKS = 40;
    protected static final int GIGANTIFY_SUSTAIN_TICKS = 60;
    protected static final int GIGANTIFY_SHRINK_TICKS = 60;
    protected static final int GIGANTIFY_TOTAL_TICKS =
            GIGANTIFY_GROWTH_TICKS + GIGANTIFY_SUSTAIN_TICKS + GIGANTIFY_SHRINK_TICKS;
    protected static final int APRIL_FOOLS_BRIDGE_EGG_PILLAR_BLOCKS = 30;
    protected static final long APRIL_FOOLS_BRIDGE_EGG_PILLAR_INTERVAL_TICKS = 2L;
    protected static final double GIGANTIFY_MAX_SCALE_MULTIPLIER = 2.5;
    protected static final Particle.DustOptions GIGANTIFY_PARTICLE =
            new DustOptions(Color.fromRGB(190, 90, 255), 1.3f);
    protected static final int NUKE_TARGET_DISTANCE = 512;
    protected static final int NUKE_COUNTDOWN_CHAT_SECONDS = 15;
    protected static final String DREAM_DEFENDER_NAME = "Dream Defender";
    protected static final String BED_BUG_NAME = "Bed Bug";
    protected static final String HAPPY_GHAST_NAME = "Happy Ghast";
    protected static final String CREEPING_CREEPER_NAME = "Creeper";
    protected static final String PORTABLE_SHOPKEEPER_NAME = "Portable Shopkeeper";
    protected static final String PORTABLE_SHOPKEEPER_TAG = "bw_portable_shopkeeper";
    protected static final String PLACEABLE_BED_ITEM_ID = "placeable_bed";
    protected static final String PROXIMITY_MINE_ITEM_ID = "proximity_mine";
    protected static final String PROXIMITY_MINE_TNT_TAG_PREFIX = "bw_proximity_mine_team_";
    protected static final String RESPAWN_BEACON_ITEM_ID = "respawn_beacon";
    protected static final String GIGANTIFY_GRENADE_ITEM_ID = "gigantify_grenade";
    protected final NamespacedKey customProjectileKey;
    protected final NamespacedKey summonTeamKey;
    protected final NamespacedKey happyGhastDriverKey;
    protected final Map<UUID, BukkitTask> defenderTasks = new HashMap<>();
    protected final Map<UUID, BukkitTask> summonNameTasks = new HashMap<>();
    protected final Map<UUID, SummonNameplate> summonNameplates = new HashMap<>();
    protected final Map<UUID, BukkitTask> loyaltyTridentTasks = new HashMap<>();
    protected final Map<UUID, List<ItemStack>> pendingLoyaltyTridentReturns = new HashMap<>();
    protected final Map<UUID, Long> fireballCooldowns = new HashMap<>();
    protected final Map<UUID, Long> flamethrowerCooldowns = new HashMap<>();
    protected final Map<UUID, Long> lungingSpearMovementCooldowns = new HashMap<>();
    protected final Map<UUID, Long> pendingSuccessfulLungingSpearEvents = new HashMap<>();
    protected final Map<UUID, Long> blockedLungingSpearVelocityUntil = new HashMap<>();
    protected final Map<UUID, BukkitTask> gigantifyTasks = new HashMap<>();
    protected final Map<UUID, Long> voidTotemFallProtection = new HashMap<>();

    private static Set<Material> materialSet(String... materialNames) {
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        if (materialNames == null) {
            return Collections.unmodifiableSet(materials);
        }
        for (String materialName : materialNames) {
            if (materialName == null || materialName.isBlank()) {
                continue;
            }
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
                materials.add(material);
            }
        }
        return Collections.unmodifiableSet(materials);
    }


    protected BedwarsListenerCustomSupport(BedwarsManager bedwarsManager) {
        this.bedwarsManager = bedwarsManager;
        this.customProjectileKey = new NamespacedKey(bedwarsManager.getPlugin(), "bw_custom_projectile");
        this.summonTeamKey = new NamespacedKey(bedwarsManager.getPlugin(), "bw_summon_team");
        this.happyGhastDriverKey = new NamespacedKey(bedwarsManager.getPlugin(), "bw_happy_ghast_driver");
    }

    protected abstract void setSummonTeam(org.bukkit.entity.Entity entity, TeamColor team);

    protected abstract void applySummonStats(LivingEntity entity, CustomItemDefinition custom);

    protected abstract void scheduleSummonDespawn(org.bukkit.entity.Entity entity, int lifetimeSeconds);

    protected abstract void startDefenderTargeting(IronGolem golem, TeamColor owner, GameSession session);

    protected abstract void applyHappyGhastHarness(LivingEntity entity, TeamColor team);

    protected abstract void scheduleHappyGhastHarness(LivingEntity entity, TeamColor team);

    protected abstract void setHappyGhastDriver(org.bukkit.entity.Entity entity, UUID playerId);

    protected abstract void applyEntityKnockbackResistance(LivingEntity entity, double resistance);

    protected abstract void scheduleMountHappyGhast(org.bukkit.entity.Entity entity, Player player);

    protected abstract Player findNearestEnemy(Location origin, TeamColor owner, GameSession session, double range);

    protected abstract EntityType resolveHappyGhastType();

    protected abstract boolean matchesCustomItem(ItemStack stack, ItemStack usedItem);

    protected abstract boolean isSummon(org.bukkit.entity.Entity entity);

    protected abstract TeamColor getSummonTeam(org.bukkit.entity.Entity entity);

    protected abstract void dropPlacedBlock(Block block, ItemStack override);

    protected abstract int placeBridgeBlocks(GameSession session,
                                             TeamColor team,
                                             Location location,
                                             Vector velocity,
                                             int width,
                                             int maxCount);

    protected CustomItemDefinition getCustomItem(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        CustomItemConfig config = bedwarsManager.getCustomItemConfig();
        if (config == null) {
            return null;
        }
        return config.getItem(id);
    }

    protected boolean isUseAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    protected CustomItemDefinition resolveCustomItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        CustomItemConfig config = bedwarsManager.getCustomItemConfig();
        if (config == null) {
            return null;
        }
        String customId = CustomItemData.getId(item);
        if (customId != null) {
            return config.getItem(customId);
        }
        if (item.getType() == Material.ARROW) {
            return null;
        }
        return config.findByMaterial(item.getType());
    }

    protected CustomItemDefinition resolveCustomProjectile(Fireball fireball) {
        if (fireball == null) {
            return null;
        }
        CustomItemConfig config = bedwarsManager.getCustomItemConfig();
        if (config == null) {
            return null;
        }
        PersistentDataContainer container = fireball.getPersistentDataContainer();
        String customId = container.get(customProjectileKey, PersistentDataType.STRING);
        if (customId != null) {
            return config.getItem(customId);
        }
        return null;
    }

    protected void applyFireballKnockback(Player victim, Fireball fireball, CustomItemDefinition definition) {
        double strength = definition.getKnockback();
        if (strength <= 0.0) {
            return;
        }
        Vector direction = victim.getLocation().toVector().subtract(fireball.getLocation().toVector());
        if (direction.lengthSquared() < 0.01) {
            direction = victim.getLocation().getDirection();
        }
        direction.setY(0);
        if (direction.lengthSquared() < 0.01) {
            direction = new Vector(0, 0, 1);
        } else {
            direction.normalize();
        }
        Vector knockback = direction.multiply(strength).setY(0.35 + strength * 0.15);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!victim.isOnline()) {
                    return;
                }
                Vector current = victim.getVelocity();
                victim.setVelocity(current.add(knockback));
            }
        }.runTask(bedwarsManager.getPlugin());
    }

    protected boolean isSameCustomItemInMainHand(Player player, ItemStack usedItem) {
        ItemStack main = player.getInventory().getItemInMainHand();
        return matchesCustomItem(main, usedItem);
    }

    protected void launchFireball(Player player, CustomItemDefinition custom) {
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setIsIncendiary(custom.isIncendiary());
        fireball.setYield(custom.getYield());
        fireball.setVelocity(player.getLocation().getDirection().normalize().multiply(custom.getVelocity()));
        fireball.getPersistentDataContainer().set(customProjectileKey, PersistentDataType.STRING, custom.getId());
    }

    protected void startLoyaltyTridentVoidTracker(Trident trident, UUID ownerId, ItemStack tridentItem) {
        if (trident == null || ownerId == null || tridentItem == null || tridentItem.getType() == Material.AIR) {
            return;
        }
        UUID tridentId = trident.getUniqueId();
        cancelLoyaltyTridentTracker(tridentId);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!trident.isValid() || trident.isDead()) {
                    cancelLoyaltyTridentTracker(tridentId);
                    return;
                }
                World world = trident.getWorld();
                if (world == null) {
                    cancelLoyaltyTridentTracker(tridentId);
                    return;
                }
                if (trident.getLocation().getY() > world.getMinHeight() - 1.0) {
                    return;
                }
                trident.remove();
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null && owner.isOnline()) {
                    returnLoyaltyTrident(owner, tridentItem.clone());
                } else {
                    queuePendingLoyaltyTrident(ownerId, tridentItem.clone());
                }
                cancelLoyaltyTridentTracker(tridentId);
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 1L, 1L);
        loyaltyTridentTasks.put(tridentId, task);
    }

    protected void cancelLoyaltyTridentTracker(UUID tridentId) {
        if (tridentId == null) {
            return;
        }
        BukkitTask task = loyaltyTridentTasks.remove(tridentId);
        if (task != null) {
            task.cancel();
        }
    }

    protected boolean hasLoyalty(ItemStack item) {
        if (item == null || item.getType() != Material.TRIDENT) {
            return false;
        }
        return item.getEnchantmentLevel(Enchantment.LOYALTY) > 0;
    }

    protected ItemStack resolveThrownTridentItem(Trident trident) {
        if (trident == null) {
            return null;
        }
        try {
            Method getItemStack = trident.getClass().getMethod("getItemStack");
            Object value = getItemStack.invoke(trident);
            if (value instanceof ItemStack stack) {
                return stack.clone();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method getItem = trident.getClass().getMethod("getItem");
            Object value = getItem.invoke(trident);
            if (value instanceof ItemStack stack) {
                return stack.clone();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return new ItemStack(Material.TRIDENT);
    }

    protected void returnLoyaltyTrident(Player player, ItemStack tridentItem) {
        if (player == null || tridentItem == null || tridentItem.getType() == Material.AIR) {
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(tridentItem);
        if (!overflow.isEmpty()) {
            for (ItemStack stack : overflow.values()) {
                if (stack == null || stack.getType() == Material.AIR) {
                    continue;
                }
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.0f);
    }

    protected void queuePendingLoyaltyTrident(UUID ownerId, ItemStack tridentItem) {
        if (ownerId == null || tridentItem == null || tridentItem.getType() == Material.AIR) {
            return;
        }
        pendingLoyaltyTridentReturns.computeIfAbsent(ownerId, ignored -> new ArrayList<>()).add(tridentItem.clone());
    }

    protected void deliverPendingLoyaltyTridents(Player player) {
        if (player == null) {
            return;
        }
        List<ItemStack> pending = pendingLoyaltyTridentReturns.remove(player.getUniqueId());
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (ItemStack tridentItem : pending) {
            returnLoyaltyTrident(player, tridentItem);
        }
    }

    protected boolean canUseFireball(Player player) {
        if (player == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long last = fireballCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < FIREBALL_COOLDOWN_MILLIS) {
            return false;
        }
        fireballCooldowns.put(player.getUniqueId(), now);
        return true;
    }

    protected boolean isPlaceableBedItem(ItemStack item) {
        return PLACEABLE_BED_ITEM_ID.equalsIgnoreCase(ShopItemData.getId(item));
    }

    protected BedLocation resolvePlacedBedLocation(Block block) {
        if (block == null) {
            return null;
        }
        if (!(block.getBlockData() instanceof Bed bedData)) {
            return null;
        }
        org.bukkit.block.BlockFace facing = bedData.getFacing();
        Block headBlock;
        Block footBlock;
        if (bedData.getPart() == Bed.Part.HEAD) {
            headBlock = block;
            footBlock = block.getRelative(facing.getOppositeFace());
        } else {
            footBlock = block;
            headBlock = block.getRelative(facing);
        }
        if (!(headBlock.getBlockData() instanceof Bed headData) || headData.getPart() != Bed.Part.HEAD) {
            return null;
        }
        if (!(footBlock.getBlockData() instanceof Bed footData) || footData.getPart() != Bed.Part.FOOT) {
            return null;
        }
        return new BedLocation(
                new BlockPoint(headBlock.getX(), headBlock.getY(), headBlock.getZ()),
                new BlockPoint(footBlock.getX(), footBlock.getY(), footBlock.getZ()));
    }

    protected boolean isFireballCustom(CustomItemDefinition definition) {
        if (definition == null) {
            return false;
        }
        return definition.getType() == CustomItemType.FIREBALL;
    }

    protected boolean activateRespawnBeacon(Player player, GameSession session, CustomItemDefinition custom) {
        int delaySeconds = resolveRespawnBeaconDelay(custom);
        boolean activated = session.triggerRespawnBeacon(player, delaySeconds);
        if (activated) {
            TeamColor team = session.getTeam(player.getUniqueId());
            Location origin = resolveBeaconEffectOrigin(session, team, player.getLocation());
            startBeaconEffect(origin, team, delaySeconds);
        }
        return activated;
    }

    protected boolean tryAutoActivateSoloRespawnBeacon(Player player, GameSession session) {
        if (player == null || session == null) {
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null
                || session.getBedState(team) != BedState.DESTROYED
                || session.getTeamMemberCount(team) != 1
                || !hasCustomItem(player, RESPAWN_BEACON_ITEM_ID)) {
            return false;
        }
        int delaySeconds = session.getDefaultRespawnDelaySeconds();
        if (!session.triggerSoloRespawnBeacon(player, delaySeconds)) {
            return false;
        }
        consumeCustomItem(player, RESPAWN_BEACON_ITEM_ID);
        startBeaconEffect(resolveBeaconEffectOrigin(session, team, player.getLocation()), team, delaySeconds);
        return true;
    }

    protected int resolveRespawnBeaconDelay(CustomItemDefinition custom) {
        return custom != null && custom.getLifetimeSeconds() > 0
                ? custom.getLifetimeSeconds()
                : 30;
    }

    protected Location resolveBeaconEffectOrigin(GameSession session, TeamColor team, Location fallback) {
        if (session == null) {
            return fallback;
        }
        if (team != null) {
            BedLocation bed = session.getActiveBedLocation(team);
            if (bed == null) {
                bed = session.getArena().getBeds().get(team);
            }
            World world = session.getArena().getWorld();
            if (bed != null && world != null) {
                return bed.head().toLocation(world);
            }
        }
        return fallback;
    }

    protected boolean hasCustomItem(Player player, String customId) {
        if (player == null || customId == null || customId.isBlank()) {
            return false;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (customId.equalsIgnoreCase(CustomItemData.getId(item))) {
                return true;
            }
        }
        if (customId.equalsIgnoreCase(CustomItemData.getId(player.getInventory().getItemInOffHand()))) {
            return true;
        }
        return customId.equalsIgnoreCase(CustomItemData.getId(player.getItemOnCursor()));
    }

    protected boolean consumeCustomItem(Player player, String customId) {
        if (player == null || customId == null || customId.isBlank()) {
            return false;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!customId.equalsIgnoreCase(CustomItemData.getId(item))) {
                continue;
            }
            decrementStack(contents, i, item);
            player.getInventory().setContents(contents);
            return true;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (customId.equalsIgnoreCase(CustomItemData.getId(offhand))) {
            if (offhand.getAmount() <= 1) {
                player.getInventory().setItemInOffHand(null);
            } else {
                offhand.setAmount(offhand.getAmount() - 1);
                player.getInventory().setItemInOffHand(offhand);
            }
            return true;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (!customId.equalsIgnoreCase(CustomItemData.getId(cursor))) {
            return false;
        }
        if (cursor.getAmount() <= 1) {
            player.setItemOnCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(cursor);
        }
        return true;
    }

    protected void decrementStack(ItemStack[] contents, int slot, ItemStack item) {
        if (contents == null || slot < 0 || slot >= contents.length || item == null) {
            return;
        }
        if (item.getAmount() <= 1) {
            contents[slot] = null;
            return;
        }
        item.setAmount(item.getAmount() - 1);
        contents[slot] = item;
    }

    protected boolean useFlamethrower(Player player,
                                   GameSession session,
                                   CustomItemDefinition custom,
                                   ItemStack item,
                                   EquipmentSlot hand) {
        if (player == null || item == null || custom == null) {
            return false;
        }
        if (!canUseFlamethrower(player)) {
            return false;
        }
        int uses = CustomItemData.getUses(item);
        if (uses <= 0) {
            uses = custom.getUses();
        }
        if (uses <= 0) {
            return false;
        }
        sprayFlamethrower(player, session, custom);
        uses--;
        if (uses <= 0) {
            setItemInHand(player, hand, null);
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        CustomItemData.setUses(meta, uses);
        updateUsesLore(meta, uses);
        item.setItemMeta(meta);
        setItemInHand(player, hand, item);
        return true;
    }

    protected boolean canUseFlamethrower(Player player) {
        if (player == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long last = flamethrowerCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < FLAMETHROWER_COOLDOWN_MILLIS) {
            return false;
        }
        flamethrowerCooldowns.put(player.getUniqueId(), now);
        return true;
    }

    protected boolean isLungingSpearMovementOnCooldown(Player player, ItemStack item) {
        if (player == null || item == null) {
            return false;
        }
        if (!isLungingMovementSpear(item)) {
            return false;
        }
        int nativeCooldownTicks = item.getType() != Material.AIR ? player.getCooldown(item.getType()) : 0;
        if (nativeCooldownTicks > 0) {
            sendLungingSpearCooldownMessage(player, nativeCooldownTicks * 50L);
            return true;
        }
        long now = System.currentTimeMillis();
        long last = lungingSpearMovementCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < LUNGING_SPEAR_MOVEMENT_COOLDOWN_MILLIS) {
            long remainingMillis = LUNGING_SPEAR_MOVEMENT_COOLDOWN_MILLIS - (now - last);
            sendLungingSpearCooldownMessage(player, remainingMillis);
            int remainingTicks = (int) Math.ceil(remainingMillis / 50.0);
            if (remainingTicks > 0) {
                player.setCooldown(item.getType(), remainingTicks);
                player.setCooldown(item, remainingTicks);
            }
            return true;
        }
        return false;
    }

    protected void markLungingSpearMovementUsed(Player player, ItemStack item) {
        if (player == null || item == null || !isLungingMovementSpear(item)) {
            return;
        }
        long now = System.currentTimeMillis();
        lungingSpearMovementCooldowns.put(player.getUniqueId(), now);
        pendingSuccessfulLungingSpearEvents.put(player.getUniqueId(), now + LUNGING_SPEAR_EVENT_LINK_WINDOW_MILLIS);
        player.setCooldown(item.getType(), (int) Math.ceil(LUNGING_SPEAR_MOVEMENT_COOLDOWN_MILLIS / 50.0));
        player.setCooldown(item, (int) Math.ceil(LUNGING_SPEAR_MOVEMENT_COOLDOWN_MILLIS / 50.0));
    }

    protected boolean isProximityMineItem(ItemStack item) {
        return PROXIMITY_MINE_ITEM_ID.equalsIgnoreCase(CustomItemData.getId(item));
    }

    protected TeamColor getProximityMineTntTeam(TNTPrimed tnt) {
        if (tnt == null) {
            return null;
        }
        for (String tag : tnt.getScoreboardTags()) {
            if (tag == null || !tag.startsWith(PROXIMITY_MINE_TNT_TAG_PREFIX)) {
                continue;
            }
            return TeamColor.fromKey(tag.substring(PROXIMITY_MINE_TNT_TAG_PREFIX.length()));
        }
        return null;
    }

    protected boolean isLungingMovementSpear(ItemStack item) {
        String itemId = ShopItemData.getId(item);
        return "netherite_spear".equalsIgnoreCase(itemId);
    }

    protected boolean isPendingLungingSpearSuccess(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        Long until = pendingSuccessfulLungingSpearEvents.get(playerId);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            pendingSuccessfulLungingSpearEvents.remove(playerId);
            return false;
        }
        return true;
    }

    protected void armBlockedLungingSpearVelocity(Player player) {
        if (player == null) {
            return;
        }
        blockedLungingSpearVelocityUntil.put(player.getUniqueId(),
                System.currentTimeMillis() + BLOCKED_LUNGING_SPEAR_VELOCITY_WINDOW_MILLIS);
    }

    protected void suppressBlockedLungingSpearMovement(Player player) {
        if (player == null) {
            return;
        }
        armBlockedLungingSpearVelocity(player);
        if (bedwarsManager.getPlugin() == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        for (long delay = 0L; delay <= 3L; delay++) {
            bedwarsManager.getPlugin().getServer().getScheduler().runTaskLater(bedwarsManager.getPlugin(), () -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online == null || !online.isOnline()) {
                    return;
                }
                online.setVelocity(new Vector(0.0, 0.0, 0.0));
            }, delay);
        }
    }

    protected boolean consumeBlockedLungingSpearVelocity(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        Long until = blockedLungingSpearVelocityUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            blockedLungingSpearVelocityUntil.remove(playerId);
            return false;
        }
        blockedLungingSpearVelocityUntil.remove(playerId);
        return true;
    }

    protected void sendLungingSpearCooldownMessage(Player player, long remainingMillis) {
        if (player == null) {
            return;
        }
        long clampedMillis = Math.max(0L, remainingMillis);
        double remainingSeconds = clampedMillis / 1000.0;
        Component message = Component.text(
                "Spear lunge cooldown: " + String.format(Locale.US, "%.1fs", remainingSeconds),
                NamedTextColor.RED);
        player.sendActionBar(message);
    }

    protected void sprayFlamethrower(Player player, GameSession session, CustomItemDefinition custom) {
        if (player == null || session == null || custom == null) {
            return;
        }
        double range = custom.getRange() > 0.0 ? custom.getRange() : FLAMETHROWER_DEFAULT_RANGE;
        double spread = custom.getYield() > 0.0 ? custom.getYield() : FLAMETHROWER_DEFAULT_SPREAD;
        spawnFlamethrowerParticles(player, range, spread);
        damageFlamethrowerCone(player, session, custom, range);
    }

    protected void spawnFlamethrowerParticles(Player player, double range, double spread) {
        if (player == null) {
            return;
        }
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 0.001) {
            right = new Vector(1.0, 0.0, 0.0);
        }
        right.normalize();
        Vector up = right.clone().crossProduct(forward).normalize();
        int rings = Math.max(8, (int) Math.ceil(range / 0.5));
        for (int i = 1; i <= rings; i++) {
            double distance = (range * i) / rings;
            double radius = Math.max(0.18, distance * spread * 0.35);
            Location center = eye.clone().add(forward.clone().multiply(distance));
            world.spawnParticle(Particle.FLAME,
                    center,
                    4,
                    radius * 0.35,
                    radius * 0.2,
                    radius * 0.35,
                    0.01);
            world.spawnParticle(Particle.SMOKE,
                    center,
                    2,
                    radius * 0.35,
                    radius * 0.2,
                    radius * 0.35,
                    0.0);
            for (int point = 0; point < 6; point++) {
                double angle = (Math.PI * 2.0 * point) / 6.0;
                Vector offset = right.clone().multiply(Math.cos(angle) * radius)
                        .add(up.clone().multiply(Math.sin(angle) * radius * 0.55));
                world.spawnParticle(Particle.FLAME, center.clone().add(offset), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.7f, 1.2f);
    }

    protected void damageFlamethrowerCone(Player player,
                                        GameSession session,
                                        CustomItemDefinition custom,
                                        double range) {
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        double minDot = Math.cos(Math.toRadians(FLAMETHROWER_HALF_ANGLE_DEGREES));
        TeamColor attackerTeam = session.getTeam(player.getUniqueId());
        Location searchCenter = eye.clone().add(forward.clone().multiply(range * 0.5));
        for (org.bukkit.entity.Entity raw : player.getWorld().getNearbyEntities(searchCenter, range, range, range)) {
            if (!(raw instanceof LivingEntity target) || target.equals(player) || !target.isValid()) {
                continue;
            }
            if (!isValidFlamethrowerTarget(session, attackerTeam, target)) {
                continue;
            }
            if (!isInsideFlamethrowerCone(eye, forward, target, range, minDot)) {
                continue;
            }
            if (!player.hasLineOfSight(target)) {
                continue;
            }
            target.setFireTicks(Math.max(target.getFireTicks(), FLAMETHROWER_FIRE_TICKS));
            if (custom.getDamage() > 0.0) {
                target.damage(custom.getDamage(), player);
            }
            applyFlamethrowerKnockback(target, forward, custom.getKnockback());
        }
    }

    protected boolean isValidFlamethrowerTarget(GameSession session,
                                              TeamColor attackerTeam,
                                              LivingEntity target) {
        if (session == null || target == null) {
            return false;
        }
        if (target instanceof Player victim) {
            if (!session.isParticipant(victim.getUniqueId())
                    || victim.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || session.hasRespawnProtection(victim.getUniqueId())) {
                return false;
            }
            TeamColor victimTeam = session.getTeam(victim.getUniqueId());
            return attackerTeam == null || victimTeam == null || attackerTeam != victimTeam;
        }
        if (!isSummon(target)) {
            return false;
        }
        TeamColor ownerTeam = getSummonTeam(target);
        return attackerTeam == null || ownerTeam == null || attackerTeam != ownerTeam;
    }

    protected boolean isInsideFlamethrowerCone(Location eye,
                                             Vector forward,
                                             LivingEntity target,
                                             double range,
                                             double minDot) {
        if (eye == null || forward == null || target == null) {
            return false;
        }
        Location targetLocation = target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0);
        Vector toTarget = targetLocation.toVector().subtract(eye.toVector());
        double distance = toTarget.length();
        if (distance <= 0.01 || distance > range) {
            return false;
        }
        Vector normalized = toTarget.multiply(1.0 / distance);
        return normalized.dot(forward) >= minDot;
    }

    protected void applyFlamethrowerKnockback(LivingEntity target, Vector forward, double amount) {
        if (target == null || forward == null || amount <= 0.0) {
            return;
        }
        Vector push = forward.clone().setY(0.0);
        if (push.lengthSquared() < 0.001) {
            return;
        }
        Vector velocity = push.normalize().multiply(amount).setY(0.08);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!target.isValid()) {
                    return;
                }
                target.setVelocity(target.getVelocity().add(velocity));
            }
        }.runTask(bedwarsManager.getPlugin());
    }

    protected boolean useBridgeBuilder(Player player,
                                     GameSession session,
                                     CustomItemDefinition custom,
                                     PlayerInteractEvent event) {
        if (player == null || session == null || custom == null || event == null) {
            return false;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        Block clicked = event.getClickedBlock();
        BlockFace clickedFace = event.getBlockFace();
        if (clicked == null || clickedFace == null) {
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Block anchor = clicked.getRelative(clickedFace);
        BlockPoint anchorPoint = new BlockPoint(anchor.getX(), anchor.getY(), anchor.getZ());
        if (!session.isInsideMap(anchorPoint)) {
            player.sendMessage(Component.text("You cannot place a bridge builder outside the map.", NamedTextColor.RED));
            return false;
        }
        if (session.isPlacementBlocked(anchorPoint) || !anchor.getType().isAir()) {
            player.sendMessage(Component.text("You cannot place a bridge builder here.", NamedTextColor.RED));
            return false;
        }
        int length = Math.max(1, custom.getMaxBlocks());
        int openingWidth = Math.max(1, custom.getBridgeWidth());
        int shellWidth = openingWidth + 2;
        int shellHalf = shellWidth / 2;
        int shellHeight = openingWidth + 2;
        double blocksPerSecond = resolveBridgeBuilderBlocksPerSecond(custom);
        double blocksPerTick = blocksPerSecond / 20.0;
        BlockFace forward = resolveBridgeBuilderForward(player);
        BlockFace rightFace = rotateClockwiseBridgeBuilderFace(forward);
        ItemStack pistonDrop = new ItemStack(Material.PISTON);
        Material blockType = Material.END_STONE;
        ItemStack record = new ItemStack(blockType);
        anchor.setType(Material.PISTON, false);
        if (anchor.getBlockData() instanceof Directional directional) {
            directional.setFacing(forward);
            anchor.setBlockData(directional, false);
        }
        session.recordPlacedBlock(anchorPoint, pistonDrop);
        new BukkitRunnable() {
            private int step = 0;
            private double budget = 0.0;

            @Override
            public void run() {
                if (!session.isRunning() || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (step >= length) {
                    cancel();
                    return;
                }
                budget += blocksPerTick;
                while (budget >= 1.0 && step < length) {
                    int baseX = anchor.getX() + forward.getModX() * step;
                    int baseY = anchor.getY();
                    int baseZ = anchor.getZ() + forward.getModZ() * step;
                    for (int y = 0; y < shellHeight; y++) {
                        for (int offset = -shellHalf; offset <= shellHalf; offset++) {
                            boolean border = y == 0
                                    || y == shellHeight - 1
                                    || Math.abs(offset) == shellHalf;
                            if (!border) {
                                continue;
                            }
                            int dx = rightFace.getModX() * offset;
                            int dz = rightFace.getModZ() * offset;
                            Block target = anchor.getWorld().getBlockAt(baseX + dx, baseY + y, baseZ + dz);
                            if (!target.getType().isAir()) {
                                continue;
                            }
                            BlockPoint point = new BlockPoint(target.getX(), target.getY(), target.getZ());
                            if (!session.isInsideMap(point) || session.isPlacementBlocked(point)) {
                                continue;
                            }
                            target.setType(blockType, false);
                            session.recordPlacedBlock(point, record);
                        }
                    }
                    step++;
                    budget -= 1.0;
                }
                if (step >= length) {
                    cancel();
                }
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 0L, 1L);
        return true;
    }

    protected double resolveBridgeBuilderBlocksPerSecond(CustomItemDefinition custom) {
        if (custom == null) {
            return 8.0;
        }
        double configured = custom.getSpeed();
        if (configured <= 0.0) {
            configured = 8.0;
        }
        return Math.max(0.1, configured);
    }

    protected BlockFace resolveBridgeBuilderForward(Player player) {
        if (player == null) {
            return BlockFace.SOUTH;
        }
        Vector direction = player.getLocation().getDirection();
        if (Math.abs(direction.getX()) >= Math.abs(direction.getZ())) {
            return direction.getX() >= 0.0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return direction.getZ() >= 0.0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    protected BlockFace rotateClockwiseBridgeBuilderFace(BlockFace face) {
        if (face == null) {
            return BlockFace.WEST;
        }
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> BlockFace.WEST;
        };
    }

    protected boolean useBridgeZapper(Player player,
                                    GameSession session,
                                    CustomItemDefinition custom,
                                    PlayerInteractEvent event) {
        if (player == null || session == null || custom == null || event == null) {
            return false;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return false;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return false;
        }
        BlockPoint start = new BlockPoint(clicked.getX(), clicked.getY(), clicked.getZ());
        if (!session.isPlacedBlock(start)) {
            player.sendMessage(Component.text("You can only zap player-placed blocks.", NamedTextColor.RED));
            return false;
        }
        int maxBlocks = Math.max(1, custom.getMaxBlocks());
        Vector direction = player.getLocation().getDirection();
        int stepX = 0;
        int stepZ = 0;
        if (Math.abs(direction.getX()) >= Math.abs(direction.getZ())) {
            stepX = direction.getX() >= 0 ? 1 : -1;
        } else {
            stepZ = direction.getZ() >= 0 ? 1 : -1;
        }
        int x = clicked.getX();
        int y = clicked.getY();
        int z = clicked.getZ();
        for (int i = 0; i < maxBlocks; i++) {
            Block block = clicked.getWorld().getBlockAt(x, y, z);
            BlockPoint point = new BlockPoint(x, y, z);
            if (!session.isPlacedBlock(point)) {
                break;
            }
            ItemStack drop = session.removePlacedBlockItem(point);
            dropPlacedBlock(block, drop);
            x += stepX;
            z += stepZ;
        }
        return true;
    }

    protected boolean activateTacticalNuke(Player player,
                                         GameSession session,
                                         CustomItemDefinition custom,
                                         PlayerInteractEvent event) {
        if (player == null || session == null || custom == null) {
            return false;
        }
        Block target = resolveTargetBlock(player, event);
        if (target == null) {
            player.sendMessage(Component.text("No target block in sight.", NamedTextColor.RED));
            return false;
        }
        BlockPoint centerPoint = new BlockPoint(target.getX(), target.getY(), target.getZ());
        if (!session.isInsideMap(centerPoint)) {
            player.sendMessage(Component.text("Target must be inside the map.", NamedTextColor.RED));
            return false;
        }
        int radius = Math.max(1, Math.round(custom.getYield() > 0.0f ? custom.getYield() : 30.0f));
        int countdown = custom.getLifetimeSeconds() > 0 ? custom.getLifetimeSeconds() : 60;
        World world = target.getWorld();
        Map<BlockPoint, String> originals = new HashMap<>();
        Map<BlockPoint, ItemStack[]> containerContents = new HashMap<>();
        int cx = target.getX();
        int cy = target.getY();
        int cz = target.getZ();
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
        broadcastNukeTitle(session, countdown);
        startNukeBeaconEffect(session, target.getLocation(), countdown);
        new BukkitRunnable() {
            private int remaining = countdown;

            @Override
            public void run() {
                GameSession active = bedwarsManager.getActiveSession();
                if (active != session || !session.isRunning()) {
                    restoreNukeBlocks(world, originals, containerContents, Set.of());
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    detonateTacticalNuke(session, player, target.getLocation(), radius, originals, containerContents);
                    cancel();
                    return;
                }
                broadcastNukeActionBar(session, remaining);
                if (remaining <= NUKE_COUNTDOWN_CHAT_SECONDS) {
                    broadcastNukeCountdown(session, remaining, target.getLocation());
                }
                remaining--;
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 0L, 20L);
        return true;
    }

    protected boolean shouldHighlightNukeBlock(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        if (type.isAir() || type == Material.DIAMOND_BLOCK || type == Material.EMERALD_BLOCK) {
            return false;
        }
        if (isExcludedNukeHighlightType(type)) {
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

    protected boolean isExcludedNukeHighlightType(Material type) {
        String name = type.name();
        return name.endsWith("_STAIRS")
                || name.endsWith("_SLAB")
                || name.endsWith("_BED");
    }

    protected Block resolveTargetBlock(Player player, PlayerInteractEvent event) {
        if (event != null && event.getClickedBlock() != null) {
            return event.getClickedBlock();
        }
        if (player == null || player.getWorld() == null) {
            return null;
        }
        org.bukkit.util.RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                NUKE_TARGET_DISTANCE);
        if (result == null) {
            return null;
        }
        return result.getHitBlock();
    }

    protected void broadcastNukeTitle(GameSession session, int countdown) {
        if (session == null) {
            return;
        }
        Component title = Component.text("TACTICAL NUKE ACTIVATED", NamedTextColor.RED);
        Component subtitle = Component.text("Explosion in " + formatNukeTime(countdown), NamedTextColor.YELLOW);
        Title display = Title.title(title, subtitle, GameSession.sharedTitleTimes());
        for (UUID playerId : session.getAssignments().keySet()) {
            Player target = Bukkit.getPlayer(playerId);
            if (target != null) {
                target.showTitle(display);
                target.sendMessage(Component.text("Tactical nuke activated.", NamedTextColor.RED));
            }
        }
    }

    protected void broadcastNukeActionBar(GameSession session, int seconds) {
        if (session == null) {
            return;
        }
        Component message = Component.text("TACTICAL NUKE: " + seconds + "s", NamedTextColor.RED);
        for (UUID playerId : session.getAssignments().keySet()) {
            Player target = Bukkit.getPlayer(playerId);
            if (target != null) {
                target.sendActionBar(message);
            }
        }
    }

    protected void broadcastNukeCountdown(GameSession session, int seconds, Location origin) {
        Component message = Component.text("Tactical Nuke: " + seconds + "s", NamedTextColor.RED);
        for (UUID playerId : session.getAssignments().keySet()) {
            Player target = Bukkit.getPlayer(playerId);
            if (target != null) {
                target.sendMessage(message);
                if (origin != null) {
                    target.playSound(origin, Sound.BLOCK_NOTE_BLOCK_PLING, 2.0f, 1.2f);
                }
            }
        }
    }

    protected String formatNukeTime(int seconds) {
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

    protected void startNukeBeaconEffect(GameSession session, Location origin, int durationSeconds) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        DustOptions dust = new DustOptions(org.bukkit.Color.RED, 1.7f);
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                GameSession active = bedwarsManager.getActiveSession();
                if (active != session || session == null || !session.isRunning()) {
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
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 0L, 5L);
    }

    protected void detonateTacticalNuke(GameSession session,
                                      Player activator,
                                      Location center,
                                      int radius,
                                      Map<BlockPoint, String> originals,
                                      Map<BlockPoint, ItemStack[]> containerContents) {
        if (session == null || center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        Set<BlockPoint> removed = new HashSet<>();
        for (Map.Entry<BlockPoint, String> entry : originals.entrySet()) {
            BlockPoint point = entry.getKey();
            if (session.isPlacedBlock(point)) {
                Block block = world.getBlockAt(point.x(), point.y(), point.z());
                ItemStack drop = session.removePlacedBlockItem(point);
                dropPlacedBlock(block, drop);
                removed.add(point);
            }
        }
        restoreNukeBlocks(world, originals, containerContents, removed);
        double radiusSquared = radius * radius;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player target) {
                if (!session.isParticipant(target.getUniqueId())
                        || target.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
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
                if (activator != null) {
                    target.damage(1000.0, activator);
                    session.recordCombat(activator.getUniqueId(), target.getUniqueId());
                } else {
                    target.setHealth(0.0);
                }
            } else {
                entity.damage(1000.0, activator);
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

    protected void restoreNukeBlocks(World world,
                                   Map<BlockPoint, String> originals,
                                   Map<BlockPoint, ItemStack[]> containerContents,
                                   Set<BlockPoint> removed) {
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

    protected void startBeaconEffect(Location origin, TeamColor team, int durationSeconds) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        org.bukkit.Color color = team != null ? team.dyeColor().getColor() : org.bukkit.Color.WHITE;
        DustOptions dust = new DustOptions(color, 1.5f);
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                GameSession session = bedwarsManager.getActiveSession();
                if (session == null || !session.isRunning()) {
                    cancel();
                    return;
                }
                if (ticks >= durationSeconds * 20) {
                    cancel();
                    return;
                }
                Location base = origin.clone().add(0.5, 0.2, 0.5);
                for (double y = 0; y <= 12; y += 0.6) {
                    base.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, y, 0),
                            2, 0.02, 0.02, 0.02, 0.0, dust);
                }
                ticks += 5;
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 0L, 5L);
    }

    protected void setItemInHand(Player player, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
    }

    protected void updateUsesLore(ItemMeta meta, int uses) {
        if (meta == null) {
            return;
        }
        List<Component> lore = meta.lore();
        if (lore == null) {
            lore = new java.util.ArrayList<>();
        } else {
            lore = new java.util.ArrayList<>(lore);
        }
        Component line = Component.text("Uses: " + uses, NamedTextColor.GRAY);
        boolean replaced = false;
        for (int i = 0; i < lore.size(); i++) {
            Component existing = lore.get(i);
            if (existing == null) {
                continue;
            }
            String plain = PlainTextComponentSerializer.plainText().serialize(existing);
            if (plain != null && plain.toLowerCase(Locale.ROOT).startsWith("uses:")) {
                lore.set(i, line);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            lore.add(line);
        }
        meta.lore(lore);
    }

    protected void launchBridgeEgg(Player player, GameSession session, CustomItemDefinition custom) {
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null) {
            return;
        }
        if (session.getActiveMatchEvent() == BedwarsMatchEventType.APRIL_FOOLS) {
            launchAprilFoolsBridgeEggPillar(player, session, team);
            return;
        }
        int maxBlocks = Math.max(1, custom.getMaxBlocks());
        int width = Math.max(1, custom.getBridgeWidth());
        Egg egg = player.launchProjectile(Egg.class);
        egg.setVelocity(player.getLocation().getDirection().normalize().multiply(custom.getVelocity()));
        new BukkitRunnable() {
            private int placed;

            @Override
            public void run() {
                if (!egg.isValid() || egg.isDead()) {
                    cancel();
                    return;
                }
                if (!session.isRunning() || !session.isInArenaWorld(egg.getWorld())) {
                    egg.remove();
                    cancel();
                    return;
                }
                int remaining = maxBlocks - placed;
                if (remaining <= 0) {
                    egg.remove();
                    cancel();
                    return;
                }
                placed += placeBridgeBlocks(session, team, egg.getLocation(), egg.getVelocity(), width, remaining);
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 0L, 1L);
    }

    protected void launchAprilFoolsBridgeEggPillar(Player player, GameSession session, TeamColor team) {
        if (player == null || session == null || team == null) {
            return;
        }
        Location start = player.getLocation();
        World world = start.getWorld();
        if (world == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        int columnX = start.getBlockX();
        int columnY = start.getBlockY();
        int columnZ = start.getBlockZ();
        Material wool = team.wool();
        new BukkitRunnable() {
            private int placed;

            @Override
            public void run() {
                Player online = Bukkit.getPlayer(playerId);
                GameSession active = bedwarsManager.getActiveSession();
                if (online == null
                        || !online.isOnline()
                        || online.isDead()
                        || active == null
                        || active != session
                        || !active.isRunning()
                        || !active.isParticipant(playerId)
                        || active.getTeam(playerId) != team
                        || !active.isInArenaWorld(online.getWorld())) {
                    cancel();
                    return;
                }
                if (placed >= APRIL_FOOLS_BRIDGE_EGG_PILLAR_BLOCKS) {
                    online.setFallDistance(0.0f);
                    cancel();
                    return;
                }
                int targetY = columnY + placed;
                BlockPoint pillarPoint = new BlockPoint(columnX, targetY, columnZ);
                BlockPoint feetPoint = new BlockPoint(columnX, targetY + 1, columnZ);
                BlockPoint headPoint = new BlockPoint(columnX, targetY + 2, columnZ);
                if (!active.isInsideMap(pillarPoint)
                        || !active.isInsideMap(feetPoint)
                        || !active.isInsideMap(headPoint)
                        || active.isPlacementBlocked(pillarPoint)) {
                    cancel();
                    return;
                }
                Block pillarBlock = online.getWorld().getBlockAt(columnX, targetY, columnZ);
                Block feetBlock = online.getWorld().getBlockAt(columnX, targetY + 1, columnZ);
                Block headBlock = online.getWorld().getBlockAt(columnX, targetY + 2, columnZ);
                if (!pillarBlock.getType().isAir()
                        || !feetBlock.getType().isAir()
                        || !headBlock.getType().isAir()) {
                    cancel();
                    return;
                }
                pillarBlock.setType(wool, false);
                active.recordPlacedBlock(pillarPoint, new ItemStack(wool));
                Location next = new Location(online.getWorld(),
                        columnX + 0.5,
                        targetY + 1.0,
                        columnZ + 0.5,
                        online.getLocation().getYaw(),
                        online.getLocation().getPitch());
                online.setVelocity(new Vector(0.0, 0.0, 0.0));
                online.teleport(next);
                online.setFallDistance(0.0f);
                online.getWorld().playSound(
                        pillarBlock.getLocation().add(0.5, 0.5, 0.5),
                        Sound.BLOCK_WOOL_PLACE,
                        0.9f,
                        1.1f
                );
                placed++;
            }
        }.runTaskTimer(
                bedwarsManager.getPlugin(),
                0L,
                APRIL_FOOLS_BRIDGE_EGG_PILLAR_INTERVAL_TICKS
        );
    }

    protected boolean launchBedBug(Player player, GameSession session, CustomItemDefinition custom) {
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.setVelocity(player.getLocation().getDirection().normalize().multiply(custom.getVelocity()));
        snowball.getPersistentDataContainer().set(customProjectileKey, PersistentDataType.STRING, custom.getId());
        setSummonTeam(snowball, team);
        return true;
    }

    protected boolean launchGigantifyGrenade(Player player, GameSession session, CustomItemDefinition custom) {
        if (player == null || session == null || custom == null) {
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Snowball snowball = player.launchProjectile(Snowball.class);
        snowball.setVelocity(player.getLocation().getDirection().normalize().multiply(custom.getVelocity()));
        snowball.setGravity(false);
        snowball.getPersistentDataContainer().set(customProjectileKey, PersistentDataType.STRING, custom.getId());
        setSummonTeam(snowball, team);
        player.playSound(player.getLocation(), Sound.ENTITY_EGG_THROW, 1.0f, 0.95f);
        trackGigantifyProjectileLifetime(snowball, session, Math.max(40L, custom.getLifetimeSeconds() * 20L));
        return true;
    }

    protected void trackGigantifyProjectileLifetime(Snowball snowball, GameSession session, long maxLifetimeTicks) {
        if (snowball == null || session == null) {
            return;
        }
        new BukkitRunnable() {
            private long livedTicks;

            @Override
            public void run() {
                if (!snowball.isValid() || snowball.isDead()) {
                    cancel();
                    return;
                }
                GameSession active = bedwarsManager.getActiveSession();
                if (active == null || active != session || !active.isRunning() || !active.isInArenaWorld(snowball.getWorld())) {
                    snowball.remove();
                    cancel();
                    return;
                }
                livedTicks++;
                if (livedTicks >= maxLifetimeTicks || isOutsideMapBounds(session, snowball.getLocation())) {
                    snowball.remove();
                    cancel();
                }
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 1L, 1L);
    }

    protected boolean isOutsideMapBounds(GameSession session, Location location) {
        if (session == null || location == null || location.getWorld() == null) {
            return true;
        }
        return !session.isInsideMap(new BlockPoint(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    protected void handleGigantifyGrenadeHit(GameSession session, Snowball snowball, ProjectileHitEvent event) {
        if (session == null || snowball == null) {
            return;
        }
        try {
            if (!(event.getHitEntity() instanceof Player target)) {
                return;
            }
            if (!session.isParticipant(target.getUniqueId())
                    || target.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || !session.isInArenaWorld(target.getWorld())) {
                return;
            }
            Player thrower = snowball.getShooter() instanceof Player player ? player : null;
            if (thrower == null
                    || !session.isParticipant(thrower.getUniqueId())
                    || !session.isInArenaWorld(thrower.getWorld())
                    || thrower.getUniqueId().equals(target.getUniqueId())) {
                return;
            }
            TeamColor throwerTeam = getSummonTeam(snowball);
            if (throwerTeam == null) {
                throwerTeam = session.getTeam(thrower.getUniqueId());
            }
            TeamColor targetTeam = session.getTeam(target.getUniqueId());
            if (throwerTeam != null && targetTeam != null && throwerTeam == targetTeam) {
                return;
            }
            if (!applyGigantifyEffect(target, session)) {
                return;
            }
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.9f, 1.3f);
            target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0.0, 1.0, 0.0),
                    6, 0.45, 0.6, 0.45, 0.02);
            target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation().add(0.0, 1.0, 0.0),
                    18, 0.5, 0.8, 0.5, 0.04);
        } finally {
            snowball.remove();
        }
    }

    protected boolean applyGigantifyEffect(Player target, GameSession session) {
        if (target == null || session == null) {
            return false;
        }
        UUID targetId = target.getUniqueId();
        if (gigantifyTasks.containsKey(targetId)) {
            return false;
        }
        BukkitRunnable runnable = new BukkitRunnable() {
            private int elapsedTicks;

            @Override
            public void run() {
                Player online = Bukkit.getPlayer(targetId);
                GameSession active = bedwarsManager.getActiveSession();
                if (online == null
                        || !online.isOnline()
                        || online.isDead()
                        || active == null
                        || active != session
                        || !active.isActive()
                        || !active.isParticipant(targetId)
                        || !active.isInArenaWorld(online.getWorld())) {
                    clearGigantifyEffect(targetId, active != null && active == session ? active : null, true);
                    return;
                }
                if (elapsedTicks >= GIGANTIFY_TOTAL_TICKS) {
                    clearGigantifyEffect(online, session, true);
                    return;
                }
                double baseScale = session.getEffectivePlayerScale(online);
                applyGigantifyScale(online, baseScale * resolveGigantifyScaleMultiplier(elapsedTicks));
                spawnGigantifyParticles(online, elapsedTicks);
                elapsedTicks++;
            }
        };
        BukkitTask task = runnable.runTaskTimer(bedwarsManager.getPlugin(), 0L, 1L);
        gigantifyTasks.put(targetId, task);
        return true;
    }

    protected double resolveGigantifyScaleMultiplier(int elapsedTicks) {
        if (elapsedTicks < 0) {
            return 1.0;
        }
        if (elapsedTicks < GIGANTIFY_GROWTH_TICKS) {
            double progress = elapsedTicks / (double) GIGANTIFY_GROWTH_TICKS;
            return 1.0 + ((GIGANTIFY_MAX_SCALE_MULTIPLIER - 1.0) * progress);
        }
        if (elapsedTicks < GIGANTIFY_GROWTH_TICKS + GIGANTIFY_SUSTAIN_TICKS) {
            return GIGANTIFY_MAX_SCALE_MULTIPLIER;
        }
        double shrinkTicks = elapsedTicks - GIGANTIFY_GROWTH_TICKS - GIGANTIFY_SUSTAIN_TICKS;
        double progress = Math.min(1.0, shrinkTicks / (double) GIGANTIFY_SHRINK_TICKS);
        return GIGANTIFY_MAX_SCALE_MULTIPLIER
                - ((GIGANTIFY_MAX_SCALE_MULTIPLIER - 1.0) * progress);
    }

    protected void spawnGigantifyParticles(Player player, int elapsedTicks) {
        if (player == null || elapsedTicks % 2 != 0) {
            return;
        }
        Location center = player.getLocation().add(0.0, 1.0, 0.0);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, center, 12, 0.45, 0.9, 0.45, 0.01, GIGANTIFY_PARTICLE);
        if (elapsedTicks % 10 == 0) {
            world.spawnParticle(Particle.CLOUD, center, 6, 0.35, 0.7, 0.35, 0.01);
        }
    }

    protected void clearGigantifyEffect(Player player, GameSession session, boolean restoreScale) {
        if (player == null) {
            return;
        }
        clearGigantifyEffect(player.getUniqueId(), session, restoreScale);
    }

    protected void clearGigantifyEffect(UUID playerId, GameSession session, boolean restoreScale) {
        if (playerId == null) {
            return;
        }
        BukkitTask task = gigantifyTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        if (!restoreScale) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        restorePlayerScale(player, session);
    }

    protected void restorePlayerScale(Player player, GameSession session) {
        if (player == null) {
            return;
        }
        if (session != null && session.isParticipant(player.getUniqueId())) {
            session.restorePlayerScale(player);
            return;
        }
        applyGigantifyScale(player, 1.0);
    }

    protected void applyGigantifyScale(Player player, double scale) {
        if (player == null) {
            return;
        }
        // Paper 1.21 exposes Attribute.SCALE directly. Older API targets may still need
        // a reflective fallback here if this BedWars runtime is ever backported.
        AttributeInstance attribute = player.getAttribute(Attribute.SCALE);
        if (attribute == null) {
            return;
        }
        // This is the only native server-side way to scale players. It also changes the hitbox,
        // so a purely visual-only version would require packet/disguise support outside this plugin.
        attribute.setBaseValue(Math.max(0.0625, scale));
    }

    protected record SummonNameplate(UUID nameId, UUID timerId) {
    }

    protected boolean spawnDreamDefender(Player player,
                                       GameSession session,
                                       CustomItemDefinition custom,
                                       PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Location spawn = clicked.getLocation().add(0.5, DEFENDER_SPAWN_Y_OFFSET, 0.5);
        IronGolem golem = clicked.getWorld().spawn(spawn, IronGolem.class, entity -> {
            entity.setPlayerCreated(false);
            entity.setRemoveWhenFarAway(true);
            entity.setPersistent(false);
            entity.setCanPickupItems(false);
            entity.addScoreboardTag(GameSession.DREAM_DEFENDER_TAG);
            setSummonTeam(entity, team);
        });
        applySummonStats(golem, custom);
        int lifetimeSeconds = custom != null && custom.getLifetimeSeconds() > 0
                ? custom.getLifetimeSeconds()
                : 180;
        scheduleSummonDespawn(golem, lifetimeSeconds);
        startDefenderTargeting(golem, team, session);
        return true;
    }

    protected boolean spawnCrystal(Player player,
                                 GameSession session,
                                 CustomItemDefinition custom,
                                 PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Block above = clicked.getRelative(org.bukkit.block.BlockFace.UP);
        if (!above.getType().isAir()) {
            return false;
        }
        BlockPoint point = new BlockPoint(above.getX(), above.getY(), above.getZ());
        if (!session.isInsideMap(point)) {
            player.sendMessage(Component.text("You cannot place crystals outside the map.", NamedTextColor.RED));
            return false;
        }
        if (session.isPlacementBlocked(point)) {
            player.sendMessage(Component.text("You cannot place crystals here.", NamedTextColor.RED));
            return false;
        }
        Location spawn = clicked.getLocation().add(0.5, CRYSTAL_SPAWN_Y_OFFSET, 0.5);
        EnderCrystal crystal = clicked.getWorld().spawn(spawn, EnderCrystal.class, entity -> {
            entity.setShowingBottom(false);
            entity.setPersistent(false);
            entity.addScoreboardTag(GameSession.CRYSTAL_TAG);
            setSummonTeam(entity, team);
            entity.getPersistentDataContainer().set(customProjectileKey, PersistentDataType.STRING, custom.getId());
        });
        return crystal != null;
    }

    protected boolean spawnHappyGhast(Player player,
                                    GameSession session,
                                    CustomItemDefinition custom,
                                    PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Block above = clicked.getRelative(org.bukkit.block.BlockFace.UP);
        if (!above.getType().isAir()) {
            return false;
        }
        BlockPoint point = new BlockPoint(above.getX(), above.getY(), above.getZ());
        if (!session.isInsideMap(point)) {
            player.sendMessage(Component.text("You cannot place a happy ghast outside the map.", NamedTextColor.RED));
            return false;
        }
        if (session.isPlacementBlocked(point)) {
            player.sendMessage(Component.text("You cannot place a happy ghast here.", NamedTextColor.RED));
            return false;
        }
        Location spawn = clicked.getLocation().add(0.5, HAPPY_GHAST_SPAWN_Y_OFFSET, 0.5);
        EntityType type = resolveHappyGhastType();
        org.bukkit.entity.Entity raw = clicked.getWorld().spawnEntity(spawn, type);
        if (!(raw instanceof LivingEntity entity)) {
            raw.remove();
            return false;
        }
        if (entity instanceof Mob mob) {
            mob.setRemoveWhenFarAway(true);
        }
        entity.setPersistent(false);
        entity.addScoreboardTag(GameSession.HAPPY_GHAST_TAG);
        setSummonTeam(entity, team);
        setHappyGhastDriver(entity, player.getUniqueId());
        entity.getPersistentDataContainer().set(customProjectileKey, PersistentDataType.STRING, custom.getId());
        applyHappyGhastHarness(entity, team);
        scheduleHappyGhastHarness(entity, team);
        applySummonStats(entity, custom);
        applyEntityKnockbackResistance(entity, 10.0);
        scheduleMountHappyGhast(entity, player);
        int lifetimeSeconds = custom != null && custom.getLifetimeSeconds() > 0
                ? custom.getLifetimeSeconds()
                : 180;
        scheduleSummonDespawn(entity, lifetimeSeconds);
        return true;
    }

    protected boolean spawnPortableShopkeeper(Player player,
                                            GameSession session,
                                            CustomItemDefinition custom,
                                            PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        Block above = clicked.getRelative(org.bukkit.block.BlockFace.UP);
        if (!above.getType().isAir()) {
            return false;
        }
        BlockPoint point = new BlockPoint(above.getX(), above.getY(), above.getZ());
        if (!session.isInsideMap(point)) {
            player.sendMessage(Component.text("You cannot place a portable shopkeeper outside the map.", NamedTextColor.RED));
            return false;
        }
        if (session.isPlacementBlocked(point)) {
            player.sendMessage(Component.text("You cannot place a portable shopkeeper here.", NamedTextColor.RED));
            return false;
        }
        Location spawn = clicked.getLocation().add(0.5, 1.0, 0.5);
        Villager villager = clicked.getWorld().spawn(spawn, Villager.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setCollidable(false);
            entity.setSilent(true);
            entity.setPersistent(false);
            entity.setRemoveWhenFarAway(true);
            entity.addScoreboardTag(GameSession.ITEM_SHOP_TAG);
            entity.addScoreboardTag(PORTABLE_SHOPKEEPER_TAG);
            setSummonTeam(entity, team);
        });
        int lifetimeSeconds = custom != null && custom.getLifetimeSeconds() > 0
                ? custom.getLifetimeSeconds()
                : 30;
        scheduleSummonDespawn(villager, lifetimeSeconds);
        return true;
    }

    protected void spawnCreepingCreeper(GameSession session,
                                      TeamColor team,
                                      Location location,
                                      CustomItemDefinition custom) {
        if (session == null || team == null || location == null || location.getWorld() == null) {
            return;
        }
        Creeper creeper = location.getWorld().spawn(location, Creeper.class, entity -> {
            entity.setRemoveWhenFarAway(true);
            entity.setPersistent(false);
            entity.setCanPickupItems(false);
            entity.addScoreboardTag(GameSession.CREEPING_CREEPER_TAG);
            setSummonTeam(entity, team);
        });
        if (custom != null && custom.getYield() > 0.0f) {
            creeper.setExplosionRadius(Math.max(1, Math.round(custom.getYield())));
        }
        applySummonStats(creeper, custom);
        Player target = findNearestEnemy(location, team, session, DEFENDER_TARGET_RANGE);
        if (target != null) {
            creeper.setTarget(target);
        }
        int lifetimeSeconds = custom != null && custom.getLifetimeSeconds() > 0
                ? custom.getLifetimeSeconds()
                : 20;
        scheduleSummonDespawn(creeper, lifetimeSeconds);
    }

}
