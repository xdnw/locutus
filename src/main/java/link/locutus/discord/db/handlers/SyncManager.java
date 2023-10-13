package link.locutus.discord.db.handlers;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.User;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SyncManager {
    private final long serialVersionUID;
    private final Locutus locutus;

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

    public void syncPush(long userId, long timestamp) {

    }

    public void syncFetch(long userId, long timestamp) {

    }

    private void sendData(User user, DataOutputStream out) {
        Set<Long> guildIds = user.getMutualGuilds().stream().map(ISnowflake::getIdLong).collect(Collectors.toSet());
        sendData(guildIds, out);
    }

    private void sendData(Set<Long> guildIds, DataOutputStream out, long timestamp) {
        iterateInstances(guildIds, new Consumer<SyncableDatabase>() {
            @Override
            public void accept(SyncableDatabase db) {
                Map<String, String> tables = db.getTablesToSync();
                for (Map.Entry<String, String> entry : tables.entrySet()) {
                    String tableName = entry.getKey();
                    String columnName = entry.getValue();

                    List<Object[]> rows = db.getTableData(db.getConnection(), tableName, columnName, timestamp);
                }



            }
        });
    }

    private void storeData(DataInputStream in, long timestamp) {
        // if timestamp == 0, store all data, delete rows not matching (but only if new rows are > 0, and table allows deletion)

    }


//

}
