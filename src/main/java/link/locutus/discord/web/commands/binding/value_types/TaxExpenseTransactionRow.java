package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.db.entities.Transaction2;

public class TaxExpenseTransactionRow {
    public final int txId;
    public final long txDatetime;
    public final String display;

    public TaxExpenseTransactionRow(Transaction2 transaction) {
        this.txId = transaction.tx_id;
        this.txDatetime = transaction.tx_datetime;
        this.display = transaction.toSimpleString();
    }
}