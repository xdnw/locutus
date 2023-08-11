package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.ReportManager;
import link.locutus.discord.db.entities.DBLoan;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.LoanManager;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
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
    @RolePermission(Roles.INTERNAL_AFFAIRS)
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
        sheet.attach(io.create()).send();
        return null;
    }

    @Command(desc = "Get all loan information banks and alliances have submitted")
    @RolePermission(Roles.ECON_STAFF)
    public String getLoanSheet(@Me IMessageIO io, @Me GuildDB db, LoanManager manager, @Default Set<DBNation> nations, @Switch("s") SpreadSheet sheet, @Switch("l") Set<DBLoan.Status> loanStatus) throws GeneralSecurityException, IOException {
        List<DBLoan> loans;
        Set<Integer> nationIds = nations.stream().map(DBNation::getId).collect(Collectors.toSet());
        if (nations.size() >= 1000) {
            loans = manager.getLoansByStatus(loanStatus); // get all loans and then filter
            // remove if not
             loans.removeIf(f -> !f.isAlliance && !nationIds.contains(f.nationOrAllianceId));
            // get all loans and then filter
        } else {
            loans = manager.getLoansByNations(nationIds, loanStatus);
        }

        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKeys.LOANS_SHEET);
        }

//        public int loanId;
//        public long loanerGuildOrAA;
//        public int loanerNation;
//        public int nationOrAllianceId;
//        public boolean isAlliance;
//        public double[] principal;
//        public double[] remaining;
//        public Status status;
//        public long dueDate;
//        public long loanDate;
//        public long date_submitted;

        List<String> header = Arrays.asList(
                "Loan ID",
                "Loaner Guild/AA",
                "Loaner Nation",
                "Receiver",
                "Principal",
                "Remaining",
                "Status",
                "Due Date",
                "Loan Date",
                "Date Submitted"
        );

        sheet.addRow(header);

        // sort loans by status then date
        loans.sort(Comparator.comparing(DBLoan::getStatus).thenComparing(DBLoan::getLoanDate));

        for (DBLoan loan : loans) {
            header.set(0, loan.loanId + "");
            String loanerName = loan.loanerGuildOrAA > Integer.MAX_VALUE ? DiscordUtil.getGuildName(loan.loanerGuildOrAA) : PnwUtil.getName(loan.loanerGuildOrAA, true);
            String loanerUrl = loan.loanerGuildOrAA > Integer.MAX_VALUE ? DiscordUtil.getGuildUrl(loan.loanerGuildOrAA) : PnwUtil.getAllianceUrl((int) loan.loanerGuildOrAA);
            String loanerMarkup = sheetUrl(loanerName, loanerUrl);
            header.set(1, loanerMarkup + "");
            header.set(2, sheetUrl(PnwUtil.getName(loan.loanerNation, false), PnwUtil.getNationUrl(loan.loanerNation)) + "");
            String name = PnwUtil.getName(loan.nationOrAllianceId, loan.isAlliance);
            String url = loan.isAlliance ? PnwUtil.getAllianceUrl(loan.nationOrAllianceId) : PnwUtil.getNationUrl(loan.nationOrAllianceId);
            String receiverMarkup = sheetUrl(name, url);
            header.set(3, receiverMarkup + "");
            header.set(4, PnwUtil.resourcesToString(loan.principal) + "");
            header.set(5, PnwUtil.resourcesToString(loan.remaining) + "");
            header.set(6, loan.status.name());
            header.set(7, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(loan.dueDate)));
            header.set(8, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(loan.loanDate)));
            header.set(9, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(loan.date_submitted)));

            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);
        sheet.attach(io.create()).send();
        return null;
    }

    @Command
    public String importLoans(SpreadSheet sheet) {
        List<List<Object>> rows = sheet.getValues();
        if (rows.isEmpty()) {
            return "No rows found: " + sheet.getURL();
        }
        List<Object> header = rows.get(0);
        int receiverIndex = -1;
        int principalIndex = -1;
        int remainingIndex = -1;
        int statusIndex = -1;
        int dueDateIndex = -1;
        int loanDateIndex = -1;

        for (Object cell : header) {
            if (cell == null) continue;
            String cellString = cell.toString().toLowerCase();

            switch (cellString) {
                case "receiver":
                    receiverIndex = header.indexOf(cell);
                    break;
                case "principal":
                    principalIndex = header.indexOf(cell);
                    break;
                case "remaining":
                    remainingIndex = header.indexOf(cell);
                    break;
                case "status":
                    statusIndex = header.indexOf(cell);
                    break;
                case "due date":
                    dueDateIndex = header.indexOf(cell);
                    break;
                case "date":
                case "loan date":
                    loanDateIndex = header.indexOf(cell);
                    break;
            }
        }

        if (receiverIndex == -1) {
            return "`receiver` column not found on first row";
        }
        if (principalIndex == -1) {
            return "`principal` column not found on first row";
        }
        if (remainingIndex == -1) {
            return "`remaining` column not found on first row";
        }
        if (statusIndex == -1) {
            return "`status` column not found on first row";
        }
        if (dueDateIndex == -1) {
            return "`due date` column not found on first row";
        }
        if (loanDateIndex == -1) {
            return "`loan date` column not found on first row";
        }

        List<DBLoan> loans = new ArrayList<>();
        for (int row = 1; row < rows.size(); row++) {
            List<Object> cells = rows.get(row);
            if (cells.size() < 6) {
                continue;
            }
            String receiverStr = cells.get(receiverIndex).toString();
            String principalStr = cells.get(principalIndex).toString();
            String remainingStr = cells.get(remainingIndex).toString();
            String statusStr = cells.get(statusIndex).toString();
            String dueDateStr = cells.get(dueDateIndex).toString();
            String loanDateStr = cells.get(loanDateIndex).toString();

            int receiverId;
            boolean isRecieverAA = false;

            Map<ResourceType, Double> principal = PnwUtil.parseResources(principalStr);
            Map<ResourceType, Double> remaining = PnwUtil.parseResources(remainingStr);
            DBLoan.Status status = DBLoan.Status.valueOf(statusStr.toUpperCase());
            // parse google sheet date format
//            long dueDate = TimeUtil.YYYY_MM_DD_HH_MM_SS;/
        }
        return "TODO: Not finished";
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
