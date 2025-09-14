package link.locutus.discord.db.entities;

import link.locutus.discord.util.SpyCount;

public enum Safety {
    QUICK(1, 1),
    NORMAL(2, 2.8284271428571428571428571428571),
    COVERT(3, 5.1961524285714285714285714285714d);

    public final int id;
    private final double costFactor;

    Safety(int id, double costFactor) {
        this.id = id;
        this.costFactor = costFactor;
    }

    public double getCostFactor() {
        return costFactor;
    }

    public static Safety byId(int safety) {
        switch (safety) {
            case 1:
                return QUICK;
            case 2:
                return NORMAL;
            case 3:
                return COVERT;
        }
        return null;
    }
}
