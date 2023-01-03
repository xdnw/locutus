package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class WarCostByAASheet extends Command {
    public WarCostByAASheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return "`" + super.help() + " <nations> <time>`";
    }

    @Override
    public String desc() {
        return "Warcost (for each alliance) broken down\n" +
                "Add -i to include inactives\n" +
                "Add -a to include applicants\n";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).isValidAlliance() && Roles.MILCOM.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage();
//        boolean includeUntaxable = flags.contains('t');
        boolean includeInactive = flags.contains('i');
        boolean includeApps = flags.contains('a');

        long cutoff = 0;
        if (args.size() == 2) cutoff = System.currentTimeMillis() - TimeUtil.timeToSec(args.get(1)) * 1000l;

        Set<DBNation> nationSet = DiscordUtil.parseNations(guild, args.get(0));
        Map<Integer, List<DBNation>> nationsByAA = Locutus.imp().getNationDB().getNationsByAlliance(nationSet, false, !includeInactive, !includeApps, true);

        boolean byAlliance = args.get(0).contains("*");

        GuildDB guildDb = Locutus.imp().getGuildDB(guild);

        SpreadSheet sheet = SpreadSheet.create(guildDb, GuildDB.Key.WAR_COST_BY_ALLIANCE_SHEET);
        List<Object> header = new ArrayList<>(Arrays.asList(
                "alliance",
                "score",
                "cities",

                "net unit dmg",
                "net infra dmg",
                "net loot dmg",
                "net consumption",
                "net dmg",

                "unit loss",
                "infra loss",
                "consume loss",
                "loss",

                "unit dmg",
                "infra dmg",
                "consume dmg",
                "dmg",

                "money",
                "gasoline",
                "munitions",
                "aluminum",
                "steel"
        ));
        sheet.setHeader(header);

        for (Map.Entry<Integer, List<DBNation>> entry : nationsByAA.entrySet()) {
            int aaId = entry.getKey();
            AttackCost warCost = new AttackCost();

            List<DBNation> aaNations = entry.getValue();
            Set<Integer> nationIds = new HashSet<>();
            for (DBNation aaNation : aaNations) nationIds.add(aaNation.getNation_id());

            List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacksAny(nationIds, cutoff);

            attacks.removeIf(n -> {
                DBNation nat1 = Locutus.imp().getNationDB().getNation(n.attacker_nation_id);
                DBNation nat2 = Locutus.imp().getNationDB().getNation(n.attacker_nation_id);
                return nat1 == null || nat2 == null || !nationsByAA.containsKey(nat1.getAlliance_id()) || !nationsByAA.containsKey(nat2.getAlliance_id());
            });

            Function<DBAttack, Boolean> isPrimary = new Function<DBAttack, Boolean>() {
                @Override
                public Boolean apply(DBAttack attack) {
                    return Locutus.imp().getNationDB().getNation(attack.attacker_nation_id).getAlliance_id() == aaId;
                }
            };
            warCost.addCost(attacks, isPrimary, f -> !isPrimary.apply(f));

            DBAlliance alliance = DBAlliance.getOrCreate(entry.getKey());


            ArrayList<Object> row = new ArrayList<>();
            row.add(MarkupUtil.sheetUrl(alliance.getName(), alliance.getUrl()));

            SimpleNationList aaMembers = new SimpleNationList(alliance.getNations());
            row.add(aaMembers.getScore());
            row.add(aaMembers.getTotal().getCities());

            row.add(-PnwUtil.convertedTotal(warCost.getNetUnitCost(true)));
            row.add(-PnwUtil.convertedTotal(warCost.getNetInfraCost(true)));
            row.add(-PnwUtil.convertedTotal(warCost.getLoot(true)));
            row.add(-PnwUtil.convertedTotal(warCost.getNetConsumptionCost(true)));
            row.add(-PnwUtil.convertedTotal(warCost.getNetCost(true)));

            row.add(PnwUtil.convertedTotal(warCost.getUnitCost(true)));
            row.add((warCost.getInfraLost(true)));
            row.add(PnwUtil.convertedTotal(warCost.getConsumption(true)));
            row.add(PnwUtil.convertedTotal(warCost.getTotal(true)));

            row.add(PnwUtil.convertedTotal(warCost.getUnitCost(false)));
            row.add((warCost.getInfraLost(false)));
            row.add(PnwUtil.convertedTotal(warCost.getConsumption(false)));
            row.add(PnwUtil.convertedTotal(warCost.getTotal(false)));

            row.add(warCost.getUnitCost(true).getOrDefault(ResourceType.MONEY, 0d) - warCost.getLoot(true).getOrDefault(ResourceType.MONEY, 0d));
            row.add(warCost.getTotal(true).getOrDefault(ResourceType.GASOLINE, 0d));
            row.add(warCost.getTotal(true).getOrDefault(ResourceType.MUNITIONS, 0d));
            row.add(warCost.getTotal(true).getOrDefault(ResourceType.ALUMINUM, 0d));
            row.add(warCost.getTotal(true).getOrDefault(ResourceType.STEEL, 0d));

            sheet.addRow(row);
        }

        sheet.clear("A:Z");
        sheet.set(0, 0);

        sheet.attach(new DiscordChannelIO(event).create()).send();
        return null;
    }
}
