package link.locutus.discord.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.function.BiConsumer;

public class SQLUtil {


    public static void set(PreparedStatement preparedStatement, int index, Object value) throws SQLException {
        if (value == null) {
            preparedStatement.setNull(index, Types.NULL);
        } else if (value instanceof String) {
            preparedStatement.setString(index, (String) value);
        } else if (value instanceof Integer) {
            preparedStatement.setInt(index, (Integer) value);
        } else if (value instanceof Long) {
            preparedStatement.setLong(index, (Long) value);
        } else if (value instanceof Double) {
            preparedStatement.setDouble(index, (Double) value);
        } else if (value instanceof byte[] byteArray) {
            preparedStatement.setBytes(index, byteArray);
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + value.getClass().getName());
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        s = s.toLowerCase(Locale.ROOT);
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static <T> int[] executeBatch(Connection connection, Collection<T> objects, String query, BiConsumer<T, PreparedStatement> consumer) {
        try {
            if (objects.size() == 1) {
                try (PreparedStatement ps = connection.prepareStatement(query)) {
                    consumer.accept(objects.iterator().next(), ps);
                    int result = ps.executeUpdate();
                    return new int[]{result};
                }
            }
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                boolean clear = false;
                for (T object : objects) {
                    if (clear) ps.clearParameters();
                    clear = true;
                    consumer.accept(object, ps);
                    ps.addBatch();
                }
                return ps.executeBatch();
            }
            finally {
                try {
                    connection.commit();
                } catch (SQLException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                } finally {
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
