package link.locutus.discord.db;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReportManager {
    private final NationDB db;

    public ReportManager(NationDB db) {
        this.db = db;
        db.executeStmt("CREATE TABLE IF NOT EXISTS REPORTS (" +
                "report_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nation_id INT NOT NULL, " +
                "discord_id BIGINT NOT NULL, " +
                "report_type INT NOT NULL, " +
                "reporter_nation_id INT NOT NULL, " +
                "reporter_discord_id BIGINT NOT NULL, " +
                "reporter_alliance_id INT NOT NULL, " +
                "reporter_guild_id BIGINT NOT NULL, " +
                "report_message VARCHAR NOT NULL, " +
                "image_url VARCHAR NOT NULL, " +
                "forum_url VARCHAR NOT NULL, " +
                "news_url VARCHAR NOT NULL, " +
                "date BIGINT NOT NULL)");

        db.executeStmt("CREATE TABLE IF NOT EXISTS REPORT_VOTES (report_id INT NOT NULL, " +
                "nation_id INT NOT NULL, " +
                "discord_id BIGINT NOT NULL, " +
                "vote INT NOT NULL, " +
                "comment VARCHAR NOT NULL, " +
                "date BIGINT NOT NULL, " +
                "PRIMARY KEY(report_id, nation_id))");
    }

    public enum ReportType {
        MULTI,
        REROLL,
        STEALING,
        DEFAULT,
        LEAKING,
        BEHAVIOR,
        FA_BLUNDER

    }

    public static class Report {

        public int reportId;
        public int nationId;
        public long discordId;
        public ReportType reportType;
        public int reporterNationId;
        public long reporterDiscordId;
        public int reporterAllianceId;
        public long reporterGuildId;
        public String reportMessage;
        public String imageUrl;
        public String forumUrl;
        public String newsUrl;
        public long date;

        public Report(ReportHeader header, List<Object> row) {
            reportId = Integer.parseInt(row.get(header.report_id).toString());
            nationId = Integer.parseInt(row.get(header.nation_id).toString());
            discordId = Long.parseLong(row.get(header.discord_id).toString());
            reportType = ReportType.valueOf(row.get(header.report_type).toString());
            reporterNationId = Integer.parseInt(row.get(header.reporter_nation_id).toString());
            reporterDiscordId = Long.parseLong(row.get(header.reporter_discord_id).toString());
            reporterAllianceId = Integer.parseInt(row.get(header.reporter_alliance_id).toString());
            reporterGuildId = Long.parseLong(row.get(header.reporter_guild_id).toString());
            reportMessage = row.get(header.report_message).toString();
            imageUrl = row.get(header.image_url).toString();
            forumUrl = row.get(header.forum_url).toString();
            newsUrl = row.get(header.news_url).toString();
            date = Long.parseLong(row.get(header.date).toString());
        }

        public Report(ReportHeader header, ResultSet rs) throws SQLException {
            reportId = rs.getInt(header.report_id);
            nationId = rs.getInt(header.nation_id);
            discordId = rs.getLong(header.discord_id);
            reportType = ReportType.valueOf(rs.getString(header.report_type));
            reporterNationId = rs.getInt(header.reporter_nation_id);
            reporterDiscordId = rs.getLong(header.reporter_discord_id);
            reporterAllianceId = rs.getInt(header.reporter_alliance_id);
            reporterGuildId = rs.getLong(header.reporter_guild_id);
            reportMessage = rs.getString(header.report_message);
            imageUrl = rs.getString(header.image_url);
            forumUrl = rs.getString(header.forum_url);
            newsUrl = rs.getString(header.news_url);
            date = rs.getLong(header.date);
        }

        @Override
        public String toString() {
            return "Report{" +
                    "reportId=" + reportId +
                    ", nationId=" + nationId +
                    ", discordId=" + discordId +
                    ", reportType=" + reportType +
                    ", reporterNationId=" + reporterNationId +
                    ", reporterDiscordId=" + reporterDiscordId +
                    ", reporterAllianceId=" + reporterAllianceId +
                    ", reporterGuildId=" + reporterGuildId +
                    ", reportMessage='" + reportMessage + '\'' +
                    ", imageUrl='" + imageUrl + '\'' +
                    ", forumUrl='" + forumUrl + '\'' +
                    ", newsUrl='" + newsUrl + '\'' +
                    ", date=" + date +
                    '}';
        }
    }

    public static class ReportHeader {
        public int report_id;
        public int nation_id;
        public int discord_id;
        public int report_type;
        public int reporter_nation_id;
        public int reporter_discord_id;
        public int reporter_alliance_id;
        public int reporter_guild_id;
        public int report_message;
        public int image_url;
        public int forum_url;
        public int news_url;

        public int date;

        public String[] getHeaderNames() {
            return new String[]{"report_id", "nation_id", "discord_id", "report_type", "reporter_nation_id", "reporter_discord_id", "reporter_alliance_id", "reporter_guild_id", "report_message", "image_url", "forum_url", "news_url", "date"};
        }

        public void setDefaultIndexes() {
            report_id = 1;
            nation_id = 2;
            discord_id = 3;
            report_type = 4;
            reporter_nation_id = 5;
            reporter_discord_id = 6;
            reporter_alliance_id = 7;
            reporter_guild_id = 8;
            report_message = 9;
            image_url = 10;
            forum_url = 11;
            news_url = 12;
            date = 13;
        }
    }

    public static class VoteHeader {
        public int report_id;
        public int nation_id;
        public int discord_id;
        public int vote;
        public int comment;
        public int date;

        public String[] getHeaderNames() {
            return new String[]{"report_id", "nation_id", "discord_id", "vote", "comment", "date"};
        }

        public void setDefaultIndexes() {
            report_id = 1;
            nation_id = 2;
            discord_id = 3;
            vote = 4;
            comment = 5;
            date = 6;
        }
    }

    public static class Vote {
        public int report_id;
        public int nationId;
        public long discordId;
        public int vote;
        public String comment;
        public long date;

        public Vote(VoteHeader header, List<Object> row) {
            report_id = Integer.parseInt(row.get(header.report_id).toString());
            nationId = Integer.parseInt(row.get(header.nation_id).toString());
            discordId = Long.parseLong(row.get(header.discord_id).toString());
            vote = Integer.parseInt(row.get(header.vote).toString());
            comment = row.get(header.comment).toString();
            date = Long.parseLong(row.get(header.date).toString());
        }

        public Vote(VoteHeader header, ResultSet rs) throws SQLException {
            report_id = rs.getInt(header.report_id);
            nationId = rs.getInt(header.nation_id);
            discordId = rs.getLong(header.discord_id);
            vote = rs.getInt(header.vote);
            comment = rs.getString(header.comment);
            date = rs.getLong(header.date);
        }
    }


    public List<Report> getReports(SpreadSheet sheet) throws NoSuchFieldException, IllegalAccessException, GeneralSecurityException, IOException {
        List<Report> result = new ArrayList<>();
        List<List<Object>> values = sheet.loadValues();

        if (values.isEmpty()) return result;

        List<Object> headerRow = values.get(0);
        if (headerRow.isEmpty()) return result;

        ReportHeader header = sheet.loadHeader(new ReportHeader(), headerRow);
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row.isEmpty()) continue;

            Report report = new Report(header, row);
            result.add(report);
        }

        return result;
    }

    public List<Vote> getVotes(SpreadSheet sheet) throws GeneralSecurityException, IOException, NoSuchFieldException, IllegalAccessException {
        List<Vote> result = new ArrayList<>();
        List<List<Object>> values = sheet.loadValues();

        if (values.isEmpty()) return result;

        List<Object> headerRow = values.get(0);
        if (headerRow.isEmpty()) return result;

        VoteHeader header = sheet.loadHeader(new VoteHeader(), headerRow);
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row.isEmpty()) continue;

            Vote report = new Vote(header, row);
            result.add(report);
        }

        return result;
    }

    public List<Report> loadReports(String whereClauseOrNull) {
        if (whereClauseOrNull == null) {
            whereClauseOrNull = "";
        } else {
            whereClauseOrNull = " WHERE " + whereClauseOrNull;
        }
        List<Report> reports = new ObjectArrayList<>();

        ReportHeader header = new ReportHeader();
        header.setDefaultIndexes();
        String select = "SELECT " + StringMan.join(header.getHeaderNames(), ",") +  " FROM REPORTS" + whereClauseOrNull;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(select)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                reports.add(new Report(header, rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return reports;
    }

    public List<Vote> loadVotes(String whereClauseOrNull) {
        if (whereClauseOrNull == null) {
            whereClauseOrNull = "";
        } else {
            whereClauseOrNull = " WHERE " + whereClauseOrNull;
        }
        List<Vote> votes = new ObjectArrayList<>();

        VoteHeader header = new VoteHeader();
        header.setDefaultIndexes();
        String select = "SELECT " + StringMan.join(header.getHeaderNames(), ",") +  " FROM REPORT_VOTES" + whereClauseOrNull;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(select)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                votes.add(new Vote(header, rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return votes;
    }
}
