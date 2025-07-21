package link.locutus.discord.apiv1.enums;

import com.politicsandwar.graphql.model.Color;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.util.TimeUtil;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum  NationColor implements NationList {
    AQUA,
    BEIGE,
    BLACK,
    BLUE,
    BROWN,
    GRAY,
    GREEN,
    LIME,
    MAROON,
    OLIVE,
    ORANGE,
    PINK,
    PURPLE,
    RED,
    WHITE,
    YELLOW,
    MINT,
    LAVENDER,
    TURQUOISE,
    GOLD

    ;

    public static final NationColor[] values = values();
    private int turnBonus;
    private String votedName;

    @Command(desc = "Get the bonus turn income for nations on this color.")
    public int getTurnBonus() {
        if (this == GRAY) return 0;
        if (this == BEIGE) return 85_000;
        return turnBonus;
    }

    @Command(desc = "Get the name of the color.")
    public String getName() {
        return name();
    }

    @Command(desc = "Get the number of nations on this color.")
    public int getNumNations(@NoFormat @Default NationFilter filter) {
        if (filter != null) {
            return Locutus.imp().getNationDB().getNationsMatching(f -> f.getColor() == this && filter.test(f)).size();
        }
        return Locutus.imp().getNationDB().getNationsMatching(f -> f.getColor() == this).size();
    }

    @Command(desc = "If this is a taxable color")
    public boolean isTaxable() {
        return this != GRAY && this != BEIGE;
    }

    private static int COLOR_REVENUE_CAP = -1;
    private static long COLOR_REVENUE_CAP_TURN = -1;

    public static int getColorRevenueCap() {
        long turn = TimeUtil.getTurn();
        if (turn == COLOR_REVENUE_CAP_TURN && COLOR_REVENUE_CAP != -1) {
            return COLOR_REVENUE_CAP;
        }

        Map<NationColor, Set<DBNation>> nationsByColor = getNationsByColor();
        RevenueCapInfo valueInfo = calculateColorRevenueCap(nationsByColor);
        COLOR_REVENUE_CAP_TURN = turn;
        COLOR_REVENUE_CAP = valueInfo.cap();
        return COLOR_REVENUE_CAP;
    }

    public static Map<NationColor, Set<DBNation>> getNationsByColor() {
        Map<NationColor, Set<DBNation>> nationsByColor = new Object2ObjectOpenHashMap<>();
        for (DBNation nation : Locutus.imp().getNationDB().getNationsMatching(f -> f.getAlliance_id() != 0 && f.getVm_turns() == 0)) {
            NationColor color = nation.getColor();
            nationsByColor.computeIfAbsent(color, _ -> new ObjectOpenHashSet<>()).add(nation);
        }
        return nationsByColor;
    }

    public static record RevenueCapInfo(double aggregateRevenue, int nations, double averageRevenue, int cap) {}

    public static RevenueCapInfo calculateColorRevenueCap(Map<NationColor, Set<DBNation>> nationsByColor) {
        double oneTurn = 1 / 12d;
        double revenueTotal = 0;
        int totalNations = 0;
        for (Map.Entry<NationColor, Set<DBNation>> entry : nationsByColor.entrySet()) {
            NationColor color = entry.getKey();
            Set<DBNation> nationsOnColor = entry.getValue();
            if (color == GRAY || color == BEIGE) continue;

            double totalRevenue = 0;
            for (DBNation nation : nationsOnColor) {
                if (nation.getAlliance_id() == 0 || nation.getVm_turns() > 0) continue;
                double[] revenue = nation.getRevenue();
                double dnr = Locutus.imp().getTradeManager().getGamePrice(revenue) * oneTurn;
                totalRevenue += dnr;
                totalNations++;
            }
        }
        double averageDNR = revenueTotal / totalNations;
        double colorRevenueCap = Math.round(averageDNR * 18d / totalNations);
        int cap = Math.toIntExact(java.lang.Math.round(colorRevenueCap));
        return new RevenueCapInfo(revenueTotal, totalNations, averageDNR, cap);
    }

    public static int countNationsLessThanC21(Map<NationColor, Set<DBNation>> nationsByColor) {
        int totalNationsLessThanC21 = 0;
        for (Map.Entry<NationColor, Set<DBNation>> entry : nationsByColor.entrySet()) {
            NationColor color = entry.getKey();
            if (color == GRAY || color == BEIGE) continue;
            Set<DBNation> nations = entry.getValue();
            for (DBNation nation : nations) {
                if (nation.getAlliance_id() == 0 || nation.getVm_turns() > 0) continue;
                if (nation.getCities() < 21) {
                    totalNationsLessThanC21++;
                }
            }
        }
        return totalNationsLessThanC21;
    }

    public static record TurnBonusInfo(int nations, int nationsBelowC21, double aggregateDNR, double averageDNR, int growthTurnBonus, int growthTurnBonusUncapped, int recruitTurnBonus, int totalTurnBonus) {};

    /**
     * Calculates the turn bonus for a color based on the nations on that color.
     * Note: growth bonus is left uncapped
     * @param nationsByColor
     * @param newTurnBonusCap
     * @param totalNationsLessThanC21
     * @return entry(growthTurnBonus, recruitTurnBonus)
     */
    public TurnBonusInfo getTurnBonus(Map<NationColor, Set<DBNation>> nationsByColor, int newTurnBonusCap, int totalNationsLessThanC21) {
        double oneTurn = 1 / 12d;

        Set<DBNation> nationsOnColor = nationsByColor.getOrDefault(this, Collections.emptySet());
        int nationsOnColorCount = 0;
        int totalNationsLessThanC21OnColor = 0;
        double totalDNROfColor = 0;
        for (DBNation nation : nationsOnColor) {
            if (nation.getAlliance_id() == 0 || nation.getVm_turns() > 0) continue;
            double[] revenue = nation.getRevenue();
            double dnr = Locutus.imp().getTradeManager().getGamePrice(revenue) * oneTurn;
            totalDNROfColor += dnr;
            nationsOnColorCount++;

            if (nation.getCities() < 21) {
                totalNationsLessThanC21OnColor++;
            }
        }
        double averageDMROfColor = totalDNROfColor / nationsOnColorCount;

        if (this == GRAY) {
            // growth and recruit bonuses are 0
            return new TurnBonusInfo(
                    nationsOnColorCount,
                    totalNationsLessThanC21OnColor,
                    totalDNROfColor,
                    averageDMROfColor,
                    0,
                    0,
                    0,
                    0
            );
        }
        double growthTurnBonus;
        double recruitTurnBonus;
        if (this == BEIGE) {
            growthTurnBonus = getTurnBonus();
            recruitTurnBonus = 0;
        } else {
            growthTurnBonus = (averageDMROfColor * 0.75d / nationsOnColorCount);
            recruitTurnBonus = Math.min(
                    (totalNationsLessThanC21OnColor * 10d / totalNationsLessThanC21),
                    1d
            ) * newTurnBonusCap;
        }
        return new TurnBonusInfo(
                nationsOnColorCount,
                totalNationsLessThanC21OnColor,
                totalDNROfColor,
                averageDMROfColor,
                Math.toIntExact(Math.round(growthTurnBonus)),
                Math.toIntExact(Math.round(growthTurnBonus)),
                Math.toIntExact(Math.round(recruitTurnBonus)),
                Math.toIntExact(Math.round(growthTurnBonus + recruitTurnBonus))
        );
    }

    public void setTurnBonus(int amt) {
        this.turnBonus = amt;
    }

    @Command(desc = "Get the name of the color that was voted by nations.")
    public String getVotedName() {
        return votedName == null ? name() : votedName;
    }

    public void setVotedName(String votedName) {
        this.votedName = votedName;
    }

    public static NationColor fromV3(Color color) {
        switch (color.getColor().toUpperCase(Locale.ROOT)) {
            case "AQUA": return AQUA;
            case "BEIGE": return BEIGE;
            case "BLACK": return BLACK;
            case "BLUE": return BLUE;
            case "BROWN": return BROWN;
            case "GRAY": return GRAY;
            case "GREEN": return GREEN;
            case "LIME": return LIME;
            case "MAROON": return MAROON;
            case "OLIVE": return OLIVE;
            case "ORANGE": return ORANGE;
            case "PINK": return PINK;
            case "PURPLE": return PURPLE;
            case "RED": return RED;
            case "WHITE": return WHITE;
            case "YELLOW": return YELLOW;
            case "MINT": return MINT;
            case "LAVENDER": return LAVENDER;
            case "TURQUOISE": return TURQUOISE;
            case "GOLD": return GOLD;
            default: throw new IllegalArgumentException("Unknown color: " + color.getColor());
        }
    }

    public Set<DBNation> getNationsMatching(@Default NationFilter filter) {
        if (filter != null) {
            return Locutus.imp().getNationDB().getNationsMatching(f -> f.getColor() == this && filter.test(f));
        }
        return Locutus.imp().getNationDB().getNationsMatching(f -> f.getColor() == this);
    }

    @Override
    public Set<DBNation> getNations() {
        return getNationsMatching(null);
    }
}
