package link.locutus.discord.web.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAuthenticated;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.TaxRecordCategorizer2;
import net.dv8tion.jda.api.entities.Guild;
import views.guild.econ.taxexpensesbyturn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class EconPages {

    @Command(desc = "Show running tax expenses by day by bracket")
    @IsAlliance
    @RolePermission(Roles.ECON_LOW_GOV)
    @WhitelistPermission
    @IsAuthenticated
    public Object taxExpensesByTime(@Me Guild guild, @Me GuildDB db, @Me DBNation me, @Timestamp long start, @Timestamp long end, @Default Set<DBNation> nationFilter, @Switch('s') @Range(min=1) Integer movingAverageTurns, @Switch('c') boolean cumulative,
                                    @Switch('t') boolean dontRequireTagged) throws Exception {

        if (movingAverageTurns != null && cumulative) throw new IllegalArgumentException("Please pick either a moving average, or cumulative, not both");

        Predicate<Integer> isNationIdPermitted;
        if (nationFilter == null) isNationIdPermitted = f -> true;
        else {
            Set<Integer> ids = nationFilter.stream().map(f -> f.getNation_id()).collect(Collectors.toSet());
            isNationIdPermitted = ids::contains;
        }
        int aaId = db.getAlliance_id();

        List<String> errors = new ArrayList<>();
        TaxRecordCategorizer2 categorized = new TaxRecordCategorizer2(db, start, end, true, dontRequireTagged, true, true, isNationIdPermitted, errors::add);

        long turnStart = TimeUtil.getTurn(start);
        long turnEnd = TimeUtil.getTurn(end);
        if (turnEnd - turnStart > 365 * 12) return "Timeframe is too large";

        Map<Integer, List<BankDB.TaxDeposit>> taxRecordsByBracket = new HashMap<>();
        for (BankDB.TaxDeposit tax : categorized.getTaxes()) {
            long turn = tax.getTurn();
            long turnRel = turn - turnStart;
            taxRecordsByBracket.computeIfAbsent(tax.tax_id, f -> new LinkedList<>()).add(tax);
        }

        Map<Integer, List<Map.Entry<Transaction2, TaxRecordCategorizer2.TransactionType>>> txsByType = categorized.getTransactionsByBracketByType();

        Map<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> categorizedByTurnByBracket = new HashMap<>();

        for (Map.Entry<Integer, List<BankDB.TaxDeposit>> entry : taxRecordsByBracket.entrySet()) {
            List<BankDB.TaxDeposit> taxRecords = entry.getValue();
            List<Map.Entry<Transaction2, TaxRecordCategorizer2.TransactionType>> transfers = txsByType.getOrDefault(entry.getKey(), Collections.emptyList());

            Map<TaxRecordCategorizer2.TransactionType, double[][]> result = categorized.sumTransfersByCategoryByTurn(turnStart, turnEnd, taxRecords, transfers);
            if (cumulative) {
                result.entrySet().forEach(e -> e.setValue(categorized.cumulative(e.getValue())));
            } else if (movingAverageTurns != null) {
                result.entrySet().forEach(e -> e.setValue(categorized.movingAverage(e.getValue(), movingAverageTurns)));
            }

            categorizedByTurnByBracket.put(entry.getKey(), result);
        }

        Map<TaxRecordCategorizer2.TransactionType, double[][]> total = new HashMap<>();
        for (Map.Entry<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> entry : categorizedByTurnByBracket.entrySet()) {
            Map<TaxRecordCategorizer2.TransactionType, double[][]> byType = entry.getValue();
            for (Map.Entry<TaxRecordCategorizer2.TransactionType, double[][]> entry2 : byType.entrySet()) {
                double[][] typeDataByTurn = entry2.getValue();
                double[][] buffer = total.computeIfAbsent(entry2.getKey(), f -> new double[typeDataByTurn.length][ResourceType.values.length]);

                for (int i = 0; i < buffer.length; i++) {
                    PnwUtil.add(buffer[i], typeDataByTurn[i]);
                }
            }
        }

        categorizedByTurnByBracket.put(-1, total);

        String title = "Alliance Income/Expenses By Turn";
        if (cumulative) {
            title = "Cumulative " + title;
        }
        if (movingAverageTurns != null) {
            title += " (moving average: " + movingAverageTurns + " turns)";
        }

        Map<Integer, TaxBracket> t = categorized.getBrackets();

        String pageHtml = taxexpensesbyturn.template(title, db, aaId, turnStart, turnEnd, categorized, categorizedByTurnByBracket, categorized.getBrackets()).render().toString();
        return pageHtml;
    }

    @Command(desc = "Show cumulative tax expenses over a period by nation/bracket")
    @IsAlliance
    @RolePermission(Roles.ECON_LOW_GOV)
    @WhitelistPermission
    @IsAuthenticated
    public Object taxExpensesIndex(@Me GuildDB db, @Timestamp long start, @Timestamp long end, @Switch('n') NationList nationList,
                                   @Switch('g') boolean dontRequireGrant, @Switch('t') boolean dontRequireTagged, @Switch('e') boolean dontRequireExpiry,
                                   @Switch('d') boolean includeDeposits) throws Exception {

        List<String> errors = new ArrayList<>();
        Predicate<Integer> allowedNations;
        if(nationList == null) {
            allowedNations =  f -> true;
        } else {
            allowedNations = id -> {
                DBNation nation = DBNation.byId(id);
                return nation != null && nationList.getNations().contains(nation);
            };
        }
        TaxRecordCategorizer2 categorized = new TaxRecordCategorizer2(db, start, end, dontRequireGrant, dontRequireTagged, dontRequireExpiry, includeDeposits, allowedNations, errors::add);

        return views.guild.econ.taxexpenses.template(
                db, categorized.getAaId(), categorized.getAlliances(), !dontRequireGrant, !dontRequireExpiry, !dontRequireTagged,
                categorized.getBrackets(), categorized.getTaxes(), categorized.getBracketsByNation(), categorized.getNationsByBracket(), categorized.getAllNations(),
                categorized.getBracketToNationDepositCount(), categorized.getAllNationDepositCount(),
                categorized.getIncomeTotal(), categorized.getIncomeByBracket(), categorized.getIncomeByNation(), categorized.getIncomeByNationByBracket(),
                categorized.getTransactionsByNation(), categorized.getTransactionsByBracket(), categorized.getTransactionsByNationByBracket(), categorized.getExpenseTransfers(),
                categorized.getExpenseTotal(), categorized.getExpensesByBracket(), categorized.getExpensesByNation(), categorized.getExpensesByNationByBracket()
                ).render().toString();
    }
}
