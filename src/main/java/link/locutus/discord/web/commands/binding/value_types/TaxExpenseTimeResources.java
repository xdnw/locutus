package link.locutus.discord.web.commands.binding.value_types;

import java.util.Map;

public class TaxExpenseTimeResources {
    public final Map<String, double[][]> byResourceByCategory;

    public TaxExpenseTimeResources(Map<String, double[][]> byResourceByCategory) {
        this.byResourceByCategory = byResourceByCategory;
    }
}
