package com.boydti.discord.commands.sync;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.update.WarUpdateProcessor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class SyncAttacks extends Command {
    public SyncAttacks() {
        super("syncattacks", CommandCategory.DEBUG, CommandCategory.LOCUTUS_ADMIN);
    }
    @Override
    public String help() {
        return "syncattacks";
    }

    @Override
    public String desc() {
        return "debug";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public synchronized String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        WarUpdateProcessor.checkActiveConflicts();
        if (args.size() == 0) {
            Locutus.imp().getWarDb().updateAttacks();
            for (DBNation value : Locutus.imp().getNationDB().getNations().values()) {
                if (value.isBeige()) {
                    value.getBeigeTurns(true);
                }
            }
        } else if (args.size() == 1) {
            Locutus.imp().getWarDb().updateAttacks(Integer.parseInt(args.get(0)), false);
        } else {
            return usage();
        }

        return "Done!";
    }
}