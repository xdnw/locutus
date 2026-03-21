package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.db.INationSnapshot;

@FunctionalInterface
public interface NationSnapshotService {
    INationSnapshot resolve(NationModifier modifier);

    static NationSnapshotService fixed(INationSnapshot snapshot) {
        return modifier -> snapshot;
    }
}