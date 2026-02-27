package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class WebTreatyChanges {
    public final List<WebTreatyChange> treaty_changes;

    public WebTreatyChanges(List<WebTreatyChange> treaty_changes) {
        this.treaty_changes = treaty_changes;
    }

    public static class WebTreatyChange {
        public final long timestamp;
        public final String action;
        public final String treaty_type;
        public final int from_alliance_id;
        public final int to_alliance_id;
        public final int turns_remaining;

        public WebTreatyChange(long timestamp, String action, String treaty_type,
                               int from_alliance_id, int to_alliance_id, int turns_remaining) {
            this.timestamp = timestamp;
            this.action = action;
            this.treaty_type = treaty_type;
            this.from_alliance_id = from_alliance_id;
            this.to_alliance_id = to_alliance_id;
            this.turns_remaining = turns_remaining;
        }
    }
}

