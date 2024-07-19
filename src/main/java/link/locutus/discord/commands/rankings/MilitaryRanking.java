package link.locutus.discord.commands.rankings;

import de.erichseifert.gral.data.DataTable;
import de.erichseifert.gral.graphics.Insets2D;
import de.erichseifert.gral.graphics.Location;
import de.erichseifert.gral.io.plots.DrawableWriter;
import de.erichseifert.gral.io.plots.DrawableWriterFactory;
import de.erichseifert.gral.plots.BarPlot;
import de.erichseifert.gral.plots.colors.ColorMapper;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.CIEDE2000;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.*;
import java.util.function.Function;

public class MilitaryRanking extends Command {
    public MilitaryRanking() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.alliance.stats.militarization.cmd);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return """
                Get the militirization levels of top 80 alliances.
                Each bar is segmented into four sections, from bottom to top: (soldiers, tanks, planes, ships)
                Each alliance is grouped by sphere and color coded.""";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        Map<Integer, List<DBNation>> byAA = Locutus.imp().getNationDB().getNationsByAlliance(true, true, true, true, true);

        Map<Integer, Color> sphereColors = new HashMap<>();
        Map<Integer, Double> sphereScore = new HashMap<>();
        Map<Integer, Map<Integer, NationList>> sphereAllianceMembers = new HashMap<>();

        Map<Integer, DBAlliance> aaCache = new HashMap<>();

        int topX = 80;
        for (Map.Entry<Integer, List<DBNation>> entry : byAA.entrySet()) {
            if (topX-- <= 0) break;
            Integer aaId = entry.getKey();
            DBAlliance alliance = aaCache.computeIfAbsent(aaId, f -> DBAlliance.getOrCreate(aaId));
            List<DBAlliance> sphere = alliance.getSphereRankedCached(aaCache);
            int sphereId = sphere.get(0).getAlliance_id();

            {
                Set<DBNation> nations = alliance.getNations(true, 2880, true);
                SimpleNationList nationList = new SimpleNationList(nations);
                sphereAllianceMembers.computeIfAbsent(sphereId, f -> new HashMap<>()).put(alliance.getAlliance_id(), nationList);
            }

            if (!sphereScore.containsKey(sphereId)) {
                List<DBNation> nations = new ArrayList<>();
                for (DBAlliance other : sphere) {
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
            sphereAAs.sort((o1, o2) -> Double.compare(o2.getValue().getScore(), o1.getValue().getScore()));
            for (Map.Entry<Integer, NationList> aaEntry : sphereAAs) {
                int aaId = aaEntry.getKey();
                NationList nations = aaEntry.getValue();

                DBNation total = nations.getTotal();

                ArrayList<Object> row = new ArrayList<>();
                if (aaId != 0) {
                    DBAlliance alliance = DBAlliance.getOrCreate(aaId);
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

                double soldierPct = 100 * (double) total.getSoldiers() / (Buildings.BARRACKS.getUnitCap() * Buildings.BARRACKS.cap(total::hasProject) * total.getCities());
                double tankPct = 100 * (double) total.getTanks() / (Buildings.FACTORY.getUnitCap() * Buildings.FACTORY.cap(total::hasProject) * total.getCities());
                double airPct = 100 * (double) total.getAircraft() / (Buildings.HANGAR.getUnitCap() * Buildings.HANGAR.cap(total::hasProject) * total.getCities());
                double navyPct = 100 * (double) total.getShips() / (Buildings.DRYDOCK.getUnitCap() * Buildings.DRYDOCK.cap(total::hasProject) * total.getCities());

                row.add(soldierPct);
                row.add(tankPct);
                row.add(airPct);
                row.add(navyPct);

                double[] mmr = nations.getAverageMMR(false);
                row.add(mmr[0] * 100 / Buildings.BARRACKS.cap(total::hasProject));
                row.add(mmr[1] * 100 / Buildings.FACTORY.cap(total::hasProject));
                row.add(mmr[2] * 100 / Buildings.HANGAR.cap(total::hasProject));
                row.add(mmr[3] * 100 / Buildings.DRYDOCK.cap(total::hasProject));

                double[] buy = nations.getMilitaryBuyPct(false);
                row.add(buy[0]);
                row.add(buy[1]);
                row.add(buy[2]);
                row.add(buy[3]);

                for (int i = 0; i < row.size(); i++) {
                    Object val = row.get(i);
                    if (val instanceof Number && !Double.isFinite(((Number) val).doubleValue())) {
                        row.set(i, 0);
                    }
                }

                sheet.addRow(row);
            }
        }

        IMessageBuilder msg = channel.create();
        {
            List<List<Object>> values = sheet.getCachedValues(null);

            DataTable data = new DataTable(Double.class, Double.class, String.class);
            Function<Number, Color> colorFunction = f -> Color.decode((String) values.get((f.intValue() / 4) + 1).get(1));

            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                String[] allianceSplit = ((String) row.get(0)).split("\"");
                String alliance = allianceSplit.length > 2 ? allianceSplit[allianceSplit.length - 2] : "bloc average.";
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
            msg.image("img.png", baos.toByteArray());
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        msg.append("> Each bar is segmented into four sections, from bottom to top: (soldiers, tanks, planes, ships)\n" +
                "> Each alliance is grouped by sphere and color coded");

        sheet.attach(msg, "alliance_ranking").send();
        return null;
    }
}