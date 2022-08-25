package link.locutus.discord.db;


import com.ptsmods.mysqlw.Database;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.AlertUtil;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.Collection;
import java.util.function.BiConsumer;
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
            Class.forName("org.sqlite.JDBC");
            String connectStr = "jdbc:sqlite:";
//        if (inMemory) connectStr += ":memory:";
            connectStr += dbLocation;
            forceConnection();
        } else {
            throw new IllegalArgumentException("Either SQLite OR MySQL must be enabled. (not both, or none)");
        }
        init();
    }


    public DSLContext ctx() {
        return ctx;
    }

    public DBMainV3 init() {
        createTables();
        return this;
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
        this.ctx = DSL.using(this.getConnection(), SQLDialect.SQLITE);
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
        log.log(Level.WARNING, " - It will be corrected on shutdown");
        log.log(Level.WARNING, "========================================");
        e.printStackTrace();
        log.log(Level.WARNING, "========================================");
        System.exit(1);
    }

    public abstract void createTables();
}
