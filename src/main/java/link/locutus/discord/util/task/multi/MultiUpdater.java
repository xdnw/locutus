package link.locutus.discord.util.task.multi;

import com.politicsandwar.graphql.model.Nation;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.csv.file.NationsFile;
import link.locutus.discord.apiv3.csv.header.NationHeaderReader;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.network.ProxyHandler;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.io.IOException;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public class MultiUpdater {
    private final Auth auth;
    private final Map<Integer, Long> lastUpdated;
    private final Set<Integer> verified;
    private final Map<Integer, BigInteger> latestUids;
    private final SnapshotMultiData snapshotData;

    public MultiUpdater() throws IOException, ParseException {
        this.auth = Locutus.imp().getRootAuth().clone();
        ProxyHandler proxy = Locutus.imp().getProxy();
        auth.setProxy(proxy.getNextProxy());

        this.lastUpdated = Locutus.imp().getDiscordDB().getMultiReportLastUpdated(f -> DBNation.getById(f) != null);
        this.verified = Locutus.imp().getDiscordDB().getVerified();
        this.latestUids = Locutus.imp().getDiscordDB().getLatestUidByNation();
        this.snapshotData = new SnapshotMultiData();
    }

    private Map<Integer, String> discords = new Int2ObjectOpenHashMap<>();
    private Map<Integer, Set<Long>> discordIds = new Int2ObjectOpenHashMap();

    private Map<Integer, Integer> nationSharesTime = new Int2IntOpenHashMap();
    private Map<Integer, Integer> allianceSharesTime = new Int2IntOpenHashMap();

    private Map<Integer, Integer> sharesUidInAa = new Int2IntOpenHashMap();
    private Map<Integer, Integer> sharesUidSimilarTime = new Int2IntOpenHashMap();

    private void init() {
        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
            // todo load alliance mmeber activity

            // todo initialize the maps above

            // todo, only use this for calculating nation factor
            Set<DBNation> members = alliance.getMemberDBNations();

            int members1yNoDiscord = 0;
            int membersNoDiscord = 0;
            int similarTime = 0;

            for (DBNation nation : members) {
                double factor = 1;

                for (DBNation other : members) {
                    if (Math.abs(nation.active_m() - other.active_m()) < 15) {
                        similarTime++;
                        nationSharesTime.merge(nation.getNation_id(), 1, Integer::sum);
                        allianceSharesTime.merge(alliance.getAlliance_id(), 1, Integer::sum);
                    }
                }

                if (nation.isVerified() || nation.data()._discordStr() != null) {
                    if (nation.getAgeDays() < 365) {
                        members1yNoDiscord++;
                    } else {
                        membersNoDiscord++;
                    }
                }

                boolean customFlag = snapshotData.hasCustomFlag(nation.getNation_id()); // 8
                boolean pickedLand = snapshotData.hasPickedLand(nation.getNation_id()); // 4
                boolean customCurrency = snapshotData.hasCustomCurrency(nation.getNation_id()); // 2
                int customizationFactor = (customFlag ? 8 : 1) * (pickedLand ? 4 : 1) * (customCurrency ? 2 : 1);
            }
        }
    }

    private double getNationWeight(DBNation nation) {
        // not verified
        // not vm
        // not registered
        // not applicant
        // active since last update
        // similar login time to other nations in alliance <15m
        // num nations sharing ip

        // no discord set ingame
        // no custom flag
        // no picked land
        // uses dollar

    }

    public void run() {
        Set<DBNation> nations = Locutus.imp().getNationDB().getAllNations();
        // not vm
        // active since last update
        // has updated in past 30 days
        long now = System.currentTimeMillis();

        long cutoffNonMember = now - TimeUnit.DAYS.toMillis(45);
        long cutoffMemberRegistered = now - TimeUnit.DAYS.toMillis(60);
        long cutoffMemberUnregistered = now - TimeUnit.DAYS.toMillis(7);

        Map<DBAlliance, Long> allianceWeight = new Object2LongOpenHashMap<>();
        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {

        }

        nations.removeIf(f -> {
            if (f.getVm_turns() > 0) return true;
            if (verified.contains(f.getNation_id())) return true;
            Long lastUpdated = this.lastUpdated.get(f.getNation_id());
            if (lastUpdated != null) {
                long cutoff;
                if (f.getPositionEnum().id > Rank.APPLICANT.id) {
                    if (f.isVerified()) {
                        cutoff = cutoffMemberRegistered;
                    } else {
                        cutoff = cutoffMemberUnregistered;
                    }
                } else {
                    cutoff = cutoffNonMember;
                }
                if (lastUpdated > cutoff) return true;
                if (lastUpdated > f.lastActiveMs()) return true;
            }
            return false;
        });

        Map<DBAlliance, Long> aaMemberAge = new Int2LongOpenHashMap();

        List<DBNation> sorted = new ObjectArrayList<>(nations);
        sorted.sort((a, b) -> {
            Long aLastUpdated = this.lastUpdated.get(a.getNation_id());
            Long bLastUpdated = this.lastUpdated.get(b.getNation_id());
            long aDiff = aLastUpdated == null ? Long.MAX_VALUE : Math.min(now - aLastUpdated, TimeUnit.DAYS.toMillis(60));
            long bDiff = bLastUpdated == null ? Long.MAX_VALUE : Math.min(now - bLastUpdated, TimeUnit.DAYS.toMillis(60));
            if (aDiff != bDiff) return Long.compare(bDiff, aDiff);
            DBAlliance aAlliance = a.getAlliance();
            DBAlliance bAlliance = b.getAlliance();
            // Prefer in an alliance
            if (aAlliance == null && bAlliance != null) return 1;
            if (aAlliance != null && bAlliance == null) return -1;
            if (aAlliance != null && bAlliance != null) {
                long aAge = aaMemberAge.computeIfAbsent(aAlliance, f -> {
                    long age = 0;
                    Set<DBNation> members = f.getMemberDBNations();
                    for (DBNation nation : members) {
                        age += nation.getAgeDays();
                    }
                    return age;
                });
            }


            long aLastActive = a.lastActiveMs();
            long bLastActive = b.lastActiveMs();
            if (aLastActive != bLastActive) return Long.compare(bLastActive, aLastActive);
            return Long.compare(bLastUpdated, aLastUpdated);
        });

        DBNation toUpdate = sorted.get(0);

        MultiResult report = Locutus.imp().getDiscordDB().getMultiResult(toUpdate.getId());
        if (report == null) report = new MultiResult(toUpdate.getId());
        try {
            lastUpdated.put(toUpdate.getNation_id(), now);
            report.update(auth);
            lastUpdated.put(toUpdate.getNation_id(), System.currentTimeMillis());
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
