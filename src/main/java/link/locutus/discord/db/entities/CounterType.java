package link.locutus.discord.db.entities;

public enum CounterType {
    UNCONTESTED(""), // no counter or countered
    GETS_COUNTERED("This has been countered"), // this war is a raid
    IS_COUNTER("This is a counter"), // this war is a counter
    ESCALATION("This is an escalation") // this war is an escalation

    ;

    private final String desc;

    CounterType(String desc) {
        this.desc = desc;
    }

    public String getDescription() {
        return desc;
    }

    public static CounterType[] values = values();
}
