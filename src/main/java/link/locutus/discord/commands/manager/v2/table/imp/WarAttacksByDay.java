package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WarAttacksByDay extends SimpleTable<Void> {
    private final long minDay;
    private final long maxDay;
    private final Map<Long, Integer> totalAttacksByDay;

    public WarAttacksByDay(@Default Set<DBNation> nations,
                           @Arg("Period of time to graph") @Default @Timestamp Long cutoff,
                           @Arg("Restrict to a list of attack types") @Default Set<AttackType> allowedTypes) {
        if (cutoff == null) cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        Long finalCutoff = cutoff;

        this.minDay = TimeUtil.getDay(cutoff);
        this.maxDay = TimeUtil.getDay();
        if (maxDay - minDay > 5000) {
            throw new IllegalArgumentException("Too many days.");
        }

        Predicate<AttackType> allowedType = allowedTypes == null ? f -> true : allowedTypes::contains;

        Map<Integer, DBWar> wars;
        if (nations == null) {
            wars = Locutus.imp().getWarDb().getWarsSince(cutoff);
        } else {
            Set<Integer> nationIds = nations.stream().map(DBNation::getId).collect(Collectors.toSet());
            wars = Locutus.imp().getWarDb().getWarsForNationOrAlliance(nationIds::contains, null, f -> f.possibleEndDate() >= finalCutoff);
        }

        final List<AbstractCursor> attacks = new ArrayList<>();
        Locutus.imp().getWarDb().iterateAttacks(wars.values(), allowedType, attack -> {
            if (attack.getDate() > finalCutoff) {
                attacks.add(attack);
            }
            return false;
        }, null, null);

        this.totalAttacksByDay = new HashMap<>();
        for (AbstractCursor attack : attacks) {
            long day = TimeUtil.getDay(attack.getDate());
            totalAttacksByDay.put(day, totalAttacksByDay.getOrDefault(day, 0) + 1);
        }

        setTitle("Total attacks by day");
        setLabelX("day");
        setLabelY("attacks");
        setLabels(new String[]{"Day", "Attacks"});

        writeData();
    }

    @Override
    protected SimpleTable<Void> writeData() {
        for (long day = minDay; day <= maxDay; day++) {
            add(day, (Void) null);
        }
        return this;
    }

    @Override
    public void add(long day, Void ignore) {
        long offset = day - minDay;
        int attacks = totalAttacksByDay.getOrDefault(day, 0);
        add(offset, new double[]{attacks});
    }

    @Override
    public long getOrigin() {
        return minDay;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.DAYS_TO_DATE;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }
}
