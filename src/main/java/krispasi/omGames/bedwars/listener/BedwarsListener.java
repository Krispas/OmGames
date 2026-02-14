package krispasi.omGames.bedwars.listener;

import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.game.GameSession;
import krispasi.omGames.bedwars.item.CustomItemConfig;
import krispasi.omGames.bedwars.item.CustomItemData;
import krispasi.omGames.bedwars.item.CustomItemDefinition;
import krispasi.omGames.bedwars.item.CustomItemType;
import krispasi.omGames.bedwars.model.BlockPoint;
import krispasi.omGames.bedwars.model.ShopType;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.gui.MapSelectMenu;
import krispasi.omGames.bedwars.gui.ShopMenu;
import krispasi.omGames.bedwars.gui.TeamAssignMenu;
import krispasi.omGames.bedwars.gui.UpgradeShopMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
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
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.block.Action;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.potion.PotionEffectType;
import java.util.EnumMap;

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
    private static final double DEFENDER_TARGET_RANGE = 16.0;
    private final NamespacedKey customProjectileKey;
    private final NamespacedKey summonTeamKey;
    private final Map<UUID, BukkitTask> defenderTasks = new HashMap<>();

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
                menu.handleClick(event);
                return;
            }
            if (topInventory.getHolder() instanceof TeamAssignMenu menu) {
                menu.handleClick(event);
            }
            if (topInventory.getHolder() instanceof ShopMenu menu) {
                menu.handleClick(event);
                return;
            }
            if (topInventory.getHolder() instanceof UpgradeShopMenu menu) {
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
            if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
                return;
            }
            if (event.getClick().isShiftClick() && isArmor(event.getCurrentItem())) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        safeHandle("onInventoryDrag", () -> {
            Inventory topInventory = event.getView().getTopInventory();
            if (topInventory.getHolder() instanceof MapSelectMenu
                    || topInventory.getHolder() instanceof TeamAssignMenu
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
            for (int rawSlot : event.getRawSlots()) {
                if (event.getView().getSlotType(rawSlot) == InventoryType.SlotType.ARMOR) {
                    event.setCancelled(true);
                    return;
                }
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
            if (!isUseAction(event.getAction())) {
                return;
            }
            Player player = event.getPlayer();
            ItemStack item = resolveInteractItem(event, player);
            if (item == null) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
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
            if (event.getHand() == EquipmentSlot.OFF_HAND && isSameCustomItemInMainHand(player, item)) {
                return;
            }
            event.setCancelled(true);
            boolean used = switch (custom.getType()) {
                case FIREBALL -> {
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
            };
            if (used) {
                consumeHeldItem(player, event.getHand(), item);
            }
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
                return;
            }
            if (!session.isInArenaWorld(event.getEntity().getWorld())) {
                return;
            }
            filterExplosionBlocks(session, event.blockList());
        });
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
                return;
            }
            if (!session.isInArenaWorld(event.getBlock().getWorld())) {
                return;
            }
            filterExplosionBlocks(session, event.blockList());
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
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld())) {
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
            session.recordPlacedBlock(point);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        safeHandle("onBlockMultiPlace", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld())) {
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
            for (org.bukkit.block.BlockState state : event.getReplacedBlockStates()) {
                Block block = state.getBlock();
                session.recordPlacedBlock(new BlockPoint(block.getX(), block.getY(), block.getZ()));
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        safeHandle("onBlockBreak", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
                return;
            }
            Player player = event.getPlayer();
            if (!session.isInArenaWorld(player.getWorld())) {
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
            dropPlacedBlock(block);
            session.removePlacedBlock(point);
            event.setCancelled(true);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        safeHandle("onEntityDamage", () -> {
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null) {
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
                if (definition != null && definition.getType() == CustomItemType.FIREBALL) {
                    event.setDamage(Math.max(0.0, definition.getDamage()));
                }
            }
            if (!sameTeamTnt && event.getDamager() instanceof TNTPrimed) {
                event.setDamage(event.getDamage() * TNT_DAMAGE_MULTIPLIER);
            }
            if (event.getDamager() instanceof Fireball fireball) {
                CustomItemDefinition definition = resolveCustomProjectile(fireball);
                if (definition != null && definition.getType() == CustomItemType.FIREBALL) {
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
            if (session == null || !session.isInArenaWorld(player.getWorld())) {
                return;
            }
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                if (!session.isRunning() || !session.isParticipant(player.getUniqueId())) {
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
            if (session.hasRespawnProtection(player.getUniqueId())) {
                event.setCancelled(true);
            }
            if (!event.isCancelled() && session.isParticipant(player.getUniqueId())) {
                session.recordDamage(player.getUniqueId());
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
            if (!(event.getEntity() instanceof Snowball snowball)) {
                return;
            }
            String customId = snowball.getPersistentDataContainer().get(customProjectileKey, PersistentDataType.STRING);
            if (customId == null || !customId.equalsIgnoreCase("bed_bug")) {
                return;
            }
            GameSession session = bedwarsManager.getActiveSession();
            if (session == null || !session.isRunning()) {
                return;
            }
            if (!session.isInArenaWorld(snowball.getWorld())) {
                return;
            }
            TeamColor team = getSummonTeam(snowball);
            if (team == null) {
                return;
            }
            CustomItemDefinition custom = getCustomItem(customId);
            Location spawn = null;
            if (event.getHitBlock() != null) {
                spawn = event.getHitBlock().getLocation().add(0.5, BED_BUG_SPAWN_Y_OFFSET, 0.5);
            } else {
                spawn = snowball.getLocation();
            }
            spawnBedBug(session, team, spawn, custom);
            snowball.remove();
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
            if (session.isParticipant(player.getUniqueId())) {
                dropResourceItems(event, session);
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
                Location lobby = session.getArena().getLobbyLocation();
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
            session.handlePlayerQuit(event.getPlayer().getUniqueId());
            showArmorForPlayer(event.getPlayer(), session);
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
        int lifetimeSeconds = custom != null && custom.getLifetimeSeconds() > 0
                ? custom.getLifetimeSeconds()
                : 180;
        scheduleSummonDespawn(golem, lifetimeSeconds);
        startDefenderTargeting(golem, team, session);
        return true;
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
        new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isValid() && !entity.isDead()) {
                    entity.remove();
                }
            }
        }.runTaskLater(bedwarsManager.getPlugin(), lifetimeSeconds * 20L);
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
                || entity.getScoreboardTags().contains(GameSession.DREAM_DEFENDER_TAG));
    }

    private CustomItemDefinition getCustomItem(String id) {
        CustomItemConfig config = bedwarsManager.getCustomItemConfig();
        if (config == null || id == null) {
            return null;
        }
        return config.getItem(id);
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
            session.recordPlacedBlock(point);
            placed++;
        }
        return placed;
    }

    private void filterExplosionBlocks(GameSession session, List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        blocks.removeIf(block -> {
            BlockPoint point = new BlockPoint(block.getX(), block.getY(), block.getZ());
            if (session.isPlacedBlock(point)) {
                session.removePlacedBlock(point);
                return false;
            }
            return true;
        });
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

    private void dropPlacedBlock(Block block) {
        Material type = block.getType();
        if (type == Material.AIR) {
            return;
        }
        block.setType(Material.AIR, false);
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(type));
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
