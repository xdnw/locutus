package link.locutus.discord.db;

import link.locutus.discord.db.entities.TransactionEndpointKey;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankDBEndpointQueryTest {
    @Test
    void latestDepositConditionMatchesOnlyExactTypedSenderOnAllianceDeposits() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createTransactionsTable(conn);
            insert(conn, 1, TransactionEndpointKey.encode(77L, TransactionEndpointKey.NATION_TYPE),
                    TransactionEndpointKey.encode(501L, TransactionEndpointKey.ALLIANCE_TYPE));
            insert(conn, 2, TransactionEndpointKey.encode(77L, TransactionEndpointKey.TAX_TYPE),
                    TransactionEndpointKey.encode(501L, TransactionEndpointKey.ALLIANCE_TYPE));
            insert(conn, 3, TransactionEndpointKey.encode(77L, TransactionEndpointKey.NATION_TYPE),
                    TransactionEndpointKey.encode(501L, TransactionEndpointKey.NATION_TYPE));
            insert(conn, 4, TransactionEndpointKey.encode(77L, TransactionEndpointKey.NATION_TYPE),
                    TransactionEndpointKey.encode(502L, TransactionEndpointKey.ALLIANCE_TYPE));

            Integer txId = queryLatestTransactionId(
                    conn,
                    "SELECT tx_id FROM TRANSACTIONS_2 WHERE sender_key = ? AND (receiver_key & ?) = ? ORDER BY tx_id DESC LIMIT 1",
                    TransactionEndpointKey.encode(77L, TransactionEndpointKey.NATION_TYPE),
                    TransactionEndpointKey.ALLIANCE_TYPE
            );

            assertEquals(4, txId);
        }
    }

    @Test
    void latestWithdrawalConditionMatchesOnlyExactTypedReceiverOnAllianceWithdrawals() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createTransactionsTable(conn);
            insert(conn, 1, TransactionEndpointKey.encode(501L, TransactionEndpointKey.ALLIANCE_TYPE),
                    TransactionEndpointKey.encode(77L, TransactionEndpointKey.NATION_TYPE));
            insert(conn, 2, TransactionEndpointKey.encode(501L, TransactionEndpointKey.ALLIANCE_TYPE),
                    TransactionEndpointKey.encode(77L, TransactionEndpointKey.TAX_TYPE));
            insert(conn, 3, TransactionEndpointKey.encode(501L, TransactionEndpointKey.NATION_TYPE),
                    TransactionEndpointKey.encode(77L, TransactionEndpointKey.NATION_TYPE));
            insert(conn, 4, TransactionEndpointKey.encode(502L, TransactionEndpointKey.ALLIANCE_TYPE),
                    TransactionEndpointKey.encode(77L, TransactionEndpointKey.NATION_TYPE));

            Integer txId = queryLatestTransactionId(
                    conn,
                    "SELECT tx_id FROM TRANSACTIONS_2 WHERE receiver_key = ? AND (sender_key & ?) = ? ORDER BY tx_id DESC LIMIT 1",
                    TransactionEndpointKey.encode(77L, TransactionEndpointKey.NATION_TYPE),
                    TransactionEndpointKey.ALLIANCE_TYPE
            );

            assertEquals(4, txId);
        }
    }

    private static void createTransactionsTable(Connection conn) throws Exception {
        try (Statement statement = conn.createStatement()) {
            statement.execute("""
                    CREATE TABLE TRANSACTIONS_2 (
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

    private static void insert(Connection conn, int txId, long senderKey, long receiverKey) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO TRANSACTIONS_2 (tx_id, tx_datetime, sender_key, receiver_key, banker_nation_id, note) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setInt(1, txId);
            stmt.setLong(2, txId * 1_000L);
            stmt.setLong(3, senderKey);
            stmt.setLong(4, receiverKey);
            stmt.setInt(5, 1);
            stmt.setBytes(6, null);
            stmt.executeUpdate();
        }
    }

        private static Integer queryLatestTransactionId(Connection conn, String sql, long endpointKey, int otherType)
                        throws Exception {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setLong(1, endpointKey);
                        stmt.setLong(2, TransactionEndpointKey.TYPE_MASK);
                        stmt.setInt(3, otherType);
                        try (ResultSet rs = stmt.executeQuery()) {
                                return rs.next() ? rs.getInt(1) : null;
                        }
                }
        }
}

