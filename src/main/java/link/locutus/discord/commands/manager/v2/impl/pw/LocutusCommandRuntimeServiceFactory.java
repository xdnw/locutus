package link.locutus.discord.commands.manager.v2.impl.pw;

import link.locutus.discord.Locutus;
import link.locutus.discord._main.ILoader;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationModifier;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationSnapshotService;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeServices;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.util.TimeUtil;

import java.io.IOException;
import java.text.ParseException;

public final class LocutusCommandRuntimeServiceFactory {
    private LocutusCommandRuntimeServiceFactory() {
    }

    public static CommandRuntimeServices create(Locutus locutus) {
        return create(locutus, Locutus.loader());
    }

    public static CommandRuntimeServices create(Locutus locutus, ILoader loader) {
        return CommandRuntimeServices.builder(createNationSnapshots(locutus, loader))
                .guildDb(guild -> guild == null ? null : locutus.getGuildDB(guild))
                .guildDbById(locutus::getGuildDB)
                .guildDatabases(() -> locutus.getGuildDatabases().values())
                .rootCoalitionServer(locutus::getRootCoalitionServer)
                .shardManager(loader::getShardManager)
                .nationDb(loader::getNationDB)
                .warDb(loader::getWarDB)
                .bankDb(loader::getBankDB)
                .stockDb(loader::getStockDB)
                .baseballDb(loader::getBaseballDB)
                .forumDb(loader::getForumDB)
                .discordDb(loader::getDiscordDB)
                .tradeManager(loader::getTradeManager)
                .build();
    }

    private static NationSnapshotService createNationSnapshots(Locutus locutus, ILoader loader) {
        return modifier -> {
            if (modifier == null) {
                return loader.getNationDB();
            }
            if (modifier.getResolvedSnapshot() != null) {
                return modifier.getResolvedSnapshot();
            }
            if (modifier.timestamp != null) {
                DataDumpParser parser = locutus.getDataDumper(true);
                try {
                    parser.load();
                    Long day = TimeUtil.getDay(modifier.timestamp);
                    if (day != null && day != TimeUtil.getDay()) {
                        INationSnapshot snapshot = parser.getSnapshotDelegate(day, true, modifier.load_snapshot_vm);
                        modifier.setResolvedSnapshot(snapshot);
                        return snapshot;
                    }
                } catch (ParseException | IOException e) {
                    throw new RuntimeException(e);
                }
            }
            INationSnapshot snapshot = loader.getNationDB();
            modifier.setResolvedSnapshot(snapshot);
            return snapshot;
        };
    }
}