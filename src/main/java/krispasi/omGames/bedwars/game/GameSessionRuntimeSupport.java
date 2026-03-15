package krispasi.omGames.bedwars.game;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import krispasi.omGames.OmVeinsAPI;
import krispasi.omGames.bedwars.BedwarsManager;
import krispasi.omGames.bedwars.event.*;
import krispasi.omGames.bedwars.generator.*;
import krispasi.omGames.bedwars.gui.*;
import krispasi.omGames.bedwars.item.*;
import krispasi.omGames.bedwars.model.*;
import krispasi.omGames.bedwars.shop.*;
import krispasi.omGames.bedwars.upgrade.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Bed;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.Vector;

/**
 * Runtime state machine for a single BedWars match.
 * <p>Tracks team assignments, beds, generators, placed blocks, upgrades, traps,
 * and per-player tool tiers.</p>
 * <p>Enforces match rules such as respawn timing, combat restrictions, sudden death,
 * and world border behavior.</p>
 * <p>Creates and tears down shop NPCs, scoreboards, and other match-scoped entities.</p>
 * @see krispasi.omGames.bedwars.game.GameState
 */

abstract class GameSessionRuntimeSupport extends GameSessionEffectSupport {

    protected GameSessionRuntimeSupport(BedwarsManager bedwarsManager, Arena arena) {
        super(bedwarsManager, arena);
    }

    public Set<String> getRotatingItemIds() {
        return Collections.unmodifiableSet(rotatingItemIds);
    }

    public Set<String> getRotatingUpgradeIds() {
        return Collections.unmodifiableSet(rotatingUpgradeIds);
    }

    public RotatingSelectionMode getRotatingMode() {
        return rotatingMode;
    }

    public boolean isMatchEventRollEnabled() {
        return matchEventRollEnabled;
    }

    public void setMatchEventRollEnabled(boolean enabled) {
        matchEventRollEnabled = enabled;
    }

    public boolean toggleMatchEventRollEnabled() {
        matchEventRollEnabled = !matchEventRollEnabled;
        return matchEventRollEnabled;
    }

    public BedwarsMatchEventType getForcedMatchEvent() {
        return forcedMatchEvent;
    }

    public void setForcedMatchEvent(BedwarsMatchEventType forcedMatchEvent) {
        this.forcedMatchEvent = forcedMatchEvent;
        if (forcedMatchEvent != null) {
            matchEventRollEnabled = true;
        }
    }

    public BedwarsMatchEventType getActiveMatchEvent() {
        return activeMatchEvent;
    }

    public double getCrystalContactDamage(double configuredDamage) {
        return configuredDamage > 0.0 ? configuredDamage : 1.0;
    }

    public ShopCost getEffectiveShopCost(ShopItemDefinition item) {
        if (item == null) {
            return null;
        }
        ShopCost cost = item.getCost();
        if (cost == null || !cost.isValid()) {
            return cost;
        }
        if (activeMatchEvent == BedwarsMatchEventType.IN_THIS_ECONOMY
                && IN_THIS_ECONOMY_PRICE_MULTIPLIED_ITEMS.contains(item.getId())) {
            return new ShopCost(cost.material(), cost.amount() * IN_THIS_ECONOMY_PRICE_MULTIPLIER);
        }
        return cost;
    }

    public Material adjustGeneratedResourceMaterial(GeneratorType generatorType, Material baseMaterial) {
        if (baseMaterial == null || activeMatchEvent != BedwarsMatchEventType.IN_THIS_ECONOMY) {
            return baseMaterial;
        }
        if (generatorType == GeneratorType.DIAMOND || generatorType == GeneratorType.EMERALD) {
            return Material.GOLD_INGOT;
        }
        return baseMaterial;
    }

    public int adjustGeneratedResourceAmount(Material material, int baseAmount) {
        return adjustGeneratedResourceAmount(null, material, baseAmount);
    }

    public int adjustGeneratedResourceAmount(GeneratorType generatorType, Material material, int baseAmount) {
        int amount = Math.max(0, baseAmount);
        if (amount <= 0 || activeMatchEvent != BedwarsMatchEventType.IN_THIS_ECONOMY || material == null) {
            return amount;
        }
        if ((generatorType == GeneratorType.DIAMOND || generatorType == GeneratorType.EMERALD)
                && material == Material.GOLD_INGOT) {
            return amount;
        }
        return switch (material) {
            case DIAMOND, EMERALD -> 0;
            case GOLD_INGOT -> {
                int halved = amount / 2;
                if ((amount & 1) == 1 && ThreadLocalRandom.current().nextBoolean()) {
                    halved++;
                }
                yield Math.max(0, halved);
            }
            case IRON_INGOT -> Math.max(0, amount * 2);
            default -> amount;
        };
    }

    public void handleMatchEventDamage(Player attacker, Player victim, double finalDamage) {
        if (attacker == null || victim == null || finalDamage <= 0.0) {
            return;
        }
        if (activeMatchEvent != BedwarsMatchEventType.BLOOD_MOON) {
            return;
        }
        TeamColor attackerTeam = getTeam(attacker.getUniqueId());
        TeamColor victimTeam = getTeam(victim.getUniqueId());
        if (attackerTeam == null || victimTeam == null || attackerTeam == victimTeam) {
            return;
        }
        double healAmount = finalDamage * BLOOD_MOON_LIFESTEAL_RATIO;
        if (healAmount <= 0.0) {
            return;
        }
        if (plugin == null) {
            healBloodMoonAttacker(attacker, healAmount);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> healBloodMoonAttacker(attacker, healAmount));
    }

    protected void healBloodMoonAttacker(Player attacker, double healAmount) {
        if (attacker == null || healAmount <= 0.0 || !attacker.isOnline()) {
            return;
        }
        if (!isParticipant(attacker.getUniqueId()) || !isInArenaWorld(attacker.getWorld())) {
            return;
        }
        double currentHealth = attacker.getHealth();
        double newHealth = Math.min(attacker.getMaxHealth(), currentHealth + healAmount);
        if (newHealth <= currentHealth) {
            return;
        }
        attacker.setHealth(newHealth);
        attacker.getWorld().spawnParticle(Particle.HEART,
                attacker.getLocation().add(0.0, 1.0, 0.0),
                2,
                0.25,
                0.35,
                0.25,
                0.0);
    }

    public void setRotatingMode(RotatingSelectionMode mode) {
        if (mode == null) {
            return;
        }
        rotatingMode = mode;
        if (rotatingMode == RotatingSelectionMode.MANUAL) {
            syncManualRotatingSelection();
        } else {
            rotatingItemIds.clear();
            rotatingUpgradeIds.clear();
        }
    }

    public RotatingSelectionMode cycleRotatingMode() {
        RotatingSelectionMode[] values = RotatingSelectionMode.values();
        int next = (rotatingMode.ordinal() + 1) % values.length;
        setRotatingMode(values[next]);
        return rotatingMode;
    }

    public List<String> getManualRotatingItemIds() {
        if (bedwarsManager.getShopConfig() != null) {
            sanitizeManualRotatingSelection(getRotatingItemCandidateIds(), getRotatingUpgradeCandidateIds());
        }
        return Collections.unmodifiableList(manualRotatingItemIds);
    }

    public List<String> getManualRotatingUpgradeIds() {
        if (bedwarsManager.getShopConfig() != null) {
            sanitizeManualRotatingSelection(getRotatingItemCandidateIds(), getRotatingUpgradeCandidateIds());
        }
        return Collections.unmodifiableList(manualRotatingUpgradeIds);
    }

    public boolean toggleManualRotatingItem(String id) {
        String normalized = normalizeItemId(id);
        if (normalized == null) {
            return false;
        }
        List<String> itemCandidates = getRotatingItemCandidateIds();
        List<String> upgradeCandidates = getRotatingUpgradeCandidateIds();
        sanitizeManualRotatingSelection(itemCandidates, upgradeCandidates);
        if (!itemCandidates.contains(normalized)) {
            return false;
        }
        if (manualRotatingItemIds.remove(normalized)) {
            syncManualRotatingSelection();
            return true;
        }
        manualRotatingItemIds.add(normalized);
        syncManualRotatingSelection();
        return true;
    }

    public boolean toggleManualRotatingUpgrade(String id) {
        String normalized = normalizeItemId(id);
        if (normalized == null) {
            return false;
        }
        List<String> itemCandidates = getRotatingItemCandidateIds();
        List<String> upgradeCandidates = getRotatingUpgradeCandidateIds();
        sanitizeManualRotatingSelection(itemCandidates, upgradeCandidates);
        if (!upgradeCandidates.contains(normalized)) {
            return false;
        }
        if (manualRotatingUpgradeIds.remove(normalized)) {
            syncManualRotatingSelection();
            return true;
        }
        manualRotatingUpgradeIds.add(normalized);
        syncManualRotatingSelection();
        return true;
    }

    public List<String> getRotatingItemCandidateIds() {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        addRotatingItemCandidates(candidates, config.getCategory(ShopCategoryType.ROTATING), config);
        return candidates;
    }

    public List<String> getRotatingUpgradeCandidateIds() {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        addRotatingUpgradeCandidates(candidates, resolveRotatingUpgradeCategory(config), config);
        return candidates;
    }

    protected void sanitizeManualRotatingSelection(List<String> itemCandidates, List<String> upgradeCandidates) {
        Set<String> validItems = new HashSet<>(itemCandidates);
        LinkedHashSet<String> sanitizedItems = new LinkedHashSet<>();
        for (String id : manualRotatingItemIds) {
            String normalized = normalizeItemId(id);
            if (normalized == null || !validItems.contains(normalized)) {
                continue;
            }
            sanitizedItems.add(normalized);
        }
        manualRotatingItemIds.clear();
        manualRotatingItemIds.addAll(sanitizedItems);

        Set<String> validUpgrades = new HashSet<>(upgradeCandidates);
        LinkedHashSet<String> sanitizedUpgrades = new LinkedHashSet<>();
        for (String id : manualRotatingUpgradeIds) {
            String normalized = normalizeItemId(id);
            if (normalized == null || !validUpgrades.contains(normalized)) {
                continue;
            }
            sanitizedUpgrades.add(normalized);
        }
        manualRotatingUpgradeIds.clear();
        manualRotatingUpgradeIds.addAll(sanitizedUpgrades);
    }

    protected void addRotatingItemCandidates(List<String> candidates,
                                           krispasi.omGames.bedwars.shop.ShopCategory category,
                                           ShopConfig config) {
        if (category == null || category.getEntries().isEmpty()) {
            return;
        }
        for (String id : category.getEntries().values()) {
            String normalized = normalizeItemId(id);
            ShopItemDefinition definition = config != null && normalized != null ? config.getItem(normalized) : null;
            if (normalized != null
                    && definition != null
                    && definition.getBehavior() != ShopItemBehavior.UPGRADE
                    && !candidates.contains(normalized)) {
                candidates.add(normalized);
            }
        }
    }

    protected void addRotatingUpgradeCandidates(List<String> candidates,
                                              krispasi.omGames.bedwars.shop.ShopCategory category,
                                              ShopConfig config) {
        if (category == null || category.getEntries().isEmpty()) {
            return;
        }
        for (String id : category.getEntries().values()) {
            String normalized = normalizeItemId(id);
            ShopItemDefinition definition = config != null && normalized != null ? config.getItem(normalized) : null;
            if (normalized != null
                    && definition != null
                    && definition.getBehavior() == ShopItemBehavior.UPGRADE
                    && !candidates.contains(normalized)) {
                candidates.add(normalized);
            }
        }
    }

    public boolean isRotatingItemAvailable(ShopItemDefinition item) {
        if (item == null) {
            return false;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return true;
        }
        krispasi.omGames.bedwars.shop.ShopCategory category = config.getCategory(ShopCategoryType.ROTATING);
        if (category == null || category.getEntries().isEmpty()) {
            return true;
        }
        boolean rotating = category.getEntries().containsValue(item.getId());
        if (!rotating) {
            return true;
        }
        if (suddenDeathActive && item.isDisabledAfterSuddenDeath()) {
            return false;
        }
        if (rotatingMode == RotatingSelectionMode.MANUAL) {
            return rotatingItemIds.contains(item.getId());
        }
        if (rotatingItemIds.isEmpty()) {
            return true;
        }
        return rotatingItemIds.contains(item.getId());
    }

    public boolean isRotatingUpgradeAvailable(TeamUpgradeType type) {
        if (type == null) {
            return true;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return true;
        }
        krispasi.omGames.bedwars.shop.ShopCategory category = resolveRotatingUpgradeCategory(config);
        if (category == null || category.getEntries().isEmpty()) {
            return true;
        }
        boolean hasRotating = false;
        for (String id : category.getEntries().values()) {
            ShopItemDefinition definition = config.getItem(id);
            if (definition == null || definition.getBehavior() != ShopItemBehavior.UPGRADE) {
                continue;
            }
            if (definition.getUpgradeType() != type) {
                continue;
            }
            hasRotating = true;
            boolean selected = rotatingMode == RotatingSelectionMode.MANUAL
                    ? rotatingUpgradeIds.contains(definition.getId())
                    : rotatingUpgradeIds.isEmpty() || rotatingUpgradeIds.contains(definition.getId());
            if (!selected) {
                continue;
            }
            if (suddenDeathActive && definition.isDisabledAfterSuddenDeath()) {
                return false;
            }
            return true;
        }
        return !hasRotating;
    }

    public ShopItemDefinition getRotatingUpgradeDefinition(TeamUpgradeType type) {
        if (type == null) {
            return null;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return null;
        }
        return findRotatingUpgradeDefinition(config, resolveRotatingUpgradeCategory(config), type);
    }

    public boolean isRotatingTrapAvailable(TrapType trap) {
        if (trap == null) {
            return false;
        }
        String itemId = normalizeItemId(trap.rotatingUpgradeItemId());
        if (itemId == null) {
            return true;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null || !isListedInRotatingUpgradeCategories(config, itemId)) {
            return false;
        }
        ShopItemDefinition definition = config.getItem(itemId);
        if (definition == null) {
            return false;
        }
        if (suddenDeathActive && definition.isDisabledAfterSuddenDeath()) {
            return false;
        }
        return isRotatingUpgradeEntrySelected(itemId);
    }

    public ShopItemDefinition getRotatingTrapDefinition(TrapType trap) {
        if (trap == null) {
            return null;
        }
        String itemId = normalizeItemId(trap.rotatingUpgradeItemId());
        if (itemId == null) {
            return null;
        }
        ShopConfig config = bedwarsManager.getShopConfig();
        return config != null ? config.getItem(itemId) : null;
    }

    protected ShopItemDefinition findRotatingUpgradeDefinition(ShopConfig config,
                                                             krispasi.omGames.bedwars.shop.ShopCategory category,
                                                             TeamUpgradeType type) {
        if (config == null || category == null || type == null) {
            return null;
        }
        for (String id : category.getEntries().values()) {
            ShopItemDefinition definition = config.getItem(id);
            if (definition == null || definition.getBehavior() != ShopItemBehavior.UPGRADE) {
                continue;
            }
            if (definition.getUpgradeType() == type) {
                return definition;
            }
        }
        return null;
    }

    protected boolean isListedInRotatingUpgradeCategories(ShopConfig config, String itemId) {
        if (config == null || itemId == null) {
            return false;
        }
        krispasi.omGames.bedwars.shop.ShopCategory upgradeCategory = resolveRotatingUpgradeCategory(config);
        return upgradeCategory != null && upgradeCategory.getEntries().containsValue(itemId);
    }

    protected boolean isRotatingUpgradeEntrySelected(String itemId) {
        if (itemId == null) {
            return false;
        }
        if (rotatingMode == RotatingSelectionMode.MANUAL) {
            return rotatingUpgradeIds.contains(itemId);
        }
        return rotatingUpgradeIds.isEmpty() || rotatingUpgradeIds.contains(itemId);
    }

    protected void rollRotatingItems() {
        rotatingItemIds.clear();
        rotatingUpgradeIds.clear();
        List<String> itemCandidates = getRotatingItemCandidateIds();
        List<String> upgradeCandidates = getRotatingUpgradeCandidateIds();
        sanitizeManualRotatingSelection(itemCandidates, upgradeCandidates);
        if (rotatingMode == RotatingSelectionMode.MANUAL) {
            for (String id : manualRotatingItemIds) {
                String normalized = normalizeItemId(id);
                if (normalized != null && itemCandidates.contains(normalized)) {
                    rotatingItemIds.add(normalized);
                }
            }
            for (String id : manualRotatingUpgradeIds) {
                String normalized = normalizeItemId(id);
                if (normalized != null && upgradeCandidates.contains(normalized)) {
                    rotatingUpgradeIds.add(normalized);
                }
            }
            return;
        }
        rotatingItemIds.addAll(pickRandom(itemCandidates, 2));
        rotatingUpgradeIds.addAll(pickRandom(upgradeCandidates, 1));
        normalizeAutoRotatingSelection(itemCandidates, upgradeCandidates);
    }

    protected krispasi.omGames.bedwars.shop.ShopCategory resolveRotatingUpgradeCategory(ShopConfig config) {
        if (config == null) {
            return null;
        }
        krispasi.omGames.bedwars.shop.ShopCategory upgradeCategory = config.getCategory(ShopCategoryType.ROTATING_UPGRADES);
        if (upgradeCategory != null && !upgradeCategory.getEntries().isEmpty()) {
            return upgradeCategory;
        }
        return config.getCategory(ShopCategoryType.ROTATING);
    }

    protected void normalizeAutoRotatingSelection(List<String> itemCandidates, List<String> upgradeCandidates) {
        if (rotatingMode == RotatingSelectionMode.MANUAL) {
            return;
        }
        normalizeAutoSelection(rotatingItemIds, itemCandidates, 2);
        normalizeAutoSelection(rotatingUpgradeIds, upgradeCandidates, 1);
    }

    protected void normalizeAutoSelection(Set<String> selected, List<String> candidates, int targetCount) {
        if (selected == null) {
            return;
        }
        if (candidates == null || candidates.isEmpty() || targetCount <= 0) {
            selected.clear();
            return;
        }
        List<String> validCandidates = new ArrayList<>();
        for (String candidate : candidates) {
            String normalized = normalizeItemId(candidate);
            if (normalized != null && !validCandidates.contains(normalized)) {
                validCandidates.add(normalized);
            }
        }
        int target = Math.min(targetCount, validCandidates.size());
        if (target <= 0) {
            selected.clear();
            return;
        }
        selected.retainAll(validCandidates);
        if (selected.size() > target) {
            List<String> ordered = new ArrayList<>(selected);
            selected.clear();
            selected.addAll(ordered.subList(0, target));
            return;
        }
        if (selected.size() == target) {
            return;
        }
        List<String> remaining = new ArrayList<>();
        for (String candidate : validCandidates) {
            if (!selected.contains(candidate)) {
                remaining.add(candidate);
            }
        }
        Collections.shuffle(remaining);
        for (String candidate : remaining) {
            if (selected.size() >= target) {
                break;
            }
            selected.add(candidate);
        }
    }

    protected void announceCurrentRotatingItems() {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return;
        }
        if (getRotatingItemCandidateIds().isEmpty() && getRotatingUpgradeCandidateIds().isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (String id : rotatingItemIds) {
            names.add(resolveRotatingItemName(id, config.getItem(id)));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        String itemText = names.isEmpty() ? "none" : String.join(", ", names);
        List<String> upgradeNames = new ArrayList<>();
        for (String id : rotatingUpgradeIds) {
            upgradeNames.add(resolveRotatingItemName(id, config.getItem(id)));
        }
        upgradeNames.sort(String.CASE_INSENSITIVE_ORDER);
        String upgradeText = upgradeNames.isEmpty() ? "none" : String.join(", ", upgradeNames);
        broadcast(Component.text("Rotation Items: ", NamedTextColor.AQUA)
                .append(Component.text(itemText, NamedTextColor.YELLOW))
                .append(Component.text(" | Upgrades: ", NamedTextColor.AQUA))
                .append(Component.text(upgradeText, NamedTextColor.YELLOW)));
    }

    protected String resolveRotatingItemName(String id, ShopItemDefinition definition) {
        if (definition != null) {
            String displayName = definition.getDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
            TeamUpgradeType upgradeType = definition.getUpgradeType();
            if (upgradeType != null) {
                return upgradeType.displayName();
            }
        }
        return humanizeRotatingId(id);
    }

    protected String humanizeRotatingId(String id) {
        if (id == null || id.isBlank()) {
            return "Unknown";
        }
        String normalized = id.replace('-', ' ').replace('_', ' ').trim();
        if (normalized.isBlank()) {
            return id;
        }
        String[] parts = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            String lower = part.toLowerCase(Locale.ROOT);
            builder.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                builder.append(lower.substring(1));
            }
        }
        return builder.length() > 0 ? builder.toString() : id;
    }

    protected List<String> pickRandom(List<String> candidates, int count) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int target = Math.max(1, Math.min(count, candidates.size()));
        if (candidates.size() <= target) {
            return new ArrayList<>(candidates);
        }
        List<String> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, target));
    }

    protected boolean isRotatingItemCandidate(String id) {
        String normalized = normalizeItemId(id);
        if (normalized == null) {
            return false;
        }
        return getRotatingItemCandidateIds().contains(normalized);
    }

    protected void syncManualRotatingSelection() {
        if (rotatingMode != RotatingSelectionMode.MANUAL) {
            return;
        }
        if (bedwarsManager.getShopConfig() != null) {
            sanitizeManualRotatingSelection(getRotatingItemCandidateIds(), getRotatingUpgradeCandidateIds());
        }
        rotatingItemIds.clear();
        rotatingUpgradeIds.clear();
        for (String id : manualRotatingItemIds) {
            String normalized = normalizeItemId(id);
            if (normalized != null) {
                rotatingItemIds.add(normalized);
            }
        }
        for (String id : manualRotatingUpgradeIds) {
            String normalized = normalizeItemId(id);
            if (normalized != null) {
                rotatingUpgradeIds.add(normalized);
            }
        }
    }

    protected String normalizeItemId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    public int getRemainingLimit(UUID playerId, ShopItemDefinition item) {
        if (playerId == null || item == null) {
            return -1;
        }
        ShopItemLimit limit = item.getLimit();
        if (limit == null || !limit.isValid()) {
            return -1;
        }
        int current = 0;
        if (limit.scope() == ShopItemLimitScope.PLAYER) {
            Map<String, Integer> counts = playerPurchaseCounts.get(playerId);
            current = counts != null ? counts.getOrDefault(item.getId(), 0) : 0;
        } else if (limit.scope() == ShopItemLimitScope.TEAM) {
            TeamColor team = getTeam(playerId);
            if (team == null) {
                return 0;
            }
            Map<String, Integer> counts = teamPurchaseCounts.get(team);
            current = counts != null ? counts.getOrDefault(item.getId(), 0) : 0;
        }
        int remaining = limit.amount() - current;
        return Math.max(0, remaining);
    }

    public int getRemainingCarryAmount(UUID playerId, ShopItemDefinition item) {
        if (playerId == null || item == null) {
            return -1;
        }
        int maxCarryAmount = item.getMaxCarryAmount();
        if (maxCarryAmount <= 0) {
            return -1;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return 0;
        }
        return Math.max(0, maxCarryAmount - countCarriedShopItem(player, item.getId()));
    }

    protected void closeFakeEnderChests() {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                continue;
            }
            Inventory open = player.getOpenInventory().getTopInventory();
            if (fakeEnderChests.containsValue(open)) {
                player.closeInventory();
            }
        }
        for (Inventory inventory : fakeEnderChests.values()) {
            inventory.clear();
        }
    }

    protected void showTitleAll(Component title, Component subtitle) {
        Title message = Title.title(title, subtitle, DEFAULT_TITLE_TIMES);
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.showTitle(message);
            }
        }
    }

    protected void showTitle(Player player, Component title, Component subtitle) {
        Title message = Title.title(title, subtitle, DEFAULT_TITLE_TIMES);
        player.showTitle(message);
    }

    protected void broadcast(Component message) {
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    protected void setSpectator(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        clearUpgradeEffects(player);
        updateSidebarForPlayer(player);
    }

    public boolean hasRespawnProtection(UUID playerId) {
        Long end = respawnProtectionEnds.get(playerId);
        if (end == null) {
            return false;
        }
        if (System.currentTimeMillis() >= end) {
            removeRespawnProtection(playerId);
            return false;
        }
        return true;
    }

    public void removeRespawnProtection(UUID playerId) {
        respawnProtectionEnds.remove(playerId);
        BukkitTask task = respawnProtectionTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    protected void grantRespawnProtection(Player player) {
        UUID playerId = player.getUniqueId();
        removeRespawnProtection(playerId);
        respawnProtectionEnds.put(playerId,
                System.currentTimeMillis() + RESPAWN_PROTECTION_SECONDS * 1000L);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> removeRespawnProtection(playerId),
                RESPAWN_PROTECTION_SECONDS * 20L);
        respawnProtectionTasks.put(playerId, task);
    }

    protected void giveStarterKit(Player player, TeamColor team) {
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        player.getInventory().addItem(sword);
        equipBaseArmor(player, team);
    }

    protected void giveTeamSelectItem(Player player) {
        ItemStack item = new ItemStack(Material.RED_BED);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pick a Team", NamedTextColor.YELLOW));
        meta.lore(List.of(
                Component.text("Right click to choose your team.", NamedTextColor.GRAY)
        ));
        TeamSelectItemData.apply(meta);
        item.setItemMeta(meta);
        placeLobbyItem(player, item, 4);
    }

    protected void giveLobbyControlItems(Player player) {
        if (player == null) {
            return;
        }
        ItemStack manage = new ItemStack(Material.WHITE_BED);
        ItemMeta manageMeta = manage.getItemMeta();
        manageMeta.displayName(Component.text("Manage Teams", NamedTextColor.YELLOW));
        manageMeta.lore(List.of(
                Component.text("Right click to edit teams.", NamedTextColor.GRAY)
        ));
        krispasi.omGames.bedwars.item.LobbyControlItemData.apply(manageMeta, "manage");
        manage.setItemMeta(manageMeta);

        ItemStack pause = new ItemStack(Material.RED_CONCRETE);
        ItemMeta pauseMeta = pause.getItemMeta();
        pauseMeta.displayName(Component.text("Pause Timer", NamedTextColor.RED));
        pauseMeta.lore(List.of(
                Component.text("Right click to pause/resume.", NamedTextColor.GRAY)
        ));
        krispasi.omGames.bedwars.item.LobbyControlItemData.apply(pauseMeta, "pause");
        pause.setItemMeta(pauseMeta);

        ItemStack skip = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta skipMeta = skip.getItemMeta();
        skipMeta.displayName(Component.text("Skip Timer", NamedTextColor.GREEN));
        skipMeta.lore(List.of(
                Component.text("Right click to start.", NamedTextColor.GRAY)
        ));
        krispasi.omGames.bedwars.item.LobbyControlItemData.apply(skipMeta, "skip");
        skip.setItemMeta(skipMeta);

        placeLobbyItem(player, manage, 0);
        placeLobbyItem(player, pause, 1);
        placeLobbyItem(player, skip, 2);
    }

    protected void placeLobbyItem(Player player, ItemStack item, int slot) {
        if (player == null || item == null) {
            return;
        }
        if (slot >= 0 && slot < 9 && player.getInventory().getItem(slot) == null) {
            player.getInventory().setItem(slot, item);
            return;
        }
        player.getInventory().addItem(item);
    }

    protected ItemStack colorLeatherArmor(Material material, org.bukkit.Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    protected void applyPermanentItems(Player player, TeamColor team) {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null || team == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        int armorTier = armorTiers.getOrDefault(playerId, 0);
        equipBaseArmor(player, team);
        if (armorTier > 0) {
            equipArmor(player, team, config, armorTier);
        }
        int pickaxeTier = pickaxeTiers.getOrDefault(playerId, 0);
        if (pickaxeTier > 0) {
            equipTieredItem(player, team, config, ShopItemBehavior.PICKAXE, pickaxeTier, PICKAXE_MATERIALS);
        }
        int axeTier = axeTiers.getOrDefault(playerId, 0);
        if (axeTier > 0) {
            equipTieredItem(player, team, config, ShopItemBehavior.AXE, axeTier, AXE_MATERIALS);
        }
        if (shearsUnlocked.contains(playerId)) {
            giveShears(player, team);
        }
        if (shieldUnlocked.contains(playerId)) {
            giveShield(player, team);
        }
        applyTeamUpgrades(player, team);
        player.updateInventory();
    }

    protected void applyPermanentItemsWithShield(Player player, TeamColor team) {
        applyPermanentItems(player, team);
        equipShieldOffhand(player, team);
    }

    protected void equipBaseArmor(Player player, TeamColor team) {
        if (team == null) {
            return;
        }
        org.bukkit.Color color = team.dyeColor().getColor();
        player.getInventory().setHelmet(colorLeatherArmor(Material.LEATHER_HELMET, color));
        player.getInventory().setChestplate(colorLeatherArmor(Material.LEATHER_CHESTPLATE, color));
        player.getInventory().setLeggings(colorLeatherArmor(Material.LEATHER_LEGGINGS, color));
        player.getInventory().setBoots(colorLeatherArmor(Material.LEATHER_BOOTS, color));
    }

    protected boolean applyTierUpgrade(Player player,
                                     ShopItemDefinition item,
                                     Map<UUID, Integer> tierMap,
                                     ShopItemBehavior behavior) {
        int tier = item.getTier();
        if (tier <= 0) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        int current = tierMap.getOrDefault(playerId, 0);
        if (behavior == ShopItemBehavior.PICKAXE || behavior == ShopItemBehavior.AXE) {
            if (tier != current + 1) {
                return false;
            }
        } else if (tier <= current) {
            return false;
        }
        tierMap.put(playerId, tier);
        applyPermanentItems(player, getTeam(playerId));
        return true;
    }

    protected boolean applyShears(Player player, ShopItemDefinition item, TeamColor team) {
        UUID playerId = player.getUniqueId();
        if (shearsUnlocked.contains(playerId)) {
            return false;
        }
        shearsUnlocked.add(playerId);
        giveItem(player, item.createPurchaseItem(team));
        applyTeamUpgrades(player, team);
        return true;
    }

    protected boolean applyShield(Player player) {
        UUID playerId = player.getUniqueId();
        if (shieldUnlocked.contains(playerId)) {
            return false;
        }
        shieldUnlocked.add(playerId);
        return true;
    }

    protected void equipTieredItem(Player player,
                                 TeamColor team,
                                 ShopConfig config,
                                 ShopItemBehavior behavior,
                                 int tier,
                                 Set<Material> removeSet) {
        ShopItemDefinition definition = config.getTieredItem(behavior, tier).orElse(null);
        if (definition == null) {
            return;
        }
        removeItems(player, removeSet);
        giveItem(player, definition.createPurchaseItem(team));
    }

    protected int resolveToolTier(Player player, ShopConfig config, ShopItemBehavior behavior) {
        if (player == null || config == null) {
            return 0;
        }
        Map<Integer, ShopItemDefinition> tiered = config.getTieredItems(behavior);
        if (tiered == null || tiered.isEmpty()) {
            return 0;
        }
        Map<Material, Integer> materialTiers = new EnumMap<>(Material.class);
        for (Map.Entry<Integer, ShopItemDefinition> entry : tiered.entrySet()) {
            Integer tier = entry.getKey();
            ShopItemDefinition definition = entry.getValue();
            if (tier == null || tier <= 0 || definition == null) {
                continue;
            }
            Material material = definition.getMaterial();
            if (material == null) {
                continue;
            }
            materialTiers.merge(material, tier, Math::max);
        }
        if (materialTiers.isEmpty()) {
            return 0;
        }
        int foundTier = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null) {
                continue;
            }
            Integer tier = materialTiers.get(item.getType());
            if (tier != null) {
                foundTier = Math.max(foundTier, tier);
            }
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null) {
            Integer tier = materialTiers.get(main.getType());
            if (tier != null) {
                foundTier = Math.max(foundTier, tier);
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null) {
            Integer tier = materialTiers.get(offhand.getType());
            if (tier != null) {
                foundTier = Math.max(foundTier, tier);
            }
        }
        return foundTier;
    }

    protected void updateToolTier(Map<UUID, Integer> map, UUID playerId, int tier) {
        if (playerId == null) {
            return;
        }
        if (tier <= 0) {
            map.remove(playerId);
        } else {
            map.put(playerId, tier);
        }
    }

    protected boolean hasInventoryItem(Player player, Material material) {
        if (player == null || material == null) {
            return false;
        }
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                return true;
            }
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() == material) {
            return true;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand != null && offhand.getType() == material;
    }

    protected void equipArmor(Player player, TeamColor team, ShopConfig config, int tier) {
        ShopItemDefinition definition = config.getTieredItem(ShopItemBehavior.ARMOR, tier).orElse(null);
        if (definition == null) {
            return;
        }
        String base = armorBase(definition.getMaterial());
        Material leggings = Material.matchMaterial(base + "_LEGGINGS");
        Material boots = Material.matchMaterial(base + "_BOOTS");
        if (leggings == null || boots == null) {
            return;
        }
        org.bukkit.Color color = team.dyeColor().getColor();
        player.getInventory().setHelmet(colorLeatherArmor(Material.LEATHER_HELMET, color));
        player.getInventory().setChestplate(colorLeatherArmor(Material.LEATHER_CHESTPLATE, color));
        if ("LEATHER".equals(base)) {
            player.getInventory().setLeggings(colorLeatherArmor(leggings, color));
            player.getInventory().setBoots(colorLeatherArmor(boots, color));
        } else {
            player.getInventory().setLeggings(makeUnbreakable(new ItemStack(leggings)));
            player.getInventory().setBoots(makeUnbreakable(new ItemStack(boots)));
        }
    }

    protected ItemStack makeUnbreakable(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    protected String armorBase(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET")) {
            return name.substring(0, name.length() - "_HELMET".length());
        }
        if (name.endsWith("_CHESTPLATE")) {
            return name.substring(0, name.length() - "_CHESTPLATE".length());
        }
        if (name.endsWith("_LEGGINGS")) {
            return name.substring(0, name.length() - "_LEGGINGS".length());
        }
        if (name.endsWith("_BOOTS")) {
            return name.substring(0, name.length() - "_BOOTS".length());
        }
        return name;
    }

    protected void giveShears(Player player, TeamColor team) {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return;
        }
        if (player.getInventory().contains(Material.SHEARS)) {
            return;
        }
        ShopItemDefinition definition = config.getFirstByBehavior(ShopItemBehavior.SHEARS);
        if (definition != null) {
            giveItem(player, definition.createPurchaseItem(team));
        } else {
            giveItem(player, new ItemStack(Material.SHEARS));
        }
    }

    protected void giveShield(Player player, TeamColor team) {
        if (countItem(player, Material.SHIELD) > 0) {
            return;
        }
        ItemStack shield = createShieldItem(team);
        if (shield != null) {
            giveItem(player, shield);
        }
    }

    protected ItemStack createShieldItem(TeamColor team) {
        ShopConfig config = bedwarsManager.getShopConfig();
        if (config == null) {
            return new ItemStack(Material.SHIELD);
        }
        ShopItemDefinition definition = config.getFirstByBehavior(ShopItemBehavior.SHIELD);
        if (definition != null) {
            return definition.createPurchaseItem(team);
        }
        return new ItemStack(Material.SHIELD);
    }

    protected void equipShieldOffhand(Player player, TeamColor team) {
        if (player == null) {
            return;
        }
        if (!shieldUnlocked.contains(player.getUniqueId())) {
            return;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == Material.SHIELD) {
            return;
        }
        ItemStack shield = removeShieldFromStorage(player);
        if (shield == null) {
            shield = createShieldItem(team);
        }
        if (shield == null) {
            shield = new ItemStack(Material.SHIELD);
        }
        if (offhand != null && !offhand.getType().isAir()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(offhand);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.getInventory().setItemInOffHand(shield);
    }

    protected ItemStack removeShieldFromStorage(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.SHIELD) {
                continue;
            }
            contents[i] = null;
            player.getInventory().setStorageContents(contents);
            return item;
        }
        return null;
    }

    protected void downgradeTools(UUID playerId) {
        downgradeTier(pickaxeTiers, playerId);
        downgradeTier(axeTiers, playerId);
    }

    protected void downgradeTier(Map<UUID, Integer> map, UUID playerId) {
        int tier = map.getOrDefault(playerId, 0);
        if (tier > 1) {
            map.put(playerId, tier - 1);
        }
    }

    protected boolean hasResources(Player player, ShopCost cost) {
        if (cost == null || !cost.isValid()) {
            return true;
        }
        int remaining = cost.amount();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != cost.material()) {
                continue;
            }
            remaining -= item.getAmount();
            if (remaining <= 0) {
                return true;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == cost.material()) {
            remaining -= offhand.getAmount();
        }
        return remaining <= 0;
    }

    protected int countItem(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && offhand.getType() == material) {
            count += offhand.getAmount();
        }
        return count;
    }

    protected int countCarriedShopItem(Player player, String itemId) {
        if (player == null || itemId == null || itemId.isBlank()) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null) {
                continue;
            }
            if (itemId.equalsIgnoreCase(ShopItemData.getId(item))) {
                count += item.getAmount();
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && itemId.equalsIgnoreCase(ShopItemData.getId(offhand))) {
            count += offhand.getAmount();
        }
        return count;
    }

    protected void removeResources(Player player, ShopCost cost) {
        if (cost == null || !cost.isValid()) {
            return;
        }
        int remaining = cost.amount();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != cost.material()) {
                continue;
            }
            int amount = item.getAmount();
            if (amount <= remaining) {
                remaining -= amount;
                contents[i] = null;
            } else {
                item.setAmount(amount - remaining);
                remaining = 0;
            }
            if (remaining <= 0) {
                break;
            }
        }
        player.getInventory().setStorageContents(contents);
        if (remaining > 0) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand != null && offhand.getType() == cost.material()) {
                int amount = offhand.getAmount();
                if (amount <= remaining) {
                    player.getInventory().setItemInOffHand(null);
                } else {
                    offhand.setAmount(amount - remaining);
                }
            }
        }
    }

    protected void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    protected void removeSwords(Player player) {
        removeItems(player, SWORD_MATERIALS);
    }

    protected void removeItems(Player player, Set<Material> materials) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && materials.contains(item.getType())) {
                contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && materials.contains(offhand.getType())) {
            player.getInventory().setItemInOffHand(null);
        }
    }

    public void handleWorldChange(Player player) {
        if (!isInArenaWorld(player.getWorld())) {
            clearElytraStrike(player, false, false);
            clearUpgradeEffects(player);
        } else if (isParticipant(player.getUniqueId())) {
            applyTeamUpgrades(player, getTeam(player.getUniqueId()));
        }
        updateSidebarForPlayer(player);
    }

    public void handlePlayerQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        TeamColor team = getTeam(playerId);
        boolean participant = isParticipant(playerId);
        boolean sessionSpectator = isSessionSpectator(player) || isLockedCommandSpectator(playerId);
        cancelRespawnCountdown(playerId);
        removeRespawnProtection(playerId);
        clearTrapImmunity(playerId);
        BukkitTask task = respawnTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        clearElytraStrike(player, false, false);
        clearUpgradeEffects(player);
        flushPendingPartyExp(player);
        if ((participant || sessionSpectator) && isInArenaWorld(player.getWorld())) {
            movePlayerToQuitLobby(player);
        }
        if (state == GameState.RUNNING && participant) {
            removeParticipant(player);
            if (team != null) {
                checkTeamEliminated(team);
            }
            return;
        }
        if (sessionSpectator) {
            lockedCommandSpectators.remove(playerId);
        }
        restoreSidebar(playerId);
    }

    public void handlePlayerJoin(Player player) {
        if (!isActive()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (isLockedCommandSpectator(playerId)) {
            Location spectate = resolveMapLobbyLocation();
            setSpectator(player);
            if (spectate != null) {
                player.teleport(spectate);
            }
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }
        if (!isParticipant(playerId)) {
            return;
        }
        if (state == GameState.LOBBY) {
            Location lobby = resolveMapLobbyLocation();
            if (lobby != null) {
                player.teleport(lobby);
            }
            player.getInventory().clear();
            if (isLobbyInitiator(playerId)) {
                giveLobbyControlItems(player);
            }
            if (teamPickEnabled) {
                giveTeamSelectItem(player);
            }
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            applyLobbyBuffs(player);
            syncToolTiers(player);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }

        TeamColor team = getTeam(playerId);
        if (team == null) {
            return;
        }
        if (state == GameState.STARTING) {
            Location spawn = arena.getSpawn(team);
            if (spawn != null) {
                player.teleport(spawn);
            }
            player.getInventory().clear();
            giveStarterKit(player, team);
            applyPermanentItemsWithShield(player, team);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            frozenPlayers.add(playerId);
            syncToolTiers(player);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }

        if (state != GameState.RUNNING) {
            return;
        }

        Location lobby = resolveMapLobbyLocation();
        if (eliminatedPlayers.contains(playerId)) {
            setSpectator(player);
            if (lobby != null) {
                player.teleport(lobby);
            }
            syncToolTiers(player);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }

        if (pendingRespawns.contains(playerId)) {
            setSpectator(player);
            if (lobby != null) {
                player.teleport(lobby);
            }
            boolean allowRespawnAfterBedBreak = respawnGracePlayers.contains(playerId);
            if (getBedState(team) == BedState.DESTROYED && !allowRespawnAfterBedBreak) {
                pendingRespawns.remove(playerId);
                awardPendingDeathFinalStats(playerId);
                eliminatePlayer(player, team);
                syncToolTiers(player);
                hideEditorsFrom(player);
                updateSidebarForPlayer(player);
                return;
            }
            scheduleRespawn(player, team, RESPAWN_DELAY_SECONDS, allowRespawnAfterBedBreak);
            syncToolTiers(player);
            hideEditorsFrom(player);
            updateSidebarForPlayer(player);
            return;
        }

        Location spawn = arena.getSpawn(team);
        if (spawn != null) {
            player.teleport(spawn);
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        grantRespawnProtection(player);
        applyPermanentItemsWithShield(player, team);
        syncToolTiers(player);
        hideEditorsFrom(player);
        updateSidebarForPlayer(player);
    }

    protected void movePlayerToQuitLobby(Player player) {
        if (player == null) {
            return;
        }
        Location lobby = resolveMapLobbyLocation();
        if (lobby != null) {
            player.teleport(lobby);
            player.setRespawnLocation(lobby, true);
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGliding(false);
    }

    protected void startSidebarUpdates() {
        if (sidebarTask != null) {
            sidebarTask.cancel();
        }
        sidebarTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> safeRun("sidebarUpdate", this::updateSidebars),
                0L,
                20L);
        tasks.add(sidebarTask);
    }

    protected void updateSidebars() {
        Set<UUID> updated = new HashSet<>();
        if (state == GameState.LOBBY) {
            applyLobbyBuffsToLobbyPlayers();
        }
        for (UUID playerId : assignments.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                updated.add(playerId);
                updateSidebarForPlayer(player);
            }
        }
        World world = arena.getWorld();
        if (world != null) {
            for (Player player : world.getPlayers()) {
                UUID playerId = player.getUniqueId();
                if (!updated.add(playerId)) {
                    continue;
                }
                updateSidebarForPlayer(player);
            }
        }
        for (UUID playerId : new HashSet<>(activeScoreboards.keySet())) {
            if (!updated.add(playerId)) {
                continue;
            }
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                updateSidebarForPlayer(player);
            } else {
                restoreSidebar(playerId);
            }
        }
    }

    protected void updateSidebarForPlayer(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!shouldShowSidebar(player)) {
            restoreSidebar(playerId);
            return;
        }
        Scoreboard scoreboard = activeScoreboards.get(playerId);
        if (scoreboard == null) {
            previousScoreboards.putIfAbsent(playerId, player.getScoreboard());
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            activeScoreboards.put(playerId, scoreboard);
        }
        ensureSidebarObjective(scoreboard);
        ensureHealthObjective(scoreboard);
        player.setScoreboard(scoreboard);
        updateTeamColors(scoreboard);
        updateSidebarLines(player, scoreboard);
        updateBelowNameHealth(scoreboard);
    }

    public void refreshSidebar(Player player) {
        updateSidebarForPlayer(player);
    }

    protected boolean shouldShowSidebar(Player player) {
        if (player == null || !isActive() || !isInArenaWorld(player.getWorld())) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        return isParticipant(playerId) || isSessionSpectator(player);
    }

    protected boolean isSessionSpectator(Player player) {
        return player != null
                && player.getGameMode() == GameMode.SPECTATOR
                && !isEditor(player.getUniqueId());
    }

    protected void restoreSidebar(UUID playerId) {
        Scoreboard previous = previousScoreboards.remove(playerId);
        Scoreboard active = activeScoreboards.remove(playerId);
        List<String> lines = sidebarLines.remove(playerId);
        if (active != null && lines != null) {
            for (String line : lines) {
                active.resetScores(line);
            }
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.setScoreboard(previous != null
                    ? previous
                    : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    protected void clearSidebars() {
        for (UUID playerId : new HashSet<>(activeScoreboards.keySet())) {
            restoreSidebar(playerId);
        }
    }

    protected void updateSidebarLines(Player player, Scoreboard scoreboard) {
        Objective objective = ensureSidebarObjective(scoreboard);
        List<String> previous = sidebarLines.get(player.getUniqueId());
        if (previous != null) {
            for (String line : previous) {
                scoreboard.resetScores(line);
            }
        }
        List<String> lines = buildSidebarLines(player);
        int score = lines.size();
        for (String line : lines) {
            objective.getScore(line).setScore(score--);
        }
        sidebarLines.put(player.getUniqueId(), lines);
    }

    protected Objective ensureSidebarObjective(Scoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(SIDEBAR_OBJECTIVE_ID);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(
                    SIDEBAR_OBJECTIVE_ID,
                    "dummy",
                    Component.text("BED WARS", NamedTextColor.GOLD)
            );
        }
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        return objective;
    }

    protected Objective ensureHealthObjective(Scoreboard scoreboard) {
        Objective objective = scoreboard.getObjective(HEALTH_OBJECTIVE_ID);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(
                    HEALTH_OBJECTIVE_ID,
                    "dummy",
                    Component.text("HP", NamedTextColor.RED)
            );
        }
        objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        return objective;
    }

    protected void updateBelowNameHealth(Scoreboard scoreboard) {
        Objective objective = ensureHealthObjective(scoreboard);
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            Player target = Bukkit.getPlayer(entry.getKey());
            String entryName = target != null ? target.getName() : null;
            if (entryName == null || entryName.isBlank()) {
                org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
                entryName = offline != null ? offline.getName() : null;
            }
            if (entryName == null || entryName.isBlank()) {
                continue;
            }
            if (target == null || !target.isOnline() || !isInArenaWorld(target.getWorld())) {
                scoreboard.resetScores(entryName);
                continue;
            }
            int health = (int) Math.ceil(Math.max(0.0, target.getHealth()));
            objective.getScore(entryName).setScore(health);
        }
    }

    protected void updateTeamColors(Scoreboard scoreboard) {
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (team.getName().startsWith("bw_") && !teamsInMatch.contains(teamFromName(team.getName()))) {
                team.unregister();
            }
        }
        for (TeamColor teamColor : teamsInMatch) {
            String teamName = "bw_" + teamColor.key();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            team.color(teamColor.textColor());
            team.setPrefix("");
            team.setSuffix("");
            for (String entry : new HashSet<>(team.getEntries())) {
                team.removeEntry(entry);
            }
        }
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            TeamColor teamColor = entry.getValue();
            if (teamColor == null || !teamsInMatch.contains(teamColor)) {
                continue;
            }
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target == null) {
                continue;
            }
            Team team = scoreboard.getTeam("bw_" + teamColor.key());
            if (team != null) {
                team.addEntry(target.getName());
            }
        }
    }

    protected TeamColor teamFromName(String teamName) {
        if (teamName == null || !teamName.startsWith("bw_")) {
            return null;
        }
        return TeamColor.fromKey(teamName.substring(3));
    }

    protected List<String> buildSidebarLines(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.DARK_GRAY + " ");
        lines.add(buildEventLine());
        lines.add(ChatColor.DARK_GRAY + "  ");

        TeamColor playerTeam = getTeam(player.getUniqueId());
        for (TeamColor team : teamsInMatch) {
            lines.add(buildTeamLine(team, playerTeam));
        }

        lines.add(ChatColor.DARK_GRAY + "   ");
        lines.add(ChatColor.GOLD + "Kills: " + ChatColor.WHITE + getKillCount(player.getUniqueId()));
        lines.add(ChatColor.AQUA + "Made by Codex");
        return lines;
    }

    protected Location resolveMapLobbyLocation() {
        Location mapLobby = arena.getMapLobbyLocation();
        if (mapLobby != null) {
            return mapLobby;
        }
        return arena.getLobbyLocation();
    }

    protected String buildEventLine() {
        if (state == GameState.LOBBY) {
            if (lobbyCountdownPaused) {
                return ChatColor.YELLOW + "Countdown Paused";
            }
            return ChatColor.YELLOW + "Starting in " + lobbyCountdownRemaining + "s";
        }
        if (state == GameState.STARTING) {
            return ChatColor.YELLOW + "Starting in " + startCountdownRemaining + "s";
        }
        if (state != GameState.RUNNING) {
            return ChatColor.GRAY + "Waiting...";
        }
        EventInfo info = getNextEventInfo();
        return ChatColor.AQUA + info.label() + ChatColor.GRAY + " in " + formatTime(info.secondsRemaining());
    }

    protected String buildTeamLine(TeamColor team, TeamColor playerTeam) {
        String status = getTeamStatus(team);
        String you = team == playerTeam ? ChatColor.GRAY + " YOU" : "";
        return team.chatColor()
                + team.shortName()
                + " "
                + team.displayName()
                + ChatColor.WHITE
                + ": "
                + status
                + you;
    }

    protected String getTeamStatus(TeamColor team) {
        if (getBedState(team) == BedState.ALIVE) {
            return ChatColor.GREEN + "Alive";
        }
        int alive = countAlivePlayers(team);
        if (alive > 0) {
            return ChatColor.YELLOW + String.valueOf(alive);
        }
        return ChatColor.RED + "X";
    }

    protected int countAlivePlayers(TeamColor team) {
        int count = 0;
        for (Map.Entry<UUID, TeamColor> entry : assignments.entrySet()) {
            if (entry.getValue() == team && !eliminatedPlayers.contains(entry.getKey())) {
                count++;
            }
        }
        return count;
    }

    protected EventInfo getNextEventInfo() {
        long elapsedSeconds = matchStartMillis > 0
                ? (System.currentTimeMillis() - matchStartMillis) / 1000L
                : 0L;
        krispasi.omGames.bedwars.model.EventSettings events = arena.getEventSettings();
        if (!tier2Triggered) {
            return new EventInfo("Generators II", remainingSeconds(events.getTier2Delay(), elapsedSeconds));
        }
        if (!tier3Triggered) {
            return new EventInfo("Generators III", remainingSeconds(events.getTier3Delay(), elapsedSeconds));
        }
        if (!bedDestructionTriggered) {
            return new EventInfo("Beds Destroyed", remainingSeconds(events.getBedDestructionDelay(), elapsedSeconds));
        }
        if (!suddenDeathActive) {
            return new EventInfo("Sudden Death", remainingSeconds(events.getSuddenDeathDelay(), elapsedSeconds));
        }
        if (!gameEndTriggered) {
            return new EventInfo("Game End", remainingSeconds(events.getGameEndDelay(), elapsedSeconds));
        }
        return new EventInfo("Game End", 0);
    }

    protected int remainingSeconds(int targetSeconds, long elapsedSeconds) {
        return (int) Math.max(0L, (long) targetSeconds - elapsedSeconds);
    }

    protected String formatTime(int totalSeconds) {
        int minutes = Math.max(0, totalSeconds) / 60;
        int seconds = Math.max(0, totalSeconds) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

}
