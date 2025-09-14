package link.locutus.discord.db;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static link.locutus.discord.util.MarkupUtil.markdownUrl;
import static link.locutus.discord.util.discord.DiscordUtil.getGuildName;
import static link.locutus.discord.util.discord.DiscordUtil.getMessageGuild;

public class Report {

    public int reportId;
    public int nationId;
    public long discordId;
    public ReportType type;
    public int reporterNationId;
    public long reporterDiscordId;
    public int reporterAllianceId;
    public long reporterGuildId;
    public String message;
    public List<String> imageUrls;
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
                  List<String> imageUrls,
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
        this.imageUrls = imageUrls;
        this.forumUrl = forumUrl;
        this.newsUrl = newsUrl;
        this.date = date;
        this.approved = approved;
    }

    public Report(ReportManager.ReportHeader header, List<Object> row) {
        reportId = Integer.parseInt(row.get(header.report_id).toString());
        nationId = Integer.parseInt(row.get(header.nation_id).toString());
        discordId = Long.parseLong(row.get(header.discord_id).toString());
        type = ReportType.valueOf(row.get(header.report_type).toString());
        reporterNationId = Integer.parseInt(row.get(header.reporter_nation_id).toString());
        reporterDiscordId = Long.parseLong(row.get(header.reporter_discord_id).toString());
        reporterAllianceId = Integer.parseInt(row.get(header.reporter_alliance_id).toString());
        reporterGuildId = Long.parseLong(row.get(header.reporter_guild_id).toString());
        message = row.get(header.report_message).toString();
        String imageUrlStr = row.get(header.image_url).toString().trim();
        if (imageUrlStr.isEmpty()) {
            imageUrls = new ArrayList<>();
        } else {
            imageUrls = Arrays.asList(imageUrlStr.split("\n"));
        }
        forumUrl = row.get(header.forum_url).toString();
        newsUrl = row.get(header.news_url).toString();
        date = Long.parseLong(row.get(header.date).toString());
        approved = Boolean.parseBoolean(row.get(header.approved).toString());
    }

    public Report(ReportManager.ReportHeader header, ResultSet rs) throws SQLException {
        reportId = rs.getInt(header.report_id);
        nationId = rs.getInt(header.nation_id);
        discordId = rs.getLong(header.discord_id);
        type = ReportType.values()[rs.getInt(header.report_type)];
        reporterNationId = rs.getInt(header.reporter_nation_id);
        reporterDiscordId = rs.getLong(header.reporter_discord_id);
        reporterAllianceId = rs.getInt(header.reporter_alliance_id);
        reporterGuildId = rs.getLong(header.reporter_guild_id);
        message = rs.getString(header.report_message);
        String imageUrlStr = rs.getString(header.image_url);
        if (imageUrlStr == null || imageUrlStr.isEmpty()) {
            imageUrls = new ArrayList<>();
        } else {
            imageUrls = Arrays.asList(imageUrlStr.split("\n"));
        }
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
                ", imageUrl='" + imageUrls + '\'' +
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
        StringBuilder body = new StringBuilder("Report `#" + reportId + "` - " + type + "\n");
        if (nationId != 0) {
            body.append("Nation: " + PW.getMarkdownUrl(nationId, false) + "\n");
        }
        if (discordId != 0) {
            body.append("Discord: `" + DiscordUtil.getUserName(discordId) + "` | `" + discordId + "`\n");
        }
        String reporterNationLink = PW.getMarkdownUrl(reporterNationId, false);
        String reporterAllianceLink = PW.getMarkdownUrl(reporterAllianceId, true);
        String reporterGuildLink = markdownUrl(getGuildName(reporterGuildId), DiscordUtil.getGuildUrl(reporterGuildId));
        body.append("Reported by: " + reporterNationLink + " | " + reporterAllianceLink + " | <@" + reporterDiscordId + "> | " + reporterGuildLink + "\n");
        body.append("```\n" + message + "\n```\n");
        if (imageUrls != null && !imageUrls.isEmpty()) {
            body.append("Image: <" + StringMan.join(imageUrls, "> <") + ">\n");
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

        List<ReportManager.Comment> comments = Locutus.imp().getNationDB().getReportManager().loadCommentsByReport(this.reportId);
        if (!comments.isEmpty()) {
            if (includeComments) {
                for (ReportManager.Comment comment : comments) {
                    body.append("-" + comment.toMarkdown() + "\n");
                }
            } else {
                body.append(comments.size() + " comments, see: " + CM.report.show.cmd.toSlashMention());
            }
        }
        return body.toString();
    }

    public String getTitle() {
        String msg = "";
        if (nationId != 0) {
            msg += PW.getName(nationId, false);
        }
        if (discordId != 0) {
            if (!msg.isEmpty()) msg += " | ";
            msg += DiscordUtil.getUserName(discordId) + " | " + discordId;
        }
        return msg + " | " + type.name();
    }
}
