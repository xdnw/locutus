package link.locutus.discord.util.task.multi;

import com.politicsandwar.graphql.model.Nation;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.*;
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
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.io.IOException;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class MultiUpdater {
    private final Auth auth;
    private final Map<Integer, Long> lastUpdated;
    private final Set<Integer> verified;
    private final Map<Integer, BigInteger> latestUids;
    private final Map<BigInteger, Set<Integer>> sharesUid;
    private final SnapshotMultiData snapshotData;

    private Map<Integer, Integer> nationSharesUid = new Int2IntOpenHashMap();
    private Map<Integer, Integer> nationSharesTimeAA = new Int2IntOpenHashMap();
    private Map<Integer, Integer> nationSharesTimeUid = new Int2IntOpenHashMap();
    private Map<Integer, Integer> nationSharesTimeUidAndAA = new Int2IntOpenHashMap();
    private Map<Integer, Integer> allianceSharesTime = new Int2IntOpenHashMap();

    public MultiUpdater() throws IOException, ParseException {
        this.auth = Locutus.imp().getRootAuth().clone();
        ProxyHandler proxy = Locutus.imp().getProxy();
        auth.setProxy(proxy.getNextProxy());

        this.lastUpdated = Locutus.imp().getDiscordDB().getMultiReportLastUpdated(f -> DBNation.getById(f) != null);
        this.verified = Locutus.imp().getDiscordDB().getVerified();
        this.latestUids = Locutus.imp().getDiscordDB().getLatestUidByNation();
        this.snapshotData = new SnapshotMultiData();

        this.sharesUid = new Object2ObjectOpenHashMap<>();

        init();
    }

    private Map<Integer, Integer> sharesUidInAa = new Int2IntOpenHashMap();

    private void init() {
        for (Map.Entry<Integer, BigInteger> entry : latestUids.entrySet()) {
            sharesUid.computeIfAbsent(entry.getValue(), f -> new IntOpenHashSet()).add(entry.getKey());
        }
        for (Map.Entry<BigInteger, Set<Integer>> entry : sharesUid.entrySet()) {
            List<DBNation> nations = sharesUid.get(entry.getKey()).stream().map(DBNation::getById).filter(f -> f != null).collect(Collectors.toList());
            for (DBNation nation : nations) {
                nationSharesUid.put(nation.getNation_id(), nations.size());
            }

            for (int i = 0; i < nations.size(); i++) {
                DBNation nat1 = nations.get(i);
                int activeM1 = nat1.active_m();
                for (int j = i + 1; j < nations.size(); j++) {
                    DBNation nat2 = nations.get(j);
                    int activeM2 = nat2.active_m();
                    boolean sameAa = nat1.getAlliance_id() == nat2.getAlliance_id() && nat1.getAlliance_id() != 0;
                    if (sameAa) {
                        sharesUidInAa.merge(nat1.getNation_id(), 1, Integer::sum);
                    }
                    if (Math.abs(activeM1 - activeM2) < 15) {
                        nationSharesTimeUid.merge(nat1.getNation_id(), 1, Integer::sum);
                        nationSharesTimeUid.merge(nat2.getNation_id(), 1, Integer::sum);
                        if (sameAa) {
                            nationSharesTimeUidAndAA.merge(nat1.getNation_id(), 1, Integer::sum);
                        }
                    }
                }
            }
        }

        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
            Set<DBNation> members = alliance.getMemberDBNations();
            for (DBNation nation : members) {
                for (DBNation other : members) {
                    if (Math.abs(nation.active_m() - other.active_m()) < 15) {
                        nationSharesTimeAA.merge(nation.getNation_id(), 1, Integer::sum);
                        allianceSharesTime.merge(alliance.getAlliance_id(), 1, Integer::sum);
                    }
                }
            }
        }
    }

    private long getAgeBasedInterval(int ageDays) {
        if (ageDays < 15) return TimeUnit.DAYS.toMillis(7);
        if (ageDays < 60) return TimeUnit.DAYS.toMillis(15);
        if (ageDays < 90) return TimeUnit.DAYS.toMillis(30);
        if (ageDays < 180) return TimeUnit.DAYS.toMillis(90);
        return TimeUnit.DAYS.toMillis(180);
    }

    public long getRecommendedInterval(DBNation nation) {
        if (nation.active_m() > 10080 || verified.contains(nation.getNation_id())) {
            return TimeUnit.DAYS.toMillis(365 * 10);
        }
        long ageBased = getAgeBasedInterval(nation.getAgeDays());
        if (nation.isVerified()) {
            if (ageBased < TimeUnit.DAYS.toMillis(90)) {
                ageBased *= 3;
            }
            return Math.max(ageBased, TimeUnit.DAYS.toMillis(45));
        }
        return ageBased;
    }

    private double getNationWeight(DBNation nation, long now) {
        long requiredDiff = getRecommendedInterval(nation);
        long lastUpdated = this.lastUpdated.getOrDefault(nation.getNation_id(), 0L);
        long diff = System.currentTimeMillis() - lastUpdated;
        if (nation.lastActiveMs() < lastUpdated) return -1;
        if (diff < requiredDiff) return -1;

        double weight = 1;

        boolean irlVerified = this.verified.contains(nation.getNation_id());
        if (irlVerified) {
            return 0;
        }
        boolean vm = nation.getVm_turns() > 0;

        boolean registered = nation.isVerified(); // strong evidence
        if (registered) {
            weight *= 0.05;
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 registered");

        boolean isMember = nation.getPositionEnum().id > Rank.APPLICANT.id;
        if (!isMember) {
            weight *= 0.8;
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 isMember");

        double ageFactor = Math.pow(300d / (nation.getAgeDays() + 300d), 0.75);

        boolean lowCities = nation.getCities() < 7;
        if (!lowCities) {
            weight *= ageFactor;
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 lowCities | " + nation.getAgeDays() + " | " + 300 / (nation.getAgeDays() + 300));

        SnapshotMultiData.MultiData data = snapshotData.data.get(nation.getNation_id());
        if (data != null) {
            if (data.portraitUrl() != null && !data.portraitUrl().isEmpty()) {
                weight *= 0.6;
            }
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 portrait");

        boolean customFlag = snapshotData.hasCustomFlag(nation.getNation_id());
        if (customFlag) {
            weight *= 0.6;
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 customFlag");

        boolean pickedLand = snapshotData.hasPickedLand(nation.getNation_id());
        if (pickedLand) {
            weight *= 0.7;
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 pickedLand");

        boolean customCurrency = snapshotData.hasCustomCurrency(nation.getNation_id());
        if (customCurrency) {
            weight *= 0.8;
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 customCurrency");

        String discStr = nation.getDiscordString();
        if (discStr != null && !discStr.isEmpty()) {
            weight *= 0.2;
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 discord");

        int sharesUid = nationSharesUid.getOrDefault(nation.getNation_id(), 0);
        if (sharesUid > 0) {
            weight = weight * (1 + (Math.pow(0.2, sharesUid + 4) - 1) * 20);
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 shares uid");

        int timeAA = nationSharesTimeAA.getOrDefault(nation.getNation_id(), 0);
        if (timeAA > 0) {
            weight = weight * (1 + (Math.pow(0.2, timeAA + 4) - 1) * 5);
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 shares timeAA");

        int timeUid = nationSharesTimeUid.getOrDefault(nation.getNation_id(), 0);
        if (timeUid > 0) {
            weight = weight * (1 + (Math.pow(0.2, timeUid + 4) - 1) * 40);
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 shares timeUid");

        int timeUidAndAA = nationSharesTimeUidAndAA.getOrDefault(nation.getNation_id(), 0);
        if (timeUidAndAA > 0) {
            weight = weight * (1 + (Math.pow(0.2, timeUidAndAA + 4) - 1) * 40);
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 shares timeUidAndAA");

        int allianceSharesTime = this.allianceSharesTime.getOrDefault(nation.getAlliance_id(), 0);
        if (allianceSharesTime > 0) {
            weight = weight * (1 + (Math.pow(0.2, allianceSharesTime + 4) - 1) * 10);
        }

        if (weight == 0) throw new IllegalArgumentException(nation.getNation() + " weight 0 shares time");
        return weight;
    }

    private final ArrayDeque<Map.Entry<DBNation, Double>> queue = new ArrayDeque();

    public void updateQueue() {
        if (queue.isEmpty()) {
            Set<DBNation> nations = Locutus.imp().getNationDB().getAllNations();
            Map<DBNation, Double> weights = new Object2DoubleOpenHashMap<>();
            for (DBNation nation : nations) {
                double weight = getNationWeight(nation, System.currentTimeMillis());
                if (weight < 0) continue;
                weights.put(nation, weight);
            }
            List<Map.Entry<DBNation, Double>> sorted = new ArrayList<>(weights.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            queue.addAll(sorted);
        }
    }

    public ArrayDeque<Map.Entry<DBNation, Double>> getQueue() {
        return queue;
    }

    private boolean checkNearTc() {
        long now = System.currentTimeMillis();
        boolean dc = TimeUtil.getDayTurn() == 0;
        int beforeTime = dc ? 15 : 5;
        int afterTime = dc ? 25 : 10;

        long turn1 = TimeUtil.getTurn(now - TimeUnit.MINUTES.toMillis(beforeTime));
        long turn2 = TimeUtil.getTurn(now + TimeUnit.MINUTES.toMillis(afterTime));
        return turn1 != turn2;
    }

    private int updated = 0;

    public void run() {
        if (checkNearTc()) return;
        updateQueue();
        Map.Entry<DBNation, Double> next = queue.poll();
        updated++;
        if (next == null) return;
        DBNation toUpdate = next.getKey();
        MultiResult report = Locutus.imp().getDiscordDB().getMultiResult(toUpdate.getId());
        if (report == null) report = new MultiResult(toUpdate.getId());
        try {
            lastUpdated.put(toUpdate.getNation_id(), System.currentTimeMillis());
            report.update(auth);
            lastUpdated.put(toUpdate.getNation_id(), System.currentTimeMillis());
            System.out.println("Updated Multi Buster: #" + updated + " | Remaining: #" + queue.size());
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }
}