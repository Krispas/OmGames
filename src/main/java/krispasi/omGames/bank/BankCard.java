package krispasi.omGames.bank;

import java.util.UUID;

public record BankCard(String cardId, String accountId, UUID ownerId, String ownerName, boolean frozen, long createdAt) {
}
