package link.locutus.discord.db.entities.metric;

public enum MembershipChangeReason {
    RECRUITED, JOINED, LEFT, DELETED, VM_LEFT, VM_RETURNED,
    UNCHANGED,

    BOUGHT, SOLD;

    public boolean previouslyMember() {
        return switch (this) {
            case LEFT, VM_LEFT, DELETED, UNCHANGED -> true;
            default -> false;
        };
    }

    public boolean afterwardsMember() {
        return switch (this) {
            case JOINED, RECRUITED, VM_RETURNED, UNCHANGED -> true;
            default -> false;
        };
    }
}
