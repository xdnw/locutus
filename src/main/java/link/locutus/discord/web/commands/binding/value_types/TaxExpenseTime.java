package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class TaxExpenseTime {
    public final int datasetId;
    public final long[] timestamps;
    public final List<TaxExpenseTimeCategory> categories;
    public final TaxExpenseTimeBracket total;
    public final List<TaxExpenseTimeBracketSummary> brackets;

    public TaxExpenseTime(int datasetId,
                          long[] timestamps,
                          List<TaxExpenseTimeCategory> categories,
                          TaxExpenseTimeBracket total,
                          List<TaxExpenseTimeBracketSummary> brackets) {
        this.datasetId = datasetId;
        this.timestamps = timestamps;
        this.categories = categories;
        this.total = total;
        this.brackets = brackets;
    }
}