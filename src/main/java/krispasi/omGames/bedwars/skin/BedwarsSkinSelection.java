package krispasi.omGames.bedwars.skin;

public record BedwarsSkinSelection(String modelId, String equipmentModelId) {
    public boolean hasEquipmentModel() {
        return equipmentModelId != null && !equipmentModelId.isBlank();
    }
}
