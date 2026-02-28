package link.locutus.discord.web.commands.binding.value_types;

/**
 * Metadata for a single virtual (temporary) conflict.
 * Returned as {@code List<VirtualConflictMeta>} by the virtualConflicts endpoint.
 */
public class VirtualConflictMeta {
    /** Nation id that owns this virtual conflict */
    public int nationId;
    /** Unique identifier for this virtual conflict */
    public String uuid;

    public VirtualConflictMeta() {
    }

    public VirtualConflictMeta(int nationId, String uuid) {
        this.nationId = nationId;
        this.uuid = uuid;
    }
}

