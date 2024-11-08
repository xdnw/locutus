package link.locutus.discord.web.commands.api;

import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.war.RaidCommand;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.announce.AnnounceType;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.*;
import link.locutus.discord.web.commands.page.PageHelper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class IAEndpoints extends PageHelper {
    @Command
    @RolePermission(Roles.MEMBER)
    @ReturnType(value = WebAudits.class, cache = CacheType.SessionStorage, duration = 30)
    public WebSuccess my_audits(@Me GuildDB db, @Me DBNation nation) throws IOException, ExecutionException, InterruptedException {
        if (!db.isAllianceId(nation.getAlliance_id()) || nation.getPositionEnum().id < Rank.MEMBER.id) {
            return error("You are not a member of the guild's alliance.");
        }
        Map<IACheckup.AuditType, Map.Entry<Object, String>> checkupResult = new HashMap<>();
        IACheckup checkup = new IACheckup(db, db.getAllianceList(), true);
        checkupResult = checkup.checkup(nation, true, true);
        checkupResult.entrySet().removeIf(f -> f.getValue() == null || f.getValue().getValue() == null);


        WebAudits audits = new WebAudits();
        for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : checkupResult.entrySet()) {
            IACheckup.AuditType type = entry.getKey();
            Map.Entry<Object, String> value = entry.getValue();
            audits.values.add(new WebAudit(type.name(), type.getSeverity().ordinal(), value.getKey().toString(), value.getValue()));
        }

        return audits;
    }

    @Command
    @RolePermission(Roles.MEMBER)
    @ReturnType(value = WebAnnouncements.class, cache = CacheType.SessionStorage, duration = 30)
    public WebSuccess announcements(@Me GuildDB db, @Me DBNation nation) {
        if (!db.isAllianceId(nation.getAlliance_id()) || nation.getPositionEnum().id < Rank.MEMBER.id) {
            return error("You are not a member of the guild's alliance.");
        }
        List<Announcement.PlayerAnnouncement> all = db.getPlayerAnnouncementsByNation(nation.getNation_id(), false);
        all.removeIf(f -> f.getParent().type != AnnounceType.MESSAGE);

        WebAnnouncements result = new WebAnnouncements();
        for (Announcement.PlayerAnnouncement announcement : all) {
            result.values.add(new WebAnnouncement(announcement.ann_id, announcement.getParent().type.ordinal(), announcement.active, announcement.getParent().title, announcement.getContent()));
        }
        return result;
    }

    @Command
    @RolePermission(Roles.MEMBER)
    @ReturnType(WebSuccess.class)
    public WebSuccess read_announcement(@Me GuildDB db, @Me DBNation nation, int ann_id) {
        db.setAnnouncementActive(ann_id, nation.getNation_id(), false);
        return success();
    }

    @Command
    @RolePermission(Roles.MEMBER)
    @ReturnType(WebSuccess.class)
    public WebSuccess unread_announcement(@Me GuildDB db, @Me DBNation nation, int ann_id) {
        db.setAnnouncementActive(ann_id, nation.getNation_id(), true);
        return success();
    }

//    @Command
//    @RolePermission(Roles.MEMBER)
//    @ReturnType(WebBalance.class)
//    public Map<String, Object> balance(@Me GuildDB db, @Me DBNation nation) {
////        balance: {
////            breakdown: {[key: string]: number[]}
////            total: number[]
////            escrow: number[]
////            can_withdraw: boolean
////        }
//        return null;
//    }
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    @ReturnType({List.class, WebTarget.class})
//    public Map<String, Object> raid(@Me GuildDB db, @Me DBNation me,
//                                    @Default("*") Set<DBNation> nations,
//                                    @Default("false") boolean weak_ground,
//                                    @Default("0") int vm_turns,
//                                    @Default("0") int beige_turns,
//                                    @Default("false") boolean ignore_dnr,
//                                    @Default("7d") @Timediff long time_inactive,
//                                    @Default("-1") double min_loot,
//                                    @Default("8") int num_results) {
//        RaidCommand.getNations(
//                db,
//                me,
//                nations,
//                weak_ground,
//                vm_turns,
//                -1,
//                beige_turns > 0,
//                !ignore_dnr,
//                null,
//                true,
//                true,
//                time_inactive / TimeUnit.MINUTES.toMillis(1),
//                me.getScore(),
//                min_loot, beige_turns,
//                false, false, num_results
//
//        );
//        return null;
//
//    }
}
