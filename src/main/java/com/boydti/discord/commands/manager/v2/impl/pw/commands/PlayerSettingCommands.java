package com.boydti.discord.commands.manager.v2.impl.pw.commands;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.v2.binding.annotation.Command;
import com.boydti.discord.commands.manager.v2.binding.annotation.Default;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import com.boydti.discord.db.DiscordDB;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.DiscordMeta;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import net.dv8tion.jda.api.entities.User;

public class PlayerSettingCommands {
    @Command
    @RolePermission(Roles.MEMBER)
    public String readAnnouncement(@Me GuildDB db, @Me DBNation nation, int ann_id, @Default Boolean markRead) {
        if (markRead == null) markRead = true;
        db.setAnnouncementActive(ann_id, nation.getNation_id(), !markRead);
        return "Marked announcement #" + ann_id + " as " + (markRead ? "" : "un") +  " read";
    }

    @Command(desc = "Opt out of war room relays and ia channel logging")
    public String optOut(@Me User user, DiscordDB db, @Default("true") boolean value) {
        byte[] data = new byte[]{(byte) (value ? 1 : 0)};
        db.setInfo(DiscordMeta.OPT_OUT, user.getIdLong(), data);
        if (value) {
            for (GuildDB guildDB : Locutus.imp().getGuildDatabases().values()) {
                guildDB.deleteInterviewMessages(user.getIdLong());
            }
        }
        return "Set " + DiscordMeta.OPT_OUT + " to " + value;
    }

}
