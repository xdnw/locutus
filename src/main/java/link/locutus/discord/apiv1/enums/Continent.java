package link.locutus.discord.apiv1.enums;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.trade.TradeManager;
import org.apache.commons.lang3.text.WordUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    @Command(desc = "If this continent has the given resource")
    public boolean hasResource(ResourceType type) {
        for (ResourceType resource : resources) {
            if (resource == type) return true;
        }
        return false;
    }

    @Command(desc = "Returns the buildings that are available on this continent")
    public Set<Building> getBuildings() {
        Set<Building> result = new ObjectOpenHashSet<>();
        for (Building building : Buildings.values()) {
            if (building.canBuild(this)) {
                result.add(building);
            }
        }
        return result;
    }

    @Command(desc = "If the given building can be built on this continent")
    public boolean canBuild(Building building) {
        return building.canBuild(this);
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

    @Command(desc = "Returns the food production ratio for this continent (0.0 - 1.0)")
    public double getFoodRatio() {
        TradeManager manager = Locutus.imp().getTradeManager();
        double global = 0;
        double local = 0;
        for (Continent continent : Continent.values) {
            double rads = manager.getGlobalRadiation(continent);
            if (continent == this) local = rads;
            global += rads;
        }
        global /= 5;

        double modifier = getSeasonModifier();
        modifier *= (1 - (local + global) / 1000d);
        return modifier;
    }

    public Set<DBNation> getNations(@Default NationFilter filter) {
        Predicate<DBNation> filter2 = filter == null ? null : filter.toCached(Long.MAX_VALUE);
        return Locutus.imp().getNationDB().getNationsMatching(f -> f.getContinent() == this && (filter2 == null || filter2.test(f)));
    }

    @Command(desc = "The ordinal of this continent")
    public int getOrdinal() {
        return ordinal();
    }

    @Command(desc = "Number of nations on this continent")
    public int getNumNations(@NoFormat @Default NationFilter filter) {
        return getNations(filter).size();
    }

    @Command(desc = "Returns the total value of the given attribute for the nations")
    public double getTotal(@NoFormat NationAttributeDouble attribute, @NoFormat @Default NationFilter filter) {
        double total = 0;
        for (DBNation nation : getNations(filter)) {
            total += attribute.apply(nation);
        }
        return total;
    }

    @Command(desc = "Returns the total value of the given attribute per nation")
    public double getAverage(@NoFormat NationAttributeDouble attribute, @NoFormat @Default NationFilter filter) {
        double total = 0;
        Set<DBNation> nations = getNations(filter);
        for (DBNation nation : nations) {
            total += attribute.apply(nation);
        }
        return total / nations.size();
    }

    @Command(desc = "Returns the average value of the given attribute per another attribute (such as cities)")
    public double getAveragePer(@NoFormat NationAttributeDouble attribute, @NoFormat NationAttributeDouble per, @NoFormat @Default NationFilter filter) {
        double total = 0;
        double perTotal = 0;
        for (DBNation nation : getNations(filter)) {
            total += attribute.apply(nation);
            perTotal += per.apply(nation);
        }
        return total / perTotal;
    }

    @Command(desc = "Returns the season modifier for this continent")
    public double getSeasonModifier() {
        return getSeasonModifier(Locutus.imp().getTradeManager().getGameDate());
    }

    @Command(desc = "Returns the season modifier for this continent at the given date")
    public double getSeasonModifierDate(@Timestamp long date) {
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

    public String getAcronym() {
        String[] split = name().split("_");
        return switch (split.length) {
            case 1 -> name.substring(0, 2);
            case 2 -> split[0].charAt(0) + "" + split[1].charAt(0);
            default -> Arrays.stream(split)
                    .map(s -> s.substring(0, 1))
                    .collect(Collectors.joining());
        };
    }
}
