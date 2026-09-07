package krispasi.omGames.bank;

public final class BankPromptSession {
    public enum Mode {
        CREATE_NON_PLAYER_ACCOUNT,
        TERMINAL_ITEM_NAME,
        TERMINAL_ITEM_PRICE
    }

    private final Mode mode;
    private final String terminalId;
    private final String itemId;

    private BankPromptSession(Mode mode, String terminalId, String itemId) {
        this.mode = mode;
        this.terminalId = terminalId;
        this.itemId = itemId;
    }

    public static BankPromptSession createNonPlayerAccount() {
        return new BankPromptSession(Mode.CREATE_NON_PLAYER_ACCOUNT, null, null);
    }

    public static BankPromptSession terminalItemName(String terminalId, String itemId) {
        return new BankPromptSession(Mode.TERMINAL_ITEM_NAME, terminalId, itemId);
    }

    public static BankPromptSession terminalItemPrice(String terminalId, String itemId) {
        return new BankPromptSession(Mode.TERMINAL_ITEM_PRICE, terminalId, itemId);
    }

    public Mode mode() {
        return mode;
    }

    public String terminalId() {
        return terminalId;
    }

    public String itemId() {
        return itemId;
    }
}
