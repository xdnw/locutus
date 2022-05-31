package com.boydti.discord.commands.rankings;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.rankings.builder.SummedMapRankBuilder;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.NationList;
import com.boydti.discord.pnw.SimpleNationList;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.math.CIEDE2000;
import com.boydti.discord.util.sheet.SpreadSheet;
import com.boydti.discord.web.WebUtil;
import de.erichseifert.gral.data.DataTable;
import de.erichseifert.gral.graphics.Insets2D;
import de.erichseifert.gral.graphics.Location;
import de.erichseifert.gral.io.plots.DrawableWriter;
import de.erichseifert.gral.io.plots.DrawableWriterFactory;
import de.erichseifert.gral.plots.BarPlot;
import de.erichseifert.gral.plots.colors.ColorMapper;
import com.boydti.discord.apiv1.enums.city.building.Buildings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MilitaryRanking extends Command {
    public MilitaryRanking() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return "Get the militirization levels of top 80 alliances\n" +
                "Each bar is segmented into four sections, from bottom to top: (soldiers, tanks, planes, ships)\n" +
                "Each alliance is grouped by sphere and color coded";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        Map<Integer, List<DBNation>> byAA = Locutus.imp().getNationDB().getNationsByAlliance(true, true, true, true);

        Map<Integer, Color> sphereColors = new HashMap<>();
        Map<Integer, Double> sphereScore = new HashMap<>();
        Map<Integer, Map<Integer, NationList>> sphereAllianceMembers = new HashMap<>();

        Map<Integer, Alliance> aaCache = new HashMap<>();

        int topX = 80;
        for (Map.Entry<Integer, List<DBNation>> entry : byAA.entrySet()) {
            if (topX-- <= 0) break;
            Integer aaId = entry.getKey();
            Alliance alliance = aaCache.computeIfAbsent(aaId, f -> new Alliance(aaId));
            List<Alliance> sphere = alliance.getSphereRankedCached(aaCache);
            int sphereId = sphere.get(0).getAlliance_id();

            {
                List<DBNation> nations = alliance.getNations(true, 2880, true);
                SimpleNationList nationList = new SimpleNationList(nations);
                sphereAllianceMembers.computeIfAbsent(sphereId, f -> new HashMap<>()).put(alliance.getAlliance_id(), nationList);
            }

            if (!sphereScore.containsKey(sphereId)) {
                List<DBNation> nations = new ArrayList<>();
                for (Alliance other : sphere) {
                    nations.addAll(other.getNations(true, 2880, true));
                }
                SimpleNationList nationList = new SimpleNationList(nations);

                sphereScore.put(sphereId, nationList.getScore());
                if (sphere.size() > 1) {
                    sphereAllianceMembers.computeIfAbsent(sphereId, f -> new HashMap<>()).put(0, nationList);
                }
            }
        }

        SpreadSheet sheet = SpreadSheet.create("1eHBo8mwvME-C3su4UtMqhB3SDdigMi9SFfCmZ2ahM04");

        List<String> header = Arrays.asList(
                "alliance",
                "sphere_id",
                "score",
                "cities",
                "soldiers",
                "tanks",
                "planes",
                "ships",

                "soldier_%",
                "tank_%",
                "plane_%",
                "ship_%",

                "barracks",
                "factories",
                "hangars",
                "drydocks",

                "soldier_change",
                "factory_change",
                "plane_change",
                "ship_change"
        );

        sheet.setHeader(header);

        sphereScore = new SummedMapRankBuilder<>(sphereScore).sort().get();
        for (Map.Entry<Integer, Double> entry : sphereScore.entrySet()) {
            int sphereId = entry.getKey();

            Color color = sphereColors.computeIfAbsent(sphereId, f -> CIEDE2000.randomColor(sphereId, DiscordUtil.BACKGROUND_COLOR, sphereColors.values()));
            String colorStr = WebUtil.getColorHex(color);

            ArrayList<Map.Entry<Integer, NationList>> sphereAAs = new ArrayList<>(sphereAllianceMembers.get(sphereId).entrySet());
            Collections.sort(sphereAAs, new Comparator<Map.Entry<Integer, NationList>>() {
                @Override
                public int compare(Map.Entry<Integer, NationList> o1, Map.Entry<Integer, NationList> o2) {
                    return Double.compare(o2.getValue().getScore(), o1.getValue().getScore());
                }
            });
            for (Map.Entry<Integer, NationList> aaEntry : sphereAAs) {
                int aaId = aaEntry.getKey();
                NationList nations = aaEntry.getValue();

                DBNation total = nations.getTotal();

                ArrayList<Object> row = new ArrayList<>();
                if (aaId != 0) {
                    Alliance alliance = new Alliance(aaId);
                    row.add(MarkupUtil.sheetUrl(alliance.getName(), alliance.getUrl()));
                } else {
                    row.add("");
                }
                row.add(colorStr);
                row.add(nations.getScore());
                row.add(total.getCities());

                row.add(total.getSoldiers());
                row.add(total.getTanks());
                row.add(total.getAircraft());
                row.add(total.getShips());

                double soldierPct = 100 * (double) total.getSoldiers() / (Buildings.BARRACKS.max() * Buildings.BARRACKS.cap() * total.getCities());
                double tankPct = 100 * (double) total.getTanks() / (Buildings.FACTORY.max() * Buildings.FACTORY.cap() * total.getCities());
                double airPct = 100 * (double) total.getAircraft() / (Buildings.HANGAR.max() * Buildings.HANGAR.cap() * total.getCities());
                double navyPct = 100 * (double) total.getShips() / (Buildings.DRYDOCK.max() * Buildings.DRYDOCK.cap() * total.getCities());

                row.add(soldierPct);
                row.add(tankPct);
                row.add(airPct);
                row.add( navyPct);

                double[] mmr = nations.getAverageMMR(false);
                row.add(mmr[0] * 100 / Buildings.BARRACKS.cap());
                row.add(mmr[1] * 100 / Buildings.FACTORY.cap());
                row.add(mmr[2] * 100 / Buildings.HANGAR.cap());
                row.add(mmr[3] * 100 / Buildings.DRYDOCK.cap());

                double[] buy = nations.getMilitaryBuyPct(false);
                row.add(buy[0]);
                row.add(buy[1]);
                row.add(buy[2]);
                row.add(buy[3]);

                for (int i = 0; i < row.size(); i++) {
                    Object val = row.get(i);
                    if (val instanceof Number && !Double.isFinite((double) ((Number) val).doubleValue())) {
                        row.set(i, 0);
                    }
                }

                sheet.addRow(row);
            }
        }

        {
            List<List<Object>> values = sheet.getValues();

            DataTable data = new DataTable(Double.class, Double.class, String.class);
            Function<Number, Color> colorFunction = f -> Color.decode((String) values.get((f.intValue() / 4) + 1).get(1));

            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                String[] allianceSplit = ((String) row.get(0)).split("\"");
                String alliance = allianceSplit.length > 2 ? allianceSplit[allianceSplit.length - 2] : "bloc average";
                Color color = Color.decode((String) row.get(1));

                double total = 0;
                for (int j = 8; j < 12; j++) {
                    double val = ((Number) row.get(j)).doubleValue() / 4d;
                    total += val;
                }
                for (int j = 11; j >= 8; j--) {
                    double val = ((Number) row.get(j)).doubleValue() / 4d;
                    data.add(i + 0d, total, j == 8 ? alliance : "");
                    total -= val;
                }
            }

            // Create new bar plot
            BarPlot plot = new BarPlot(data);

            // Format plot
            plot.setInsets(new Insets2D.Double(40.0, 40.0, 40.0, 0.0));
            plot.setBarWidth(0.9);
            plot.setBackground(Color.WHITE);

            Color COLOR1 = Color.DARK_GRAY;
            // Format bars
            BarPlot.BarRenderer pointRenderer = (BarPlot.BarRenderer) plot.getPointRenderers(data).get(0);
            pointRenderer.setColor(new ColorMapper() {
                @Override
                public Paint get(Number number) {
                    return colorFunction.apply(number);
                }

                @Override
                public Mode getMode() {
                    return null;
                }
            });
            pointRenderer.setBorderStroke(new BasicStroke(1f));
            pointRenderer.setBorderColor(Color.LIGHT_GRAY);
            pointRenderer.setValueVisible(true);
            pointRenderer.setValueColumn(2);
            pointRenderer.setValueLocation(Location.NORTH);
            pointRenderer.setValueRotation(90);
            pointRenderer.setValueColor(new ColorMapper() {
                @Override
                public Paint get(Number number) {
                    return CIEDE2000.findComplement(colorFunction.apply(number));
                }

                @Override
                public Mode getMode() {
                    return null;
                }
            });
            pointRenderer.setValueFont(Font.decode(null).deriveFont(12.0f));

            DrawableWriter writer = DrawableWriterFactory.getInstance().get("image/png");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(plot, baos, 1400, 600);
            RateLimitUtil.queue(event.getChannel().sendFile(baos.toByteArray(), ("img.png")));
        }

        sheet.clear("A:Z");
        sheet.set(0, 0);

        String msg = "> Each bar is segmented into four sections, from bottom to top: (soldiers, tanks, planes, ships)\n" +
                "> Each alliance is grouped by sphere and color coded";
        return "<" + sheet.getURL() + ">\n" + msg;
    }
}