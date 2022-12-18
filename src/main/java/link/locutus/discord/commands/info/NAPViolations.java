package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.rankings.builder.GroupedRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.CounterType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarParser;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Deprecated
public class NAPViolations extends Command {
    public NAPViolations() {
        super(CommandCategory.FOREIGN_AFFAIRS, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return super.help() + " [alliance]";
    }

    @Override
    public String desc() {
        return "List the NAP violations since GW 16\n" +
                "Note: Results are generated, and may contain innaccuracies";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        long cutoff = 1608192693000L;

        String alliances = "Black Knights,Carthago,Church Of Atom,Eclipse,House Stark,Order of the White Rose,Seven Kingdoms,The Knights Radiant,The Syndicate,Amarr Empire,Chocolate Castle,Convent of Atom,Golden Phoenix Coalition,micro pockyclypse,Oceania,Rothschild Family,The Legion,The Enterprise,The Rohirrim ,Alpha,Aurora,Solar Knights,Strickland Propane,Children of the Light,Error 404,Knights Templar,Grumpy Old Bastards,Guardian,99942 Apophis,Dragon Confederacy,Dual Suns,Her Divine Imperial Majestys Throne,Mothership Zeta,NukaWorld,Nuka-Cola Bottling Company,Advanced Syndicalist Mechanics,Atlas Technologies,Camelot,Rose,Schrute Farms,World Task Force,Akatsuki,Golden Dawn,Oblivion,Oromo Peoples Coaltion,Farkistan,The Ampersand,The Federation,The Lost Empire,United Socialist Nations,Ad Meliora,Global Alliance & Treaty Organization,Nova Roma,The Jedi Order,The Knights of The Stratosphere,The Lost Legion,The Void Touched,Unforgiven Legion,United Ummah,Dark Brotherhood,Spanish Armada,The Commonwealth,United Purple Nations,Celtic Commonwealth,Corporate Union,Crusaders of the Holy Boogaloo,Goon Squad,Purple Flower Garden,Respublica Romana,The Greater Unitary Republic,The Hanseatic League,The Imperial Federation,Rothschild Family,Weebunism,Wigglytufts Guild,Soldiers of Liberty,The Fighting Pacifists,The Immortals,Democracy,Federation of Soviet Republics,The Mortals,The United Armies,Taith,Yarr";
        Set<Integer> aaIds = new HashSet<>();
        for (String aaStr : alliances.split(",")) {
            aaStr = aaStr.trim();
            Integer aaId = PnwUtil.parseAllianceId(aaStr);
            if (aaId == null) {
                continue;
            }
            aaIds.add(aaId);
        }

        Integer aaId = null;
        Set<Integer> coal1 = new HashSet<>(aaIds);
        if (args.size() == 1) {
            aaId = PnwUtil.parseAllianceId(args.get(0));
            if (aaId == null) return "Invalid alliance: `" + args.get(0) + "`";
            if (!aaIds.contains(aaId)) return "Alliance isn't under NAP";
            coal1 = Collections.singleton(aaId);
        }

        WarParser parser = WarParser.of(coal1, null, aaIds, null, cutoff, TimeUtil.getTimeFromTurn(224772));
        Map<Integer, DBWar> wars = parser.getWars();

        Map<Integer, Integer> fought = new HashMap<>();
        Map<Integer, Integer> peaced = new HashMap<>();
        Map<Integer, Integer> expired = new HashMap<>();

        Map<Integer, List<DBWar>> violations = new HashMap<>();

        int total = 0;
        int unknown = 0;

        for (Map.Entry<Integer, DBWar> entry : wars.entrySet()) {
            DBWar war = entry.getValue();
            if (!aaIds.contains(war.defender_aa)) continue;
            if (!aaIds.contains(war.attacker_aa)) continue;
            DBNation defender = DBNation.byId(war.defender_id);
            if (defender == null) continue;
            CounterStat stats = war.getCounterStat();
            if (stats.type == CounterType.IS_COUNTER) continue;

            Rank rank = defender.getAlliancePosition(war.date).getValue();
            if (rank.id > Rank.APPLICANT.id) {
                ByteBuffer key = ByteBuffer.allocate(4);
                key.putInt(war.warId);
//                String appInfo = Locutus.imp().getDiscordDB().getInfo(new String(key.array()));
//                if (appInfo != null) {
//                    if (appInfo.getBytes()[0] == 0) continue;
//                }
                total++;

                violations.computeIfAbsent(war.attacker_aa, f -> new ArrayList<>()).add(war);
                switch (war.status) {
                    case ACTIVE:
                    case DEFENDER_VICTORY:
                    case ATTACKER_VICTORY:
                        fought.put(war.attacker_aa, fought.getOrDefault(war.attacker_aa, 0) + 1);
                        break;
                    case PEACE:
                    case DEFENDER_OFFERED_PEACE:
                    case ATTACKER_OFFERED_PEACE:
                        peaced.put(war.attacker_aa, peaced.getOrDefault(war.attacker_aa, 0) + 1);
                        break;
                    case EXPIRED:
                        expired.put(war.attacker_aa, expired.getOrDefault(war.attacker_aa, 0) + 1);
                        break;
                }
            }
        }

        SummedMapRankBuilder<Integer, Integer> ranking = new GroupedRankBuilder<>(violations).sumValues(f -> 1).sort();
        Map<Integer, Integer> rankingMap = ranking.get();

        if (args.isEmpty()) {
            RankBuilder<String> named = ranking.name(new Function<Map.Entry<Integer, Integer>, String>() {
                @Override
                public String apply(Map.Entry<Integer, Integer> entry) {
                    int aaId = entry.getKey();
                    String aaName = PnwUtil.getName(aaId, true);
                    int aaFought = fought.getOrDefault(aaId, 0);
                    int aaPeaced = peaced.getOrDefault(aaId, 0);
                    int aaExpired = expired.getOrDefault(aaId, 0);
                    return aaName + ": " + aaFought + "/" + aaPeaced + "/" + aaExpired;
                }
            });
            String title = "Possible NAP violations: fought/peaced/expired";
            named.build(event, title);

            return "Total: " + total + "\nUse `" + Settings.commandPrefix(true) + "NapViolations [alliance]` for more AA info";
        } else if (args.size() == 1) {
            int rank = 0;
            for (Map.Entry<Integer, Integer> entry : rankingMap.entrySet()) {
                rank++;
                if (entry.getKey().equals(aaId)) break;
            }
            List<DBWar> warsFought = Locutus.imp().getWarDb().getWars(Collections.singleton(aaId), cutoff);
            String rankStr = rank + "/" + aaIds.size();

            String title = "Possible NAP violations: " + PnwUtil.getName(aaId, true);
            StringBuilder result = new StringBuilder();
            result.append(PnwUtil.getMarkdownUrl(aaId, true)).append("\n");
            result.append("Total wars since NAP: " + warsFought.size());

            result.append("\n\n**Violations:** #" + rankStr + "\n");
            result.append("Fought: " + fought.getOrDefault(aaId, 0)).append("\n");
            result.append("Peaced: " + peaced.getOrDefault(aaId, 0)).append("\n");
            result.append("Expired: " + expired.getOrDefault(aaId, 0)).append("\n\n");

            List<DBWar> aaWars = violations.getOrDefault(aaId, new ArrayList<>());
            if (!aaWars.isEmpty()) {
                result.append("Wars:\n");
                for (DBWar war : aaWars) {
                    result.append("`" + Settings.commandPrefix(true) + "warinfo " + war.warId + "` - " + war.status + " - " + PnwUtil.getName(war.defender_aa, true) + "\n");
                }
            }

            DiscordUtil.createEmbedCommand(event.getChannel(), title, result.toString());
            return null;
        } else {
            return usage();
        }
    }
}
