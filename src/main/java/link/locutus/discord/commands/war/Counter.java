package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class Counter extends Command {
    public Counter() {
        super("counter", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.war.counter.nation.cmd, CM.war.counter.url.cmd);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "counter <war> [alliance|coalition|role]";
    }

    @Override
    public String desc() {
        return "Get a list of nations to counter\n" +
                "Add `-o` to ignore nations with 5 offensive slots\n" +
                "Add `-w` to filter out weak attackers\n" +
                "Add `-a` to only list active nations (past hour)\n" +
                "Add `-d` to require discord\n" +
                "Add `-p` to mention added nations\n" +
                "Add `-s` to allow same alliance countering\n" +
                "Add `-i` to include inactive nations\n" +
                "Add `-m` to include applicants";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 2) {
            return usage(args.size(), 1, 2, channel);
        }
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention();
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);
        DBNation counter;
        int defenderId;

        String arg0 = args.get(0);
        if (!arg0.startsWith("" + Settings.INSTANCE.PNW_URL() + "/nation/war/") || !arg0.contains("=")) {
            defenderId = 0;
            Integer counterId = DiscordUtil.parseNationId(arg0);
            if (counterId == null) {
                return "Invalid `war-url` or `nation`:`" + arg0 + "`";
            }
            counter = Locutus.imp().getNationDB().getNationById(counterId);
        } else {
            int warId = Integer.parseInt(arg0.split("=")[1].replaceAll("/", ""));
            DBWar war = Locutus.imp().getWarDb().getWar(warId);
            int counterId = war.getAttacker_id();
            counter = Locutus.imp().getNationDB().getNationById(counterId);

            if (counter.getAlliance_id() == me.getAlliance_id() || (guild != null && Locutus.imp().getGuildDB(guild).getCoalition("allies").contains(counter.getAlliance_id()))) {
                counterId = (war.getDefender_id());
                counter = Locutus.imp().getNationDB().getNationById(counterId);
                defenderId = (war.getAttacker_id());
            } else {
                defenderId = (war.getDefender_id());
            }
        }

        Set<DBNation> counterWith = null;
        if (args.size() >= 2) {
            if (args.get(1).equalsIgnoreCase("*")) {
                Set<Integer> aaIds = Locutus.imp().getGuildDB(guild).getAllianceIds();
                Set<Integer> allies = Locutus.imp().getGuildDB(guild).getCoalition("allies");
                if (!aaIds.isEmpty()) allies.addAll(aaIds);
                counterWith = Locutus.imp().getNationDB().getNationsByAlliance(allies);
            } else {
                try {
                    counterWith = DiscordUtil.parseNations(guild, author, me, args.get(1), false, false);
                } catch (Throwable e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        }

        boolean allowMaxOff = flags.contains('o');
        boolean onlyActive = flags.contains('a');
        boolean filterWeak = flags.contains('w');
        boolean requireDiscord = flags.contains('d');
        boolean ping = flags.contains('p');
        boolean allowSameAA = flags.contains('s');
        boolean includeInactive = flags.contains('i');
        boolean includeApplicants = flags.contains('m');

        return WarCommands.counter(me, db, counter, counterWith, allowMaxOff, filterWeak, onlyActive, requireDiscord, allowSameAA, includeInactive, includeApplicants, ping);
    }
}