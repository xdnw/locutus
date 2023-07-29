package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

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
import java.util.function.Function;

public class WarCostByResourceSheet extends Command {
    public WarCostByResourceSheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM, CommandCategory.ECON);
    }

    @Override
    public String help() {
        return "`" + super.help() + " <nations> <enemy-alliances> <days>`";
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

        Set<Integer> attAAId = DiscordUtil.parseAlliances(guild, args.get(0));
        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));
        Set<Integer> alliances = args.get(1).equals("*") ? null : DiscordUtil.parseAlliances(guild, args.get(1));
        Function<Integer, Boolean> aaFilter = f -> true;

        if (nations.isEmpty()) return usage("No nations found for `" + args.get(0) + "`", channel);
        if (alliances == null || alliances.isEmpty()) {
            if (!args.get(1).equalsIgnoreCase("*")) return usage("No alliances found for: `" + args.get(1) + "`", channel);
        } else {
            aaFilter = f -> alliances.contains(f);
        }

        Integer days = MathMan.parseInt(args.get(2));
        if (days == null) {
            return "Invalid number of days: `" + args.get(2) + "`";
        }
        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        CompletableFuture<IMessageBuilder> msgFuture = channel.sendMessage("Please wait...");

        SpreadSheet sheet = SpreadSheet.create(guildDb, SheetKeys.WAR_COST_BY_RESOURCE_SHEET);
        List<Object> header = new ArrayList<>(Arrays.asList(
                // Raids	Raid profit	Raid Ratio	Def	Def Loss	Def Dmg	Def Ratio	Off	Off Loss	Off Dmg	Off Ratio	Wars	War Loss	War Dmg	War Ratio
            "nation",
            "alliance",
            "wars"
        ));

        for (ResourceType type : ResourceType.values) {
            header.add(type.name());
        }
        header.add("convertedTotal");

        sheet.clear("A:Z");

        sheet.setHeader(header);

        long start = System.currentTimeMillis();
        for (DBNation nation : nations) {
            if (System.currentTimeMillis() - start > 10000) {
                IMessageBuilder msg = msgFuture.get();
                if (msg != null && msg.getId() > 0) {
                    msg.clear().append("Updating wars for " + nation.getNation()).sendIfFree();
                }
                start = System.currentTimeMillis();
            }
            int nationId = nation.getNation_id();

            AttackCost activeCost = new AttackCost();

            {
                List<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(nationId);
                Map<Integer, List<AbstractCursor>> allAttacks = Locutus.imp().getWarDb().getAttacksByNationGroupWar(nationId, cutoffMs);

                for (DBWar war : wars) {
                    if (war.date < cutoffMs) {
                        continue;
                    }
                    if (!aaFilter.apply(war.attacker_aa) && !aaFilter.apply(war.defender_aa)) {
                        continue;
                    }
                    if (attAAId != null && !attAAId.isEmpty() && !attAAId.contains(war.attacker_aa) && !attAAId.contains(war.defender_aa)) {
                        continue;
                    }

                    List<AbstractCursor> warAttacks = allAttacks.getOrDefault(war.warId, Collections.emptyList());

                    boolean selfAttack = false;
                    boolean enemyAttack = false;

                    for (AbstractCursor attack : warAttacks) {
                        if (attack.getAttacker_id() == nationId) {
                            selfAttack = true;
                        } else {
                            enemyAttack = true;
                        }
                    }

                    Function<AbstractCursor, Boolean> isPrimary = a -> a.getAttacker_id() == nationId;
                    Function<AbstractCursor, Boolean> isSecondary = a -> a.getAttacker_id() != nationId;

                    AttackCost cost = null;
                    if (war.attacker_id == nationId) {
                        if (selfAttack) {
                            if (enemyAttack) {
                                cost = activeCost;
                            } else if (flags.contains('g')) {
                                cost = activeCost;
                            }
                        } else if (enemyAttack) {
//                            cost = attSuicides;
                        } else {
                            continue;
                        }
                    } else {
                        if (selfAttack) {
                            if (enemyAttack) {
                                cost = activeCost;
                            } else if (flags.contains('g')) {
                                cost = activeCost;
                            }
                        } else if (enemyAttack && flags.contains('d')) {
                            cost = activeCost;
                        } else {
                            continue;
                        }
                    }

                    if (cost != null) {
                        cost.addCost(warAttacks, isPrimary, isSecondary);
                    }
                }
            }

            header = new ArrayList<>();
            header.add(MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
            header.add(MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getAllianceUrl()));

            int numWars = activeCost.getNumWars();
            header.add(numWars);

            Map<ResourceType, Double> total = new HashMap<>();

            if (!flags.contains('c')) {
                total = PnwUtil.add(total, activeCost.getConsumption(true));
            }
            if (!flags.contains('i')) {
                double infraCost = activeCost.getInfraLost(true);
                total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + infraCost);
            }
            if (!flags.contains('l')) {
                total = PnwUtil.add(total, activeCost.getLoot(true));
            }
            if (!flags.contains('u')) {
                total = PnwUtil.add(total, activeCost.getUnitCost(true));
            }

            if (flags.contains('n')) {
                for (Map.Entry<ResourceType, Double> entry : total.entrySet()) {
                    entry.setValue(entry.getValue() / nation.getCities());
                }
            }
            if (flags.contains('w')) {
                for (Map.Entry<ResourceType, Double> entry : total.entrySet()) {
                    entry.setValue(numWars == 0 ? 0 : entry.getValue() / numWars);
                }
            }

            for (ResourceType type : ResourceType.values) {
                header.add(total.getOrDefault(type, 0d));
            }
            header.add(PnwUtil.convertedTotal(total));

            sheet.addRow(header);
        }

        try {
            IMessageBuilder msg = msgFuture.get();
            if (msg != null && msg.getId() > 0) channel.delete(msg.getId());
        } catch (Throwable e) {
            e.printStackTrace();
        }
        sheet.set(0, 0);
        sheet.attach(channel.create()).send();
        return null;
    }
}
