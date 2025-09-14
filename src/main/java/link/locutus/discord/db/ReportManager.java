package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.AllianceChange;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.sheet.SpreadSheet;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

        db.executeStmt("CREATE TABLE IF NOT EXISTS REPORT_COMMENTS (report_id INT NOT NULL, " +
                "nation_id INT NOT NULL, " +
                "discord_id BIGINT NOT NULL, " +
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
        stmt.setString(header.image_url, StringMan.join(report.imageUrls, "\n"));
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

    public void saveComment(Comment comment) {
        CommentHeader header = new CommentHeader();
        header.setDefaultIndexes();
        List<String> columns = new ArrayList<>(Arrays.asList(header.getHeaderNames()));
        String query = "INSERT OR REPLACE INTO REPORT_COMMENTS " +
                "(" + StringMan.join(columns, ", ") + ")" +
                " VALUES (" + StringMan.repeat("?, ", columns.size() - 1) + "?)";
        synchronized (db) {
            try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
                stmt.setInt(header.report_id, comment.report_id);
                stmt.setInt(header.nation_id, comment.nationId);
                stmt.setLong(header.discord_id, comment.discordId);
                stmt.setString(header.comment, comment.comment);
                stmt.setLong(header.date, comment.date);

                stmt.executeUpdate();
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

    public void deleteReport(int reportId) {
        db.update("DELETE FROM REPORTS WHERE report_id = ?", reportId);
    }

    public List<Report> loadReports(Integer nationIdReported, Long userIdReported, Integer reportingNation, Long reportingUser) {
        // if all null throw error
        if (nationIdReported == null && userIdReported == null && reportingNation == null && reportingUser == null) {
            throw new IllegalArgumentException("At least one of the parameters must be provided");
        }
        // May be null

        // Use where and clause for sql
        String whereClause = "";
        if (nationIdReported != null) {
            whereClause += "nation_id = " + nationIdReported;
        }
        if (userIdReported != null) {
            if (!whereClause.isEmpty()) {
                whereClause += " AND ";
            }
            whereClause += "discord_id = " + userIdReported;
        }
        if (reportingNation != null) {
            if (!whereClause.isEmpty()) {
                whereClause += " AND ";
            }
            whereClause += "reporter_nation_id = " + reportingNation;
        }
        if (reportingUser != null) {
            if (!whereClause.isEmpty()) {
                whereClause += " AND ";
            }
            whereClause += "reporter_discord_id = " + reportingUser;
        }
        return loadReports(whereClause);
    }

    public void setBanned(DBNation nation, long timestamp, String reason) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeLong(timestamp);
        dos.writeUTF(reason);
        nation.setMeta(NationMeta.REPORT_BAN, out.toByteArray());
    }

    public Map.Entry<String, Long> getBan(DBNation nation) {
        ByteBuffer buf = nation.getMeta(NationMeta.REPORT_BAN);
        if (buf == null) return null;
        ByteArrayInputStream in = new ByteArrayInputStream(buf.array());
        DataInputStream dis = new DataInputStream(in);
        try {
            return new KeyValue<>(dis.readUTF(), dis.readLong());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private final Map<Integer, Map<Integer, List<Map.Entry<Long, Long>>>> REMOVES_CACHE = new ConcurrentHashMap<>();

    public Map<Integer, List<Map.Entry<Long, Long>>> getCachedHistory(int nationId) {
        synchronized (REMOVES_CACHE) {
            if (REMOVES_CACHE.containsKey(nationId)) {
                return REMOVES_CACHE.get(nationId);
            }
            List<AllianceChange> removes = Locutus.imp().getNationDB().getRemovesByNation(nationId);
            Map<Integer, List<Map.Entry<Long, Long>>> durations = getAllianceDurationMap(removes);
            REMOVES_CACHE.put(nationId, durations);
            return durations;
        }
    }

    public Map<Integer, Long> getBlackListProximity(DBNation nation, Map<Integer, List<Map.Entry<Long, Long>>> allianceDurationMap) {
        Set<Integer> nationIds = getReportedNationIds(true);
        Map<Integer, Long> proximityMap = new HashMap<>();

        for (int nationId : nationIds) {
            if (nationId == nation.getId()) {
                continue;  // Skip comparing with itself
            }

            Map<Integer, List<Map.Entry<Long, Long>>> otherAllianceDurationMap = getCachedHistory(nationId);
            long totalProximity = 0;

            for (Map.Entry<Integer, List<Map.Entry<Long, Long>>> entry : allianceDurationMap.entrySet()) {
                int allianceId = entry.getKey();
                List<Map.Entry<Long, Long>> otherDurations = otherAllianceDurationMap.get(allianceId);
                if (otherDurations == null || otherDurations.isEmpty()) continue;

                List<Map.Entry<Long, Long>> durations = entry.getValue();

                // Durations is a list of start -> end date (epoch millis)
                // Get the time overlapped between durations and otherDurations
                long overlapTime = calculateOverlapDuration(durations, otherDurations);
                totalProximity += overlapTime;
            }
            if (totalProximity > 0) {
                proximityMap.put(nationId, totalProximity);
            }
        }
        return proximityMap;
    }

    public static long calculateOverlapDuration(List<Map.Entry<Long, Long>> list1, List<Map.Entry<Long, Long>> list2) {
        long overlapDuration = 0;
        int i = 0, j = 0;
        while (i < list1.size() && j < list2.size()) {
            Map.Entry<Long, Long> interval1 = list1.get(i);
            Map.Entry<Long, Long> interval2 = list2.get(j);
            if (interval1.getValue() < interval2.getKey()) {
                i++;
            } else if (interval2.getValue() < interval1.getKey()) {
                j++;
            } else {
                long start = Math.max(interval1.getKey(), interval2.getKey());
                long end = Math.min(interval1.getValue(), interval2.getValue());
                overlapDuration += end - start;
                if (interval1.getValue() < interval2.getValue()) {
                    i++;
                } else {
                    j++;
                }
            }
        }
        return overlapDuration;
    }
    public Map<Integer, List<Map.Entry<Long, Long>>> getAllianceDurationMap(List<AllianceChange> history) {
        Map<Integer, List<Map.Entry<Long, Long>>> allianceDurationMap = new Int2ObjectOpenHashMap<>();

        long currentTime = System.currentTimeMillis();
        int currentAllianceId = -1;
        long currentStartTime = -1;

        for (AllianceChange change : history) {
            long timestamp = change.getDate();
            int allianceId = change.getFromId();

            if (allianceId != currentAllianceId) {
                if (currentAllianceId > 0) {
                    long endTime = timestamp;
                    if (endTime > currentTime) {
                        endTime = currentTime;
                    }
                    allianceDurationMap.computeIfAbsent(currentAllianceId, k -> new ObjectArrayList<>())
                            .add(KeyValue.of(currentStartTime, endTime));
                }
                currentAllianceId = change.getToId();
                currentStartTime = timestamp;
            }
        }

        // Handle the last alliance entry
        if (currentAllianceId > 0) {
            long endTime = currentTime;
            allianceDurationMap.computeIfAbsent(currentAllianceId, k -> new ObjectArrayList<>())
                    .add(KeyValue.of(currentStartTime, endTime));
        }

        return allianceDurationMap;
    }

    public List<Report> loadApprovedReports() {
        return loadReports("approved = true");
    }

    public Map<Integer, Set<ReportType>> getApprovedReportTypesByNation(Set<Integer> nationIds) {
        Map<Integer, Set<ReportType>> types = new HashMap<>();

        List<Report> reports = loadReports("approved = true AND nation_id IN " + StringMan.getString(nationIds));
        for (Report report : reports) {
            types.computeIfAbsent(report.nationId, k -> new HashSet<>()).add(report.type);
        }
        return types;
    }

    public List<DBTrade> getBlacklistedTrades(DBNation nation) {
        Set<Integer> reportedNationIds = getReportedNationIds(true);
        reportedNationIds.add(nation.getNation_id());
        List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(nation.getNation_id(), 0);
        trades.removeIf(trade -> !reportedNationIds.contains(trade.getBuyer()) || !reportedNationIds.contains(trade.getSeller()) || trade.getBuyer() == trade.getSeller());
        return trades;
    }

    public Map<Integer, Double> getBlacklistedMoneyTrade(DBNation nation) {
        List<DBTrade> trades = getBlacklistedTrades(nation);
        // if (offer.getResource() == ResourceType.CREDITS) continue;
        //            int max = offer.getResource() == ResourceType.FOOD ? 1000 : 10000;
        //            if (offer.getPpu() > 1 && offer.getPpu() < max) continue;
        Map<Integer, Double> moneyTrades = new HashMap<>();
        for (DBTrade offer : trades) {
            if (offer.getResource() == ResourceType.CREDITS) continue;
            int max = offer.getResource() == ResourceType.FOOD ? 1000 : 10000;
            if (offer.getPpu() > 1 && offer.getPpu() < max) continue;
            double value = Math.max(ResourceType.convertedTotal(offer.getResource(), offer.getQuantity()), offer.getTotal());
            int id = offer.getBuyer() == nation.getNation_id() ? offer.getSeller() : offer.getBuyer();
            moneyTrades.compute(id, (k, v) -> v == null ? value : v + value);
        }
        return moneyTrades;
    }

    public void deleteComment(int reportId, int nationId) {
        db.update("DELETE FROM report_comments WHERE report_id = ? AND nation_id = ?", reportId, nationId);
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

    public static class CommentHeader {
        public int report_id;
        public int nation_id;
        public int discord_id;
        public int comment;
        public int date;

        public String[] getHeaderNames() {
            return new String[]{"report_id", "nation_id", "discord_id", "comment", "date"};
        }

        public void setDefaultIndexes() {
            report_id = 1;
            nation_id = 2;
            discord_id = 3;
            comment = 4;
            date = 5;
        }
    }

    public static class Comment {
        public int report_id;
        public int nationId;
        public long discordId;
        public String comment;
        public long date;

        public Comment(int report_id, int nationId, long discordId, String comment, long date) {
            this.report_id = report_id;
            this.nationId = nationId;
            this.discordId = discordId;
            this.comment = comment;
            this.date = date;
        }
        public Comment(CommentHeader header, List<Object> row) {
            report_id = Integer.parseInt(row.get(header.report_id).toString());
            nationId = Integer.parseInt(row.get(header.nation_id).toString());
            discordId = Long.parseLong(row.get(header.discord_id).toString());
            comment = row.get(header.comment).toString();
            date = Long.parseLong(row.get(header.date).toString());
        }

        public Comment(CommentHeader header, ResultSet rs) throws SQLException {
            report_id = rs.getInt(header.report_id);
            nationId = rs.getInt(header.nation_id);
            discordId = rs.getLong(header.discord_id);
            comment = rs.getString(header.comment);
            date = rs.getLong(header.date);
        }

        public String toMarkdown() {
            StringBuilder msg = new StringBuilder();
            msg.append("[" + DiscordUtil.timestamp(date, null) + "]");
            msg.append("(by: " + PW.getName(nationId, false) + " | " + DiscordUtil.getUserName(discordId) + "):\n");
            msg.append("> " + comment.replace("\n", "\n> "));
            return msg.toString();
        }
    }


    public List<Report> getReports(SpreadSheet sheet) throws NoSuchFieldException, IllegalAccessException, GeneralSecurityException, IOException {
        List<Report> result = new ArrayList<>();
        List<List<Object>> values = sheet.fetchAll(null);

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

    public List<Comment> getComments(SpreadSheet sheet) throws GeneralSecurityException, IOException, NoSuchFieldException, IllegalAccessException {
        List<Comment> result = new ArrayList<>();
        List<List<Object>> values = sheet.loadValuesCurrentTab(true);

        if (values.isEmpty()) return result;

        List<Object> headerRow = values.get(0);
        if (headerRow.isEmpty()) return result;

        CommentHeader header = sheet.loadHeader(new CommentHeader(), headerRow);
        for (int i = 1; i < values.size(); i++) {
            List<Object> row = values.get(i);
            if (row.isEmpty()) continue;

            Comment report = new Comment(header, row);
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

    public Set<Integer> getReportedNationIds(boolean isApproved) {
        String query = "SELECT DISTINCT nation_id FROM reports";
        if (isApproved) {
            query += " WHERE approved = 1";
        }
        Set<Integer> result = new ObjectOpenHashSet<>();
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
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

    public List<Comment> loadCommentsByReport(int reportId) {
        return loadComments("report_id = " + reportId);
    }

    public List<Comment> loadCommentsByNation(int nationId) {
        return loadComments("nation_id = " + nationId);
    }

    public List<Comment> loadCommentsByUser(long userId) {
        return loadComments("discord_id = " + userId);
    }

    public Comment loadCommentsByReportNation(int reportId, int nationId) {
        List<Comment> votes = loadComments("report_id = " + reportId + " AND nation_id = " + nationId);
        if (votes.isEmpty()) return null;
        return votes.get(0);
    }

    public List<Comment> loadComments(String whereClauseOrNull) {
        if (whereClauseOrNull == null) {
            whereClauseOrNull = "";
        } else {
            whereClauseOrNull = " WHERE " + whereClauseOrNull;
        }
        List<Comment> votes = new ObjectArrayList<>();

        CommentHeader header = new CommentHeader();
        header.setDefaultIndexes();
        String select = "SELECT " + StringMan.join(header.getHeaderNames(), ",") +  " FROM REPORT_COMMENTS" + whereClauseOrNull;
        try (PreparedStatement stmt = db.getConnection().prepareStatement(select)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                votes.add(new Comment(header, rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return votes;
    }
}
