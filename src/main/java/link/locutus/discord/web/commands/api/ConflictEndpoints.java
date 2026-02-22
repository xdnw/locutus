package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.conflict.ConflictUtil;
import link.locutus.discord.db.conflict.VirtualConflictStorageManager;
import link.locutus.discord.user.Roles;
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

    private void requireVirtualConflictAccess(Conflict conflict, DBNation me, User user, Guild guild, GuildDB db) {
        if (hasVirtualConflictAdminPerms(user, guild, db)) {
            return;
        }
        ConflictUtil.VirtualConflictId virtualId = conflict == null ? null : conflict.getVirtualConflictId();
        if (virtualId != null && me != null && me.getId() == virtualId.nationId()) {
            return;
        }
        throw new IllegalArgumentException("You do not have permission to access this temporary conflict");
    }

    @Command(viewable = true)
    @ReturnType(value = ConflictAlliances.class)
    public Object conflictAlliances(@Me GuildDB db, ConflictManager manager, Set<Conflict> conflicts) {
        Int2ObjectMap<String> allianceNames = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<List<List<Integer>>> byConflictFast = new Int2ObjectOpenHashMap<>();

        if (conflicts == null || conflicts.isEmpty()) {
            return new ConflictAlliances(allianceNames, byConflictFast);
        }

        Map<Integer, List<List<Integer>>> byConflict = manager.getConflictAlliances(conflicts);
        for (Map.Entry<Integer, List<List<Integer>>> e : byConflict.entrySet())
            byConflictFast.put(e.getKey(), e.getValue());

        for (List<List<Integer>> sides : byConflictFast.values()) {
            for (List<Integer> side : sides) {
                for (Integer aaId : side) {
                    int key = aaId.intValue();
                    if (!allianceNames.containsKey(key)) {
                        String name = manager.getAllianceNameOrNull(aaId);
                        if (name != null)
                            allianceNames.put(key, name);
                    }
                }
            }
        }

        return new ConflictAlliances(allianceNames, byConflictFast);
    }

    @Command(viewable = true)
    @ReturnType(value = WebOptions.class)
    public Object virtualConflicts(ConflictManager manager, @Me @Default GuildDB db, @Me @Default Guild guild,
            @Me @Default User user, @Me @Default DBNation me, @Switch("a") boolean all) {
        boolean hasAdminPerms = hasVirtualConflictAdminPerms(user, guild, db);
        if (all && !hasAdminPerms) {
            return error("You do not have permission to list all temporary conflicts");
        }

        Integer nationFilter = null;
        if (!all) {
            if (me == null) {
                return new WebOptions(false).withText().withSubtext();
            }
            nationFilter = me.getId();
        }

        VirtualConflictStorageManager storageManager = new VirtualConflictStorageManager(manager.getCloud());
        List<ConflictUtil.VirtualConflictId> ids = storageManager.listIds(nationFilter);

        WebOptions options = new WebOptions(false).withText().withSubtext();
        String site = link.locutus.discord.config.Settings.INSTANCE.WEB.CONFLICTS.SITE;
        for (ConflictUtil.VirtualConflictId id : ids) {
            String webId = id.toWebId();
            options.add(webId, webId, site + "/conflict?id=" + webId);
        }
        return options;
    }

    @Command(viewable = true)
    @ReturnType(value = WebVirtualConflict.class)
    public Object virtualConflictInfo(ConflictManager manager, Conflict conflict, @Me @Default GuildDB db,
            @Me @Default Guild guild, @Me @Default User user, @Me @Default DBNation me) {
        if (conflict == null || !conflict.isVirtual()) {
            return error("Please provide a temporary conflict");
        }
        requireVirtualConflictAccess(conflict, me, user, guild, db);

        ConflictUtil.VirtualConflictId virtualId = conflict.getVirtualConflictId();
        if (virtualId == null) {
            return error("Temporary conflict id is missing");
        }

        Int2ObjectMap<String> allianceNames = new Int2ObjectOpenHashMap<>();
        List<Integer> side1 = new ArrayList<>(conflict.getCoalition1());
        List<Integer> side2 = new ArrayList<>(conflict.getCoalition2());
        for (Integer aaId : side1) {
            if (!allianceNames.containsKey(aaId)) {
                String name = manager.getAllianceNameOrNull(aaId);
                if (name != null) {
                    allianceNames.put(aaId, name);
                }
            }
        }
        for (Integer aaId : side2) {
            if (!allianceNames.containsKey(aaId)) {
                String name = manager.getAllianceNameOrNull(aaId);
                if (name != null) {
                    allianceNames.put(aaId, name);
                }
            }
        }
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

    @Command
    @ReturnType(value = WebSuccess.class)
    public Object removeVirtualConflict(ConflictManager manager, Conflict conflict, @Me @Default GuildDB db,
            @Me @Default Guild guild, @Me @Default User user, @Me @Default DBNation me) {
        if (conflict == null || !conflict.isVirtual()) {
            return error("Please provide a temporary conflict");
        }
        requireVirtualConflictAccess(conflict, me, user, guild, db);

        ConflictUtil.VirtualConflictId virtualId = conflict.getVirtualConflictId();
        if (virtualId == null) {
            return error("Temporary conflict id is missing");
        }

        VirtualConflictStorageManager storageManager = new VirtualConflictStorageManager(manager.getCloud());
        storageManager.deleteConflict(virtualId);
        return success();
    }
}
