package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.db.entities.WebTaxBracket;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TaxExpenseTimeBracketSummary {
    public final int taxId;
    public final @Nullable WebTaxBracket bracket;
    public final int nationCount;
    public final double incomeValue;
    public final double expenseValue;
    public final double netValue;

    public TaxExpenseTimeBracketSummary(int taxId,
                                        @Nullable WebTaxBracket bracket,
                                        int nationCount,
                                        double incomeValue,
                                        double expenseValue,
                                        double netValue) {
        this.taxId = taxId;
        this.bracket = bracket;
        this.nationCount = nationCount;
        this.incomeValue = incomeValue;
        this.expenseValue = expenseValue;
        this.netValue = netValue;
    }
}