package krispasi.omGames.bank;

import java.util.UUID;

public record BankAccountEditor(String accountId, UUID playerId, String playerName) {
}
