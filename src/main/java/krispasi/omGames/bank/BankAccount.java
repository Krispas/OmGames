package krispasi.omGames.bank;

import java.util.UUID;

public record BankAccount(UUID playerId, String playerName, long balance, long createdAt) {
}
