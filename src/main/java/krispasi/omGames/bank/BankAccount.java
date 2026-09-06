package krispasi.omGames.bank;

import java.util.UUID;

public record BankAccount(String accountId, UUID playerId, String displayName, boolean playerAccount, long balance, long createdAt) {
}
