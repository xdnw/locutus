package link.locutus.discord.db.entities.announce;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class Announcement {
    public final int id;
    public final long sender;
    public final String title;
    public final String body;
    public final String replacements;
    public final String filter;
    public final long date;
    public boolean active;
    public boolean allowCreation;
    public AnnounceType type;

    public Announcement(int id, AnnounceType type, long sender, long date, String title, String body, String replacements, String filter, boolean active, boolean allowCreation) {
        this.id = id;
        this.type = type;
        this.date = date;
        this.sender = sender;
        this.title = title;
        this.body = body;
        this.replacements = replacements;
        this.filter = filter;
        this.active = active;
        this.allowCreation = allowCreation;
    }

    public Announcement(ResultSet rs) throws SQLException {
        id = rs.getInt("ann_id");
        type = AnnounceType.values[rs.getInt("type")];
        sender = rs.getLong("sender");
        active = rs.getBoolean("active");
        title = rs.getString("title");
        body = rs.getString("content");
        replacements = rs.getString("replacements");
        filter = rs.getString("filter");
        date = rs.getLong("date");
        allowCreation = rs.getBoolean("allow_creation");
    }

    public static List<String> getReplacements(String replacements) {
        return Arrays.asList(replacements.split("(?<!\\\\\\\\)\\\\n|\\\\\\\\n"));
    }

    public List<String> getReplacements() {
        return getReplacements(replacements);
    }

    public Predicate<DBNation> getFilter(GuildDB db) {
        NationPlaceholders placeholders = Locutus.cmd().getV2().getNationPlaceholders();
        LocalValueStore store = placeholders.createLocals(db.getGuild(), null, null);
        return placeholders.parseFilter(store, filter);
    }

    public static class PlayerAnnouncement {
        private final GuildDB db;
        private Announcement parent;
        private boolean parentInitialized = false;
        public int ann_id;
        public final byte[] diff;
        public final int receiverNation;
        public final boolean active;

        public PlayerAnnouncement(GuildDB db, Announcement parent, byte[] diff, int receiverNation, boolean active) {
            this.db = db;
            this.parent = parent;
            this.ann_id = parent.id;
            this.diff = diff;
            this.receiverNation = receiverNation;
            this.active = active;
        }

        public PlayerAnnouncement(GuildDB db, Announcement ann, ResultSet rs) throws SQLException {
            this.db = db;
            this.parent = ann;
            this.ann_id = rs.getInt("ann_id");
            this.receiverNation = rs.getInt("receiver");
            this.active = rs.getBoolean("active");
            this.diff = rs.getBytes("diff");
        }

        public PlayerAnnouncement(GuildDB db, ResultSet rs) throws SQLException {
            this.db = db;
            this.ann_id = rs.getInt("ann_id");
            this.receiverNation = rs.getInt("receiver");
            this.active = rs.getBoolean("active");
            this.diff = rs.getBytes("diff");
        }

        public void setParent(Announcement parent) {
            this.parent = parent;
        }

        public Announcement getParent() {
            if (parent == null && !parentInitialized) {
                parentInitialized = true;
                parent = db.getAnnouncement(ann_id);
            }
            return parent;
        }

        public boolean isActive() {
            return active && getParent().active;
        }

        public String getContent() {
            return StringMan.getDiffVariant(getParent().body, diff);
        }
    }
}
