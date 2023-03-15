package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.User;

public class KeyStoreCommands {

    @Command
    @RolePermission(Roles.ADMIN)
    public String register(@Me GuildDB db, @Me User author, DBAlliance alliance) {
        GuildDB.Key key = GuildDB.Key.ALLIANCE_ID;
        String value = key.toString(alliance.getId());
        return setKey(db, author, key, value);
    }

    private String setKey(GuildDB db, User author, GuildDB.Key key, String value) {
        if (!key.hasPermission(db, author, value)) return "No permission to set " + key + " to `" + value + "`";
        db.setInfo(key, value);
        if (key == GuildDB.Key.API_KEY) value = "<redacted>";
        else value = key.toString(db.getOrThrow(key));
        return "Set " + key + " to `" + value + "`";
    }
}
