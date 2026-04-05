package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class TaxExpenseBracketRows {
    public final int taxId;
    public final List<TaxExpenseBracketRow> rows;

    public TaxExpenseBracketRows(int taxId, List<TaxExpenseBracketRow> rows) {
        this.taxId = taxId;
        this.rows = rows;
    }
}
