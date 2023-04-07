package link.locutus.discord.commands.account;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.roles.IAutoRoleTask;
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
        return "Auto-Role discord users with registered role and alliance role.";
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
            Function<Long, Boolean> func = i -> {
                task.autoRoleAll(new Consumer<>() {
                    private boolean messaged = false;

                    @Override
                    public void accept(String s) {
                        if (messaged) return;
                        messaged = true;
                        RateLimitUtil.queue(event.getChannel().sendMessage(s));
                    }
                });
                return true;
            };
            Roles.ADMIN.hasOnRoot(event.getAuthor());
            RateLimitUtil.queue(event.getChannel().sendMessage("Please wait..."));
            func.apply(0L);

            if (db.hasAlliance()) {
                for (Map.Entry<Member, GuildDB.UnmaskedReason> entry : db.getMaskedNonMembers().entrySet()) {
                    response.append(entry.getKey().getAsMention());
                    DBNation nation = DiscordUtil.getNation(entry.getKey().getUser());
                    if (nation != null) {
                        String active = TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m());
                        if (nation.getActive_m() > 10000) active = "**" + active + "**";
                        response.append(nation.getName()).append(" | <").append(nation.getNationUrl()).append("> | ").append(active).append(" | ").append(Rank.byId(nation.getPosition())).append(" in the alliance:").append(nation.getAllianceName());
                    }
                    response.append(" - ").append(entry.getValue());
                    response.append("\n");
                }
            }


        } else {
            DBNation nation = DiscordUtil.parseNation(args.get(0));
            if (nation == null) return "User is not registered in-game.";
            User user = nation.getUser();
            if (user == null) return "Nation is not registered.";
            Member member = db.getGuild().getMember(user);
            List<String> output = new ArrayList<>();
            Consumer<String> out = output::add;
            task.autoRole(member, out);

            if (!output.isEmpty()) {
                DiscordUtil.sendMessage(event.getGuildChannel(), StringMan.join(output, "\n"));
            }
        }

        response.append("Done!");

        if (db.getOrNull(GuildDB.Key.AUTOROLE) == null) {
<<<<<<< HEAD
            response.append("\n - AutoRole disabled. To enable it use: " + CM.settings.cmd.create(GuildDB.Key.AUTOROLE.name(), null, null, null).toSlashCommand() + "");
        }
        else response.append("\n - AutoRole Mode: ").append(db.getOrNull(GuildDB.Key.AUTOROLE) + "");
        if (db.getOrNull(GuildDB.Key.AUTONICK) == null) {
            response.append("\n - AutoNick disabled. To enable it use: " + CM.settings.cmd.create(GuildDB.Key.AUTONICK.name(), null, null, null).toSlashCommand() + "");
        }
        else response.append("\n - AutoNick Mode: ").append(db.getOrNull(GuildDB.Key.AUTONICK) + "");
        if (Roles.REGISTERED.toRole(db) == null) response.append("\n - Please set a registered role: " + CM.role.setAlias.cmd.create(Roles.REGISTERED.name(), "", null).toSlashCommand() + "");
=======
            response.append("\n - AutoRole disabled. To enable it use: ").append(CM.settings.cmd.create(GuildDB.Key.AUTOROLE.name(), null).toSlashCommand());
        } else response.append("\n - AutoRole Mode: ").append(db.getInfo(GuildDB.Key.AUTOROLE));
        if (db.getOrNull(GuildDB.Key.AUTONICK) == null) {
            response.append("\n - AutoNick disabled. To enable it use: ").append(CM.settings.cmd.create(GuildDB.Key.AUTONICK.name(), null).toSlashCommand());
        } else response.append("\n - AutoNick Mode: ").append(db.getInfo(GuildDB.Key.AUTONICK));
        if (Roles.REGISTERED.toRole(db) == null)
            response.append("\n - Please set a registered role: ").append(CM.role.setAlias.cmd.create(Roles.REGISTERED.name(), "").toSlashCommand());
>>>>>>> pr/15

        return response.toString();
    }
}