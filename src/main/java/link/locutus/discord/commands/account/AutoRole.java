package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.task.roles.IAutoRoleTask;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class AutoRole extends Command {
    public AutoRole() {
        super("autorole", CommandCategory.USER_SETTINGS, CommandCategory.GUILD_MANAGEMENT, CommandCategory.INTERNAL_AFFAIRS);
    }

    @Override
    public String help() {
        return super.help() + " <user|*>";
    }

    @Override
    public String desc() {
        return "Auto rank users on discord";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 1) {
            return usage(event);
        }

        GuildDB db = Locutus.imp().getGuildDB(event);
        if (db == null) return "No registered guild.";
        IAutoRoleTask task = db.getAutoRoleTask();
        task.syncDB();

        StringBuilder response = new StringBuilder();

        if (args.get(0).equalsIgnoreCase("*")) {
            Function<Long, Boolean> func = new Function<Long, Boolean>() {
                @Override
                public Boolean apply(Long i) {
                    task.autoRoleAll(new Consumer<String>() {
                        private boolean messaged = false;
                        @Override
                        public void accept(String s) {
                            if (messaged) return;
                            messaged = true;
                            RateLimitUtil.queue(event.getChannel().sendMessage(s));
                        }
                    });
                    return true;
                }
            };
            if (Roles.ADMIN.hasOnRoot(event.getAuthor()) || true) {
                RateLimitUtil.queue(event.getChannel().sendMessage("Please wait..."));
                func.apply(0L);
            } else if (Roles.INTERNAL_AFFAIRS.has(event.getMember())) {
                String taskId = getClass().getSimpleName() + "." + db.getGuild().getId();
                Boolean result = TimeUtil.runTurnTask(taskId, func);
                if (result != Boolean.TRUE) return "Task already ran this turn";
            } else {
                return "No permission";
            }

            if (db.getOrNull(GuildDB.Key.ALLIANCE_ID) != null) {
                for (Map.Entry<Member, GuildDB.UnmaskedReason> entry : db.getMaskedNonMembers().entrySet()) {
                    response.append(entry.getKey().getAsMention());
                    DBNation nation = DiscordUtil.getNation(entry.getKey().getUser());
                    if (nation != null) {
                        String active = TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m());
                        if (nation.getActive_m() > 10000) active = "**" + active + "**";
                        response.append(nation.getName() + " | <" + nation.getNationUrl() + "> | " + active + " | " + Rank.byId(nation.getPosition()) + " in AA:" + nation.getAlliance());
                    }
                    response.append(" - ").append(entry.getValue());
                    response.append("\n");
                }
            }


        } else {
            List<Member> mentions = event.getMessage().getMentionedMembers();
            if (mentions.size() != 1) {
                User user = DiscordUtil.getUser(args.get(0));
                if (user != null) {
                    Member member = db.getGuild().getMember(user);
                    if (member != null) {
                        mentions.add(member);
                    }
                }
                if (mentions.size() != 1) {
                    return usage(event);
                }
            }
            Member mention = mentions.get(0);
            DBNation nation = DiscordUtil.getNation(mention.getUser());
            List<String> output = new ArrayList<>();
            Consumer<String> out = output::add;
            if (nation == null) out.accept("That nation isn't registered: `!verify`");
            task.autoRole(mention, out);

            if (!output.isEmpty()) {
                DiscordUtil.sendMessage(event.getGuildChannel(), StringMan.join(output, "\n"));
            }
        }

        response.append("Done!");

        if (db.getInfo(GuildDB.Key.AUTOROLE) == null) {
            response.append("\n - AutoRole disabled. To enable it use: `!KeyStore AutoRole`");
        }
        else response.append("\n - AutoRole Mode: ").append(db.getInfo(GuildDB.Key.AUTOROLE));
        if (db.getInfo(GuildDB.Key.AUTONICK) == null) {
            response.append("\n - AutoNick disabled. To enable it use: `!KeyStore AutoNick`");
        }
        else response.append("\n - AutoNick Mode: ").append(db.getInfo(GuildDB.Key.AUTONICK));

        return response.toString();
    }
}