package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.util.TimeUtil;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import link.locutus.discord.util.scheduler.KeyValue;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class Activity {

    double[] byDay = new double[7];
    double[] byDayTurn = new double[12];
    double[] byWeekTurn = new double[12 * 7];

    public double[] getByDay() {
        return byDay;
    }

    public double[] getByDayTurn() {
        return byDayTurn;
    }

    public double[] getByWeekTurn() {
        return byWeekTurn;
    }

    public static Function<DBNation, Activity> createCache(int turns) {
        HashMap<DBNation, Activity> activityMap = new HashMap<>();
        return nation -> activityMap.computeIfAbsent(nation, (Function<DBNation, Activity>) n -> n.getActivity(turns));
    }

    /**
     * Turns from now, array of activity probability
     * @param numTurns
     * @param week
     * @return
     */
    public Map.Entry<Integer, double[]> optimalAttackTime(int numTurns, boolean week) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        DayOfWeek day = now.getDayOfWeek();
        int dayTurn = now.getHour() / 2;
        int weekTurn = dayTurn + day.ordinal() * 12;
        double[] arr = week ? byWeekTurn : byDayTurn;

        if (numTurns >= arr.length || numTurns <= 1) return new KeyValue<>(0, new double[0]);

        for (int i = 0; i < arr.length; i++) {
            arr[i] = Math.pow(arr[i], 2);
        }

        double total = 0;
        double[] arrTotals = new double[arr.length + numTurns];
        for (int i = 0; i < arrTotals.length; i++) {
            int indexRel = i % arr.length;
            double prob = arr[indexRel];
            total += prob;
            arrTotals[i] = total;
        }

        double min = Double.MAX_VALUE;
        int maxStart = -1;
        for (int i = 0; i < arr.length; i++) {
            double start = i == 0 ? 0 : arrTotals[i - 1];
            double end = arrTotals[i + numTurns];
            total = end - start;

            int dayEnd = i - (i % 12) + 12;
            if (dayEnd - i < numTurns) {
                total += arrTotals[dayEnd] - start;
            }

            total += arrTotals[i] - start;

            if (total < min) {
                maxStart = i;
                min = total;
            }
        }

        double[] result = new double[numTurns];
        for (int i = maxStart; i < maxStart + numTurns; i++) {
            result[i - maxStart] = arr[i % arr.length];
        }

        int optimalTurn = maxStart - (week ? weekTurn : dayTurn);
        if (optimalTurn < 0) maxStart += arr.length;

        return new KeyValue<>(optimalTurn, result);
    }

    public double loginChance(int numTurns, boolean week) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        DayOfWeek day = now.getDayOfWeek();
        int dayTurn = now.getHour() / 2;
        int weekTurn = dayTurn + day.ordinal() * 12;
        int start = week ? weekTurn : dayTurn;
        return loginChance(start, numTurns, week);
    }

    public double loginChance(int currentTurn, int numTurns, boolean week) {
        double[] arr = week ? byWeekTurn : byDayTurn;

        int end = currentTurn + numTurns;

        double total = 0;

        for (int i = currentTurn; i <= end; i++) {
            int indexRel = i % arr.length;
            double prob = arr[indexRel];
            total = prob + total - (prob * total);
        }
        return total;
    }

    private void load(Set<Long> activity, long minTurnAbs, long maxTurnAbs) {
        if (activity.isEmpty()) return;
        if (maxTurnAbs == Long.MAX_VALUE) maxTurnAbs = TimeUtil.getTurn();

        int[] byDayTotal = new int[7];
        int[] byDayTurnTotal = new int[12];
        int[] byWeekTurnTotal = new int[12 * 7];

        long currentTurn = TimeUtil.getTurn();
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        minTurnAbs = Math.max(minTurnAbs, activity.iterator().next());

        DayOfWeek lastDay = null;
        DayOfWeek lastDayTotal = null;
        for (long turn = minTurnAbs; turn <= maxTurnAbs; turn++) {
            boolean active = activity.contains(turn);
            long turnDiff = currentTurn - turn;
            ZonedDateTime other = now.minusHours(turnDiff * 2);

            DayOfWeek day = other.getDayOfWeek();
            int dayTurn = other.getHour() / 2;
            int weekTurn = dayTurn + day.ordinal() * 12;

            if (day != lastDayTotal) {
                byDayTotal[day.ordinal()]++;
                lastDayTotal = day;
            }

            byDayTurnTotal[dayTurn]++;
            byWeekTurnTotal[weekTurn]++;

            if (active) {
                if (day != lastDay) {
                    byDay[day.ordinal()]++;
                    lastDay = day;
                }

                byDayTurn[dayTurn]++;
                byWeekTurn[weekTurn]++;
            }
        }

        for (int i = 0; i < byDay.length; i++) {
            if (byDay[i] != 0) byDay[i] /= byDayTotal[i];
        }
        for (int i = 0; i < byDayTurn.length; i++) {
            if (byDayTurn[i] != 0) byDayTurn[i] /= byDayTurnTotal[i];
        }
        for (int i = 0; i < byWeekTurn.length; i++) {
            if (byWeekTurn[i] != 0) byWeekTurn[i] /= byWeekTurnTotal[i];
        }
    }

    public Activity(int nationId, long turnStartAbs, long turnEndAbs) {
        Set<Long> activity = Locutus.imp().getNationDB().getActivity(nationId, turnStartAbs, turnEndAbs);
        if (activity.isEmpty()) return;
        load(activity, turnStartAbs, turnEndAbs);
    }

    public Activity(int nationId) {
        long turnStartAbs = 0;
        Set<Long> activity = Locutus.imp().getNationDB().getActivity(nationId, turnStartAbs, Long.MAX_VALUE);
        if (activity.isEmpty()) return;

        load(activity, turnStartAbs, Long.MAX_VALUE);
    }

    public double getAverageByDay() {
        double average = 0;
        for (double v : byDay) {
            average += v;
        }
        average /= byDay.length;
        return average;
    }

    public double getAverageByWeekTurn() {
        double average = 0;
        for (double v : byWeekTurn) {
            average += v;
        }
        average /= byWeekTurn.length;
        return average;
    }
}
