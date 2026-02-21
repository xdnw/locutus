package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.conflict.ConflictUtil;
import link.locutus.discord.db.conflict.VirtualConflictStorageManager;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.jooby.S3CompatibleStorage;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VirtualConflictCommands {
    private static final String SAMPLE_VIRTUAL_CONFLICT_PATH = "n/189573/902bce00-67fb-4b98-8dc5-44bd3592914b";

    @Command(desc = "Create a temporary conflict between two coalitions\n" +
            "Conflict is not auto updated")
    @RolePermission(Roles.ADMIN)
    public String createTemporary(@Me Guild guild, ConflictManager manager, @Me DBNation nation, @Me User author,
            Set<DBAlliance> col1, Set<DBAlliance> col2, @Timestamp long start, @Default @Timestamp Long end,
            @Switch("g") boolean includeGraphs) throws IOException, ParseException {
        if (end == null)
            end = Long.MAX_VALUE;
        if (includeGraphs && (Math.min(System.currentTimeMillis(), end) - start > TimeUnit.DAYS.toMillis(90))) {
            throw new IllegalArgumentException(
                    "Graph generation is limited to 90 days of data. Please reduce the time range or set `includeGraphs: false`");
        }

        String col1Name = col1.size() == 1 ? col1.iterator().next().getName() : "Coalition 1";
        String col2Name = col2.size() == 1 ? col2.iterator().next().getName() : "Coalition 2";
        String conflictName = "Generated conflict";

        long turnStart = TimeUtil.getTurn(start);
        long turnEnd = end == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTurn(end);
        Conflict conflict = new Conflict(-1, -1, conflictName, turnStart, turnEnd, 0, 0, 0, false);
        conflict.setLoaded(col1Name, col2Name, guild.getIdLong(), ConflictCategory.GENERATED, "", "", "");
        for (DBAlliance aa : col1)
            conflict.addParticipant(aa.getId(), true, false, true, null, null);
        for (DBAlliance aa : col2)
            conflict.addParticipant(aa.getId(), false, false, true, null, null);

        manager.loadVirtualConflict(conflict, false);
        if (includeGraphs) {
            conflict.updateGraphsLegacy(manager);
        }

        String id = "n/" + nation.getId() + "/" + UUID.randomUUID();
        long now = System.currentTimeMillis();
        conflict.pushChanges(manager, id, true, true, false, false, false, now);
        return Settings.INSTANCE.WEB.CONFLICTS.SITE + "/conflict?id=" + id + "\n" +
                "Note: Generated conflicts do NOT auto update.";
    }

    public static void main(String[] args) {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());

        S3CompatibleStorage aws = S3CompatibleStorage.forAwsS3(
                Settings.INSTANCE.WEB.S3.ACCESS_KEY,
                Settings.INSTANCE.WEB.S3.SECRET_ACCESS_KEY,
                Settings.INSTANCE.WEB.S3.BUCKET,
                Settings.INSTANCE.WEB.S3.REGION,
                Settings.INSTANCE.WEB.S3.BASE_URL
        );

        try {
            Integer nationFilter = null;
            String loadId = args.length == 0 ? SAMPLE_VIRTUAL_CONFLICT_PATH : null;
            VirtualConflictStorageManager virtualConflictManager = new VirtualConflictStorageManager(aws);

            if (args.length >= 1 && !args[0].isBlank()) {
                if (args[0].startsWith("n/")) {
                    loadId = args[0];
                } else {
                    nationFilter = Integer.parseInt(args[0]);
                }
            }
            if (args.length >= 2 && !args[1].isBlank()) {
                loadId = args[1];
            }

            List<ConflictUtil.VirtualConflictId> virtualConflictIds = virtualConflictManager.listIds(nationFilter);
            System.out.println("Found " + virtualConflictIds.size() + " temporary conflicts"
                    + (nationFilter == null ? "" : " for nation " + nationFilter) + ":");
            for (ConflictUtil.VirtualConflictId id : virtualConflictIds) {
                System.out.println("- " + id + " -> " + Settings.INSTANCE.WEB.CONFLICTS.SITE + "/conflict?id=" + id);
            }

            if (loadId != null) {
                String idToLoad = loadId;
                try {
                    Conflict loaded = virtualConflictManager.loadConflict(idToLoad);
                    System.out.println("\nLoaded temp conflict: " + loaded.getName());
                    System.out.println("- id: " + idToLoad);
                    System.out.println("- startTurn: " + loaded.getStartTurn());
                    System.out.println("- endTurn: " + loaded.getEndTurn());
                    System.out.println("- coalition1: " + loaded.getCoalitionName(true) + " (" + loaded.getCoalition1().size() + " alliances)");
                    System.out.println("- coalition2: " + loaded.getCoalitionName(false) + " (" + loaded.getCoalition2().size() + " alliances)");
                } catch (Exception e) {
                    System.err.println("Failed to load conflict `" + idToLoad + "`: " + e.getMessage());
                }
            } else {
                System.out.println("No specific conflict id requested; listing only.");
            }
        } finally {
            aws.close();
        }
    }
}
