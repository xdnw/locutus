package link.locutus.discord.apiv3.csv.header;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.db.entities.nation.DataWrapper;
import link.locutus.discord.db.entities.nation.LocalDataWrapper;

import java.util.function.Predicate;

public class NationHeaderReader extends DataReader<NationHeader> {
    private DBNationSnapshot cached;
    private int nationLoaded;
    private static final int LOADED = 1;
    private static final int ALLOW_VM = 2;
    private static final int ALLOW_DELETED = 4;
    private static final Predicate<Integer> ALLOW_ALL = b -> true;

    private final DataWrapper<NationHeader> wrapper;

    public NationHeaderReader(NationHeader header, long date) {
        super(header, date);
        this.wrapper = new LocalDataWrapper<>(date, header);
    }

    public void clear() {
        cached = null;
        nationLoaded = 0;
    }

    public DBNationSnapshot getNation(boolean allowVm, boolean allowDeleted) {
        int nationId = header.nation_id.get();
        if (cached != null) {
            if (cached.getId() == nationId) {
                if (!allowVm && (nationLoaded & ALLOW_VM) != 0 && cached.getVm_turns() > 0) return null;
                if (!allowDeleted && (nationLoaded & ALLOW_DELETED) != 0 && !cached.isValid()) return null;
                return cached;
            }
            cached = null;
            nationLoaded = 0;
        }
        if ((nationLoaded & LOADED) != 0 && (!allowVm || (nationLoaded & ALLOW_VM) == 0) && (!allowDeleted || (nationLoaded & ALLOW_DELETED) == 0)) return null;
        nationLoaded |= LOADED | (allowVm ? ALLOW_VM : 0) | (allowDeleted ? ALLOW_DELETED : 0);
        return cached = loadNationUnchecked(nationId, ALLOW_ALL, ALLOW_ALL, allowVm, allowVm, allowDeleted);
    }

    public DBNationSnapshot getNation(Predicate<Integer> allowedNationIds, Predicate<Integer> allowedAllianceIds, boolean allowVm, boolean allowNoVmCol, boolean allowDeleted) {
        int nationId = header.nation_id.get();
        if (cached != null) {
            if (cached.getId() == nationId) {
                if (!allowVm && (nationLoaded & ALLOW_VM) != 0 && cached.getVm_turns() > 0) return null;
                if (!allowDeleted && (nationLoaded & ALLOW_DELETED) != 0 && !cached.isValid()) return null;
                if (!allowedNationIds.test(nationId)) return null;
                if (!allowedAllianceIds.test(cached.getAlliance_id())) return null;
                return cached;
            }
            cached = null;
            nationLoaded = 0;
        }
        if ((nationLoaded & LOADED) != 0 && (!allowVm || (nationLoaded & ALLOW_VM) == 0) && (!allowDeleted || (nationLoaded & ALLOW_DELETED) == 0)) return null;
        nationLoaded = LOADED | (allowVm ? ALLOW_VM : 0) | (allowDeleted ? ALLOW_DELETED : 0);
        return cached = loadNationUnchecked(nationId, allowedNationIds, allowedAllianceIds, allowVm, allowNoVmCol, allowDeleted);
    }

    private DBNationSnapshot loadNationUnchecked(int nationId, Predicate<Integer> allowedNationIds, Predicate<Integer> allowedAllianceIds, boolean allowVm, boolean allowNoVmCol, boolean allowDeleted) {
        if (!allowedNationIds.test(nationId)) return null;
        Integer vm_turns = header.vm_turns.get();
        if (vm_turns != null) {
            if (vm_turns > 0 && !allowVm) {
                return null;
            }
        } else {
            if (!allowNoVmCol) {
                return null;
            }
            vm_turns = 0;
        }
        int aaId = header.alliance_id.get();
        if (!allowedAllianceIds.test(aaId)) return null;
        if (!allowDeleted) {
            DBNation existing = DBNation.getById(nationId);
            if (existing == null) {
                return null;
            }
        }
        DBNationSnapshot nation = new DBNationSnapshot(wrapper, header.getOffset());
        return nation;
    }
}
