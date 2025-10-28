package link.locutus.discord.util.task.multi;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.network.ProxyHandler;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Auth;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class MultiUpdater {
    private final Auth auth;
    private Map<Integer, Long> lastUpdated = new Int2LongOpenHashMap();
    private final Set<Integer> verified;
    private final SnapshotMultiData snapshotData;

    private final Int2IntOpenHashMap nationSharesUid = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap nationSharesTimeAA = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap nationSharesTimeUid = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap nationSharesTimeUidAndAA = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap allianceSharesTime = new Int2IntOpenHashMap();
    // TODO will use later
    private final Int2IntOpenHashMap sharesUidInAa = new Int2IntOpenHashMap();
    private final IntArrayList allianceNationIds = new IntArrayList();
    private final IntArrayList allianceActiveMinutes = new IntArrayList();
    private final BooleanArrayList allianceVerifiedFlags = new BooleanArrayList();

    public MultiUpdater() throws IOException, ParseException {
        this.auth = new Auth(Settings.INSTANCE.NATION_ID, Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
        ProxyHandler proxy = Locutus.imp().getProxy();
        auth.setProxy(proxy.getNextProxy());

        this.verified = Locutus.imp().getDiscordDB().getVerified();
        this.snapshotData = new SnapshotMultiData();

        init();
        printMultiInfo();
    }



    private void init() {
        loadUidStatistics();      // no more Map<PwUid, Set<Integer>>
        loadAllianceTimeShares(); // the second block is unchanged
    }

    private static final String LATEST_UUID_SQL = """
        SELECT nation_id,
               uuid
        FROM (
            SELECT u.nation_id,
                   u.uuid,
                   ROW_NUMBER() OVER (PARTITION BY u.nation_id ORDER BY u.date DESC) AS rn
            FROM UUIDS u
        ) x
        WHERE rn = 1
        ORDER BY uuid, nation_id
        """;

    private void loadUidStatistics() {
        try (PreparedStatement ps = Locutus.imp().getDiscordDB().prepareQuery(LATEST_UUID_SQL);
             ResultSet rs = ps.executeQuery()) {

            byte[] lastUuid = null;
            List<DBNation> group = new ObjectArrayList<>(4);     // typical group is tiny

            while (rs.next()) {
                byte[] uuidBytes = rs.getBytes("uuid");
                if (lastUuid == null || !Arrays.equals(lastUuid, uuidBytes)) {
                    handleUidGroup(group);                 // consume previous uuid
                    group.clear();
                    lastUuid = uuidBytes;
                }

                DBNation nation = DBNation.getById(rs.getInt("nation_id"));
                if (nation != null) {
                    group.add(nation);
                }
            }
            handleUidGroup(group);                         // flush final group
        } catch (SQLException e) {
            throw new RuntimeException("Unable to load latest uuid data", e);
        }
    }

    private void handleUidGroup(List<DBNation> nations) {
        if (nations.isEmpty()) return;

        final int groupSize = nations.size();
        for (DBNation nation : nations) {
            nationSharesUid.put(nation.getNation_id(), groupSize);
        }
        if (groupSize == 1) return;

        for (int i = 0; i < groupSize; i++) {
            DBNation n1 = nations.get(i);
            final int n1Active = n1.active_m();
            final int n1Id = n1.getNation_id();
            final int n1AA = n1.getAlliance_id();

            for (int j = i + 1; j < groupSize; j++) {
                DBNation n2 = nations.get(j);
                final boolean sameAA = n1AA != 0 && n1AA == n2.getAlliance_id();

                if (sameAA) {
                    sharesUidInAa.addTo(n1Id, 1);
                }

                if (Math.abs(n1Active - n2.active_m()) < 15) {
                    nationSharesTimeUid.addTo(n1Id, 1);
                    nationSharesTimeUid.addTo(n2.getNation_id(), 1);

                    if (sameAA) {
                        nationSharesTimeUidAndAA.addTo(n1Id, 1);
                    }
                }
            }
        }
    }

    private void loadAllianceTimeShares() {
        Map<Integer, Boolean> isRegisted = new Int2BooleanOpenHashMap();
        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
            Set<DBNation> members = alliance.getMemberDBNations();
            int size = members.size();
            if (size == 0) continue;
            allianceNationIds.clear();
            allianceActiveMinutes.clear();
            allianceVerifiedFlags.clear();
            allianceNationIds.ensureCapacity(size);
            allianceActiveMinutes.ensureCapacity(size);
            allianceVerifiedFlags.ensureCapacity(size);
            for (DBNation nation : members) {
                allianceNationIds.add(nation.getNation_id());
                allianceActiveMinutes.add(nation.active_m());
                allianceVerifiedFlags.add(nation.isVerified());
            }
            int allianceId = alliance.getAlliance_id();
            int allianceTotal = 0;
            for (int i = 0; i < size; i++) {
                final int id1 = allianceNationIds.getInt(i);
                final int active1 = allianceActiveMinutes.getInt(i);
                final boolean reg1 = allianceVerifiedFlags.getBoolean(i);
                if (!reg1) {
                    nationSharesTimeAA.addTo(id1, 1);
                    allianceTotal++;
                }
                for (int j = i + 1; j < size; j++) {
                    final int active2 = allianceActiveMinutes.getInt(j);
                    if (Math.abs(active1 - active2) >= 15) continue;
                    final boolean reg2 = allianceVerifiedFlags.getBoolean(j);
                    if (reg1 && reg2) continue;
                    final int id2 = allianceNationIds.getInt(j);
                    nationSharesTimeAA.addTo(id1, 1);
                    nationSharesTimeAA.addTo(id2, 1);
                    allianceTotal += 2;
                }
            }
            if (allianceTotal > 0) {
                allianceSharesTime.addTo(allianceId, allianceTotal);
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
                boolean sharesUid = nationSharesUid.get(nation.getNation_id()) > 0;;
                if (!sharesUid) {
                    if (snapshotData.hasCustomFlag(nation.getNation_id()) || snapshotData.hasCustomPortrait(nation.getNation_id())) {
                        ageBased = Math.max(ageBased, TimeUnit.DAYS.toMillis(180));
                    } else {
                        boolean sharesTime = nationSharesTimeAA.get(nation.getNation_id()) > 0;
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
        long diff = now - lastUpdated;
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

        boolean isMember = nation.getPositionEnum().id > Rank.APPLICANT.id;
        if (!isMember) {
            weight *= 0.8;
        }

        double ageFactor = Math.pow(300d / (nation.getAgeDays() + 300d), 0.75);

        boolean lowCities = nation.getCities() < 7;
        if (!lowCities) {
            weight *= ageFactor;
        }

        SnapshotMultiData.MultiData data = snapshotData.data.get(nation.getNation_id());
        if (data != null) {
            if (data.portraitUrl() != null && !data.portraitUrl().isEmpty()) {
                weight *= 0.6;
            }
        }

        boolean customFlag = snapshotData.hasCustomFlag(nation.getNation_id());
        if (customFlag) {
            weight *= 0.6;
        }

        boolean pickedLand = snapshotData.hasPickedLand(nation.getNation_id());
        if (pickedLand) {
            weight *= 0.7;
        }

        boolean customCurrency = snapshotData.hasCustomCurrency(nation.getNation_id());
        if (customCurrency) {
            weight *= 0.8;
        }

        String discStr = nation.getDiscordString();
        if (discStr != null && !discStr.isEmpty()) {
            weight *= 0.2;
        }

        int sharesUid = nationSharesUid.get(nation.getNation_id());
        if (sharesUid > 0) {
            weight = weight * (1 + (Math.pow(0.2, sharesUid + 4) - 1) * 20);
        }

        int timeAA = nationSharesTimeAA.get(nation.getNation_id());
        if (timeAA > 0) {
            weight = weight * (1 + (Math.pow(0.2, timeAA + 4) - 1) * 5);
        }

        int timeUid = nationSharesTimeUid.get(nation.getNation_id());
        if (timeUid > 0) {
            weight = weight * (1 + (Math.pow(0.2, timeUid + 4) - 1) * 40);
        }

        int timeUidAndAA = nationSharesTimeUidAndAA.get(nation.getNation_id());
        if (timeUidAndAA > 0) {
            weight = weight * (1 + (Math.pow(0.2, timeUidAndAA + 4) - 1) * 40);
        }

        int allianceSharesTime = this.allianceSharesTime.get(nation.getAlliance_id());
        if (allianceSharesTime > 0) {
            weight = weight * (1 + (Math.pow(0.2, allianceSharesTime + 4) - 1) * 10);
        }

        return weight;
    }

    private final ArrayDeque<Map.Entry<DBNation, Double>> queue = new ArrayDeque<>();

    public void updateQueue() {
        if (queue.isEmpty()) {
            this.lastUpdated = Locutus.imp().getDiscordDB().getMultiReportLastUpdated(f -> DBNation.getById(f) != null);
            long now = System.currentTimeMillis();
            // 2h between queue update
            if (now - lastUpdatedQueue < TimeUnit.HOURS.toMillis(2)) return;
            lastUpdatedQueue = now;
            Set<DBNation> nations = Locutus.imp().getNationDB().getAllNations();
            Map<DBNation, Double> weights = new Object2DoubleOpenHashMap<>();
            final long nowMillis = System.currentTimeMillis();
            for (DBNation nation : nations) {
                int fail = failCount.getOrDefault(nation.getNation_id(), 0);
                if (fail >= 3) continue;
                double weight = getNationWeight(nation, nowMillis);
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
        if (next == null || !next.getKey().isValid()) return;
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
                Logg.info("Updated Multi Buster: #" + updated + " | Remaining: #" + queue.size());
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
        updateQueue();
        Set<DBNation> nations = getQueue().stream().map(Map.Entry::getKey).collect(Collectors.toSet());

        double updatesPerDay = 0;
        long oneDay = TimeUnit.DAYS.toMillis(1);
        for (DBNation nation : nations) {
            double days = ((double) getRecommendedInterval(nation) / oneDay);
            updatesPerDay += 1d / days;
        }
        Logg.info("Multi check. Updates per day: " + updatesPerDay + " | " + nations.size());
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
            row.add(nation.getDiscordString());
            row.add(nation.getUserDiscriminator());
            row.add(nation.isVerified() + "");
            row.add(nation.hasProvidedIdentity(cacheStore) + "");
            row.add(factor + "");
            System.out.println(StringMan.join(row, "\t"));
        }
    }
}
