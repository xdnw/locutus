package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationSnapshotVm;
import link.locutus.discord.db.entities.nation.DataWrapper;
import link.locutus.discord.db.entities.nation.GlobalDataWrapper;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.TimeUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class NationsFileSnapshot implements INationSnapshot {
    private final Map<Integer, DBNationSnapshot> nations;
    private final long day;
    private final DataDumpParser dumper;
    private final boolean loadCities;
    private volatile Map<Integer, Set<DBNation>> allianceIndex;
    private volatile Map<String, DBNation> leaderIndex;
    private volatile Map<String, DBNation> nameIndex;

    public NationsFileSnapshot(DataDumpParser dumper, long day, boolean loadCities) throws IOException, ParseException {
        this.day = day;
        this.dumper = dumper;
        this.loadCities = loadCities;
        // Snapshot instances may apply VM overlays; keep per-instance state isolated.
        this.nations = new Int2ObjectOpenHashMap<>(dumper.getNations(day, loadCities));
    }

    public NationsFileSnapshot loadVm(@Nullable Set<Integer> nationIds) throws IOException, ParseException {
        Set<Integer> missing = new IntOpenHashSet();
        if (nationIds != null) {
            synchronized (nations) {
                for (int id : nationIds) {
                    if (!nations.containsKey(id)) {
                        missing.add(id);
                    }
                }
            }
            if (missing.isEmpty())
                return this;
        }

        long start = System.currentTimeMillis();
        Map<Integer, List<Map.Entry<Integer, Integer>>> vmRanges = dumper.getUtil().getCachedVmRanged(day + 1, false);
        if (nationIds == null) {
            missing = dumper.getUtil().getVMNations(vmRanges, (int) day);
            synchronized (nations) {
                missing.removeIf(nations::containsKey);
            }
        }

        long timestamp = TimeUtil.getTimeFromDay(day);
        long dayTurn = TimeUtil.getTurn(timestamp);

        Map<Integer, Long> leavingVm = new Int2LongOpenHashMap();
        Map<Integer, Set<Integer>> nationsByDay = new Int2ObjectOpenHashMap<>();
        for (int id : missing) {
            List<Map.Entry<Integer, Integer>> ranges = vmRanges.get(id);
            if (ranges == null)
                continue;
            for (Map.Entry<Integer, Integer> range : ranges) {
                int startDay = range.getKey();
                int endDay = range.getValue();
                if (startDay <= day && endDay >= day) {
                    nationsByDay.computeIfAbsent(startDay, k -> new IntOpenHashSet()).add(id);
                    long vmEnd;
                    if (endDay == Integer.MAX_VALUE) {
                        DBNation nation = DBNation.getById(id);
                        if (nation != null) {
                            vmEnd = Math.max(dayTurn, nation.getLeaving_vm());
                        } else {
                            vmEnd = dayTurn;
                        }
                    } else {
                        vmEnd = TimeUtil.getTurn(TimeUtil.getTimeFromDay((long) endDay));
                    }
                    leavingVm.put(id, vmEnd);
                }
            }
        }

        ExecutorService executor = Locutus.imp().getExecutor();
        List<Future<?>> tasks = new ObjectArrayList<>();
        for (Map.Entry<Integer, Set<Integer>> entry : nationsByDay.entrySet()) {
            Set<Integer> ids = entry.getValue();
            int fetchDay = entry.getKey();
            long enteredVm = TimeUtil.getTurn(TimeUtil.getTimeFromDay((long) fetchDay));
            tasks.add(executor.submit(() -> {
                try {
                    Map<Integer, DBNationSnapshot> addNations = dumper.getNations(fetchDay, loadCities);
                    synchronized (nations) {
                        for (Map.Entry<Integer, DBNationSnapshot> entry2 : addNations.entrySet()) {
                            int nationId = entry2.getKey();
                            if (!ids.contains(nationId))
                                continue;
                            DBNationSnapshot nation = entry2.getValue();
                            // update VM turns
                            long leavingVmMs = leavingVm.get(nationId);
                            long enteredVmMs = enteredVm;
                            int offset = nation.getOffset();

                            GlobalDataWrapper<NationHeader> wrapper = (GlobalDataWrapper<NationHeader>) nation
                                    .getWrapper();
                            DataWrapper<NationHeader> newWrapper = new GlobalDataWrapper<>(timestamp, wrapper.header,
                                    wrapper.data, wrapper.getCities);

                            DBNationSnapshotVm newNation = new DBNationSnapshotVm(newWrapper, nation.getOffset(),
                                    leavingVmMs, enteredVmMs);
                            nations.put(nationId, newNation);
                        }
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }));
        }
        for (Future<?> task : tasks) {
            FileUtil.get(task);
        }

        synchronized (nations) {
            // VM backfill mutates nation entries; discard derived lookup indexes.
            allianceIndex = null;
            leaderIndex = null;
            nameIndex = null;
        }

        return this;
    }

    private void ensureIndexesLocked() {
        if (allianceIndex != null && leaderIndex != null && nameIndex != null) {
            return;
        }
        Map<Integer, Set<DBNation>> alliances = new Int2ObjectOpenHashMap<>();
        Map<String, DBNation> leaders = new java.util.HashMap<>();
        Map<String, DBNation> names = new java.util.HashMap<>();
        for (DBNationSnapshot nation : nations.values()) {
            alliances.computeIfAbsent(nation.getAlliance_id(), k -> new ObjectOpenHashSet<>()).add(nation);
            String leader = nation.getLeader();
            if (leader != null && !leader.isEmpty()) {
                leaders.putIfAbsent(leader.toLowerCase(Locale.ROOT), nation);
            }
            String natName = nation.getName();
            if (natName != null && !natName.isEmpty()) {
                names.putIfAbsent(natName.toLowerCase(Locale.ROOT), nation);
            }
        }
        allianceIndex = alliances;
        leaderIndex = leaders;
        nameIndex = names;
    }

    @Override
    public DBNation getNationById(int id) {
        synchronized (nations) {
            return nations.get(id);
        }
    }

    @Override
    public Set<DBNation> getNationsByAlliance(Set<Integer> alliances) {
        synchronized (nations) {
            ensureIndexesLocked();
            Set<DBNation> result = new ObjectOpenHashSet<>();
            for (int allianceId : alliances) {
                Set<DBNation> members = allianceIndex.get(allianceId);
                if (members != null && !members.isEmpty()) {
                    result.addAll(members);
                }
            }
            return result;
        }
    }

    @Override
    public DBNation getNationByLeader(String input) {
        synchronized (nations) {
            ensureIndexesLocked();
            return leaderIndex.get(input.toLowerCase(Locale.ROOT));
        }
    }

    public DBNation getNationByName(String input) {
        synchronized (nations) {
            ensureIndexesLocked();
            return nameIndex.get(input.toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public Set<DBNation> getAllNations() {
        synchronized (nations) {
            return new ObjectOpenHashSet<>(nations.values());
        }
    }

    @Override
    public Set<DBNation> getNationsByBracket(int taxId) {
        synchronized (nations) {
            Set<DBNation> result = new ObjectOpenHashSet<>();
            for (DBNationSnapshot nation : nations.values()) {
                if (nation.getTax_id() == taxId) {
                    result.add(nation);
                }
            }
            return result;
        }
    }

    @Override
    public Set<DBNation> getNationsByAlliance(int id) {
        synchronized (nations) {
            ensureIndexesLocked();
            Set<DBNation> members = allianceIndex.get(id);
            if (members == null || members.isEmpty()) {
                return new ObjectOpenHashSet<>();
            }
            return new ObjectOpenHashSet<>(members);
        }
    }
}
