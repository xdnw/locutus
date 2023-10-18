package link.locutus.discord.commands.sheets;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.scheduler.TriFunction;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.jooq.meta.derby.sys.Sys;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class WarCostByResourceSheet extends Command {
    public WarCostByResourceSheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM, CommandCategory.ECON);
    }

    @Override
    public String help() {
        return "`" + super.help() + " <nations> <enemy-alliances> <time>`";
    }

    @Override
    public String desc() {
        return "Transfer sheet of warcost (for each nation) broken down by resource type.\n" +
                "Add -c to exclude consumption cost\n" +
                "Add -i to exclude infra cost\n" +
                "Add -l to exclude loot cost\n" +
                "Add -u to exclude unit cost\n" +
                "Add -g to include gray inactive nations\n" +
                "Add -d to include non fighting defensive wars\n" +
                "Add -n to normalize it per city\n" +
                "Add -w to normalize it per war";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ECON.has(user, server) || Roles.MILCOM.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 3) return usage(args.size(), 3, channel);
        if (guild == null) return "not in guild";
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);

        Set<NationOrAlliance> attackers = PWBindings.nationOrAlliance(null, guild, args.get(0), author, me);
        Set<NationOrAlliance> defenders = PWBindings.nationOrAlliance(null, guild, args.get(1), author, me);

        long timeRel = TimeUtil.timeToSec(args.get(2));
        if (timeRel < 60) {
            return "Invalid time: `" + args.get(2) + "` use e.g. `5d` for days";
        }

        long cutoffMs = System.currentTimeMillis() - timeRel * 1000;

        return StatCommands.WarCostByResourceSheet(
                channel,
                guildDb,
                attackers,
                defenders,
                cutoffMs,
                flags.contains('c'),
                flags.contains('i'),
                flags.contains('l'),
                flags.contains('u'),
                flags.contains('g'),
                flags.contains('d'),
                flags.contains('n'),
                flags.contains('w'),
            null);
    }
}
