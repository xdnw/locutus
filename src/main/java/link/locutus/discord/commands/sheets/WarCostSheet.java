package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.rankings.builder.GroupedRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.WarParser;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

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
import java.util.stream.Collectors;

public class WarCostSheet extends Command {
    public WarCostSheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return "`" + super.help() + " <attackers> <defenders> [time]`";
    }

    @Override
    public String desc() {
        return "Warcost (for each nation) broken down by war type.\n" +
                "Add -c to exclude consumption cost\n" +
                "Add -i to exclude infra cost\n" +
                "Add -l to exclude loot cost\n" +
                "Add -u to exclude unit cost\n" +
                "Add -n to normalize it per city";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).isValidAlliance() && Roles.MILCOM.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 3) return usage(args.size(), 3, channel);
        if (guild == null) return "not in guild";
        long millis = TimeUtil.timeToSec(args.get(2)) * 1000L;
        long cutOff = System.currentTimeMillis() - millis;

        GuildDB guildDb = Locutus.imp().getGuildDB(guild);

        WarParser parser1 = WarParser.of(guild, args.get(0), args.get(1), cutOff);

        CompletableFuture<IMessageBuilder> msgFuture = (channel.sendMessage("Please wait..."));

        SpreadSheet sheet = SpreadSheet.create(guildDb, SheetKeys.WAR_COST_SHEET);
        List<Object> header = new ArrayList<>(Arrays.asList(
                // Raids	Raid profit	Raid Ratio	Def	Def Loss	Def Dmg	Def Ratio	Off	Off Loss	Off Dmg	Off Ratio	Wars	War Loss	War Dmg	War Ratio
            "nation",
            "raids",
            "profit",

            "avg",
            "def",
            "loss",
            "dmg",
            "ratio",

            "off",
            "loss",
            "dmg",
            "ratio",

            "total wars",
            "loss",
            "dmg",
            "ratio"
        ));

        sheet.clear("A:Z");

        sheet.setHeader(header);
        long start = System.currentTimeMillis();

        Map<Integer, DBWar> allWars = new HashMap<>(parser1.getWars());

        Map<DBNation, List<DBWar>> warsByNation = new RankBuilder<>(allWars.values()).group(new BiConsumer<DBWar, GroupedRankBuilder<DBNation, DBWar>>() {
            @Override
            public void accept(DBWar war, GroupedRankBuilder<DBNation, DBWar> group) {
                DBNation attacker = war.getNation(true);
                DBNation defender = war.getNation(false);
                if (attacker != null && parser1.getIsPrimary().apply(war)) {
                    group.put(attacker, war);
                }
                if (defender != null && parser1.getIsSecondary().apply(war)) {
                    group.put(defender, war);
                }
            }
        }).get();

        List<DBAttack> allAttacks = new ArrayList<>(parser1.getAttacks());

        for (Map.Entry<DBNation, List<DBWar>> entry : warsByNation.entrySet()) {
            DBNation nation = entry.getKey();
            if (-start + (start = System.currentTimeMillis()) > 5000) {
                IMessageBuilder msg = msgFuture.get();
                if (msg != null && msg.getId() > 0) {
                    msg.clear().append("Updating wars for " + nation.getNation()).sendIfFree();
                }
            }
            int nationId = nation.getNation_id();

            AttackCost attInactiveCost = new AttackCost();
            AttackCost defInactiveCost = new AttackCost();
            AttackCost attActiveCost = new AttackCost();
            AttackCost defActiveCost = new AttackCost();

            AttackCost attSuicides = new AttackCost();
            AttackCost defSuicides = new AttackCost();

            {
                List<DBWar> wars = entry.getValue();
                Set<Integer> warIds = wars.stream().map(f -> f.warId).collect(Collectors.toSet());
                List<DBAttack> attacks = new ArrayList<>();
                for (DBAttack attack : allAttacks) if (warIds.contains(attack.getWar_id())) attacks.add(attack);
                Map<Integer, List<DBAttack>> attacksByWar = new RankBuilder<>(attacks).group(f -> f.getWar_id()).get();

                for (DBWar war : wars) {
                    List<DBAttack> warAttacks = attacksByWar.getOrDefault(war.warId, Collections.emptyList());

                    boolean selfAttack = false;
                    boolean enemyAttack = false;

                    for (DBAttack attack : warAttacks) {
                        if (attack.getAttacker_nation_id() == nationId) {
                            selfAttack = true;
                        } else {
                            enemyAttack = true;
                        }
                    }

                    Function<DBAttack, Boolean> isPrimary = a -> a.getAttacker_nation_id() == nationId;
                    Function<DBAttack, Boolean> isSecondary = a -> a.getAttacker_nation_id() != nationId;

                    AttackCost cost;
                    if (war.attacker_id == nationId) {
                        if (selfAttack) {
                            if (enemyAttack) {
                                cost = attActiveCost;
                            } else {
                                cost = attInactiveCost;
                            }
                        } else if (enemyAttack) {
                            cost = attSuicides;
                        } else {
                            continue;
                        }
                    } else {
                        if (selfAttack) {
                            if (enemyAttack) {
                                cost = defActiveCost;
                            } else {
                                cost = defSuicides;
                            }
                        } else if (enemyAttack) {
                            cost = defInactiveCost;
                        } else {
                            continue;
                        }
                    }

                    cost.addCost(warAttacks, isPrimary, isSecondary);
                }
            }

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));

            header.set(1, attInactiveCost.getNumWars());
            header.set(2, -total(flags, nation, attInactiveCost,true));
            header.set(3, attInactiveCost.getNumWars() == 0 ? 0 : (-total(flags, nation, attInactiveCost, true)) / (double) attInactiveCost.getNumWars());

            header.set(4, defActiveCost.getNumWars());
            header.set(5, defActiveCost.getNumWars() == 0 ? 0 : total(flags, nation, defActiveCost,true) / defActiveCost.getNumWars());
            header.set(6, defActiveCost.getNumWars() == 0 ? 0 : total(flags, nation, defActiveCost,false) / defActiveCost.getNumWars());
            double defRatio = (double) header.get(6) / (double) header.get(5);
            header.set(7, defActiveCost.getNumWars() == 0 ? 0 : Double.isFinite(defRatio) ? defRatio : 0);

            header.set(8, attActiveCost.getNumWars());
            header.set(9, attActiveCost.getNumWars() == 0 ? 0 : total(flags, nation, attActiveCost,true) / attActiveCost.getNumWars());
            header.set(10, attActiveCost.getNumWars() == 0 ? 0 : total(flags, nation, attActiveCost,false) / attActiveCost.getNumWars());
            double attRatio = (double) header.get(10) / (double) header.get(9);
            header.set(11, attActiveCost.getNumWars() == 0 ? 0 : Double.isFinite(attRatio) ? attRatio : 0);

            int numTotal = defActiveCost.getNumWars() + attActiveCost.getNumWars();
            double lossTotal = total(flags, nation, defActiveCost,true) + total(flags, nation, attActiveCost,true);
            double dmgTotal = total(flags, nation, defActiveCost,false) + total(flags, nation, attActiveCost,false);
            header.set(12, numTotal);
            header.set(13, numTotal == 0 ? 0 : lossTotal / numTotal);
            header.set(14, numTotal == 0 ? 0 : dmgTotal / numTotal);
            double ratio = (double) header.get(14) / (double) header.get(13);
            header.set(15, numTotal == 0 ? 0 : Double.isFinite(ratio) ? ratio : 0);

            sheet.addRow(header);
        }

        sheet.clear("A:Z");
        sheet.set(0, 0);
        try {
            IMessageBuilder msg = msgFuture.get();
            if (msg != null && msg.getId() > 0) channel.delete(msg.getId());
        } catch (Throwable e) {
            e.printStackTrace();
        }

        sheet.attach(channel.create()).send();
        return null;
    }

    private double total(Set<Character> flags, DBNation nation, AttackCost cost, boolean isPrimary) {
        Map<ResourceType, Double> total = new HashMap<>();

        if (!flags.contains('c')) {
            total = PnwUtil.add(total, cost.getConsumption(isPrimary));
        }
        if (!flags.contains('i')) {
            double infraCost = cost.getInfraLost(isPrimary);
            total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + infraCost);
        }
        if (!flags.contains('l')) {
            total = PnwUtil.add(total, cost.getLoot(isPrimary));
        }
        if (!flags.contains('u')) {
            total = PnwUtil.add(total, cost.getUnitCost(isPrimary));
        }

        if (flags.contains('n')) {
            for (Map.Entry<ResourceType, Double> entry : total.entrySet()) {
                entry.setValue(entry.getValue() / nation.getCities());
            }
        }
        return PnwUtil.convertedTotal(total);
    }
}
