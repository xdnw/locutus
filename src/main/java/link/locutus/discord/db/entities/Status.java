package link.locutus.discord.db.entities;

public enum Status {
    OPEN("\uD83D\uDEAA"), // 1f6aa
    CLOSED("\uD83D\uDD10"), // U+1f510
    EXTENDED("\u21aa\ufe0f"), // U+21aaU+fe0f
    MISSED_PAYMENT("\u23f1\ufe0f"),
    DEFAULTED("\u274c"),
    ;
    public final String emoji;

    Status(String s) {
        this.emoji = s;
    }
}
