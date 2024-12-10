package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PW;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.HashSet;
import java.util.Set;

public class ScoreTierGraph extends SimpleTable<Void> {
    private final double[] coal1StrSpread;
    private final double[] coal2StrSpread;
    private final int minScore;
    private final int maxScore;

    public ScoreTierGraph(String col1Str, String col2Str, Set<DBNation> coalition1Nations, Set<DBNation> coalition2Nations, boolean includeInactives, boolean includeApplicants) {
        Set<DBNation> allNations = new HashSet<>();
        coalition1Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        coalition2Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        allNations.addAll(coalition1Nations);
        allNations.addAll(coalition2Nations);

        if (coalition1Nations.isEmpty() || coalition2Nations.isEmpty()) throw new IllegalArgumentException("No nations provided");

        int maxScore = 0;
        int minScore = Integer.MAX_VALUE;
        for (DBNation nation : allNations) {
            maxScore = (int) Math.max(maxScore, nation.getScore());
            minScore = (int) Math.min(minScore, nation.getScore());
        }
        this.maxScore = maxScore;
        this.minScore = minScore;

        double[] coal1Str = new double[(int) (maxScore * PW.WAR_RANGE_MAX_MODIFIER)];
        double[] coal2Str = new double[(int) (maxScore * PW.WAR_RANGE_MAX_MODIFIER)];

        this.coal1StrSpread = new double[coal1Str.length];
        this.coal2StrSpread = new double[coal2Str.length];

        for (DBNation nation : coalition1Nations) {
            coal1Str[(int) (nation.getScore() * 0.75)] += 1;
        }
        for (DBNation nation : coalition2Nations) {
            coal2Str[(int) (nation.getScore() * 0.75)] += 1;
        }
        for (int min = 10; min < coal1Str.length; min++) {
            double val = coal1Str[min];
            if (val == 0) continue;
            int max = Math.min(coal1StrSpread.length, (int) (PW.WAR_RANGE_MAX_MODIFIER * (min / 0.75)));

            for (int i = min; i < max; i++) {
                coal1StrSpread[i] += val;
            }
        }
        for (int min = 10; min < coal2Str.length; min++) {
            double val = coal2Str[min];
            if (val == 0) continue;
            int max = Math.min(coal2StrSpread.length, (int) (PW.WAR_RANGE_MAX_MODIFIER * (min / 0.75)));

            for (int i = min; i < max; i++) {
                coal2StrSpread[i] += val;
            }
        }

        setTitle("Nations by score range");
        setLabelX("score");
        setLabelY("nations");
        setLabels(col1Str, col2Str);

        writeData();
    }

    @Override
    protected ScoreTierGraph writeData() {
        for (int score = (int) Math.max(10, minScore * 0.75 - 10); score < maxScore * 1.25 + 10; score++) {
            add(score, (Void) null);
        }
        return this;
    }

    @Override
    public void add(long score, Void ignore) {
        add(score - minScore, coal1StrSpread[(int) score], coal2StrSpread[(int) score]);
    }

    @Override
    public long getOrigin() {
        return minScore;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.DECIMAL_ROUNDED;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }
}