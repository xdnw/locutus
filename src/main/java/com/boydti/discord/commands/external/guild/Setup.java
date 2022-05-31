package com.boydti.discord.commands.external.guild;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.user.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.boydti.discord.db.GuildDB.Key.*;

public class Setup extends Command {
    public Setup() {
        super("setup", "locutus", CommandCategory.GUILD_MANAGEMENT);
    }

    @Override
    public String help() {
        return super.help();
    }

    @Override
    public String desc() {
        return super.desc();
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        GuildDB guildDb = Locutus.imp().getGuildDB(event);
        for (GuildDB.Key key : values()) {
            if (!key.requiresSetup) continue;
            if (key.requires != null) {
                if (guildDb.getInfo(key.requires) == null) continue;
            }
            String value = guildDb.getInfo(key);
            if (value == null) {
                return "Please use `!KeyStore " + key.name() + " <value>`";
            }
            try {
                key.validate(guildDb, value);
            } catch (Throwable e) {
                return e.getMessage() + " for " + key.name();
            }
        }

        for (Roles role : Roles.values()) {
            if (role.toRole(event.getGuild()) == null) {
                return "Please use `!AliasRole " + role.name() + " <discord-role>`";
            }
        }

        Map<String, Set<Integer>> coalitions = guildDb.getCoalitions();
        {
            Set<Integer> offshores = coalitions.getOrDefault("offshore", new LinkedHashSet<>());
            boolean hasValidOffshore = false;
            for (int allianceId : offshores) {
                String name = Locutus.imp().getNationDB().getAllianceName(allianceId);
                if (name != null) hasValidOffshore = true;
            }
            if (!hasValidOffshore) {
                return "Please set an offshore using !setcoalition <alliance> offshore";
            }

            if (coalitions.getOrDefault("allies", new LinkedHashSet<>()).isEmpty()) {
                return "Please set allies using `!setcoalition <alliance> allies";
            }
        }

        return "All set! If you are lacking permission for a command, please ping `@borg`";
    }
}
