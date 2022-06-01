package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarParser;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.apiv1.enums.WarType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class WarSheet extends Command {
    @Override
    public String help() {
        return "!" + getClass().getSimpleName() + " <allies> <enemies> [time]";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MILCOM.has(user, server);
    }

    @Override
    public String desc() {
        return "List active wars\n" +
                "Add `-i` to list inactive wars";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2 || args.size() > 3) {
            return usage(event);
        }
        Set<DBNation> allies = DiscordUtil.parseNations(guild, args.get(0));
        Set<DBNation> enemies = DiscordUtil.parseNations(guild, args.get(1));

        long now = System.currentTimeMillis();
        long cutoff = now - TimeUnit.DAYS.toMillis(5);
        if (args.size() == 3) cutoff = now - (TimeUtil.timeToSec(args.get(2)) * 1000L);

        WarParser parser1 = WarParser.ofAANatobj(null, allies, null, enemies, cutoff, now);
        WarParser parser2 = WarParser.ofAANatobj(null, enemies, null, allies, cutoff, now);

        Set<DBWar> allWars = new HashSet<>();
        allWars.addAll(parser1.getWars().values());
        allWars.addAll(parser2.getWars().values());

        if (!flags.contains('i')) allWars.removeIf(f -> !f.isActive());
        allWars.removeIf(f -> {
            DBNation att = f.getNation(true);
            DBNation def = f.getNation(false);
            return (!allies.contains(att) && !enemies.contains(att)) || (!allies.contains(def) && !enemies.contains(def));
        });

        SpreadSheet sheet = null;
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String arg = iter.next();
            if (arg.startsWith("sheet:")) {
                sheet = SpreadSheet.create(arg);
                iter.remove();;
            }
        }
        if (sheet == null) {
            sheet = SpreadSheet.create(Locutus.imp().getGuildDB(guild), GuildDB.Key.WAR_SHEET);
        }

        List<Object> headers = new ArrayList<>(Arrays.asList(
                "id",
                "type",
                "counter",
                "GS",
                "AS",
                "B",
                "ships",
                "planes",
                "tanks",
                "soldiers",
                "cities",
                "MAP",
                "Resistance",
                "Attacker",
                "Att AA",
                "Turns",
                "Def AA",
                "Defender",
                "Resistance",
                "MAP",
                "Cities",
                "Soldiers",
                "Tanks",
                "Planes",
                "Ships",
                "GS",
                "AS",
                "B"
        ));

        sheet.setHeader(headers);

        for (DBWar war : allWars) {
            DBNation att = war.getNation(true);
            DBNation def = war.getNation(false);

            if (att == null || def == null) continue;

            WarType type = war.getWarType();
            WarCard card = new WarCard(war, true, false);


            headers.set(0, MarkupUtil.sheetUrl(war.warId + "", war.toUrl()));
            headers.set(1, war.getWarType().name());
            CounterStat counterStat = card.getCounterStat();
            headers.set(2, counterStat == null ? "" : counterStat.type.name());
            headers.set(3, card.groundControl == war.attacker_id ? "Y" : "N");
            headers.set(4, card.airSuperiority == war.attacker_id ? "Y" : "N");
            headers.set(5, card.blockaded == war.attacker_id ? "Y" : "N");
            headers.set(6, att.getShips());
            headers.set(7, att.getAircraft());
            headers.set(8, att.getTanks());
            headers.set(9, att.getSoldiers());
            headers.set(10, att.getCities());
            headers.set(11, card.attackerMAP);
            headers.set(12, card.attackerResistance);
            headers.set(13, MarkupUtil.sheetUrl(att.getNation(), att.getNationUrl()));
            headers.set(14, MarkupUtil.sheetUrl(att.getAlliance(), att.getAllianceUrl()));

            long turnStart = TimeUtil.getTurn(war.date);
            long turns = 60 - (TimeUtil.getTurn() - turnStart);
            headers.set(15, turns);

            headers.set(16, MarkupUtil.sheetUrl(def.getAlliance(), def.getAllianceUrl()));
            headers.set(17, MarkupUtil.sheetUrl(def.getNation(), def.getNationUrl()));
            headers.set(18, card.defenderResistance);
            headers.set(19, card.defenderMAP);
            headers.set(20, def.getCities());
            headers.set(21, def.getSoldiers());
            headers.set(22, def.getTanks());
            headers.set(23, def.getAircraft());
            headers.set(24, def.getShips());
            headers.set(25, card.groundControl == war.defender_id ? "Y" : "N");
            headers.set(26, card.airSuperiority == war.defender_id ? "Y" : "N");
            headers.set(27, card.blockaded == war.defender_id ? "Y" : "N");

            sheet.addRow(headers);
        }

        sheet.clear("A:Z");
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }
}
