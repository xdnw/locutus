package link.locutus.discord.db;


import link.locutus.discord.config.Settings;
import link.locutus.discord.util.AlertUtil;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class DBMain implements Closeable {
    private static final Logger log = Logger.getLogger("DBMain");

    private final File dbLocation;
    private Connection connection;
    private boolean inMemory;

    public DBMain(String name) throws SQLException, ClassNotFoundException {
       this(name, false);
    }

    public DBMain(String name, boolean inMemory) throws SQLException, ClassNotFoundException {
        this.inMemory = inMemory;
        File file = new File((Settings.INSTANCE.TEST ? "test" + File.separator : "") + "database", name + ".db");
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        this.dbLocation = file;

        connection = openConnection();
        createTables();
    }

    protected int getIntDef0(ResultSet rs, String id) throws SQLException {
        return rs.getInt(id);
    }

    protected int getIntDef0(ResultSet rs, int id) throws SQLException {
        return rs.getInt(id);
    }

    protected long getLongDef0(ResultSet rs, String id) throws SQLException {
        return rs.getLong(id);
    }

    protected long getLongDef0(ResultSet rs, int id) throws SQLException {
        return rs.getLong(id);
    }

    protected Integer getInt(ResultSet rs, String id) throws SQLException {
        int val = rs.getInt(id);
        if (rs.wasNull()) {
            return null;
        }
        return val;
    }

    protected Integer getInt(ResultSet rs, int id) throws SQLException {
        int val = rs.getInt(id);
        if (rs.wasNull()) {
            return null;
        }
        return val;
    }

    protected Long getLong(ResultSet rs, int id) throws SQLException {
        long val = rs.getLong(id);
        if (rs.wasNull()) {
            return null;
        }
        return val;
    }

    protected Long getLong(ResultSet rs, String id) throws SQLException {
        long val = rs.getLong(id);
        if (rs.wasNull()) {
            return null;
        }
        return val;
    }

    protected byte[] getBytes(ResultSet rs, String id) throws SQLException {
        byte[] val = rs.getBytes(id);
        if (rs.wasNull()) {
            return null;
        }
        return val;
    }
    protected byte[] getBytes(ResultSet rs, int id) throws SQLException {
        byte[] val = rs.getBytes(id);
        if (rs.wasNull()) {
            return null;
        }
        return val;
    }

    public File getFile() {
        return dbLocation;
    }

    private void commit() {
        try {
            if (connection == null) {
                return;
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Connection openConnection() throws SQLException, ClassNotFoundException {
        if (checkConnection()) {
            return connection;
        }
        if (!dbLocation.exists()) {
            try {
                dbLocation.getParentFile().mkdirs();
                dbLocation.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();

                log.log(Level.SEVERE, "Unable to create the database!");
            }
        }
        return forceConnection();
    }

    private Connection forceConnection() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        String connectStr = "jdbc:sqlite:";
//        if (inMemory) connectStr += ":memory:";
        connectStr += dbLocation;
        connection = DriverManager.getConnection(connectStr);
        if (inMemory) {
            connection.createStatement().execute("pragma journal_mode = WAL;\n" +
                    "pragma synchronous = normal;\n" +
                    "pragma temp_store = memory;\n" +
                    "pragma mmap_size = 30000000000;");
        }
        return connection;
    }

    protected synchronized PreparedStatement prepareStatement(String sql) throws SQLException {
        PreparedStatement stmt = getConnection().prepareStatement(sql);
        stmt.setFetchSize(10000);
        return stmt;
    }

    protected PreparedStatement prepareQuery(String sql) throws SQLException {
        PreparedStatement stmt = getConnection().prepareStatement(sql);
        stmt.setFetchSize(10000);
        return stmt;
    }

    public synchronized void executeStmt(String query) {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(query);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public <T> int[] executeBatch(Collection<T> objects, String query, BiConsumer<T, PreparedStatement> consumer) {
        if (objects.isEmpty()) return new int[0];
        synchronized (this) {
            try {
                getConnection().setAutoCommit(false);
            try (PreparedStatement ps = getConnection().prepareStatement(query)) {
                boolean clear = false;
                for (T object : objects) {
                    if (clear) ps.clearParameters();
                    clear = true;
                    consumer.accept(object, ps);
                    ps.addBatch();
                }
                return ps.executeBatch();
            }
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                try {
                    getConnection().commit();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                try {
                    getConnection().setAutoCommit(true);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected boolean query(String sql, Consumer<PreparedStatement> withStmt, Consumer<ResultSet> rsq) {
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

    protected boolean prepareStatement(String sql, Consumer<PreparedStatement> withStmt) {
        synchronized (this) {
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                stmt.setFetchSize(10000);
                withStmt.accept(stmt);
                return stmt.execute();
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    protected int update(String sql, Consumer<PreparedStatement> withStmt) {
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
        log.log(Level.WARNING, " - It will be corrected on shutdown");
        log.log(Level.WARNING, "========================================");
        e.printStackTrace();
        log.log(Level.WARNING, "========================================");
        System.exit(1);
    }

    public abstract void createTables();
}
