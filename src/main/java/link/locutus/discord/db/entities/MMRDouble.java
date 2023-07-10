package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;

import java.util.ArrayList;
import java.util.List;

public class MMRDouble {
    public final double[] mmr;

    public MMRDouble(double[] mmr) {
        this.mmr = mmr;
    }

    public static MMRDouble fromString(String input) {
        double[] mmr = new double[4];
        if (input.length() == 4) {
            mmr[0] = (int) (input.charAt(0) - '0');
            mmr[1] = (int) (input.charAt(1) - '0');
            mmr[2] = (int) (input.charAt(2) - '0');
            mmr[3] = (int) (input.charAt(3) - '0');
        } else if (input.contains("/") || input.contains(",")) {
            String[] split = input.split("[/,]");
            mmr[0] = Double.parseDouble(split[0]);
            mmr[1] = Double.parseDouble(split[1]);
            mmr[2] = Double.parseDouble(split[2]);
            mmr[3] = Double.parseDouble(split[3]);
        } else {
            throw new IllegalArgumentException("MMR must be 4 numbers. Provided value: `" + input + "`");
        }
        return new MMRDouble(mmr);
    }


    public int toNumber() {
        return (int) (Math.round(mmr[0]) * 1000 + Math.round(mmr[1]) * 100 + Math.round(mmr[2]) * 10 + Math.round(mmr[3]));
    }

    public double get(MilitaryUnit unit) {
        MilitaryBuilding building = unit.getBuilding();
        if (building == null) return 0;
        return mmr[building.ordinal() - Buildings.BARRACKS.ordinal()];
    }

    /**
     * Value between 0 and 1
     * @param unit
     * @return
     */
    public double getPercent(MilitaryUnit unit) {
        MilitaryBuilding building = unit.getBuilding();
        if (building == null) return 0;
        return mmr[building.ordinal() - Buildings.BARRACKS.ordinal()] / building.cap(null);
    }

    @Override
    public String toString() {
        boolean isDouble = false;
        List<String> valueStr = new ArrayList<>();
        for (double value : mmr) {
            if ((int) value != value) isDouble = true;
            valueStr.add(MathMan.format(value));
        }
        if (isDouble) return StringMan.join(valueStr, "/");
        return StringMan.join(valueStr, "");
    }

    public double getBarracks() {
        return mmr[0];
    }

    public double getFactory() {
        return mmr[1];
    }

    public double getHangar() {
        return mmr[2];
    }

    public double getDrydock() {
        return mmr[3];
    }
}
