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
    /** Last modified timestamp (epoch millis) from cloud object metadata */
    public long dateModified;

    public VirtualConflictMeta() {
    }

    public VirtualConflictMeta(int nationId, String uuid) {
        this(nationId, uuid, 0L);
    }

    public VirtualConflictMeta(int nationId, String uuid, long dateModified) {
        this.nationId = nationId;
        this.uuid = uuid;
        this.dateModified = dateModified;
    }
}

