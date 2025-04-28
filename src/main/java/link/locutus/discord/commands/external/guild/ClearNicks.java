package link.locutus.discord.commands.external.guild;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

public class ClearNicks extends Command {
    private final Map<Long, String> previous = new Long2ObjectOpenHashMap<>();

    public ClearNicks() {
        super(CommandCategory.GUILD_MANAGEMENT);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.has(user, server);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.role.clearNicks.cmd);
    }
    @Override
    public String help() {
        return super.help() + "[*]";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        int failed = 0;
        String msg = null;
        List<Future<?>> tasks = new ArrayList<>();
        for (Member member : guild.getMembers()) {
            if (member.getNickname() != null) {
                try {
                    String nick;
                    if (args.isEmpty()) {
                        previous.put(member.getIdLong(), member.getNickname());
                        nick = null;
                    } else {
                        if (args.get(0).equalsIgnoreCase("*")) {
                            nick = previous.get(member.getIdLong());
                        } else {
                            previous.put(member.getIdLong(), member.getNickname());
                            nick = DiscordUtil.trimContent(fullCommandRaw);
                            nick = nick.substring(nick.indexOf(' ') + 1);
                        }
                    }
                    tasks.add(RateLimitUtil.queue(member.modifyNickname(nick)));
                } catch (Throwable e) {
                    msg = e.getMessage();
                    failed++;
                }
            }
        }
        for (Future<?> task : tasks) {
            task.get();
        }
        if (failed != 0) {
            return "Failed to clear " + failed + " nicknames for reason: " + msg;
        }
        return "Cleared all possible nicknames!";
    }
}
