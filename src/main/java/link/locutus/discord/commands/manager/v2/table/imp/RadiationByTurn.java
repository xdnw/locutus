package link.locutus.discord.commands.manager.v2.table.imp;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;

public class RadiationByTurn extends SimpleTable<Void> {
    private final long turnStart;
    private final long turnEnd;
    private final Map<Long, Map<Continent, Double>> radsbyTurn;
    private final List<Continent> continentsList;
    private final String[] labels;
    private final double[] buffer;

    public RadiationByTurn(Collection<Continent> continents, long start, long end) {
        this.turnStart = TimeUtil.getTurn(start);
        this.turnEnd = (end >= TimeUtil.getTimeFromTurn(TimeUtil.getTurn())) ? TimeUtil.getTurn() : TimeUtil.getTimeFromTurn(end);
        this.radsbyTurn = Locutus.imp().getNationDB().getRadiationByTurns();
        this.continentsList = new ObjectArrayList<>(continents);
        this.labels = new String[continents.size() + 1];
        for (int i = 0; i < continentsList.size(); i++) {
            labels[i] = continentsList.get(i).name();
        }
        labels[labels.length - 1] = "Global";
        this.buffer = new double[labels.length];
        setTitle("Radiation by turn");
        setLabelX("turn");
        setLabelY("Radiation");
        setLabels(labels);
    }

    @Override
    public SimpleTable<Void> writeData() {
        for (long turn = turnStart; turn <= turnEnd; turn++) {
            add(turn, (Void) null);
        }
        return this;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.DAYS_TO_DATE;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }

    @Override
    public void add(long turn, Void ignore) {
        int turnRelative = (int) (turn - turnStart);
        Map<Continent, Double> radsByCont = radsbyTurn.get(turn);
        if (radsByCont != null) {
            for (int i = 0; i < continentsList.size(); i++) {
                double rads = radsByCont.getOrDefault(continentsList.get(i), 0d);
                buffer[i] = rads;
            }
            double total = 0;
            for (double val : radsByCont.values()) total += val;
            buffer[buffer.length - 1] = total / 5d;
        }
        add(turnRelative, buffer);
    }
}
