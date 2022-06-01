package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;

public class MilitaryChange implements Timestamp, Cost {
    private final int to;
    private final long date;
    private final MilitaryUnit unit;
    private final int from;

    public MilitaryChange(long date, MilitaryUnit unit, int from, int to) {
        this.date = date;
        this.unit = unit;
        this.from = from;
        this.to = to;
    }

    @Override
    public double[] getPrice() {
        double[] result = new double[ResourceType.values.length];
        if (to > from) {
            long amt = to - from;
            result[0] += unit.getCost() * amt;
            for (ResourceType resource : unit.getResources()) {
                result[resource.ordinal()] += unit.getRssAmt(resource) * amt;
            }
        }
        return result;
    }

    @Override
    public long getDate() {
        return date;
    }
}
