package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.User;

public class PlayerSettingCommands {
    @Command(desc = "Mark an announcement by Locutus as read/unread")
    @RolePermission(Roles.MEMBER)
    public String readAnnouncement(@Me GuildDB db, @Me DBNation nation, int ann_id, @Default Boolean markRead) {
        if (markRead == null) markRead = true;
        db.setAnnouncementActive(ann_id, nation.getNation_id(), !markRead);
        return "Marked announcement #" + ann_id + " as " + (markRead ? "" : "un") + " read";
    }

    @Command(desc = "Opt out of war room relays and ia channel logging")
    public String optOut(@Me User user, DiscordDB db, @Default("true") boolean optOut) {
        byte[] data = new byte[]{(byte) (optOut ? 1 : 0)};
        db.setInfo(DiscordMeta.OPT_OUT, user.getIdLong(), data);
        if (optOut) {
            for (GuildDB guildDB : Locutus.imp().getGuildDatabases().values()) {
                guildDB.deleteInterviewMessages(user.getIdLong());
            }
        }
        return "Set " + DiscordMeta.OPT_OUT + " to " + optOut;
    }

}
