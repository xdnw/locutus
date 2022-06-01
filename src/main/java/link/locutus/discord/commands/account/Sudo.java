package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.dummy.DelegateMessage;
import link.locutus.discord.commands.manager.dummy.DelegateMessageEvent;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class Sudo extends Command implements Noformat {
    public Sudo() {
        super("sudo", CommandCategory.LOCUTUS_ADMIN);
    }
    @Override
    public String help() {
        return "!sudo <user> <command>";
    }

    @Override
    public String desc() {
        return "Run a command as another user";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() < 2) {
            return usage(event);
        }
        String arg0 = args.get(0);
        String content = DiscordUtil.trimContent(event.getMessage().getContentRaw());
        int start = content.indexOf(' ', content.indexOf(' ') + 1);

        String command = content.substring(start + 1);

        User user = DiscordUtil.getMention(arg0);

        Command cmd = Locutus.imp().getCommandManager().getCommandMap().get(args.get(1).substring(1).toLowerCase());
        if (cmd == null) {
            return "Unknown command: ``" + args.get(1) + "`" + "`";
        }

        Guild guild = event.isFromGuild() ? event.getGuild() : null;

        DBNation nation;
        if (user == null) {
            Integer nationId = DiscordUtil.parseNationId(arg0);

            if (nationId == null) {
                return "Invalid nation: `" + arg0 + "`";
            }
            nation = Locutus.imp().getNationDB().getNation(nationId);
        } else {
            nation = DiscordUtil.getNation(user);
            if (nation == null) {
                return "Invalid nation: `" + arg0 + "`";
            }
            if (!Roles.ADMIN.hasOnRoot(event.getAuthor()) || !cmd.checkPermission(guild, user)) {
                user = null;
            }
        }

        User finalUser = user;
        Message message = new DelegateMessage(event.getMessage()) {
            @Nonnull
            @Override
            public String getContentRaw() {
                return command;
            }

            @Nonnull
            @Override
            public User getAuthor() {
                return finalUser == null ? event.getAuthor() : finalUser;
            }

            @Nullable
            @Override
            public Member getMember() {
                return getGuild().getMember(getAuthor());
            }
        };

        MessageReceivedEvent finalEvent = new DelegateMessageEvent(event.isFromGuild() ? event.getGuild() : null, event.getResponseNumber(), message);
        DiscordUtil.withNation(nation, () -> {
            Locutus.imp().getCommandManager().run(finalEvent, false, true);
            return null;
        });
        return null;
    }

}
