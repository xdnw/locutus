package link.locutus.discord.web.commands.alliance;

import gg.jte.generated.precompiled.JtebasictableGenerated;
import gg.jte.generated.precompiled.alliance.JteannouncementvariationsGenerated;
import gg.jte.generated.precompiled.alliance.JtemanageannouncementsGenerated;
import gg.jte.generated.precompiled.alliance.JteplayerannouncementsGenerated;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AllianceChange;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PW;
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
    public Object allianceLeaves(WebStore ws, int allianceId, @Switch("a") boolean includeInactive, @Switch("v") boolean includeVM, @Switch("m") boolean include) {
        List<AllianceChange> removes = Locutus.imp().getNationDB().getRemovesByAlliance(allianceId);

        String title = "Rank changes for " + MarkupUtil.htmlUrl(PW.getName(allianceId, true), PW.getUrl(allianceId, true));
        List<String> header = Arrays.asList("time", "nation", "position", "now-alliance", "now-position", "now-activity");
        List<List<String>> rows = new ArrayList<>();

        for (AllianceChange change : removes) {
            ArrayList<String> row = new ArrayList<>();

            int nationId = change.getNationId();
            DBNation nation = Locutus.imp().getNationDB().getNation(nationId);

            row.add(TimeUtil.YYYY_MM_DD_HH_MM_A.format(new Date(change.getDate())));
            row.add(MarkupUtil.htmlUrl(PW.getName(nationId, false), PW.getUrl(nationId, false)));
            row.add(change.getToRank().name());
            if (nation != null) {
                row.add(MarkupUtil.htmlUrl(nation.getAllianceName(), nation.getAllianceUrl()));
                row.add(Rank.byId(nation.getPosition()).name());
                String active = TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m());
                if (nation.getVm_turns() != 0) {
                    active = MarkupUtil.htmlColor("orange", "VM:" + TimeUtil.secToTime(TimeUnit.HOURS, nation.getVm_turns() * 2));
                } else if (nation.active_m() > 10000) {
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
        return WebStore.render(f -> JtebasictableGenerated.render(f, null, ws, title, header, ws.tableUnsafe(rows)));
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String announcements(WebStore ws, @Me GuildDB db, @Me DBNation nation, @Switch("a") boolean showArchived) {
        List<Announcement.PlayerAnnouncement> announcements = db.getPlayerAnnouncementsByNation(nation.getNation_id(), !showArchived);

        return WebStore.render(f -> JteplayerannouncementsGenerated.render(f, null, ws, db, nation, announcements));
    }

    @Command
    @RolePermission(Roles.ADMIN)
    public String manageAnnouncements(WebStore ws, @Me GuildDB db, @Me DBNation nation, @Switch("a") boolean showArchived) {
        List<Announcement> announcements = db.getAnnouncements();
        return WebStore.render(f -> JtemanageannouncementsGenerated.render(f, null, ws, db, announcements));
    }

    @Command
    @RolePermission(Roles.ADMIN)
    public String announcementVariations(WebStore ws, @Me GuildDB db, @Me DBNation nation, int announcementId) {
        Announcement announcement = db.getAnnouncement(announcementId);
        List<Announcement.PlayerAnnouncement> announcements = db.getPlayerAnnouncementsByAnnId(announcementId);
        return WebStore.render(f -> JteannouncementvariationsGenerated.render(f, null, ws, db, announcement, announcements));
    }
}
