package link.locutus.discord.db.handlers;

import io.javalin.util.function.ThrowingRunnable;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jooq.meta.derby.sys.Sys;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SyncManager {
    private final long serialVersionUID;
    private final Locutus locutus;

    private enum DatabaseType {
        NATION,
        DISCORD,
        GUILD,
        ;
    }

    private final Map<Map.Entry<DatabaseType, Long>, Map.Entry<Long, Long>> syncDates = new ConcurrentHashMap<>();

    private void loadSyncDates() {
        File file = new File("syncdates.dat");
        // Create the file if it does not exist.
        if (!file.exists()) {
            return;
        }

        try (DataInputStream inputStream = new DataInputStream(new FileInputStream(file))) {
            while (inputStream.available() > 0) {
                byte typeOrdinal = inputStream.readByte();
                DatabaseType type = DatabaseType.values()[typeOrdinal];
                long id = inputStream.readLong();
                long send = inputStream.readLong();
                long receive = inputStream.readLong();

                syncDates.put(Map.entry(type, id), Map.entry(send, receive));
            }
        } catch (IOException e) {
            // Handle IOException, e.g., log an error or throw an exception.
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        String input = "AA:Singularity";
        List<String> splitAnd = StringMan.split(input, ',');
        for (String andGroup : splitAnd) {
            List<String> splitXor = StringMan.split(andGroup, '^');
            if (splitXor.isEmpty()) {
                throw new IllegalArgumentException("Invalid group: `" + andGroup + "`: Empty group");
            }

            for (String xorGroup : splitXor) {
                List<String> splitOr = StringMan.split(xorGroup, '|');
                if (splitOr.isEmpty()) {
                    throw new IllegalArgumentException("Invalid group: `" + xorGroup + "`: Empty group");
                }

                for (String elem : splitOr) {
                    if (elem.isEmpty()) {
                        if (xorGroup.isEmpty()) {
                            if (andGroup.isEmpty()) {
                                throw new IllegalArgumentException("Invalid group: `" + input + "`: Empty group");
                            }
                            throw new IllegalArgumentException("Invalid group: `" + andGroup + "`: Empty group");
                        }
                        throw new IllegalArgumentException("Invalid group: `" + xorGroup + "`: Empty group");
                    }
                    char char0 = elem.charAt(0);
                }
            }
        }
    }

    private void writeSyncDates() {
        File file = new File("syncdates.dat");
        try (DataOutputStream outputStream = new DataOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<Map.Entry<DatabaseType, Long>, Map.Entry<Long, Long>> entry : syncDates.entrySet()) {
                Map.Entry<DatabaseType, Long> key = entry.getKey();
                Map.Entry<Long, Long> value = entry.getValue();

                outputStream.writeByte(key.getKey().ordinal());
                outputStream.writeLong(key.getValue());
                outputStream.writeLong(value.getKey());
                outputStream.writeLong(value.getValue());
            }
        } catch (IOException e) {
            // Handle IOException, e.g., log an error or throw an exception.
            e.printStackTrace();
        }
    }



    private void setLastSyncDate(SyncableDatabase db, long date, boolean isSend) {
        throw new UnsupportedOperationException("TODO FIXME");
//        try (Connection connection = getConnection();
//             PreparedStatement preparedStatement = connection.prepareStatement(
//                     "INSERT OR REPLACE INTO LAST_SYNC_DATE (id, date_updated) VALUES (?, ?)")
//        ) {
//            preparedStatement.setInt(1, isSend ? 1 : 2);
//            preparedStatement.setLong(2, date);
//            preparedStatement.executeUpdate();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
    }

    private long getLastSyncDate(SyncableDatabase db, boolean isSend) {
        throw new UnsupportedOperationException("TODO FIXME");
//        long lastSyncDate = 0;
//        try (Connection connection = getConnection();
//             PreparedStatement preparedStatement = connection.prepareStatement("SELECT date_updated FROM LAST_SYNC_DATE WHERE id = ?")
//        ) {
//            preparedStatement.setInt(1, isSend ? 1 : 2);
//            ResultSet resultSet = preparedStatement.executeQuery();
//            if (resultSet.next()) {
//                lastSyncDate = resultSet.getLong("date_updated");
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//        return lastSyncDate;
    }

    SyncManager(Locutus locutus) {
        this.locutus = locutus;
        GuildDB rootDB = locutus.getRootDb();
        if (rootDB == null) {
            throw new IllegalArgumentException("Root DB is null");
        }
        Map<String, String> guildTables = rootDB.getTablesToSync();
        Map<String, String> discordTables = locutus.getDiscordDB().getTablesToSync();
        Map<String, String> nationTables = locutus.getNationDB().getTablesToSync();
        // combine the hashcodes of the above into a long
        long hashCode = 17L;
        hashCode = hashCode * 31L + guildTables.hashCode();
        hashCode = hashCode * 31L + discordTables.hashCode();
        hashCode = hashCode * 31L + nationTables.hashCode();

        this.serialVersionUID = hashCode;
    }

    private void iterateInstances(Set<Long> guildIds, Consumer<SyncableDatabase> onEach) {
        onEach.accept(locutus.getDiscordDB());
        onEach.accept(locutus.getNationDB());
        for (long guildId : guildIds) {
            GuildDB guildDB = locutus.getGuildDB(guildId);
            if (guildDB != null) {
                onEach.accept(guildDB);
            }
        }
    }

    private void sendData(User user, DataOutputStream out, long bufferTime) throws IOException {
        PrivateChannel channel = RateLimitUtil.complete(user.openPrivateChannel());
        Set<Long> guildIds = user.getMutualGuilds().stream().map(ISnowflake::getIdLong).collect(Collectors.toSet());
        sendData(guildIds, out, new Consumer<Runnable>() {
            @Override
            public void accept(Runnable runnable) {
                throw new UnsupportedOperationException("TODO FIXME");

            }
        }, bufferTime);
    }

    private DatabaseType getType(SyncableDatabase db) {
        if (db instanceof NationDB) {
            return DatabaseType.NATION;
        }
        if (db instanceof DiscordDB) {
            return DatabaseType.DISCORD;
        }
        if (db instanceof GuildDB) {
            return DatabaseType.GUILD;
        }
        throw new IllegalArgumentException("Unknown database type: " + db.getClass().getName());
    }

    private void writeDatabaseHeader(SyncableDatabase db, DataOutputStream out) throws IOException {
        DatabaseType type = getType(db);
        out.writeByte(type.ordinal());
        if (db instanceof GuildDB guildDB) {
            long subId = guildDB.getIdLong();
            out.writeLong(subId);
        }
    }


    private SyncableDatabase readDatabaseHeader(DataInputStream in) throws IOException {
        int ordinal = in.read();
        DatabaseType type = DatabaseType.values()[ordinal];
        if (type == DatabaseType.NATION) {
            return locutus.getNationDB();
        }
        if (type == DatabaseType.DISCORD) {
            return locutus.getDiscordDB();
        }
        if (type == DatabaseType.GUILD) {
            long subId = in.readLong();
            return locutus.getGuildDB(subId);
        }
        throw new IOException("Unknown database type: " + type);
    }

    private void sendData(Set<Long> guildIds, DataOutputStream out, Consumer<Runnable> setLastSync, long bufferTime) throws IOException {
        iterateInstances(guildIds, new ThrowingConsumer<SyncableDatabase>() {
            @Override
            public void acceptThrows(SyncableDatabase db) throws IOException {
                long dateLastSent = getLastSyncDate(db, true);
                long lastModified = db.getLastModified();
                if (lastModified == 0) lastModified = System.currentTimeMillis();
                if (dateLastSent + bufferTime >= lastModified) {
                    return;
                }
                out.writeBoolean(true);
                writeDatabaseHeader(db, out);
                out.writeLong(dateLastSent);
                out.writeLong(lastModified);

                Map<String, String> tables = db.getTablesToSync();
                for (Map.Entry<String, String> entry : tables.entrySet()) {
                    String tableName = entry.getKey();
                    String columnName = entry.getValue();

                    boolean wroteTableHeader = false;

                    List<Object[]> added = db.getTableData(tableName, columnName, dateLastSent);
                    if (!added.isEmpty()) {
                        out.writeBoolean(true);
                        wroteTableHeader = true;
                        out.writeUTF(tableName);
                        db.serializeSQLRowData(added, out);
                    } else {
                        out.writeBoolean(false);
                    }

                    List<Object[]> deleted = db.getDeletionData(tableName, dateLastSent);
                    if (!deleted.isEmpty()) {
                        out.writeBoolean(true);
                        if (!wroteTableHeader) {
                            out.writeUTF(tableName);
                        }
                        db.serializeSQLRowData(deleted, out);
                    } else {
                        out.writeBoolean(false);
                    }
                }
                out.writeUTF("");
                long finalLastModified = lastModified;
                setLastSync.accept(() -> setLastSyncDate(db, finalLastModified, true));
            }
        });
        out.writeBoolean(false);
    }

    private void storeData(DataInputStream in) throws IOException {
        while (in.readBoolean()) {
            SyncableDatabase db = readDatabaseHeader(in);
            long lastSent = in.readLong();
            long lastModified = in.readLong();

            while (true) {
                String tableName = in.readUTF();
                if (tableName.isEmpty()) {
                    break;
                }
                List<String> columns = db.getTableColumns(tableName);
                if (columns == null) {
                    throw new IOException("Unknown table: " + tableName);
                }
                Map<String, String> primaryKeys = db.getPrimaryKeys(tableName);
                List<String> keyNames = new ArrayList<>(primaryKeys.keySet());

                if (in.readBoolean()) {
                    db.writeData(db, tableName, columns, in);
                }
                if (in.readBoolean()) {
                    db.writeDeletions(db, tableName, keyNames, in);
                }
            }
            setLastSyncDate(db, lastModified, false);
        }
    }
}
