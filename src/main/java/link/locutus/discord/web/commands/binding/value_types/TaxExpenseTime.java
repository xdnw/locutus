package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class TaxExpenseTime {
    public final long[] timestamps;
    public final List<TaxExpenseTimeCategory> categories;
    public final TaxExpenseTimeBracket total;
    public final List<TaxExpenseTimeBracket> brackets;

    public TaxExpenseTime(long[] timestamps,
                          List<TaxExpenseTimeCategory> categories,
                          TaxExpenseTimeBracket total,
                          List<TaxExpenseTimeBracket> brackets) {
        this.timestamps = timestamps;
        this.categories = categories;
        this.total = total;
        this.brackets = brackets;
    }
}