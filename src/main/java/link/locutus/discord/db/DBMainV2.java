package link.locutus.discord.db;

import ch.qos.logback.classic.db.names.TableName;
import com.ptsmods.mysqlw.Database;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.FileUtil;
import org.apache.http.util.TextUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DBMainV2 implements Closeable {
    private boolean isDelegate;
    private final Database db;
    public DBMainV2(String name) throws SQLException {
        this(name, true);
    }
    public DBMainV2(String name, boolean init) throws SQLException {
        this(Settings.INSTANCE.DATABASE, name, init);
    }

    public DBMainV2(Settings.DATABASE config, String name, boolean init) throws SQLException {
        if (config.SQLITE.USE) {
            File file = new File(config.SQLITE.DIRECTORY + File.separator + name + ".db");
            // create file directory if not exist
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            this.db = Database.connect(file);
        } else {
            throw new IllegalArgumentException("Either SQLite OR MySQL must be enabled. (not both, or none)");
//            this.db = Database.connect(config.MYSQL.HOST,
//                    config.MYSQL.PORT,
//                    name,
//                    config.MYSQL.USER,
//                    config.MYSQL.PASSWORD
//                    );
        }
        init();
    }

    public DBMainV2(File file) throws SQLException {
        this.db = Database.connect(file);
        init();
    }

    public DBMainV2(String host, int port, String name, String username, String password) throws SQLException {
        this.db = Database.connect(host, port, name, username, password);
        init();
    }

    public DBMainV2(Database other) {
        this.db = other;
        this.isDelegate = true;
        init();
    }

    private void init() {
        // Do any initialization here

        createTables();
    }

    public boolean tableExists(String tableName) throws SQLException {
        DatabaseMetaData meta = getConnection().getMetaData();
        try (ResultSet resultSet = meta.getTables(null, null, tableName, new String[] {"TABLE"})) {

            return resultSet.next();
        }
    }

    protected void createTables() {

    }

    public Database getDb() {
        return db;
    }

    @Override
    public void close() throws IOException {
        try {
            db.getConnection().close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * deprecated
     */


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
                if (objects.size() == 1) {
                    try (PreparedStatement ps = getConnection().prepareStatement(query)) {
                        consumer.accept(objects.iterator().next(), ps);
                        int result = ps.executeUpdate();
                        return new int[]{result};
                    }
                }
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
                finally {
                    try {
                        getConnection().commit();
                    } catch (SQLException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    } finally {
                        try {
                            getConnection().setAutoCommit(true);
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

    protected int update(String sql, Object... values) {
        synchronized (this) {
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) {
                    stmt.setObject(i + 1, values[i]);
                }
                return stmt.executeUpdate();
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
        return getDb().getConnection();
    }
}
