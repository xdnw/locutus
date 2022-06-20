package link.locutus.discord.web.commands.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Announcement;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.Rank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AlliancePages {
    @Command
    public Object allianceLeaves(int allianceId, @Switch('a') boolean includeInactive, @Switch('v') boolean includeVM, @Switch('m') boolean include) {
        Map<Integer, Map.Entry<Long, Rank>> removes = Locutus.imp().getNationDB().getRemovesByAlliance(allianceId);

        String title = "Rank changes for " + MarkupUtil.htmlUrl(PnwUtil.getName(allianceId, true), PnwUtil.getUrl(allianceId, true));
        List<String> header = Arrays.asList("time", "nation", "position", "now-alliance", "now-position", "now-activity");
        List<List<Object>> rows = new ArrayList<>();

        for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
            ArrayList<Object> row = new ArrayList<>();

            Map.Entry<Long, Rank> timeRank = entry.getValue();
            int nationId = entry.getKey();
            DBNation nation = Locutus.imp().getNationDB().getNation(entry.getKey());

            row.add(TimeUtil.YYYY_MM_DD_HH_MM_A.format(new Date(timeRank.getKey())));
            row.add(MarkupUtil.htmlUrl(PnwUtil.getName(nationId, false), PnwUtil.getUrl(nationId, false)));
            row.add(timeRank.getValue());
            if (nation != null) {
                row.add(MarkupUtil.htmlUrl(nation.getAllianceName(), nation.getAllianceUrl()));
                row.add(Rank.byId(nation.getPosition()));
                String active = TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m());
                if (nation.getVm_turns() != 0) {
                    active = MarkupUtil.htmlColor("orange", "VM:" + TimeUtil.secToTime(TimeUnit.HOURS, nation.getVm_turns() * 2));
                } else if (nation.getActive_m() > 10000) {
                    active = MarkupUtil.htmlColor("#8B8000", active);
                }
                row.add(active);
            } else {
                row.add("DELETED");
                row.add("DELETED");
                row.add(MarkupUtil.htmlColor("red", "DELETED"));
            }
            rows.add(row);
        }
        return views.basictable.template(title, header, rows).render().toString();
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String announcements(@Me GuildDB db, @Me DBNation nation, @Switch('a') boolean showArchived) {
        List<Announcement.PlayerAnnouncement> announcements = db.getPlayerAnnouncementsByNation(nation.getNation_id(), !showArchived);

        return views.alliance.playerannouncements.template(db, nation, announcements).render().toString();
    }

    @Command
    @RolePermission(Roles.ADMIN)
    public String manageAnnouncements(@Me GuildDB db, @Me DBNation nation, @Switch('a') boolean showArchived) {
        List<Announcement> announcements = db.getAnnouncements();
        return views.alliance.manageannouncements.template(db, announcements).render().toString();
    }

    @Command
    @RolePermission(Roles.ADMIN)
    public String announcementVariations(@Me GuildDB db, @Me DBNation nation, int announcementId) {
        Announcement announcement = db.getAnnouncement(announcementId);
        List<Announcement.PlayerAnnouncement> announcements = db.getPlayerAnnouncementsByAnnId(announcementId);
        return views.alliance.announcementvariations.template(db, announcement, announcements).render().toString();
    }
}
