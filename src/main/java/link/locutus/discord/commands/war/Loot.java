package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.LootEntry;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Loot extends Command {
    public Loot() {
        super("loot", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.nation.loot.cmd);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "loot <nation|alliance>";
    }

    @Override
    public String desc() {
        return "Get nation or bank loot history\n" +
                "Add `score:1234` to specify looting score (for bank loot)\n" +
                "Add `-p` to use pirate war policy";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null) return false;
        if (nation.getAlliance_id() == 0 || nation.getPositionEnum().id <= 1 || nation.getAlliance_id() == 6143) return false;
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String scoreStr = DiscordUtil.parseArg(args, "score");
        Double score = scoreStr == null ? null : PrimitiveBindings.Double(scoreStr);
        if (args.size() != 1) {
            return usage(args.size(), 1, channel);
        }
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention();
        }

        NationOrAlliance nationOrAlliance = PWBindings.nationOrAlliance(args.get(0));
        return UtilityCommands.loot(channel, me, nationOrAlliance, score, flags.contains('p'));
    }
}
