package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class TaxExpenseNation {
    public final int taxId;
    public final int nationId;
    public final @Nullable Integer currentTaxId;
    public final int depositCount;
    public final int transactionCount;
    public final double incomeValue;
    public final double expenseValue;
    public final double netValue;
    public final double[] income;
    public final double[] expense;
    public final List<TaxExpenseTransactionRow> transactions;

    public TaxExpenseNation(int taxId,
                            int nationId,
                            @Nullable Integer currentTaxId,
                            int depositCount,
                            int transactionCount,
                            double incomeValue,
                            double expenseValue,
                            double netValue,
                            double[] income,
                            double[] expense,
                            List<TaxExpenseTransactionRow> transactions) {
        this.taxId = taxId;
        this.nationId = nationId;
        this.currentTaxId = currentTaxId;
        this.depositCount = depositCount;
        this.transactionCount = transactionCount;
        this.incomeValue = incomeValue;
        this.expenseValue = expenseValue;
        this.netValue = netValue;
        this.income = income;
        this.expense = expense;
        this.transactions = transactions;
    }
}
