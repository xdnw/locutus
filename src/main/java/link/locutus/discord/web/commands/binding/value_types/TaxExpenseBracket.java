package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.WebTaxBracket;

import java.util.List;
import java.util.Map;

public class TaxExpenseBracket {
    public final Map<Integer, List<WebTransaction>> transactionsByNation;
    public final WebTaxBracket bracket;
    public final Map<Integer, Integer> bracketToNationDepositCount;
    public final double[] income;
    public final List<NationTaxInfo> nations;
    public final Map<Integer, double[]> incomeByNation;
    public final double[] expense;
    public final Map<Integer, double[]> expensesByNation;

    public TaxExpenseBracket(WebTaxBracket bracket,
                             List<NationTaxInfo> nations,
                             Map<Integer, Integer> bracketToNationDepositCount,
                             double[] income,
                             Map<Integer, double[]> incomeByNation,
                             Map<Integer, List<WebTransaction>> transactionsByNation,
                             double[] expense,
                             Map<Integer, double[]> expensesByNation) {
        this.bracket = bracket;
        this.nations = nations;
        this.bracketToNationDepositCount = bracketToNationDepositCount;
        this.income = income;
        this.incomeByNation = incomeByNation;
        this.transactionsByNation = transactionsByNation;
        this.expense = expense;
        this.expensesByNation = expensesByNation;
    }
}