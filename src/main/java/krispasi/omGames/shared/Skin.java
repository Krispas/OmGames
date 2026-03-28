package krispasi.omGames.shared;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

public class Skin {
    private final Component name;
    private final List<String> loreList;
    private final SKIN_TYPE type;

    public Skin(Component name, List<String> loreList, SKIN_TYPE type) {
        this.name = name;
        this.loreList = loreList;
        this.type = type;
    }

    public Component name() {
        return name;
    }

    public List<String> loreList() {
        return loreList;
    }

    public SKIN_TYPE type() {
        return type;
    }

    public List<Component> getLore() {
        List<Component> lore = new ArrayList<>();
        loreList.forEach(s -> lore.add(Component.text(s)));
        return lore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Skin skin = (Skin) o;
        return java.util.Objects.equals(name, skin.name)
                && java.util.Objects.equals(loreList, skin.loreList)
                && type == skin.type;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, loreList, type);
    }

    @Override
    public String toString() {
        return "Skin[name=" + name + ", loreList=" + loreList + ", type=" + type + "]";
    }
}
