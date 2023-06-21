package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.sheet.SheetUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class KDRSheet extends Command {
    public KDRSheet() {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return "`" + super.help() + " <nations>`";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.MILCOM.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(args.size(), 1, channel);
        if (guild == null) return "not in guild";
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);

        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));

        if (nations.isEmpty()) return usage(args.size(), unkown, channel);

        Message msg = RateLimitUtil.complete(channel().sendMessage("Clearing sheet..."));

        SpreadSheet sheet = SpreadSheet.create(guildDb, SheetKeys.WAR_COST_BY_RESOURCE_SHEET);
        List<Object> header = new ArrayList<>(Arrays.asList(
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

                "total",
                "loss",
                "dmg",
                "ratio"
        ));

        sheet.clear(SheetUtil.getRange(0, 0, header.size(), nations.size()));

        RateLimitUtil.queue(channel().editMessageById(msg.getIdLong(), "Updating (wars..."));

        sheet.setHeader(header);

        for (DBNation nation : nations) {
            RateLimitUtil.queue(channel().editMessageById(msg.getIdLong(), "Updating wars for " + nation.getNation()));
            int nationId = nation.getNation_id();

            AttackCost attInactiveCost = new AttackCost();
            AttackCost defInactiveCost = new AttackCost();
            AttackCost attActiveCost = new AttackCost();
            AttackCost defActiveCost = new AttackCost();

            AttackCost attSuicides = new AttackCost();
            AttackCost defSuicides = new AttackCost();

            {
                List<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(nationId);
                Map<Integer, List<DBAttack>> allAttacks = Locutus.imp().getWarDb().getAttacksByWar(nationId, 0);

                for (DBWar war : wars) {
                    List<DBAttack> warAttacks = allAttacks.getOrDefault(war.warId, Collections.emptyList());

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
            header.set(2, -attInactiveCost.convertedTotal(true));
            header.set(3, attInactiveCost.getNumWars() == 0 ? 0 : (-attInactiveCost.convertedTotal(true)) / (double) attInactiveCost.getNumWars());

            header.set(4, defActiveCost.getNumWars());
            header.set(5, defActiveCost.getNumWars() == 0 ? 0 : defActiveCost.convertedTotal(true) / defActiveCost.getNumWars());
            header.set(6, defActiveCost.getNumWars() == 0 ? 0 : defActiveCost.convertedTotal(false) / defActiveCost.getNumWars());
            double defRatio = (double) header.get(6) / (double) header.get(5);
            header.set(7, defActiveCost.getNumWars() == 0 ? 0 : Double.isFinite(defRatio) ? defRatio : 0);

            header.set(8, attActiveCost.getNumWars());
            header.set(9, attActiveCost.getNumWars() == 0 ? 0 : attActiveCost.convertedTotal(true) / attActiveCost.getNumWars());
            header.set(10, attActiveCost.getNumWars() == 0 ? 0 : attActiveCost.convertedTotal(false) / attActiveCost.getNumWars());
            double attRatio = (double) header.get(10) / (double) header.get(9);
            header.set(11, attActiveCost.getNumWars() == 0 ? 0 : Double.isFinite(attRatio) ? attRatio : 0);

            int numTotal = defActiveCost.getNumWars() + attActiveCost.getNumWars();
            double lossTotal = defActiveCost.convertedTotal(true) + attActiveCost.convertedTotal(true);
            double dmgTotal = defActiveCost.convertedTotal(false) + attActiveCost.convertedTotal(false);
            header.set(12, numTotal);
            header.set(13, numTotal == 0 ? 0 : lossTotal / numTotal);
            header.set(14, numTotal == 0 ? 0 : dmgTotal / numTotal);
            double ratio = (double) header.get(14) / (double) header.get(13);
            header.set(15, numTotal == 0 ? 0 : Double.isFinite(ratio) ? ratio : 0);

            sheet.addRow(header);
        }

        try {

            RateLimitUtil.queue(channel().editMessageById(msg.getIdLong(), "Uploading (sheet"));

            sheet.set(0, 0);

            RateLimitUtil.queue(channel().deleteMessageById(event.getMessageIdLong()));
        } catch (Throwable e) {
            e.printStackTrace();
        }

        sheet.attach(channel.create()).send();
        return null;
    }
}
