package com.boydti.discord.commands.manager.v2.impl.pw;

import com.boydti.discord.util.MathMan;

public class TaxRate {
    public final int money;
    public final int resources;

    public TaxRate(String parse) {
        String[] split = parse.split("/");
        if (split.length != 2) throw new IllegalArgumentException("Invalid tax rate: " + parse + ". (must be in format 100/100)");
        if (!MathMan.isInteger(split[0]) || !MathMan.isInteger(split[1])) throw new IllegalArgumentException("Tax rate must be numeric: " + parse);
        this.money = Integer.parseInt(split[0]);
        this.resources = Integer.parseInt(split[1]);
    }

    public TaxRate(int money, int rss) {
        this.money = money;
        this.resources = rss;
    }

    @Override
    public String toString() {
        return money + "/" + resources;
    }

    public int[] toArray() {
        return new int[]{money, resources};
    }
}
