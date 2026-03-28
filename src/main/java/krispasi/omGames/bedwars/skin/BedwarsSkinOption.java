package krispasi.omGames.bedwars.skin;

import krispasi.omGames.shared.Skin;

public record BedwarsSkinOption(String modelId, Skin skin, String equipmentModelId) {
    public boolean hasEquipmentModel() {
        return equipmentModelId != null && !equipmentModelId.isBlank();
    }
}
