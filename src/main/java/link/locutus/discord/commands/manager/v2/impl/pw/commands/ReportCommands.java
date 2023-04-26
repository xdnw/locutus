package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

public class ReportCommands {
    public static String REPORT_SHEET = "1HoENcKOBgc9lkdNEPvAyDT_SVtU6qYxOL4CYHwPNaHU";
    public static String VOTE_SHEET = "17suZfmU6T2qWcJi0zYsdPDzt5e1EJZY6QcEjrr3gmXM";


    // TODO
    // manage
    // vote
    // clean up output
    // resolve nation / discord in report and list


//    @Command(desc = "List your own reports (and allow you to remove them)")
//    public void manage(@Me DBNation me, @Me User user) {
//
//    }

    @Command(desc = "List the user reports made to Locutus for a nation or user")
    public String list(@Switch("n") DBNation nation, @Switch("u") User user) throws GeneralSecurityException, IOException, NoSuchFieldException, IllegalAccessException {
        if (nation == null || user == null) throw new IllegalArgumentException("Please specify a user or nation.");
        List<String> reportList = getReports().stream().filter(f -> f.nationId == nation.getId() && f.discordId == user.getIdLong()).map(Objects::toString).collect(Collectors.toList());
        if (reportList.isEmpty()) return "No reports founds.";

        return "**" + reportList.size() + " reports**:\n" + String.join("\n", reportList);
    }

    @Command(desc = "Report a nation to Locutus")
    public String create(@Me DBNation me, @Me User author, @Me GuildDB db,
                         ReportType type,
                         @Arg("Nation to report") DBNation target,
                         @Arg("Description of report") @TextArea String message,
                         @Arg("Image evidence of report") @Switch("i") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String imageEvidenceUrl,
                         @Arg("User to report") @Switch("u") User user,
                         @Arg("Link to relevant forum post") @Switch("f") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String forumPost,
                         @Arg("Link to relevant news post") @Switch("m") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String newsReport,
                         @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException, NoSuchFieldException, IllegalAccessException {
        if (sheet == null) sheet = SpreadSheet.create(REPORT_SHEET);
        if (message.charAt(0) == '=') return "Invalid message.";
        if (message.length() < 25) return "Message is too short (25 characters minimum)";
        if (forumPost != null && !forumPost.toLowerCase().contains("forums.politicsandwar.com/"))
            throw new IllegalArgumentException("`forumPost` must be a valid forum post URL. Provided: `" + forumPost + "`");
        if (newsReport != null && !newsReport.toLowerCase().contains("https://discord.com/channels/"))
            throw new IllegalArgumentException("`newsReport` must be a valid discord message URL. Provided: `" + newsReport + "`");

        List<List<Object>> values = sheet.loadValues();
        List<Object> headerRow;
        if (values.isEmpty()) {
            headerRow = new ArrayList<>();
            for (Field field : ReportHeader.class.getDeclaredFields()) {
                headerRow.add(field.getName());
            }
            sheet.addRow(headerRow);
        } else {
            headerRow = values.get(0);
        }
        ReportHeader header = sheet.loadHeader(new ReportHeader(), headerRow);

        List<Object> addRow = new ArrayList<>(headerRow);

        UUID id = UUID.randomUUID();
        addRow.set(header.report_id, id.toString());
        addRow.set(header.nation_id, target.getId());
        addRow.set(header.discord_id, user != null ? user.getId() : "0");
        addRow.set(header.report_type, type.name());
        addRow.set(header.reporter_nation_id, me.getId());
        addRow.set(header.reporter_discord_id, author.getId());
        addRow.set(header.reporter_alliance_id, me.getAlliance_id());
        addRow.set(header.reporter_guild_id, db.getGuild().getId());
        addRow.set(header.report_message, message);
        addRow.set(header.image_url, Optional.ofNullable(imageEvidenceUrl).orElse(""));
        addRow.set(header.forum_url, Optional.ofNullable(forumPost).orElse(""));
        addRow.set(header.news_url, Optional.ofNullable(newsReport).orElse(""));

        sheet.set(0, 0);

        return "Created report with id: `" + id + "`";
    }

    public List<Report> getReports() throws NoSuchFieldException, IllegalAccessException, GeneralSecurityException, IOException {
        List<Report> result = new ArrayList<>();
        SpreadSheet sheet = SpreadSheet.create(REPORT_SHEET);
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

    public List<Vote> getVotes() throws GeneralSecurityException, IOException, NoSuchFieldException, IllegalAccessException {
        List<Vote> result = new ArrayList<>();
        SpreadSheet sheet = SpreadSheet.create(VOTE_SHEET);
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

        public UUID reportId;
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

        public Report(ReportHeader header, List<Object> row) {
            reportId = UUID.fromString(row.get(header.report_id).toString());
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
    }

    public static class VoteHeader {
        public int report_id;
        public int nation_id;
        public int discord_id;
        public int vote;
        public int comment;
    }

    public static class Vote {
        public UUID report_id;
        public int nationId;
        public long discordId;
        public int vote;
        public String comment;

        public Vote(VoteHeader header, List<Object> row) {
            report_id = UUID.fromString(row.get(header.report_id).toString());
            nationId = Integer.parseInt(row.get(header.nation_id).toString());
            discordId = Long.parseLong(row.get(header.discord_id).toString());
            vote = Integer.parseInt(row.get(header.vote).toString());
            comment = row.get(header.comment).toString();
        }
    }
}
