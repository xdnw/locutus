package link.locutus.discord.db.bank;

import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.io.BitBuffer;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class Transaction2RowMapper implements RowMapper<Transaction2> {
    private final ThreadLocal<BitBuffer> buffers =
            ThreadLocal.withInitial(Transaction2::createNoteBuffer);

    @Override
    public Transaction2 map(ResultSet rs, StatementContext ctx) throws SQLException {
        return Transaction2.fromStoredPayload(
                rs.getInt("tx_id"),
                rs.getLong("tx_datetime"),
                rs.getLong("sender_key"),
                rs.getLong("receiver_key"),
                rs.getInt("banker_nation_id"),
                rs.getBytes("note"),
                buffers.get()
        );
    }
}