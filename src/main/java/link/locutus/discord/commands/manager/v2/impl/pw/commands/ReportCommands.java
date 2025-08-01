package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Messages;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.ReportManager;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static link.locutus.discord.apiv1.enums.ResourceType.resourcesToArray;
import static link.locutus.discord.apiv1.enums.ResourceType.subtract;
import static link.locutus.discord.db.guild.GuildKey.REPORT_ALERT_CHANNEL;
import static link.locutus.discord.util.MarkupUtil.sheetUrl;
import static link.locutus.discord.util.PW.*;
import static link.locutus.discord.util.discord.DiscordUtil.*;

public class ReportCommands {
    @Command(desc=  "Generate a sheet of all the community reports for players", viewable = true)
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String reportSheet(@Me IMessageIO io, @Me @Default GuildDB db, ReportManager manager, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException, NoSuchFieldException, IllegalAccessException {
        List<ReportManager.Report> reports = manager.loadReports(null);

        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.REPORTS_SHEET);
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
            column.set(1, natUrl);
            column.set(2, discordUrl);
            column.set(3, report.type.name());
            column.set(4, reporterNatUrl);
            column.set(5, reporterDiscordUrl);
            column.set(6, reporterAlliance);
            column.set(7, reporterGuild);
            column.set(8, report.message);
            column.set(9, StringMan.join(report.imageUrls, "\n"));
            column.set(10, report.forumUrl);
            column.set(11, report.newsUrl);
            column.set(12, report.date + "");
            column.set(13, report.approved + "");

            sheet.addRow(column);
        }
        sheet.updateClearCurrentTab();
        sheet.updateWrite();
        sheet.attach(io.create(), "reports").send();
        return null;
    }

    @Command(desc = "Import the legacy sheet of reports from a google sheet\n" +
            "Expects the columns: `Discord ID`, `Nation ID`, `Reason`, `Reporting Entity`")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERNAL_AFFAIRS}, root = true, any = true)
    public String importLegacyBlacklist(ReportManager reportManager, @Me GuildDB db, @Me DBNation me, @Me User author, SpreadSheet sheet) {
        List<List<Object>> rows = sheet.fetchAll(null);
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
            if (row.isEmpty()) continue;

            String nationIdStr = row.get(nationIdIndex).toString();
            String discordIdsStr = row.get(discordIndex).toString();
            int nationId = nationIdStr.equalsIgnoreCase("unknown") ||
                    nationIdStr.equalsIgnoreCase("deleted") ||
                    nationIdStr.isEmpty() ? 0 : Integer.parseInt(nationIdStr);
            Set<Long> discordIds = new LongOpenHashSet();

            if (!discordIdsStr.equalsIgnoreCase("unknown") &&
                    !discordIdsStr.equalsIgnoreCase("deleted") &&
                    !discordIdsStr.isEmpty()) {
                for (String idStr : discordIdsStr.split(",")) {
                    discordIds.add(Long.parseLong(idStr.trim()));
                }
            }
            if (discordIds.isEmpty()) discordIds.add(0L);

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
            if (reportingEntityIndex != -1 && reportingEntityIndex < row.size()) {
                Object entityObj = row.get(reportingEntityIndex);
                if (entityObj != null && !entityObj.toString().isEmpty()) {
                    reasonFinal += "\nreporting entity: " + entityObj;
                }
            }
            for (long discordId : discordIds) {
                if (nationId == 0 && discordId == 0) continue;

                ReportManager.Report report = new ReportManager.Report(
                        nationId,
                        discordId,
                        type,
                        me.getNation_id(),
                        author.getIdLong(),
                        me.getAlliance_id(),
                        db.getIdLong(),
                        reasonFinal,
                        new ArrayList<>(),
                        "",
                        "",
                        System.currentTimeMillis(),
                        true
                );
                reports.add(report);
            }
        }


        // ignore reports already exists
        List<ReportManager.Report> existing = reportManager.loadReports();
        Map<Integer, Set<String>> existingMap = new Int2ObjectOpenHashMap<>();
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

        return "Added " + reports.size() + " reports. See " + CM.report.sheet.generate.cmd.toSlashMention();
    }

    @Command(desc = """
            Generate a google sheet of all loan information banks and alliances have submitted
            If no nations are provided, only the loans for this server are returned
            If no loan status is provided, all loans are returned""", viewable = true)
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String getLoanSheet(@Me IMessageIO io, @Me GuildDB db, LoanManager manager, @Default Set<DBNation> nations, @Switch("s") SpreadSheet sheet, @Switch("l") Set<DBLoan.Status> loanStatus) throws GeneralSecurityException, IOException {
        List<DBLoan> loans;
        if (nations == null) {
            loans = manager.getLoansByGuildDB(db);
        } else {
            Set<Integer> nationIds = nations.stream().map(DBNation::getId).collect(Collectors.toSet());
            if (nations.size() >= 1000) {
                loans = manager.getLoansByStatus(loanStatus); // get all loans and then filter
                // remove if not
                loans.removeIf(f -> !f.isAlliance && !nationIds.contains(f.nationOrAllianceId));
                // get all loans and then filter
            } else {
                loans = manager.getLoansByNations(nationIds, loanStatus);
            }
        }
        if (loanStatus != null && !loanStatus.isEmpty()) {
            loans.removeIf(f -> !loanStatus.contains(f.getStatus()));
        }

        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.LOANS_SHEET);
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

        sheet.setHeader(header);

        // sort loans by status then date
        loans.sort(Comparator.comparing(DBLoan::getStatus).thenComparing(DBLoan::getLoanDate));

        double[] total = ResourceType.getBuffer();

        for (DBLoan loan : loans) {
            header.set(0, loan.loanId + "");
            String loanerName = loan.loanerGuildOrAA > Integer.MAX_VALUE ? DiscordUtil.getGuildName(loan.loanerGuildOrAA) : PW.getName(loan.loanerGuildOrAA, true);
            String loanerUrl = loan.loanerGuildOrAA > Integer.MAX_VALUE ? DiscordUtil.getGuildUrl(loan.loanerGuildOrAA) : PW.getAllianceUrl((int) loan.loanerGuildOrAA);
            String loanerMarkup = sheetUrl(loanerName, loanerUrl);
            header.set(1, loanerMarkup);
            header.set(2, sheetUrl(PW.getName(loan.loanerNation, false), PW.getNationUrl(loan.loanerNation)));
            String name = PW.getName(loan.nationOrAllianceId, loan.isAlliance);
            String url = loan.isAlliance ? PW.getAllianceUrl(loan.nationOrAllianceId) : PW.getNationUrl(loan.nationOrAllianceId);
            String receiverMarkup = sheetUrl(name, url);
            header.set(3, receiverMarkup);
            header.set(4, ResourceType.toString(loan.principal));
            header.set(4, ResourceType.toString(loan.paid));
            header.set(5, ResourceType.toString(loan.remaining));
            header.set(6, loan.status.name());
            header.set(7, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(loan.dueDate)));
            header.set(8, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(loan.loanDate)));
            header.set(9, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(loan.date_submitted)));

            if (loan.remaining != null) {
                total = ResourceType.add(total, loan.remaining);
            }

            sheet.addRow(header);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();
        sheet.attach(io.create(), "loans")
                .append("Total on loan: `" + ResourceType.toString(total) + "` worth `$" + MathMan.format(ResourceType.convertedTotal(total)) + "`")
                .send();
        return null;
    }


    @Command(desc = "Add a loan for a nation or alliance")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.ECON}, any = true)
    public String addLoan(LoanManager loanManager, @Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, NationOrAlliance receiver, @Default DBLoan.Status status, @Switch("o") @GuildLoan DBLoan overwriteLoan,
                          @Switch("p") Map<ResourceType, Double> principal, @Switch("r") Map<ResourceType, Double> remaining, @Switch("c") Map<ResourceType, Double> amountPaid,
                          @Switch("d") @Timestamp Long dueDate, @Switch("a") DBAlliance allianceLending, @Switch("f") boolean force) {
        if (allianceLending != null) {
            if (!db.isAllianceId(allianceLending.getId())) {
                throw new IllegalArgumentException("Alliance lending must be from this server. See " + CM.settings_default.registerAlliance.cmd.toSlashMention());
            }
        }
        if (overwriteLoan != null) {
            if (overwriteLoan.isAlliance) {
                if (!db.isAllianceId((int) overwriteLoan.loanerGuildOrAA)) {
                    // Belongs to another alliance
                    throw new IllegalArgumentException("Cannot overwrite loan from another alliance (" + PW.getMarkdownUrl((int) overwriteLoan.loanerGuildOrAA, true) + ")");
                }
                else if (db.getIdLong() != overwriteLoan.loanerGuildOrAA) {
                    throw new IllegalArgumentException("Cannot overwrite loan from another guild (id:" + (int) overwriteLoan.loanerGuildOrAA + ")");
                }
            }
        }

        long loanerId;
        if (overwriteLoan == null) {
            if (allianceLending != null) {
                loanerId = allianceLending.getId();
            } else if (db.isAlliance()) {
                loanerId = db.getAllianceIds().iterator().next();
            } else {
                loanerId = db.getIdLong();
            }
            // set remaining to principal if not there
            if (remaining == null) {
                remaining = principal;
            }
            // set paid to 0
            if (amountPaid == null) {
                amountPaid = new Object2DoubleOpenHashMap<>();
            }
        } else {
            loanerId = overwriteLoan.loanerGuildOrAA;
            if (amountPaid != null && remaining == null) {
                if (overwriteLoan.remaining != null && !ResourceType.isZero(overwriteLoan.remaining)) {
                    remaining = ResourceType.builder()
                            .add(overwriteLoan.remaining)
                            .add(overwriteLoan.paid)
                            .subtract(amountPaid)
                            .maxZero().buildMap();
                }

            } else if (remaining != null && amountPaid == null) {
                amountPaid = ResourceType.builder()
                        .add(overwriteLoan.paid)
                        .add(remaining)
                        .subtract(overwriteLoan.remaining)
                        .maxZero().buildMap();

            }
        }

        if (!force) {

            String title = (overwriteLoan == null ? "Add" : "Update") + " Loan";
            StringBuilder body = new StringBuilder();

            if (loanerId > Integer.MAX_VALUE) {
                body.append("**Lending:** `").append(DiscordUtil.getGuildName(loanerId)).append("`\n");
            } else {
                body.append("**Lender:** ").append(PW.getMarkdownUrl((int) loanerId, true)).append("\n");
            }

            if (status != null) {
                body.append("**Status:** ").append(status.name()).append("\n");
            }
            if (principal != null) {
                body.append("**Principal:** ").append(ResourceType.toString(principal)).append("\n");
            }
            if (remaining != null) {
                body.append("**Remaining:** ").append(ResourceType.toString(remaining)).append("\n");
            }
            if (amountPaid != null) {
                body.append("**Amount Paid:** ").append(ResourceType.toString(amountPaid)).append("\n");
            }

            // If loan already exists to nation from this guild, prompt to update it
            if (overwriteLoan == null) {
                List<DBLoan> foundLoans = loanManager.getLoanByReceiver(db, receiver);
                foundLoans.removeIf(f -> f.status == DBLoan.Status.CLOSED);
                if (!foundLoans.isEmpty()) {
                    body.append("**A loan already exists to ")
                            .append(receiver.isAlliance() ? "alliance " : "nation ")
                            .append(PW.getMarkdownUrl(receiver.getId(), receiver.isAlliance()))
                            .append(".**\nConsider updating or closing these: " + CM.report.loan.update.cmd.toSlashMention() + " " + CM.report.loan.remove.cmd.toSlashMention() + "\n");
                    for (DBLoan loan : foundLoans) {
                        // Id, status, date, remaining
                        body.append("- " + loan.getLineString(false, false) + "\n");
                    }
                }

                if (principal == null) {
                    body.append("**Warning:** No `principal` amount set. It is recommended to set the initial loan amount\n");
                }
                if (dueDate == null) {
                    dueDate = TimeUtil.getTimeFromTurn(TimeUtil.getTurn()) + TimeUnit.DAYS.toMillis(7);
                    body.append("**Warning:** No `dueDate` set. Defaulting to 7 days\n");
                }
                if (status == null) {
                    status = DBLoan.Status.OPEN;
                    body.append("**Warning:** No `status` set. Defaulting to `OPEN`\n");
                }
            }

            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }


        boolean isUpdate = overwriteLoan == null;
        if (overwriteLoan == null) {
            if (dueDate == null && (overwriteLoan == null || overwriteLoan.status == DBLoan.Status.OPEN || overwriteLoan.status == DBLoan.Status.EXTENDED)) {
                dueDate = TimeUtil.getTimeFromTurn(TimeUtil.getTurn()) + TimeUnit.DAYS.toMillis(7);
            }
            if (principal == null) {
                principal = new Object2DoubleOpenHashMap<>();
            }
            if (remaining == null) {
                remaining = new Object2DoubleOpenHashMap<>();
            }

            overwriteLoan = new DBLoan(
                    loanerId,
                    me.getNation_id(),
                    receiver.getId(),
                    receiver.isAlliance(),
                    ResourceType.resourcesToArray(principal),
                    ResourceType.resourcesToArray(remaining),
                    ResourceType.resourcesToArray(amountPaid),
                    status,
                    dueDate,
                    System.currentTimeMillis(),
                    System.currentTimeMillis()
            );
        }

        StringBuilder response = new StringBuilder("Done! " + (isUpdate ? "Updated" : "Added") + "1 loan");

        // If there are loans updated >1w ago, prompt to update them
        List<DBLoan> existing = loanManager.getLoansByGuildDB(db);
        long now = System.currentTimeMillis();
        existing.removeIf(f -> f.status == DBLoan.Status.DEFAULTED || f.status == DBLoan.Status.CLOSED || f.dueDate >= now);
        // The following loans are >1w old, would you like to update them?
        if (!existing.isEmpty()) {
            response.append("\n**Warning:** There are loans >1w old that have not been updated. Consider updating them:\n");
            for (DBLoan loan : existing) {
                response.append("- " + loan.getLineString(true, false) + "\n");
            }
        }

        response.append("\nTo view all loans, see: " + CM.report.loan.sheet.cmd.toSlashMention() + "\n" +
                "To bulk update loans, see: " + CM.report.loan.upload.cmd.toSlashMention());

        if (isUpdate) {
            loanManager.updateLoan(overwriteLoan);
        } else {
            loanManager.addLoans(List.of(overwriteLoan));
        }

        return response.toString();
    }

    @Command(desc = "Update a loan for a nation or alliance\n" +
            "If no other arguments are provided, only the last updated date will be set")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.ECON}, any = true)
    public String updateLoan(LoanManager loanManager, @Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, @Me DBNation me,
                             @GuildLoan DBLoan loan,
                             @Switch("p") Map<ResourceType, Double> principal,
                             @Switch("r") Map<ResourceType, Double> remaining,
                             @Switch("c") Map<ResourceType, Double> amountPaid,
                             @Switch("d") @Timestamp Long dueDate,
                             @Switch("f") boolean force) {
        NationOrAlliance receiver = null;
        if (loan.isAlliance) {
            receiver = DBAlliance.getOrCreate(loan.nationOrAllianceId);
        } else {
            receiver = DBNation.getById(loan.nationOrAllianceId);
            if (receiver == null) {
                DBNation nation = new SimpleDBNation(new DBNationData());
                nation.setNation_id(loan.nationOrAllianceId);
                receiver = nation;
            }
        }
        return addLoan(loanManager, command, io, db, me, receiver, loan.status, loan, principal, remaining, amountPaid, dueDate, null, force);
    }

    // delete loan
    @Command(desc = "Delete a loan for a nation or alliance\n" +
            "Note: To delete all loans use the loan purge command")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.ECON}, any = true)
    public String deleteLoan(LoanManager loanManager, @Me User author, @Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, @GuildLoan DBLoan loan, @Switch("f") boolean force) {
        if (!loan.hasPermission(db, me, author)) {
            return "You do not have permission to delete this loan. Loan was created by: " + loan.getLoanerString();
        }
        if (!force) {
            String title = "Confirm delete loan";
            String body = loan.getLineString(true, true);
            io.create().confirmation(title, body, command).send();
            return null;
        }
        // ensure has permission
        loanManager.deleteLoans(List.of(loan));
        return "Loan deleted";
    }

    @Command(desc = "Delete all loan information")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.ECON}, any = true)
    public String purgeLoans(LoanManager loanManager, @Me JSONObject command, @Me User author, @Me IMessageIO io, @Me GuildDB db, @Arg("Purge all loans created by this guild or alliance id") @Default Long guildOrAllianceId, @Switch("f") boolean force) {
        if (guildOrAllianceId != null && !Roles.ADMIN.hasOnRoot(author)) {
            // check permission
            if (guildOrAllianceId != db.getIdLong() && (guildOrAllianceId > Integer.MAX_VALUE || !db.isAllianceId(guildOrAllianceId.intValue()))) {
                return "You do not have permission to purge loans for this guild/alliance";
            }
        }
        List<DBLoan> loans;
        if (guildOrAllianceId == null) {
            loans = loanManager.getLoansByGuildDB(db);
        } else {
            loans = loanManager.getLoansByGuildOrAA(Collections.singleton(guildOrAllianceId), null);
        }
        if (loans.isEmpty()) {
            return "No loans found. Create one with " + CM.report.loan.add.cmd.toSlashMention();
        }
        if (!force) {
            String title = "Confirm delete all " + loans.size() + " loans";
            StringBuilder body = new StringBuilder();
            body.append("**Note:** It is recommended to create a copy first\n" +
                    "See: " + CM.report.loan.sheet.cmd.toSlashMention() + "\n\n");

            if (guildOrAllianceId == null) {
                body.append("Deleting: All Loans from this guild/alliance (" + db.getGuild().toString() + ")");
            } else if (guildOrAllianceId > Integer.MAX_VALUE) {
                body.append("Deleting: All Loans from guild: " + DiscordUtil.getGuildName(guildOrAllianceId) + " (" + guildOrAllianceId + ")");
            } else {
                body.append("Deleting: All Loans from alliance: " + PW.getMarkdownUrl(guildOrAllianceId.intValue(), true));
            }

            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        loanManager.deleteLoans(loans);
        return "All loans deleted";
    }


    @Command(desc = "Mark all active loans by this guild as up to date\n" +
            "It is useful for loan reporting to remain accurate")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.ECON}, any = true)
    public String markAllLoansAsUpdated(LoanManager loanManager, @Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, @Default Set<DBLoan.Status> loanStatus, @Switch("f") boolean force) {
        List<DBLoan> loans = loanManager.getLoansByGuildDB(db);
        if (loanStatus != null) {
            loans.removeIf(f -> !loanStatus.contains(f.status));
        }
        if (loans.isEmpty()) {
            return "No loans found";
        }
        if (!force) {
            String title = "Confirm mark " + loans.size() + " loans as up to date";
            StringBuilder body = new StringBuilder();
            for (DBLoan loan : loans) {
                body.append("- " + loan.getLineString(true, false)).append("\n");
            }
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        for (DBLoan loan : loans) {
            loan.date_submitted = System.currentTimeMillis();
        }
        loanManager.updateLoans(loans);
        return "Updated " + loans.size() + " loans";

    }

    @Command(desc = """
            Import loan report data from a google sheet
            Expects the columns: Receiver, Principal, Remaining, Status, Due Date, Loan Date, Paid, Interest
            This is not affect member balances and is solely for sharing information with the public""")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.ECON}, any = true)
    public String importLoans(LoanManager loanManager, @Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, SpreadSheet sheet, @Default DBLoan.Status defaultStatus, @Switch("o") boolean overwriteLoans, @Switch("m") boolean overwriteSameNation, @Switch("a") boolean addLoans) throws ParseException {
        List<List<Object>> rows = sheet.fetchAll(null);
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
            if (receiverStr.contains("/alliance") || receiverStr.toLowerCase().contains("aa:") || receiverStr.toLowerCase().contains("alliance:")) {
                receiverId = PW.parseAllianceId(receiverStr);
                isRecieverAA = true;
            } else {
                receiverId = DiscordUtil.parseNation(receiverStr, true, true, db.getGuild()).getId();
                isRecieverAA = false;
            }

            Map<ResourceType, Double> principal = ResourceType.parseResources(principalStr);
            Map<ResourceType, Double> remaining = remainingStr == null ? null : ResourceType.parseResources(remainingStr);
            Map<ResourceType, Double> paid = paidStr == null ? null : ResourceType.parseResources(paidStr);
            Map<ResourceType, Double> interest = interestStr == null ? null : ResourceType.parseResources(interestStr);

            if (paid == null && remaining != null) {
                double[] principalArr = resourcesToArray(principal);
                double[] remainingArr = resourcesToArray(remaining);
                double[] interestArr = interest == null ? null : resourcesToArray(interest);
                if (interest != null) {
                    // paid = principal - remaining + interest
                    paid = ResourceType.resourcesToMap(ResourceType.add(subtract(principalArr, remainingArr), interestArr));
                } else {
                    // paid = principal - remaining
                    paid = ResourceType.resourcesToMap(ResourceType.max(ResourceType.getBuffer(), subtract(principalArr, remainingArr)));
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
                    resourcesToArray(principal),
                    paid == null ? ResourceType.getBuffer() : resourcesToArray(paid),
                    remaining == null ? ResourceType.getBuffer() : resourcesToArray(remaining),
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


        if (!overwriteLoans && !addLoans && !overwriteSameNation) {
            String title = "Add or Overwrite loans";
            StringBuilder body = new StringBuilder();
            body.append(loans.size() + " loans will be added to the database\n");

            List<DBLoan> existing = loanManager.getLoansByGuildOrAlliance(loanerGuildOrAA);
            body.append("There are currently " + existing.size() + " loans already in the database\n");
            body.append("\nPress `Add` to add the loans to the database\n");
            body.append("Press `Same` to overwrite loans with the same receiver\n");
            body.append("Press `Overwrite` to overwrite the existing loans with the new loans\n");
            body.append("Press `View` to view all current loans\n");

            io.create().embed(title, body.toString())
                    .confirmation(command, "addLoans", "Add")
                    .confirmation(command, "overwriteSameNation", "Same")
                    .confirmation(command, "overwriteLoans", "Overwrite")
                    .commandButton(CommandBehavior.DELETE_PRESSED_BUTTON, CM.report.sheet.generate.cmd, "View")
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
                "See: " + CM.report.loan.sheet.cmd.toSlashMention();
    }

    @Command(desc = "Report a nation or user's game behavior to the bot for things such as fraud\n" +
            "Note: Submitting spam or maliciously false reports may result in being locked out of reporting commands")
//    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF, Roles.ECON_STAFF}, any = true)
    public String createReport(@Me DBNation me, @Me User author, @Me GuildDB db, @Me IMessageIO io, @Me JSONObject command,
                         ReportManager reportManager,
                         ReportManager.ReportType type,
                         @Arg("Description of report") @TextArea String message,
                         @Default @Arg("Nation to report") DBNation nation,
                         @Default @Arg("Discord user to report") Long discord_user_id,
                         @Arg("Image evidence of report") @Switch("i") String imageEvidenceUrl,
                         @Arg("Link to relevant forum post") @Switch("p") String forum_post,
                         @Arg("Link to relevant news post") @Switch("m") String news_post,
                               @Switch("u") ReportManager.Report updateReport,
                               @Switch("f") boolean force) {
        Map.Entry<String, Long> ban = reportManager.getBan(me);
        if (ban != null) {
            return "You were banned from reporting on " + DiscordUtil.timestamp(ban.getValue(), null) + " for `" + ban.getKey() + "`";
        }

        Integer nationId = nation == null ? null : nation.getId();
        int reporterNationId = me.getId();
        long reporterUserId = author.getIdLong();
        long reporterGuildId = db.getIdLong();
        int reporterAlliance = me.getAlliance_id();

        ReportManager.Report existing = null;
        if (updateReport != null) {
            existing = updateReport;
            if (!existing.hasPermission(me, author, db)) {
                return "You do not have permission to edit this report: `#" + updateReport.reportId +
                        "` (owned by nation:" + PW.getName(existing.reporterNationId, false) + ")\n" +
                        "To add a comment: " + CM.report.comment.add.cmd.toSlashMention();
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
            if (forum_post == null && existing.forumUrl != null && !existing.forumUrl.isEmpty()) {
                forum_post = existing.forumUrl;
            }
            if (news_post == null && existing.newsUrl != null && !existing.newsUrl.isEmpty()) {
                news_post = existing.newsUrl;
            }
            // Keep reporter info if updating
            reporterNationId = existing.reporterNationId;
            reporterUserId = existing.reporterDiscordId;
            reporterGuildId = existing.reporterGuildId;
            reporterAlliance = existing.reporterAllianceId;
        }

        if (nationId == null && discord_user_id == null) {
            // say must provide one of either
            // post link for how to get discord id <url>
            return """
                    You must provide either a `nation` or a `discord_user_id` to report
                    To get a discord user id, right click on the user and select `Copy ID`
                    See: <https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID->""";
        }
//        if (forum_post == null && news_post == null) {
//            return "No argument provided\n" + Messages.FORUM_NEWS_ERROR;
//        }

        // At least one forum post or news report must be attached
        Set<Long> supportedServers = new LongOpenHashSet(Arrays.asList(
                869424139037990912L, // Ducc News Network
                446601982564892672L, // Royal Orbis News
                821587932384067584L, // Orbis Crowned News
                827905979575959629L, // Very Good Media
                353730979816407052L, // Pirate Island Times
                1022224780751011860L, // Micro Minute
                580481635645128745L, // Thalmoria
                1139041525817409539L, // Orbis Business & Innovation Forum
                756580822739320883L // Black Label Media
        ));


        if (forum_post != null && !forum_post.startsWith("https://forum.politicsandwar.com/index.php?/topic/")) {
            return "Forum post (`" + forum_post + "`) must be on domain `https://forum.politicsandwar.com/index.php?/topic/`\n" + Messages.FORUM_NEWS_ERROR;
        }
        if (news_post != null) {
            // https://discord.com/channels/SERVER_ID/992205932006228041/1073856622545346641
            // remove the https://discord.com/channels/ part
            news_post = news_post.replace("ptb.", "");
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

        if (discord_user_id != null && nationId != null) {
            PNWUser nationUser = Locutus.imp().getDiscordDB().getUserFromNationId(nationId);
            DBNation userNation = DiscordUtil.getNation(discord_user_id);
            if (nationUser != null && nationUser.getDiscordId() != discord_user_id) {
                throw new IllegalArgumentException("The nation you provided is already linked to a different discord user `" + DiscordUtil.getUserName(nationUser.getDiscordId()) + "`.\n" +
                        "Please create a separate report for this discord user.");
            }
            if (userNation != null && userNation.getId() != nationId) {
                throw new IllegalArgumentException("The discord user you provided is already linked to a different nation `" + userNation.getName() + "`.\n" +
                        "Please create a separate report for this nation.");
            }
        }

        if (discord_user_id == null) {
            PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(nationId);
            if (user != null) {
                discord_user_id = user.getDiscordId();
            }
        }
        if (nationId == null) {
            DBNation nation2 = DiscordUtil.getNation(discord_user_id);
            nationId = nation2 != null ? nation2.getId() : null;
        }

        if (!force) {
            String title = (existing != null ? "Update " : "Report") +
                    type + " report by " +
                    DiscordUtil.getUserName(reporterUserId) + " | " + PW.getName(reporterNationId, false);

            StringBuilder body = new StringBuilder();
            if (existing != null && existing.approved) {
                body.append("`This report will lose its approved status if you update it.`\n");
            }
            if (nationId != null) {
                body.append("Nation: " + PW.getMarkdownUrl(nationId, false) + "\n");
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
                body.append("To add a comment: " + CM.report.comment.add.cmd.toSlashMention() + "\n");
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

        List<String> imageUrls = new ArrayList<>();
        if (imageEvidenceUrl != null) {
            // split by space
            String[] split = imageEvidenceUrl.split(" ");
            for (String imageUrl : split) {
                imageUrls.add(imageUrl);
                // ensure imageEvidenceUrl is discord image url
                String imageOcr = ImageUtil.getText(imageUrl);
                if (imageOcr != null) {
                    message += "\nScreenshot transcript:\n```\n" + imageOcr + "\n```";
                }
            }
        } else if (updateReport != null) {
            imageUrls = existing.imageUrls;
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
            imageUrls,
            forum_post == null ? "" : forum_post,
            news_post == null ? "" : news_post,
            System.currentTimeMillis(),
            Roles.INTERNAL_AFFAIRS_STAFF.hasOnRoot(author));

        if (existing != null) {
            report.reportId = existing.reportId;
        }

        reportManager.saveReport(report);

        ReportManager.Report finalExisting = existing;
        AlertUtil.forEachChannel(f -> true, REPORT_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB db) {
                String title = (finalExisting != null ? "Updating" : "New") + " " + report.getTitle();
                String body = report.toMarkdown(false);
                new DiscordChannelIO(channel).create().embed(title, body).sendWhenFree();
            }
        });

        return "Report " + (existing == null ? "created" : "updated") + " with id `" + report.reportId + "`\n" +
                "See: " + CM.report.show.cmd.report(report.reportId + "").toSlashCommand(true);
    }

    @Command(desc = "Remove a report of a nation or user")
    public String removeReport(ReportManager reportManager, @Me JSONObject command, @Me IMessageIO io, @Me DBNation me, @Me User author, @Me GuildDB db, @ReportPerms ReportManager.Report report, @Switch("f") boolean force) {
        if (!report.hasPermission(me, author, db)) {
            return "You do not have permission to remove this report: `#" + report.reportId +
                    "` (owned by nation:" + PW.getName(report.reporterNationId, false) + ")\n" +
                    "To add a comment: " + CM.report.comment.add.cmd.toSlashMention();
        }
        if (!force) {
            String title = "Remove " + report.type + " report";
            StringBuilder body = new StringBuilder(report.toMarkdown(true));
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        reportManager.deleteReport(report.reportId);
        return "Report removed.";
    }

    @Command(desc = "Remove a comment on a report")
    public String removeComment(ReportManager reportManager, @Me JSONObject command, @Me IMessageIO io, @Me DBNation me, @Me User author, @ReportPerms ReportManager.Report report, @Default DBNation nationCommenting, @Switch("f") boolean force) {
        if (nationCommenting == null) nationCommenting = me;
        if (nationCommenting.getNation_id() != me.getNation_id() && !Roles.INTERNAL_AFFAIRS_STAFF.hasOnRoot(author)) {
            return "You do not have permission to remove another nation's comment on report: `#" + report.reportId +
                    "` (owned by nation:" + PW.getName(nationCommenting.getNation_id(), false) + ")\n" +
                    "To add a comment: " + CM.report.comment.add.cmd.toSlashMention();
        }
        ReportManager.Comment comment = reportManager.loadCommentsByReportNation(report.reportId, nationCommenting.getNation_id());
        if (comment == null) {
            return "No comment found for nation: " + PW.getName(nationCommenting.getNation_id(), false) + " on report: `#" + report.reportId + "`";
        }

        if (!force) {
            String title = "Remove comment by nation:" + PW.getName(comment.nationId, false);
            StringBuilder body = new StringBuilder();
            body.append("See report: #" + report.reportId + " | " + CM.report.show.cmd.toSlashMention() + "\n");
            body.append(comment.toMarkdown());
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        reportManager.deleteComment(report.reportId, comment.nationId);
        return "Comment removed.";
    }

    @Command(desc = "Approve a report for a nation or user")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERNAL_AFFAIRS}, root = true, any = true)
    public String approveReport(ReportManager reportManager, @Me JSONObject command, @Me IMessageIO io, @ReportPerms ReportManager.Report report, @Switch("f") boolean force) {
        if (report.approved) {
            return "Report #" + report.reportId + " is already approved.";
        }
        if (!force) {
            String title = "Verify " + report.type + " report";
            StringBuilder body = new StringBuilder(report.toMarkdown(false));
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        report.approved = true;
        reportManager.saveReport(report);
        return "Verified report #" + report.reportId;
    }

    @Command(desc = "Add a short comment to a report")
    public String comment(ReportManager reportManager, @Me JSONObject command, @Me IMessageIO io, @Me DBNation me, @Me User author, ReportManager.Report report, String comment, @Switch("f") boolean force) {
        Map.Entry<String, Long> ban = reportManager.getBan(me);
        if (ban != null) {
            return "You were banned from reporting on " + DiscordUtil.timestamp(ban.getValue(), null) + " for `" + ban.getKey() + "`";
        }
        ReportManager.Comment existing = reportManager.loadCommentsByReportNation(report.reportId, me.getNation_id());

        if (!force) {
            String title = "Comment on " + report.getTitle();
            StringBuilder body = new StringBuilder(report.toMarkdown(false));

            String bodyString = "Your comment\n```\n" + comment + "\n```\n" + body.toString();
            if (existing != null) {
                bodyString = "Overwrite your existing comment:\n```\n" + existing.comment + "\n```\n" + bodyString;
            }

            io.create().confirmation(title, bodyString, command).send();
            return null;
        }

        boolean isNewComment = existing == null;

        existing = new ReportManager.Comment(report.reportId, me.getNation_id(), author.getIdLong(), comment, System.currentTimeMillis());

        reportManager.saveComment(existing);

        ReportManager.Comment finalExisting = existing;
        AlertUtil.forEachChannel(f -> true, REPORT_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB db) {
                String title = (isNewComment ? "New " : "Updating ") + " comment";
                String body = "Report: #" + report.reportId + " " + report.getTitle() + "\n\n" + finalExisting.toMarkdown();
                new DiscordChannelIO(channel).create().embed(title, body).sendWhenFree();
            }
        });

        return "Added comment to #" + report.reportId +
                "\nSee: " + CM.report.show.cmd.toSlashMention();
    }

    @Command(desc = "Mass delete reports about or submitted by a user or nation")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERNAL_AFFAIRS}, root = true, any = true)
    public String purgeReports(@Me IMessageIO io, @Me JSONObject command, ReportManager reportManager, @Switch("n") Integer nationIdReported, @Switch("d") Long userIdReported, @Switch("i") Integer reportingNation, @Switch("u") Long reportingUser, @Switch("f") boolean force) {
        List<ReportManager.Report> reports = reportManager.loadReports(nationIdReported, userIdReported, reportingNation, reportingUser);
        if (reports.isEmpty()) {
            return "No reports found";
        }
        if (!force) {
            String title = "Purge " + reports.size() + " reports";
            StringBuilder body = new StringBuilder();
            // append each variable used
            if (nationIdReported != null) {
                DBNation nation = DBNation.getById(nationIdReported);
                String aaName = nation == null ? "None" : nation.getAllianceUrlMarkup();
                body.append("Nation reported: ").append(PW.getMarkdownUrl(nationIdReported, false) + " | " + aaName).append("\n");
            }
            if (userIdReported != null) {
                body.append("User reported: ").append("<@" + userIdReported + ">").append("\n");
            }
            if (reportingNation != null) {
                DBNation nation = DBNation.getById(reportingNation);
                String aaName = nation == null ? "None" : nation.getAllianceUrlMarkup();
                body.append("Reporting nation: ").append(PW.getMarkdownUrl(reportingNation, false) + " | " + aaName).append("\n");
            }
            if (reportingUser != null) {
                body.append("Reporting user: ").append("<@" + reportingUser + ">").append("\n");
            }
            body.append("\n");
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        for (ReportManager.Report report : reports) {
            reportManager.deleteReport(report.reportId);
        }
        return "Deleted " + reports.size() + " reports";
    }

    @Command(desc = "Mass delete reports about or submitted by a user or nation")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERNAL_AFFAIRS}, root = true, any = true)
    public String purgeComments(@Me IMessageIO io, @Me JSONObject command, ReportManager reportManager, @Switch("r") ReportManager.Report report, @Switch("i") Integer nation_id, @Switch("u") Long discord_id, @Switch("f") boolean force) {
        if (nation_id != null && discord_id != null) {
            return "Cannot specify both a nation and a user. Pick one";
        }
        if (report != null && (nation_id != null || discord_id != null)) {
            return "Cannot specify both a report and a nation/user. Pick one";
        }
        List<ReportManager.Comment> comments;
        if (report != null) {
            comments = reportManager.loadCommentsByReport(report.reportId);
        } else if (nation_id != null) {
            comments = reportManager.loadCommentsByNation(nation_id);
        } else if (discord_id != null) {
            comments = reportManager.loadCommentsByUser(discord_id);
        } else {
            return "Please specify either `report` or `nation_id` or `discord_id`";
        }
        if (comments.isEmpty()) {
            return "No comments found";
        }
        if (!force) {
            String title = "Purge " + comments.size() + " comments";
            StringBuilder body = new StringBuilder();
            // append each variable used
            if (nation_id != null) {
                DBNation nation = DBNation.getById(nation_id);
                String aaName = nation == null ? "None" : nation.getAllianceUrlMarkup();
                body.append("Nation commenting: ").append(PW.getMarkdownUrl(nation_id, false) + " | " + aaName).append("\n");
            }
            if (discord_id != null) {
                body.append("User commenting: ").append("<@" + discord_id + ">").append("\n");
            }
            body.append("\n");
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        for (ReportManager.Comment comment : comments) {
            reportManager.deleteComment(comment.report_id, comment.nationId);
        }
        return "Deleted " + comments.size() + " comments";
    }

    @Command(desc = """
            Ban a nation from submitting new reports
            Reports they have already submitted will remain
            Use the purge command to delete existing reports""")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERNAL_AFFAIRS}, root = true, any = true)
    public String ban(ReportManager reportManager, @Me IMessageIO io, @Me JSONObject command, DBNation nation, @Timestamp long timestamp, String reason, @Switch("f") boolean force) throws IOException {
        if (!force) {
            String title = "Ban " + nation.getName() + " from reporting";
            StringBuilder body = new StringBuilder();
            body.append("Nation: ").append(nation.getNationUrlMarkup() + " | " + nation.getAllianceUrlMarkup()).append("\n");
            body.append("Reason:\n```\n").append(reason).append("\n```\n");
            body.append("Until: ").append(DiscordUtil.timestamp(timestamp, null)).append("\n");

            Map.Entry<String, Long> ban = reportManager.getBan(nation);
            if (ban != null) {
                body.append("Existing ban:\n```\n").append(ban.getKey()).append("\n```\nuntil ").append(DiscordUtil.timestamp(ban.getValue(), null)).append("\n");
            }

            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        reportManager.setBanned(nation, timestamp, reason);
        return "Banned " + nation.getName() + " from submitting nation reports";
    }

    @Command(desc = "Remove a ban on a nation submitting new reports")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERNAL_AFFAIRS}, root = true, any = true)
    public String unban(ReportManager reportManager, @Me IMessageIO io, @Me JSONObject command, DBNation nation, @Switch("f") boolean force) throws IOException {
        Map.Entry<String, Long> ban = reportManager.getBan(nation);
        if (ban == null) {
            return "No ban found for " + nation.getName();
        }
        if (!force) {
            String title = "Unban " + nation.getName() + " from reporting";
            StringBuilder body = new StringBuilder();
            body.append("Nation: ").append(nation.getNationUrlMarkup() + " | " + nation.getAllianceUrlMarkup()).append("\n");
            if (ban != null) {
                body.append("Existing ban:\n```\n").append(ban.getKey()).append("\n```\nuntil ").append(DiscordUtil.timestamp(ban.getValue(), null)).append("\n");
            }
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        nation.deleteMeta(NationMeta.REPORT_BAN);
        return "Unbanned " + nation.getName() + " from submitting nation reports";
    }

//    @Command
//    public String viewReports(ReportManager reportManager, @Switch("n") DBNation nation, @Switch("d") Long discordId, @Switch("u") User discordUser) {
//        if (discordUser != null && discordId != null) {
//            // can only do one, throw exception, pick one
//        }
//
//        if (nation == null && discordUser == null && discordId == null) {
//            // must do at least one, pick,. throw error
//        }
//        Integer nationId = nation == null ? null : nation.getNation_id();
//        if (discordUser != null) discordId = discordUser.getIdLong();
//        List<ReportManager.Report> reports = reportManager.loadReportsByNationOrUser(nationId, discordId);
//        if (reports.isEmpty()) {
//            return "No reports found";
//        }
//
//        StringBuilder response = new StringBuilder();
//        // append each report incl comments
//
//        return null;
//    }

    // report search
    @Command(desc = "List all reports about or submitted by a nation or user", viewable = true)
    public String searchReports(ReportManager reportManager, @Switch("n") Integer nationIdReported, @Switch("d") Long userIdReported, @Switch("i") Integer reportingNation, @Switch("u") Long reportingUser) {
        List<ReportManager.Report> reports = reportManager.loadReports(nationIdReported, userIdReported, reportingNation, reportingUser);
        // list reports matching
        if (reports.isEmpty()) {
            return "No reports found";
        }
        StringBuilder response = new StringBuilder();
        response.append("# " + reports.size()).append(" reports found:\n");
        for (ReportManager.Report report : reports) {
            response.append("### ").append(report.toMarkdown(false)).append("\n");
        }
        return response.toString();
    }

    // report show, incl comments
    @Command(desc = "View a report and its comments", viewable = true)
    public String showReport(ReportManager.Report report) {
        return "### " + report.toMarkdown(true);
    }

    @Command(desc = "Show an analysis of a nation's risk factors including: Reports, loans, discord & game bans, money trades and proximity with blacklisted nations, multi info, user account age, inactivity predictors")
    public String riskFactors(@Me IMessageIO io, ReportManager reportManager, LoanManager loanManager, DBNation nation) {
        StringBuilder response = new StringBuilder();
        // Nation | Alliance (#AA Rank) | Position
        response.append(nation.getNationUrlMarkup() + " | " + nation.getAllianceUrlMarkup());
        if (nation.getAlliance_id() > 0) {
            DBAlliance alliance = nation.getAlliance();
            response.append(" (#").append(alliance.getRank()).append(")");
            response.append(" " + nation.getPositionEnum().name());
        }

        response.append("\n");
        //- loot factor risks (activity)
        response.append("Inactivity factors:\n");
        LoginFactorResult factors = DBNation.getLoginFactorPercents(nation);
        for (Map.Entry<LoginFactor, Double> entry : factors.getResult().entrySet()) {
            LoginFactor factor = entry.getKey();
            double percent = entry.getValue();
            response.append("- " + factor.name + "=" + factor.toString(factor.get(nation)) + ": " + MathMan.format(100 - percent) + "%\n");
        }

        //- Active loans (share_loan_info, or shared sheets)
        List<DBLoan> loans = loanManager.getLoansByNation(nation.getId());

        // remove completed loans
        loans.removeIf(loan -> loan.status == DBLoan.Status.CLOSED);
        if (!loans.isEmpty()) {
            Map<DBLoan.Status, List<DBLoan>> loansByStatus = new HashMap<>();
            for (DBLoan loan : loans) {
                loansByStatus.computeIfAbsent(loan.status, k -> new ArrayList<>()).add(loan);
            }
            for (DBLoan.Status status : DBLoan.Status.values()) {
                List<DBLoan> loansForStatus = loansByStatus.get(status);
                if (loansForStatus == null) continue;
                response.append(status.name() + " loans:\n");
                for (DBLoan loan : loansForStatus) {
                    double amt = ResourceType.convertedTotal(loan.remaining);
                    String amtStr = MathMan.format(amt);
                    String loanerStr = loan.loanerGuildOrAA > Integer.MAX_VALUE ? DiscordUtil.getGuildName(loan.loanerGuildOrAA) : PW.getMarkdownUrl((int) loan.loanerGuildOrAA, true);
                    String dateLoaned = DiscordUtil.timestamp(loan.loanDate, null);
                    String dateSubmitted = DiscordUtil.timestamp(loan.date_submitted, null);
                    response.append("- ~$" + amtStr + " from " + loanerStr + " on " + dateLoaned + " (submitted: " + dateSubmitted + ")\n");
                }
            }
        }

        // Get reports by same nation/discord
        List<ReportManager.Report> reports = reportManager.loadReports(nation.getId(), nation.getUserId(), null, null);
        if (reports.size() > 0) {
            int approved = (int) reports.stream().filter(report -> report.approved).count();
            int pending = reports.size() - approved;
            response.append("Reports: " + reports.size() + " (" + approved + " approved, " + pending + " pending) " +
                    CM.report.search.cmd.toSlashMention() + "\n");
        }


        //- server bans
        User user = nation.getUser();
        if (user == null) {
            response.append("`Nation is not registered to a discord user`\n");
        } else {
            //- account age
            long dateCreated = user.getTimeCreated().toEpochSecond() * 1000L;
            response.append("Discord created: " + DiscordUtil.timestamp(dateCreated, null) + "\n");

            List<DiscordBan> discordBans = Locutus.imp().getDiscordDB().getBans(user.getIdLong());
            if (!discordBans.isEmpty()) {
                response.append("Discord bans:\n");
                for (DiscordBan ban : discordBans) {
                    response.append("- " + DiscordUtil.getGuildName(ban.server) + " on " + DiscordUtil.timestamp(ban.date, null) + "\n");
                }
            }
        }
        // Get bans
        List<DBBan> bans = nation.getBans();
        List<DBBan> currentIpBans = new ArrayList<>();
        List<DBBan> historicalIpBans = new ArrayList<>();

        List<Integer> currentSharedNetwork = new ArrayList<>();
        List<Integer> historicalSharedNetwork = new ArrayList<>();

        // Get same network bans
        BigInteger latestUuid = Locutus.imp().getDiscordDB().getLatestUuid(nation.getNation_id());
        if (latestUuid != null) {
            Set<Integer> nations = Locutus.imp().getDiscordDB().getMultis(latestUuid);
            for (int nationId : nations) {
                BigInteger otherUuid = Locutus.imp().getDiscordDB().getLatestUuid(nation.getNation_id());
                List<DBBan> nationBans = Locutus.imp().getNationDB().getBansForNation(nationId);
                if (otherUuid == null || !otherUuid.equals(latestUuid)) {
                    historicalIpBans.addAll(nationBans);
                    historicalSharedNetwork.add(nationId);
                } else {
                    currentIpBans.addAll(nationBans);
                    currentSharedNetwork.add(nationId);
                }
            }
        }
        currentSharedNetwork.remove((Integer) nation.getNation_id());
        historicalSharedNetwork.remove((Integer) nation.getNation_id());
        Set<DBNation> sharedNetworkActive = currentSharedNetwork.stream().map(DBNation::getById).filter(Objects::nonNull).collect(Collectors.toSet());

        // add bans to response
        if (!bans.isEmpty()) {
            response.append("Game bans:\n");
            for (DBBan ban : bans) {
                response.append("- " + (ban.isExpired() ? "[Expired]" : "") +
                        DiscordUtil.timestamp(ban.date, null) + " " +
                        ban.reason.split("\n")[0] + "\n");
            }
        }
        if (!currentIpBans.isEmpty()) {
            response.append("Current network bans:\n");
            for (DBBan ban : currentIpBans) {
                response.append("- nation:" + ban.nation_id + "\n");
            }
        }
        if (!historicalIpBans.isEmpty()) {
            response.append("Historical network bans (unreliable):\n");
            for (DBBan ban : historicalIpBans) {
                response.append("- nation:" + ban.nation_id + "\n");
            }
        }

        if (!bans.isEmpty() || !currentIpBans.isEmpty() || !historicalIpBans.isEmpty()) {
            response.append("See " + CM.nation.list.bans.cmd.toSlashMention() + "\n");
        }

        // Add multi information
        if (!sharedNetworkActive.isEmpty()) {
            response.append("Sharing network with " + sharedNetworkActive.size() + " active nation" + (sharedNetworkActive.size() == 1 ? "" : "s") + ":\n");
            for (DBNation other : sharedNetworkActive) {
                response.append("- " + other.getNationUrlMarkup() + "\n");
            }
        }
        // Alliance history
        Map<Integer, List<Map.Entry<Long, Long>>> history = reportManager.getCachedHistory(nation.getNation_id());
//        Map<Integer, List<Map.Entry<Long, Long>>> durations = reportManager.getAllianceDurationMap(history);
//        // Member in the following alliances
//        response.append("Member in " + durations.size() + " alliance" + (durations.size() == 1 ? "" : "s") + ":\n");
//        for (Map.Entry<Integer, List<Map.Entry<Long, Long>>> entry : durations.entrySet()) {
//            int aaId = entry.getKey();
//            List<Map.Entry<Long, Long>> aaHistory = entry.getValue();
//            String aaName = PW.getMarkdownUrl(aaId, true);
//            long duration = 0;
//            for (Map.Entry<Long, Long> aaEntry : aaHistory) {
//                duration += aaEntry.getValue() - aaEntry.getKey();
//            }
//            response.append("- " + aaName + ": " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, duration) + "\n");
//        }

        //- proximity to banned or reported individuals (alliance history, trades, bank transfers)
        // Map of Nation id -> Alliance -> duration
        Map<Integer, Long> sameAAProximity = reportManager.getBlackListProximity(nation, history);
        Map<Integer, Set<ReportManager.ReportType>> reportTypes = reportManager.getApprovedReportTypesByNation(sameAAProximity.keySet());
        if (!sameAAProximity.isEmpty()) {
            response.append("Proximity to reported individuals:\n");
            for (Map.Entry<Integer, Long> entry : sameAAProximity.entrySet()) {
                int nationId = entry.getKey();
                String nationName = PW.getMarkdownUrl(nationId, false);
                String reportListStr = reportTypes.get(nationId).stream().map(ReportManager.ReportType::name).collect(Collectors.joining(","));
                response.append("- " + nationName + " | " + reportListStr + ": " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, entry.getValue()) + "\n");
            }
            response.append("See: " + CM.nation.departures.cmd.toSlashMention() + "\n");
        }
        Map<Integer, Double> trades = reportManager.getBlacklistedMoneyTrade(nation);
        if (!trades.isEmpty()) {
            response.append("Money Trades with reported individuals:\n");
            for (Map.Entry<Integer, Double> entry : trades.entrySet()) {
                int nationId = entry.getKey();
                String nationName = PW.getMarkdownUrl(nationId, false);
                response.append("- " + nationName + ": " + MathMan.format(entry.getValue()) + "\n");
            }
        }
        if (response.length() < 4000) {
            io.create().embed(nation.getNation() + " Analysis", response.toString()).send();
        } else {
            io.send(response.toString());
        }
        return null;
//
//        // Print the following so user can check it
//        //      - nation description/city description
//        //        //- Has account description and profile picture
//        //        //- verified
//        //        //- vip
//        return null;
    }
}
