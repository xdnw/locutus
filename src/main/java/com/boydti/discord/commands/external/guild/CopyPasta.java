package com.boydti.discord.commands.external.guild;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.manager.Noformat;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CopyPasta extends Command implements Noformat {
    public CopyPasta() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <key> <message>";
    }

    @Override
    public String desc() {
        return "Use `!copypasta <key>` to post a premade response\n" +
                "Use `!copypasta <key> <message>` to set the response\n" +
                "Use e.g. `ECON.key` as the key to restrict use to the econ role";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (guild == null) return "Not in a guild";
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (args.isEmpty()) {
            Set<String> options = new HashSet<>(db.getInfoMap().keySet());
            options.removeIf(f -> !f.toLowerCase().startsWith("copypasta."));
            options = options.stream().map(f -> f.split("\\.", 2)[1]).collect(Collectors.toSet());
            return usage(StringMan.join(options, ","), event.getChannel());
        }
        String key = args.get(0).toLowerCase();
        if (args.size() == 1) {
            String value = db.getInfo("copypasta." + key);

            if (value == null) {
                Map<String, String> map = db.getInfoMap();
                outer:
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    String otherKey = entry.getKey();
                    if (!otherKey.startsWith("copypasta.")) continue;

                    String[] split = otherKey.split("\\.");
                    if (!split[split.length - 1].equalsIgnoreCase(key)) continue;

                    for (int i = 1; i < split.length - 1; i++) {
                        String roleName = split[i];
                        Roles role = Roles.parse(roleName);
                        if (role != null && !role.has(event.getMember())) continue outer;

                        Role discRole = DiscordUtil.getRole(guild, roleName);
                        if (discRole != null && !event.getMember().getRoles().contains(discRole)) continue outer;

                        value = entry.getValue();
                    }
                }
            }

            if (value == null) return "No message set for `" + args.get(0) + "`. Plase use `!copypasta <key> <message>`";
            if (event.getMessage().getEmbeds().isEmpty()) {
                com.boydti.discord.util.RateLimitUtil.queue(event.getMessage().delete());
            }
            return DiscordUtil.format(guild, event.getGuildChannel(), author, me, value);
        } else {
            if (!Roles.INTERNAL_AFFAIRS.has(author, guild)) return "No permission";

            String[] split = key.split("\\.");
            for (int i = 0; i < split.length - 1; i++) {
                Roles role = Roles.parse(split[i]);
                Role discRole = DiscordUtil.getRole(guild, split[i]);
                if (role == null && discRole == null) return "Invalid role name: `" + split[i] + "` (note: Periods are used as a delimiter in the copypasta key)";
            }

            String content = DiscordUtil.trimContent(event.getMessage().getContentRaw());
            int start = content.indexOf(' ', content.indexOf(' ') + 1);
            String message = content.substring(start + 1);
            if (message.isEmpty() || message.equalsIgnoreCase("null")) {
                db.deleteInfo("copypasta." + key);
                return "Deleted message for `!copypasta " + args.get(0) + "`";
            } else {
                db.setInfo("copypasta." + key, message);
                return "Added message for `!copypasta " + args.get(0) + "`\n" +
                        "Remove using `!copypasta " + args.get(0) + " null`";
            }
        }
    }
}
