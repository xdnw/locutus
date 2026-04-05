package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;

public class TaxExpenseTransactions {
    public final int taxId;
    public final int nationId;
    public final List<TaxExpenseTransactionRow> transactions;

    public TaxExpenseTransactions(int taxId, int nationId, List<TaxExpenseTransactionRow> transactions) {
        this.taxId = taxId;
        this.nationId = nationId;
        this.transactions = transactions;
    }
}