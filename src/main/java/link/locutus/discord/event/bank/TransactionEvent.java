package link.locutus.discord.event.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.guild.GuildScopeEvent;

public class TransactionEvent extends GuildScopeEvent {
    private final Transaction2 tx;

    public TransactionEvent(Transaction2 tx) {
        this.tx = tx;
    }

    public Transaction2 getTransaction() {
        return tx;
    }

    @Override
    protected void postToGuilds() {
        GuildDB senderDb = null;
        if (tx.isSenderAA()) {
            senderDb = Locutus.imp().getGuildDBByAA((int) tx.getSender());
            if (senderDb != null) {
                post(senderDb);
            }
        }
        if (tx.isReceiverAA()) {
            GuildDB receiverDb = Locutus.imp().getGuildDBByAA((int) tx.getReceiver());
            if (receiverDb != null && receiverDb != senderDb) {
                post(receiverDb);
            }
        }
    }
}
