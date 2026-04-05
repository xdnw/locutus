package link.locutus.discord.web.commands.binding.value_types;

import java.util.Set;
import java.util.List;

public class TaxExpenses {
    public final TaxExpenseBracket total;
    public final List<TaxExpenseBracket> brackets;
    public final Set<Integer> alliances;
    public final int taxRecordCount;

    public TaxExpenses(TaxExpenseBracket total,
                       List<TaxExpenseBracket> brackets,
                       Set<Integer> alliances,
                       int taxRecordCount) {
        this.total = total;
        this.brackets = brackets;
        this.alliances = alliances;
        this.taxRecordCount = taxRecordCount;
    }
}
