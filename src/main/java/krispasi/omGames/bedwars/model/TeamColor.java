package krispasi.omGames.bedwars.model;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;

public enum TeamColor {
    RED("red", "Red", NamedTextColor.RED, DyeColor.RED, Material.RED_WOOL, Material.RED_BED),
    BLUE("blue", "Blue", NamedTextColor.BLUE, DyeColor.BLUE, Material.BLUE_WOOL, Material.BLUE_BED),
    LIME("lime", "Lime", NamedTextColor.GREEN, DyeColor.LIME, Material.LIME_WOOL, Material.LIME_BED),
    WHITE("white", "White", NamedTextColor.WHITE, DyeColor.WHITE, Material.WHITE_WOOL, Material.WHITE_BED),
    BLACK("black", "Black", NamedTextColor.BLACK, DyeColor.BLACK, Material.BLACK_WOOL, Material.BLACK_BED),
    PURPLE("purple", "Purple", NamedTextColor.DARK_PURPLE, DyeColor.PURPLE, Material.PURPLE_WOOL, Material.PURPLE_BED),
    CYAN("cyan", "Cyan", NamedTextColor.AQUA, DyeColor.CYAN, Material.CYAN_WOOL, Material.CYAN_BED),
    PINK("pink", "Pink", NamedTextColor.LIGHT_PURPLE, DyeColor.PINK, Material.PINK_WOOL, Material.PINK_BED);

    private static final List<TeamColor> ORDERED = List.of(
            RED, BLUE, LIME, WHITE, BLACK, PURPLE, CYAN, PINK
    );

    private final String key;
    private final String displayName;
    private final NamedTextColor textColor;
    private final DyeColor dyeColor;
    private final Material wool;
    private final Material bed;

    TeamColor(String key, String displayName, NamedTextColor textColor, DyeColor dyeColor, Material wool, Material bed) {
        this.key = key;
        this.displayName = displayName;
        this.textColor = textColor;
        this.dyeColor = dyeColor;
        this.wool = wool;
        this.bed = bed;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor textColor() {
        return textColor;
    }

    public DyeColor dyeColor() {
        return dyeColor;
    }

    public Material wool() {
        return wool;
    }

    public Material bed() {
        return bed;
    }

    public Component displayComponent() {
        return Component.text(displayName, textColor);
    }

    public ChatColor chatColor() {
        return switch (this) {
            case RED -> ChatColor.RED;
            case BLUE -> ChatColor.BLUE;
            case LIME -> ChatColor.GREEN;
            case WHITE -> ChatColor.WHITE;
            case BLACK -> ChatColor.DARK_GRAY;
            case PURPLE -> ChatColor.DARK_PURPLE;
            case CYAN -> ChatColor.AQUA;
            case PINK -> ChatColor.LIGHT_PURPLE;
        };
    }

    public String shortName() {
        return switch (this) {
            case BLACK -> "K";
            default -> displayName.substring(0, 1);
        };
    }

    public static List<TeamColor> ordered() {
        return ORDERED;
    }

    public static TeamColor fromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (TeamColor color : values()) {
            if (color.key.equals(normalized)) {
                return color;
            }
        }
        return null;
    }
}
