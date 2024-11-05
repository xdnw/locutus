package link.locutus.discord.web.commands.api;

import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.task.ia.IACheckup;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class IAEndpoints {
    @Command
    @RolePermission(Roles.MEMBER)
    public Map<String, Object> my_audits(@Me GuildDB db, @Me DBNation nation) throws IOException, ExecutionException, InterruptedException {
        if (!db.isAllianceId(nation.getAlliance_id()) || nation.getPositionEnum().id < Rank.MEMBER.id) {
            return Map.of("success", false, "message", "You are not a member of the guild's alliance.");
        }
        Map<IACheckup.AuditType, Map.Entry<Object, String>> checkupResult = new HashMap<>();
        IACheckup checkup = new IACheckup(db, db.getAllianceList(), true);
        checkupResult = checkup.checkup(nation, true, true);
        checkupResult.entrySet().removeIf(f -> f.getValue() == null || f.getValue().getValue() == null);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("success", true);

        Map<String, Object> auditValues = new LinkedHashMap<>();
        for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : checkupResult.entrySet()) {
            IACheckup.AuditType type = entry.getKey();
            Map.Entry<Object, String> value = entry.getValue();
            Map<String, Object> auditJson = new LinkedHashMap<>();
            auditJson.put("severity", type.getSeverity().ordinal());
            auditJson.put("value", value.getKey());
            auditJson.put("description", value.getValue());
            auditValues.put(type.name(), auditJson);
        }
        root.put("audits", auditValues);
        return root;
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public Map<String, Object> announcements(@Me GuildDB db, @Me DBNation nation) {
        if (!db.isAllianceId(nation.getAlliance_id()) || nation.getPositionEnum().id < Rank.MEMBER.id) {
            return Map.of("success", false, "message", "You are not a member of the guild's alliance.");
        }
        List<Announcement.PlayerAnnouncement> all = db.getPlayerAnnouncementsByNation(nation.getNation_id(), false);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("success", true);
        Map<String, Object> announcements = new LinkedHashMap<>();
        for (Announcement.PlayerAnnouncement announcement : all) {
            Map<String, Object> announcementJson = new LinkedHashMap<>();
            announcementJson.put("active", announcement.active);
            announcementJson.put("title", announcement.getParent().title);
            announcementJson.put("content", announcement.getContent());
            announcements.put(String.valueOf(announcement.ann_id), announcementJson);
        }
        root.put("announcements", announcements);
        return root;
    }

    @Command
    @RolePermission
    public Map<String, Object> read_announcement(@Me GuildDB db, @Me DBNation nation, int ann_id) {
        db.setAnnouncementActive(ann_id, nation.getNation_id(), false);
        return Map.of("success", true);
    }

    @Command
    @RolePermission
    public Map<String, Object> unread_announcement(@Me GuildDB db, @Me DBNation nation, int ann_id) {
        db.setAnnouncementActive(ann_id, nation.getNation_id(), true);
        return Map.of("success", true);
    }

    @Command
    @RolePermission
    public Map<String, Object> balance(@Me GuildDB db, @Me DBNation nation) {
//        balance: {
//            breakdown: {[key: string]: number[]}
//            total: number[]
//            escrow: number[]
//            can_withdraw: boolean
//        }
    }
}
