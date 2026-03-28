package link.locutus.discord.db.bank;

import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.Transaction2Jdbc;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class LegacyWideTransactionRowMapper implements RowMapper<Transaction2> {
    @Override
    public Transaction2 map(ResultSet rs, StatementContext ctx) throws SQLException {
        return Transaction2Jdbc.readLegacyWide(rs);
    }
}