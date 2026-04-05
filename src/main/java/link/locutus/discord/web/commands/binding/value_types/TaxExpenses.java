package link.locutus.discord.web.commands.binding.value_types;

import java.util.Set;
import java.util.List;

public class TaxExpenses {
    public final int datasetId;
    public final TaxExpenseBracket total;
    public final List<TaxExpenseBracket> brackets;
    public final Set<Integer> alliances;
    public final int taxRecordCount;

    public TaxExpenses(int datasetId,
                       TaxExpenseBracket total,
                       List<TaxExpenseBracket> brackets,
                       Set<Integer> alliances,
                       int taxRecordCount) {
        this.datasetId = datasetId;
        this.total = total;
        this.brackets = brackets;
        this.alliances = alliances;
        this.taxRecordCount = taxRecordCount;
    }
}
