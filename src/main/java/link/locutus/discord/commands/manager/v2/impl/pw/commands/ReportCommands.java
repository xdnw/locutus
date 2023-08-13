package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.ReportManager;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

import static link.locutus.discord.util.MarkupUtil.sheetUrl;
import static link.locutus.discord.util.PnwUtil.getAllianceUrl;
import static link.locutus.discord.util.PnwUtil.getName;
import static link.locutus.discord.util.PnwUtil.getNationUrl;
import static link.locutus.discord.util.discord.DiscordUtil.getGuildName;
import static link.locutus.discord.util.discord.DiscordUtil.getGuildUrl;
import static link.locutus.discord.util.discord.DiscordUtil.getUserName;
import static link.locutus.discord.util.discord.DiscordUtil.userUrl;

public class ReportCommands {
    @Command(desc=  "Get a sheet of all the community reports for players")
    public String reportSheet(@Me IMessageIO io, @Me GuildDB db, ReportManager manager, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException, NoSuchFieldException, IllegalAccessException {
        List<ReportManager.Report> reports = manager.loadReports(null);

        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKeys.REPORTS_SHEET);
        }


        ReportManager.ReportHeader header = new ReportManager.ReportHeader();
        List<String> column = new ArrayList<>(Arrays.asList(header.getHeaderNames()));
        sheet.addRow(column);

        for (ReportManager.Report report : reports) {

            String natUrl = sheetUrl(getName(report.nationId, false), getNationUrl(report.nationId));
            String discordUrl = sheetUrl(getUserName(report.discordId), userUrl(report.discordId, false));

            String reporterNatUrl = sheetUrl(getName(report.reporterNationId, false), getNationUrl(report.reporterNationId));
            String reporterDiscordUrl = sheetUrl(getUserName(report.reporterDiscordId), userUrl(report.reporterDiscordId, false));

            String reporterAlliance = sheetUrl(getName(report.reporterAllianceId, true), getAllianceUrl(report.reporterAllianceId));
            String reporterGuild = sheetUrl(getGuildName(report.reporterGuildId), getGuildUrl(report.reporterGuildId));

            column.set(0, report.reportId + "");
            column.set(1, natUrl + "");
            column.set(2, discordUrl + "");
            column.set(3, report.reportType.name());
            column.set(4, reporterNatUrl + "");
            column.set(5, reporterDiscordUrl + "");
            column.set(6, reporterAlliance + "");
            column.set(7, reporterGuild + "");
            column.set(8, report.reportMessage);
            column.set(9, report.imageUrl);
            column.set(10, report.forumUrl);
            column.set(11, report.newsUrl);
            column.set(12, report.date + "");

            sheet.addRow(column);
        }
        sheet.clearAll();
        sheet.set(0, 0);

        sheet.attach(io.create(), "reports").send();
        return null;
    }

    // TODO
    // manage
    // vote
    // clean up output
    // resolve nation / discord in report and list


//    @Command(desc = "List your own reports (and allow you to remove them)")
//    public void manage(@Me DBNation me, @Me User user) {
//
//    }

//    @Command(desc = "List the user reports made to the bot for a nation or user")
//    public String list(@Switch("n") DBNation nation, @Switch("u") long discordId, @Switch("n") int nationId) throws GeneralSecurityException, IOException, NoSuchFieldException, IllegalAccessException {
//
//    }
//
//    @Command(desc = "Report a nation to the bot")
//    public String create(@Me DBNation me, @Me User author, @Me GuildDB db,
//                         ReportType type,
//                         @Arg("Nation to report") DBNation target,
//                         @Arg("Description of report") @TextArea String message,
//                         @Arg("Image evidence of report") @Switch("i") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String imageEvidenceUrl,
//                         @Arg("User to report") @Switch("u") User user,
//                         @Arg("Link to relevant forum post") @Switch("f") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String forumPost,
//                         @Arg("Link to relevant news post") @Switch("m") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String newsReport,
//                         @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException, NoSuchFieldException, IllegalAccessException {
//        if (sheet == null) sheet = SpreadSheet.create(REPORT_SHEET);
//        if (message.charAt(0) == '=') return "Invalid message.";
//        if (message.length() < 25) return "Message is too short (25 characters minimum)";
//        if (forumPost != null && !forumPost.toLowerCase().contains("forums.politicsandwar.com/"))
//            throw new IllegalArgumentException("`forumPost` must be a valid forum post URL. Provided: `" + forumPost + "`");
//        if (newsReport != null && !newsReport.toLowerCase().contains("https://discord.com/channels/"))
//            throw new IllegalArgumentException("`newsReport` must be a valid discord message URL. Provided: `" + newsReport + "`");
//
//        List<List<Object>> values = sheet.loadValues();
//        List<Object> headerRow;
//        if (values.isEmpty()) {
//            headerRow = new ArrayList<>();
//            for (Field field : ReportHeader.class.getDeclaredFields()) {
//                headerRow.add(field.getName());
//            }
//            sheet.addRow(headerRow);
//        } else {
//            headerRow = values.get(0);
//        }
//        ReportHeader header = sheet.loadHeader(new ReportHeader(), headerRow);
//
//        List<Object> addRow = new ArrayList<>(headerRow);
//
//        UUID id = UUID.randomUUID();
//        addRow.set(header.report_id, id.toString());
//        addRow.set(header.nation_id, target.getId());
//        addRow.set(header.discord_id, user != null ? user.getId() : "0");
//        addRow.set(header.report_type, type.name());
//        addRow.set(header.reporter_nation_id, me.getId());
//        addRow.set(header.reporter_discord_id, author.getId());
//        addRow.set(header.reporter_alliance_id, me.getAlliance_id());
//        addRow.set(header.reporter_guild_id, db.getGuild().getId());
//        addRow.set(header.report_message, message);
//        addRow.set(header.image_url, Optional.ofNullable(imageEvidenceUrl).orElse(""));
//        addRow.set(header.forum_url, Optional.ofNullable(forumPost).orElse(""));
//        addRow.set(header.news_url, Optional.ofNullable(newsReport).orElse(""));
//
//        sheet.set(0, 0);
//
//        return "Created report with id: `" + id + "`";
//    }




}
