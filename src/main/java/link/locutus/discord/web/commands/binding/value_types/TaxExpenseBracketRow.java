package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

public class TaxExpenseBracketRow {
    public final int nationId;
    public final @Nullable Integer currentTaxId;
    public final double netValue;

    public TaxExpenseBracketRow(int nationId,
                                @Nullable Integer currentTaxId,
                                double netValue) {
        this.nationId = nationId;
        this.currentTaxId = currentTaxId;
        this.netValue = netValue;
    }
}
