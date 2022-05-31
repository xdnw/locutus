package com.boydti.discord.commands.external.account;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.PNWUser;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.FileUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class LoadUsers extends Command {
    public LoadUsers() {
        super("loaduserstest", CommandCategory.LOCUTUS_ADMIN);
    }
    @Override
    public String help() {
        return null;
    }

    @Override
    public String desc() {
        return null;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        String[] lines = FileUtil.readFile("/users.txt").split("\\r?\\n");
        for (String line : lines) {
            int index = line.indexOf('|');
            if (index == -1) continue;
            int nationId = Integer.parseInt(line.substring(0, index).trim());
            if (Locutus.imp().getDiscordDB().getUserFromNationId(nationId) != null) {
                continue;
            }
            String name = line.substring(index + 1, line.length()).trim();
            PNWUser user = new PNWUser(nationId, null, name);
            try {
                if (user.getUser() == null) {
                    Locutus.imp().getDiscordDB().addUser(user);
                }
            } catch (IllegalArgumentException e) {
            }
        }
        return "Done!";
    }
}
