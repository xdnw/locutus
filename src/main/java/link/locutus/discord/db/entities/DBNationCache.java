package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.util.TimeUtil;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class DBNationCache {
    public long lastCheckEspionageFull;

//    public long lastCheckSpyCasualtiesMs;
//    public long lastUpdateSpiesMs;

    public long lastCheckProjectsMS;
    public boolean checkedUnitLastTurn;
    public long firstCheckThisTurnMS;
    public long lastCheckUnitMS;
    /**
     * Char array, 12 long. First 6 = buys last turn, second 6 = buys this turn
     * Order: Soldier, Tank, Aircraft, Ship, Missile, Nuke
     * An array is used to improve performance and reduce memory usage
     *
     * Note: Unit buys are not counted if there is no previous turn info, to avoid cases of accumulating the count of multiple previous turns
     */
    public int[] unitBuys;

    public void processUnitChange(DBNation parent, MilitaryUnit unit, int previous, int current) {
        // Index for each unit type in the unit buy array
        int index = -1;
        switch (unit) {
            case SOLDIER:
                index = 0;
                break;
            case TANK:
                index = 1;
                break;
            case AIRCRAFT:
                index = 2;
                break;
            case SHIP:
                index = 3;
                break;
            case MISSILE:
                index = 4;
                break;
            case NUKE:
                index = 5;
                break;
        }
        if (index != -1) {
            long now = System.currentTimeMillis();
            long diff = now - firstCheckThisTurnMS;
            long lastCheckUnit = TimeUtil.getTurn(firstCheckThisTurnMS);
            lastCheckUnitMS = now;

            boolean countChange = true;

            long currentTurn = TimeUtil.getTurn();
            if (currentTurn != lastCheckUnit) {
                boolean isTurnChange = lastCheckUnit == currentTurn - 1;
                if (isTurnChange) {
                    checkedUnitLastTurn = true;
                    shiftUnitBuys();
                } else {
                    checkedUnitLastTurn = false;
                    resetBuys();

                    countChange = false; // Don't count unit buys when
                }
                firstCheckThisTurnMS = now;
            } else if (countChange && diff < TimeUnit.MINUTES.toMillis(3) && !checkedUnitLastTurn) {
                // Don't count changes if this is the first update
                countChange = false;
            }

            if (countChange) {
                if (current > previous) {
                    if (unitBuys == null) unitBuys = new int[12];
                    unitBuys[6 + index] += (current - previous);
                }

                // Check again even if there is no buy, because project
                if (unitBuys != null) {
                    // If the project has been updated recently, check their projects, otherwise be lenient and assume they have all projects
                    Predicate<Project> hasProjects;

                    if (now - lastCheckProjectsMS < TimeUnit.MINUTES.toMillis(3)) {
                        hasProjects = parent::hasProject;
                    } else if (current > previous) {
                        hasProjects = f -> true;
                    } else {
                        // Don't check if there is no buy and project info isn't updated
                        return;
                    }

                    int capFast = unit.getMaxPerDay(parent.getCities(), hasProjects);
                    int totalBought = unitBuys[index] + unitBuys[index + 6];

                    if (totalBought > capFast) {
                        // A double buy is sign of day change
                        parent.setDc_turn((int) TimeUtil.getDayTurn());
                    }
                }
            }
        }
    }

    private void resetBuys() {
        if (unitBuys != null) {
            Arrays.fill(unitBuys, 0);
        }
    }

    private void shiftUnitBuys() {
        if (unitBuys != null) {
            for (int i = 6; i < 12; i++) {
                unitBuys[i - 6] = unitBuys[i];
                unitBuys[i] = 0;
            }
        }
    }

    private int[] getOrCreateUnitBuys() {
        if (unitBuys == null) unitBuys = new int[12];
        return unitBuys;
    }

//    public long lastCheckSpies; // Disabled. Spy count can be unreliable
}
