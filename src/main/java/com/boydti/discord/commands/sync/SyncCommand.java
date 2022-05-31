package com.boydti.discord.commands.sync;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.task.SyncUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class SyncCommand extends Command {
    public SyncCommand() {
        super("sync", CommandCategory.DEBUG, CommandCategory.LOCUTUS_ADMIN);
    }
    @Override
    public String help() {
        return "sync";
    }

    @Override
    public String desc() {
        return "debug";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() > 1) return usage();
        if (args.size() == 1) {
            DBNation nation = DiscordUtil.parseNation(args.get(0));
            if (nation == null) return "Invalid nation: `" + args.get(0) + "`";
            nation.getPnwNation();
            return "Updated " + nation.getNation();
        } else if (!Roles.ADMIN.hasOnRoot(author)) return "No permission";

        Message message = com.boydti.discord.util.RateLimitUtil.complete(event.getChannel().sendMessage("Please wait while we update the database using the PnW API: ~5m"));

        boolean force = (args.size() == 1 && args.get(0).equalsIgnoreCase("-f"));

        Consumer<String> msgUpd = new Consumer<String>() {
            @Override
            public void accept(String s) {
                RateLimitUtil.queue(event.getChannel().editMessageById(message.getIdLong(), s));
            }
        };
        if (force) {
            SyncUtil.INSTANCE.sync(msgUpd, force);
        } else {
            if (SyncUtil.INSTANCE.syncIfFree(msgUpd, force)) {

            } else if (SyncUtil.INSTANCE.isLocked()) {
                msgUpd.accept("Error: Sync already in progress");
            } else {
                msgUpd.accept("Sync failed (see console). Maybe try `!sync -f` ?");
            }
        }
        return null;
    }
}
