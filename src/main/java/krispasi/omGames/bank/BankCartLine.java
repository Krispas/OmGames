package krispasi.omGames.bank;

import java.util.UUID;

public record BankCartLine(UUID playerId, String terminalId, String itemId, String displayName, long price, int amount) {
    public long lineTotal() {
        return price * amount;
    }
}
