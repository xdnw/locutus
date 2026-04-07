package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.PW;

import java.nio.charset.StandardCharsets;

public class TaxExpenseTransactionRow {
    public final int txId;
    public final long txDatetime;
    public final byte[] note;
    public final long senderId;
    public final int senderType;
    public final String senderName;
    public final long receiverId;
    public final int receiverType;
    public final int bankerNationId;
    public final double[] resources;

    public TaxExpenseTransactionRow(Transaction2 transaction) {
        this.txId = transaction.tx_id;
        this.txDatetime = transaction.tx_datetime;
        this.note = transaction.getLegacyNote().getBytes(StandardCharsets.UTF_8);
        this.senderId = transaction.sender_id;
        this.senderType = transaction.sender_type;
        this.senderName = PW.getName(transaction.sender_id, transaction.isSenderAA());
        this.receiverId = transaction.receiver_id;
        this.receiverType = transaction.receiver_type;
        this.bankerNationId = transaction.banker_nation;
        this.resources = transaction.resources;
    }
}