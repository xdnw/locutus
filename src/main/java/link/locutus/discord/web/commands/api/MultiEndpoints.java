package link.locutus.discord.web.commands.api;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.entities.PwUid;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllowDeleted;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.task.multi.AdvMultiReport;
import link.locutus.discord.util.task.multi.MultiResult;
import link.locutus.discord.util.task.multi.SnapshotMultiData;
import link.locutus.discord.web.commands.ReturnType;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MultiEndpoints {
    @Command(viewable = true)
    @ReturnType(MultiResult.class)
    public MultiResult multi_buster(@AllowDeleted DBNation nation, @Default Boolean forceUpdate) throws InterruptedException {
        synchronized (MultiEndpoints.class) {
            MultiResult result = Locutus.imp().getDiscordDB().getMultiResult(nation.getId());
            Auth auth = Locutus.imp().getRootAuth();
            result.updateIfOutdated(auth, forceUpdate == Boolean.TRUE ? TimeUnit.DAYS.toMillis(1) : Long.MAX_VALUE, true);
            return result.loadNames();
        }
    }

    private static WeakReference<SnapshotMultiData> snapshotData = new WeakReference<>(null);
    private static WeakReference<Map<Integer, PwUid>> uids = new WeakReference<>(null);

    @Command(viewable = true)
    @ReturnType(AdvMultiReport.class)
    public AdvMultiReport multi_v2(@AllowDeleted DBNation nation, @Default Boolean forceUpdate) throws IOException, ParseException {
        synchronized (MultiEndpoints.class) {
            SnapshotMultiData snapshot = snapshotData.get();
            if (snapshot == null) {
                snapshot = new SnapshotMultiData();
                snapshotData = new WeakReference<>(snapshot);
            }

            Map<Integer, PwUid> uidMap = uids.get();
            if (uidMap == null) {
                uidMap = Locutus.imp().getDiscordDB().getLatestUidByNation();
                uids = new WeakReference<>(uidMap);
            }

            return new AdvMultiReport(nation, snapshot, uidMap, true, forceUpdate ? TimeUnit.DAYS.toMillis(1) : Long.MAX_VALUE);
        }
    }
}
