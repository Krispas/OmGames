package krispasi.omGames.bedwars.listener;

import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.item.CustomItemConfig;
import krispasi.omGames.bedwars.item.CustomItemData;
import krispasi.omGames.bedwars.item.CustomItemDefinition;
import krispasi.omGames.bedwars.item.CustomItemType;
import krispasi.omGames.bedwars.item.FireworkData;
import krispasi.omGames.bedwars.item.LobbyControlItemData;
import krispasi.omGames.bedwars.item.TeamSelectItemData;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.BedLocation;
import krispasi.omGames.bedwars.model.BedState;
import krispasi.omGames.bedwars.model.ShopType;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.gui.MapSelectMenu;
import krispasi.omGames.bedwars.gui.RotatingItemMenu;
import krispasi.omGames.bedwars.gui.ShopMenu;
import krispasi.omGames.bedwars.gui.TeamAssignMenu;
import krispasi.omGames.bedwars.gui.TeamPickMenu;
import krispasi.omGames.bedwars.gui.UpgradeShopMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.block.Action;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.EntityType;
import org.bukkit.Tag;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.Sound;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.potion.PotionEffectType;
import java.util.EnumMap;
import java.util.Locale;

/**
 * Main Bukkit listener for BedWars gameplay.
 * <p>Enforces match rules and handles GUI actions, custom items, combat, entity behavior,
 * and block placement during a {@link krispasi.omGames.bedwars.game.GameSession}.</p>
 * <p>Also applies custom projectile metadata and manages summon lifetimes.</p>
 */
public class BedwarsListener implements Listener {
    private final BedwarsManager bedwarsManager;
    private static final double TNT_DAMAGE_MULTIPLIER = 0.6;
    private static final EnumSet<Material> RESOURCE_MATERIALS = EnumSet.of(
            Material.IRON_INGOT,
            Material.GOLD_INGOT,
            Material.DIAMOND,
            Material.EMERALD
    );
    private static final EnumSet<Material> SWORD_MATERIALS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.DIAMOND_SWORD
    );
    private static final EnumSet<Material> WEAPON_MATERIALS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.DIAMOND_SWORD,
            Material.BOW,
            Material.CROSSBOW,
            Material.MACE,
            Material.NETHERITE_SPEAR,
            Material.TRIDENT,
            Material.SHIELD
    );
    private static final EnumSet<Material> TOOL_MATERIALS = EnumSet.of(
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
    private static final double DEFENDER_SPAWN_Y_OFFSET = 1.0;
    private static final double BED_BUG_SPAWN_Y_OFFSET = 1.0;
    private static final double CRYSTAL_SPAWN_Y_OFFSET = 1.0;
    private static final double HAPPY_GHAST_SPAWN_Y_OFFSET = 1.5;
    private static final double BASALT_BREAK_CHANCE = 0.35;
    private static final double DEFENDER_TARGET_RANGE = 16.0;
    private static final double SUMMON_NAME_OFFSET = 0.7;
    private static final double SUMMON_TIMER_OFFSET = 0.45;
    private static final String SUMMON_NAME_TAG = "bw_summon_nameplate";
    private static final String HAPPY_GHAST_MOUNTED_TAG = "bw_happy_ghast_mounted";
    private static final long FIREBALL_COOLDOWN_MILLIS = 500L;
    private static final long FLAMETHROWER_COOLDOWN_MILLIS = 120L;
    private static final int NUKE_TARGET_DISTANCE = 512;
    private static final int NUKE_COUNTDOWN_CHAT_SECONDS = 15;
    private static final String DREAM_DEFENDER_NAME = "Dream Defender";
    private static final String BED_BUG_NAME = "Bed Bug";
    private static final String HAPPY_GHAST_NAME = "Happy Ghast";
    private static final String CREEPING_CREEPER_NAME = "Creeper";
    private static final String PORTABLE_SHOPKEEPER_NAME = "Portable Shopkeeper";
    private static final String PORTABLE_SHOPKEEPER_TAG = "bw_portable_shopkeeper";
    private final NamespacedKey customProjectileKey;
    private final NamespacedKey summonTeamKey;
    private final Map<UUID, BukkitTask> defenderTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> summonNameTasks = new HashMap<>();
    private final Map<UUID, SummonNameplate> summonNameplates = new HashMap<>();
    private final Map<UUID, Long> fireballCooldowns = new HashMap<>();
    private final Map<UUID, Long> flamethrowerCooldowns = new HashMap<>();

    public BedwarsListener(BedwarsManager bedwarsManager) {
        this.bedwarsManager = bedwarsManager;
        this.customProjectileKey = new NamespacedKey(bedwarsManager.getPlugin(), "bw_custom_projectile");
        this.summonTeamKey = new NamespacedKey(bedwarsManager.getPlugin(), "bw_summon_team");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        safeHandle("onInventoryClick", () -> {
            Inventory topInventory = event.getView().getTopInventory();
            if (topInventory.getHolder() instanceof MapSelectMenu menu) {
                if (event.getRawSlot() >= topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
                menu.handleClick(event);
                return;
            }
            if (topInventory.getHolder() instanceof TeamAssignMenu menu) {
                if (event.getRawSlot() >= topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
                menu.handleClick(event);
            }
            if (topInventory.getHolder() instanceof RotatingItemMenu menu) {
                if (event.getRawSlot() >= topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
                menu.handleClick(event);
                return;
            }
            if (topInventory.getHolder() instanceof TeamPickMenu menu) {
                if (event.getRawSlot() >= topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
                menu.handleClick(event);
                return;
            }
            if (topInventory.getHolder() instanceof ShopMenu menu) {
                if (event.getRawSlot() >= topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
                menu.handleClick(event);
                return;
            }
            if (topInventory.getHolder() instanceof UpgradeShopMenu menu) {
                if (event.getRawSlot() >= topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
                menu.handleClick(event);
                return;
            }

            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            if (isStorageInventory(topInventory) && shouldBlockContainerMove(event, player, topInventory)) {
                event.setCancelled(true);
                return;
            }
            if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                return;
            }
            if (event.getClick().isShiftClick() && isArmor(event.getCurrentItem())) {
                event.setCancelled(true);
            }
            scheduleEquipmentUnbreakable(player, session);
            scheduleToolTierSync(player, session);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        safeHandle("onInventoryDrag", () -> {
            Inventory topInventory = event.getView().getTopInventory();
            if (topInventory.getHolder() instanceof MapSelectMenu
                    || topInventory.getHolder() instanceof TeamAssignMenu
                    || topInventory.getHolder() instanceof RotatingItemMenu
                    || topInventory.getHolder() instanceof ShopMenu
                    || topInventory.getHolder() instanceof UpgradeShopMenu) {
                event.setCancelled(true);
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            if (isStorageInventory(topInventory) && isBlockedStorageItem(event.getOldCursor())) {
                for (int rawSlot : event.getRawSlots()) {
                    if (rawSlot < topInventory.getSize()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            for (int rawSlot : event.getRawSlots()) {
                if (event.getView().getSlotType(rawSlot) == InventoryType.SlotType.ARMOR) {
                    event.setCancelled(true);
                    return;
                }
            }
            scheduleEquipmentUnbreakable(player, session);
            scheduleToolTierSync(player, session);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderChestOpen(InventoryOpenEvent event) {
        safeHandle("onEnderChestOpen", () -> {
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }
            if (event.getInventory().getType() != InventoryType.ENDER_CHEST) {
                return;
            }
            String worldName = player.getWorld().getName();
            if (!bedwarsManager.isBedwarsWorld(worldName)) {
                return;
            }
            event.setCancelled(true);
            player.sendMessage(Component.text("Ender chests are disabled in BedWars.", NamedTextColor.RED));
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBarrelOpen(InventoryOpenEvent event) {
        safeHandle("onBarrelOpen", () -> {
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }
            if (event.getInventory().getType() != InventoryType.BARREL) {
                return;
            }
            String worldName = player.getWorld().getName();
            if (!bedwarsManager.isBedwarsWorld(worldName)) {
                return;
            }
            event.setCancelled(true);
            player.sendMessage(Component.text("Barrels are disabled in BedWars.", NamedTextColor.RED));
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnderChestInteract(PlayerInteractEvent event) {
        safeHandle("onEnderChestInteract", () -> {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENDER_CHEST) {
                return;
            }
            Player player = event.getPlayer();
            String worldName = player.getWorld().getName();
            if (!bedwarsManager.isBedwarsWorld(worldName)) {
                return;
            }
            event.setCancelled(true);
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isInArenaWorld(player.getWorld())) {
                player.sendMessage(Component.text("Ender chests are disabled in BedWars.", NamedTextColor.RED));
                return;
            }
            if (!session.isParticipant(player.getUniqueId())) {
                player.sendMessage(Component.text("Ender chests are disabled in BedWars.", NamedTextColor.RED));
                return;
            }
            session.openFakeEnderChest(player);
        });
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onPlayerUseCustomItem(PlayerInteractEvent event) {
        safeHandle("onPlayerUseCustomItem", () -> {
            Action action = event.getAction();
            boolean rightClick = isUseAction(action);
            boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
            if (!rightClick && !leftClick) {
                return;
            }
            Player player = event.getPlayer();
            ItemStack item = resolveInteractItem(event, player);
            if (item == null) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session != null && session.isLobby() && session.isInArenaWorld(player.getWorld())) {
                if (event.getHand() == EquipmentSlot.OFF_HAND) {
                    return;
                }
                if (rightClick) {
                    String actionId = LobbyControlItemData.getAction(item);
                    if (actionId != null) {
                        event.setCancelled(true);
                        if (!session.isLobbyInitiator(player.getUniqueId())) {
                            return;
                        }
                        switch (actionId) {
                            case "skip" -> session.skipLobbyCountdown();
                            case "pause" -> session.toggleLobbyCountdownPause();
                            case "manage" -> new TeamAssignMenu(bedwarsManager, session).open(player);
                            default -> {
                            }
                        }
                        return;
                    }
                    if (TeamSelectItemData.isTeamSelectItem(item)) {
                        event.setCancelled(true);
                        session.openTeamPickMenu(player);
                        return;
                    }
                }
            }
            if (session == null || !session.isRunning()) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            CustomItemDefinition custom = resolveCustomItem(item);
            if (custom == null) {
                return;
            }
            if (!rightClick && custom.getType() != CustomItemType.BRIDGE_ZAPPER) {
                return;
            }
            if (event.getHand() == EquipmentSlot.OFF_HAND && isSameCustomItemInMainHand(player, item)) {
                return;
            }
            event.setCancelled(true);
            boolean consume = true;
            boolean used = switch (custom.getType()) {
                case FIREBALL -> {
                    if (!canUseFireball(player)) {
                        yield false;
                    }
                    launchFireball(player, custom);
                    yield true;
                }
                case BRIDGE_EGG -> {
                    launchBridgeEgg(player, session, custom);
                    yield true;
                }
                case BED_BUG -> {
                    yield launchBedBug(player, session, custom);
                }
                case DREAM_DEFENDER -> {
                    yield spawnDreamDefender(player, session, custom, event);
                }
                case CRYSTAL -> {
                    yield spawnCrystal(player, session, custom, event);
                }
                case HAPPY_GHAST -> {
                    yield spawnHappyGhast(player, session, custom, event);
                }
                case RESPAWN_BEACON -> {
                    yield activateRespawnBeacon(player, session, custom);
                }
                case FLAMETHROWER -> {
                    consume = false;
                    yield useFlamethrower(player, session, custom, item, event.getHand());
                }
                case BRIDGE_BUILDER -> {
                    yield useBridgeBuilder(player, session, custom);
                }
                case CREEPING_ARROW -> {
                    yield false;
                }
                case TACTICAL_NUKE -> {
                    yield activateTacticalNuke(player, session, custom, event);
                }
                case BRIDGE_ZAPPER -> {
                    yield useBridgeZapper(player, session, custom, event);
                }
                case PORTABLE_SHOPKEEPER -> {
                    yield spawnPortableShopkeeper(player, session, custom, event);
                }
                case WARDEN -> {
                    yield false;
                }
            };
            if (used && consume) {
                consumeHeldItem(player, event.getHand(), item);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        safeHandle("onEntityShootBow", () -> {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            ItemStack consumable = event.getConsumable();
            CustomItemDefinition custom = resolveCustomItem(consumable);
            if (custom == null || custom.getType() != CustomItemType.CREEPING_ARROW) {
                return;
            }
            if (!(event.getProjectile() instanceof Arrow arrow)) {
                return;
            }
            arrow.getPersistentDataContainer().set(customProjectileKey, PersistentDataType.STRING, custom.getId());
            TeamColor team = session.getTeam(player.getUniqueId());
            if (team != null) {
                setSummonTeam(arrow, team);
            }
            arrow.setDamage(0.0);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onChestQuickDeposit(PlayerInteractEvent event) {
        safeHandle("onChestQuickDeposit", () -> {
            if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
                return;
            }
            Block block = event.getClickedBlock();
            if (block == null) {
                return;
            }
            Material type = block.getType();
            if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.ENDER_CHEST) {
                return;
            }
            Player player = event.getPlayer();
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            event.setCancelled(true);
            Inventory target = null;
            if (type == Material.ENDER_CHEST) {
                target = session.getFakeEnderChest(player);
            } else if (block.getState() instanceof Container container) {
                target = container.getInventory();
            }
        if (target == null) {
            return;
        }
        depositHeldItem(player, target, event.getHand());
        scheduleToolTierSync(player, session);
    });
}

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        safeHandle("onPlayerInteractEntity", () -> {
            if (!(event.getRightClicked() instanceof Villager villager)) {
                return;
            }
            if (!villager.getScoreboardTags().contains(GameSession.ITEM_SHOP_TAG)
                    && !villager.getScoreboardTags().contains(GameSession.UPGRADES_SHOP_TAG)) {
                return;
            }
            Player player = event.getPlayer();
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            event.setCancelled(true);
            ShopType type = villager.getScoreboardTags().contains(GameSession.UPGRADES_SHOP_TAG)
                    ? ShopType.UPGRADES
                    : ShopType.ITEM;
            session.openShop(player, type);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        safeHandle("onEntityExplode", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                if (bedwarsManager.isBedwarsWorld(event.getEntity().getWorld().getName())) {
                    event.blockList().clear();
                }
                return;
            }
            if (!session.isInArenaWorld(event.getEntity().getWorld())) {
                return;
            }
            if (event.getEntity() instanceof EnderCrystal crystal) {
                CustomItemDefinition custom = getCustomEntity(crystal);
                if (custom != null && custom.getYield() > 0.0f
                        && !crystal.getScoreboardTags().contains(GameSession.CRYSTAL_EXPLOSION_TAG)) {
                    crystal.addScoreboardTag(GameSession.CRYSTAL_EXPLOSION_TAG);
                    Location location = crystal.getLocation();
                    event.setCancelled(true);
                    if (location.getWorld() != null) {
                        location.getWorld().createExplosion(location, (float) custom.getYield(), false, true, crystal);
                    }
                    crystal.remove();
                    return;
                }
            }
            if (isWindChargeExplosion(event.getEntity())) {
                event.blockList().clear();
                return;
            }
            boolean limited = event.getEntity() instanceof Fireball;
            filterExplosionBlocks(session, event.blockList(), limited);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFireworkExplode(FireworkExplodeEvent event) {
        safeHandle("onFireworkExplode", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            Firework firework = event.getEntity();
            if (session == null || !session.isRunning()) {
                return;
            }
            if (firework == null || !session.isInArenaWorld(firework.getWorld())) {
                return;
            }
            Double explosionPower = FireworkData.getExplosionPower(firework.getFireworkMeta());
            Double explosionDamage = FireworkData.getExplosionDamage(firework.getFireworkMeta());
            Double explosionKnockback = FireworkData.getExplosionKnockback(firework.getFireworkMeta());
            if (!hasCustomFireworkSettings(explosionPower, explosionDamage, explosionKnockback)) {
                return;
            }
            double radius = explosionPower != null ? Math.max(0.0, explosionPower) : 2.5;
            double damage = explosionDamage != null ? Math.max(0.0, explosionDamage) : 0.0;
            double knockback = explosionKnockback != null ? Math.max(0.0, explosionKnockback) : -1.0;
            if (radius <= 0.0 || (damage <= 0.0 && knockback <= 0.0)) {
                return;
            }
            TeamColor ownerTeam = null;
            org.bukkit.projectiles.ProjectileSource shooter = firework.getShooter();
            if (shooter instanceof Player player) {
                if (session.isParticipant(player.getUniqueId())) {
                    ownerTeam = session.getTeam(player.getUniqueId());
                }
            }
            Location origin = firework.getLocation();
            firework.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            double radiusSquared = radius * radius;
            for (Player target : origin.getWorld().getNearbyPlayers(origin, radius, radius, radius)) {
                if (!session.isParticipant(target.getUniqueId()) || target.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    continue;
                }
                if (session.hasRespawnProtection(target.getUniqueId())) {
                    continue;
                }
                if (ownerTeam != null && ownerTeam == session.getTeam(target.getUniqueId())) {
                    continue;
                }
                if (target.getLocation().distanceSquared(origin) > radiusSquared) {
                    continue;
                }
                Player damager = shooter instanceof Player ? (Player) shooter : null;
                if (damage > 0.0) {
                    if (damager != null) {
                        target.damage(damage, damager);
                        session.recordCombat(damager.getUniqueId(), target.getUniqueId());
                    } else {
                        target.damage(damage);
                    }
                }
                double strength = knockback > 0.0 ? knockback : Math.min(1.6, 0.4 + radius * 0.2);
                if (strength > 0.0) {
                    Vector direction = target.getLocation().toVector().subtract(origin.toVector());
                    if (direction.lengthSquared() > 0.0001) {
                        direction.normalize().multiply(strength);
                        target.setVelocity(target.getVelocity().add(direction.setY(0.3)));
                    }
                }
                session.recordDamage(target.getUniqueId());
            }
        });
    }

    private boolean hasCustomFireworkSettings(Firework firework) {
        if (firework == null) {
            return false;
        }
        Double explosionPower = FireworkData.getExplosionPower(firework.getFireworkMeta());
        Double explosionDamage = FireworkData.getExplosionDamage(firework.getFireworkMeta());
        Double explosionKnockback = FireworkData.getExplosionKnockback(firework.getFireworkMeta());
        return hasCustomFireworkSettings(explosionPower, explosionDamage, explosionKnockback);
    }

    private boolean hasCustomFireworkSettings(Double explosionPower,
                                              Double explosionDamage,
                                              Double explosionKnockback) {
        boolean powerSet = explosionPower != null && explosionPower > 0.0;
        boolean damageSet = explosionDamage != null && explosionDamage > 0.0;
        boolean knockbackSet = explosionKnockback != null && explosionKnockback > 0.0;
        return powerSet || damageSet || knockbackSet;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        safeHandle("onFoodLevelChange", () -> {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            event.setCancelled(true);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        safeHandle("onBlockExplode", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                if (bedwarsManager.isBedwarsWorld(event.getBlock().getWorld().getName())) {
                    event.blockList().clear();
                }
                return;
            }
            if (!session.isInArenaWorld(event.getBlock().getWorld())) {
                return;
            }
            filterExplosionBlocks(session, event.blockList(), false);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        safeHandle("onPlayerMove", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isStarting()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isParticipant(player.getUniqueId()) || !session.isFrozen(player.getUniqueId())) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) {
                return;
            }
            if (from.getBlockX() == to.getBlockX()
                    && from.getBlockY() == to.getBlockY()
                    && from.getBlockZ() == to.getBlockZ()) {
                return;
            }
            event.setTo(from);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        safeHandle("onBlockPlace", () -> {
            Player player = event.getPlayer();
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                if (!player.isOp() && bedwarsManager.isBedwarsWorld(player.getWorld().getName())) {
                    event.setCancelled(true);
                }
                return;
            }
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (session.isEditor(player.getUniqueId())) {
                return;
            }
            if (!session.isParticipant(player.getUniqueId())) {
                if (session.isActive()) {
                    event.setCancelled(true);
                }
                return;
            }
            if (!session.isRunning()) {
                event.setCancelled(true);
                return;
            }
            Block block = event.getBlockPlaced();
            BlockPoint point = new BlockPoint(block.getX(), block.getY(), block.getZ());
            if (!session.isInsideMap(point)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot place blocks outside the map.", NamedTextColor.RED));
                return;
            }
            if (session.isPlacementBlocked(point)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot place blocks here.", NamedTextColor.RED));
                return;
            }
            if (block.getType() == Material.TNT) {
                event.setCancelled(true);
                block.setType(Material.AIR, false);
                Location spawn = block.getLocation().add(0.5, 0.0, 0.5);
                block.getWorld().spawn(spawn, TNTPrimed.class, tnt -> {
                    tnt.setFuseTicks(80);
                    tnt.setSource(player);
                });
                player.getWorld().playSound(spawn, org.bukkit.Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
                consumeSingleItem(player, event.getHand());
                return;
            }
            session.recordPlacedBlock(point, event.getItemInHand());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        safeHandle("onBlockMultiPlace", () -> {
            Player player = event.getPlayer();
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                if (!player.isOp() && bedwarsManager.isBedwarsWorld(player.getWorld().getName())) {
                    event.setCancelled(true);
                }
                return;
            }
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (session.isEditor(player.getUniqueId())) {
                return;
            }
            if (!session.isParticipant(player.getUniqueId())) {
                if (session.isActive()) {
                    event.setCancelled(true);
                }
                return;
            }
            if (!session.isRunning()) {
                event.setCancelled(true);
                return;
            }
            for (org.bukkit.block.BlockState state : event.getReplacedBlockStates()) {
                Block block = state.getBlock();
                BlockPoint point = new BlockPoint(block.getX(), block.getY(), block.getZ());
                if (!session.isInsideMap(point)) {
                    event.setCancelled(true);
                    player.sendMessage(Component.text("You cannot place blocks outside the map.", NamedTextColor.RED));
                    return;
                }
                if (session.isPlacementBlocked(point)) {
                    event.setCancelled(true);
                    player.sendMessage(Component.text("You cannot place blocks here.", NamedTextColor.RED));
                    return;
                }
            }
            ItemStack placedItem = event.getItemInHand();
            for (org.bukkit.block.BlockState state : event.getReplacedBlockStates()) {
                Block block = state.getBlock();
                session.recordPlacedBlock(new BlockPoint(block.getX(), block.getY(), block.getZ()), placedItem);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        safeHandle("onBlockBreak", () -> {
            Player player = event.getPlayer();
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                if (!player.isOp() && bedwarsManager.isBedwarsWorld(player.getWorld().getName())) {
                    event.setCancelled(true);
                }
                return;
            }
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (session.isEditor(player.getUniqueId())) {
                return;
            }
            if (!session.isParticipant(player.getUniqueId())) {
                if (session.isActive()) {
                    event.setCancelled(true);
                }
                return;
            }
            if (!session.isRunning()) {
                event.setCancelled(true);
                return;
            }
            Block block = event.getBlock();
            BlockPoint point = new BlockPoint(block.getX(), block.getY(), block.getZ());
            TeamColor bedOwner = session.getBedOwner(point);
            if (bedOwner != null) {
                TeamColor breakerTeam = session.getTeam(player.getUniqueId());
                if (bedOwner == breakerTeam) {
                    event.setCancelled(true);
                    player.sendMessage(Component.text("You cannot break your own bed.", NamedTextColor.RED));
                    return;
                }
                event.setDropItems(false);
                session.handleBedDestroyed(bedOwner, player);
                return;
            }
            if (!session.isInsideMap(point)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot break blocks outside the map.", NamedTextColor.RED));
                return;
            }
            if (!session.isPlacedBlock(point)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You can only break blocks placed in this game.", NamedTextColor.RED));
                return;
            }
            ItemStack drop = session.removePlacedBlockItem(point);
            dropPlacedBlock(block, drop);
            event.setCancelled(true);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        safeHandle("onEntityDamage", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                if (event.getEntity() instanceof Player victim
                        && bedwarsManager.isBedwarsWorld(victim.getWorld().getName())) {
                    event.setCancelled(true);
                }
                return;
            }
            if (!(event.getEntity() instanceof Player victim)) {
                if (!session.isInArenaWorld(event.getEntity().getWorld())) {
                    return;
                }
                if (!isSummon(event.getEntity())) {
                    return;
                }
                Player attacker = resolveAttacker(event);
                if (attacker != null && session.isParticipant(attacker.getUniqueId())) {
                    TeamColor attackerTeam = session.getTeam(attacker.getUniqueId());
                    TeamColor ownerTeam = getSummonTeam(event.getEntity());
                    if (ownerTeam != null && ownerTeam == attackerTeam) {
                        event.setCancelled(true);
                    }
                }
                return;
            }
            if (!session.isInArenaWorld(victim.getWorld())) {
                return;
            }
            if (isSummon(event.getDamager())) {
                if (!session.isParticipant(victim.getUniqueId())
                        || victim.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    event.setCancelled(true);
                    return;
                }
                TeamColor ownerTeam = getSummonTeam(event.getDamager());
                TeamColor victimTeam = session.getTeam(victim.getUniqueId());
                if (ownerTeam != null && victimTeam != null && ownerTeam == victimTeam) {
                    event.setCancelled(true);
                    return;
                }
                CustomItemDefinition summon = getSummonDefinition(event.getDamager());
                if (summon != null && summon.getDamage() > 0.0) {
                    event.setDamage(summon.getDamage());
                }
            }
            if (event.getDamager() instanceof EnderCrystal crystal) {
                TeamColor ownerTeam = getSummonTeam(crystal);
                TeamColor victimTeam = session.getTeam(victim.getUniqueId());
                if (ownerTeam != null && victimTeam != null && ownerTeam == victimTeam) {
                    event.setCancelled(true);
                    return;
                }
                CustomItemDefinition custom = getCustomEntity(crystal);
                if (custom != null && custom.getDamage() > 0.0) {
                    event.setDamage(custom.getDamage());
                }
            }
            boolean sameTeamTnt = false;
            if (event.getDamager() instanceof TNTPrimed tnt
                    && tnt.getSource() instanceof Player source
                    && session.isParticipant(source.getUniqueId())
                    && session.isParticipant(victim.getUniqueId())) {
                TeamColor sourceTeam = session.getTeam(source.getUniqueId());
                TeamColor victimTeam = session.getTeam(victim.getUniqueId());
                if (sourceTeam != null && sourceTeam == victimTeam) {
                    event.setDamage(0.0);
                    sameTeamTnt = true;
                }
            }
            Player attacker = resolveAttacker(event);
            if (attacker != null
                    && session.isParticipant(attacker.getUniqueId())
                    && session.isInArenaWorld(attacker.getWorld())
                    && session.hasRespawnProtection(attacker.getUniqueId())) {
                session.removeRespawnProtection(attacker.getUniqueId());
            }
            boolean sameTeamFireball = false;
            if (attacker != null
                    && session.isParticipant(attacker.getUniqueId())
                    && session.isParticipant(victim.getUniqueId())) {
                TeamColor attackerTeam = session.getTeam(attacker.getUniqueId());
                TeamColor victimTeam = session.getTeam(victim.getUniqueId());
                if (attackerTeam != null && attackerTeam == victimTeam) {
                    if (event.getDamager() instanceof Fireball) {
                        sameTeamFireball = true;
                        event.setDamage(0.0);
                    } else {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            if (!session.isRunning()) {
                event.setCancelled(true);
                return;
            }
            if (session.hasRespawnProtection(victim.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            if (!sameTeamFireball && event.getDamager() instanceof Fireball fireball) {
                CustomItemDefinition definition = resolveCustomProjectile(fireball);
                if (isFireballCustom(definition)) {
                    event.setDamage(Math.max(0.0, definition.getDamage()));
                }
            }
            if (event.getDamager() instanceof Firework firework) {
                if (hasCustomFireworkSettings(firework)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (!sameTeamTnt && event.getDamager() instanceof TNTPrimed) {
                event.setDamage(event.getDamage() * TNT_DAMAGE_MULTIPLIER);
            }
            if (event.getDamager() instanceof Fireball fireball) {
                CustomItemDefinition definition = resolveCustomProjectile(fireball);
                if (isFireballCustom(definition)) {
                    applyFireballKnockback(victim, fireball, definition);
                }
            }
            if (!event.isCancelled()
                    && attacker != null
                    && victim.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                victim.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
            if (!event.isCancelled()
                    && attacker != null
                    && session.isParticipant(attacker.getUniqueId())
                    && session.isParticipant(victim.getUniqueId())) {
                TeamColor attackerTeam = session.getTeam(attacker.getUniqueId());
                TeamColor victimTeam = session.getTeam(victim.getUniqueId());
                if (attackerTeam != null && victimTeam != null && attackerTeam != victimTeam) {
                    session.recordCombat(attacker.getUniqueId(), victim.getUniqueId());
                }
            }
            if (!event.isCancelled() && session.isParticipant(victim.getUniqueId())) {
                session.recordDamage(victim.getUniqueId());
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        safeHandle("onEntityDamageOther", () -> {
            if (event instanceof EntityDamageByEntityEvent) {
                return;
            }
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                if (bedwarsManager.isBedwarsWorld(player.getWorld().getName())
                        && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
                    event.setCancelled(true);
                }
                return;
            }
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                if (!session.isRunning()) {
                    return;
                }
                if (!session.isParticipant(player.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
                event.setDamage(1000.0);
                session.recordDamage(player.getUniqueId());
                return;
            }
            if (!session.isRunning()) {
                event.setCancelled(true);
                return;
            }
            boolean participant = session.isParticipant(player.getUniqueId());
            if (participant) {
                if (event.getCause() == EntityDamageEvent.DamageCause.WORLD_BORDER) {
                    session.recordDamage(player.getUniqueId());
                    return;
                }
                event.setCancelled(true);
                return;
            }
            if (session.hasRespawnProtection(player.getUniqueId())) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        safeHandle("onEntityRegainHealth", () -> {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            if (!session.isParticipant(player.getUniqueId()) || !session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        safeHandle("onEntityTarget", () -> {
            if (!isSummon(event.getEntity())) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                event.setCancelled(true);
                return;
            }
            if (!(event.getTarget() instanceof Player target)) {
                event.setCancelled(true);
                return;
            }
            if (!session.isParticipant(target.getUniqueId())
                    || !session.isInArenaWorld(target.getWorld())
                    || target.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                event.setCancelled(true);
                return;
            }
            TeamColor ownerTeam = getSummonTeam(event.getEntity());
            TeamColor targetTeam = session.getTeam(target.getUniqueId());
            if (ownerTeam != null && targetTeam != null && ownerTeam == targetTeam) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        safeHandle("onProjectileHit", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            if (!session.isInArenaWorld(event.getEntity().getWorld())) {
                return;
            }
            if (event.getEntity() instanceof Snowball snowball) {
                String customId = snowball.getPersistentDataContainer().get(customProjectileKey, PersistentDataType.STRING);
                if (customId == null || !customId.equalsIgnoreCase("bed_bug")) {
                    return;
                }
                TeamColor team = getSummonTeam(snowball);
                if (team == null) {
                    return;
                }
                CustomItemDefinition custom = getCustomItem(customId);
                Location spawn;
                if (event.getHitBlock() != null) {
                    spawn = event.getHitBlock().getLocation().add(0.5, BED_BUG_SPAWN_Y_OFFSET, 0.5);
                } else {
                    spawn = snowball.getLocation();
                }
                spawnBedBug(session, team, spawn, custom);
                snowball.remove();
                return;
            }
            if (event.getEntity() instanceof Arrow arrow) {
                String customId = arrow.getPersistentDataContainer().get(customProjectileKey, PersistentDataType.STRING);
                if (customId == null || !customId.equalsIgnoreCase("creeping_arrow")) {
                    return;
                }
                TeamColor team = getSummonTeam(arrow);
                if (team == null) {
                    return;
                }
                CustomItemDefinition custom = getCustomItem(customId);
                Location spawn;
                if (event.getHitBlock() != null) {
                    spawn = event.getHitBlock().getLocation().add(0.5, 0.0, 0.5);
                } else if (event.getHitEntity() != null) {
                    spawn = event.getHitEntity().getLocation();
                } else {
                    spawn = arrow.getLocation();
                }
                spawnCreepingCreeper(session, team, spawn, custom);
                arrow.remove();
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        safeHandle("onEntityDeath", () -> {
            if (!isSummon(event.getEntity())) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session != null && session.isRunning()) {
                session.handleGarryDeath(event.getEntity(), event.getEntity().getKiller());
            }
            event.getDrops().clear();
            event.setDroppedExp(0);
            BukkitTask task = defenderTasks.remove(event.getEntity().getUniqueId());
            if (task != null) {
                task.cancel();
            }
            cleanupSummonTracker(event.getEntity().getUniqueId());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        safeHandle("onPlayerDropItem", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isParticipant(player.getUniqueId()) || !session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (session.isStarting()) {
                event.setCancelled(true);
                return;
            }
            if (session.isInCombat(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot drop items while in combat.", NamedTextColor.RED));
                return;
            }
            ItemStack dropped = event.getItemDrop().getItemStack();
            if (dropped != null && dropped.getType() == Material.WOODEN_SWORD) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot drop your wooden sword.", NamedTextColor.RED));
                return;
            }
            if (dropped != null && TOOL_MATERIALS.contains(dropped.getType())) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot drop tools in BedWars.", NamedTextColor.RED));
                return;
            }
            if (dropped != null && SWORD_MATERIALS.contains(dropped.getType())) {
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
                        if (hasAnySword(player)) {
                            return;
                        }
                        ItemStack wooden = new ItemStack(Material.WOODEN_SWORD);
                        if (player.getInventory().getItemInMainHand().getType() == Material.AIR) {
                            player.getInventory().setItemInMainHand(wooden);
                        } else {
                            player.getInventory().addItem(wooden);
                        }
                        session.applyUpgradesTo(player);
                        player.updateInventory();
                    }
                }.runTask(bedwarsManager.getPlugin());
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        safeHandle("onEntityPickupItem", () -> {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            ItemStack stack = event.getItem().getItemStack();
            if (stack == null) {
                return;
            }
            if (isProtectedItem(stack)) {
                ItemStack updated = makeUnbreakable(stack);
                event.getItem().setItemStack(updated);
            }
            if (RESOURCE_MATERIALS.contains(stack.getType())) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            }
            if (!SWORD_MATERIALS.contains(stack.getType())) {
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
                    if (hasBetterSword(player)) {
                        removeWoodenSword(player);
                    }
                    session.applyUpgradesTo(player);
                    player.updateInventory();
                }
            }.runTask(bedwarsManager.getPlugin());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        safeHandle("onPlayerItemDamage", () -> {
            Player player = event.getPlayer();
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            ItemStack item = event.getItem();
            if (!isProtectedItem(item)) {
                return;
            }
            event.setCancelled(true);
            makeUnbreakable(item);
        });
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        safeHandle("onPlayerDeath", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            Player player = event.getEntity();
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            boolean participant = session.isParticipant(player.getUniqueId());
            if (participant) {
                dropResourceItems(event, session);
            }
            UUID killerId = null;
            Player killer = player.getKiller();
            if (killer != null && session.isParticipant(killer.getUniqueId())) {
                killerId = killer.getUniqueId();
            } else {
                UUID recent = session.getRecentDamager(player.getUniqueId());
                if (recent != null && session.isParticipant(recent)) {
                    killerId = recent;
                }
            }
            boolean finalDeath = false;
            boolean statsEnabled = session.isStatsEnabled();
            if (participant) {
                TeamColor team = session.getTeam(player.getUniqueId());
                finalDeath = team != null && session.getBedState(team) == BedState.DESTROYED;
                if (statsEnabled) {
                    bedwarsManager.getStatsService().addDeath(player.getUniqueId());
                    if (finalDeath) {
                        bedwarsManager.getStatsService().addFinalDeath(player.getUniqueId());
                    }
                }
            }
            if (killerId != null && !killerId.equals(player.getUniqueId())) {
                session.addKill(killerId);
                if (statsEnabled) {
                    bedwarsManager.getStatsService().addKill(killerId);
                    if (finalDeath) {
                        bedwarsManager.getStatsService().addFinalKill(killerId);
                    }
                }
            }
            session.handlePlayerDeath(player);
        });
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        safeHandle("onPlayerRespawn", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (session.isPendingRespawn(player.getUniqueId()) || session.isEliminated(player.getUniqueId())) {
                Location mapLobby = session.getArena().getMapLobbyLocation();
                Location lobby = mapLobby != null ? mapLobby : session.getArena().getLobbyLocation();
                if (lobby != null) {
                    event.setRespawnLocation(lobby);
                }
            }
            session.handleRespawn(player);
            refreshInvisibility(player, session);
        });
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        safeHandle("onPlayerChangedWorld", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            session.handleWorldChange(event.getPlayer());
            refreshInvisibility(event.getPlayer(), session);
            syncInvisibilityForViewer(event.getPlayer(), session);
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        safeHandle("onPlayerJoin", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            session.handlePlayerJoin(event.getPlayer());
            refreshInvisibility(event.getPlayer(), session);
            syncInvisibilityForViewer(event.getPlayer(), session);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        safeHandle("onPlayerQuit", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            org.bukkit.entity.Entity vehicle = event.getPlayer().getVehicle();
            if (vehicle instanceof LivingEntity living && isHappyGhast(living)) {
                living.remove();
                cleanupSummonTracker(living.getUniqueId());
            }
            fireballCooldowns.remove(event.getPlayer().getUniqueId());
            flamethrowerCooldowns.remove(event.getPlayer().getUniqueId());
            session.handlePlayerQuit(event.getPlayer().getUniqueId());
            showArmorForPlayer(event.getPlayer(), session);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDismount(EntityDismountEvent event) {
        safeHandle("onEntityDismount", () -> {
            if (!(event.getDismounted() instanceof LivingEntity living)) {
                return;
            }
            if (!isHappyGhast(living)) {
                return;
            }
            bedwarsManager.getPlugin().getServer().getScheduler().runTask(bedwarsManager.getPlugin(), () -> {
                living.remove();
                cleanupSummonTracker(living.getUniqueId());
            });
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionEffectChange(EntityPotionEffectEvent event) {
        safeHandle("onPotionEffectChange", () -> {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            PotionEffectType type = event.getModifiedType();
            if (type != PotionEffectType.INVISIBILITY) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld())) {
                showArmorForPlayer(player, session);
                return;
            }
            if (event.getAction() == EntityPotionEffectEvent.Action.REMOVED
                    || event.getAction() == EntityPotionEffectEvent.Action.CLEARED
                    || event.getNewEffect() == null) {
                showArmorForPlayer(player, session);
                return;
            }
            hideArmorForPlayer(player, session);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        safeHandle("onPrepareCraft", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            for (org.bukkit.entity.HumanEntity viewer : event.getViewers()) {
                if (viewer instanceof Player player && session.isInArenaWorld(player.getWorld())) {
                    event.getInventory().setResult(null);
                    return;
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        safeHandle("onCraftItem", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            event.setCancelled(true);
        });
    }

    private boolean isUseAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private CustomItemDefinition resolveCustomItem(ItemStack item) {
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

    private CustomItemDefinition resolveCustomProjectile(Fireball fireball) {
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

    private void applyFireballKnockback(Player victim, Fireball fireball, CustomItemDefinition definition) {
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

    private boolean isSameCustomItemInMainHand(Player player, ItemStack usedItem) {
        ItemStack main = player.getInventory().getItemInMainHand();
        return matchesCustomItem(main, usedItem);
    }

    private void launchFireball(Player player, CustomItemDefinition custom) {
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setIsIncendiary(custom.isIncendiary());
        fireball.setYield(custom.getYield());
        fireball.setVelocity(player.getLocation().getDirection().normalize().multiply(custom.getVelocity()));
        fireball.getPersistentDataContainer().set(customProjectileKey, PersistentDataType.STRING, custom.getId());
    }

    private boolean canUseFireball(Player player) {
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

    private boolean isFireballCustom(CustomItemDefinition definition) {
        if (definition == null) {
            return false;
        }
        return definition.getType() == CustomItemType.FIREBALL
                || definition.getType() == CustomItemType.FLAMETHROWER;
    }

    private boolean activateRespawnBeacon(Player player, GameSession session, CustomItemDefinition custom) {
        int delaySeconds = custom != null && custom.getLifetimeSeconds() > 0
                ? custom.getLifetimeSeconds()
                : 30;
        boolean activated = session.triggerRespawnBeacon(player, delaySeconds);
        if (activated) {
            TeamColor team = session.getTeam(player.getUniqueId());
            Location origin = player.getLocation();
            if (team != null) {
                BedLocation bed = session.getArena().getBeds().get(team);
                World world = session.getArena().getWorld();
                if (bed != null && world != null) {
                    origin = bed.head().toLocation(world);
                }
            }
            startBeaconEffect(origin, team, delaySeconds);
        }
        return activated;
    }

    private boolean useFlamethrower(Player player,
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
        launchFlameProjectile(player, custom);
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

    private boolean canUseFlamethrower(Player player) {
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

    private void launchFlameProjectile(Player player, CustomItemDefinition custom) {
        Fireball fireball = player.launchProjectile(SmallFireball.class);
        fireball.setIsIncendiary(false);
        fireball.setYield(custom.getYield());
        fireball.setVelocity(player.getLocation().getDirection().normalize().multiply(custom.getVelocity()));
        fireball.getPersistentDataContainer().set(customProjectileKey, PersistentDataType.STRING, custom.getId());
    }

    private boolean useBridgeBuilder(Player player, GameSession session, CustomItemDefinition custom) {
        if (player == null || session == null || custom == null) {
            return false;
        }
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        int length = Math.max(1, custom.getMaxBlocks());
        int width = Math.max(1, custom.getBridgeWidth());
        int half = width / 2;
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.01) {
            return false;
        }
        direction.normalize();
        Vector right = new Vector(-direction.getZ(), 0, direction.getX());
        Location origin = player.getLocation().getBlock().getLocation();
        Material blockType = Material.END_STONE;
        ItemStack record = new ItemStack(blockType);
        new BukkitRunnable() {
            private int step = 0;

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
                Location base = origin.clone().add(direction.clone().multiply(step + 1));
                int baseX = base.getBlockX();
                int baseY = origin.getBlockY();
                int baseZ = base.getBlockZ();
                for (int y = 0; y < width; y++) {
                    for (int offset = -half; offset <= half; offset++) {
                        int dx = (int) Math.round(right.getX() * offset);
                        int dz = (int) Math.round(right.getZ() * offset);
                        Block target = base.getWorld().getBlockAt(baseX + dx, baseY + y, baseZ + dz);
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
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 0L, 2L);
        return true;
    }

    private boolean useBridgeZapper(Player player,
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

    private boolean activateTacticalNuke(Player player,
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
                    if (block.getType().isAir()) {
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
        startNukeBeaconEffect(target.getLocation(), countdown);
        new BukkitRunnable() {
            private int remaining = countdown;

            @Override
            public void run() {
                GameSession active = bedwarsManager.getActiveSession();
                if (active == null || !active.isRunning()) {
                    restoreNukeBlocks(world, originals, containerContents, Set.of());
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    detonateTacticalNuke(active, player, target.getLocation(), radius, originals, containerContents);
                    cancel();
                    return;
                }
                if (remaining <= NUKE_COUNTDOWN_CHAT_SECONDS) {
                    broadcastNukeCountdown(active, remaining, target.getLocation());
                }
                remaining--;
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 0L, 20L);
        return true;
    }

    private boolean shouldHighlightNukeBlock(Block block) {
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

    private boolean isExcludedNukeHighlightType(Material type) {
        String name = type.name();
        return name.endsWith("_STAIRS")
                || name.endsWith("_SLAB")
                || name.endsWith("_BED");
    }

    private Block resolveTargetBlock(Player player, PlayerInteractEvent event) {
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

    private void broadcastNukeTitle(GameSession session, int countdown) {
        if (session == null) {
            return;
        }
        Component title = Component.text("Tactical Nuke Activated", NamedTextColor.RED);
        Component subtitle = Component.text("Explosion in " + formatNukeTime(countdown), NamedTextColor.YELLOW);
        Title.Times times = Title.Times.times(java.time.Duration.ofMillis(200),
                java.time.Duration.ofSeconds(3),
                java.time.Duration.ofMillis(200));
        Title display = Title.title(title, subtitle, times);
        for (UUID playerId : session.getAssignments().keySet()) {
            Player target = Bukkit.getPlayer(playerId);
            if (target != null) {
                target.showTitle(display);
                target.sendMessage(Component.text("Tactical nuke activated.", NamedTextColor.RED));
            }
        }
    }

    private void broadcastNukeCountdown(GameSession session, int seconds, Location origin) {
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
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        DustOptions dust = new DustOptions(org.bukkit.Color.RED, 1.7f);
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
                for (double y = 0; y <= 18; y += 0.6) {
                    base.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, y, 0),
                            2, 0.02, 0.02, 0.02, 0.0, dust);
                }
                ticks += 5;
            }
        }.runTaskTimer(bedwarsManager.getPlugin(), 0L, 5L);
    }

    private void detonateTacticalNuke(GameSession session,
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

    private void restoreNukeBlocks(World world,
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

    private void startBeaconEffect(Location origin, TeamColor team, int durationSeconds) {
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

    private void setItemInHand(Player player, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
    }

    private void updateUsesLore(ItemMeta meta, int uses) {
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

    private void launchBridgeEgg(Player player, GameSession session, CustomItemDefinition custom) {
        TeamColor team = session.getTeam(player.getUniqueId());
        if (team == null) {
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

    private boolean launchBedBug(Player player, GameSession session, CustomItemDefinition custom) {
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

    private boolean spawnDreamDefender(Player player,
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

    private boolean spawnCrystal(Player player,
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

    private boolean spawnHappyGhast(Player player,
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
        entity.getPersistentDataContainer().set(customProjectileKey, PersistentDataType.STRING, custom.getId());
        applyHappyGhastHarness(entity, team);
        scheduleHappyGhastHarness(entity, team);
        applySummonStats(entity, custom);
        scheduleMountHappyGhast(entity, player);
        int lifetimeSeconds = custom != null && custom.getLifetimeSeconds() > 0
                ? custom.getLifetimeSeconds()
                : 180;
        scheduleSummonDespawn(entity, lifetimeSeconds);
        return true;
    }

    private boolean spawnPortableShopkeeper(Player player,
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

    private void spawnCreepingCreeper(GameSession session,
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

    private void scheduleMountHappyGhast(org.bukkit.entity.Entity entity, Player player) {
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

    private void applyHappyGhastHarness(LivingEntity entity, TeamColor team) {
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

    private void scheduleHappyGhastHarness(LivingEntity entity, TeamColor team) {
        if (entity == null) {
            return;
        }
        bedwarsManager.getPlugin().getServer().getScheduler().runTaskLater(bedwarsManager.getPlugin(),
                () -> applyHappyGhastHarness(entity, team), 1L);
        bedwarsManager.getPlugin().getServer().getScheduler().runTaskLater(bedwarsManager.getPlugin(),
                () -> applyHappyGhastHarness(entity, team), 20L);
    }

    private ItemStack createHarnessItem(TeamColor team, DyeColor dyeColor) {
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

    private void applyHarnessEquipment(LivingEntity entity, ItemStack harness) {
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

    private EquipmentSlot resolveEquipmentSlot(String name) {
        if (name == null) {
            return null;
        }
        try {
            return EquipmentSlot.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void applyHarnessByReflection(LivingEntity entity, ItemStack harness, DyeColor dyeColor) {
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

    private boolean invokeBooleanSetter(Object target, String methodName, boolean value) {
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

    private boolean invokeObjectSetter(Object target, String methodName, Class<?> argType, Object value) {
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

    private Method[] concatMethods(Method[] first, Method[] second) {
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

    private void applyHappyGhastHarnessNms(LivingEntity entity, ItemStack harness, DyeColor dyeColor) {
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

    private Object toNmsItem(ItemStack item) {
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

    private Object resolveNmsDyeColor(DyeColor dyeColor) {
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

    private String getCraftBukkitVersion() {
        String name = Bukkit.getServer().getClass().getPackage().getName();
        int index = name.lastIndexOf('.');
        if (index < 0) {
            return null;
        }
        return name.substring(index + 1);
    }

    private void spawnBedBug(GameSession session, TeamColor team, Location location, CustomItemDefinition custom) {
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

    private void startDefenderTargeting(IronGolem golem, TeamColor owner, GameSession session) {
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

    private void scheduleSummonDespawn(org.bukkit.entity.Entity entity, int lifetimeSeconds) {
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

    private void cleanupSummonTracker(UUID entityId) {
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

    private void updateSummonName(org.bukkit.entity.Entity entity, int remainingSeconds) {
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
        Component timer = Component.text("Despawn: " + Math.max(0, remainingSeconds) + "s", NamedTextColor.GRAY);
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

    private void sendSummonActionBar(org.bukkit.entity.Entity entity, int remainingSeconds) {
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

    private boolean isHappyGhast(org.bukkit.entity.Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(GameSession.HAPPY_GHAST_TAG);
    }

    private SummonNameplate createSummonNameplate(org.bukkit.entity.Entity entity) {
        if (entity == null || entity.getWorld() == null) {
            return null;
        }
        Location base = entity.getLocation();
        double height = estimateEntityHeight(entity);
        ArmorStand nameStand = spawnNameStand(base.clone().add(0, height + SUMMON_NAME_OFFSET, 0));
        ArmorStand timerStand = spawnNameStand(base.clone().add(0, height + SUMMON_TIMER_OFFSET, 0));
        return new SummonNameplate(nameStand.getUniqueId(), timerStand.getUniqueId());
    }

    private ArmorStand spawnNameStand(Location location) {
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

    private void updateSummonNameplates(org.bukkit.entity.Entity entity, SummonNameplate nameplate) {
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

    private void updateNameplateText(SummonNameplate nameplate, Component title, Component timer) {
        ArmorStand nameStand = getNameStand(nameplate.nameId());
        if (nameStand != null) {
            nameStand.customName(title);
        }
        ArmorStand timerStand = getNameStand(nameplate.timerId());
        if (timerStand != null) {
            timerStand.customName(timer);
        }
    }

    private ArmorStand getNameStand(UUID id) {
        if (id == null) {
            return null;
        }
        org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
        return entity instanceof ArmorStand stand ? stand : null;
    }

    private void removeNameplate(SummonNameplate nameplate) {
        ArmorStand nameStand = getNameStand(nameplate.nameId());
        if (nameStand != null) {
            nameStand.remove();
        }
        ArmorStand timerStand = getNameStand(nameplate.timerId());
        if (timerStand != null) {
            timerStand.remove();
        }
    }

    private double estimateEntityHeight(org.bukkit.entity.Entity entity) {
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

    private record SummonNameplate(UUID nameId, UUID timerId) {
    }

    private String resolveSummonDisplayName(org.bukkit.entity.Entity entity) {
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
        if (entity.getScoreboardTags().contains(GameSession.GARRY_TAG)) {
            return "Garry";
        }
        if (entity.getScoreboardTags().contains(GameSession.GARRY_WIFE_TAG)) {
            return "Garry's Wife";
        }
        if (entity.getScoreboardTags().contains(GameSession.GARRY_JR_TAG)) {
            return "Garry Jr.";
        }
        return toDisplayName(entity.getType());
    }

    private String toDisplayName(EntityType type) {
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

    private Player findNearestEnemy(Location origin, TeamColor owner, GameSession session, double range) {
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

    private void setSummonTeam(org.bukkit.entity.Entity entity, TeamColor team) {
        if (entity == null || team == null) {
            return;
        }
        entity.getPersistentDataContainer().set(summonTeamKey, PersistentDataType.STRING, team.key());
    }

    private TeamColor getSummonTeam(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return null;
        }
        String key = entity.getPersistentDataContainer().get(summonTeamKey, PersistentDataType.STRING);
        if (key == null) {
            return null;
        }
        return TeamColor.fromKey(key);
    }

    private boolean isSummon(org.bukkit.entity.Entity entity) {
        return entity != null
                && (entity.getScoreboardTags().contains(GameSession.BED_BUG_TAG)
                || entity.getScoreboardTags().contains(GameSession.DREAM_DEFENDER_TAG)
                || entity.getScoreboardTags().contains(GameSession.HAPPY_GHAST_TAG)
                || entity.getScoreboardTags().contains(GameSession.CREEPING_CREEPER_TAG)
                || entity.getScoreboardTags().contains(PORTABLE_SHOPKEEPER_TAG)
                || entity.getScoreboardTags().contains(GameSession.GARRY_TAG)
                || entity.getScoreboardTags().contains(GameSession.GARRY_WIFE_TAG)
                || entity.getScoreboardTags().contains(GameSession.GARRY_JR_TAG));
    }

    private CustomItemDefinition getSummonDefinition(org.bukkit.entity.Entity entity) {
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
        if (entity.getScoreboardTags().contains(GameSession.GARRY_TAG)) {
            return getCustomItem("garry");
        }
        if (entity.getScoreboardTags().contains(GameSession.GARRY_WIFE_TAG)) {
            return getCustomItem("garry_wife");
        }
        if (entity.getScoreboardTags().contains(GameSession.GARRY_JR_TAG)) {
            return getCustomItem("garry_jr");
        }
        return null;
    }

    private void applySummonStats(LivingEntity entity, CustomItemDefinition custom) {
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
            applyEntitySpeed(entity, speed);
        }
        double range = custom.getRange();
        if (range > 0.0) {
            applyEntityRange(entity, range);
        }
    }

    private void applyEntitySpeed(LivingEntity entity, double speed) {
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

    private void applyEntityRange(LivingEntity entity, double range) {
        try {
            Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            Method getAttribute = LivingEntity.class.getMethod("getAttribute", attributeClass);
            Object followRange = resolveAttribute(attributeClass, "GENERIC_FOLLOW_RANGE");
            applyAttributeValue(entity, getAttribute, followRange, range);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private Object resolveAttribute(Class<?> attributeClass, String name) {
        if (attributeClass == null || name == null) {
            return null;
        }
        try {
            return Enum.valueOf((Class) attributeClass, name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void applyAttributeValue(LivingEntity entity,
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

    private EntityType resolveHappyGhastType() {
        try {
            return EntityType.valueOf("HAPPY_GHAST");
        } catch (IllegalArgumentException ex) {
            return EntityType.GHAST;
        }
    }

    private CustomItemDefinition getCustomItem(String id) {
        CustomItemConfig config = bedwarsManager.getCustomItemConfig();
        if (config == null || id == null) {
            return null;
        }
        return config.getItem(id);
    }

    private CustomItemDefinition getCustomEntity(org.bukkit.entity.Entity entity) {
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

    private void consumeHeldItem(Player player, EquipmentSlot hand, ItemStack usedItem) {
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

    private boolean matchesCustomItem(ItemStack stack, ItemStack usedItem) {
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

    private ItemStack resolveInteractItem(PlayerInteractEvent event, Player player) {
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

    private void refreshInvisibility(Player player, GameSession session) {
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

    private void syncInvisibilityForViewer(Player viewer, GameSession session) {
        if (!session.isParticipant(viewer.getUniqueId())) {
            return;
        }
        if (!session.isInArenaWorld(viewer.getWorld())) {
            return;
        }
        Map<EquipmentSlot, ItemStack> hidden = new EnumMap<>(EquipmentSlot.class);
        hidden.put(EquipmentSlot.HEAD, new ItemStack(Material.AIR));
        hidden.put(EquipmentSlot.CHEST, new ItemStack(Material.AIR));
        hidden.put(EquipmentSlot.LEGS, new ItemStack(Material.AIR));
        hidden.put(EquipmentSlot.FEET, new ItemStack(Material.AIR));
        for (UUID targetId : session.getAssignments().keySet()) {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || target.equals(viewer)) {
                continue;
            }
            if (!session.isInArenaWorld(target.getWorld())) {
                continue;
            }
            if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                viewer.sendEquipmentChange(target, hidden);
            } else {
                ItemStack[] armor = target.getInventory().getArmorContents();
                Map<EquipmentSlot, ItemStack> visible = new EnumMap<>(EquipmentSlot.class);
                visible.put(EquipmentSlot.FEET, cloneOrAir(armor, 0));
                visible.put(EquipmentSlot.LEGS, cloneOrAir(armor, 1));
                visible.put(EquipmentSlot.CHEST, cloneOrAir(armor, 2));
                visible.put(EquipmentSlot.HEAD, cloneOrAir(armor, 3));
                viewer.sendEquipmentChange(target, visible);
            }
        }
    }

    private void hideArmorForPlayer(Player target, GameSession session) {
        Map<EquipmentSlot, ItemStack> hidden = new EnumMap<>(EquipmentSlot.class);
        hidden.put(EquipmentSlot.HEAD, new ItemStack(Material.AIR));
        hidden.put(EquipmentSlot.CHEST, new ItemStack(Material.AIR));
        hidden.put(EquipmentSlot.LEGS, new ItemStack(Material.AIR));
        hidden.put(EquipmentSlot.FEET, new ItemStack(Material.AIR));
        sendArmorUpdate(target, session, hidden);
    }

    private void showArmorForPlayer(Player target, GameSession session) {
        ItemStack[] armor = target.getInventory().getArmorContents();
        Map<EquipmentSlot, ItemStack> visible = new EnumMap<>(EquipmentSlot.class);
        visible.put(EquipmentSlot.FEET, cloneOrAir(armor, 0));
        visible.put(EquipmentSlot.LEGS, cloneOrAir(armor, 1));
        visible.put(EquipmentSlot.CHEST, cloneOrAir(armor, 2));
        visible.put(EquipmentSlot.HEAD, cloneOrAir(armor, 3));
        sendArmorUpdate(target, session, visible);
    }

    private ItemStack cloneOrAir(ItemStack[] armor, int index) {
        if (armor == null || index < 0 || index >= armor.length) {
            return new ItemStack(Material.AIR);
        }
        ItemStack item = armor[index];
        if (item == null || item.getType() == Material.AIR) {
            return new ItemStack(Material.AIR);
        }
        return item.clone();
    }

    private void sendArmorUpdate(Player target, GameSession session, Map<EquipmentSlot, ItemStack> equipment) {
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

    private int placeBridgeBlocks(GameSession session,
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

    private void filterExplosionBlocks(GameSession session, List<Block> blocks, boolean limitedTypes) {
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

    private boolean isFireballBreakable(Block block) {
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

    private boolean isWindChargeExplosion(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return false;
        }
        String type = entity.getType().name();
        return "WIND_CHARGE".equals(type) || "BREEZE_WIND_CHARGE".equals(type);
    }

    private boolean isArmor(ItemStack item) {
        if (item == null) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    private boolean isProtectedItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return isArmor(item)
                || TOOL_MATERIALS.contains(type)
                || WEAPON_MATERIALS.contains(type);
    }

    private boolean isBlockedStorageItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.WOODEN_SWORD || type == Material.SHIELD || TOOL_MATERIALS.contains(type);
    }

    private boolean isStorageInventory(Inventory inventory) {
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

    private boolean shouldBlockContainerMove(InventoryClickEvent event, Player player, Inventory topInventory) {
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

    private void scheduleEquipmentUnbreakable(Player player, GameSession session) {
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

    private void scheduleToolTierSync(Player player, GameSession session) {
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

    private void enforceEquipmentUnbreakable(Player player) {
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

    private ItemStack makeUnbreakable(ItemStack item) {
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

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private void safeHandle(String context, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE, "BedWars error in " + context, ex);
        }
    }

    private void dropPlacedBlock(Block block, ItemStack override) {
        Material type = block.getType();
        if (type == Material.AIR) {
            return;
        }
        block.setType(Material.AIR, false);
        ItemStack drop = override != null ? override.clone() : new ItemStack(type);
        drop.setAmount(1);
        block.getWorld().dropItemNaturally(block.getLocation(), drop);
    }

    private void dropResourceItems(PlayerDeathEvent event, GameSession session) {
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

    private void sendKillLootMessage(Player recipient, Map<Material, Integer> totals) {
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

    private String formatResourceName(Material material) {
        return switch (material) {
            case IRON_INGOT -> "Iron";
            case GOLD_INGOT -> "Gold";
            case DIAMOND -> "Diamond";
            case EMERALD -> "Emerald";
            default -> material.name();
        };
    }

    private NamedTextColor resourceColor(Material material) {
        return switch (material) {
            case IRON_INGOT -> NamedTextColor.GRAY;
            case GOLD_INGOT -> NamedTextColor.GOLD;
            case DIAMOND -> NamedTextColor.AQUA;
            case EMERALD -> NamedTextColor.GREEN;
            default -> NamedTextColor.GRAY;
        };
    }

    private void depositHeldItem(Player player, Inventory target, EquipmentSlot hand) {
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

    private void consumeSingleItem(Player player, EquipmentSlot hand) {
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

    private boolean hasAnySword(Player player) {
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

    private boolean hasBetterSword(Player player) {
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

    private void removeWoodenSword(Player player) {
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
