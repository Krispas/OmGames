package krispasi.omGames.bedwars.listener;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.item.CustomItemConfig;
import krispasi.omGames.bedwars.item.CustomItemData;
import krispasi.omGames.bedwars.item.CustomItemDefinition;
import krispasi.omGames.bedwars.item.CustomItemType;
import krispasi.omGames.bedwars.item.FireworkData;
import krispasi.omGames.bedwars.item.LobbyControlItemData;
import krispasi.omGames.bedwars.item.ShopItemData;
import krispasi.omGames.bedwars.item.TeamSelectItemData;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.BedLocation;
import krispasi.omGames.bedwars.model.BedState;
import krispasi.omGames.bedwars.model.ShopType;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopItemDefinition;
import krispasi.omGames.bedwars.gui.MapSelectMenu;
import krispasi.omGames.bedwars.gui.EventSelectMenu;
import krispasi.omGames.bedwars.gui.LockpickTargetMenu;
import krispasi.omGames.bedwars.gui.RotatingItemMenu;
import krispasi.omGames.bedwars.gui.ShopMenu;
import krispasi.omGames.bedwars.gui.SkinSelectMenu;
import krispasi.omGames.bedwars.gui.SkinTypeMenu;
import krispasi.omGames.bedwars.gui.TeamAssignMenu;
import krispasi.omGames.bedwars.gui.TeamPickMenu;
import krispasi.omGames.bedwars.gui.TimeCapsuleViewMenu;
import krispasi.omGames.bedwars.gui.UpgradeShopMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Bed;
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
import org.bukkit.entity.Trident;
import org.bukkit.entity.Villager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import java.util.concurrent.ThreadLocalRandom;
import java.util.EnumSet;
import java.util.ArrayList;
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

public class BedwarsListener extends BedwarsListenerRuntimeSupport implements Listener {

    public BedwarsListener(BedwarsManager bedwarsManager) {
        super(bedwarsManager);
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
                return;
            }
            if (topInventory.getHolder() instanceof EventSelectMenu menu) {
                if (event.getRawSlot() >= topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
                menu.handleClick(event);
                return;
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
            if (topInventory.getHolder() instanceof LockpickTargetMenu menu) {
                if (event.getRawSlot() >= topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
                menu.handleClick(event);
                return;
            }
            if (topInventory.getHolder() instanceof TimeCapsuleViewMenu menu) {
                menu.handleClick(event);
                return;
            }
            if (topInventory.getHolder() instanceof SkinTypeMenu menu) {
                if (event.getRawSlot() >= topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
                menu.handleClick(event);
                return;
            }
            if (topInventory.getHolder() instanceof SkinSelectMenu menu) {
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
            if (isStorageInventory(topInventory)) {
                if (shouldBlockContainerMove(event, player, topInventory)
                        || shouldBlockCarryLimitedStorageMove(event, player, session, topInventory)) {
                    event.setCancelled(true);
                    return;
                }
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
                    || topInventory.getHolder() instanceof EventSelectMenu
                    || topInventory.getHolder() instanceof RotatingItemMenu
                    || topInventory.getHolder() instanceof ShopMenu
                    || topInventory.getHolder() instanceof UpgradeShopMenu
                    || topInventory.getHolder() instanceof LockpickTargetMenu
                    || topInventory.getHolder() instanceof TimeCapsuleViewMenu
                    || topInventory.getHolder() instanceof SkinTypeMenu
                    || topInventory.getHolder() instanceof SkinSelectMenu) {
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        safeHandle("onInventoryClose", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }
            if (!session.handleTimeCapsuleInventoryClose(player, event.getInventory())) {
                return;
            }
            if (session.isParticipant(player.getUniqueId()) && session.isInArenaWorld(player.getWorld())) {
                scheduleEquipmentUnbreakable(player, session);
                scheduleToolTierSync(player, session);
            }
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
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null || clickedBlock.getType() != Material.ENDER_CHEST) {
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
            ItemStack item = resolveInteractItem(event, player);
            CustomItemDefinition custom = resolveCustomItem(item);
            boolean holdingLockpick = custom != null && custom.getType() == CustomItemType.LOCKPICK;
            if (holdingLockpick && event.getHand() == EquipmentSlot.OFF_HAND && isSameCustomItemInMainHand(player, item)) {
                return;
            }
            event.setCancelled(true);
            if (!holdingLockpick) {
                session.openAccessibleEnderChest(player, clickedBlock, true);
                return;
            }
            TeamColor ownerTeam = session.resolveBaseOwner(clickedBlock);
            TeamColor playerTeam = session.getTeam(player.getUniqueId());
            if (ownerTeam == null || ownerTeam == playerTeam) {
                session.openAccessibleEnderChest(player, clickedBlock, true);
                return;
            }
            UUID ownerId = session.resolveAccessibleEnderChestOwner(player, clickedBlock, true);
            if (ownerId != null && !ownerId.equals(player.getUniqueId())) {
                session.openAccessibleEnderChest(player, clickedBlock, true);
                return;
            }
            new LockpickTargetMenu(session, player, clickedBlock, ownerTeam).open(player);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBaseChestInteract(PlayerInteractEvent event) {
        safeHandle("onBaseChestInteract", () -> {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null) {
                return;
            }
            Material type = clickedBlock.getType();
            if (type != Material.CHEST && type != Material.TRAPPED_CHEST) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            TeamColor ownerTeam = session.resolveBaseOwner(clickedBlock);
            if (ownerTeam == null || session.canAccessBaseChest(player, clickedBlock)) {
                return;
            }
            ItemStack item = resolveInteractItem(event, player);
            CustomItemDefinition custom = resolveCustomItem(item);
            boolean holdingLockpick = custom != null && custom.getType() == CustomItemType.LOCKPICK;
            if (holdingLockpick && event.getHand() == EquipmentSlot.OFF_HAND && isSameCustomItemInMainHand(player, item)) {
                return;
            }
            event.setCancelled(true);
            if (!holdingLockpick) {
                sendLockedBaseChestMessage(player, ownerTeam);
                return;
            }
            if (session.beginChestLockpick(player, clickedBlock)) {
                consumeHeldItem(player, event.getHand(), item);
            }
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onLobbyOpenableInteract(PlayerInteractEvent event) {
        safeHandle("onLobbyOpenableInteract", () -> {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }
            Block clicked = event.getClickedBlock();
            if (clicked == null) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            Player player = event.getPlayer();
            if (!isOutsideRunningBedwarsGame(player, session)) {
                return;
            }
            if (canEditProtectedBedwarsWorld(player, session)) {
                return;
            }
            if (!(clicked.getBlockData() instanceof Openable)) {
                return;
            }
            event.setCancelled(true);
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
            if (bedwarsManager.getLobbyParkour().handleInteract(event)) {
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
            if (custom.getType() == CustomItemType.MAGIC_MILK) {
                return;
            }
            if (custom.getType() == CustomItemType.LOCKPICK) {
                return;
            }
            if (custom.getType() == CustomItemType.PROXIMITY_MINE) {
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
                    launchFireball(player, session, custom);
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
                case GIGANTIFY_GRENADE -> {
                    yield launchGigantifyGrenade(player, session, custom);
                }
                case ABYSSAL_RIFT -> {
                    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                        yield false;
                    }
                    yield session.deployAbyssalRift(player, custom, event.getClickedBlock());
                }
                case RESPAWN_BEACON -> {
                    yield activateRespawnBeacon(player, session, custom);
                }
                case FLAMETHROWER -> {
                    consume = false;
                    yield useFlamethrower(player, session, custom, item, event.getHand());
                }
                case BRIDGE_BUILDER -> {
                    yield useBridgeBuilder(player, session, custom, event);
                }
                case TOWER_CHEST -> {
                    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                        yield false;
                    }
                    yield session.deployTowerChest(player, event.getClickedBlock(), event.getBlockFace());
                }
                case STEEL_SHELL -> {
                    yield session.activateSteelShell(player, custom);
                }
                case PROXIMITY_MINE -> {
                    yield false;
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
                case ELYTRA_STRIKE -> {
                    yield session.activateElytraStrike(player, custom);
                }
                case RAILGUN_BLAST -> {
                    yield session.activateRailgunBlast(player, custom);
                }
                case MIRACLE_OF_THE_STARS -> {
                    yield session.activateMiracleOfTheStars(player, custom);
                }
                case UNSTABLE_TELEPORTATION_DEVICE -> {
                    yield session.activateUnstableTeleportationDevice(player, custom);
                }
                case TIME_CAPSULE -> {
                    yield session.activateTimeCapsule(player, item, custom);
                }
                case WOODOO_DOLL -> {
                    yield false;
                }
                case LOCKPICK -> {
                    yield false;
                }
                case MAGIC_MILK -> {
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
    public void onPlayerArmSwing(PlayerArmSwingEvent event) {
        safeHandle("onPlayerArmSwing", () -> {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isLungingMovementSpear(item)) {
                return;
            }
            if (isLungingSpearMovementOnCooldown(player, item)) {
                suppressBlockedLungingSpearMovement(player);
                event.setCancelled(true);
                return;
            }
            markLungingSpearMovementUsed(player, item);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        safeHandle("onPrePlayerAttackEntity", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            if (!event.willAttack()) {
                return;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isLungingMovementSpear(item)) {
                return;
            }
            if (isPendingLungingSpearSuccess(player)) {
                return;
            }
            if (isLungingSpearMovementOnCooldown(player, item)) {
                suppressBlockedLungingSpearMovement(player);
                event.setCancelled(true);
                return;
            }
            markLungingSpearMovementUsed(player, item);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        safeHandle("onPlayerRiptide", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            ItemStack item = event.getItem();
            if (isPendingLungingSpearSuccess(player)) {
                return;
            }
            if (isLungingSpearMovementOnCooldown(player, item)) {
                suppressBlockedLungingSpearMovement(player);
                event.setCancelled(true);
                return;
            }
            markLungingSpearMovementUsed(player, item);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        safeHandle("onPlayerVelocity", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            if (!consumeBlockedLungingSpearVelocity(player)) {
                return;
            }
            event.setVelocity(new Vector(0.0, 0.0, 0.0));
            event.setCancelled(true);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        safeHandle("onPlayerToggleFlight", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            if (!session.handleElytraStrikeFlightToggle(player)) {
                return;
            }
            event.setCancelled(true);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        safeHandle("onProjectileLaunch", () -> {
            if (!(event.getEntity() instanceof Trident trident)) {
                return;
            }
            if (!(trident.getShooter() instanceof Player owner)) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            if (!session.isInArenaWorld(trident.getWorld())
                    || !session.isParticipant(owner.getUniqueId())
                    || !session.isInArenaWorld(owner.getWorld())) {
                return;
            }
            ItemStack tridentItem = resolveThrownTridentItem(trident);
            if (!hasLoyalty(tridentItem)) {
                return;
            }
            startLoyaltyTridentVoidTracker(trident, owner.getUniqueId(), tridentItem);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        safeHandle("onPlayerItemConsume", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld()) || !session.isParticipant(player.getUniqueId())) {
                return;
            }
            ItemStack consumed = event.getItem();
            String customId = CustomItemData.getId(consumed);
            if (customId == null || !customId.equalsIgnoreCase("magic_milk")) {
                return;
            }
            CustomItemDefinition custom = bedwarsManager.getCustomItemConfig().getItem(customId);
            if (custom == null || custom.getType() != CustomItemType.MAGIC_MILK) {
                handlePotionBottleCleanup(event);
                return;
            }
            int immunitySeconds = Math.max(0, custom.getLifetimeSeconds());
            if (immunitySeconds <= 0) {
                handlePotionBottleCleanup(event);
                return;
            }
            session.grantTrapImmunity(player.getUniqueId(), immunitySeconds);
            player.sendMessage(Component.text("Magic Milk activated! Trap immunity for " + immunitySeconds + "s.", NamedTextColor.AQUA));
            handlePotionBottleCleanup(event);
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
                target = session.getAccessibleEnderChest(player, block, true);
            } else if (block.getState() instanceof Container container) {
                TeamColor ownerTeam = session.resolveBaseOwner(block);
                if (ownerTeam != null && !session.canAccessBaseChest(player, block)) {
                    sendLockedBaseChestMessage(player, ownerTeam);
                    return;
                }
                target = container.getInventory();
            }
            if (target == null) {
                return;
            }
            depositHeldItem(player, target, event.getHand());
            scheduleToolTierSync(player, session);
        });
    }

    private void sendLockedBaseChestMessage(Player player, TeamColor ownerTeam) {
        if (player == null) {
            return;
        }
        if (ownerTeam == null) {
            player.sendMessage(Component.text("This chest is locked.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("This chest is locked to the ", NamedTextColor.RED)
                .append(ownerTeam.displayComponent())
                .append(Component.text(" team until their bed is destroyed.", NamedTextColor.RED)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        safeHandle("onPlayerInteractEntity", () -> {
            Player player = event.getPlayer();
            GameSession session = bedwarsManager.getActiveSession();
            if (isProtectedLobbyItemFrame(event.getRightClicked(), player, session)) {
                event.setCancelled(true);
                return;
            }
            if (!(event.getRightClicked() instanceof Villager villager)) {
                return;
            }
            if (!villager.getScoreboardTags().contains(GameSession.ITEM_SHOP_TAG)
                    && !villager.getScoreboardTags().contains(GameSession.UPGRADES_SHOP_TAG)) {
                return;
            }
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

    @EventHandler(ignoreCancelled = false)
    public void onLobbyParkourPlatePress(PlayerInteractEvent event) {
        safeHandle("onLobbyParkourPlatePress", () -> {
            if (event.getAction() != Action.PHYSICAL) {
                return;
            }
            Block clicked = event.getClickedBlock();
            if (clicked == null) {
                return;
            }
            Player player = event.getPlayer();
            GameSession session = bedwarsManager.getActiveSession();
            if (clicked.getType() == Material.FARMLAND
                    && isOutsideRunningBedwarsGame(player, session)
                    && !canEditProtectedBedwarsWorld(player, session)) {
                event.setCancelled(true);
                return;
            }
            bedwarsManager.getLobbyParkour().handlePlatePress(player, clicked);
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
            if (isOutsideRunningBedwarsGame(player, session)) {
                event.setCancelled(true);
                applyOutsideGameBedwarsBuffs(player);
                return;
            }
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
            Player player = event.getPlayer();
            Location from = event.getFrom();
            Location to = event.getTo();
            bedwarsManager.getLobbyParkour().handleMove(player, from, to);
            GameSession session = bedwarsManager.getActiveSession();
            if (isOutsideRunningBedwarsGame(player, session)) {
                applyOutsideGameBedwarsBuffs(player);
            }
            if (session != null) {
                session.handleElytraStrikeMovement(player);
                session.handleProximityMineMovement(player);
                Location railgunChargeLock = session.getRailgunChargeLockedLocation(player);
                if (railgunChargeLock != null) {
                    if (to == null
                            || to.getWorld() != railgunChargeLock.getWorld()
                            || to.distanceSquared(railgunChargeLock) > 0.0001
                            || Math.abs(to.getYaw() - railgunChargeLock.getYaw()) > 0.01f
                            || Math.abs(to.getPitch() - railgunChargeLock.getPitch()) > 0.01f) {
                        event.setTo(railgunChargeLock);
                    }
                    return;
                }
            }
            if (session == null || !session.isStarting()) {
                return;
            }
            if (!session.isParticipant(player.getUniqueId()) || !session.isFrozen(player.getUniqueId())) {
                return;
            }
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
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
                if (!canEditProtectedBedwarsWorld(player, null)
                        && bedwarsManager.isBedwarsWorld(player.getWorld().getName())) {
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
            if (event instanceof BlockMultiPlaceEvent) {
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
            if (isPlaceableBedItem(event.getItemInHand())) {
                BedLocation location = resolvePlacedBedLocation(block);
                if (location == null || !session.placeTeamBed(player, location)) {
                    event.setCancelled(true);
                }
                return;
            }
            if (isProximityMineItem(event.getItemInHand())) {
                if (!session.placeProximityMine(player, block, event.getItemInHand())) {
                    event.setCancelled(true);
                }
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
                if (!canEditProtectedBedwarsWorld(player, null)
                        && bedwarsManager.isBedwarsWorld(player.getWorld().getName())) {
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
            if (isPlaceableBedItem(placedItem)) {
                BedLocation location = resolvePlacedBedLocation(event.getBlockPlaced());
                if (location == null || !session.placeTeamBed(player, location)) {
                    event.setCancelled(true);
                }
                return;
            }
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
                if (!canEditProtectedBedwarsWorld(player, null)
                        && bedwarsManager.isBedwarsWorld(player.getWorld().getName())) {
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
            Player protectedWorldAttacker = resolveAttacker(event);
            if (isProtectedLobbyItemFrame(event.getEntity(), protectedWorldAttacker, session)) {
                event.setCancelled(true);
                return;
            }
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
                if (session.isAbyssalRiftEntity(event.getEntity())) {
                    Player attacker = resolveAttacker(event);
                    if (session.damageAbyssalRift(event.getEntity(), attacker, resolveAbyssalRiftDamage(event))) {
                        event.setCancelled(true);
                    }
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
                CustomItemDefinition custom = getCustomEntity(crystal);
                double crystalDamage = session.getCrystalContactDamage(custom != null ? custom.getDamage() : 0.0);
                if (ownerTeam != null && victimTeam != null && ownerTeam == victimTeam && crystalDamage <= 0.0) {
                    event.setCancelled(true);
                    return;
                }
                if (crystalDamage > 0.0) {
                    event.setDamage(crystalDamage);
                }
            }
            boolean sameTeamTnt = false;
            boolean proximityMineTnt = false;
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
            } else if (event.getDamager() instanceof TNTPrimed tnt
                    && session.isParticipant(victim.getUniqueId())) {
                TeamColor mineTeam = getProximityMineTntTeam(tnt);
                TeamColor victimTeam = session.getTeam(victim.getUniqueId());
                proximityMineTnt = mineTeam != null;
                if (mineTeam != null && mineTeam == victimTeam) {
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
                if (proximityMineTnt) {
                    CustomItemDefinition mineCustom = getCustomItem(PROXIMITY_MINE_ITEM_ID);
                    if (mineCustom != null && mineCustom.getDamage() > 0.0) {
                        setExactEventDamage(event, mineCustom.getDamage());
                    } else {
                        event.setDamage(event.getDamage() * TNT_DAMAGE_MULTIPLIER);
                    }
                } else {
                    event.setDamage(event.getDamage() * TNT_DAMAGE_MULTIPLIER);
                }
            }
            if (event.getDamager() instanceof Fireball fireball) {
                CustomItemDefinition definition = resolveCustomProjectile(fireball);
                if (isFireballCustom(definition)) {
                    applyFireballKnockback(victim, fireball, definition);
                }
            }
            if (!event.isCancelled()
                    && session.isParticipant(victim.getUniqueId())
                    && isLethalDamage(victim, event)
                    && tryActivateTotemProtection(victim, session, false)) {
                event.setCancelled(true);
                return;
            }
            if (!event.isCancelled()
                    && attacker != null
                    && victim.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                victim.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
            if (!event.isCancelled()
                    && attacker != null
                    && event.getDamager() instanceof Player
                    && session.isParticipant(attacker.getUniqueId())
                    && session.isParticipant(victim.getUniqueId())) {
                ItemStack heldItem = attacker.getInventory().getItemInMainHand();
                CustomItemDefinition heldCustom = resolveCustomItem(heldItem);
                if (heldCustom != null
                        && heldCustom.getType() == CustomItemType.WOODOO_DOLL
                        && session.handleWoodooDollHit(attacker, victim)) {
                    consumeHeldItem(attacker, EquipmentSlot.HAND, heldItem);
                }
            }
            if (!event.isCancelled()
                    && attacker != null
                    && session.isParticipant(attacker.getUniqueId())
                    && session.isParticipant(victim.getUniqueId())) {
                TeamColor attackerTeam = session.getTeam(attacker.getUniqueId());
                TeamColor victimTeam = session.getTeam(victim.getUniqueId());
                if (attackerTeam != null && victimTeam != null && attackerTeam != victimTeam) {
                    session.recordCombat(attacker.getUniqueId(), victim.getUniqueId());
                    session.handleMatchEventDamage(attacker, victim, event.getFinalDamage());
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
            GameSession session = bedwarsManager.getActiveSession();
            if (!(event.getEntity() instanceof Player player)) {
                if (session == null || !session.isRunning()) {
                    return;
                }
                if (!session.isInArenaWorld(event.getEntity().getWorld())) {
                    return;
                }
                if (session.isAbyssalRiftEntity(event.getEntity())) {
                    if (session.damageAbyssalRift(event.getEntity(), null, resolveAbyssalRiftDamage(event))) {
                        event.setCancelled(true);
                    }
                    return;
                }
                return;
            }
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
                if (tryRescuePlayerFromVoidWithTotem(player, session)) {
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
                if (session.hasRespawnProtection(player.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                        && consumeVoidTotemFallProtection(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.setFallDistance(0.0f);
                    return;
                }
                if (isAllowedParticipantEnvironmentalDamage(event.getCause())) {
                    if (isLethalDamage(player, event) && tryActivateTotemProtection(player, session, false)) {
                        event.setCancelled(true);
                        return;
                    }
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
            GameSession session = bedwarsManager.getActiveSession();
            if (!(event.getEntity() instanceof Player player)) {
                if (session == null || !session.isRunning()) {
                    return;
                }
                if (!session.isInArenaWorld(event.getEntity().getWorld())) {
                    return;
                }
                return;
            }
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
            if (ownerTeam != null
                    && targetTeam != null
                    && ownerTeam == targetTeam) {
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
                if (customId == null) {
                    return;
                }
                if (customId.equalsIgnoreCase(GIGANTIFY_GRENADE_ITEM_ID)) {
                    handleGigantifyGrenadeHit(session, snowball, event);
                    return;
                }
                if (!customId.equalsIgnoreCase("bed_bug")) {
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
                    spawn = event.getHitBlock().getLocation().add(0.5, CREEPING_CREEPER_SPAWN_Y_OFFSET, 0.5);
                } else if (event.getHitEntity() != null) {
                    spawn = event.getHitEntity().getLocation().add(0.0, CREEPING_CREEPER_SPAWN_Y_OFFSET, 0.0);
                } else {
                    spawn = arrow.getLocation().add(0.0, CREEPING_CREEPER_SPAWN_Y_OFFSET, 0.0);
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
                stack = updated;
            }
            String shopItemId = ShopItemData.getId(stack);
            if (shopItemId != null && bedwarsManager.getShopConfig() != null) {
                ShopItemDefinition definition = bedwarsManager.getShopConfig().getItem(shopItemId);
                if (definition != null && definition.getMaxCarryAmount() > 0
                        && !session.canCarryAdditionalAmount(player, definition, stack.getAmount())) {
                    event.setCancelled(true);
                    player.sendActionBar(Component.text("You cannot carry more of that item.", NamedTextColor.RED));
                    return;
                }
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
            voidTotemFallProtection.remove(player.getUniqueId());
            clearGigantifyEffect(player, session, true);
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
                event.getDrops().removeIf(session::isActiveElytraStrikeItem);
                tryAutoActivateSoloRespawnBeacon(player, session);
                finalDeath = session.handlePlayerDeath(player);
                if (!finalDeath) {
                    session.recordPendingDeathCredit(player.getUniqueId(), killerId);
                }
                if (statsEnabled) {
                    bedwarsManager.getStatsService().addDeath(player.getUniqueId());
                    if (finalDeath) {
                        bedwarsManager.getStatsService().addFinalDeath(player.getUniqueId());
                    }
                }
            }
            if (killerId != null && !killerId.equals(player.getUniqueId())) {
                session.addKill(killerId);
                if (finalDeath) {
                    session.rewardFinalKill(killerId);
                }
                if (statsEnabled) {
                    bedwarsManager.getStatsService().addKill(killerId);
                    if (finalDeath) {
                        bedwarsManager.getStatsService().addFinalKill(killerId);
                    }
                }
            }
        });
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        safeHandle("onPlayerRespawn", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                Player player = event.getPlayer();
                voidTotemFallProtection.remove(player.getUniqueId());
                if (bedwarsManager.isBedwarsWorld(player.getWorld().getName())) {
                    bedwarsManager.getPlugin().getServer().getScheduler().runTask(bedwarsManager.getPlugin(),
                            () -> applyOutsideGameBedwarsBuffs(player));
                }
                return;
            }
            Player player = event.getPlayer();
            voidTotemFallProtection.remove(player.getUniqueId());
            if (!session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (session.isPendingRespawn(player.getUniqueId()) || session.isEliminated(player.getUniqueId())) {
                Location mapLobby = session.getArena().getMapLobbyLocation();
                Location lobby = mapLobby;
                if (lobby == null && session.getArena().getCenter() != null) {
                    lobby = session.getArena().getCenter().toLocation(player.getWorld());
                }
                if (lobby != null) {
                    event.setRespawnLocation(lobby);
                }
            }
            session.handleRespawn(player);
            refreshInvisibility(player, session);
            if (isOutsideRunningBedwarsGame(player, session)) {
                bedwarsManager.getPlugin().getServer().getScheduler().runTask(bedwarsManager.getPlugin(),
                        () -> applyOutsideGameBedwarsBuffs(player));
            }
            deliverPendingLoyaltyTridents(player);
        });
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        safeHandle("onPlayerChangedWorld", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            Player player = event.getPlayer();
            if (session != null) {
                session.handleWorldChange(player);
                if (gigantifyTasks.containsKey(player.getUniqueId())
                        && (!session.isParticipant(player.getUniqueId()) || !session.isInArenaWorld(player.getWorld()))) {
                    clearGigantifyEffect(player, session, true);
                }
                if (session.isLockedCommandSpectator(player.getUniqueId()) && !session.isInArenaWorld(player.getWorld())) {
                    Location fallback = session.getLockedCommandSpectatorLocation();
                    if (fallback != null) {
                        bedwarsManager.getPlugin().getServer().getScheduler().runTask(bedwarsManager.getPlugin(), () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            GameSession active = bedwarsManager.getActiveSession();
                            if (active == null || active != session || !active.isLockedCommandSpectator(player.getUniqueId())) {
                                return;
                            }
                            player.teleport(fallback);
                            player.sendMessage(Component.text("Spectators must stay in the BedWars world.", NamedTextColor.RED));
                        });
                    }
                }
                refreshInvisibility(player, session);
                syncInvisibilityForViewer(player, session);
            } else if (gigantifyTasks.containsKey(player.getUniqueId())) {
                clearGigantifyEffect(player, null, true);
            }
            if (isOutsideRunningBedwarsGame(player, session)) {
                applyOutsideGameBedwarsBuffs(player);
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        safeHandle("onPlayerJoin", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            Player player = event.getPlayer();
            if (session != null) {
                session.handlePlayerJoin(player);
                refreshInvisibility(player, session);
                syncInvisibilityForViewer(player, session);
            }
            if (isOutsideRunningBedwarsGame(player, session)) {
                if (!canEditProtectedBedwarsWorld(player, session)) {
                    restoreOutsideGameBedwarsLobbyState(player);
                }
                applyOutsideGameBedwarsBuffs(player);
            }
            deliverPendingLoyaltyTridents(player);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onKarmaAnvilLand(EntityChangeBlockEvent event) {
        safeHandle("onKarmaAnvilLand", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            boolean karmaAnvil = session != null
                    ? session.isKarmaAnvil(event.getEntity())
                    : event.getEntity().getScoreboardTags().contains("bw_karma_anvil");
            if (!karmaAnvil) {
                return;
            }
            event.setCancelled(true);
            Location impact = event.getEntity().getLocation();
            World world = impact.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.EXPLOSION, impact, 1);
                world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 1.0f);
                double radius = 3.5;
                for (org.bukkit.entity.Entity entity : world.getNearbyEntities(impact, radius, radius, radius)) {
                    if (!(entity instanceof LivingEntity living)) {
                        continue;
                    }
                    if (living instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }
                    living.damage(12.0);
                }
            }
            if (session != null) {
                session.clearTrackedKarmaAnvil(event.getEntity().getUniqueId());
            }
            event.getEntity().remove();
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onKarmaLightningIgnite(BlockIgniteEvent event) {
        safeHandle("onKarmaLightningIgnite", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            if (!session.isInArenaWorld(event.getBlock().getWorld())) {
                return;
            }
            if (!session.isKarmaLightning(event.getIgnitingEntity())) {
                return;
            }
            event.setCancelled(true);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onKarmaLightningDamage(EntityDamageByEntityEvent event) {
        safeHandle("onKarmaLightningDamage", () -> {
            if (!(event.getDamager() instanceof org.bukkit.entity.LightningStrike strike)) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            if (!session.isKarmaLightning(strike)) {
                return;
            }
            if (!(event.getEntity() instanceof LivingEntity living)) {
                return;
            }
            event.setCancelled(true);
            double newHealth = living.getHealth() - 15.0;
            if (newHealth <= 0.0) {
                living.setHealth(0.0);
            } else {
                living.setHealth(newHealth);
            }
        });
    }

    private void handlePotionBottleCleanup(PlayerItemConsumeEvent event) {
        ItemStack consumed = event.getItem();
        if (consumed == null || consumed.getType() != Material.POTION) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        Bukkit.getScheduler().runTask(bedwarsManager.getPlugin(), () -> {
            ItemStack held = hand == EquipmentSlot.OFF_HAND
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();
            if (held != null && held.getType() == Material.GLASS_BOTTLE) {
                if (hand == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(null);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onLockedCommandSpectatorTeleport(PlayerTeleportEvent event) {
        safeHandle("onLockedCommandSpectatorTeleport", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isActive()) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isLockedCommandSpectator(player.getUniqueId())) {
                return;
            }
            Location destination = event.getTo();
            if (destination == null || session.isInArenaWorld(destination.getWorld())) {
                return;
            }
            event.setCancelled(true);
            player.sendMessage(Component.text("Spectators must stay in the BedWars world.", NamedTextColor.RED));
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        safeHandle("onPlayerQuit", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            bedwarsManager.getLobbyParkour().handleQuit(event.getPlayer());
            clearGigantifyEffect(event.getPlayer(), session, true);
            if (session == null) {
                return;
            }
            org.bukkit.entity.Entity vehicle = event.getPlayer().getVehicle();
            if (vehicle instanceof LivingEntity living
                    && isHappyGhast(living)
                    && isHappyGhastDriver(living, event.getPlayer())) {
                living.remove();
                cleanupSummonTracker(living.getUniqueId());
            }
            fireballCooldowns.remove(event.getPlayer().getUniqueId());
            flamethrowerCooldowns.remove(event.getPlayer().getUniqueId());
            lungingSpearMovementCooldowns.remove(event.getPlayer().getUniqueId());
            pendingSuccessfulLungingSpearEvents.remove(event.getPlayer().getUniqueId());
            blockedLungingSpearVelocityUntil.remove(event.getPlayer().getUniqueId());
            voidTotemFallProtection.remove(event.getPlayer().getUniqueId());
            Player player = event.getPlayer();
            if (session.isRunning()
                    && session.isParticipant(player.getUniqueId())
                    && session.isInArenaWorld(player.getWorld())) {
                player.getInventory().clear();
            }
            session.handlePlayerQuit(event.getPlayer());
            showArmorForPlayer(event.getPlayer(), session);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDismount(EntityDismountEvent event) {
        safeHandle("onEntityDismount", () -> {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            if (isAprilFoolsMountedFireball(event.getDismounted())) {
                event.setCancelled(true);
                return;
            }
            if (!(event.getDismounted() instanceof LivingEntity living)) {
                return;
            }
            if (!isHappyGhast(living)) {
                return;
            }
            if (!isHappyGhastDriver(living, player)) {
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

}
