package krispasi.omGames.bedwars.timecapsule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class TimeCapsuleSerialization {
    private TimeCapsuleSerialization() {
    }

    public static boolean hasAnyContents(ItemStack[] contents) {
        if (contents == null) {
            return false;
        }
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                return true;
            }
        }
        return false;
    }

    public static String serialize(ItemStack[] contents) throws IOException {
        if (contents == null) {
            return null;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeInt(contents.length);
            for (ItemStack item : contents) {
                output.writeObject(item);
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static ItemStack[] deserialize(String encoded, int size) throws IOException {
        int normalizedSize = Math.max(0, size);
        ItemStack[] contents = new ItemStack[normalizedSize];
        if (encoded == null || encoded.isBlank() || normalizedSize <= 0) {
            return contents;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Failed to decode time capsule contents.", ex);
        }
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            int storedSize = Math.max(0, input.readInt());
            for (int i = 0; i < storedSize; i++) {
                Object raw;
                try {
                    raw = input.readObject();
                } catch (ClassNotFoundException ex) {
                    throw new IOException("Failed to deserialize time capsule inventory.", ex);
                }
                if (i >= normalizedSize) {
                    continue;
                }
                if (raw instanceof ItemStack item && item.getType() != Material.AIR) {
                    contents[i] = item;
                }
            }
        }
        return contents;
    }
}
