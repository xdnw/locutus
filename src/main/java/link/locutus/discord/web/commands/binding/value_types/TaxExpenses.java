package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;
import java.util.Set;

public class TaxExpenses {
    public final List<TaxExpenseBracket> brackets;
    public final Set<Integer> alliances;
    public final boolean requireGrant;
    public final boolean requireExpiry;
    public final boolean requireTagged;

    public TaxExpenses(List<TaxExpenseBracket> brackets, Set<Integer> alliances, boolean requireGrant, boolean requireExpiry, boolean requireTagged) {
        this.brackets = brackets;
        this.alliances = alliances;
        this.requireGrant = requireGrant;
        this.requireExpiry = requireExpiry;
        this.requireTagged = requireTagged;
    }
}
