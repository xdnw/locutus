package link.locutus.discord.db.entities.nation;

import link.locutus.discord.apiv3.csv.header.NationHeader;

public class DBNationSnapshotVm extends DBNationSnapshot{

    private final long leavingVm;
    private final long enteredVm;

    public DBNationSnapshotVm(DataWrapper<NationHeader> wrapper, int offset, long leavingVm, long enteredVm) {
        super(wrapper, offset);
        this.leavingVm = leavingVm;
        this.enteredVm = enteredVm;
    }

    @Override
    public long _enteredVm() {
        return enteredVm;
    }

    @Override
    public long _leavingVm() {
        return leavingVm;
    }
}
