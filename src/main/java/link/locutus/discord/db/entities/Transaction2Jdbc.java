package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.util.io.BitBuffer;
import link.locutus.discord.util.math.ArrayUtil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public final class Transaction2Jdbc {
    private Transaction2Jdbc() {
    }

    public static String insertSql(String table, boolean includeId, boolean ignore) {
        StringBuilder sql = new StringBuilder("INSERT ");
        if (includeId) {
            sql.append("OR ").append(ignore ? "IGNORE" : "REPLACE").append(' ');
        }
        sql.append("INTO `").append(table).append("` (");
        if (includeId) {
            sql.append("tx_id, ");
        }
        sql.append("tx_datetime, sender_key, receiver_key, banker_nation_id, note) VALUES (");
        int fieldCount = includeId ? 6 : 5;
        for (int i = 0; i < fieldCount; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(')');
        return sql.toString();
    }

    public static void bindWithId(PreparedStatement stmt, Transaction2 tx, BitBuffer buffer) throws SQLException {
        stmt.setInt(1, tx.tx_id);
        stmt.setLong(2, tx.tx_datetime);
        stmt.setLong(3, tx.getSenderKey());
        stmt.setLong(4, tx.getReceiverKey());
        stmt.setInt(5, tx.banker_nation);
        bindNote(stmt, 6, tx.getNoteBytes(buffer));
    }

    public static void bindWithoutId(PreparedStatement stmt, Transaction2 tx, BitBuffer buffer) throws SQLException {
        stmt.setLong(1, tx.tx_datetime);
        stmt.setLong(2, tx.getSenderKey());
        stmt.setLong(3, tx.getReceiverKey());
        stmt.setInt(4, tx.banker_nation);
        bindNote(stmt, 5, tx.getNoteBytes(buffer));
    }

    public static Transaction2 readStoredPayload(ResultSet rs, BitBuffer buffer) throws SQLException {
        return readStoredPayload(rs, buffer, true);
    }

    public static Transaction2 readStoredPayload(ResultSet rs, BitBuffer buffer, boolean allowCompatibilityRepair)
            throws SQLException {
        return Transaction2.fromStoredPayload(
                rs.getInt("tx_id"),
                rs.getLong("tx_datetime"),
                rs.getLong("sender_key"),
                rs.getLong("receiver_key"),
                rs.getInt("banker_nation_id"),
                rs.getBytes("note"),
                buffer,
                allowCompatibilityRepair
        );
    }

    public static Transaction2 readSplitPayload(ResultSet rs, BitBuffer buffer) throws SQLException {
        return Transaction2.fromStoredPayload(
                rs.getInt("tx_id"),
                rs.getLong("tx_datetime"),
                rs.getLong("sender_id"),
                rs.getInt("sender_type"),
                rs.getLong("receiver_id"),
                rs.getInt("receiver_type"),
                rs.getInt("banker_nation_id"),
                rs.getBytes("note"),
                buffer
        );
    }

    public static Transaction2 readLegacyWide(ResultSet rs) throws SQLException {
        int txId = rs.getInt("tx_id");
        long txDatetime = rs.getLong("tx_datetime");
        long senderId = rs.getLong("sender_id");
        int senderType = rs.getInt("sender_type");
        long receiverId = rs.getLong("receiver_id");
        int receiverType = rs.getInt("receiver_type");
        int bankerNationId = rs.getInt("banker_nation_id");
        String note = rs.getString("note");

        double[] resources = new double[ResourceType.values.length];
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) {
                continue;
            }
            resources[type.ordinal()] = ArrayUtil.fromCents(rs.getLong(type.name()));
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

    private static void bindNote(PreparedStatement stmt, int index, byte[] noteBytes) throws SQLException {
        if (noteBytes == null) {
            stmt.setNull(index, Types.BLOB);
        } else {
            stmt.setBytes(index, noteBytes);
        }
    }
}
