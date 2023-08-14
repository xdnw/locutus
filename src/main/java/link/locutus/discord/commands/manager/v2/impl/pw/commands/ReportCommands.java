package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.config.Messages;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.ReportManager;
import link.locutus.discord.db.entities.DBLoan;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.LoanManager;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.ImageUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

import static link.locutus.discord.util.MarkupUtil.sheetUrl;
import static link.locutus.discord.util.PnwUtil.add;
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
            column.set(3, report.type.name());
            column.set(4, reporterNatUrl + "");
            column.set(5, reporterDiscordUrl + "");
            column.set(6, reporterAlliance + "");
            column.set(7, reporterGuild + "");
            column.set(8, report.message);
            column.set(9, report.imageUrl);
            column.set(10, report.forumUrl);
            column.set(11, report.newsUrl);
            column.set(12, report.date + "");
            column.set(13, report.approved + "");

            sheet.addRow(column);
        }
        sheet.clearAll();
        sheet.set(0, 0);
        sheet.attach(io.create(), "reports").send();
        return null;
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String importLegacyBlacklist(ReportManager reportManager, @Me GuildDB db, @Me DBNation me, @Me User author, SpreadSheet sheet) {
        List<List<Object>> rows = sheet.getValues();
        List<Object> header = rows.get(0);

        int discordIndex = header.indexOf("Discord ID");
        int nationIdIndex = header.indexOf("Nation ID");
        int reasonIndex = header.indexOf("Reason");
        int reportingEntityIndex = header.indexOf("Reporting Entity");
        if (discordIndex == -1) {
            return "`Discord ID` column not found";
        }
        if (nationIdIndex == -1) {
            return "`Nation ID` column not found";
        }
        if (reasonIndex == -1) {
            return "`Reason` column not found";
        }

        List<ReportManager.Report> reports = new ArrayList<>();
        // iterate rows

        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);

            int nationId = Integer.parseInt(row.get(nationIdIndex).toString());
            long discordId = Long.parseLong(row.get(discordIndex).toString());

            String reason = row.get(reasonIndex).toString();
            String reasonLower = reason.toLowerCase();

            ReportManager.ReportType type;
            if (reasonLower.contains("attacking") || reason.contains("declaring war")) {
                type = ReportManager.ReportType.THREATS_COERCION;
            } else if (reasonLower.contains("scam") || reasonLower.contains("$")) {
                type = ReportManager.ReportType.FRAUD;
            } else if (reasonLower.contains("default")) {
                type = ReportManager.ReportType.BANK_DEFAULT;
            } else if (reasonLower.contains("multi")) {
                type = ReportManager.ReportType.MULTI;
            } else {
                type = ReportManager.ReportType.FRAUD;
            }

            String reasonFinal = reason + "\nnote: `legacy blacklist`";
            if (reportingEntityIndex != -1) {
                reasonFinal += "\nreporting entity: " + row.get(reportingEntityIndex);
            }

            ReportManager.Report report = new ReportManager.Report(
                    nationId,
                    discordId,
                    type,
                    me.getNation_id(),
                    author.getIdLong(),
                    me.getAlliance_id(),
                    db.getIdLong(),
                    reason,
                    "",
                    "",
                    "",
                    System.currentTimeMillis(),
                    true
            );
            reports.add(report);
        }


        // ignore reports already exists
        List<ReportManager.Report> existing = reportManager.loadReports();
        Map<Integer, Set<String>> existingMap = new HashMap<>();
        for (ReportManager.Report report : existing) {
            int nationId = report.nationId;
            String msg = report.message;
            existingMap.computeIfAbsent(nationId, k -> new HashSet<>()).add(msg);
        }

        reports.removeIf(f -> existingMap.getOrDefault(f.nationId, Collections.emptySet()).contains(f.message));

        if (reports.isEmpty()) {
            return "No new reports to add";
        }

        reportManager.saveReports(reports);

        return "Added " + reports.size() + " reports. Use TODO CMD ref to view all reports";
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

        List<String> header = Arrays.asList(
                "Loan ID",
                "Loaner Guild/AA",
                "Loaner Nation",
                "Receiver",
                "Principal",
                "Paid",
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
            header.set(4, PnwUtil.resourcesToString(loan.paid) + "");
            header.set(5, PnwUtil.resourcesToString(loan.remaining) + "");
            header.set(6, loan.status.name());
            header.set(7, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(loan.dueDate)));
            header.set(8, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(loan.loanDate)));
            header.set(9, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(loan.date_submitted)));

            sheet.addRow(header);
        }

        sheet.clearAll();
        sheet.set(0, 0);
        sheet.attach(io.create(), "loans").send();
        return null;
    }



    @Command(desc = "Import loans from a spreadsheet")
    @RolePermission(Roles.ECON_STAFF)
    public String importLoans(LoanManager loanManager, @Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, SpreadSheet sheet, @Default DBLoan.Status defaultStatus, @Switch("o") boolean overwriteLoans, @Switch("m") boolean overwriteSameNation, @Switch("a") boolean addLoans) throws ParseException {
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
        int InterestIndex = -1;
        int paidIndex = -1;

        for (Object cell : header) {
            if (cell == null) continue;
            String cellString = cell.toString().toLowerCase();

            switch (cellString) {
                case "nation":
                case "receiver":
                    receiverIndex = header.indexOf(cell);
                    break;
                case "loan amount":
                case "principal":
                    principalIndex = header.indexOf(cell);
                    break;
                case "amount remaining":
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
                case "paid":
                    paidIndex = header.indexOf(cell);
                    break;
                case "interest":
                    InterestIndex = header.indexOf(cell);
                    break;
            }
        }

        long loanerGuildOrAA = db.getIdLong();
        if (db.isAlliance()) {
            loanerGuildOrAA = db.getAllianceIds().iterator().next();
        }

        List<String> warnings = new ArrayList<>();
        if (receiverIndex == -1) {
            return "`receiver` column not found on first row";
        }
        if (principalIndex == -1) {
            return "`principal` column not found on first row";
        }
        if (loanDateIndex == -1) {
            return "`loan date` column not found on first row";
        }
        // due date is optional
        if (dueDateIndex == -1) {
            warnings.add("`due date` column not found on first row");
        }
        // remaining is optional
        if (remainingIndex == -1) {
            warnings.add("`remaining` column not found on first row");
        }
        if (statusIndex == -1) {
            if (defaultStatus == null) {
                return "`status` column not found on first row. Please specify a status for this command or add a `status` column";
            } else {
                warnings.add("`status` column not found on first row, using `" + defaultStatus.name() + "` as default");
            }
        }

        long now = System.currentTimeMillis();

        List<DBLoan> loans = new ArrayList<>();
        for (int row = 1; row < rows.size(); row++) {
            List<Object> cells = rows.get(row);
            if (cells.size() < 6) {
                continue;
            }
            // required
            String receiverStr = cells.get(receiverIndex).toString();
            String principalStr = cells.get(principalIndex).toString();
            String loanDateStr = cells.get(loanDateIndex).toString();

            // optional
            String remainingStr = remainingIndex == -1 ? null : cells.get(remainingIndex).toString();
            String statusStr = statusIndex == -1 ? null : cells.get(statusIndex).toString();
            String dueDateStr = dueDateIndex == -1 ? null : cells.get(dueDateIndex).toString();
            String interestStr = InterestIndex == -1 ? null : cells.get(InterestIndex).toString();
            String paidStr = paidIndex == -1 ? null : cells.get(paidIndex).toString();

            int receiverId;
            boolean isRecieverAA;
            if (receiverStr.contains("/alliance") || receiverStr.toLowerCase().contains("aa:")) {
                receiverId = PnwUtil.parseAllianceId(receiverStr);
                isRecieverAA = true;
            } else {
                receiverId = DiscordUtil.parseNationId(receiverStr);
                isRecieverAA = false;
            }

            Map<ResourceType, Double> principal = PnwUtil.parseResources(principalStr);
            Map<ResourceType, Double> remaining = remainingStr == null ? null : PnwUtil.parseResources(remainingStr);
            Map<ResourceType, Double> paid = paidStr == null ? null : PnwUtil.parseResources(paidStr);
            Map<ResourceType, Double> interest = interestStr == null ? null : PnwUtil.parseResources(interestStr);

            if (paid == null && remaining != null) {
                double[] principalArr = PnwUtil.resourcesToArray(principal);
                double[] remainingArr = PnwUtil.resourcesToArray(remaining);
                double[] interestArr = interest == null ? null : PnwUtil.resourcesToArray(interest);
                if (interest != null) {
                    // paid = principal - remaining + interest
                    paid = PnwUtil.resourcesToMap(ResourceType.add(ResourceType.subtract(principalArr, remainingArr), interestArr));
                } else {
                    // paid = principal - remaining
                    paid = PnwUtil.resourcesToMap(PnwUtil.max(ResourceType.getBuffer(), ResourceType.subtract(principalArr, remainingArr)));
                }
            }

            DBLoan.Status status = statusStr == null || statusStr.isEmpty() ? defaultStatus : DBLoan.Status.valueOf(statusStr.toUpperCase());

            long loanDate = TimeUtil.YYYY_MM_DD_HH_MM_SS.parse(loanDateStr).getTime();
            long dueDate = dueDateStr == null ? 0 : TimeUtil.YYYY_MM_DD_HH_MM_SS.parse(dueDateStr).getTime();

            DBLoan loan = new DBLoan(
                    loanerGuildOrAA,
                    me.getId(),
                    receiverId,
                    isRecieverAA,
                    PnwUtil.resourcesToArray(principal),
                    paid == null ? ResourceType.getBuffer() : PnwUtil.resourcesToArray(paid),
                    remaining == null ? ResourceType.getBuffer() : PnwUtil.resourcesToArray(remaining),
                    status,
                    dueDate,
                    loanDate,
                    now
            );
            loans.add(loan);
        }

        // only allow 1 option to be set, not multiple
        int numSet = (overwriteLoans ? 1 : 0) + (addLoans ? 1 : 0) + (overwriteSameNation ? 1 : 0);
        if (numSet > 1) {
            return "Only 1 option can be set at a time. Please select one of `overwriteLoans`, `addLoans`, or `overwriteSameNation`";
        }


        if (overwriteLoans == false && addLoans == false && overwriteSameNation == false) {
            String title = "Add or Overwrite loans";
            StringBuilder body = new StringBuilder();
            body.append(loans.size() + " loans will be added to the database\n");

            List<DBLoan> existing = loanManager.getLoansByGuildOrAlliance(loanerGuildOrAA);
            body.append("There are currently " + existing.size() + " loans already in the database\n");
            body.append("\nPress `Add` to add the loans to the database\n");
            body.append("Press `Same` to overwrite loans with the same receiver\n");
            body.append("Press `Overwrite` to overwrite the existing loans with the new loans\n");
            body.append("Press `View` to view the loans that will be added\n");

            io.create().embed(title, body.toString())
                    .confirmation(command, "addLoans", "Add")
                    .confirmation(command, "overwriteSameNation", "Same")
                    .confirmation(command, "overwriteLoans", "Overwrite")
//                    .commandButton(TODO cm ref view)
                    .send();
            return null;
        }
        if (addLoans) {
            loanManager.addLoans(loans);
        } else if (overwriteSameNation) {
            // delete matching loans
            List<DBLoan> existing = loanManager.getLoansByGuildOrAlliance(loanerGuildOrAA);

            Set<Integer> addNationIds = loans.stream().filter(f -> !f.isAlliance).map(f -> f.nationOrAllianceId).collect(Collectors.toSet());
            Set<Integer> addAAIds = loans.stream().filter(f -> f.isAlliance).map(f -> f.nationOrAllianceId).collect(Collectors.toSet());

            List<DBLoan> sameNationOrAA = existing.stream().filter(f ->
                    (f.isAlliance && addAAIds.contains(f.nationOrAllianceId)) ||
                            (!f.isAlliance && addNationIds.contains(f.nationOrAllianceId))).toList();

            loanManager.deleteLoans(sameNationOrAA);

            loanManager.addLoans(loans);
        } else if (overwriteLoans) {
            loanManager.setLoans(loanerGuildOrAA, loans);
        }

        return "Done, created `" + loans.size() + "` loans\n" +
                "See: TODO CM ref to view current loans";
    }

    @Command(desc = "Report a nation to the bot")
    public String createReport(@Me DBNation me, @Me User author, @Me GuildDB db, @Me IMessageIO io, @Me JSONObject command,
                         ReportManager reportManager,
                         ReportManager.ReportType type,
                         @Arg("Description of report") @TextArea String message,
                         @Default @Arg("Nation to report") DBNation nation,
                         @Default @Arg("Discord user to report") Long discord_user_id,
                         @Arg("Image evidence of report") @Switch("i") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String imageEvidenceUrl,
                         @Arg("Link to relevant forum post") @Switch("f") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String forum_post,
                         @Arg("Link to relevant news post") @Switch("m") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String news_post,
                               @Switch("i") ReportManager.Report updateReport,
                               @Switch("f") boolean force) {
        if (forum_post == null && news_post == null) {
            return "You must provide either a link to a forum post, or a link to a news report";
        }
        if (nation == null && discord_user_id == null) {
            // say must provide one of either
            // post link for how to get discord id <url>
            return """
                    You must provide either a `nation` or a `discord_user_id` to report
                    To get a discord user id, right click on the user and select `Copy ID`
                    See: <https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID->""";
        }

        // At least one forum post or news report must be attached
        Set<Long> supportedServers = new HashSet<>(Arrays.asList(
                869424139037990912L, // Ducc News Network
                446601982564892672L, // Royal Orbis News
                821587932384067584L, // Orbis Crowned News
                827905979575959629L, // Very Good Media
                353730979816407052L, // Pirate Island Times
                1022224780751011860L, // Micro Minute
                580481635645128745L // Thalmoria
        ));

        if (forum_post == null && news_post == null) {
            return "No argument provided\n" + Messages.FORUM_NEWS_ERROR;
        }
        if (forum_post != null && !forum_post.startsWith("https://forum.politicsandwar.com/index.php?/topic/")) {
            return "Forum post must be on domain `https://forum.politicsandwar.com/index.php?/topic/`\n" + Messages.FORUM_NEWS_ERROR;
        }
        if (news_post != null) {
            // https://discord.com/channels/SERVER_ID/992205932006228041/1073856622545346641
            // remove the https://discord.com/channels/ part
            String[] idsStr = news_post.substring("https://discord.com/channels/".length()).split("/");
            if (idsStr.length != 3) {
                return "News post must be discord message link in the format `https://discord.com/channels/SERVER_ID/CHANNEL_ID/MESSAGE_ID`\n" + Messages.FORUM_NEWS_ERROR;
            }
            try {
                long serverId = Long.parseLong(idsStr[0]);
                if (!supportedServers.contains(serverId)) {
                    return "The news server you linked is not supported\n" + Messages.FORUM_NEWS_ERROR;
                }
            } catch (NumberFormatException e) {
                return "News post must be discord message link in the format `https://discord.com/channels/SERVER_ID/CHANNEL_ID/MESSAGE_ID`\n" + Messages.FORUM_NEWS_ERROR;
            }
        }

        if (imageEvidenceUrl != null) {
            // ensure imageEvidenceUrl is discord image url
            String imageOcr = ImageUtil.getText(imageEvidenceUrl);
            if (imageOcr != null) {
                message += "\nScreenshot transcript:\n```\n" + imageOcr + "\n```";
            }
        }

        if (discord_user_id != null && nation != null) {
            PNWUser nationUser = Locutus.imp().getDiscordDB().getUserFromNationId(nation.getId());
            DBNation userNation = DiscordUtil.getNation(discord_user_id);
            if (nationUser != null && nationUser.getDiscordId() != discord_user_id) {
                throw new IllegalArgumentException("The nation you provided is already linked to a different discord user `" + DiscordUtil.getUserName(nationUser.getDiscordId()) + "`.\n" +
                        "Please create a separate report for this discord user.");
            }
            if (userNation != null && userNation.getId() != nation.getId()) {
                throw new IllegalArgumentException("The discord user you provided is already linked to a different nation `" + userNation.getName() + "`.\n" +
                        "Please create a separate report for this nation.");
            }
        }

        if (discord_user_id == null) {
            PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(nation.getId());
            if (user != null) {
                discord_user_id = user.getDiscordId();
            }
        }
        if (nation == null) {
            nation = DiscordUtil.getNation(discord_user_id);
        }

        Integer nationId = nation == null ? null : nation.getId();

        int reporterNationId = me.getId();
        long reporterUserId = author.getIdLong();
        long reporterGuildId = db.getIdLong();
        int reporterAlliance = me.getAlliance_id();

        ReportManager.Report existing = null;
        if (updateReport != null) {
            existing = updateReport;
            if (existing.hasPermission(me, author, db)) {
                return "You do not have permission to edit this report: `#" + updateReport.reportId +
                        "` (owned by nation:" + PnwUtil.getName(existing.reporterNationId, false) + ")\n" +
                        "To add a comment: TODO CM ref";
            }
            // set the missing fields to the values from this report
            if (nationId == null) {
                nationId = existing.reporterNationId;
            }
            if (discord_user_id == null) {
                discord_user_id = existing.reporterDiscordId;
            }
            if (type == null) {
                type = existing.type;
            }
            if (message == null) {
                message = existing.message;
            }
            if (imageEvidenceUrl == null) {
                imageEvidenceUrl = existing.imageUrl;
            }
            if (forum_post == null) {
                forum_post = existing.forumUrl;
            }
            if (news_post == null) {
                news_post = existing.newsUrl;
            }
            // Keep reporter info if updating
            reporterNationId = existing.reporterNationId;
            reporterUserId = existing.reporterDiscordId;
            reporterGuildId = existing.reporterGuildId;
            reporterAlliance = existing.reporterAllianceId;
        }

        if (!force) {
            String reportedName = "";
            String title = (existing != null ? "Update " : "Report") +
                    type + " report by " +
                    DiscordUtil.getUserName(reporterUserId) + " | " + PnwUtil.getName(reporterNationId, false);

            StringBuilder body = new StringBuilder();
            if (nationId != null) {
                body.append("Nation: " + PnwUtil.getMarkdownUrl(nationId, false) + "\n");
            }
            if (discord_user_id != null) {
                body.append("Discord user: <@" + discord_user_id + ">\n");
            }
            body.append("Your message:\n```\n" + message + "\n```\n");
            if (imageEvidenceUrl != null) {
                body.append("Image evidence: " + imageEvidenceUrl + "\n");
            }
            if (forum_post != null) {
                body.append("Forum post: " + forum_post + "\n");
            }
            if (news_post != null) {
                body.append("News post: " + news_post + "\n");
            }

            List<ReportManager.Report> reportList = reportManager.loadReportsByNationOrUser(nationId, discord_user_id);
            if (!reportList.isEmpty()) {
                body.append("To add a comment: TODO CM Ref\n");
                body.append("**Please look at these existing reports and add a comment instead if you are reporting the same thing**\n");
                for (ReportManager.Report report : reportList) {
                    body.append("#" + report.reportId +": ```\n" + report.message + "\n```\n");
                }
            }

            io.create().embed(title, body.toString()).append("""
                    If there is a game rule violation, create a report on the P&W discord, or forums
                    If there is a violation of discord ToS, report to discord: 
                    <https://discord.com/safety/360044103651-reporting-abusive-behavior-to-discord>""")
                    .confirmation(command).send();
            return null;
        }
        ReportManager.Report report = new ReportManager.Report(
            nationId == null ? 0 : nationId,
            discord_user_id == null ? 0 : discord_user_id,
            type,
            reporterNationId,
            reporterUserId,
            reporterAlliance,
            reporterGuildId,
            message,
            imageEvidenceUrl == null ? "" : imageEvidenceUrl,
            forum_post == null ? "" : forum_post,
            news_post == null ? "" : news_post,
            System.currentTimeMillis(),
            Roles.INTERNAL_AFFAIRS_STAFF.hasOnRoot(author));

        if (existing != null) {
            report.reportId = existing.reportId;
        }

        reportManager.saveReport(report);

        return "Report " + (existing == null ? "created" : "updated") + " with id `" + report.reportId + "`\n" +
                "See: TODO CM REf to view your report\";";
    }

    @Command
    public String removeReport(ReportManager reportManager, @Me DBNation me, @Me User author, @Me GuildDB db, ReportManager.Report report, @Switch("f") boolean force) {
        if (!report.hasPermission(me, author, db)) {
            return "You do not have permission to remove this report: `#" + report.reportId +
                    "` (owned by nation:" + PnwUtil.getName(report.reporterNationId, false) + ")\n" +
                    "To add a comment: TODO CM ref";
        }
        if (!force) {
            // report type, and nation reported
            String title = "Remove " + report.type + " report";
            StringBuilder body = new StringBuilder(report.toMarkdown());
        }
        reportManager.deleteReport(report.reportId);
        return "Report removed.";
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
////                         ReportType type,
////                         @Arg("Nation to report") DBNation target,
////                         @Arg("Description of report") @TextArea String message,
////                         @Arg("Image evidence of report") @Switch("i") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String imageEvidenceUrl,
////                         @Arg("User to report") @Switch("u") User user,
////                         @Arg("Link to relevant forum post") @Switch("f") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String forumPost,
////                         @Arg("Link to relevant news post") @Switch("m") @Filter("[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)") String newsReport,
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
