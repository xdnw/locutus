package link.locutus.discord.db.bank;

import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.Transaction2Jdbc;
import link.locutus.discord.util.io.BitBuffer;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.BooleanSupplier;

public final class Transaction2RowMapper implements RowMapper<Transaction2> {
    private final BooleanSupplier allowCompatibilityRepair;
    private final ThreadLocal<BitBuffer> buffers =
            ThreadLocal.withInitial(Transaction2::createNoteBuffer);

    public Transaction2RowMapper() {
        this(() -> true);
    }

    public Transaction2RowMapper(BooleanSupplier allowCompatibilityRepair) {
        this.allowCompatibilityRepair = allowCompatibilityRepair;
    }

    @Override
    public Transaction2 map(ResultSet rs, StatementContext ctx) throws SQLException {
        return Transaction2Jdbc.readStoredPayload(rs, buffers.get(), allowCompatibilityRepair.getAsBoolean());
    }
}