package link.locutus.discord.db;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.TransactionEndpointKey;
import link.locutus.discord.db.entities.TransactionNote;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalTxPurgerTest {
    @Test
    void purgeAllOppositeExpireDecayPairsDeletesOnlyMatchedCanonicalPairs() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createInternalTransactionsTable(conn);

            long txDatetime = 1_700_000_000_000L;
            long expire = txDatetime + 60_000L;
            long decay = txDatetime + 120_000L;

            TransactionNote matchedNote = TransactionNote.builder()
                    .put(DepositType.GRANT)
                    .put(DepositType.CITY, 3L)
                    .put(DepositType.EXPIRE, expire)
                    .put(DepositType.DECAY, decay)
                    .build();
            TransactionNote unmatchedNote = TransactionNote.builder()
                    .put(DepositType.GRANT)
                    .put(DepositType.CITY, 4L)
                    .put(DepositType.EXPIRE, expire)
                    .put(DepositType.DECAY, decay)
                    .build();

            insert(conn, Transaction2.construct(1, txDatetime, 11L, 1, 22L, 2, 33,
                    matchedNote, false, false, money(10.0)));
            insert(conn, Transaction2.construct(2, txDatetime, 22L, 2, 11L, 1, 33,
                    matchedNote, false, false, money(10.0)));
            insert(conn, Transaction2.construct(3, txDatetime, 11L, 1, 22L, 2, 33,
                    unmatchedNote, false, false, money(5.0)));
            insert(conn, Transaction2.construct(4, txDatetime, 11L, TransactionEndpointKey.TAX_TYPE, 22L,
                    TransactionEndpointKey.ALLIANCE_TYPE, 33,
                    matchedNote, false, false, money(10.0)));

            StringBuilder details = new StringBuilder();
            int deleted = InternalTxPurger.purgeAllOppositeExpireDecayPairs(conn, true, details);

            assertEquals(2, deleted);
            assertEquals(2, countRows(conn));
            assertEquals(List.of(3L, 4L), remainingTxIds(conn));
            assertTrue(details.toString().contains("matched 1 cancelling pairs"));
            assertTrue(details.toString().contains("Deleted 2 rows."));
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

    private static void insert(Connection conn, Transaction2 tx) throws Exception {
        try (PreparedStatement statement = conn.prepareStatement("""
                INSERT INTO INTERNAL_TRANSACTIONS2 (
                    tx_id, tx_datetime, sender_key, receiver_key, banker_nation_id, note
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            int index = 1;
            statement.setInt(index++, tx.tx_id);
            statement.setLong(index++, tx.tx_datetime);
            statement.setLong(index++, TransactionEndpointKey.encode(tx.sender_id, tx.sender_type));
            statement.setLong(index++, TransactionEndpointKey.encode(tx.receiver_id, tx.receiver_type));
            statement.setInt(index++, tx.banker_nation);
            statement.setBytes(index++, tx.getNoteBytes());
            statement.executeUpdate();
        }
    }

    private static double[] money(double amount) {
        double[] resources = new double[ResourceType.values.length];
        resources[ResourceType.MONEY.ordinal()] = amount;
        return resources;
    }

    private static int countRows(Connection conn) throws Exception {
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM INTERNAL_TRANSACTIONS2")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static List<Long> remainingTxIds(Connection conn) throws Exception {
        try (Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery("SELECT tx_id FROM INTERNAL_TRANSACTIONS2 ORDER BY tx_id")) {
            List<Long> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
            return ids;
        }
    }
}

