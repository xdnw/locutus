package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

public class TaxExpenseBracketRow {
    public final int nationId;
    public final @Nullable Integer currentTaxId;
    public final double incomeValue;
    public final double expenseValue;
    public final double netValue;

    public TaxExpenseBracketRow(int nationId,
                                @Nullable Integer currentTaxId,
                                double incomeValue,
                                double expenseValue,
                                double netValue) {
        this.nationId = nationId;
        this.currentTaxId = currentTaxId;
        this.incomeValue = incomeValue;
        this.expenseValue = expenseValue;
        this.netValue = netValue;
    }
}
