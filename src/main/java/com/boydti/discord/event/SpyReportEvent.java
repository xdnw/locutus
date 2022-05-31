package com.boydti.discord.event;

import com.boydti.discord.pnw.DBNation;

public class SpyReportEvent extends Event {
    private final DBNation reportBy;
    private final DBNation target;
    private final double[] loot;
    private final long lastOpDay;

    public DBNation getReportBy() {
        return reportBy;
    }

    public DBNation getTarget() {
        return target;
    }

    public double[] getLoot() {
        return loot;
    }

    public long getLastOpDay() {
        return lastOpDay;
    }

    public int getAmt() {
        return amt;
    }

    private final int amt;

    public SpyReportEvent(DBNation reportBy, DBNation target, double[] loot, long lastOpDay, int amt) {
        this.reportBy = reportBy;
        this.target = target;
        this.loot = loot;
        this.lastOpDay = lastOpDay;
        this.amt = amt;

    }
}
