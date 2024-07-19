package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
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
    public List<CommandRef> getSlashReference() {
        return List.of(CM.copyPasta.cmd);
    }
    @Override
    public String desc() {
        return "Use `" + Settings.commandPrefix(true) + "copypasta <key>` to post a premade response\n" +
                "Use `" + Settings.commandPrefix(true) + "copypasta <key> <message>` to set the response\n" +
                "Use e.g. `ECON.key` as the key to restrict use to the econ role.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (guild == null) return "Not in a guild";
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (args.isEmpty()) {
            Set<String> options = new HashSet<>(db.getInfoMap().keySet());
            options.removeIf(f -> !f.toLowerCase().startsWith("copypasta."));
            options = options.stream().map(f -> f.split("\\.", 2)[1]).collect(Collectors.toSet());
            return usage(StringMan.join(options, ","), channel);
        }
        String key = args.get(0).toLowerCase();
        if (args.size() == 1) {
            String value = db.getCopyPasta(key, true);

            if (value == null) {
                Map<String, String> map = db.getInfoMap();
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    String otherKey = entry.getKey();
                    if (!otherKey.startsWith("copypasta.")) continue;

                    String[] split = otherKey.split("\\.");
                    if (!split[split.length - 1].equalsIgnoreCase(key)) continue;

                    if (!db.getMissingCopypastaPerms(otherKey, guild.getMember(author)).isEmpty()) continue;

                    value = entry.getValue();
                }
            } else if (!db.getMissingCopypastaPerms(key, guild.getMember(author)).isEmpty())
                return "You do not have permission to use that key.";

            if (value == null)
                return "No message set for `" + args.get(0) + "`. Plase use `" + Settings.commandPrefix(true) + "copypasta <key> <message>`";
            IMessageBuilder existing = channel.getMessage();
            if (existing != null && existing.getId() > 0) channel.delete(existing.getId());
            NationPlaceholders formatter = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
            return formatter.format2(guild, me, author, value, me, false);
        } else {
            if (!Roles.INTERNAL_AFFAIRS.has(author, guild)) return "No permission.";

            String[] split = key.split("\\.");
            for (int i = 0; i < split.length - 1; i++) {
                Roles role = Roles.parse(split[i]);
                Role discRole = DiscordUtil.getRole(guild, split[i]);
                if (role == null && discRole == null)
                    return "Invalid role name: `" + split[i] + "` (note: Periods are used as a delimiter in the copypasta key)";
            }

            String content = DiscordUtil.trimContent(fullCommandRaw);
            int start = content.indexOf(' ', content.indexOf(' ') + 1);
            String message = content.substring(start + 1);
            if (message.isEmpty() || message.equalsIgnoreCase("null")) {
                db.deleteCopyPasta(key);
                return "Deleted message for `" + Settings.commandPrefix(true) + "copypasta " + args.get(0) + "`";
            } else {
                db.setCopyPasta(key, message);
                return "Added message for `" + Settings.commandPrefix(true) + "copypasta " + args.get(0) + "`\n" +
                        "Remove using `" + Settings.commandPrefix(true) + "copypasta " + args.get(0) + " null`";
            }
        }
    }
}
