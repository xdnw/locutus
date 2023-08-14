package link.locutus.discord.db;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static link.locutus.discord.util.MarkupUtil.markdownUrl;
import static link.locutus.discord.util.discord.DiscordUtil.getGuildName;
import static link.locutus.discord.util.discord.DiscordUtil.getMessageGuild;

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
                "date BIGINT NOT NULL," +
                "approved BOOLEAN NOT NULL)");

        db.executeStmt("CREATE TABLE IF NOT EXISTS REPORT_VOTES (report_id INT NOT NULL, " +
                "nation_id INT NOT NULL, " +
                "discord_id BIGINT NOT NULL, " +
                "vote INT NOT NULL, " +
                "comment VARCHAR NOT NULL, " +
                "date BIGINT NOT NULL, " +
                "PRIMARY KEY(report_id, nation_id))");
    }

    private void setValues(PreparedStatement stmt, Report report, boolean reportId) throws SQLException {
        ReportHeader header = new ReportHeader();
        header.setDefaultIndexes(reportId);
        if (reportId) {
            stmt.setInt(1, report.reportId);
        }
        stmt.setInt(header.nation_id, report.nationId);
        stmt.setLong(header.discord_id, report.discordId);
        stmt.setInt(header.report_type, report.type.ordinal());
        stmt.setInt(header.reporter_nation_id, report.reporterNationId);
        stmt.setLong(header.reporter_discord_id, report.reporterDiscordId);
        stmt.setInt(header.reporter_alliance_id, report.reporterAllianceId);
        stmt.setLong(header.reporter_guild_id, report.reporterGuildId);
        stmt.setString(header.report_message, report.message);
        stmt.setString(header.image_url, report.imageUrl);
        stmt.setString(header.forum_url, report.forumUrl);
        stmt.setString(header.news_url, report.newsUrl);
        stmt.setLong(header.date, report.date);
        stmt.setBoolean(header.approved, report.approved);
    }

    public void saveReport(Report report) {
        ReportHeader header = new ReportHeader();
        header.setDefaultIndexes(false);
        List<String> columns = new ArrayList<>(Arrays.asList(header.getHeaderNames()));

        if (report.reportId == -1) {
            columns.remove(0);
        }
        String query = "INSERT INTO REPORTS " +
                "(" + StringMan.join(columns, ", ") + ")" +
                " VALUES (" + StringMan.repeat("?, ", columns.size() - 1) + "?)";

        synchronized (db) {
            try (PreparedStatement stmt = db.getConnection().prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
                setValues(stmt, report, report.reportId != -1);

                stmt.executeUpdate();
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    report.reportId = rs.getInt(1);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveReports(List<Report> reports) {
        ReportHeader header = new ReportHeader();
        header.setDefaultIndexes(false);
        List<String> columns = new ArrayList<>(Arrays.asList(header.getHeaderNames()));
        columns.remove(0);
        String query = "INSERT INTO REPORTS " +
                "(" + StringMan.join(columns, ", ") + ")" +
                " VALUES (" + StringMan.repeat("?, ", columns.size() - 1) + "?)";
        db.executeBatch(reports, query, new ThrowingBiConsumer<Report, PreparedStatement>() {
            @Override
            public void acceptThrows(Report report, PreparedStatement stmt) throws Exception {
                setValues(stmt, report, false);
            }
        });
    }

    public enum ReportType {
        MULTI,
        REROLL,
        FRAUD,
        BANK_DEFAULT,
        COUPING,
        THREATS_COERCION,
        LEAKING,
        DEFAMATION,
        SPAMMING,
        IMPERSONATING,
        PHISHING,
        BEHAVIOR_OOC,

    }

    public static class Report {

        public int reportId;
        public int nationId;
        public long discordId;
        public ReportType type;
        public int reporterNationId;
        public long reporterDiscordId;
        public int reporterAllianceId;
        public long reporterGuildId;
        public String message;
        public String imageUrl;
        public String forumUrl;
        public String newsUrl;
        public long date;
        public boolean approved;

        public Report(int nationId,
                      long discordId,
                        ReportType reportType,
                        int reporterNationId,
                        long reporterDiscordId,
                        int reporterAllianceId,
                        long reporterGuildId,
                        String reportMessage,
                        String imageUrl,
                        String forumUrl,
                        String newsUrl,
                        long date,
                        boolean approved) {
            reportId = -1;
            this.nationId = nationId;
            this.discordId = discordId;
            this.type = reportType;
            this.reporterNationId = reporterNationId;
            this.reporterDiscordId = reporterDiscordId;
            this.reporterAllianceId = reporterAllianceId;
            this.reporterGuildId = reporterGuildId;
            this.message = reportMessage;
            this.imageUrl = imageUrl;
            this.forumUrl = forumUrl;
            this.newsUrl = newsUrl;
            this.date = date;
            this.approved = approved;
        }

        public Report(ReportHeader header, List<Object> row) {
            reportId = Integer.parseInt(row.get(header.report_id).toString());
            nationId = Integer.parseInt(row.get(header.nation_id).toString());
            discordId = Long.parseLong(row.get(header.discord_id).toString());
            type = ReportType.valueOf(row.get(header.report_type).toString());
            reporterNationId = Integer.parseInt(row.get(header.reporter_nation_id).toString());
            reporterDiscordId = Long.parseLong(row.get(header.reporter_discord_id).toString());
            reporterAllianceId = Integer.parseInt(row.get(header.reporter_alliance_id).toString());
            reporterGuildId = Long.parseLong(row.get(header.reporter_guild_id).toString());
            message = row.get(header.report_message).toString();
            imageUrl = row.get(header.image_url).toString();
            forumUrl = row.get(header.forum_url).toString();
            newsUrl = row.get(header.news_url).toString();
            date = Long.parseLong(row.get(header.date).toString());
            approved = Boolean.parseBoolean(row.get(header.approved).toString());
        }

        public Report(ReportHeader header, ResultSet rs) throws SQLException {
            reportId = rs.getInt(header.report_id);
            nationId = rs.getInt(header.nation_id);
            discordId = rs.getLong(header.discord_id);
            type = ReportType.valueOf(rs.getString(header.report_type));
            reporterNationId = rs.getInt(header.reporter_nation_id);
            reporterDiscordId = rs.getLong(header.reporter_discord_id);
            reporterAllianceId = rs.getInt(header.reporter_alliance_id);
            reporterGuildId = rs.getLong(header.reporter_guild_id);
            message = rs.getString(header.report_message);
            imageUrl = rs.getString(header.image_url);
            forumUrl = rs.getString(header.forum_url);
            newsUrl = rs.getString(header.news_url);
            date = rs.getLong(header.date);
            approved = rs.getBoolean(header.approved);
        }

        @Override
        public String toString() {
            return "Report{" +
                    "reportId=" + reportId +
                    ", nationId=" + nationId +
                    ", discordId=" + discordId +
                    ", reportType=" + type +
                    ", reporterNationId=" + reporterNationId +
                    ", reporterDiscordId=" + reporterDiscordId +
                    ", reporterAllianceId=" + reporterAllianceId +
                    ", reporterGuildId=" + reporterGuildId +
                    ", reportMessage='" + message + '\'' +
                    ", imageUrl='" + imageUrl + '\'' +
                    ", forumUrl='" + forumUrl + '\'' +
                    ", newsUrl='" + newsUrl + '\'' +
                    ", date=" + date +
                    ", approved=" + approved +
                    '}';
        }

        public boolean hasPermission(DBNation me, User author, GuildDB db) {
            if (me.getNation_id() == reporterNationId) return true;
            if (author.getIdLong() == reporterDiscordId) return true;
            if (db.getIdLong() == this.reporterDiscordId && Roles.ADMIN.has(author, db.getGuild())) {
                return true;
            }
            if (db.isAllianceId(this.reporterAllianceId) && Roles.ADMIN.has(author, db.getGuild())) {
                return true;
            }
            return Roles.INTERNAL_AFFAIRS_STAFF.hasOnRoot(author);
        }

        public String toMarkdown(boolean includeComments) {
            StringBuilder body = new StringBuilder("Report #" + reportId + " - " + type + "\n");
            if (nationId != 0) {
                body.append("Nation: " + PnwUtil.getMarkdownUrl(nationId, false) + "\n");
            }
            if (discordId != 0) {
                body.append("Discord: `" + DiscordUtil.getUserName(discordId) + "` | `" + discordId + "`\n");
            }
            String reporterNationLink = PnwUtil.getMarkdownUrl(reporterNationId, false);
            String reporterAllianceLink = PnwUtil.getMarkdownUrl(reporterAllianceId, true);
            String reporterGuildLink = markdownUrl(getGuildName(reporterGuildId), DiscordUtil.getGuildUrl(reporterGuildId));
            body.append("Reported by: " + reporterNationLink + " | " + reporterAllianceLink  + " | <@" + reporterDiscordId + "> | " + reporterGuildLink + "\n");
            body.append("```\n" + message + "\n```\n");
            if (imageUrl != null && !imageUrl.isEmpty()) {
                body.append("Image: " + imageUrl + "\n");
            }
            if (forumUrl != null && !forumUrl.isEmpty()) {
                // https://forum.politicsandwar.com/index.php?/topic
                String urlStub = forumUrl.replace("https://forum.politicsandwar.com/index.php?/topic/", "");
                String idAndName = urlStub.split("/")[0];
                body.append("Forum: " + MarkupUtil.markdownUrl(idAndName, forumUrl) + "\n");
            }
            if (newsUrl != null && !newsUrl.isEmpty()) {
                String newsMarkup = markdownUrl(getGuildName(getMessageGuild(newsUrl)), newsUrl);
                body.append("News: " + newsMarkup + "\n");
            }

            if (includeComments) {
                List<Vote> comments = Locutus.imp().getNationDB().getReportManager().loadVotesByReport(this.reportId);
                // append num comments
                if (!comments.isEmpty()) {
                    body.append(comments.size() + " comments, see: TODO CM ref\n");
                }
            }
            return body.toString();
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

        public int approved;

        public String[] getHeaderNames() {
            return new String[]{"report_id", "nation_id", "discord_id", "report_type", "reporter_nation_id", "reporter_discord_id", "reporter_alliance_id", "reporter_guild_id", "report_message", "image_url", "forum_url", "news_url", "date", "approved"};
        }
        public void setDefaultIndexes() {
            setDefaultIndexes(true);
        }
        public void setDefaultIndexes(boolean setReportId) {
            report_id = setReportId ? 1 : -1;
            nation_id = setReportId ? 2 : 1;
            discord_id = setReportId ? 3 : 2;
            report_type = setReportId ? 4 : 3;
            reporter_nation_id = setReportId ? 5 : 4;
            reporter_discord_id = setReportId ? 6 : 5;
            reporter_alliance_id = setReportId ? 7 : 6;
            reporter_guild_id = setReportId ? 8 : 7;
            report_message = setReportId ? 9 : 8;
            image_url = setReportId ? 10 : 9;
            forum_url = setReportId ? 11 : 10;
            news_url = setReportId ? 12 : 11;
            date = setReportId ? 13 : 12;
            approved = setReportId ? 14 : 13;
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

    public List<Report> loadReports() {
        return loadReports(null);
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

    public List<Report> loadReportsByNationOrUser(Integer nationIdOrNull, Long discordUserIdOrNull) {
        if (nationIdOrNull == null && discordUserIdOrNull == null) return Collections.emptyList();
        String whereClause = "";
        if (nationIdOrNull != null) {
            whereClause += "nation_id = " + nationIdOrNull;
        }
        if (discordUserIdOrNull != null) {
            if (!whereClause.isEmpty()) whereClause += " OR ";
            whereClause += "discord_id = " + discordUserIdOrNull;
        }
        return loadReports(whereClause);
    }

    public Report getReport(int reportId) {
        List<Report> reports = loadReports("report_id = " + reportId);
        if (reports.isEmpty()) return null;
        return reports.get(0);
    }

    public List<Vote> loadVotesByReport(int reportId) {
        return loadVotes("report_id = " + reportId);
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
