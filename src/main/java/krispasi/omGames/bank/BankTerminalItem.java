package krispasi.omGames.bank;

import org.bukkit.Material;

public record BankTerminalItem(
        String itemId,
        String terminalId,
        String displayName,
        long price,
        int sortOrder,
        Material iconMaterial,
        String clickWorld,
        Integer clickX,
        Integer clickY,
        Integer clickZ
) {
    public boolean hasClickLocation() {
        return clickWorld != null && !clickWorld.isBlank()
                && clickX != null
                && clickY != null
                && clickZ != null;
    }

    public String clickLocationText() {
        if (!hasClickLocation()) {
            return "not set";
        }
        return clickWorld + " " + clickX + " " + clickY + " " + clickZ;
    }
}
