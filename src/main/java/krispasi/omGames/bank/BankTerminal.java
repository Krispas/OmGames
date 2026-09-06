package krispasi.omGames.bank;

import java.util.UUID;

public record BankTerminal(String terminalId, String accountId, UUID ownerId, String ownerName, String name, long createdAt) {
}
