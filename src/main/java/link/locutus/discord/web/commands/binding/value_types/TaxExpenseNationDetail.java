package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

public class TaxExpenseNationDetail {
    public final int taxId;
    public final int nationId;
    public final @Nullable Integer currentTaxId;
    public final int depositCount;
    public final int transactionCount;
    public final double[] income;
    public final double[] expense;

    public TaxExpenseNationDetail(int taxId,
                                  int nationId,
                                  @Nullable Integer currentTaxId,
                                  int depositCount,
                                  int transactionCount,
                                  double[] income,
                                  double[] expense) {
        this.taxId = taxId;
        this.nationId = nationId;
        this.currentTaxId = currentTaxId;
        this.depositCount = depositCount;
        this.transactionCount = transactionCount;
        this.income = income;
        this.expense = expense;
    }
}