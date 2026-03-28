package link.locutus.discord.db.bank;

import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.Transaction2Jdbc;
import link.locutus.discord.util.io.BitBuffer;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class LegacySplitPayloadTransactionRowMapper implements RowMapper<Transaction2> {
    private final ThreadLocal<BitBuffer> buffers =
            ThreadLocal.withInitial(Transaction2::createNoteBuffer);

    @Override
    public Transaction2 map(ResultSet rs, StatementContext ctx) throws SQLException {
        Transaction2 tx = Transaction2Jdbc.readSplitPayload(rs, buffers.get());
        tx.original_id = tx.tx_id;
        return tx;
    }
}