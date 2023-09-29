package link.locutus.discord.apiv1.enums;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import org.apache.commons.lang3.text.WordUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
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

    private final Boolean north;
    private final String name;
    private final ResourceType[] resources;

    Continent(Boolean north, ResourceType... resources) {
        this.north = north;
        this.resources = resources;
        this.name = WordUtils.capitalize(name().replace("_", " "));
    }

    @Command(desc = "Returns the name of the continent")
    public String getName() {
        return name();
    }

    @Command(desc = "If this continent is in the northern hemisphere")
    public boolean isNorth() {
        return north;
    }

    @Command(desc = "The resources that are available on this continent")
    public List<ResourceType> getResources() {
        return List.of(resources);
    }

    public ResourceType[] getResourceArray() {
        return resources;
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


    @Command(desc = "Returns the season modifier for this continent")
    public double getSeasonModifier() {
        return getSeasonModifier(Locutus.imp().getTradeManager().getGameDate());
    }

    @Command(desc = "Returns the season modifier for this continent at the given date")
    public double getSeasonModifier(@Timestamp long date) {
        return getSeasonModifier(Instant.ofEpochMilli(date));
    }

    public double getSeasonModifier(Instant instant) {
        double season = 1;
        switch (LocalDateTime.ofInstant(instant, ZoneOffset.UTC).getMonth()) {
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

    @Command(desc = "Returns the radiation index for this continent")
    public double getRadIndex() {
        return Locutus.imp().getTradeManager().getGlobalRadiation(this);
    }
}
