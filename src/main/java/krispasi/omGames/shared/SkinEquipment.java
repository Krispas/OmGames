package krispasi.omGames.shared;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;

public class SkinEquipment extends Skin {
    private final String equipment;

    public SkinEquipment(Component name, List<String> loreList, SKIN_TYPE type, String equipment) {
        super(name, loreList, type);
        this.equipment = equipment;
    }

    public String equipment() {
        return equipment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        SkinEquipment that = (SkinEquipment) o;
        return Objects.equals(equipment, that.equipment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), equipment);
    }

    @Override
    public String toString() {
        return "SkinEquipment[" + "name=" + name() + ", loreList=" + loreList()
                + ", type=" + type() + ", equipment=" + equipment + "]";
    }
}
