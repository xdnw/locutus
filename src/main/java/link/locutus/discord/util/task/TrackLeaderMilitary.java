package link.locutus.discord.util.task;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class TrackLeaderMilitary implements Runnable {
    private final Set<DBAlliance> alliances;

    public TrackLeaderMilitary(Set<DBAlliance> alliances) {
        this.alliances = alliances;
    }

    public TrackLeaderMilitary(int topX) {
        this(DBAlliance.getTopX(topX, false));
    }

    public void run() {
        Set<DBNation> toCheck = new HashSet<>();
        for (DBAlliance alliance : alliances) {
            List<DBNation> nations = alliance.getNations(true, 120, true);
            nations.removeIf(f -> f.getPosition() < Rank.OFFICER.id);
            toCheck.addAll(nations);
        }
        long date = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        for (DBNation nation : toCheck) {
            if (nation.getOff() != 0 || nation.getDef() != 0) continue;

            // only check if they have bought units
            if (
                    nation.getUnits(MilitaryUnit.SOLDIER) == nation.getUnits(MilitaryUnit.SOLDIER, date)
                    && nation.getUnits(MilitaryUnit.TANK) == nation.getUnits(MilitaryUnit.TANK, date)
                    && nation.getUnits(MilitaryUnit.AIRCRAFT) == nation.getUnits(MilitaryUnit.AIRCRAFT, date)
            ) {
                continue;
            }

            double[] mmrFromTotal = new double[4];
            double[] mmrToTotal = new double[4];

            Map<Integer, JavaCity> previous = nation.getCityMap(false, false);
            Map<Integer, JavaCity> current = nation.getCityMap(true, false);
            int cities = current.size();

            if (previous.size() != current.size()) continue;

            for (Map.Entry<Integer, JavaCity> entry : previous.entrySet()) {
                int[] mmrFrom = entry.getValue().getMMRArray();
                int[] mmrTo = current.get(entry.getKey()).getMMRArray();

                for (int i = 0; i < 4; i++) mmrFromTotal[i] += mmrFrom[i] / (double) cities;
                for (int i = 0; i < 4; i++) mmrToTotal[i] += mmrTo[i] / (double) cities;
            }
            if ((mmrToTotal[0] >= mmrFromTotal[0] + 1 ||
                    mmrToTotal[1] >= mmrFromTotal[1] + 1 ||
                    mmrToTotal[2] >= mmrFromTotal[2] + 1)
                    &&
                    mmrToTotal[0] >= mmrFromTotal[0] &&
                    mmrToTotal[1] >= mmrFromTotal[1] &&
                    mmrToTotal[2] >= mmrFromTotal[2]
            ) {
                String title = "MMR:" + nation.getNation() + " | " + nation.getAllianceName();
                StringBuilder body = new StringBuilder();
                body.append(nation.getNationUrlMarkup(true)).append(" | ");
                body.append(nation.getAllianceUrlMarkup(true)).append(" | ");
                body.append(Rank.byId(nation.getPosition()));

                body.append("\nCities: " + nation.getCities());

                body.append("\n**Barrack**: " + MathMan.format(mmrFromTotal[0]) + "->" + MathMan.format(mmrToTotal[0]));
                body.append("\n**Factory**: " + MathMan.format(mmrFromTotal[1]) + "->" + MathMan.format(mmrToTotal[1]));
                body.append("\n**Hangars**: " + MathMan.format(mmrFromTotal[2]) + "->" + MathMan.format(mmrToTotal[2]));
                body.append("\n**Drydock**: " + MathMan.format(mmrFromTotal[3]) + "->" + MathMan.format(mmrToTotal[3]));

                if (nation.getOff() != 0 || nation.getDef() != 0) {
                    String warUrl = nation.getNationUrl() + "&display=war";
                    body.append("\n\nOff/Def wars: " + MarkupUtil.markdownUrl(nation.getOff() + "/" + nation.getDef(), warUrl));
                }

                AlertUtil.forEachChannel(f -> true, GuildDB.Key.ORBIS_OFFICER_MMR_CHANGE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                    @Override
                    public void accept(MessageChannel channel, GuildDB guildDB) {
                        DiscordUtil.createEmbedCommand(channel, title, body.toString());
                    }
                });
            }
        }
    }
}
