package link.locutus.discord.db;


import link.locutus.discord.config.Settings;
import link.locutus.discord.util.AlertUtil;
import org.jetbrains.annotations.NotNull;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.io.Closeable;
import java.io.File;
import java.sql.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class DBMainV3 implements Closeable {
    private static final Logger log = Logger.getLogger("DBMain");

    private final File dbLocation;
    private DSLContext ctx;
    private Connection connection;
    private boolean inMemory;

    public DBMainV3(Settings.DATABASE config, String name, boolean inMemory) throws SQLException, ClassNotFoundException {
        this.inMemory = inMemory;
        if (config.SQLITE.USE) {
            dbLocation = new File(config.SQLITE.DIRECTORY + File.separator + name + ".db");
            // create file directory if not exist
            if (!dbLocation.getParentFile().exists()) {
                dbLocation.getParentFile().mkdirs();
            }
            forceConnection();
        } else {
            throw new IllegalArgumentException("Either SQLite OR MySQL must be enabled. (not both, or none)");
        }
        init();
    }

    protected void createTableWithIndexes(Table<?> table) {
        ctx()
                .createTableIfNotExists(table)
                .columns(table.fields())
                .primaryKey(table.getPrimaryKey().getFields())
                .execute();

        for (Index idx : table.getIndexes()) {
            ctx()
                    .createIndexIfNotExists(idx.getName())
                    .on(table, idx.getFields().toArray(new SortField<?>[0]))
                    .execute();
        }
    }

    public boolean tableExists(String tableName) throws SQLException {
        DatabaseMetaData meta = getConnection().getMetaData();
        try (ResultSet resultSet = meta.getTables(null, null, tableName, new String[] {"TABLE"})) {

            return resultSet.next();
        }
    }

    public Condition and(Condition a, Condition b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.and(b);
    }
    public Condition or(Condition a, Condition b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.or(b);
    }


    public DSLContext ctx() {
        return ctx;
    }

    public DBMainV3 init() {
        createTables();
        return this;
    }

    protected int updateLegacy(String sql, Consumer<PreparedStatement> withStmt) {
        synchronized (this) {
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                withStmt.accept(stmt);
                return stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    protected boolean queryLegacy(String sql, Consumer<PreparedStatement> withStmt, Consumer<ResultSet> rsq) {
        {
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setFetchSize(10000);
                withStmt.accept(stmt);
                ResultSet rs = stmt.executeQuery();
                rsq.accept(rs);
                return rs != null;
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    public Result<Record> query(TableLike<?> table, Condition condition, SortField<?> orderBy, Integer limit, GroupField... groupBy) {
        @NotNull SelectJoinStep<Record> select = ctx().select().from(table);
        SelectConnectByStep<Record> where;
        if (condition != null) {
            where = select.where(condition);
        } else {
            where = select;
        }

        SelectHavingStep<Record> groupStep;
        if (groupBy != null && groupBy.length > 0) {
            groupStep = where.groupBy(groupBy);
        } else {
            groupStep = where;
        }
        SelectLimitStep<Record> orderStep;
        if (orderBy != null) {
            orderStep = groupStep.orderBy(orderBy);
        } else {
            orderStep = groupStep;
        }
        SelectForUpdateStep<Record> limitStep;
        if (limit != null) {
            limitStep = orderStep.limit(limit);
        } else {
            limitStep = orderStep;
        }
        return limitStep.fetch();
    }

    private Connection forceConnection() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        String connectStr = "jdbc:sqlite:";
//        if (inMemory) connectStr += ":memory:";
        connectStr += dbLocation;
        connection = DriverManager.getConnection(connectStr);
        if (inMemory) {
            connection.createStatement().execute("""
                    pragma journal_mode = WAL;
                    pragma synchronous = normal;
                    pragma temp_store = memory;
                    pragma mmap_size = 30000000000;""");
        }
        this.ctx = DSL.using(connection, SQLDialect.SQLITE, new org.jooq.conf.Settings()
                .withExecuteLogging(false)
        );
        return connection;
    }

    /**
     * Gets the connection with the database
     *
     * @return Connection with the database, null if none
     */
    public Connection getConnection() {
        if (connection == null) {
            try {
                forceConnection();
            } catch (ClassNotFoundException | SQLException e) {
                e.printStackTrace();
            }
        }
        return connection;
    }

    /**
     * Closes the connection with the database
     *
     * @return true if successful
     * @throws SQLException if the connection cannot be closed
     */
    public boolean closeConnection() throws SQLException {
        if (connection == null) {
            return false;
        }
        synchronized (this) {
            if (connection == null) {
                return false;
            }
            connection.close();
            connection = null;
            return true;
        }
    }

    /**
     * Checks if a connection is open with the database
     *
     * @return true if the connection is open
     */
    public synchronized boolean checkConnection() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public synchronized void close() {
        try {
            closeConnection();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    protected void handleError(Throwable e) {
        AlertUtil.displayTray("DATABASAE ERROR", "SSEE CONSOLE");
        log.log(Level.WARNING, "============ DATABASE ERROR ============");
        log.log(Level.WARNING, "There was an error updating the database.");
        log.log(Level.WARNING, "- It will be corrected on shutdown");
        log.log(Level.WARNING, "========================================");
        e.printStackTrace();
        log.log(Level.WARNING, "========================================");
        System.exit(1);
    }

    public abstract void createTables();
}
