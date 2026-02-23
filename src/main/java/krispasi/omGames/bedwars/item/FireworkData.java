package krispasi.omGames.bedwars.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persistent data helper for firework explosion metadata.
 * <p>Stores explosion power and damage on {@link org.bukkit.inventory.meta.ItemMeta} to
 * drive custom firework blasts.</p>
 * @see krispasi.omGames.bedwars.listener.BedwarsListener
 */
public final class FireworkData {
    private static final NamespacedKey EXPLOSION_POWER_KEY =
            new NamespacedKey(JavaPlugin.getProvidingPlugin(FireworkData.class), "firework_explosion_power");
    private static final NamespacedKey EXPLOSION_DAMAGE_KEY =
            new NamespacedKey(JavaPlugin.getProvidingPlugin(FireworkData.class), "firework_explosion_damage");
    private static final NamespacedKey EXPLOSION_KNOCKBACK_KEY =
            new NamespacedKey(JavaPlugin.getProvidingPlugin(FireworkData.class), "firework_explosion_knockback");

    private FireworkData() {
    }

    public static void apply(ItemMeta meta, Double power, Double damage, Double knockback) {
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (power != null) {
            container.set(EXPLOSION_POWER_KEY, PersistentDataType.DOUBLE, power);
        }
        if (damage != null) {
            container.set(EXPLOSION_DAMAGE_KEY, PersistentDataType.DOUBLE, damage);
        }
        if (knockback != null) {
            container.set(EXPLOSION_KNOCKBACK_KEY, PersistentDataType.DOUBLE, knockback);
        }
    }

    public static Double getExplosionPower(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(EXPLOSION_POWER_KEY, PersistentDataType.DOUBLE);
    }

    public static Double getExplosionDamage(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(EXPLOSION_DAMAGE_KEY, PersistentDataType.DOUBLE);
    }

    public static Double getExplosionKnockback(ItemMeta meta) {
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(EXPLOSION_KNOCKBACK_KEY, PersistentDataType.DOUBLE);
    }
}
