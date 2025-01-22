package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.db.entities.Transaction2;

public class WebTransaction {
    public final int tx_id;
    public final long tx_datetime;
    public final long sender_id;
    public final int sender_type;
    public final long receiver_id;
    public final int receiver_type;
    public final int banker_nation;
    public final String note;
    public final double[] resources;

    public WebTransaction(Transaction2 tx) {
        this.tx_id = tx.tx_id;
        this.tx_datetime = tx.tx_datetime;
        this.sender_id = tx.sender_id;
        this.sender_type = tx.sender_type;
        this.receiver_id = tx.receiver_id;
        this.receiver_type = tx.receiver_type;
        this.banker_nation = tx.banker_nation;
        this.note = tx.note;
        this.resources = tx.resources;
    }
}
