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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class NationsFileSnapshot implements INationSnapshot {
    private final Map<Integer, DBNationSnapshot> nations;
    private final long day;
    private final DataDumpParser dumper;
    private final boolean loadCities;

    public NationsFileSnapshot(DataDumpParser dumper, long day, boolean loadCities) throws IOException, ParseException {
        this.day = day;
        this.dumper = dumper;
        this.loadCities = loadCities;
        this.nations = dumper.getNations(day);
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
            if (missing.isEmpty()) return this;
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
            if (ranges == null) continue;
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
                    Map<Integer, DBNationSnapshot> addNations = dumper.getNations(fetchDay);
                    synchronized (nations) {
                        for (Map.Entry<Integer, DBNationSnapshot> entry2 : addNations.entrySet()) {
                            int nationId = entry2.getKey();
                            if (!ids.contains(nationId)) continue;
                            DBNationSnapshot nation = entry2.getValue();
                            // update VM turns
                            long leavingVmMs = leavingVm.get(nationId);
                            long enteredVmMs = enteredVm;
                            int offset = nation.getOffset();

                            GlobalDataWrapper<NationHeader> wrapper = (GlobalDataWrapper<NationHeader>) nation.getWrapper();
                            DataWrapper<NationHeader> newWrapper = new GlobalDataWrapper<>(timestamp, wrapper.header, wrapper.data, wrapper.getCities);

                            DBNationSnapshotVm newNation = new DBNationSnapshotVm(newWrapper, nation.getOffset(), leavingVmMs, enteredVmMs);
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

        return this;
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
            return nations.values().stream().filter(n -> alliances.contains(n.getAlliance_id())).collect(Collectors.toSet());
        }
    }

    @Override
    public DBNation getNationByLeader(String input) {
        synchronized (nations) {
            return nations.values().stream().filter(n -> n.getLeader().equalsIgnoreCase(input)).findFirst().orElse(null);
        }
    }

    public DBNation getNationByName(String input) {
        synchronized (nations) {
            return nations.values().stream().filter(n -> n.getName().equalsIgnoreCase(input)).findFirst().orElse(null);
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
            return nations.values().stream().filter(n -> n.getTax_id() == taxId).collect(Collectors.toSet());
        }
    }

    @Override
    public Set<DBNation> getNationsByAlliance(int id) {
        synchronized (nations) {
            return nations.values().stream().filter(n -> n.getAlliance_id() == id).collect(Collectors.toSet());
        }
    }
}
