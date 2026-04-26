package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.conflict.ConflictStartDetector;
import link.locutus.discord.db.conflict.ConflictUtil;
import link.locutus.discord.db.conflict.VirtualConflictStorageManager;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.*;
import link.locutus.discord.web.commands.page.PageHelper;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;

public class ConflictEndpoints extends PageHelper {
    private boolean hasVirtualConflictAdminPerms(User user, Guild guild, GuildDB db) {
        boolean hasMilcom = user != null
                && (Roles.MILCOM.hasOnRoot(user) || (guild != null && Roles.MILCOM.has(user, guild)));
        boolean hasCoalitionPerm = db != null && db.hasCoalitionPermsOnRoot(Coalition.MANAGE_CONFLICTS);
        return hasMilcom && hasCoalitionPerm;
    }

    /**
     * Validate that the conflict is virtual, the caller has access, and the virtual id is present.
     * @return the validated id
     * @throws IllegalArgumentException if validation fails
     */
    private ConflictUtil.VirtualConflictId requireValidVirtualConflict(Conflict conflict, DBNation me, User user, Guild guild, GuildDB db) {
        if (conflict == null || !conflict.isVirtual()) {
            throw new IllegalArgumentException("Please provide a temporary conflict");
        }
        // Permission: admin perms OR owner nation
        if (!hasVirtualConflictAdminPerms(user, guild, db)) {
            ConflictUtil.VirtualConflictId virtualId = conflict.getVirtualConflictId();
            if (virtualId == null || me == null || me.getId() != virtualId.nationId()) {
                throw new IllegalArgumentException("You do not have permission to access this temporary conflict");
            }
        }
        ConflictUtil.VirtualConflictId virtualId = conflict.getVirtualConflictId();
        if (virtualId == null) {
            throw new IllegalArgumentException("Temporary conflict id is missing");
        }
        return virtualId;
    }

    /** Resolve alliance ids to names, skipping unknowns. */
    private static void resolveAllianceNames(ConflictManager manager, Iterable<Integer> allianceIds, Int2ObjectMap<String> out) {
        for (Integer aaId : allianceIds) {
            int key = aaId.intValue();
            if (!out.containsKey(key)) {
                String name = manager.getAllianceNameOrNull(aaId);
                if (name != null) out.put(key, name);
            }
        }
    }

    @Command(desc = "Fetch alliance names and participant lists for given conflicts", viewable = true)
    @ReturnType(value = ConflictAlliances.class)
    public Object conflictAlliances(@Me GuildDB db, ConflictManager manager, Set<Conflict> conflicts) {
        Int2ObjectMap<String> allianceNames = new Int2ObjectOpenHashMap<>();

        if (conflicts == null || conflicts.isEmpty()) {
            return new ConflictAlliances(allianceNames, new Int2ObjectOpenHashMap<>());
        }

        Map<Integer, List<List<Integer>>> byConflict = manager.getConflictAlliances(conflicts);
        Int2ObjectMap<List<List<Integer>>> byConflictFast = new Int2ObjectOpenHashMap<>(byConflict);

        for (List<List<Integer>> sides : byConflictFast.values()) {
            for (List<Integer> side : sides) {
                resolveAllianceNames(manager, side, allianceNames);
            }
        }

        return new ConflictAlliances(allianceNames, byConflictFast);
    }

    @Command(desc = "List temporary virtual conflicts accessible to the current user", viewable = true)
    @ReturnType({List.class, VirtualConflictMeta.class})
    public Object virtualConflicts(ConflictManager manager, @Me @Default GuildDB db, @Me @Default Guild guild,
            @Me @Default User user, @Me @Default DBNation me, @Switch("a") boolean all) {
        boolean hasAdminPerms = hasVirtualConflictAdminPerms(user, guild, db);
        if (all && !hasAdminPerms) {
            return error("You do not have permission to list all temporary conflicts");
        }

        Integer nationFilter = null;
        if (!all) {
            if (me == null) {
                return List.of();
            }
            nationFilter = me.getId();
        }

        VirtualConflictStorageManager storageManager = new VirtualConflictStorageManager(manager.getCloud());
        List<VirtualConflictStorageManager.VirtualConflictObjectMeta> metadata = storageManager.listObjectMetadata(nationFilter);

        List<VirtualConflictMeta> entries = new ArrayList<>(metadata.size());
        for (VirtualConflictStorageManager.VirtualConflictObjectMeta entry : metadata) {
            ConflictUtil.VirtualConflictId id = entry.id();
            entries.add(new VirtualConflictMeta(id.nationId(), id.uuid().toString(), entry.dateModified()));
        }
        return entries;
    }

    @Command(desc = "Retrieve detailed information about a specific temporary conflict", viewable = true)
    @ReturnType(value = WebVirtualConflict.class)
    public Object virtualConflictInfo(ConflictManager manager, Conflict conflict, @Me @Default GuildDB db,
            @Me @Default Guild guild, @Me @Default User user, @Me @Default DBNation me) {
        ConflictUtil.VirtualConflictId virtualId = requireValidVirtualConflict(conflict, me, user, guild, db);

        List<Integer> side1 = new ArrayList<>(conflict.getCoalition1());
        List<Integer> side2 = new ArrayList<>(conflict.getCoalition2());

        Int2ObjectMap<String> allianceNames = new Int2ObjectOpenHashMap<>();
        resolveAllianceNames(manager, side1, allianceNames);
        resolveAllianceNames(manager, side2, allianceNames);

        Map<Integer, List<List<Integer>>> byConflict = new Int2ObjectOpenHashMap<>();
        byConflict.put(conflict.getId(), List.of(side1, side2));
        ConflictAlliances alliances = new ConflictAlliances(allianceNames, byConflict);

        Map<String, List<List<Object>>> posts = new LinkedHashMap<>();
        Map<String, List> rawPosts = conflict.getAnnouncementsList();
        for (Map.Entry<String, List> entry : rawPosts.entrySet()) {
            posts.put(entry.getKey(), List.of(new ArrayList<>(entry.getValue())));
        }

        return new WebVirtualConflict(
                virtualId.toWebId(),
                conflict.getName(),
                conflict.getCategory().name(),
                conflict.getStartMS(),
                conflict.getEndMS(),
                conflict.getWiki(),
                conflict.getCasusBelli(),
                conflict.getStatusDesc(),
                alliances,
                posts);
    }

    @Command(desc = "Delete a specified temporary conflict if the caller has appropriate permissions")
    @ReturnType(value = WebSuccess.class)
    public Object removeVirtualConflict(ConflictManager manager, Conflict conflict, @Me @Default GuildDB db,
            @Me @Default Guild guild, @Me @Default User user, @Me @Default DBNation me) {
        ConflictUtil.VirtualConflictId virtualId = requireValidVirtualConflict(conflict, me, user, guild, db);

        VirtualConflictStorageManager storageManager = new VirtualConflictStorageManager(manager.getCloud());
        storageManager.deleteConflict(virtualId);
        return success();
    }

    private boolean hasConflictWritePerm(Conflict conflict, DBNation me, User user, Guild guild, GuildDB db) {
        if (hasVirtualConflictAdminPerms(user, guild, db)) {
            return true;
        }
        ConflictUtil.VirtualConflictId virtualId = conflict == null ? null : conflict.getVirtualConflictId();
        return virtualId != null && me != null && me.getId() == virtualId.nationId();
    }

    @Command(desc = """
            Scan declared wars to produce nearby candidate start turns for a conflict.

            With `force=false` (or omitted): runs detection, stores a token, and
            returns nearby qualifying candidate turns without mutating the conflict.
            The trigger is at least three distinct attacker nations on one side
            declaring onto the opposing coalition during that turn.

            With `force=true`: applies a previously surfaced candidate using the
            supplied token and optional `candidate_turn`, without recomputing.""")
    @ReturnType(value = WebConflictStartDetection.class)
    public Object detectConflictStart(ConflictManager manager, Conflict conflict, @Me @Default GuildDB db,
            @Me @Default Guild guild, @Me @Default User user, @Me @Default DBNation me,
            @Switch("t") Integer turn_allowance, @Switch("f") boolean force,
            @Switch("k") UUID token, @Switch("c") Long candidate_turn) {
        if (conflict == null) {
            throw new IllegalArgumentException("Please provide a conflict");
        }
        if (!hasConflictWritePerm(conflict, me, user, guild, db)) {
            throw new IllegalArgumentException("You do not have permission to modify this conflict");
        }

        String webId = conflict.isVirtual()
                ? conflict.getVirtualConflictId().toWebId()
                : Integer.toString(conflict.getId());
        long currentStartTurn = conflict.getStartTurn();

        if (force) {
            ConflictStartDetector.Candidate applied = ConflictStartDetector.resolveCandidate(conflict, token, candidate_turn);
            long appliedMs = applied.turnMs();
            conflict.setStart(appliedMs);
            return new WebConflictStartDetection(
                    webId,
                    currentStartTurn,
                    currentStartTurn,
                    applied.turn(),
                    true,
                    null,
                    Collections.emptyList());
        }

        ConflictStartDetector.Result result = ConflictStartDetector.detect(
                conflict, turn_allowance == null ? 0 : turn_allowance);
        UUID previewToken = result.candidates().isEmpty() ? null : ConflictStartDetector.createToken(conflict, result);
        List<WebConflictStartDetection.Candidate> candidates = new ArrayList<>(result.candidates().size());
        for (ConflictStartDetector.Candidate c : result.candidates()) {
            candidates.add(new WebConflictStartDetection.Candidate(
                    c.turn(),
                    c.coal1Nations(),
                    c.coal2Nations(),
                    c.coal1Declarations(),
                    c.coal2Declarations(),
                    c.totalDeclarations(),
                    toAllianceList(manager, c.coal1Alliances()),
                    toAllianceList(manager, c.coal2Alliances())));
        }
        return new WebConflictStartDetection(
                webId,
                currentStartTurn,
                result.searchedFromTurn(),
                null,
                false,
                previewToken == null ? null : previewToken.toString(),
                candidates);
    }

    private static List<WebConflictStartDetection.AllianceSummary> toAllianceList(
            ConflictManager manager,
            List<ConflictStartDetector.AllianceSummary> src) {
        List<WebConflictStartDetection.AllianceSummary> out = new ArrayList<>(src.size());
        for (ConflictStartDetector.AllianceSummary entry : src) {
            out.add(new WebConflictStartDetection.AllianceSummary(
                    entry.allianceId(),
                    manager.getAllianceNameOrNull(entry.allianceId()),
                    entry.nations(),
                    entry.declarations()));
        }
        return out;
    }

    @Command(desc = "Fetch forum announcement posts for given conflicts", viewable = true)
    @ReturnType(value = ConflictPosts.class)
    public Object conflictPosts(ConflictManager manager, Set<Conflict> conflicts) {
        Map<Integer, Map<String, List>> result = new Int2ObjectOpenHashMap<>();

        if (conflicts == null || conflicts.isEmpty()) {
            return new ConflictPosts(result);
        }

        for (Conflict conflict : conflicts) {
            if (conflict == null) continue;
            Map<String, List> announcements = conflict.getAnnouncementsList();
            if (!announcements.isEmpty()) {
                result.put(conflict.getId(), announcements);
            }
        }

        return new ConflictPosts(result);
    }
}
