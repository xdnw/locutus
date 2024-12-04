package link.locutus.discord.db.entities.nation;

import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.column.ProjectColumn;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.util.TimeUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import static link.locutus.discord.apiv1.core.Utility.unsupported;

public class DBNationSnapshotVm extends DBNationSnapshot{

    private final long leavingVm;
    private final long enteredVm;

    public DBNationSnapshotVm(SnapshotDataWrapper<NationHeader> wrapper, int offset, long leavingVm, long enteredVm) {
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
