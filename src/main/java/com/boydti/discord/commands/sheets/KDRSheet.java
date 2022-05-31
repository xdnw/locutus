package com.boydti.discord.commands.sheets;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.DBWar;
import com.boydti.discord.db.entities.AttackCost;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.sheet.SheetUtil;
import com.boydti.discord.util.sheet.SpreadSheet;
import com.boydti.discord.apiv1.domains.subdomains.DBAttack;
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
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        if (guild == null) return "not in guild";
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);

        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));

        if (nations.isEmpty()) return usage(event);

        Message msg = com.boydti.discord.util.RateLimitUtil.complete(event.getChannel().sendMessage("Clearing sheet..."));

        SpreadSheet sheet = SpreadSheet.create(guildDb, GuildDB.Key.WAR_COST_BY_RESOURCE_SHEET);
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

        RateLimitUtil.queue(event.getChannel().editMessageById(msg.getIdLong(), "Updating (wars..."));

        sheet.setHeader(header);

        for (DBNation nation : nations) {
            RateLimitUtil.queue(event.getChannel().editMessageById(msg.getIdLong(), "Updating wars for " + nation.getNation()));
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
                        if (attack.attacker_nation_id == nationId) {
                            selfAttack = true;
                        } else {
                            enemyAttack = true;
                        }
                    }

                    Function<DBAttack, Boolean> isPrimary = a -> a.attacker_nation_id == nationId;
                    Function<DBAttack, Boolean> isSecondary = a -> a.attacker_nation_id != nationId;

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

            RateLimitUtil.queue(event.getChannel().editMessageById(msg.getIdLong(), "Uploading (sheet"));

            sheet.set(0, 0);

            com.boydti.discord.util.RateLimitUtil.queue(event.getChannel().deleteMessageById(event.getMessageIdLong()));
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return "<" + sheet.getURL() + ">";
    }
}
