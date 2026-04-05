package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.db.entities.WebTaxBracket;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TaxExpenseBracket {
    public final int taxId;
    public final @Nullable WebTaxBracket bracket;
    public final int nationCount;
    public final double[] income;
    public final double[] expense;

    public TaxExpenseBracket(int taxId,
                             @Nullable WebTaxBracket bracket,
                             int nationCount,
                             double[] income,
                             double[] expense) {
        this.taxId = taxId;
        this.bracket = bracket;
        this.nationCount = nationCount;
        this.income = income;
        this.expense = expense;
    }
}