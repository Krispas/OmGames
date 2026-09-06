package krispasi.omGames.bank;

import java.util.UUID;

public record BankCard(String cardId, UUID ownerId, String ownerName, boolean frozen, long createdAt) {
}
