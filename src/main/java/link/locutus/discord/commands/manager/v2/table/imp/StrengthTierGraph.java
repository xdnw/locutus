package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.MMRDouble;
import link.locutus.discord.util.PW;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.HashSet;
import java.util.Set;

public class StrengthTierGraph extends SimpleTable<Void> {
    private final double[] coal1StrSpread;
    private final double[] coal2StrSpread;
    private final int minScore;
    private final int maxScore;

    public StrengthTierGraph(String col1Str, String col2Str, Set<DBNation> coalition1Nations, Set<DBNation> coalition2Nations, boolean includeInactives,
                             boolean includeApplicants, MMRDouble col1MMR, MMRDouble col2MMR, Double col1Infra, Double col2Infra) {
        Set<DBNation> allNations = new HashSet<>();
        coalition1Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        coalition2Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        allNations.addAll(coalition1Nations);
        allNations.addAll(coalition2Nations);
        if (coalition1Nations.isEmpty() || coalition2Nations.isEmpty()) throw new IllegalArgumentException("No nations provided");

        int maxScore = 0;
        int minScore = Integer.MAX_VALUE;
        for (DBNation nation : allNations) {
            maxScore = (int) Math.max(maxScore, nation.estimateScore(col1MMR, col1Infra, null, null));
            minScore = (int) Math.min(minScore, nation.estimateScore(col2MMR, col2Infra, null, null));
        }
        this.maxScore = maxScore;
        this.minScore = minScore;

        double[] coal1Str = new double[(int) (maxScore * PW.WAR_RANGE_MAX_MODIFIER)];
        double[] coal2Str = new double[(int) (maxScore * PW.WAR_RANGE_MAX_MODIFIER)];

        this.coal1StrSpread = new double[coal1Str.length];
        this.coal2StrSpread = new double[coal2Str.length];

        for (DBNation nation : coalition1Nations) {
            coal1Str[(int) (nation.estimateScore(col1MMR, col1Infra, null, null) * PW.WAR_RANGE_MIN_MODIFIER)] += nation.getStrengthMMR(col1MMR);
        }
        for (DBNation nation : coalition2Nations) {
            coal2Str[(int) (nation.estimateScore(col2MMR, col2Infra, null, null) * PW.WAR_RANGE_MIN_MODIFIER)] += nation.getStrengthMMR(col2MMR);
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

        setTitle("Effective military strength by score range");
        setLabelX("score");
        setLabelY("strength");
        setLabels(col1Str, col2Str);

        writeData();
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

    @Override
    public void add(long score, Void ignore) {
        add(score - minScore, coal1StrSpread[(int) score], coal2StrSpread[(int) score]);
    }

    @Override
    protected StrengthTierGraph writeData() {
        for (int score = (int) Math.max(10, minScore * 0.75 - 10); score < maxScore * 1.25 + 10; score++) {
            add(score, (Void) null);
        }
        return this;
    }
}
