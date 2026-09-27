package krispasi.omGames.hallsofcarnage;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

final class HallsDamageSupport {
    private HallsDamageSupport() {
    }

    static double applyArmor(Player player, double damage) {
        if (player == null || damage <= 0.0) {
            return Math.max(0.0, damage);
        }
        double armor = attributeValue(player, Attribute.ARMOR);
        double toughness = attributeValue(player, Attribute.ARMOR_TOUGHNESS);
        if (armor <= 0.0) {
            return damage;
        }
        double armorPoints = Math.min(20.0, Math.max(armor / 5.0, armor - damage / (2.0 + toughness / 4.0)));
        return damage * (1.0 - armorPoints / 25.0);
    }

    private static double attributeValue(Player player, Attribute attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? 0.0 : Math.max(0.0, instance.getValue());
    }
}
