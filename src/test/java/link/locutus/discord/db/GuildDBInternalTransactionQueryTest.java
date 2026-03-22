package link.locutus.discord.db;

import link.locutus.discord.db.entities.TransactionEndpointKey;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuildDBInternalTransactionQueryTest {
    @Test
    void keyedLookupQueryAppliesDateBoundsToSenderAndReceiverMatches() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createInternalTransactionsTable(conn);
            long endpointKey = TransactionEndpointKey.encode(77L, 1);
            long otherKey = TransactionEndpointKey.encode(88L, 1);
            long start = 2_000L;
            long end = 4_000L;

            insert(conn, 1, 1_000L, endpointKey, otherKey);
            insert(conn, 2, 2_500L, otherKey, endpointKey);
            insert(conn, 3, 3_500L, endpointKey, otherKey);
            insert(conn, 4, 3_000L, otherKey, otherKey);

            String query = GuildDB.buildInternalTransactionLookupQuery("sender_key = ? OR receiver_key = ?", start, end);
            List<Integer> txIds = queryTxIds(conn, query, stmt -> {
                stmt.setLong(1, endpointKey);
                stmt.setLong(2, endpointKey);
                stmt.setLong(3, start);
                stmt.setLong(4, end);
            });

            assertEquals(List.of(2, 3), txIds);
        }
    }

    @Test
    void keyedInQueryAppliesDateBoundsToWholeEndpointPredicate() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createInternalTransactionsTable(conn);
            long firstKey = TransactionEndpointKey.encode(77L, 1);
            long secondKey = TransactionEndpointKey.encode(55L, 2);
            long otherKey = TransactionEndpointKey.encode(88L, 1);
            long start = 2_000L;
            long end = 4_000L;

            insert(conn, 1, 1_000L, firstKey, otherKey);
            insert(conn, 2, 2_500L, otherKey, secondKey);
            insert(conn, 3, 3_500L, firstKey, otherKey);
            insert(conn, 4, 3_000L, otherKey, otherKey);

            String inClause = "(" + firstKey + ", " + secondKey + ")";
            String query = GuildDB.buildInternalTransactionLookupQuery(
                    "sender_key IN " + inClause + " OR receiver_key IN " + inClause, start, end);
            List<Integer> txIds = queryTxIds(conn, query, stmt -> {
                stmt.setLong(1, start);
                stmt.setLong(2, end);
            });

            assertEquals(List.of(2, 3), txIds);
        }
    }

    private static void createInternalTransactionsTable(Connection conn) throws Exception {
        try (Statement statement = conn.createStatement()) {
            statement.execute("""
                    CREATE TABLE INTERNAL_TRANSACTIONS2 (
                        tx_id INTEGER PRIMARY KEY,
                        tx_datetime INTEGER NOT NULL,
                        sender_key INTEGER NOT NULL,
                        receiver_key INTEGER NOT NULL,
                        banker_nation_id INTEGER NOT NULL,
                        note BLOB
                    )
                    """);
        }
    }

    private static void insert(Connection conn, int txId, long txDatetime, long senderKey, long receiverKey) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO INTERNAL_TRANSACTIONS2 (tx_id, tx_datetime, sender_key, receiver_key, banker_nation_id, note) VALUES (?, ?, ?, ?, ?, ?)") ) {
            stmt.setInt(1, txId);
            stmt.setLong(2, txDatetime);
            stmt.setLong(3, senderKey);
            stmt.setLong(4, receiverKey);
            stmt.setInt(5, 1);
            stmt.setBytes(6, null);
            stmt.executeUpdate();
        }
    }

    private static List<Integer> queryTxIds(Connection conn, String query, SqlBinder binder) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            binder.bind(stmt);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Integer> txIds = new ArrayList<>();
                while (rs.next()) {
                    txIds.add(rs.getInt("tx_id"));
                }
                return txIds;
            }
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement stmt) throws Exception;
    }
}

