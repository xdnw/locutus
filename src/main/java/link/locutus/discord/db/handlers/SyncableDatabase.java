package link.locutus.discord.db.handlers;

import link.locutus.discord.config.Settings;
import link.locutus.discord.db.SQLUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public interface SyncableDatabase {

    /**
     * @return Map of table name to date column name
     */
    Map<String, String> getTablesToSync();

    Connection getConnection();

    Set<String> getTablesAllowingDeletion();

    default List<String> getTableColumns(String tableName) {
        List<String> columnNames = new ArrayList<>();

        try {
            DatabaseMetaData metaData = getConnection().getMetaData();
            ResultSet columnsResultSet = metaData.getColumns(null, null, tableName, null);

            while (columnsResultSet.next()) {
                String columnName = columnsResultSet.getString("COLUMN_NAME");
                columnNames.add(columnName);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Handle the exception as needed
        }
        return columnNames;
    }

    default void createDeletionsTables() {
        if (Settings.INSTANCE.DATABASE.SYNC.ENABLED) {
            for (String table : getTablesAllowingDeletion()) {
                createDeletionsTable(table);
            }
        }
    }

    private void createDeletionsTable(String tableName) {
        Map<String, String> primaryKeyNameType = getPrimaryKeys(tableName);
        StringBuilder createStmt = new StringBuilder("CREATE TABLE IF NOT EXISTS " + tableName + "_deletions (");

        for (Map.Entry<String, String> entry : primaryKeyNameType.entrySet()) {
            String columnName = entry.getKey();
            String columnType = entry.getValue();

            createStmt.append(columnName).append(" ").append(columnType).append(" NOT NULL, ");
        }
        createStmt.append("date_updated BIG INT NOT NULL, ");

        // Add the primary key constraint with the primary key column names
        String primaryKeyColumns = String.join(", ", primaryKeyNameType.keySet());
        createStmt.append("PRIMARY KEY(").append(primaryKeyColumns).append("))");

        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(createStmt.toString());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void purgeDeletionData(long timestamp) {
        for (String table : getTablesAllowingDeletion()) {
            String deletionTableName = table + "_deletions";
            String deleteQuery = "DELETE FROM " + deletionTableName + (timestamp > 0 ? " WHERE date_updated < ?" : "");

            try (PreparedStatement stmt = getConnection().prepareStatement(deleteQuery)) {
                if (timestamp > 0) {
                    stmt.setLong(1, timestamp);
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    default void logDeletions(String tableName, long date, String[] primaryKeys, List<Object[]> primaryKeyValues) {
        if (Settings.INSTANCE.DATABASE.SYNC.ENABLED) {
            if (primaryKeyValues.isEmpty()) return;
            String deletionTable = tableName + "_deletions";
            StringBuilder query = new StringBuilder("INSERT OR REPLACE INTO " + deletionTable + " (");
            for (int i = 0; i < primaryKeys.length; i++) {
                query.append(primaryKeys[i]);
                if (i < primaryKeys.length - 1) {
                    query.append(", ");
                }
            }
            query.append(", date_updated) VALUES (");
            for (int i = 0; i < primaryKeys.length + 1; i++) {
                query.append("?");
                if (i < primaryKeys.length + 1 - 1) {
                    query.append(", ");
                }
            }
            query.append(")");
            SQLUtil.executeBatch(getConnection(), primaryKeyValues, query.toString(), (ThrowingBiConsumer<Object[], PreparedStatement>) (keyValues, stmt) -> {
                for (int i = 0; i < keyValues.length; i++) {
                    stmt.setObject(i + 1, keyValues[i]);
                }
                stmt.setLong(keyValues.length + 1, date);
            });
        }
    }
    default void logDeletion(String nationMeta, long date, String condition, String... columns) {
        if (Settings.INSTANCE.DATABASE.SYNC.ENABLED) {
            String select = "SELECT " + String.join(", ", columns) + " FROM " + nationMeta + " WHERE " + condition;
            try (PreparedStatement stmt = getConnection().prepareStatement(select)) {
                ResultSet resultSet = stmt.executeQuery();
                List<Object[]> list = new ArrayList<>();
                while (resultSet.next()) {
                    Object[] objects = new Object[columns.length];
                    for (int i = 0; i < columns.length; i++) {
                        objects[i] = resultSet.getObject(columns[i]);
                    }
                    list.add(objects);
                }
                logDeletions(nationMeta, date, columns, list);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    default void logDeletion(String tableName, long date, String primaryKey, Object primaryKeyValue) {
        logDeletion(tableName, date, new String[]{primaryKey}, primaryKeyValue);
    }

    default void logDeletion(String tableName, long date, String[] primaryKeys, Object... primaryKeyValues) {
        List<Object[]> list = new ArrayList<>(1);
        list.add(primaryKeyValues);
        logDeletions(tableName, date, primaryKeys, list);
    }

    default Map<String, String> getPrimaryKeys(String tableName) {
        try (ResultSet primaryKeys = getConnection().getMetaData().getPrimaryKeys(null, null, tableName)) {
            Map<String, String> primaryKeyNameAndType = new LinkedHashMap<>();
            while (primaryKeys.next()) {
                String columnName = primaryKeys.getString("COLUMN_NAME");
                String dataType = primaryKeys.getString("TYPE_NAME");
                primaryKeyNameAndType.put(columnName, dataType);
            }
            return primaryKeyNameAndType;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    default List<Object[]> getDeletionData(String tableName, long timestampMS) {
        String deletionsTable = tableName + "_deletions";
        String dateColumnName = "date_updated";

        String query = "SELECT * FROM " + deletionsTable + " WHERE " + dateColumnName + " >= ?";

        List<Object[]> result = new ArrayList<>();
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(query)) {
            preparedStatement.setLong(1, timestampMS);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                int columnCount = resultSet.getMetaData().getColumnCount();
                Object[] row = new Object[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    row[i - 1] = resultSet.getObject(i);
                }
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Handle the exception as needed
        }
        return result;
    }

    default List<Object[]> getTableData(String tableName, String dateColumnName, long timestampMS) {
        List<Object[]> result = new ArrayList<>();
        String query = "SELECT * FROM " + tableName + " WHERE " + dateColumnName + " >= ?";

        try (PreparedStatement preparedStatement = getConnection().prepareStatement(query)) {
            preparedStatement.setLong(1, timestampMS);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                int columnCount = resultSet.getMetaData().getColumnCount();
                Object[] row = new Object[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    row[i - 1] = resultSet.getObject(i);
                }
                result.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Handle the exception as needed
        }
        return result;
    }

    default void serializeSQLRowData(List<Object[]> data, DataOutputStream out) throws IOException {
        // Iterate through the data list
        for (Object[] row : data) {
            for (Object obj : row) {
                if (obj == null) {
                    // Write a null marker to the stream
                    out.writeByte(0);
                } else if (obj instanceof String) {
                    // Write type marker (1 for String) and the String bytes to the stream
                    out.writeByte(1);
                    byte[] stringBytes = ((String) obj).getBytes();
                    out.writeInt(stringBytes.length);
                    out.write(stringBytes);
                } else if (obj instanceof Integer) {
                    // Write type marker (2 for int) and the int value to the stream
                    out.writeByte(2);
                    out.writeInt((Integer) obj);
                } else if (obj instanceof Long) {
                    // Write type marker (3 for long) and the long value to the stream
                    out.writeByte(3);
                    out.writeLong((Long) obj);
                } else if (obj instanceof Double) {
                    // Write type marker (4 for double) and the double value to the stream
                    out.writeByte(4);
                    out.writeDouble((Double) obj);
                } else if (obj instanceof byte[] byteArray) {
                    // Write type marker (5 for byte[]) and the byte[] length and content
                    out.writeByte(5);
                    out.writeInt(byteArray.length);
                    out.write(byteArray);
                } else {
                    // Handle unsupported types or throw an exception
                    throw new IllegalArgumentException("Unsupported data type: " + obj.getClass().getName());
                }
            }
        }
    }

    default List<Object[]> deserializeSQLRowData(int numColumns, DataInputStream is) throws IOException {
        List<Object[]> result = new ArrayList<>();
        while (is.available() > 0) {
            Object[] row = new Object[numColumns];
            for (int i = 0; i < numColumns; i++) {
                byte typeMarker = is.readByte();
                switch (typeMarker) {
                    case 0:
                        // Null marker, set the element to null
                        row[i] = null;
                        break;
                    case 1:
                        // String
                        int stringLength = is.readInt();
                        byte[] stringBytes = new byte[stringLength];
                        is.readFully(stringBytes);
                        row[i] = new String(stringBytes);
                        break;
                    case 2:
                        // int
                        row[i] = is.readInt();
                        break;
                    case 3:
                        // long
                        row[i] = is.readLong();
                        break;
                    case 4:
                        // double
                        row[i] = is.readDouble();
                        break;
                    case 5:
                        // byte[]
                        int byteArrayLength = is.readInt();
                        byte[] byteArray = new byte[byteArrayLength];
                        is.readFully(byteArray);
                        row[i] = byteArray;
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported type marker: " + typeMarker);
                }
            }
            result.add(row);
        }
        return result;
    }

    default void writeDeletions(Object lock, String table, List<String> primaryKeys, DataInputStream is) throws IOException {
        List<Object[]> data = deserializeSQLRowData(primaryKeys.size() + 1, is);
        String query = constructDeletionQuery(table, primaryKeys);
        synchronized (lock) {
            SQLUtil.executeBatch(getConnection(), data, query, (ThrowingBiConsumer<Object[], PreparedStatement>) (rowData, preparedStatement) -> {
                for (int i = 0; i < rowData.length; i++) {
                    Object value = rowData[i];
                    SQLUtil.set(preparedStatement, i + 1, value);
                }
            });
        }
    }

    default void writeData(Object lock, String table, List<String> columns, DataInputStream is) throws IOException {
        List<Object[]> data = deserializeSQLRowData(columns.size(), is);
        String query = constructInsertQuery(table, columns);
        synchronized (lock) {
            SQLUtil.executeBatch(getConnection(), data, query, (ThrowingBiConsumer<Object[], PreparedStatement>) (rowData, preparedStatement) -> {
                for (int i = 0; i < rowData.length; i++) {
                    Object value = rowData[i];
                    SQLUtil.set(preparedStatement, i + 1, value);
                }
            });
        }
    }

    private String constructInsertQuery(String table, List<String> columns) {
        // Construct the INSERT query based on the table name and column names
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("INSERT INTO ").append(table).append(" (");
        for (String column : columns) {
            queryBuilder.append(column).append(", ");
        }
        queryBuilder.delete(queryBuilder.length() - 2, queryBuilder.length()); // Remove the trailing comma and space
        queryBuilder.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            queryBuilder.append("?, ");
        }
        queryBuilder.delete(queryBuilder.length() - 2, queryBuilder.length()); // Remove the trailing comma and space
        queryBuilder.append(")");
        return queryBuilder.toString();
    }

    private String constructDeletionQuery(String table, List<String> primaryKeys) {
        // also date_updated
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("DELETE FROM ").append(table).append(" WHERE ");
        for (String column : primaryKeys) {
            queryBuilder.append(column).append(" = ? AND ");
        }
        queryBuilder.append("date_updated < ?");
        return queryBuilder.toString();
    }
}
