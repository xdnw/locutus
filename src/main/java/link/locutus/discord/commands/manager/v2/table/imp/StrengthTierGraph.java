package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

public class StrengthTierGraph extends SimpleTable{

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public SimpleTable writeData() {
        return null;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.SI_UNIT;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }

    @Override
    public void add(long day, Object cost) {

    }
}
