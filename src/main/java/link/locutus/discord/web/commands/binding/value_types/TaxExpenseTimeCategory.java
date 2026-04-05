package link.locutus.discord.web.commands.binding.value_types;

public class TaxExpenseTimeCategory {
    public final String name;
    public final boolean expense;

    public TaxExpenseTimeCategory(String name, boolean expense) {
        this.name = name;
        this.expense = expense;
    }
}
