package krispasi.omGames.bedwars.game;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import krispasi.omGames.bedwars.item.CustomItemDefinition;
import krispasi.omGames.bedwars.model.TeamColor;
import krispasi.omGames.bedwars.shop.ShopConfig;
import krispasi.omGames.bedwars.shop.ShopItemDefinition;
import krispasi.omGames.bedwars.timecapsule.TimeCapsuleItemData;
import krispasi.omGames.bedwars.timecapsule.TimeCapsuleQueueType;
import krispasi.omGames.bedwars.timecapsule.TimeCapsuleSerialization;
import krispasi.omGames.bedwars.timecapsule.TimeCapsuleService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class GameSessionTimeCapsuleRuntime {
    static final String TIME_CAPSULE_ITEM_ID = "time_capsule";
    private static final int TIME_CAPSULE_SIZE = 27;
    private static final Component TIME_CAPSULE_TITLE = Component.text("Time Capsule", NamedTextColor.DARK_PURPLE);

    private final GameSession session;
    private final TimeCapsuleService service;
    private final Map<UUID, OpenTimeCapsuleState> openCapsules = new HashMap<>();

    GameSessionTimeCapsuleRuntime(GameSession session, TimeCapsuleService service) {
        this.session = session;
        this.service = service;
    }

    void reset() {
        closeOpenInventories();
        openCapsules.clear();
    }

    void closeOpenInventories() {
        for (Map.Entry<UUID, OpenTimeCapsuleState> entry : List.copyOf(openCapsules.entrySet())) {
            UUID playerId = entry.getKey();
            OpenTimeCapsuleState state = entry.getValue();
            if (state == null) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            if (player.getOpenInventory().getTopInventory() == state.inventory()) {
                player.closeInventory();
            }
        }
    }

    boolean activate(Player player, ItemStack item, CustomItemDefinition custom) {
        if (player == null || item == null || custom == null) {
            return false;
        }
        clearStaleState(player.getUniqueId(), player.getOpenInventory().getTopInventory());
        String storedContents = TimeCapsuleItemData.getContents(item);
        if (storedContents != null && !storedContents.isBlank()) {
            return openRewardCapsule(player, storedContents);
        }
        Inventory inventory = Bukkit.createInventory(null, TIME_CAPSULE_SIZE, TIME_CAPSULE_TITLE);
        openCapsules.put(player.getUniqueId(), new OpenTimeCapsuleState(inventory, TimeCapsuleMode.PACK, custom));
        player.openInventory(inventory);
        return true;
    }

    boolean handleInventoryClose(Player player, Inventory inventory) {
        if (player == null || inventory == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        OpenTimeCapsuleState state = openCapsules.get(playerId);
        if (state == null || state.inventory() != inventory) {
            return false;
        }
        openCapsules.remove(playerId);
        if (state.mode() == TimeCapsuleMode.PACK) {
            savePackedCapsule(player, state.custom(), inventory.getContents());
        } else {
            returnContents(player, inventory.getContents());
        }
        return true;
    }

    Map<UUID, ItemStack> createMatchRewardItems(Collection<UUID> participantIds) {
        if (participantIds == null || participantIds.isEmpty() || service == null || !isTimeCapsuleActiveForMatch()) {
            return Map.of();
        }
        ShopItemDefinition definition = resolveTimeCapsuleShopDefinition();
        CustomItemDefinition custom = resolveTimeCapsuleCustomDefinition();
        if (definition == null || custom == null) {
            return Map.of();
        }
        List<UUID> recipients = participantIds.stream()
                .filter(id -> id != null && session.getTeam(id) != null)
                .distinct()
                .toList();
        if (recipients.isEmpty()) {
            return Map.of();
        }
        List<TimeCapsuleService.ClaimedTimeCapsule> claimedCapsules = service.claimRandomCapsules(resolveQueueType(), recipients.size());
        if (claimedCapsules.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ItemStack> rewards = new LinkedHashMap<>();
        for (int i = 0; i < recipients.size() && i < claimedCapsules.size(); i++) {
            UUID playerId = recipients.get(i);
            TeamColor team = session.getTeam(playerId);
            TimeCapsuleService.ClaimedTimeCapsule capsule = claimedCapsules.get(i);
            rewards.put(playerId, buildRewardItem(
                    definition,
                    team,
                    capsule.contentsBase64(),
                    resolveSourcePlayerName(capsule.creatorId())));
        }
        return rewards;
    }

    private boolean openRewardCapsule(Player player, String storedContents) {
        ItemStack[] contents;
        try {
            contents = TimeCapsuleSerialization.deserialize(storedContents, TIME_CAPSULE_SIZE);
        } catch (IOException | IllegalArgumentException ex) {
            session.getBedwarsManager().getPlugin().getLogger()
                    .log(Level.WARNING, "Failed to open time capsule reward for " + player.getUniqueId(), ex);
            player.sendMessage(Component.text("This Time Capsule could not be opened.", NamedTextColor.RED));
            return false;
        }
        Inventory inventory = Bukkit.createInventory(null, TIME_CAPSULE_SIZE, TIME_CAPSULE_TITLE);
        inventory.setContents(contents);
        openCapsules.put(player.getUniqueId(), new OpenTimeCapsuleState(inventory, TimeCapsuleMode.LOOT, null));
        player.openInventory(inventory);
        return true;
    }

    private void savePackedCapsule(Player player, CustomItemDefinition custom, ItemStack[] originalContents) {
        if (player == null || originalContents == null) {
            return;
        }
        if (!TimeCapsuleSerialization.hasAnyContents(originalContents)) {
            player.sendMessage(Component.text("Your Time Capsule was empty.", NamedTextColor.YELLOW));
            return;
        }
        ItemStack[] savedContents = new ItemStack[originalContents.length];
        int filledSlots = 0;
        int savedSlots = 0;
        double saveChancePercent = resolveSaveChancePercent(custom);
        for (int slot = 0; slot < originalContents.length; slot++) {
            ItemStack item = originalContents[slot];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            filledSlots++;
            if (ThreadLocalRandom.current().nextDouble(100.0) < saveChancePercent) {
                savedContents[slot] = item.clone();
                savedSlots++;
            }
        }
        if (savedSlots <= 0) {
            player.sendMessage(Component.text("Your Time Capsule failed to preserve any filled slots.", NamedTextColor.RED));
            return;
        }

        String encodedContents;
        try {
            encodedContents = TimeCapsuleSerialization.serialize(savedContents);
        } catch (IOException ex) {
            session.getBedwarsManager().getPlugin().getLogger()
                    .log(Level.WARNING, "Failed to serialize a packed time capsule for " + player.getUniqueId(), ex);
            returnContents(player, originalContents);
            player.sendMessage(Component.text("Time Capsule storage failed. Its contents were returned.", NamedTextColor.RED));
            return;
        }

        if (encodedContents == null || encodedContents.isBlank()
                || !service.saveCapsule(resolveQueueType(), player.getUniqueId(), encodedContents)) {
            returnContents(player, originalContents);
            player.sendMessage(Component.text("Time Capsule storage failed. Its contents were returned.", NamedTextColor.RED));
            return;
        }

        if (savedSlots == filledSlots) {
            player.sendMessage(Component.text("Your Time Capsule preserved all " + filledSlots + " filled slots.",
                    NamedTextColor.GREEN));
            return;
        }
        player.sendMessage(Component.text("Your Time Capsule preserved " + savedSlots + "/" + filledSlots + " filled slots.",
                NamedTextColor.YELLOW));
    }

    private void returnContents(Player player, ItemStack[] contents) {
        if (player == null || contents == null) {
            return;
        }
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            session.giveItem(player, item.clone());
        }
    }

    private ItemStack buildRewardItem(ShopItemDefinition definition,
                                      TeamColor team,
                                      String encodedContents,
                                      String sourcePlayerName) {
        ItemStack reward = definition.createPurchaseItem(team);
        ItemMeta meta = reward.getItemMeta();
        List<Component> lore = meta.lore();
        List<Component> updatedLore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        updatedLore.add(Component.text("Packed in a previous match.", NamedTextColor.GRAY));
        if (sourcePlayerName != null && !sourcePlayerName.isBlank()) {
            updatedLore.add(Component.text("Packed by " + sourcePlayerName + ".", NamedTextColor.YELLOW));
        } else {
            updatedLore.add(Component.text("Packed by an unknown player.", NamedTextColor.YELLOW));
        }
        updatedLore.add(Component.text("Right-click to unpack it.", NamedTextColor.DARK_GRAY));
        meta.lore(updatedLore);
        TimeCapsuleItemData.applyContents(meta, encodedContents);
        TimeCapsuleItemData.applySourcePlayerName(meta, sourcePlayerName);
        reward.setItemMeta(meta);
        return reward;
    }

    private String resolveSourcePlayerName(UUID creatorId) {
        if (creatorId == null) {
            return null;
        }
        Player online = Bukkit.getPlayer(creatorId);
        if (online != null && online.getName() != null && !online.getName().isBlank()) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(creatorId);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return null;
    }

    private void clearStaleState(UUID playerId, Inventory currentInventory) {
        if (playerId == null) {
            return;
        }
        OpenTimeCapsuleState state = openCapsules.get(playerId);
        if (state != null && state.inventory() != currentInventory) {
            openCapsules.remove(playerId);
        }
    }

    private boolean isTimeCapsuleActiveForMatch() {
        return session.getRotatingItemIds().contains(TIME_CAPSULE_ITEM_ID);
    }

    private ShopItemDefinition resolveTimeCapsuleShopDefinition() {
        ShopConfig shopConfig = session.getBedwarsManager().getShopConfig();
        return shopConfig == null ? null : shopConfig.getItem(TIME_CAPSULE_ITEM_ID);
    }

    private CustomItemDefinition resolveTimeCapsuleCustomDefinition() {
        return session.getBedwarsManager().getCustomItemConfig().getItem(TIME_CAPSULE_ITEM_ID);
    }

    private TimeCapsuleQueueType resolveQueueType() {
        return session.isTestMode() ? TimeCapsuleQueueType.TEST : TimeCapsuleQueueType.NORMAL;
    }

    private double resolveSaveChancePercent(CustomItemDefinition custom) {
        double configured = custom != null ? custom.getSaveChancePercent() : 50.0;
        return Math.max(0.0, Math.min(100.0, configured));
    }

    private enum TimeCapsuleMode {
        PACK,
        LOOT
    }

    private record OpenTimeCapsuleState(Inventory inventory, TimeCapsuleMode mode, CustomItemDefinition custom) {
    }
}
