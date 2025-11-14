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
import net.dv8tion.jda.api.entities.Guild;
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
    public String createTemporary(@Me Guild guild, ConflictManager manager, @Me DBNation nation, @Me User author, Set<DBAlliance> col1, Set<DBAlliance> col2, @Timestamp long start, @Default @Timestamp Long end, @Switch("g") boolean includeGraphs) throws IOException, ParseException {
        if (end == null) end = Long.MAX_VALUE;
        if (includeGraphs && (Math.min(System.currentTimeMillis(), end) - start > TimeUnit.DAYS.toMillis(90))) {
            throw new IllegalArgumentException("Graph generation is limited to 90 days of data. Please reduce the time range or set `includeGraphs: false`");
        }

        String col1Name = col1.size() == 1 ? col1.iterator().next().getName() : "Coalition 1";
        String col2Name = col2.size() == 1 ? col2.iterator().next().getName() : "Coalition 2";
        String conflictName = "Generated conflict";

        long turnStart = TimeUtil.getTurn(start);
        long turnEnd = end == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTurn(end);
        Conflict conflict = new Conflict(-1, -1, conflictName, turnStart, turnEnd, 0, 0, 0, false);
        conflict.initData(col1Name, col2Name, guild.getIdLong(), ConflictCategory.GENERATED, "", "", "");
        for (DBAlliance aa : col1) conflict.addParticipant(aa.getId(), true, false, true, null, null);
        for (DBAlliance aa : col2) conflict.addParticipant(aa.getId(), false, false, true, null, null);

        manager.loadVirtualConflict(conflict, false);
        if (includeGraphs) {
            conflict.updateGraphsLegacy(manager);
        }

        String id = "n/" + nation.getId() + "/" + UUID.randomUUID();
        long now = System.currentTimeMillis();
        List<String> urls = conflict.pushChanges(manager, id, true, true, false, false, now);
        return Settings.INSTANCE.WEB.CONFLICTS.SITE + "/conflict?id=" + id + "\n" +
                "Note: Generated conflicts do NOT auto update.";
    }

}
