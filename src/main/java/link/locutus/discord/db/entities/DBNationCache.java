package link.locutus.discord.db.entities;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Research;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.util.TimeUtil;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

public class DBNationCache {
    public Int2ObjectArrayMap<byte[]> metaCache;
    public long lastCheckEspionageFull;

//    public long lastCheckSpyCasualtiesMs;
//    public long lastUpdateSpiesMs;

    public long lastCheckProjectsMS;
    public boolean checkedUnitLastTurn;
    public long firstCheckThisTurnMS;
    public long lastCheckUnitMS;
    /**
     * Int array, 14 long. First 7 = buys last turn, second 7 = buys this turn.
     * Order: Soldier, Tank, Aircraft, Ship, Missile, Nuke, Spies.
     *
     * <p>An array is used to improve performance and reduce memory usage. Unit buys are not counted
     * if there is no previous turn info, to avoid accumulating the count of multiple previous turns.</p>
     */
    public int[] unitBuys;

    public void processUnitChange(DBNation parent, MilitaryUnit unit, int previous, int current) {
        int index = trackedUnitBuyIndex(unit);
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
                    if (unitBuys == null) unitBuys = new int[14];
                    unitBuys[7 + index] += (current - previous);
                }

                // Check again even if there is no buy, because project
                if (unitBuys != null) {
                    // If the project has been updated recently, check their projects, otherwise be lenient and assume they have all projects
                    Predicate<Project> hasProjects;
                    Function< Research, Integer> getResearch;

                    if (now - lastCheckProjectsMS < TimeUnit.MINUTES.toMillis(3)) {
                        hasProjects = parent::hasProject;
                        getResearch = f -> parent.getResearch(null, f);
                    } else if (current > previous) {
                        hasProjects = Predicates.alwaysTrue();
                        getResearch = f -> 20;
                    } else {
                        // Don't check if there is no buy and project info isn't updated
                        return;
                    }

                    int capFast = unit.getMaxPerDay(parent.getCities(), hasProjects, getResearch);
                    int totalBought = unitBuys[index] + unitBuys[index + 7];

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
            for (int i = 7; i < 14; i++) {
                unitBuys[i - 7] = unitBuys[i];
                unitBuys[i] = 0;
            }
        }
    }

    public int currentTurnUnitBuys(MilitaryUnit unit) {
        int index = trackedUnitBuyIndex(unit);
        if (index < 0 || unitBuys == null) {
            return 0;
        }
        int currentTurnIndex = 7 + index;
        return currentTurnIndex < unitBuys.length ? Math.max(0, unitBuys[currentTurnIndex]) : 0;
    }

    private int[] getOrCreateUnitBuys() {
        if (unitBuys == null) unitBuys = new int[14];
        return unitBuys;
    }

    private static int trackedUnitBuyIndex(MilitaryUnit unit) {
        return switch (unit) {
            case SOLDIER -> 0;
            case TANK -> 1;
            case AIRCRAFT -> 2;
            case SHIP -> 3;
            case MISSILE -> 4;
            case NUKE -> 5;
            case SPIES -> 6;
            default -> -1;
        };
    }
}
