package link.locutus.discord.db;

public enum ReportType {
    MULTI("Suspected or confirmed multi boxing, where a user controls or accesses multiple nations simultaneously"),
    REROLL("Has created a new nation after their previous one has been reset, deleted, banned or forgotten"),
    FRAUD("Deceptive actions such as not paying for an agreed trade or service, investment frauds such as ponzi schemes, embezzlement of funds, selling worthless services, fake lotteries, auctions, charity appeals"),
    BANK_DEFAULT("When a player defaults on their financial commitments or obligations such as loans or trade agreements"),
    COUPING("A player attempting to overthrow or seize control of an alliance or bank"),
    THREATS_COERCION("Threatening action which violates the sovereignty of a nation or alliance unless demands are met. " +
            "This can include alliance disbandment, subjugation, forced merges, permanent war. " +
            "Reparations and peace negotiations for a war are not considered notable threats or coercion."),
    LEAKING("when a player is suspected of sharing sensitive or confidential in-game information with unauthorized individuals or groups. " +
            "This can include revealing military plans, alliance strategies, or private announcements."),
    DEFAMATION("When a player engages in false or damaging statements about another player or alliance with the intent to harm their reputation"),
    SPAMMING("when a player excessively and repeatedly posts the same or similar content in game or on a discord server. "),
    IMPERSONATING("When a player is pretending to be someone else, either by adopting their identity or by falsely claiming to represent another user, alliance or group."),
    PHISHING("When a player attempts to deceive others by creating fake pages, messages, or communications that appear legitimate but are designed to access sensitive information."),
    BEHAVIOR_OOC("This report is for out-of-character (OOC) actions such as racism, harassment, doxxing, and other behaviors that are not related to in-game actions but instead involve real-world misconduct."),

    ;

    public final String description;

    ReportType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
