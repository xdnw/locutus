package link.locutus.discord.apiv1.enums;

import com.politicsandwar.graphql.model.Color;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.util.PW;

import java.util.Collection;
import java.util.Locale;
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

    ;

    public static int COLOR_REVENUE_CAP = 125_000;

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
    public int getNumNations(@Default NationFilter filter) {
        if (filter != null) {
            return Locutus.imp().getNationDB().getNationsMatching(f -> f.getColor() == this && filter.test(f)).size();
        }
        return Locutus.imp().getNationDB().getNationsMatching(f -> f.getColor() == this).size();
    }

    @Command(desc = "If this is a taxable color")
    public boolean isTaxable() {
        return this != GRAY && this != BEIGE;
    }

    public int getTurnBonus(Set<DBNation> nationsOnColor, boolean cap) {
        if (this == GRAY) return 0;
        if (this == BEIGE) return getTurnBonus();
        double totalRev = 0;
        for (DBNation nation : nationsOnColor) {
            totalRev += ResourceType.convertedTotal(nation.getRevenue()) / 12d;
        }
        double colorRev = Math.round((totalRev / Math.pow(nationsOnColor.size(), 2)));
        if (cap) {
            colorRev = Math.max(0, Math.min(COLOR_REVENUE_CAP, colorRev));
        }
        return (int) Math.round(colorRev);
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
