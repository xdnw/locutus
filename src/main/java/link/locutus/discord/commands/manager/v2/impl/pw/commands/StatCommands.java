package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.politicsandwar.graphql.model.BBGame;
import com.ptsmods.mysqlw.query.QueryCondition;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationMetricDouble;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.rankings.builder.GroupedRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.commands.rankings.table.TimeDualNumericTable;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.db.BaseballDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AllianceMetric;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarParser;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.Alliance;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.trade.TradeManager;
import de.erichseifert.gral.data.DataTable;
import de.erichseifert.gral.graphics.Insets2D;
import de.erichseifert.gral.graphics.Location;
import de.erichseifert.gral.io.plots.DrawableWriter;
import de.erichseifert.gral.io.plots.DrawableWriterFactory;
import de.erichseifert.gral.plots.BarPlot;
import de.erichseifert.gral.plots.colors.ColorMapper;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.SAllianceContainer;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.ClassUtils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StatCommands {
    @Command(desc = "List resources in each continent")
    public String continent(TradeManager manager) {
        StringBuilder response = new StringBuilder();
        for (Continent continent : Continent.values) {
            List<ResourceType> types = Arrays.asList(ResourceType.values).stream().filter(f -> {
                Building build = f.getBuilding();
                return build != null && build.canBuild(continent);
            }).collect(Collectors.toList());
            response.append(continent.name() + ": (rads=" + MathMan.format(continent.getRadIndex()) + ")\n");
            response.append(StringMan.join(types, "\n - ") + "\n");
        }
        return response.toString();
    }

    @Command(desc = "List the radiation in each continent")
    public String radiation(TradeManager manager) {
        double global = 0;
        Map<Continent, Double> radsByCont = new LinkedHashMap<>();
        for (Continent continent : Continent.values) {
            double rads = manager.getGlobalRadiation(continent);
            global += rads;
            radsByCont.put(continent, rads);
        }
        global /= 5;

        StringBuilder result = new StringBuilder();
        result.append("Global: " + MathMan.format(global)).append("rads\n");
        for (Map.Entry<Continent, Double> entry : radsByCont.entrySet()) {
            Continent continent = entry.getKey();
            Double local = entry.getValue();

            double modifier = continent.getSeasonModifier();
            modifier *= (1 - (local + global) / 1000d);

            result.append(" - " + continent + ": " + MathMan.format(local))
                    .append("rads. (" + MathMan.format(modifier * 100) + "% food production)")
            .append("\n");
        }
        return result.toString();
    }

    @Command(desc = "View an alliances stats for counters")
    @RolePermission(Roles.MEMBER)
    @WhitelistPermission
    public String counterStats(@Me MessageChannel channel, Alliance alliance) {
        List<Map.Entry<DBWar, CounterStat>> counters = Locutus.imp().getWarDb().getCounters(Collections.singleton(alliance.getAlliance_id()));

        if (counters.isEmpty()) return "No data (to include treatied alliances, append `-a`";

        int[] uncontested = new int[2];
        int[] countered = new int[2];
        int[] counter = new int[2];
        for (Map.Entry<DBWar, CounterStat> entry : counters) {
            CounterStat stat = entry.getValue();
            DBWar war = entry.getKey();
            switch (stat.type) {
                case ESCALATION:
                case IS_COUNTER:
                    countered[stat.isActive ? 1 : 0]++;
                    continue;
                case UNCONTESTED:
                    if (war.status == WarStatus.ATTACKER_VICTORY) {
                        uncontested[stat.isActive ? 1 : 0]++;
                    } else {
                        counter[stat.isActive ? 1 : 0]++;
                    }
                    break;
                case GETS_COUNTERED:
                    counter[stat.isActive ? 1 : 0]++;
                    break;
            }
        }

        int totalActive = counter[1] + uncontested[1];
        int totalInactive = counter[0] + uncontested[0];

        double chanceActive = ((double) counter[1] + 1) / (totalActive + 1);
        double chanceInactive = ((double) counter[0] + 1) / (totalInactive + 1);

        if (!Double.isFinite(chanceActive)) chanceActive = 0.5;
        if (!Double.isFinite(chanceInactive)) chanceInactive = 0.5;

        String title = "% of wars that are countered (" + alliance.getName() + ")";
        StringBuilder response = new StringBuilder();
        response.append(MathMan.format(chanceActive * 100) + "% for actives (" + totalActive + " wars)").append('\n');
        response.append(MathMan.format(chanceInactive * 100) + "% for inactives (" + totalInactive + " wars)");

        DiscordUtil.createEmbedCommand(channel, title, response.toString());
        return null;
    }

    @Command
    public String metricTierGraph(@Me MessageChannel channel, NationMetricDouble metric, Set<DBNation> coalition1, Set<DBNation> coalition2, @Switch('i') boolean includeInactives, @Switch('i') boolean includeApplicants, @Switch('t') boolean total) throws IOException {
        coalition1.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        coalition2.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));

        if (coalition1.isEmpty() || coalition2.isEmpty()) throw new IllegalArgumentException("No nations provided");
        if (coalition1.size() < 3 || coalition2.size() < 3) return "Coalitions are too small to compare";

        Map<Integer, List<DBNation>> coalition1ByCity = new HashMap<>();
        Map<Integer, List<DBNation>> coalition2ByCity = new HashMap<>();

        for (DBNation n : coalition1) coalition1ByCity.computeIfAbsent(n.getCities(), f -> new LinkedList<>()).add(n);
        for (DBNation n : coalition2) coalition2ByCity.computeIfAbsent(n.getCities(), f -> new LinkedList<>()).add(n);

        int min = Math.min(Collections.min(coalition1ByCity.keySet()), Collections.min(coalition2ByCity.keySet()));
        int max = Math.max(Collections.max(coalition1ByCity.keySet()), Collections.max(coalition2ByCity.keySet()));

        DataTable data = new DataTable(Double.class, Double.class, String.class);

        for (int cities = min; cities <= max; cities++) {
            List<DBNation> natAtCity1 = coalition1ByCity.getOrDefault(cities, Collections.emptyList());
            List<DBNation> natAtCity2 = coalition2ByCity.getOrDefault(cities, Collections.emptyList());
            List<DBNation>[] coalitions = new List[]{natAtCity1, natAtCity2};

            for (int j = 0; j < coalitions.length; j++) {
                List<DBNation> coalition = coalitions[j];
                SimpleNationList natCityList = new SimpleNationList(coalition);

                String name = j == 0 ? "" + cities : "";

                double valueTotal = 0;
                int count = 0;
                Collection<DBNation> nations = natCityList.getNations();
                for (DBNation nation : nations) {
                    if (nation.hasUnsetMil()) continue;
                    count++;
                    valueTotal += metric.apply(nation);
                }
                if (count > 1 && !total) {
                    valueTotal /= count;
                }

                data.add(cities + (j * 0.5d), valueTotal, name);
            }
        }

        int segments = 1;
        // Create new bar plot
        BarPlot plot = new BarPlot(data);
        plot.getTitle().setText((total ? "Total" : "Average") + " " + metric.getName() + " by city count");

        // Format plot
        plot.setInsets(new Insets2D.Double(20.0, 100.0, 40.0, 0.0));
        plot.setBarWidth(0.5);
        plot.setBackground(Color.WHITE);

        // Format bars
        BarPlot.BarRenderer pointRenderer = (BarPlot.BarRenderer) plot.getPointRenderers(data).get(0);
        pointRenderer.setColor(new ColorMapper() {
            @Override
            public Paint get(Number number) {
                int column = (number.intValue() / segments);
                Color color = (column % 2) == 0 ? Color.RED : Color.BLUE;
                return color;
            }

            @Override
            public ColorMapper.Mode getMode() {
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
                return Color.BLACK;
            }

            @Override
            public ColorMapper.Mode getMode() {
                return null;
            }
        });
        pointRenderer.setValueFont(Font.decode(null).deriveFont(12.0f));

        DrawableWriter writer = DrawableWriterFactory.getInstance().get("image/png");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer.write(plot, baos, 1400, 600);
        RateLimitUtil.queue(channel.sendFile(baos.toByteArray(), ("img.png")));

        plot.setInsets(new Insets2D.Double(20.0, 100.0, 40.0, 0.0));
        plot.getTitle().setText("MMR by city count");

        StringBuilder response = new StringBuilder("> Each coalition is grouped by city count and color coded.\n" +
                "> **RED** = Coalition 1\n" +
                "> **BLUE** = Coalition 2\n");

        return response.toString();
    }

    @Command(desc = "Generate a graph of nation strength by score between two coalitions", aliases = {"strengthTierGraph"})
    public String strengthTierGraph(@Me GuildDB db, @Me MessageChannel channel, Set<DBNation> coalition1, Set<DBNation> coalition2, @Switch('i') boolean includeInactives, @Switch('a') boolean includeApplicants) throws IOException {
        Set<DBNation> allNations = new HashSet<>();
        coalition1.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        coalition2.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        allNations.addAll(coalition1);
        allNations.addAll(coalition2);
        if (coalition1.isEmpty() || coalition2.isEmpty()) throw new IllegalArgumentException("No nations provided");

        int maxScore = 0;
        int minScore = Integer.MAX_VALUE;
        for (DBNation nation : allNations) {
            maxScore = (int) Math.max(maxScore, nation.getScore());
            minScore = (int) Math.min(minScore, nation.getScore());
        }
        double[] coal1Str = new double[(int) (maxScore * 1.75)];
        double[] coal2Str = new double[(int) (maxScore * 1.75)];

        double[] coal1StrSpread = new double[coal1Str.length];
        double[] coal2StrSpread = new double[coal2Str.length];

        // min = x * 0.75;
        // max = x * 1.25
        // max = (min / 0.6)

        for (DBNation nation : coalition1) {
            coal1Str[(int) (nation.getScore() * 0.75)] += nation.getStrength();
        }
        for (DBNation nation : coalition2) {
            coal2Str[(int) (nation.getScore() * 0.75)] += nation.getStrength();
        }
        for (int min = 10; min < coal1Str.length; min++) {
            double val = coal1Str[min];
            if (val == 0) continue;
            int max = (int) (min / 0.6);

            for (int i = min; i <= max; i++) {
                double shaped = val - 0.4 * val * ((double) (i - min) / (max - min));
                coal1StrSpread[i] += shaped;
            }
        }
        for (int min = 10; min < coal2Str.length; min++) {
            double val = coal2Str[min];
            if (val == 0) continue;
            int max = (int) (min / 0.6);

            for (int i = min; i <= max; i++) {
                double shaped = val - 0.4 * val * ((double) (i - min) / (max - min));
                coal2StrSpread[i] += shaped;
            }
        }

        double[] buffer = new double[2];
        TimeDualNumericTable<Void> table = new TimeDualNumericTable<Void>("Effective military strength by score range", "score", "strength", "coalition 1", "coalition 2") {
            @Override
            public void add(long score, Void ignore) {
                add(score, coal1StrSpread[(int) score], coal2StrSpread[(int) score]);
            }
        };
        for (int score = (int) Math.max(10, minScore * 0.75 - 10); score < maxScore * 1.25 + 10; score++) {
            table.add(score, (Void) null);
        }
        table.write(channel);
        return null;
    }

    @Command(desc = "Generate a graph of spy counts by city count between two coalitions (filters out >2d inactives)")
    public String spyTierGraph(@Me GuildDB db, @Me MessageChannel channel, Set<DBNation> coalition1, Set<DBNation> coalition2, @Switch('t') boolean total) throws IOException {
        Set<DBNation> allNations = new HashSet<>();
        coalition1.removeIf(f -> f.getVm_turns() != 0 || (f.getPosition() <= 1) || (f.getActive_m() > 2880));
        coalition2.removeIf(f -> f.getVm_turns() != 0 || (f.getPosition() <= 1) || (f.getActive_m() > 2880));
        allNations.addAll(coalition1);
        allNations.addAll(coalition2);
        if (coalition1.isEmpty() || coalition2.isEmpty()) throw new IllegalArgumentException("No nations provided");
        int min = 0;
        int max = 0;
        for (DBNation nation : allNations) max = Math.max(nation.getCities(), max);
        max++;

        Set<DBNation>[] coalitions = new Set[]{coalition1, coalition2};
        int[][] counts = new int[coalitions.length][];
        for (int i = 0; i < coalitions.length; i++) {
            Set<DBNation> coalition = coalitions[i];
            int[] cities = new int[max + 1];
            int[] spies = new int[max + 1];
            counts[i] = spies;
            int k = 0;
            for (DBNation nation : coalition) {
                cities[nation.getCities()]++;
                spies[nation.getCities()] += nation.updateSpies(false, false);
            }
            if (!total) {
                for (int j = 0; j < cities.length; j++) {
                    if (cities[j] > 1) spies[j] /= cities[j];
                }
            }
        }

        String title = (total ? "Total" : "Average") + " spies by city count";
        TimeDualNumericTable<Void> table = new TimeDualNumericTable<Void>(title, "cities", "spies", "coalition 1", "coalition 2") {
            @Override
            public void add(long cities, Void ignore) {
                add(cities, counts[0][(int) cities], counts[1][(int) cities]);
            }
        };
        for (int cities = min; cities <= max; cities++) {
            table.add(cities, (Void) null);
        }
        table.write(channel);
        return null;
    }

    @Command(desc = "Generate a graph of num nation by score between two coalitions", aliases = {"scoreTierGraph", "scoreTierSheet"})
    public String scoreTierGraph(@Me GuildDB db, @Me MessageChannel channel, Set<DBNation> coalition1, Set<DBNation> coalition2, @Switch('i') boolean includeInactives, @Switch('a') boolean includeApplicants) throws IOException {
        Set<DBNation> allNations = new HashSet<>();
        coalition1.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        coalition2.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        allNations.addAll(coalition1);
        allNations.addAll(coalition2);

        if (coalition1.isEmpty() || coalition2.isEmpty()) throw new IllegalArgumentException("No nations provided");

        int maxScore = 0;
        int minScore = Integer.MAX_VALUE;
        for (DBNation nation : allNations) {
            maxScore = (int) Math.max(maxScore, nation.getScore());
            minScore = (int) Math.min(minScore, nation.getScore());
        }
        double[] coal1Str = new double[(int) (maxScore * 1.75)];
        double[] coal2Str = new double[(int) (maxScore * 1.75)];

        double[] coal1StrSpread = new double[coal1Str.length];
        double[] coal2StrSpread = new double[coal2Str.length];

        for (DBNation nation : coalition1) {
            coal1Str[(int) (nation.getScore() * 0.75)] += 1;
        }
        for (DBNation nation : coalition2) {
            coal2Str[(int) (nation.getScore() * 0.75)] += 1;
        }
        for (int min = 10; min < coal1Str.length; min++) {
            double val = coal1Str[min];
            if (val == 0) continue;
            int max = Math.min(coal1StrSpread.length, (int) (1.75 * (min / 0.75)));

            for (int i = min; i < max; i++) {
                coal1StrSpread[i] += val;
            }
        }
        for (int min = 10; min < coal2Str.length; min++) {
            double val = coal2Str[min];
            if (val == 0) continue;
            int max = Math.min(coal2StrSpread.length, (int) (1.75 * (min / 0.75)));

            for (int i = min; i < max; i++) {
                coal2StrSpread[i] += val;
            }
        }

        double[] buffer = new double[2];
        TimeDualNumericTable<Void> table = new TimeDualNumericTable<Void>("Nations by score range", "score", "nations", "coalition 1", "coalition 2") {
            @Override
            public void add(long score, Void ignore) {
                add(score, coal1StrSpread[(int) score], coal2StrSpread[(int) score]);
            }
        };
        for (int score = (int) Math.max(10, minScore * 0.75 - 10); score < maxScore * 1.25 + 10; score++) {
            table.add(score, (Void) null);
        }
        table.write(channel);
        return null;
    }

    @Command(desc = "Rank of nations by # of challenge baseball games")
    public void baseballRanking(BaseballDB db, @Me MessageChannel channel, @Timestamp long date, @Switch('f') boolean uploadFile, @Switch('a') boolean byAlliance) {
        List<BBGame> games = db.getBaseballGames(f -> f.where(QueryCondition.greater("date", date)));

        Map<Integer, Integer> mostGames = new HashMap<>();
        for (BBGame game : games) {
            if (byAlliance) {
                DBNation home = DBNation.byId(game.getHome_nation_id());
                DBNation away = DBNation.byId(game.getAway_nation_id());
                if (home != null) mostGames.merge(home.getAlliance_id(), 1, Integer::sum);
                if (away != null) mostGames.merge(away.getAlliance_id(), 1, Integer::sum);
            } else {
                mostGames.merge(game.getHome_nation_id(), 1, Integer::sum);
                mostGames.merge(game.getAway_nation_id(), 1, Integer::sum);
            }
        }

        String title = "# BB Games (" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - date) + ")";
        RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostGames).sort().nameKeys(f -> PnwUtil.getName(f, byAlliance));
        ranks.build(null, channel, null, title);
        if (uploadFile && ranks.get().size() > 25) {
            DiscordUtil.upload(channel, title, ranks.toString());
        }
    }

    @Command(desc = "Rank of nations by # of challenge baseball games")
    public void baseballChallengeRanking(BaseballDB db, @Me MessageChannel channel, @Switch('f') boolean uploadFile, @Switch('a') boolean byAlliance) {
        List<BBGame> games = db.getBaseballGames(f -> f.where(QueryCondition.greater("wager", 0)));

        Map<Integer, Integer> mostGames = new HashMap<>();
        for (BBGame game : games) {
            if (byAlliance) {

                DBNation home = DBNation.byId(game.getHome_nation_id());
                DBNation away = DBNation.byId(game.getAway_nation_id());
                if (home != null) mostGames.merge(home.getAlliance_id(), 1, Integer::sum);
                if (away != null) mostGames.merge(away.getAlliance_id(), 1, Integer::sum);
            } else {
                mostGames.merge(game.getHome_nation_id(), 1, Integer::sum);
                mostGames.merge(game.getAway_nation_id(), 1, Integer::sum);
            }
        }

        String title = "# Challenge BB Games";
        RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostGames).sort().nameKeys(f -> PnwUtil.getName(f, byAlliance));
        ranks.build(null, channel, null, title);
        if (uploadFile && ranks.get().size() > 25) {
            DiscordUtil.upload(channel, title, ranks.toString());
        }
    }

    @Command(desc = "List the baseball wager inflows for a nation id")
    public String baseBallChallengeInflow(BaseballDB db, @Me MessageChannel channel, int nationId, @Default("timestamp:0") @Timestamp long dateSince) {
        List<BBGame> games = db.getBaseballGames(new Consumer<SelectBuilder>() {
            @Override
            public void accept(SelectBuilder f) {
                f.where(QueryCondition.greater("wager", 0)
                        .and(QueryCondition.equals("home_nation_id", nationId).or(QueryCondition.equals("away_nation_id", nationId)))
                        .and(QueryCondition.greater("date", dateSince))
                );
            }
        });

        if (games.isEmpty()) return "No games found";

        {
            String title = "# Wagers with " + PnwUtil.getName(nationId, false);
            Map<Integer, Integer> mostWageredGames = new HashMap<>();
            for (BBGame game : games) {
                mostWageredGames.merge(game.getHome_nation_id(), 1, Integer::sum);
                mostWageredGames.merge(game.getAway_nation_id(), 1, Integer::sum);
            }
            RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostWageredGames).sort().nameKeys(f -> PnwUtil.getName(f, false));
            ranks.build(null, channel, null, title);
        }
        {
            String title = "$ Wagered with " + PnwUtil.getName(nationId, false);
            Map<Integer, Long> mostWageredWinnings = new HashMap<>();
            for (BBGame game : games) {
                int otherId = game.getAway_nation_id() == nationId ? game.getHome_nation_id() : game.getAway_nation_id();
                mostWageredWinnings.merge(otherId, game.getWager().longValue(), Long::sum);
            }
            RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostWageredWinnings).sort().nameKeys(f -> PnwUtil.getName(f, false));
            ranks.build(null, channel, null, title);
        }
        return null;
    }

    @Command(desc = "Rank of nations by challenge baseball game earnings")
    public void baseballEarningsRanking(BaseballDB db, @Me MessageChannel channel, @Timestamp long date, @Switch('f') boolean uploadFile, @Switch('a') boolean byAlliance) {
        List<BBGame> games = db.getBaseballGames(f -> f.where(QueryCondition.greater("date", date)));

        Map<Integer, Long> mostWageredWinnings = new HashMap<>();
        for (BBGame game : games) {
            int id;
            if (game.getHome_score() > game.getAway_score()) {
                id = game.getHome_nation_id();

            } else if (game.getAway_score() > game.getHome_score()) {
                id = game.getAway_nation_id();
            } else continue;
            if (byAlliance) {
                DBNation nation = DBNation.byId(id);
                if (nation == null) continue;
                id = nation.getAlliance_id();
            }
            mostWageredWinnings.merge(id, game.getSpoils().longValue(), Long::sum);
        }

        String title = "BB Earnings $ (" + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - date) + ")";
        RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostWageredWinnings).sort().nameKeys(f -> PnwUtil.getName(f, byAlliance));
        ranks.build(null, channel, null, title);
        if (uploadFile && ranks.get().size() > 25) {
            DiscordUtil.upload(channel, title, ranks.toString());
        }
    }

    @Command(desc = "Rank of nations by challenge baseball game earnings")
    public void baseballChallengeEarningsRanking(BaseballDB db, @Me MessageChannel channel, @Switch('f') boolean uploadFile, @Switch('a') boolean byAlliance) {
        List<BBGame> games = db.getBaseballGames(f -> f.where(QueryCondition.greater("wager", 0)));

        Map<Integer, Long> mostWageredWinnings = new HashMap<>();
        for (BBGame game : games) {
            int id;
            if (game.getHome_score() > game.getAway_score()) {
                id = game.getHome_nation_id();

            } else if (game.getAway_score() > game.getHome_score()) {
                id = game.getAway_nation_id();
            } else continue;
            if (byAlliance) {
                DBNation nation = DBNation.byId(id);
                if (nation == null) continue;
                id = nation.getAlliance_id();
            }
            mostWageredWinnings.merge(id, game.getWager().longValue(), Long::sum);
        }

        String title = "BB Challenge Earnings $";
        RankBuilder<String> ranks = new SummedMapRankBuilder<>(mostWageredWinnings).sort().nameKeys(f -> PnwUtil.getName(f, byAlliance));
        ranks.build(null, channel, null, title);
        if (uploadFile && ranks.get().size() > 25) {
            DiscordUtil.upload(channel, title, ranks.toString());
        }
    }

    @Command(desc = "Generate ranking of war status by AA")
    public void warStatusRankingByAA(@Me GuildDB db, @Me MessageChannel channel, Set<DBNation> attackers, Set<DBNation> defenders, @Timestamp long time) {
        warStatusRankingBy(true, db, channel, attackers, defenders, time);
    }

    @Command(desc = "Generate ranking of war status by Nation")
    public void warStatusRankingByNation(@Me GuildDB db, @Me MessageChannel channel, Set<DBNation> attackers, Set<DBNation> defenders, @Timestamp long time) {
        warStatusRankingBy(false, db, channel, attackers, defenders, time);
    }

    public void warStatusRankingBy(boolean isAA, @Me GuildDB db, @Me MessageChannel channel, Set<DBNation> attackers, Set<DBNation> defenders, @Timestamp long time) {
        BiFunction<Boolean, DBWar, Integer> getId;
        if (isAA) getId = (primary, war) -> primary ? war.attacker_aa : war.defender_aa;
        else getId = (primary, war) -> primary ? war.attacker_id : war.defender_id;

        WarParser parser = WarParser.ofAANatobj(null, attackers, null, defenders, time, Long.MAX_VALUE);

        Map<Integer, Integer> victoryByEntity = new HashMap<>();
        Map<Integer, Integer> expireByEntity = new HashMap<>();

        for (Map.Entry<Integer, DBWar> entry : parser.getWars().entrySet()) {
            DBWar war = entry.getValue();
            boolean primary = parser.getIsPrimary().apply(war);

            if (war.status == WarStatus.DEFENDER_VICTORY) primary = !primary;
            int id = getId.apply(primary, war);

            if (war.status == WarStatus.ATTACKER_VICTORY || war.status == WarStatus.DEFENDER_VICTORY) {
                victoryByEntity.put(id, victoryByEntity.getOrDefault(id, 0) + 1);
            } else if (war.status == WarStatus.EXPIRED) {
                expireByEntity.put(id, expireByEntity.getOrDefault(id, 0) + 1);
            }
        }

        if (!victoryByEntity.isEmpty()) new SummedMapRankBuilder<>(victoryByEntity).sort().nameKeys(f -> PnwUtil.getName(f, isAA)).build(null, channel, null, "Victories");
        if (!expireByEntity.isEmpty()) new SummedMapRankBuilder<>(expireByEntity).sort().nameKeys(f -> PnwUtil.getName(f, isAA)).build(null, channel, null, "Expiries");
    }

    @Command(desc = "Generate a graph of num nation by score between two coalitions")
    public String mmrTierGraph(@Me GuildDB db, @Me MessageChannel channel, Set<DBNation> coalition1, Set<DBNation> coalition2, @Switch('i') boolean includeInactives, @Switch('i') boolean includeApplicants, @Switch('s') SpreadSheet sheet) throws IOException, GeneralSecurityException {
        coalition1.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        coalition2.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));

        if (coalition1.isEmpty() || coalition2.isEmpty()) throw new IllegalArgumentException("No nations provided");
        if (coalition1.size() < 3 || coalition2.size() < 3) return "Coalitions are too small to compare";

        Map<Integer, List<DBNation>> coalition1ByCity = new HashMap<>();
        Map<Integer, List<DBNation>> coalition2ByCity = new HashMap<>();

        for (DBNation n : coalition1) coalition1ByCity.computeIfAbsent(n.getCities(), f -> new LinkedList<>()).add(n);
        for (DBNation n : coalition2) coalition2ByCity.computeIfAbsent(n.getCities(), f -> new LinkedList<>()).add(n);

        int min = Math.min(Collections.min(coalition1ByCity.keySet()), Collections.min(coalition2ByCity.keySet()));
        int max = Math.max(Collections.max(coalition1ByCity.keySet()), Collections.max(coalition2ByCity.keySet()));

        List<Object> headers = new ArrayList<>(Arrays.asList("cities", "coalition", "num_nations", "soldier%", "tank%", "air%", "ship%", "avg%"));
        if (sheet != null) sheet.setHeader(headers);

        {
            DataTable data = new DataTable(Double.class, Double.class, String.class);

            for (int cities = min; cities <= max; cities++) {
                List<DBNation> natAtCity1 = coalition1ByCity.getOrDefault(cities, Collections.emptyList());
                List<DBNation> natAtCity2 = coalition2ByCity.getOrDefault(cities, Collections.emptyList());
                List<DBNation>[] coalitions = new List[]{natAtCity1, natAtCity2};

                for (int j = 0; j < coalitions.length; j++) {
                    List<DBNation> coalition = coalitions[j];
                    SimpleNationList natCityList = new SimpleNationList(coalition);
                    DBNation nation = natCityList.getNations().isEmpty() ? null : natCityList.getAverage();
                    String name = j == 0 ? "" + cities : "";
                    if (nation == null) {
                        nation = new DBNation();
                        nation.setSoldiers(0);
                        nation.setTanks(0);
                        nation.setAircraft(0);
                        nation.setShips(0);
                    }
                    double total = 0;

                    MilitaryUnit[] units = new MilitaryUnit[]{MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP};
                    double[] pcts = new double[units.length];

                    for (int i = 0; i < units.length; i++) {
                        MilitaryUnit unit = units[i];
                        double amt = nation.getUnits(unit);
                        double cap = unit.getBuilding().cap() * unit.getBuilding().max() * cities;
                        double pct = amt / cap;
                        pcts[i] = pct;

                        total += pct * 25;
                    }
                    for (int i = units.length - 1; i >= 0; i--) {
                        data.add(cities + (j * 0.5d), total, i == 3 ? name : "");
                        total -= pcts[i] * 25;
                    }

                    if (sheet != null) {
                        headers.set(0, cities);
                        headers.set(1, j + 1);
                        headers.set(2, coalition.size());
                        headers.set(3, pcts[0]);
                        headers.set(4, pcts[1]);
                        headers.set(5, pcts[2]);
                        headers.set(6, pcts[3]);
                        headers.set(7, total / 100d);
                        sheet.addRow(headers);
                    }
                }
            }

            // Create new bar plot
            BarPlot plot = new BarPlot(data);
            plot.getTitle().setText("MMR by city count");

            // Format plot
            plot.setInsets(new Insets2D.Double(40.0, 40.0, 40.0, 0.0));
            plot.setBarWidth(0.5);
            plot.setBackground(Color.WHITE);

            // Format bars
            BarPlot.BarRenderer pointRenderer = (BarPlot.BarRenderer) plot.getPointRenderers(data).get(0);
            pointRenderer.setColor(new ColorMapper() {
                @Override
                public Paint get(Number number) {
                    int column = (number.intValue() / 4);
                    Color color = (column % 2) == 0 ? Color.decode("#8b0000") : Color.BLUE;
                    return color;
                }

                @Override
                public ColorMapper.Mode getMode() {
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
                    return Color.BLACK;
                }

                @Override
                public ColorMapper.Mode getMode() {
                    return null;
                }
            });
            pointRenderer.setValueFont(Font.decode(null).deriveFont(12.0f));

            DrawableWriter writer = DrawableWriterFactory.getInstance().get("image/png");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writer.write(plot, baos, 1400, 600);
            RateLimitUtil.queue(channel.sendFile(baos.toByteArray(), ("img.png")));

            plot.setInsets(new Insets2D.Double(20.0, 100.0, 40.0, 0.0));
            plot.getTitle().setText("MMR by city count");

            StringBuilder response = new StringBuilder("> Each bar is segmented into four sections, from bottom to top: (soldiers, tanks, planes, ships)\n" +
                    "> Each coalition is grouped by city count and color coded.\n" +
                    "> **RED** = Coalition 1\n" +
                    "> **BLUE** = Coalition 2\n");

            if (sheet != null) {
                sheet.clear("A:Z");
                sheet.set(0, 0);
                response.append("<" + sheet.getURL() + ">");
            }

            return response.toString();
        }
    }

    @Command(desc = "Generate a graph of num nation by city count")
    public String cityTierGraph(@Me GuildDB db, @Me MessageChannel channel, NationList coalition1, NationList coalition2, @Switch('i') boolean includeInactives, @Switch('i') boolean includeApplicants) throws IOException {
        Set<DBNation> allNations = new HashSet<>();
        coalition1.getNations().removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        coalition2.getNations().removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        allNations.addAll(coalition1.getNations());
        allNations.addAll(coalition2.getNations());
        if (coalition1.getNations().isEmpty() || coalition2.getNations().isEmpty()) throw new IllegalArgumentException("No nations provided");
        int min = 0;
        int max = 0;
        for (DBNation nation : allNations) max = Math.max(nation.getCities(), max);
        max++;
        int[] count1 = new int[max + 1];
        int[] count2 = new int[max + 1];
        for (DBNation nation : coalition1.getNations()) count1[nation.getCities()]++;
        for (DBNation nation : coalition2.getNations()) count2[nation.getCities()]++;
        TimeDualNumericTable<Void> table = new TimeDualNumericTable<Void>("Nations by city count", "city", "nations", coalition1.getFilter(), coalition2.getFilter()) {
            @Override
            public void add(long cities, Void ignore) {
                add(cities, count1[(int) cities], count2[(int) cities]);
            }
        };
        for (int cities = min; cities <= max; cities++) {
            table.add(cities, (Void) null);
        }
        table.write(channel);
        return null;
    }

    @Command
    public String allianceMetricsCompareByTurn(@Me MessageChannel channel, @Me Guild guild, @Me GuildDB db, AllianceMetric metric, Set<Alliance> alliances, @Timestamp long time) throws IOException {
        long turnStart = TimeUtil.getTurn(time);
        Set<Alliance>[] coalitions = alliances.stream().map(f -> Collections.singleton(f)).collect(Collectors.toList()).toArray(new Set[0]);
        List<String> coalitionNames = alliances.stream().map(f -> f.getName()).collect(Collectors.toList());
        TimeNumericTable table = AllianceMetric.generateTable(metric, turnStart, coalitionNames, coalitions);
        table.write(channel);
        return "Done!";
    }

    @Command(aliases = {})
    public String allianceMetricsAB(@Me MessageChannel channel, @Me Guild guild, @Me GuildDB db, AllianceMetric metric, Set<Alliance> coalition1, Set<Alliance> coalition2, @Timestamp long time) throws IOException {
        long turnStart = TimeUtil.getTurn(time);
        TimeNumericTable table = AllianceMetric.generateTable(metric, turnStart, null, coalition1, coalition2);
        table.write(channel);
        return "Done!";
    }

    @RolePermission(value = {Roles.ECON, Roles.MILCOM, Roles.FOREIGN_AFFAIRS, Roles.INTERNAL_AFFAIRS}, any=true)
    @Command(desc = "Create a nation sheet, grouped by alliance")
    public String allianceNationsSheet(@Me MessageChannel channel, @Me User author, @Me Guild guild, @Me GuildDB db, Set<DBNation> nations, List<String> columns,
                                       @Switch('s') SpreadSheet sheet, @Switch('t') boolean useTotal, @Switch('i') boolean includeInactives, @Switch('a') boolean includeApplicants) throws IOException, GeneralSecurityException, IllegalAccessException, InvocationTargetException {
        nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        Map<Integer, Set<DBNation>> natByAA = new HashMap<>();
        for (DBNation nation : nations) {
            natByAA.computeIfAbsent(nation.getAlliance_id(), f -> new LinkedHashSet<>()).add(nation);
        }
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.ALLIANCES_SHEET);
        }
        List<String> header = (columns.stream().map(f -> f.replace("{", "").replace("}", "").replace("=", "")).collect(Collectors.toList()));
        sheet.setHeader(header);

        List<SAllianceContainer> allianceList = Locutus.imp().getPnwApi().getAlliances().getAlliances();
        Map<Integer, SAllianceContainer> alliances = allianceList.stream().collect(Collectors.toMap(f -> Integer.parseInt(f.getId()), f -> f));

        for (Map.Entry<Integer, Set<DBNation>> entry : natByAA.entrySet()) {
            Integer aaId = entry.getKey();

            Alliance alliance = new Alliance(aaId);
            SAllianceContainer sAlliance = alliances.get(aaId);

            SimpleNationList list = new SimpleNationList(entry.getValue());
            DBNation total = list.getTotal();
            DBNation average = list.getAverage();

            for (int i = 0; i < columns.size(); i++) {
                String arg = columns.get(i);
                if (arg.equalsIgnoreCase("{nations}")) {
                    arg = list.getNations().size() + "";
                } else {
                    DBNation nation = useTotal ? total : average;
                    if (arg.startsWith("avg:")) {
                        arg = arg.substring(4);
                        nation = average;
                    } else if (arg.startsWith("total:")) {
                        arg = arg.substring(6);
                        nation = total;
                    }
                    if (arg.contains("{") && arg.contains("}")) {
                        arg = DiscordUtil.format(guild, channel, author, nation, arg);
                        if (!arg.contains("{")) {
                            // TODO replace nation format
                        }
                    }
                    if (arg.contains("{") && arg.contains("}")) {
                        if (sAlliance != null)
                            for (Field field : SAllianceContainer.class.getDeclaredFields()) {
                                String placeholder = "{" + field.getName() + "}";
                                if (arg.contains(placeholder)) {
                                    field.setAccessible(true);
                                    arg = arg.replace(placeholder, field.get(sAlliance) + "");
                                }
                            }

                        for (Method method : Alliance.class.getDeclaredMethods()) {
                            if (method.getParameters().length != 0) continue;
                            Class type = method.getReturnType();
                            if (type == String.class || ClassUtils.isPrimitiveOrWrapper(type)) {
                                String placeholder = "{" + method.getName().toLowerCase() + "}";
                                if (arg.contains(placeholder)) {
                                    method.setAccessible(true);
                                    arg = arg.replace(placeholder, method.invoke(alliance) + "");
                                }
                            }
                        }
                    }
                }

                header.set(i, arg);
            }

            sheet.addRow(header);
        }


        sheet.clear("A:ZZ");
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
    }

    @Command
    public String allianceMetricsByTurn(@Me MessageChannel channel, @Me Guild guild, @Me GuildDB db, AllianceMetric metric, Set<Alliance> coalition, @Timestamp long time) throws IOException {
        long turnStart = TimeUtil.getTurn(time);
        List<String> coalitionNames = Arrays.asList(metric.name());
        TimeNumericTable table = AllianceMetric.generateTable(metric, turnStart, coalitionNames, coalition);
        table.write(channel);
        return "Done!";
    }

    @Command(
            desc="Transfer sheet of warcost (for each nation) broken down by resource type.\n" +
                    "Add -c to exclude consumption cost\n" +
                    "Add -i to exclude infra cost\n" +
                    "Add -l to exclude loot cost\n" +
                    "Add -u to exclude unit cost\n" +
                    "Add -g to include gray inactive nations\n" +
                    "Add -d to include non fighting defensive wars\n" +
                    "Add -n to normalize it per city\n" +
                    "Add -w to normalize it per war"
    )
    @RolePermission(Roles.MILCOM)
    public String WarCostByResourceSheet(@Me MessageChannel channel, @Me Guild guild, @Me GuildDB db, Set<NationOrAlliance> attackers, Set<NationOrAlliance> defenders, @Timestamp long time,
                                         @Switch('c') boolean excludeConsumption,
                                         @Switch('i') boolean excludeInfra,
                                         @Switch('l') boolean excludeLoot,
                                         @Switch('u') boolean excludeUnitCost,
                                         @Switch('g') boolean includeGray,
                                         @Switch('d') boolean includeDefensives,
                                         @Switch('n') boolean normalizePerCity,
                                         @Switch('w') boolean normalizePerWar,
                                         @Switch('s') SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.WAR_COST_SHEET);
        }

        WarParser parser1 = WarParser.of(attackers, defenders, time, Long.MAX_VALUE);
        WarParser parser2 = WarParser.of(defenders, attackers, time, Long.MAX_VALUE);

        Map<Integer, DBWar> allWars = new HashMap<>(parser1.getWars());
        allWars.putAll(parser2.getWars());

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

        Message msg = RateLimitUtil.complete(channel.sendMessage("Clearing sheet..."));

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

        RateLimitUtil.queue(channel.editMessageById(msg.getIdLong(), "Updating wars..."));

        sheet.setHeader(header);

        long start = System.currentTimeMillis();
        for (Map.Entry<DBNation, List<DBWar>> nationEntry : warsByNation.entrySet()) {
            DBNation nation = nationEntry.getKey();
            List<DBWar> wars = nationEntry.getValue();
            if (System.currentTimeMillis() - start > 5000) {
                RateLimitUtil.queue(channel.editMessageById(msg.getIdLong(), "Updating wars for " + nation.getNation()));
                start = System.currentTimeMillis();
            }
            int nationId = nation.getNation_id();

            AttackCost activeCost = new AttackCost();

            {
                Map<Integer, List<DBAttack>> allAttacks = Locutus.imp().getWarDb().getAttacksByWar(nationId, time);

                for (DBWar war : wars) {
                    if (war.date < time) {
                        continue;
                    }

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

                    AttackCost cost = null;
                    if (war.attacker_id == nationId) {
                        if (selfAttack) {
                            if (enemyAttack) {
                                cost = activeCost;
                            } else if (includeGray) {
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
                            } else if (includeGray) {
                                cost = activeCost;
                            }
                        } else if (enemyAttack && includeDefensives) {
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
            header.add(MarkupUtil.sheetUrl(nation.getAlliance(), nation.getAllianceUrl()));

            int numWars = activeCost.getNumWars();
            header.add(numWars);

            Map<ResourceType, Double> total = new HashMap<>();

            if (!excludeConsumption) {
                total = PnwUtil.add(total, activeCost.getConsumption(true));
            }
            if (!excludeInfra) {
                double infraCost = activeCost.getInfraLost(true);
                total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + infraCost);
            }
            if (!excludeLoot) {
                total = PnwUtil.add(total, activeCost.getLoot(true));
            }
            if (!excludeUnitCost) {
                total = PnwUtil.add(total, activeCost.getUnitCost(true));
            }

            if (normalizePerCity) {
                for (Map.Entry<ResourceType, Double> entry : total.entrySet()) {
                    entry.setValue(entry.getValue() / nation.getCities());
                }
            }
            if (normalizePerWar) {
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

            RateLimitUtil.queue(channel.editMessageById(msg.getIdLong(), "Uploading sheet"));

            sheet.set(0, 0);

            RateLimitUtil.queue(channel.deleteMessageById(msg.getIdLong()));
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return "<" + sheet.getURL() + ">";
    }

    @Command(aliases = {"WarCostByAllianceSheet", "WarCostByAASheet", "WarCostByAlliance", "WarCostByAA"},
            desc = "Warcost (for each alliance) broken down\n" +
                    "Add -i to include inactives\n" +
                    "Add -a to include applicants")
    @RolePermission(Roles.MILCOM)
    public String WarCostByAllianceSheet(@Me MessageChannel channel, @Me Guild guild, @Me GuildDB db,
                                         Set<DBNation> nationSet,
                                         @Timestamp long time,
                                         @Switch('i') boolean includeInactives,
                                         @Switch('a') boolean includeApplicants) throws IOException, GeneralSecurityException {
        Map<Integer, List<DBNation>> nationsByAA = Locutus.imp().getNationDB().getNationsByAlliance(nationSet, false, !includeInactives, !includeApplicants, true);

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

            List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacksAny(nationIds, time);

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

            Alliance alliance = new Alliance(entry.getKey());


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

        return "<" + sheet.getURL() + ">";
    }

    @Command(desc = "Warcost (for each nation) broken down by war type.\n" +
            "Add -c to exclude consumption cost\n" +
            "Add -i to exclude infra cost\n" +
            "Add -l to exclude loot cost\n" +
            "Add -u to exclude unit cost\n" +
            "Add -n to normalize it per city")
    @RolePermission(Roles.MILCOM)
    public String WarCostSheet(@Me MessageChannel channel, @Me Guild guild, @Me GuildDB db, Set<NationOrAlliance> attackers, Set<NationOrAlliance> defenders, @Timestamp long time,
                               @Switch('c') boolean excludeConsumption,
                               @Switch('i') boolean excludeInfra,
                               @Switch('l') boolean excludeLoot,
                               @Switch('u') boolean excludeUnitCost,
                               @Switch('n') boolean normalizePerCity,
                               @Switch('s') SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.WAR_COST_SHEET);
        }

        WarParser parser1 = WarParser.of(attackers, defenders, time, Long.MAX_VALUE);
        WarParser parser2 = WarParser.of(defenders, attackers, time, Long.MAX_VALUE);

        Message msg = RateLimitUtil.complete(channel.sendMessage("Clearing sheet..."));

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

                "total wars",
                "loss",
                "dmg",
                "ratio"
        ));

        sheet.clear("A:Z");

        RateLimitUtil.queue(channel.editMessageById(msg.getIdLong(), "Updating wars..."));

        sheet.setHeader(header);
        long start = System.currentTimeMillis();

        Map<Integer, DBWar> allWars = new HashMap<>(parser1.getWars());
        allWars.putAll(parser2.getWars());

        Map<Integer, List<DBWar>> warsByNation = new RankBuilder<>(allWars.values()).group(new BiConsumer<DBWar, GroupedRankBuilder<Integer, DBWar>>() {
            @Override
            public void accept(DBWar war, GroupedRankBuilder<Integer, DBWar> group) {
                if (parser1.getIsPrimary().apply(war)) {
                    group.put(war.attacker_id, war);
                } else if (parser1.getIsSecondary().apply(war)) {
                    group.put(war.defender_id, war);
                }
//                if (attacker != null && parser2.getIsPrimary().apply(war)) {
//                    group.put(defender, war);
//                }
//                if (attacker != null && parser2.getIsSecondary().apply(war)) {
//                    group.put(attacker, war);
//                }
            }
        }).get();

        List<DBAttack> allAttacks = new ArrayList<>(parser1.getAttacks());
        allAttacks.addAll(parser2.getAttacks());

        for (Map.Entry<Integer, List<DBWar>> entry : warsByNation.entrySet()) {
            int nationId = entry.getKey();
            DBNation nation = DBNation.byId(nationId);
            if (nation == null) continue;
            if (-start + (start = System.currentTimeMillis()) > 5000) {
                RateLimitUtil.queue(channel.editMessageById(msg.getIdLong(), "Updating wars for " + nationId));
            }
//            int nationId = nation.getNation_id();

            AttackCost attInactiveCost = new AttackCost();
            AttackCost defInactiveCost = new AttackCost();
            AttackCost attActiveCost = new AttackCost();
            AttackCost defActiveCost = new AttackCost();

            AttackCost attSuicides = new AttackCost();
            AttackCost defSuicides = new AttackCost();

            {
                List<DBWar> wars = entry.getValue();
                Set<Integer> warIds = wars.stream().map(f -> f.warId).collect(Collectors.toSet());
                List<DBAttack> attacks = new LinkedList<>();
                for (DBAttack attack : allAttacks) if (warIds.contains(attack.war_id)) attacks.add(attack);
                Map<Integer, List<DBAttack>> attacksByWar = new RankBuilder<>(attacks).group(f -> f.war_id).get();

                for (DBWar war : wars) {
                    List<DBAttack> warAttacks = attacksByWar.getOrDefault(war.warId, Collections.emptyList());

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
                            cost = defActiveCost;//defInactiveCost;
                        } else {
                            continue;
                        }
                    }

                    cost.addCost(warAttacks, isPrimary, isSecondary);
                }
            }

            header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));

            header.set(1, attInactiveCost.getNumWars());
            header.set(2, -total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attInactiveCost,true));
            header.set(3, attInactiveCost.getNumWars() == 0 ? 0 : (-total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attInactiveCost, true)) / (double) attInactiveCost.getNumWars());

            header.set(4, defActiveCost.getNumWars());
            header.set(5, defActiveCost.getNumWars() == 0 ? 0 : total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, defActiveCost,true) / defActiveCost.getNumWars());
            header.set(6, defActiveCost.getNumWars() == 0 ? 0 : total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, defActiveCost,false) / defActiveCost.getNumWars());
            double defRatio = (double) header.get(6) / (double) header.get(5);
            header.set(7, defActiveCost.getNumWars() == 0 ? 0 : Double.isFinite(defRatio) ? defRatio : 0);

            header.set(8, attActiveCost.getNumWars());
            header.set(9, attActiveCost.getNumWars() == 0 ? 0 : total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attActiveCost,true) / attActiveCost.getNumWars());
            header.set(10, attActiveCost.getNumWars() == 0 ? 0 : total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attActiveCost,false) / attActiveCost.getNumWars());
            double attRatio = (double) header.get(10) / (double) header.get(9);
            header.set(11, attActiveCost.getNumWars() == 0 ? 0 : Double.isFinite(attRatio) ? attRatio : 0);

            int numTotal = defActiveCost.getNumWars() + attActiveCost.getNumWars();
            double lossTotal = total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, defActiveCost,true) + total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attActiveCost,true);
            double dmgTotal = total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, defActiveCost,false) + total(!excludeConsumption, !excludeInfra, !excludeLoot, !excludeUnitCost, normalizePerCity, nation, attActiveCost,false);
            header.set(12, numTotal);
            header.set(13, numTotal == 0 ? 0 : lossTotal / numTotal);
            header.set(14, numTotal == 0 ? 0 : dmgTotal / numTotal);
            double ratio = (double) header.get(14) / (double) header.get(13);
            header.set(15, numTotal == 0 ? 0 : Double.isFinite(ratio) ? ratio : 0);

            sheet.addRow(header);
        }

        try {

            RateLimitUtil.queue(channel.editMessageById(msg.getIdLong(), "Uploading sheet"));
            sheet.clear("A:Z");
            sheet.set(0, 0);

            RateLimitUtil.queue(channel.deleteMessageById(msg.getIdLong()));
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return "<" + sheet.getURL() + ">";
    }

    private double total(boolean consumption, boolean infra, boolean loot, boolean units, boolean normalize, DBNation nation, AttackCost cost, boolean isPrimary) {
        Map<ResourceType, Double> total = new HashMap<>();

        if (consumption) {
            total = PnwUtil.add(total, cost.getConsumption(isPrimary));
        }
        if (infra) {
            double infraCost = cost.getInfraLost(isPrimary);
            total.put(ResourceType.MONEY, total.getOrDefault(ResourceType.MONEY, 0d) + infraCost);
        }
        if (loot) {
            total = PnwUtil.add(total, cost.getLoot(isPrimary));
        }
        if (units) {
            total = PnwUtil.add(total, cost.getUnitCost(isPrimary));
        }

        if (normalize) {
            for (Map.Entry<ResourceType, Double> entry : total.entrySet()) {
                entry.setValue(entry.getValue() / nation.getCities());
            }
        }
        return PnwUtil.convertedTotal(total);
    }
}
