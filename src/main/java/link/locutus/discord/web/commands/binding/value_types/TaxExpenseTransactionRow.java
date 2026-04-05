package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.PW;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TaxExpenseTransactionRow {
    public final int txId;
    public final long txDatetime;
    public final String noteSummary;
    public final long senderId;
    public final int senderType;
    public final String senderName;
    public final long receiverId;
    public final int receiverType;
    public final String receiverName;
    public final int bankerNationId;
    public final @Nullable String bankerNationName;
    public final double[] resources;

    public TaxExpenseTransactionRow(Transaction2 transaction) {
        this.txId = transaction.tx_id;
        this.txDatetime = transaction.tx_datetime;
        this.noteSummary = transaction.getNoteSummary();
        this.senderId = transaction.sender_id;
        this.senderType = transaction.sender_type;
        this.senderName = PW.getName(transaction.sender_id, transaction.isSenderAA());
        this.receiverId = transaction.receiver_id;
        this.receiverType = transaction.receiver_type;
        this.receiverName = PW.getName(transaction.receiver_id, transaction.isReceiverAA());
        this.bankerNationId = transaction.banker_nation;
        this.bankerNationName = transaction.banker_nation > 0 ? PW.getName(transaction.banker_nation, false) : null;
        this.resources = transaction.resources;
    }
}