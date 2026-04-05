package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.db.entities.WebTaxBracket;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TaxExpenseTimeBracket {
    public final int taxId;
    public final @Nullable WebTaxBracket bracket;
    public final int nationCount;
    public final double[][] overallByCategory;

    public TaxExpenseTimeBracket(int taxId,
                                 @Nullable WebTaxBracket bracket,
                                 int nationCount,
                                 double[][] overallByCategory) {
        this.taxId = taxId;
        this.bracket = bracket;
        this.nationCount = nationCount;
        this.overallByCategory = overallByCategory;
    }
}