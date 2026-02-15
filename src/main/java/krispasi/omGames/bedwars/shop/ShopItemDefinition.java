package krispasi.omGames.bedwars.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import krispasi.omGames.bedwars.item.CustomItemData;
import krispasi.omGames.bedwars.model.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

public class ShopItemDefinition {
    private final String id;
    private final Material material;
    private final int amount;
    private final ShopCost cost;
    private final ShopItemBehavior behavior;
    private final boolean teamColor;
    private final int tier;
    private final Map<Enchantment, Integer> enchantments;
    private final List<PotionEffect> potionEffects;
    private final String displayName;
    private final List<String> lore;
    private final String customItemId;
    private final Integer fireworkPower;
    private final FireworkEffect fireworkEffect;

    public ShopItemDefinition(String id,
                              Material material,
                              int amount,
                              ShopCost cost,
                              ShopItemBehavior behavior,
                              boolean teamColor,
                              int tier,
                              Map<Enchantment, Integer> enchantments,
                              List<PotionEffect> potionEffects,
                              String displayName,
                              List<String> lore,
                              String customItemId,
                              Integer fireworkPower,
                              FireworkEffect fireworkEffect) {
        this.id = id;
        this.material = material;
        this.amount = amount;
        this.cost = cost;
        this.behavior = behavior;
        this.teamColor = teamColor;
        this.tier = tier;
        this.enchantments = enchantments;
        this.potionEffects = potionEffects;
        this.displayName = displayName;
        this.lore = lore;
        this.customItemId = customItemId;
        this.fireworkPower = fireworkPower;
        this.fireworkEffect = fireworkEffect;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public ShopCost getCost() {
        return cost;
    }

    public ShopItemBehavior getBehavior() {
        return behavior;
    }

    public boolean isTeamColor() {
        return teamColor;
    }

    public int getTier() {
        return tier;
    }

    public String getCustomItemId() {
        return customItemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ItemStack createDisplayItem(TeamColor team) {
        return createItem(team, true);
    }

    public ItemStack createPurchaseItem(TeamColor team) {
        return createItem(team, false);
    }

    private ItemStack createItem(TeamColor team, boolean includeCost) {
        Material resolved = resolveMaterial(team);
        ItemStack stack = new ItemStack(resolved, amount);
        ItemMeta meta = stack.getItemMeta();

        if (displayName != null && !displayName.isBlank()) {
            meta.displayName(Component.text(displayName, NamedTextColor.WHITE));
        }

        if (!enchantments.isEmpty()) {
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
            if (includeCost) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }

        if (!potionEffects.isEmpty() && meta instanceof PotionMeta potionMeta) {
            for (PotionEffect effect : potionEffects) {
                potionMeta.addCustomEffect(effect, true);
            }
            meta = potionMeta;
        }
        if (meta instanceof FireworkMeta fireworkMeta) {
            if (fireworkPower != null) {
                fireworkMeta.setPower(Math.max(0, fireworkPower));
            }
            if (fireworkEffect != null) {
                fireworkMeta.clearEffects();
                fireworkMeta.addEffect(fireworkEffect);
            }
            meta = fireworkMeta;
        }

        List<Component> lines = new ArrayList<>();
        if (lore != null) {
            for (String line : lore) {
                lines.add(Component.text(line));
            }
        }
        if (includeCost && cost != null && cost.isValid()) {
            Component costLine = formatCost();
            if (costLine != null) {
                lines.add(costLine);
            }
        }
        if (!lines.isEmpty()) {
            meta.lore(lines);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        CustomItemData.apply(meta, customItemId);
        stack.setItemMeta(meta);
        return stack;
    }

    private Material resolveMaterial(TeamColor team) {
        if (!teamColor || team == null) {
            return material;
        }
        if (material.name().endsWith("_WOOL")) {
            return team.wool();
        }
        return material;
    }

    private Component formatCost() {
        if (cost == null || !cost.isValid()) {
            return null;
        }
        NamedTextColor color = switch (cost.material()) {
            case IRON_INGOT -> NamedTextColor.GRAY;
            case GOLD_INGOT -> NamedTextColor.GOLD;
            case DIAMOND -> NamedTextColor.AQUA;
            case EMERALD -> NamedTextColor.GREEN;
            default -> NamedTextColor.GRAY;
        };
        String name = switch (cost.material()) {
            case IRON_INGOT -> "Iron";
            case GOLD_INGOT -> "Gold";
            case DIAMOND -> "Diamond";
            case EMERALD -> "Emerald";
            default -> cost.material().name();
        };
        return Component.text("Cost: " + cost.amount() + " " + name, color);
    }
}
