package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class TaxExpenseBracketMembers {
    public final int taxId;
    public final List<Integer> nationIds;

    public TaxExpenseBracketMembers(int taxId, List<Integer> nationIds) {
        this.taxId = taxId;
        this.nationIds = nationIds;
    }
}