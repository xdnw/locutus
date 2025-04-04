package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.NationTaxInfo;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseBracket;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenses;
import link.locutus.discord.web.commands.binding.value_types.WebTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TaxEndpoints {
    @Command(desc = "Show cumulative tax expenses over a period by nation/bracket", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenses.class)
    public TaxExpenses tax_expense(WebStore ws, @Me GuildDB db, @Timestamp long start, @Timestamp long end, @Switch("n") NationList nationList,
                                   @Switch("g") boolean dontRequireGrant, @Switch("t") boolean dontRequireTagged, @Switch("e") boolean dontRequireExpiry,
                                   @Switch("d") boolean includeDeposits) throws Exception {
        List<String> errors = new ArrayList<>();
        Predicate<Integer> allowedNations;
        if (nationList == null) {
            allowedNations = f -> true;
        } else {
            allowedNations = id -> {
                DBNation nation = DBNation.getById(id);
                return nation != null && nationList.getNations().contains(nation);
            };
        }
        TaxRecordCategorizer2 categorized = new TaxRecordCategorizer2(db, start, end, dontRequireGrant, dontRequireTagged, dontRequireExpiry, includeDeposits, allowedNations, errors::add);

        Map<Integer, double[]> expensesByBracket = categorized.getExpensesByBracket();
        Map<Integer, double[]> incomeByBracket = categorized.getIncomeByBracket();

        List<TaxExpenseBracket> byBracket = new ObjectArrayList<>();
        for (Map.Entry<Integer, TaxBracket> entry : categorized.getBrackets().entrySet()) {
            int taxId = entry.getKey();
            if (expensesByBracket.get(taxId) == null && incomeByBracket.get(taxId) == null) {
                continue;
            }
            WebTaxBracket webBracket = new WebTaxBracket(entry.getValue());
            List<DBNation> nations = categorized.getNationsByBracket().getOrDefault(taxId, new ObjectArrayList<>());
            List<NationTaxInfo> nationsInfo = nations.stream().map(nation -> new NationTaxInfo(nation)).toList();
            Map<Integer, Integer> bracketToNationDepositCount = categorized.getBracketToNationDepositCount().getOrDefault(taxId, Map.of());
            double[] income = incomeByBracket.get(taxId);
            Map<Integer, double[]> incomeByNation = categorized.getIncomeByNationByBracket().getOrDefault(taxId, Map.of());
            Map<Integer, List<Transaction2>> transactionsByNation = categorized.getTransactionsByNationByBracket().getOrDefault(taxId, Map.of());
            Map<Integer, List<WebTransaction>> transactionsByNationWeb = transactionsByNation.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().map(WebTransaction::new).toList()));
            double[] expense = expensesByBracket.get(taxId);
            Map<Integer, double[]> expensesByNation = categorized.getExpensesByNationByBracket().getOrDefault(taxId, Map.of());
            TaxExpenseBracket bracketInfo = new TaxExpenseBracket(webBracket, nationsInfo, bracketToNationDepositCount, income, incomeByNation, transactionsByNationWeb, expense, expensesByNation);
            byBracket.add(bracketInfo);
        }

        TaxExpenses taxes = new TaxExpenses(byBracket, categorized.getAlliances(), !dontRequireGrant, !dontRequireExpiry, !dontRequireTagged);
        return taxes;
    }
}
