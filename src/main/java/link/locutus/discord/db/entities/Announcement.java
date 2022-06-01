package link.locutus.discord.db.entities;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.StringMan;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Announcement {
    public final int id;
    public final long sender;
    public final String title;
    public final String body;
    public final String replacements;
    public final String filter;
    public final long date;
    public boolean active;

    public Announcement(int id, long sender, long date, String title, String body, String replacements, String filter, boolean active) {
        this.id = id;
        this.date = date;
        this.sender = sender;
        this.title = title;
        this.body = body;
        this.replacements = replacements;
        this.filter = filter;
        this.active = active;
    }

    public Announcement(ResultSet rs) throws SQLException {
        id = rs.getInt("ann_id");
        sender = rs.getLong("sender");
        active = rs.getBoolean("active");
        title = rs.getString("title");
        body = rs.getString("content");
        replacements = rs.getString("replacements");
        filter = rs.getString("filter");
        date = rs.getLong("date");
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
