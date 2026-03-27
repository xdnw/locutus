package link.locutus.discord.db.bank;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.math.ArrayUtil;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class LegacyWideTransactionRowMapper implements RowMapper<Transaction2> {
    @Override
    public Transaction2 map(ResultSet rs, StatementContext ctx) throws SQLException {
        int txId = rs.getInt("tx_id");
        long txDatetime = rs.getLong("tx_datetime");
        long senderId = rs.getLong("sender_id");
        int senderType = rs.getInt("sender_type");
        long receiverId = rs.getLong("receiver_id");
        int receiverType = rs.getInt("receiver_type");
        int bankerNationId = rs.getInt("banker_nation_id");
        String note = rs.getString("note");

        double[] resources = new double[ResourceType.values().length];
        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.CREDITS) {
                continue;
            }
            try {
                resources[type.ordinal()] = ArrayUtil.fromCents(rs.getLong(type.name()));
            } catch (SQLException ignored) {
                resources[type.ordinal()] = 0d;
            }
        }

        Transaction2 tx = Transaction2.constructLegacy(
                txId,
                txDatetime,
                senderId,
                senderType,
                receiverId,
                receiverType,
                bankerNationId,
                note,
                resources
        );
        tx.original_id = txId;
        return tx;
    }
}