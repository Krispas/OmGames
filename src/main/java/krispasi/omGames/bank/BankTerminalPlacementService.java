package krispasi.omGames.bank;

import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BankTerminalPlacementService {
    private static final String SCOREBOARD_TAG = "omgames_bank_terminal";

    private final BankManager manager;
    private final NamespacedKey terminalIdKey;
    private final NamespacedKey placementIdKey;
    private final NamespacedKey entityTypeKey;

    public BankTerminalPlacementService(BankManager manager, JavaPlugin plugin) {
        this.manager = manager;
        this.terminalIdKey = new NamespacedKey(plugin, "bank_terminal_id");
        this.placementIdKey = new NamespacedKey(plugin, "bank_terminal_placement_id");
        this.entityTypeKey = new NamespacedKey(plugin, "bank_terminal_entity_type");
    }

    public boolean place(Player player, Block clickedBlock, BlockFace face, EquipmentSlot hand, ItemStack item) {
        if (player == null || clickedBlock == null || hand == null || item == null) {
            return false;
        }
        String terminalId = manager.readTerminalId(item);
        if (terminalId == null) {
            return false;
        }
        BankTerminal terminal = manager.getTerminal(terminalId);
        if (terminal == null) {
            player.sendMessage(Component.text("Terminal not found.", NamedTextColor.RED));
            return true;
        }
        if (!manager.canEditAccount(player, terminal.accountId())) {
            player.sendMessage(Component.text("You cannot place this terminal.", NamedTextColor.RED));
            return true;
        }
        Block targetBlock = clickedBlock.getRelative(face == null ? BlockFace.UP : face);
        if (!targetBlock.getType().isAir()) {
            player.sendMessage(Component.text("Place the cash register into an empty block.", NamedTextColor.RED));
            return true;
        }
        ItemStack displayItem = manager.createCashRegisterItem(terminal);
        if (displayItem == null) {
            player.sendMessage(Component.text("OmVeins cash_register item is not available.", NamedTextColor.RED));
            return true;
        }
        String placementId = UUID.randomUUID().toString();
        Location base = targetBlock.getLocation().add(0.5, 0.0, 0.5);
        spawnTerminalEntities(base, terminal, placementId, displayItem);
        consumeOne(player, hand, item);
        player.playSound(base, Sound.BLOCK_COPPER_PLACE, 0.8f, 1.0f);
        return true;
    }

    public boolean interact(Player player, Entity entity) {
        if (player == null || entity == null) {
            return false;
        }
        String terminalId = terminalId(entity);
        if (terminalId == null) {
            return false;
        }
        String placementId = placementId(entity);
        manager.openTerminalForPlayer(player, terminalId, placementId);
        return true;
    }

    public BankManager.Result deconstruct(Player player, String terminalId, String placementId) {
        BankTerminal terminal = manager.getTerminal(terminalId);
        if (terminal == null) {
            return BankManager.Result.fail("Terminal not found.");
        }
        if (!manager.canEditAccount(player, terminal.accountId())) {
            return BankManager.Result.fail("You cannot deconstruct this terminal.");
        }
        int removed = 0;
        Location soundLocation = null;
        Set<String> removedPlacementIds = new LinkedHashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (matchesTerminalPlacement(entity, terminalId, placementId)) {
                    if (soundLocation == null) {
                        soundLocation = entity.getLocation();
                    }
                    String entityPlacementId = placementId(entity);
                    if (entityPlacementId != null && !entityPlacementId.isBlank()) {
                        removedPlacementIds.add(entityPlacementId);
                    }
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed <= 0) {
            return BankManager.Result.fail("Placed terminal entities were not found.");
        }
        int itemCount = Math.max(1, removedPlacementIds.size());
        for (int index = 0; index < itemCount; index++) {
            ItemStack item = manager.createCashRegisterItem(terminal);
            if (item == null) {
                return BankManager.Result.fail("Could not recreate cash register item.");
            }
            manager.giveOrDrop(player, item);
        }
        player.playSound(soundLocation == null ? player.getLocation() : soundLocation, Sound.BLOCK_COPPER_BREAK, 0.8f, 1.0f);
        return BankManager.Result.ok("Deconstructed terminal " + terminal.name() + " and returned " + itemCount + " item" + (itemCount == 1 ? "" : "s") + ".");
    }

    private void spawnTerminalEntities(Location base, BankTerminal terminal, String placementId, ItemStack displayItem) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        world.spawn(base.clone().add(0.0, 0.55, 0.0), ItemDisplay.class, display -> {
            display.setItemStack(displayItem);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Quaternionf(),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Quaternionf()
            ));
            tag(display, terminal.terminalId(), placementId, "display");
        });
        world.spawn(base, Interaction.class, interaction -> {
            interaction.setInteractionWidth(0.9f);
            interaction.setInteractionHeight(1.0f);
            interaction.setResponsive(true);
            tag(interaction, terminal.terminalId(), placementId, "interaction");
        });
    }

    private void tag(Entity entity, String terminalId, String placementId, String entityType) {
        entity.setPersistent(true);
        entity.addScoreboardTag(SCOREBOARD_TAG);
        entity.getPersistentDataContainer().set(terminalIdKey, PersistentDataType.STRING, terminalId);
        entity.getPersistentDataContainer().set(placementIdKey, PersistentDataType.STRING, placementId);
        entity.getPersistentDataContainer().set(entityTypeKey, PersistentDataType.STRING, entityType);
    }

    private String terminalId(Entity entity) {
        return entity.getPersistentDataContainer().get(terminalIdKey, PersistentDataType.STRING);
    }

    private String placementId(Entity entity) {
        return entity.getPersistentDataContainer().get(placementIdKey, PersistentDataType.STRING);
    }

    private boolean matchesTerminalPlacement(Entity entity, String terminalId, String placementId) {
        if (placementId != null && !placementId.isBlank()) {
            return placementId.equals(placementId(entity));
        }
        return terminalId.equals(terminalId(entity));
    }

    private void consumeOne(Player player, EquipmentSlot hand, ItemStack item) {
        ItemStack remaining = item.clone();
        remaining.setAmount(item.getAmount() - 1);
        if (remaining.getAmount() <= 0) {
            remaining = null;
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(remaining);
        } else {
            player.getInventory().setItemInMainHand(remaining);
        }
    }
}
