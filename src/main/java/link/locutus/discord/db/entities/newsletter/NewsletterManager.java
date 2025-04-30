package link.locutus.discord.db.entities.newsletter;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NewsletterManager {
    public static boolean isAllowed(long idLong) {
        return idLong == 821587932384067584L || idLong == 672217848311054346L;
    }

    private final GuildDB db;
    private final ConcurrentHashMap<Integer, Newsletter> newsletters;

    public NewsletterManager(GuildDB db) {
        this.db = db;
        this.createTables();
        this.newsletters = new ConcurrentHashMap<>(getNewsletters());
    }

    private void createTables() {
        String createNewsletters = "CREATE TABLE NEWSLETTERS (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, date_created BIGINT NOT NULL, last_sent BIGINT NOT NULL, send_interval BIGINT NOT NULL, sendConfirmationChannel BIGINT, pingRole BIGINT)";
        // make (newsletter, channel_id combined primary)
        String createChannels = "CREATE TABLE NEWSLETTER_CHANNELS (newsletter INTEGER NOT NULL, channel_id BIGINT NOT NULL, date_created BIGINT NOT NULL, FOREIGN KEY(newsletter) REFERENCES NEWSLETTERS(id), PRIMARY KEY(newsletter, channel_id))";
        // newsletter id, nation id, long date
        String subscriptions = "CREATE TABLE NEWSLETTER_SUBSCRIPTIONS (newsletter INTEGER NOT NULL, nation_id INTEGER NOT NULL, date_created BIGINT NOT NULL, FOREIGN KEY(newsletter) REFERENCES NEWSLETTERS(id), PRIMARY KEY(newsletter, nation_id))";

        db.executeStmt(createNewsletters);
        db.executeStmt(createChannels);
        db.executeStmt(subscriptions);
    }

    public Map<Integer, Newsletter> getNewsletters() {
        Map<Integer, Newsletter> map = new LinkedHashMap<>();
        db.query("SELECT * FROM NEWSLETTERS", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                long dateCreated = rs.getLong("date_created");
                long lastSent = rs.getLong("last_sent");
                long sendInterval = rs.getLong("send_interval");
                long sendConfirmationChannel = rs.getLong("sendConfirmationChannel");
                long pingRole = rs.getLong("pingRole");
                Newsletter newsletter = new Newsletter(id, name, lastSent, dateCreated, sendInterval, sendConfirmationChannel, pingRole);
                map.put(id, newsletter);
            }
        });

        // get channels
        db.query("SELECT * FROM NEWSLETTER_CHANNELS", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int newsletterId = rs.getInt("newsletter");
                long channelId = rs.getLong("channel_id");
                Newsletter newsletter = map.get(newsletterId);
                if (newsletter != null) {
                    newsletter.addChannelId(channelId);
                }
            }
        });

        return map;
    }

    public Set<Newsletter> getSubscriptions(int nationId) {
        Set<Newsletter> set = new ObjectOpenHashSet<>();
        db.query("SELECT * FROM NEWSLETTER_SUBSCRIPTIONS WHERE nation_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int newsletterId = rs.getInt("newsletter");
                Newsletter newsletter = newsletters.get(newsletterId);
                if (newsletter != null) {
                    set.add(newsletter);
                }
            }
        });
        return set;
    }

    public Set<Integer> getSubscribedNations(int newsletterId) {
        Set<Integer> set = new IntOpenHashSet();
        db.query("SELECT * FROM NEWSLETTER_SUBSCRIPTIONS WHERE newsletter = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, newsletterId);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int nationId = rs.getInt("nation_id");
                set.add(nationId);
            }
        });
        return set;
    }

    public void subscribe(int nationId, int newsletterId) {
        db.executeStmt("INSERT OR IGNORE INTO NEWSLETTER_SUBSCRIPTIONS (newsletter, nation_id, date_created) VALUES (?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, newsletterId);
            stmt.setInt(2, nationId);
            stmt.setLong(3, System.currentTimeMillis());
        });
    }

    public void unsubscribe(int nationId, int newsletterId) {
        db.executeStmt("DELETE FROM NEWSLETTER_SUBSCRIPTIONS WHERE newsletter = ? AND nation_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, newsletterId);
            stmt.setInt(2, nationId);
        });
    }

    public void unsubscribeAllNation(int nationId) {
        db.executeStmt("DELETE FROM NEWSLETTER_SUBSCRIPTIONS WHERE nation_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
        });
    }

    public void unsubscribeAll(int newsletterId) {
        db.executeStmt("DELETE FROM NEWSLETTER_SUBSCRIPTIONS WHERE newsletter = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, newsletterId);
        });
    }

    public void addNewsletter(Newsletter newsletter) {
        String query = "INSERT INTO NEWSLETTERS (name, date_created, last_sent, send_interval, sendConfirmationChannel, pingRole) VALUES (?, ?, ?, ?, ?, ?)";

        synchronized (db) {
            try (PreparedStatement stmt = db.getConnection().prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, newsletter.getName());
                stmt.setLong(2, newsletter.getDateCreated());
                stmt.setLong(3, newsletter.getLastSent());
                stmt.setLong(4, newsletter.getSendInterval());
                stmt.setLong(5, newsletter.getSendConfirmationChannel());
                stmt.setLong(6, newsletter.getPingRole());

                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    newsletter.setId(rs.getInt(1));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateNewsletter(Collection<Newsletter> newsletters) {
        db.executeBatch(newsletters, "INSERT OR REPLACE INTO NEWSLETTERS (id, name, last_sent, date_created, send_interval, sendConfirmationChannel, pingRole) VALUES (?, ?, ?, ?, ?, ?, ?)",
            (ThrowingBiConsumer<Newsletter, PreparedStatement>) (nl, stmt) -> {
                stmt.setInt(1, nl.getId());
                stmt.setString(2, nl.getName());
                stmt.setLong(3, nl.getDateCreated());
                stmt.setLong(4, nl.getLastSent());
                stmt.setLong(5, nl.getSendInterval());
                stmt.setLong(6, nl.getSendConfirmationChannel());
                stmt.setLong(7, nl.getPingRole());
        });
    }

    public void addChannel(int newsLetterId, long channelId) {
        db.executeStmt("INSERT OR IGNORE INTO NEWSLETTER_CHANNELS (newsletter, channel_id, date_created) VALUES (?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, newsLetterId);
            stmt.setLong(2, channelId);
            stmt.setLong(3, System.currentTimeMillis());
        });
    }

    public void removeChannel(int newsLetterId, long channelId) {
        db.executeStmt("DELETE FROM NEWSLETTER_CHANNELS WHERE newsletter = ? AND channel_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, newsLetterId);
            stmt.setLong(2, channelId);
        });
    }

    public void delete(int newsLetterId) {
        db.executeStmt("DELETE FROM NEWSLETTERS WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, newsLetterId);
        });
        db.executeStmt("DELETE FROM NEWSLETTER_CHANNELS WHERE newsletter = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, newsLetterId);
        });
        unsubscribeAll(newsLetterId);
    }

    public Newsletter getNewsletter(String name) {
        for (Newsletter newsletter : newsletters.values()) {
            if (newsletter.getName().equalsIgnoreCase(name)) {
                return newsletter;
            }
        }
        return null;
    }
}
