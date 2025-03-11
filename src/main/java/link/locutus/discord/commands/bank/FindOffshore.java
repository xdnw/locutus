package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class FindOffshore extends Command {
    public FindOffshore() {
        super("FindOffshore", "FindOffshores", CommandCategory.ECON);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.offshore.find.for_coalition.cmd);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + getClass().getSimpleName() + " <alliance> [days]";
    }

    @Override
    public String desc() {
        return "Find potential offshores used by an alliance.\n" +
                "Use `-c` to display transfer count instead of value";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return usage(args.size(), 1, channel);
        }
        Integer alliance = PW.parseAllianceId(args.get(0));
        if (alliance == null) {
            return "Invalid alliance: `" + args.get(0) + "`";
        }

        int days = 7;
        if (args.size() == 2) {
            days = Integer.parseInt(args.get(1));
        }

        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;
        JSONObject command = CM.offshore.find.for_coalition.cmd
                .alliance(alliance + "").cutoffMs(days + "d").transfer_count(flags.contains('c') ? "true" : null)
                .toJson();
        return UtilityCommands.findOffshore(channel, command, DBAlliance.getOrCreate(alliance), cutoffMs, flags.contains('c'));
    }
}
