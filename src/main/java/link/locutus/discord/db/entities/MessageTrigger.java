package link.locutus.discord.db.entities;

public enum MessageTrigger {
    CREATION("When a nation is created\n" +
            "Messages will not send to nations that are inactive, or created before the message was created"),
    MEMBER_DEPARTURE("When an applicant or member (but not officer) leaves an alliance\n" +
            "Messages will not send if another message has been sent recently, if the nation is inactive, or it was created recently"),
    GRAVEYARD_ACTIVE("When a nation not part of a functioning alliance becomes active\n" +
            "Nations must be inactive for at least the time specified to be sent the custom message" +
            "Messages will not send if another message has been sent recently");

    private final String desc;

    MessageTrigger(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
