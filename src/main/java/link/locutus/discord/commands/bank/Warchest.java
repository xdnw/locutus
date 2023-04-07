package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
<<<<<<< HEAD
import link.locutus.discord.apiv1.enums.DepositType;
=======
import link.locutus.discord.apiv1.enums.ResourceType;
>>>>>>> pr/15
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

<<<<<<< HEAD
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
=======
import java.util.*;
>>>>>>> pr/15

public class Warchest extends Command {
    public Warchest() {
        super(CommandCategory.ECON, CommandCategory.MILCOM);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server) && server != null;
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "warchest <*|nations|tax_url> <resources> <note>";
    }

    @Override
    public String desc() {
        return "Determine how much to send to each member to meet their warchest requirements (per city)\n" +
<<<<<<< HEAD
                "Add `-s` to skip checking stockpile\n" +
                "add `-m` to convert to money\n" +
                "Add `-b` to bypass checks\n" +
                "Add e.g. `nation:blah` to specify a nation account\n" +
                "Add e.g. `alliance:blah` to specify an alliance account\n" +
                "Add e.g. `offshore:blah` to specify an offshore account\n" +
                "Add e.g. `tax_id:blah` to specify a tax bracket";
=======
                "Add `-s` to skip checking stockpile.";
>>>>>>> pr/15
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        DBNation nationAccount = null;
        DBAlliance allianceAccount = null;
        DBAlliance offshoreAccount = null;
        TaxBracket taxAccount = null;

        String nationAccountStr = DiscordUtil.parseArg(args, "nation");
        if (nationAccountStr != null) {
            nationAccount = PWBindings.nation(author, nationAccountStr);
        }

        String allianceAccountStr = DiscordUtil.parseArg(args, "alliance");
        if (allianceAccountStr != null) {
            allianceAccount = PWBindings.alliance(allianceAccountStr);
        }

        String offshoreAccountStr = DiscordUtil.parseArg(args, "offshore");
        if (offshoreAccountStr != null) {
            offshoreAccount = PWBindings.alliance(offshoreAccountStr);
        }

        String taxIdStr = DiscordUtil.parseArg(args, "tax_id");
        if (taxIdStr == null) taxIdStr = DiscordUtil.parseArg(args, "bracket");
        if (taxIdStr != null) {
            taxAccount = PWBindings.bracket(guildDb, "tax_id=" + taxIdStr);
        }

        if (args.size() < 3) {
            return usage(event, "Current warchest (per city): " + PnwUtil.resourcesToString(guildDb.getPerCityWarchest(me)));
        }

<<<<<<< HEAD
=======
        String note;
        note = args.get(2);
        Collection<String> allowedLabels = Arrays.asList("#warchest", "#grant", "#deposit", "#trade", "#ignore", "#tax", "#account");
        if (!allowedLabels.contains(note.split("=")[0]))
            return "Please use one of the following labels: " + StringMan.getString(allowedLabels);
        Integer aaId = Locutus.imp().getGuildDB(guild).getOrNull(GuildDB.Key.ALLIANCE_ID);
        note += "=" + Objects.requireNonNullElseGet(aaId, guild::getIdLong);

        boolean hasEcon = Roles.ECON.has(author, guild);
        Collection<DBNation> nations;
        if (args.get(0).equalsIgnoreCase("*")) {
            if (!hasEcon) {
                return "No permission: " + Roles.ECON.name();
            }
            nations = Locutus.imp().getNationDB().getNations(Collections.singleton(aaId));
        } else {
            nations = DiscordUtil.parseNations(event.getGuild(), args.get(0));
        }
        if (nations.isEmpty()) return "No nation specified.";
        if (!hasEcon && (nations.size() != 1 || !nations.iterator().next().equals(me)))
            return "You only have permission to send to your own nation.";


        nations.removeIf(f -> f.getActive_m() > 7200);
        nations.removeIf(f -> f.getPosition() <= 1);
        nations.removeIf(f -> f.getVm_turns() != 0);

        if (nations.isEmpty()) {
            return "No nation in this tax bracket.";
        }
>>>>>>> pr/15

        Map<ResourceType, Double> perCity = PnwUtil.parseResources(args.get(1));
        if (perCity.isEmpty()) return "Invalid amount: `" + args.get(1) + "`";
        boolean ignoreInactives = !flags.contains('i');

        DepositType.DepositTypeInfo type = PWBindings.DepositTypeInfo(args.get(2));

        String arg = args.get(0);
        List<DBNation> nations = new ArrayList<>(DiscordUtil.parseNations(event.getGuild(), arg));
        if (nations.size() != 1 || !flags.contains('b')) {
            nations.removeIf(n -> n.getPosition() <= 1);
            nations.removeIf(n -> n.getVm_turns() != 0);
            nations.removeIf(n -> n.getActive_m() > 2880);
            nations.removeIf(n -> n.isGray() && n.getOff() == 0);
            nations.removeIf(n -> n.isBeige() && n.getCities() <= 4);
        }
        if (nations.isEmpty()) {
            return "No nations found (add `-f` to force send)";
        }
        boolean skipStockpile = flags.contains('s');
<<<<<<< HEAD
        return UnsortedCommands.warchest(
                guildDb,
                new DiscordChannelIO(event.getChannel()),
                guild,
                author,
                me,
                new SimpleNationList(nations),
                perCity,
                type,
                skipStockpile,
                nationAccount,
                allianceAccount,
                offshoreAccount,
                taxAccount,
                null,
                flags.contains('m'),
                flags.contains('b'),
                flags.contains('f'));
=======
        if (!flags.contains('s')) {
            if (aaId == null) return "No alliance found for this guild. Add `-s` to skip checking stockpile.";
            memberResources2 = DBAlliance.getOrCreate(aaId).getMemberStockpile();
        }
        for (DBNation nation : nations) {
            Map<ResourceType, Double> stockpile = memberResources2.getOrDefault(nation, skipStockpile ? Collections.emptyMap() : null);

            if (PnwUtil.convertedTotal(stockpile) < 0) continue;

            Map<ResourceType, Double> toSendCurrent = new HashMap<>();
            for (ResourceType type : perCity.keySet()) {
                double required = perCity.getOrDefault(type, 0d) * nation.getCities();
                double current = stockpile.getOrDefault(type, 0d);
                if (required > current) {
                    toSendCurrent.put(type, required - current);
                }
            }
            if (!toSendCurrent.isEmpty()) {
                fundsToSendNations.put(nation, toSendCurrent);
            }
        }

        String result = Disperse.disperse(guildDb, fundsToSendNations, Collections.emptyMap(), note, new DiscordChannelIO(event), "Send Warchest");
        if (fundsToSendNations.size() > 1) {
            RateLimitUtil.queue(event.getGuildChannel().sendMessage(author.getAsMention()));
        }
        return result;
>>>>>>> pr/15
    }
}
