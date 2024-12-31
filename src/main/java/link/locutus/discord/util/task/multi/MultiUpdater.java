package link.locutus.discord.util.task.multi;

import com.politicsandwar.graphql.model.Nation;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.*;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.csv.file.NationsFile;
import link.locutus.discord.apiv3.csv.header.NationHeaderReader;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.network.ProxyHandler;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.io.IOException;
import java.math.BigInteger;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MultiUpdater {
    private final Auth auth;
    private final Map<Integer, Long> lastUpdated;
    private final Set<Integer> verified;
    private final Map<Integer, BigInteger> latestUids;
    private final Map<BigInteger, Set<Integer>> sharesUid;
    private final SnapshotMultiData snapshotData;

    private final Map<Integer, Integer> nationSharesUid = new Int2IntOpenHashMap();
    private final Map<Integer, Integer> nationSharesTimeAA = new Int2IntOpenHashMap();
    private final Map<Integer, Integer> nationSharesTimeUid = new Int2IntOpenHashMap();
    private final Map<Integer, Integer> nationSharesTimeUidAndAA = new Int2IntOpenHashMap();
    private final Map<Integer, Integer> allianceSharesTime = new Int2IntOpenHashMap();

    public MultiUpdater() throws IOException, ParseException {
        this.auth = new Auth(Settings.INSTANCE.NATION_ID, Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
        ProxyHandler proxy = Locutus.imp().getProxy();
        auth.setProxy(proxy.getNextProxy());

        this.lastUpdated = Locutus.imp().getDiscordDB().getMultiReportLastUpdated(f -> DBNation.getById(f) != null);
        this.verified = Locutus.imp().getDiscordDB().getVerified();
        this.latestUids = Locutus.imp().getDiscordDB().getLatestUidByNation();
        this.snapshotData = new SnapshotMultiData();

        this.sharesUid = new Object2ObjectOpenHashMap<>();

        init();
        printMultiInfo();
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

        Map<Integer, Boolean> isRegisted = new Int2BooleanOpenHashMap();
        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
            Set<DBNation> members = alliance.getMemberDBNations();
            for (DBNation nation : members) {
                boolean nat1Reg = isRegisted.computeIfAbsent(nation.getNation_id(), f -> nation.isVerified());
                for (DBNation other : members) {
                    boolean nat2Reg = isRegisted.computeIfAbsent(other.getNation_id(), f -> other.isVerified());
                    if (nat1Reg && nat2Reg) continue;
                    if (Math.abs(nation.active_m() - other.active_m()) < 15) {
                        nationSharesTimeAA.merge(nation.getNation_id(), 1, Integer::sum);
                        allianceSharesTime.merge(alliance.getAlliance_id(), 1, Integer::sum);
                    }
                }
            }
        }
    }

    private long getAgeBasedInterval(int ageDays) {
        if (ageDays < 60) return TimeUnit.DAYS.toMillis(29);
        if (ageDays < 360) return TimeUnit.DAYS.toMillis(60);
        if (ageDays < 730) return TimeUnit.DAYS.toMillis(180);
        return TimeUnit.DAYS.toMillis(360);
    }

    public long getRecommendedInterval(DBNation nation) {
        if (nation.active_m() > 2880 || verified.contains(nation.getNation_id())) {
            return TimeUnit.DAYS.toMillis(365 * 10);
        }
        int ageDays = nation.getAgeDays();
        long ageBased = getAgeBasedInterval(ageDays);
        if (ageDays > 60) {
            if (nation.isVerified()) {
                ageBased = Math.max(ageBased, TimeUnit.DAYS.toMillis(360));
            } else if (nation.getDiscordString() != null && !nation.getDiscordString().isEmpty()) {
                ageBased = Math.max(ageBased, TimeUnit.DAYS.toMillis(180));
            } else {
                boolean sharesUid = nationSharesUid.getOrDefault(nation.getNation_id(), 0) > 0;
                if (!sharesUid) {
                    if (snapshotData.hasCustomFlag(nation.getNation_id()) || snapshotData.hasCustomPortrait(nation.getNation_id())) {
                        ageBased = Math.max(ageBased, TimeUnit.DAYS.toMillis(180));
                    } else {
                        boolean sharesTime = nationSharesTimeAA.getOrDefault(nation.getNation_id(), 0) > 0;
                        if (!sharesTime && snapshotData.hasCustomCurrency(nation.getNation_id()) && snapshotData.hasPickedLand(nation.getNation_id())) {
                            ageBased = Math.max(ageBased, TimeUnit.DAYS.toMillis(120));
                        }
                    }
                }
            }
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
            long now = System.currentTimeMillis();
            // 2h between queue update
            if (now - lastUpdatedQueue < TimeUnit.HOURS.toMillis(2)) return;
            lastUpdatedQueue = now;
            Set<DBNation> nations = Locutus.imp().getNationDB().getAllNations();
            Map<DBNation, Double> weights = new Object2DoubleOpenHashMap<>();
            for (DBNation nation : nations) {
                int fail = failCount.getOrDefault(nation.getNation_id(), 0);
                if (fail >= 3) continue;
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

    private Map<Integer, Integer> failCount = new Int2IntOpenHashMap();
    private int updated = 0;
    private long lastUpdatedQueue = 0;

    public void run() {
        if (checkNearTc()) return;
        synchronized (this) {
            updateQueue();
        }
        Map.Entry<DBNation, Double> next;
        synchronized (this) {
            next = queue.poll();
        }
        updated++;
        if (next == null) return;
        DBNation toUpdate = next.getKey();
        update(toUpdate);
    }

    private boolean update(DBNation toUpdate) {
        synchronized (auth) {
            MultiResult report = Locutus.imp().getDiscordDB().getMultiResult(toUpdate.getId());
            if (report == null) report = new MultiResult(toUpdate.getId());
            try {
                lastUpdated.put(toUpdate.getNation_id(), System.currentTimeMillis());
                report.update(auth);
                lastUpdated.put(toUpdate.getNation_id(), System.currentTimeMillis());
                return true;
            } catch (Throwable e) {
                failCount.merge(toUpdate.getNation_id(), 1, Integer::sum);
                e.printStackTrace();
                return false;
            }
        }
    }

    public void forceUpdate(Set<DBNation> nations, BiConsumer<DBNation, Boolean> onEach, long delay, int retry) {
        Set<Integer> nationIds = new IntOpenHashSet(nations.size());
        for (DBNation nation : nations) nationIds.add(nation.getNation_id());
        synchronized (this) {
            queue.removeIf(f -> nationIds.contains(f.getKey().getNation_id()));
        }
        Runnable sleepCall = () -> {
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        List<DBNation> nationList = new ObjectArrayList<>(nations);
        int currRetry = 0;
        for (int i = 0; i < nationList.size(); i++) {
            DBNation nation = nationList.get(i);
            boolean result = update(nation);
            if (!result) {
                if (currRetry < retry) {
                    i--;
                    currRetry++;
                    sleepCall.run();
                    continue;
                }
            }
            boolean sleep = i < nationList.size() - 1;
            onEach.accept(nation, result);
            if (sleep) {
                sleepCall.run();
            }
        }
    }

    private void printMultiInfo() throws IOException, ParseException {
        System.out.println("STARTED LOCUTUS!!!");
        System.out.println("STARTED MULTI UPDATER!!!");
        updateQueue();
        System.out.println("MULTI QUEUE CALCULATED!!!");

        Set<DBNation> nations = getQueue().stream().map(Map.Entry::getKey).collect(Collectors.toSet());

        double updatesPerDay = 0;
        long oneDay = TimeUnit.DAYS.toMillis(1);
        for (DBNation nation : nations) {
            double days = ((double) getRecommendedInterval(nation) / oneDay);
            updatesPerDay += 1d / days;
        }
        System.out.println("Updates per day: " + updatesPerDay + " | " + nations.size());
        ValueStore<DBNation> cacheStore = PlaceholderCache.createCache(nations, DBNation.class);

        for (Map.Entry<DBNation, Double> entry : getQueue()) {
            DBNation nation = entry.getKey();
            double factor = entry.getValue();
            List<String> row = new ArrayList<>();
            row.add(nation.getSheetUrl());
            DBAlliance aa = nation.getAlliance();
            if (aa != null) {
                row.add(aa.getSheetUrl());
            } else {
                row.add("");
            }
            row.add(nation.getPositionEnum().name());
            row.add(nation.lastActiveMs() + "");
            row.add(nation.getVm_turns() + "");
            row.add(nation.getAgeDays() + "");
            row.add(nation.getCities() + "");
            row.add(nation.getDiscordString() + "");
            row.add(nation.getUserDiscriminator() + "");
            row.add(nation.isVerified() + "");
            row.add(nation.hasProvidedIdentity(cacheStore) + "");
            row.add(factor + "");
            System.out.println(StringMan.join(row, "\t"));
        }
    }
}
