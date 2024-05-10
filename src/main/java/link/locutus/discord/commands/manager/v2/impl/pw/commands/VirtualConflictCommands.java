package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VirtualConflictCommands {
    @Command(desc = "Create a temporary conflict between two coalitions\n" +
            "Conflict is not auto updated")
    @RolePermission(Roles.ADMIN)
    public String createTemporary(ConflictManager manager, @Me DBNation nation, @Me User author, Set<DBAlliance> col1, Set<DBAlliance> col2, @Timestamp long start, @Default @Timestamp Long end, @Switch("g") boolean includeGraphs) throws IOException, ParseException {
        if (end == null) end = Long.MAX_VALUE;
        if (includeGraphs && (Math.min(System.currentTimeMillis(), end) - start > TimeUnit.DAYS.toMillis(90))) {
            throw new IllegalArgumentException("Graph generation is limited to 90 days of data. Please reduce the time range or set `includeGraphs: false`");
        }

        String col1Name = col1.size() == 1 ? col1.iterator().next().getName() : "Coalition 1";
        String col2Name = col2.size() == 1 ? col2.iterator().next().getName() : "Coalition 2";
        String conflictName = "Generated conflict";

        long turnStart = TimeUtil.getTurn(start);
        long turnEnd = end == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTurn(end);
        Conflict conflict = new Conflict(-1, -1, ConflictCategory.GENERATED,
                conflictName,
                col1Name,
                col2Name,
                "",
                "",
                "",
                turnStart,
                turnEnd
        );
        for (DBAlliance aa : col1) conflict.addParticipant(aa.getId(), true, false, null, null);
        for (DBAlliance aa : col2) conflict.addParticipant(aa.getId(), false, false, null, null);

        manager.loadVirtualConflict(conflict, false);
        if (includeGraphs) {
            conflict.updateGraphsLegacy(manager);
        }

        String id = "n/" + nation.getId() + "/" + UUID.randomUUID();
        List<String> urls = conflict.push(manager, id, true);
        return Settings.INSTANCE.WEB.S3.SITE + "/conflict?id=" + id;
    }

}
