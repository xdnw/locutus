package link.locutus.discord.event;

import link.locutus.discord.db.entities.Transaction2;

public class TransactionEvent extends Event {
    private final Transaction2 tx;

    public TransactionEvent(Transaction2 tx) {
        this.tx = tx;
    }

    public Transaction2 getTransaction() {
        return tx;
    }
}
