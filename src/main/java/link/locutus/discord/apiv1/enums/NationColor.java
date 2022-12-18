package link.locutus.discord.apiv1.enums;

import com.politicsandwar.graphql.model.Color;

import java.util.Locale;

public enum  NationColor {
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

    public static final NationColor[] values = values();
    private int turnBonus;
    private String votedName;

    public int getTurnBonus() {
        return turnBonus;
    }

    public void setTurnBonus(int amt) {
        this.turnBonus = amt;
    }

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
}
