package link.locutus.discord.apiv1.enums;

import link.locutus.discord.Locutus;
import org.apache.commons.lang3.text.WordUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

import static link.locutus.discord.apiv1.enums.ResourceType.*;

public enum Continent {
    NORTH_AMERICA(true, COAL, IRON, URANIUM, FOOD),
    SOUTH_AMERICA(false, OIL, BAUXITE, LEAD, FOOD),
    EUROPE(true, COAL, IRON, LEAD, FOOD),
    AFRICA(false, OIL, BAUXITE, URANIUM, FOOD),
    ASIA(true, OIL, IRON, URANIUM, FOOD),
    AUSTRALIA(false, COAL, BAUXITE, LEAD, FOOD),
    ANTARCTICA(null, COAL, OIL, URANIUM, FOOD)

    ;

    public static Continent[] values = values();

    public final Boolean north;
    public final String name;
    public final ResourceType[] resources;

    Continent(Boolean north, ResourceType... resources) {
        this.north = north;
        this.resources = resources;
        this.name = WordUtils.capitalize(name().replace("_", " "));
    }

    public static Continent parseV3(String toUpperCase) {
        switch (toUpperCase.toUpperCase(Locale.ROOT)) {
            case "NA": return NORTH_AMERICA;
            case "SA": return SOUTH_AMERICA;
            case "EU": return EUROPE;
            case "AF": return AFRICA;
            case "AS": return ASIA;
            case "AU": return AUSTRALIA;
            case "AN": return ANTARCTICA;
            default:
                throw new IllegalArgumentException("No continent found for: " + toUpperCase);
        }
    }

    public double foodModifier(ZonedDateTime time) {
        if (north == Boolean.TRUE) {
            return 1;
        } else if (north == Boolean.FALSE){
            return 1;
        }
        return 0.5;
    }

    public double getSeasonModifier() {
        return getSeasonModifier(Locutus.imp().getTradeManager().getGameDate().toEpochMilli());
    }
    public double getSeasonModifier(long date) {
        double season = 1;
        switch (ZonedDateTime.from(Instant.ofEpochMilli(date)).getMonth()) {
            case DECEMBER:
            case JANUARY:
            case FEBRUARY:
                switch (this) {
                    case NORTH_AMERICA:
                    case EUROPE:
                    case ASIA:
                        season = 0.8;
                        break;
                    case ANTARCTICA:
                        season = 0.5;
                        break;
                    default:
                        season = 1.2;
                        break;
                }
                break;

            case JUNE:
            case JULY:
            case AUGUST:
                switch (this) {
                    case NORTH_AMERICA:
                    case EUROPE:
                    case ASIA:
                        season = 1.2;
                        break;
                    case ANTARCTICA:
                        season = 0.5;
                        break;
                    default:
                        season = 0.8;
                        break;
                }
                break;
        }
        return season;
    }

    public double getRadIndex() {
        return Locutus.imp().getTradeManager().getGlobalRadiation(this);
    }
}
