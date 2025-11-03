package link.locutus.discord.util;

import com.google.common.base.Predicates;
import com.google.common.hash.Hashing;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.politicsandwar.graphql.model.GameInfo;
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.domains.subdomains.AllianceBankContainer;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.CommerceBuilding;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.building.ResourceBuilding;
import link.locutus.discord.apiv1.enums.city.building.imp.APowerBuilding;
import link.locutus.discord.apiv1.enums.city.building.imp.AResourceBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.csv.file.NationsFileSnapshot;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.stock.Exchange;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.web.WebUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class PW {
    public static class API {
        // Settings.USE_FALLBACK is honored as a hard override
// public final class Settings { public static boolean USE_FALLBACK = false; }

        private static final Object LOCK = new Object();

        private static final long MIN_BACKOFF_MS = TimeUnit.MINUTES.toMillis(1);    // initial
        private static final long MAX_BACKOFF_MS = TimeUnit.MINUTES.toMillis(15);   // cap
        private static final long ERROR_STALE_RESET_MS = TimeUnit.MINUTES.toMillis(5); // if no 500s for this long, reset counters

        // State
        private static volatile long last500Error = 0L;
        private static volatile int consecutive500Errors = 0;
        private static volatile long currentBackoffMs = 0L;
        private static volatile long backoffUntil = 0L;

        // Call this when catching exceptions from the primary. Returns true if it looks like a 500.
        public static boolean is500Error(Throwable e) {
            if (!looksLike500(e)) return false;

            final long now = System.currentTimeMillis();
            synchronized (LOCK) {
                // If the last 500 was a while ago, treat this as a fresh incident
                if (now - last500Error > ERROR_STALE_RESET_MS) {
                    consecutive500Errors = 0;
                    currentBackoffMs = 0;
                }

                last500Error = now;
                consecutive500Errors++;

                // Only start backoff after the second 500
                if (consecutive500Errors >= 2) {
                    currentBackoffMs = (currentBackoffMs == 0)
                            ? MIN_BACKOFF_MS
                            : Math.min(MAX_BACKOFF_MS, currentBackoffMs << 1); // double, capped
                    backoffUntil = now + currentBackoffMs;
                }
            }
            return true;
        }

        // Check this before using the primary. If true, prefer the fallback.
        public static boolean hasRecent500Error() {
            if (Settings.USE_FALLBACK) return true;

            final long now = System.currentTimeMillis();
            final long until = backoffUntil; // volatile read
            return until > now;
        }

        // Optional helper: call this after a successful primary call to reset state.
        public static void recordPrimarySuccess() {
            synchronized (LOCK) {
                consecutive500Errors = 0;
                currentBackoffMs = 0;
                backoffUntil = 0;
                // Keep last500Error as historical
            }
        }

        // Optional helper: how long until we try primary again.
        public static long millisUntilPrimaryAllowed() {
            final long now = System.currentTimeMillis();
            final long until = backoffUntil;
            return Math.max(0L, until - now);
        }

        public static boolean is500Message(String t) {
            if (t == null) return false;
            t = t.toLowerCase(Locale.ROOT);
            return t.contains("500 internal server error")
                    || t.contains("http 500")
                    || t.contains(" status 500")
                    || t.contains("is the game's api down?");
        }

        private static boolean looksLike500(Throwable t) {
            while (t != null) {
                final String msg = t.getMessage();
                if (is500Message(msg)) {
                    return true;
                }
                t = t.getCause();
            }
            return false;
        }
    }

    public static final class City {
        public static double getCostReduction(Predicate<Project> projects) {
            return getCostReduction(projects.test(Projects.URBAN_PLANNING),
                    projects.test(Projects.ADVANCED_URBAN_PLANNING),
                    projects.test(Projects.METROPOLITAN_PLANNING));
        }

        public static double getCostReduction(boolean up, boolean aup, boolean mp) {
            double reduction = 0;
            if (up) reduction += 173118000;
            if (aup) reduction += 346236000;
            if (mp) reduction += 519354000;
            return reduction;
        }

        public static final class Building {
            public static int SIZE = 27;
        }

        private static final int NUKE_TURN_MAX = 11 * 12;
        private static final double NUKE_POLLUTION_MAX = 400;

        public static int getNukePollution(int nukeTurn) {
            int pollution = 0;
            long turns = TimeUtil.getTurn() - nukeTurn;
            if (turns < NUKE_TURN_MAX) {
                double pollutionMax = 400d;
                double nukePollution = (NUKE_TURN_MAX - turns) * NUKE_POLLUTION_MAX / (NUKE_TURN_MAX);
                if (nukePollution > 0) {
                    pollution += (int) nukePollution;
                }
            }
            return pollution;
        }

        public static class Land {
            public static final double NEW_CITY_BASE = 250;

            public static double calculateLand(double from, double to) {
                if (from < 0 || from == to) return 0;
                if (to <= from) return (from - to) * -50;
                if (to > 20000) throw new IllegalArgumentException("Land cannot exceed 10,000");
                double[] tmp = LAND_COST_CACHE;
                if (tmp != null && from == tmp[0] && to == tmp[1]) {
                    return tmp[2];
                }

                double total = 0;
                for (double i = Math.max(0, from); i < to; i += 500) {
                    double cost = 0.002d * Math.pow(Math.max(20, i - 20), 2) + 50;
                    double amt = Math.min(500, to - i);
                    total += cost * amt;
                }
                LAND_COST_CACHE = new double[]{from, to, total};

                return total;
            }

            public static double calculateLand(double from, double to, boolean ra, boolean aec, boolean ala, boolean gsa, boolean bda) {
                double factor = 1;
                if (aec) factor -= 0.05;
                if (ala) factor -= 0.05;
                if (ra) {
                    factor -= 0.05;
                    if (gsa) factor -= 0.025;
                    if (bda) factor -= 0.0125;
                }
                return PW.City.Land.calculateLand(from, to) * factor;
            }
        }

        public static class Infra {
            public static final double NEW_CITY_BASE = 10;

            private static int getInfraCostCents(double infra) {
                if (infra <= 4000) {
                    int index = Math.max(0, (int) (infra * 100) - 3000);
                    return INFRA_COST_FAST_CACHE[index];
                }
                return (int) Math.round(100 * (300d + (Math.pow(Math.max(infra - 10d, 20), (2.2d))) * 0.00140845070422535211267605633803));
            }

            private static int getInfraCostCents(int infra_cents) {
                if (infra_cents <= 400000) {
                    int index = Math.max(0, infra_cents - 3000);
                    return INFRA_COST_FAST_CACHE[index];
                }
                return (int) Math.round(100 * (300d + (Math.pow(Math.max(infra_cents - 1000, 2000) * 0.01, (2.2d))) * 0.00140845070422535211267605633803));
            }

            public static double calculateInfra(double from, double to, boolean aec, boolean cfce, boolean urbanization, boolean gsa, boolean bda) {
                double factor = 1;
                if (aec) factor -= 0.05;
                if (cfce) factor -= 0.05;
                if (urbanization) {
                    factor -= 0.05;
                    if (gsa) factor -= 0.025;
                    if (bda) factor -= 0.0125;
                }
                return calculateInfra(from, to) * (to > from ? factor : 1);
            }

            // precompute the cost for first 4k infra
            public static double calculateInfra(double from, double to) {
                if (from < 0) from = 0;
                if (to <= from) return (from - to) * -150;
                if (to > 20000) throw new IllegalArgumentException("Infra cannot exceed 10,000 (" + to + ")");
                long total_cents = 0;
                int to_cents = (int) Math.round(to * 100);
                int from_cents = (int) Math.round(from * 100);
                for (int i = to_cents; i >= from_cents; i -= 10000) {
                    int amt = Math.min(10000, i - from_cents);
                    int cost_cents = getInfraCostCents(i - amt);
                    total_cents += ((long) cost_cents * amt);
                }
                total_cents = (total_cents + 50)  / 100;
                return total_cents * 0.01;
            }

            /**
             * Value of attacking target with infra, to take them from current infra -> 1500
             *
             * @param avg_infra
             * @param cities
             * @return net damage value that should be done
             */
            public static int calculateInfraAttackValue(int avg_infra, int cities) {
                if (avg_infra < 1500) return 0;
                double total = 0;
                for (int i = 1500; i < avg_infra; i++) {
                    total += 300d + (Math.pow((i - 10d), (2.2d))) / 710d;
                }
                return (int) (total * cities);
            }
        }

        public static int getPollution(Predicate<Project> hasProject, Function<link.locutus.discord.apiv1.enums.city.building.Building, Integer> getBuildings, int pollution) {
            for (link.locutus.discord.apiv1.enums.city.building.Building building : Buildings.POLLUTION_BUILDINGS) {
                int amt = getBuildings.apply(building);
                if (amt == 0) continue;
                int buildPoll = building.pollution(hasProject);
                if (buildPoll != 0) {
                    pollution += amt * buildPoll;
                }
            }
            return Math.max(0, pollution);
        }

        public static int getCommerce(Predicate<Project> hasProject, Function<link.locutus.discord.apiv1.enums.city.building.Building, Integer> getBuildings, int maxCommerce, int commerce) {
            for (link.locutus.discord.apiv1.enums.city.building.Building building : Buildings.COMMERCE_BUILDINGS) {
                int amt = getBuildings.apply(building);
                if (amt == 0) continue;
                commerce += amt * building.getCommerce();
            }
            if (hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM)) {
                commerce += 4;
            }
            if (hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER)) {
                commerce += 1;
                if (hasProject.test(Projects.TELECOMMUNICATIONS_SATELLITE)) {
                    commerce += 2;
                }
            }
            if (commerce > maxCommerce) {
                commerce = maxCommerce;
            }
            return commerce;
        }

        public static int getCommerce(Predicate<Project> hasProject, Function<link.locutus.discord.apiv1.enums.city.building.Building, Integer> getBuildings) {
            int commerce = 0;
            int maxCommerce;
            if (hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER)) {
                if (hasProject.test(Projects.TELECOMMUNICATIONS_SATELLITE)) {
                    maxCommerce = 125;
                } else {
                    maxCommerce = 115;
                }
            } else {
                maxCommerce = 100;
            }
            return getCommerce(hasProject, getBuildings, maxCommerce, commerce);
        }

        public static double getCrime(Predicate<Project> hasProject, Function<link.locutus.discord.apiv1.enums.city.building.Building, Integer> getBuildings, long infra_cents, int commerce) {
            int police = getBuildings.apply(Buildings.POLICE_STATION);
            double policeMod;
            if (police > 0) {
                double policePct = hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM) ? 3.5 : 2.5;
                policeMod = police * (policePct);
            } else {
                policeMod = 0;
            }
            return Math.max(0, ((MathMan.sqr(103 - commerce) + (infra_cents))*(0.000009d) - policeMod));
        }

        public static double profitConverted(Continent continent, double rads, Predicate<Project> hasProject, int numCities, double grossModifier, ICity city) {
            double profit = 0;

            final boolean powered = (city.getPowered() != Boolean.FALSE) && (city.getPoweredInfra() >= city.getInfra());
            int unpoweredInfra = (int) Math.ceil(city.getInfra());

            if (powered) {
                for (int ordinal = 0; ordinal < 4; ordinal++) {
                    int amt = city.getBuildingOrdinal(ordinal);
                    if (amt == 0) continue;
                    link.locutus.discord.apiv1.enums.city.building.Building building = Buildings.get(ordinal);
                    for (int i = 0; i < amt; i++) {
                        if (unpoweredInfra > 0) {
                            profit += ((APowerBuilding) building).consumptionConverted(unpoweredInfra);
                            unpoweredInfra = unpoweredInfra - ((APowerBuilding) building).getInfraMax();
                        }
                    }
                    profit += building.profitConverted(continent, rads, hasProject, city.getLand(), amt);
                }
                for (int ordinal = Buildings.GAS_REFINERY.ordinal(); ordinal < PW.City.Building.SIZE; ordinal++) {
                    int amt = city.getBuildingOrdinal(ordinal);
                    if (amt == 0) continue;
                    link.locutus.discord.apiv1.enums.city.building.Building building = Buildings.get(ordinal);
                    profit += building.profitConverted(continent, rads, hasProject, city.getLand(), amt);
                }
            }

            for (int ordinal = 4; ordinal < Buildings.GAS_REFINERY.ordinal(); ordinal++) {
                int amt = city.getBuildingOrdinal(ordinal);
                if (amt == 0) continue;

                link.locutus.discord.apiv1.enums.city.building.Building building = Buildings.get(ordinal);
                profit += building.profitConverted(continent, rads, hasProject, city.getLand(), amt);
            }

            int commerce = powered ? city.calcCommerce(hasProject) : 0;

            double newPlayerBonus = 1 + Math.max(1 - (numCities - 1) * 0.05, 0);

            double income = Math.max(0, (((commerce * 0.02) * 0.725) + 0.725) * city.calcPopulation(hasProject) * newPlayerBonus) * grossModifier;

            profit += income;

            double basePopulation = city.getInfra() * 100;
            double food = (Math.pow(basePopulation, 2)) / 125_000_000 + ((basePopulation) * (1 + Math.log(city.getAgeDays()) / 15d) - basePopulation) / 850;

            profit -= ResourceType.convertedTotalNegative(ResourceType.FOOD, food);

            return profit;
        }

        public static double[] profit(Continent continent,
                               double rads,
                               long date,
                               Predicate<Project> hasProject,
                               double[] profitBuffer,
                               int numCities,
                               double grossModifier,
                               boolean forceUnpowered,
                               int turns,
                               ICity city
        ) {
            if (profitBuffer == null) profitBuffer = new double[ResourceType.values.length];

            boolean powered;
            if (forceUnpowered) {
                powered = false;
            } else {
                powered = true;
                Boolean setPowered = city.getPowered();
                if (setPowered != null) powered = setPowered;
                if (powered && city.getPoweredInfra() < city.getInfra()) powered = false;
            }

            int unpoweredInfra = (int) Math.ceil(city.getInfra());
            for (link.locutus.discord.apiv1.enums.city.building.Building building : Buildings.values()) {
                int amt = city.getBuilding(building);
                if (amt == 0) continue;
                if (!powered) {
                    if (building instanceof CommerceBuilding || building instanceof MilitaryBuilding || (building instanceof ResourceBuilding && ((AResourceBuilding) building).getResourceProduced().isManufactured())) {
                        continue;
                    }
                }
                profitBuffer = building.profit(continent, rads, date, hasProject, city, profitBuffer, turns, amt);
                if (building instanceof APowerBuilding) {
                    for (int i = 0; i < amt; i++) {
                        if (unpoweredInfra > 0) {
                            profitBuffer = ((APowerBuilding) building).consumption(unpoweredInfra, profitBuffer, turns);
                            unpoweredInfra = unpoweredInfra - ((APowerBuilding) building).getInfraMax();
                        }
                    }
                }
            }
            int commerce = city.calcCommerce(hasProject);
            double newPlayerBonus = 1 + Math.max(1 - (numCities - 1) * 0.05, 0);

            double income = (((commerce * 0.02d) * 0.725d) + 0.725d) * city.calcPopulation(hasProject) * newPlayerBonus * grossModifier;

            profitBuffer[ResourceType.MONEY.ordinal()] += income * turns / 12;

            double basePopulation = city.getInfra() * 100;
            double food = (Math.pow(basePopulation, 2)) / 125_000_000 + ((basePopulation) * (1 + Math.log(city.getAgeDays()) / 15d) - basePopulation) / 850;
            profitBuffer[ResourceType.FOOD.ordinal()] -= food * turns / 12d;

            return profitBuffer;
        }

        public static double getDisease(Predicate<Project> hasProject, Function<link.locutus.discord.apiv1.enums.city.building.Building, Integer> getBuildings, long infra_cents, long land_cents, double pollution) {
            int hospitals = getBuildings.apply(Buildings.HOSPITAL);
            double hospitalModifier;
            if (hospitals > 0) {
                double hospitalPct = hasProject.test(Projects.CLINICAL_RESEARCH_CENTER) ? 3.5 : 2.5;
                hospitalModifier = hospitals * hospitalPct;
            } else {
                hospitalModifier = 0;
            }
            double pollutionModifier = pollution * 0.05;
            return Math.max(0, ((0.01 * MathMan.sqr((infra_cents) / (land_cents * 0.01 + 0.001)) - 25) * 0.01d) + (infra_cents * 0.01 * 0.001) - hospitalModifier + pollutionModifier);
        }

        public static double nextCityCost(DBNation nation, int amount) {
            int current = nation.getCities();
            return cityCost(nation, current, current + amount);
        }

        public static double cityCost(DBNation nation, int from, int to) {
            return cityCost(from, to,
                    nation != null && nation.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY,
//                    nation != null && nation.hasProject(Projects.URBAN_PLANNING),
//                    nation != null && nation.hasProject(Projects.ADVANCED_URBAN_PLANNING),
//                    nation != null && nation.hasProject(Projects.METROPOLITAN_PLANNING),
                    nation != null && nation.hasProject(Projects.GOVERNMENT_SUPPORT_AGENCY),
                    nation != null && nation.hasProject(Projects.BUREAU_OF_DOMESTIC_AFFAIRS));
        }

        public static int getPopulation(long infra_cents, double crime, double disease, long ageDays) {
            double ageBonus = (1 + Math.log(Math.max(1, ageDays)) * 0.0666666666666666666666666666666);
            double diseaseDeaths = ((disease * 0.01) * infra_cents);
            double crimeDeaths = Math.max((crime * 0.1) * (infra_cents) - 25, 0);

            return (int) Math.round(Math.max(10, ((infra_cents - diseaseDeaths - crimeDeaths) * ageBonus)));
        }

        public static double cityCost(int from, int to, boolean manifestDestiny, boolean govSupportAgency, boolean bureauOfDomesticAffairs) {
            double total = 0;
            for (int city = Math.max(1, from); city < to; city++) {
                total += nextCityCost(city,
                        manifestDestiny,
                        govSupportAgency, bureauOfDomesticAffairs);
            }
            return total;
        }

        public static void updateCityAverage() {
            GameInfo gameInfo = Locutus.imp().getApiPool().getGameInfo();
            Double avg = gameInfo.getCity_average();
            if (avg != null) {
                CITY_AVERAGE = avg;
                Locutus.imp().getDiscordDB().setCityAverage(avg);
            }
        }

        public static double CITY_AVERAGE = -1;
//        public static double nextCityCost(int currentCity, boolean manifestDestiny, boolean cityPlanning, boolean advCityPlanning, boolean metPlanning, boolean govSupportAgency, boolean bureauOfDomesticAffairs) {
//            double cost = 50000*Math.pow(currentCity - 1, 3) + 150000 * (currentCity) + 75000;
//            if (cityPlanning) {
//                cost -= 50000000;
//            }
//            if (advCityPlanning) {
//                cost -= 100000000;
//            }
//            if (metPlanning) {
//                cost -= 150_000_000;
//            }
//            if (manifestDestiny) {
//                double factor = 0.05;
//                if (govSupportAgency) factor += 0.025;
//                if (bureauOfDomesticAffairs) factor += 0.0125;
//                cost *= (1 - factor);
//            }
//            return Math.max(0, cost);
//        }

        // removed: , boolean cityPlanning, boolean advCityPlanning, boolean metPlanning
        public static double nextCityCost(int currentCity, boolean manifestDestiny, boolean govSupportAgency, boolean bureauOfDomesticAffairs) {
            if (CITY_AVERAGE == -1) {
                CITY_AVERAGE = Locutus.imp().getDiscordDB().getCityAverage(40.8216);
                Logg.info("Loaded City Average: " + CITY_AVERAGE);
            }
            double top20AverageQuarter = CITY_AVERAGE * 0.25;
            double cost = Math.max(Math.pow(currentCity + 1, 2) * 100000, 100000 * Math.pow((currentCity + 1) - (top20AverageQuarter), 3) + 150000 * ((currentCity + 1) - (top20AverageQuarter)) + 75000);
            if (manifestDestiny) {
                double factor = 0.05;
                if (govSupportAgency) factor += 0.025;
                if (bureauOfDomesticAffairs) factor += 0.0125;
                cost *= (1 - factor);
            }
            return Math.max(1, cost);
        }

        public static String getCityUrl(int cityId) {
            return Settings.PNW_URL() + "/city/id=" + cityId;
        }
    }

    public static List<Integer> getNationsFromTable(String html, int tableIndex) {
        List<Integer> results = new ArrayList<>();

        Document dom = Jsoup.parse(html);
        Elements tables = dom.getElementsByClass("nationtable");
        int finalTableIndex = tableIndex == -1 ? tables.size() - 1 : tableIndex;
        if (finalTableIndex < 0 || finalTableIndex >= tables.size()) {
            throw new IllegalArgumentException("Unable to fetch table" + "\n" + html);
        }
        Element table = tables.get(finalTableIndex);
        Elements rows = table.getElementsByTag("tr");

        List<Element> subList = rows.subList(1, rows.size());

        for (Element element : subList) {
            Elements row = element.getElementsByTag("td");
            String url = row.get(1).selectFirst("a").attr("href");
            int id = Integer.parseInt(url.split("=")[1]);
            results.add(id);
        }

        return results;
    }

    public static String getAlert(Document document) {
        for (Element element : document.getElementsByClass("alert")) {
            if (element.hasClass("alert-info")) continue;
            String text = element.text();
            if (text.startsWith("Player Advertisement by ") || text.contains("Current Market Index")) {
                continue;
            }
            return text;
        }
        return null;
    }
    
    public static Set<Long> expandCoalition(Collection<Long> coalition) {
        Set<Long> extra = new LongOpenHashSet(coalition);
        for (Long id : coalition) {
            GuildDB other;
            if (id > Integer.MAX_VALUE) {
                other = Locutus.imp().getGuildDB(id);
            } else {
                other = Locutus.imp().getGuildDBByAA(id.intValue());
            }
            if (other != null) {
                for (Integer allianceId : other.getAllianceIds()) {
                    extra.add(allianceId.longValue());
                }
                extra.add(other.getGuild().getIdLong());
            }
        }
        return extra;
    }

    public static String getSphereName(int sphereId) {
        GuildDB db = Locutus.imp().getRootCoalitionServer();
        if (db != null) {
            for (String coalition : db.getCoalitionNames()) {
                Coalition namedCoal = Coalition.parse(coalition);
                if (namedCoal != null) continue;
                Set<Long> ids = db.getCoalitionRaw(coalition);
                if (ids.contains((long) sphereId)) {
                    return coalition;
                }
            }
        }
        return "sphere:" + getName(sphereId, true);
    }

    /**
     * Sum the nation transactions (assumes all transactions are valid and should be added)
     * @param tracked
     * @param transactionsEntries
     * @return
     */
    public static Map<DepositType, double[]> sumNationTransactions(DBNation nation, GuildDB guildDB, Set<Long> tracked, List<Map.Entry<Integer, Transaction2>> transactionsEntries, boolean forceIncludeExpired, boolean forceIncludeIgnored, Predicate<Transaction2> filter) {
        Map<DepositType, double[]> result = new EnumMap<>(DepositType.class);
        BiConsumer<Integer, Transaction2> forEach = createSumNationTransactions(nation, guildDB, tracked, forceIncludeExpired, forceIncludeIgnored, filter, result);
        long start = System.currentTimeMillis();
        for (Map.Entry<Integer, Transaction2> entry : transactionsEntries) {
            int sign = entry.getKey();
            Transaction2 record = entry.getValue();
            forEach.accept(sign, record);
        }
        long diff = System.currentTimeMillis() - start;
        if (diff > 50) {
            Logg.info("Summed " + transactionsEntries.size() + " transactions in " + diff + "ms");
        }
        return result;

    }

    public static Integer getSign(Transaction2 record, int nationId, Set<Long> tracked) {
//        Long otherId = null;
        if (((record.isSenderGuild() || record.isSenderAA()) && tracked.contains(record.sender_id))
                || (record.sender_type == 0 && record.sender_id == 0 && record.tx_id == -1)) {
//            otherId = record.sender_id;
        } else if (((record.isReceiverGuild() || record.isReceiverAA()) && tracked.contains(record.receiver_id))
                || (record.receiver_type == 0 && record.receiver_id == 0 && record.tx_id == -1)) {
//            otherId = record.receiver_id;
        } else {
            return null;
        }

        int sign;
        if (record.sender_id == nationId && record.sender_type == 1) {
            return 1;
        } else {
            return -1;
        }
    }

    public static BiConsumer<Integer, Transaction2> createSumNationTransactions(DBNation nation, GuildDB guildDB, Set<Long> tracked, boolean forceIncludeExpired, boolean forceIncludeIgnored, Predicate<Transaction2> filter, Map<DepositType, double[]> result) {
        long start = System.currentTimeMillis();

        boolean allowExpiryDefault = (guildDB.getOrNull(GuildKey.RESOURCE_CONVERSION) == Boolean.TRUE) || guildDB.getIdLong() == 790253684537688086L;
        long allowExpiryCutoff = 1635910300000L;
        Predicate<Transaction2> allowExpiry;
        if (forceIncludeExpired) allowExpiry = Predicates.alwaysFalse();
        else {
            allowExpiry = transaction2 ->
                    allowExpiryDefault || transaction2.tx_datetime > allowExpiryCutoff;
        }

        if (tracked == null) {
            tracked = guildDB.getTrackedBanks();
        }
        long forceRssConversionAfter;
        long allowConversionDefaultCutoff = 1735265627000L;
        boolean allowConversionDefault = guildDB.getOrNull(GuildKey.RESOURCE_CONVERSION) == Boolean.TRUE;
        if (allowConversionDefault && nation != null) {
            GuildDB delegate = guildDB.getDelegateServer();
            if (delegate == null) delegate = guildDB;
            Role role = Roles.RESOURCE_CONVERSION.toRole2(delegate);
            if (role != null) {
                allowConversionDefault = false;
                User user = nation.getUser();
                if (user != null) {
                    Member member = delegate.getGuild().getMember(user);
                    if (member != null) {
                        if (member.getUnsortedRoles().contains(role)) {
                            allowConversionDefault = true;
                        }
                    }
                }
            }
            if (allowConversionDefault) {
                Long convertDate = GuildKey.FORCE_RSS_CONVERSION.getOrNull(guildDB);
                if (convertDate != null) forceRssConversionAfter = convertDate;
                else {
                    forceRssConversionAfter = 0;
                }
            } else {
                forceRssConversionAfter = 0;
            }
        } else {
            forceRssConversionAfter = 0;
        }

        Function<ResourceType, Double> rateFunc = guildDB.getConversionRate(nation);

        Set<Long> finalTracked = tracked;
        boolean finalAllowConversionDefault = allowConversionDefault;
        return (sign, record) -> {
            if (filter != null && !filter.test(record)) return;

            boolean isOffshoreSender = (record.sender_type == 2 || record.sender_type == 3) && record.receiver_type == 1;

            boolean allowConversion = (finalAllowConversionDefault && record.tx_datetime > allowConversionDefaultCutoff) || (record.tx_id != -1 && isOffshoreSender);
            boolean allowArbitraryConversion = record.tx_id != -1 && isOffshoreSender;
            long forceConversionAfterFinal = record.isInternal() ? Long.MAX_VALUE : forceRssConversionAfter;

            Predicate<Transaction2> allowExpiryFinal = isOffshoreSender || record.isInternal() ? allowExpiry : Predicates.alwaysFalse();
            PW.processDeposit(record, guildDB, finalTracked, sign, result, record.resources, record.tx_datetime, allowExpiryFinal, allowConversion, allowArbitraryConversion, true, forceIncludeIgnored, rateFunc, forceConversionAfterFinal, start);
        };
    }

    public static boolean aboveMMR(String currentMMR, String requiredMMR) {
        requiredMMR = requiredMMR.toLowerCase();
        for (int i = 0; i < 4; i++) {
            int val1 = currentMMR.charAt(i) - '0';
            char char2 = currentMMR.charAt(i);

            int val2;
            if (char2 == 'X') {
                val2 = 0;
            } else {
                val2 = char2 - '0';
            }
            if (val1 < val2) return false;
        }
        return true;
    }

    public static boolean matchesMMR(String currentMMR, String requiredMMR) {
        requiredMMR = requiredMMR.toLowerCase().replace('X', '.');
        return currentMMR.matches(requiredMMR);
    }

    public static void processDeposit(Transaction2 record,
                                      GuildDB guildDB,
                                      Set<Long> tracked,
                                      int sign,
                                      Map<DepositType, double[]> result,
                                      double[] amount,
                                      long date,
                                      Predicate<Transaction2> allowExpiry,
                                      boolean allowConversion,
                                      boolean allowArbitraryConversion,
                                      boolean ignoreMarkedDeposits,
                                      boolean includeIgnored,
                                      Function<ResourceType, Double> rates,
                                      long forceConvertRssAfter,
                                      long now) {
        /*
        allowConversion sender is nation and alliance has conversion enabled
         */
        if (tracked == null) {
            tracked = guildDB.getTrackedBanks();
        }
        // TODO also update Grant.isNoteFromDeposits if this code is updated

        Map<DepositType, Object> notes3 = record.getNoteMap();
        DepositType type = DepositType.DEPOSIT;
        double decayFactor = 1;

        for (Map.Entry<DepositType, Object> entry2 : notes3.entrySet()) {
            DepositType tag2 = entry2.getKey();
            Object value2 = entry2.getValue();

            switch (tag2) {
                case NATION:
                case ALLIANCE:
                case GUILD:
                case ACCOUNT:
                    return;
                case IGNORE:
                    if (includeIgnored) {
                        if (value2 instanceof Number n && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && ignoreMarkedDeposits && !tracked.contains(n.longValue())) {
                            return;
                        }
                        continue;
                    }
                    return;
                case DEPOSIT:
                case TRADE:
                case WARCHEST:
                    if (value2 instanceof Number n && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && ignoreMarkedDeposits && !tracked.contains(n.longValue())) {
                        return;
                    }
                    type = DepositType.DEPOSIT;
                    continue;
                case RAWS:
                case TAX:
                    type = DepositType.TAX;
                    if (value2 instanceof Number n && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && ignoreMarkedDeposits && !tracked.contains(n.longValue())) {
                        return;
                    }
                    continue;
                case LOAN:
                case GRANT:
                    if (value2 instanceof Number n && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && ignoreMarkedDeposits && !tracked.contains(n.longValue())) {
                        return;
                    }
                    if (type == DepositType.DEPOSIT) {
                        type = DepositType.LOAN;
                    }
                    continue;
                case DECAY: {
                    if (allowExpiry.test(record) && value2 instanceof Number n) {
                        try {
                            long expire = n.longValue();
                            if (now > expire) {
                                return;
                            }
                            decayFactor = Math.min(decayFactor, 1 - (now - date) / (double) (expire - date));
                            type = DepositType.GRANT;
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                            type = DepositType.LOAN;
                        }
                    }
                    continue;
                }
                case EXPIRE: {
                    if (allowExpiry.test(record) && value2 instanceof Number n) {
                        try {
                            long expire = n.longValue();
                            if (now > expire) {
                                return;
                            }
                            type = DepositType.GRANT;
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                            type = DepositType.LOAN;
                        }
                    }
                    continue;
                }
                case CASH:
                    if (allowConversion) {
                        applyCashConversion(guildDB, allowArbitraryConversion, notes3, amount, value2, record, date, rates);
                        allowConversion = false;
                    }
                    continue;
            }
        }
        if (record.receiver_type != 1 && allowConversion && forceConvertRssAfter > 0 && date > forceConvertRssAfter) {
            applyCashConversion(guildDB, allowArbitraryConversion, notes3, amount, null, record, date, rates);
        }
        double[] rss = result.computeIfAbsent(type, f -> ResourceType.getBuffer());
        if (sign == 1 && decayFactor == 1) {
            ResourceType.add(rss, amount);
        } else if (sign == -1 && decayFactor == 1) {
            ResourceType.subtract(rss, amount);
        } else {
            double factor = decayFactor * sign;
            for (int i = 0; i < rss.length; i++) {
                rss[i] += amount[i] * factor;
            }
        }
    }

    private static void applyCashConversion(GuildDB guildDB,
            boolean allowArbitraryConversion,
            Map<DepositType, Object> notes3,
            double[] amount,
            Object value,
            Transaction2 record,
            long date,
            Function<ResourceType, Double> rates) {
        if (value != null && value instanceof Number n && n.doubleValue() == 0d) return;

        boolean hasNonCashRss = false;
        for (ResourceType rss : ResourceType.values) {
            if (rss == ResourceType.MONEY || rss == ResourceType.CREDITS) continue;
            if (amount[rss.ordinal()] >= 1) {
                hasNonCashRss = true;
                break;
            }
        }
        if (!hasNonCashRss) return;
        Double cashValue = null;
        Set<Byte> convert = null;

        Object rssNote = notes3.get(DepositType.RSS);
        if (rssNote instanceof Number n) {
            long rssId = n.longValue();
            convert = new ByteOpenHashSet();
            for (ResourceType rss : ResourceType.values) {
                if ((rssId & (1L << rss.ordinal())) != 0) {
                    convert.add((byte) rss.ordinal());
                }
            }
        }

        if (value instanceof Number n) {
            if (allowArbitraryConversion) {
                cashValue = n.doubleValue();
            }
        }

        if (cashValue == null) {
            Supplier<String> getHash = ArrayUtil.memorize(() -> Hashing.md5()
                    .hashString(Settings.INSTANCE.CONVERSION_SECRET + record.tx_id, StandardCharsets.UTF_8)
                    .toString());
            String hash = null;
            if (value instanceof Number n) {
                hash = getHash.get();
                if (record.note.contains(hash)) {
                    cashValue = n.doubleValue();
                }
            }

            if (cashValue == null) {
                long oneWeek = TimeUnit.DAYS.toMillis(7);
                TradeDB tradeDb = Locutus.imp().getTradeManager().getTradeDb();

                Set<Byte> convertCached = null;
                boolean setConvert = false;
                cashValue = 0d;
                for (int i = 0; i < amount.length; i++) {
                    ResourceType resource = ResourceType.values[i];
                    double amt = amount[i];
                    if (amt < 1) continue;
                    if (resource == ResourceType.MONEY) {
                        cashValue += amt;
                        continue;
                    }
                    if (convert != null && !convert.contains((byte) resource.ordinal())) continue;
                    double rate = rates.apply(resource);
                    if (rate <= 0) {
                        setConvert = true;
                        continue;
                    }
                    if (convertCached == null) {
                        convertCached = new ByteOpenHashSet();
                    }
                    convertCached.add((byte) resource.ordinal());
                    Double avg = tradeDb.getWeeklyAverage(resource, date, resource.getMarketValue());
                    if (avg != null) {
                        cashValue += amt * avg * rate;
                    }
                }
                if (convertCached == null) {
                    // No resources
                    return;
                }

                if (setConvert) {
                    convert = convertCached;
                    if (convert.isEmpty()) {
                        convert = new ByteOpenHashSet(1);
                        convert.add((byte) ResourceType.MONEY.ordinal());
                    }
                }
                {
                    if (hash == null) hash = getHash.get();

                    String note = record.note;
                    note = note.toLowerCase(Locale.ROOT).replaceAll("#cash[^ ]*", "");
                    note = note.replaceAll("#[a-f0-9]{32}", "");
                    note = note.replaceAll("\\s+", " ").trim();
                    note += " #" + hash + " " + "#cash=" + MathMan.format(cashValue).replace(",", "");

                    if (convert != null) {
                        long convertBits = 0;
                        for (byte b : convert) {
                            convertBits |= (1L << b);
                        }
                        note += " #rss=" + convertBits;
                    }

                    record.note = note.trim();

                    if (record.isInternal()) {
                        guildDB.updateNote(record.original_id, record.note);
                    } else {
                        Locutus.imp().getBankDB().addTransaction(record, false);
                    }
                }
            }
        }
        if (convert == null) {
            Arrays.fill(amount, 0);
        } else {
            for (byte b : convert) {
                ResourceType rss = ResourceType.values[b];
                amount[rss.ordinal()] = 0;
            }
        }
        amount[0] = cashValue;
    }

    public static double WAR_RANGE_MAX_MODIFIER = 2.50;
    public static double WAR_RANGE_MIN_MODIFIER = 0.75;

    public static double SPY_RANGE_MIN_MODIFIER = 0.4;
    public static double SPY_RANGE_MAX_MODIFIER = 2.5;

    /**
     * @param offensive (else defensive)
     * @param isWar (else spy)
     * @param isMin (else max)
     * @return
     */
    public static double getAttackRange(boolean offensive, boolean isWar, boolean isMin, double score) {
        long scoreInt = Math.round(score * 100);
        long range;
        if (offensive) {
            if (isWar) {
                if (isMin) {
                    range = Math.round(scoreInt * WAR_RANGE_MIN_MODIFIER);
                } else {
                    range = Math.round(scoreInt * WAR_RANGE_MAX_MODIFIER);
                }
            } else {
                if (isMin) {
                    range = Math.round(scoreInt * SPY_RANGE_MIN_MODIFIER);
                } else {
                    range = Math.round(scoreInt * SPY_RANGE_MAX_MODIFIER);
                }
            }
        } else {
            if (isWar) {
                if (isMin) {
                    range = Math.round(scoreInt / PW.WAR_RANGE_MAX_MODIFIER);
                } else {
                    range = Math.round(scoreInt / PW.WAR_RANGE_MIN_MODIFIER);
                }
            } else {
                if (isMin) {
                    range = Math.round(scoreInt / SPY_RANGE_MAX_MODIFIER);
                } else {
                    range = Math.round(scoreInt / SPY_RANGE_MIN_MODIFIER);
                }
            }
        }
        return range * 0.01;
    }

    public static Map.Entry<double[], String> createDepositEmbed(GuildDB db, NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuild, Map<DepositType, double[]> categorized, Boolean showCategories, double[] escrowed, long escrowExpire, boolean condenseFormat) {
        boolean withdrawIgnoresGrants = GuildKey.MEMBER_CAN_WITHDRAW_IGNORES_GRANTS.getOrNull(db) == Boolean.TRUE;

        boolean hasEscrowed = (escrowed != null && !ResourceType.isZero(escrowed) && ResourceType.convertedTotal(escrowed) > 0);

        StringBuilder response = new StringBuilder();

        List<String> footers = new ArrayList<>();
        if (!condenseFormat) {
            footers.add("value is based on current market prices");
        }

        double[] balance = ResourceType.getBuffer();
        double[] nonBalance = ResourceType.getBuffer();
        List<String> balanceNotes = new ArrayList<>(Arrays.asList("#deposit", "#tax", "#loan", "#grant", "#expire", "#decay"));

        List<String> excluded = new ArrayList<>(Arrays.asList("/escrow"));
        if (withdrawIgnoresGrants) {
            balanceNotes.remove("#expire");
            balanceNotes.remove("#decay");
            excluded.add("#expire");
            excluded.add("#decay");
        }

        for (Map.Entry<DepositType, double[]> entry : categorized.entrySet()) {
            DepositType type = entry.getKey();
            double[] current = entry.getValue();
            if (!withdrawIgnoresGrants || type != DepositType.GRANT) {
                ResourceType.add(balance, current);
            } else {
                ResourceType.add(nonBalance, current);
            }
        }

        if (showCategories) {
            if (categorized.containsKey(DepositType.DEPOSIT)) {
                response.append("**`#DEPOSIT`** worth $" + MathMan.format(ResourceType.convertedTotal(categorized.get(DepositType.DEPOSIT))));
                response.append("\n```").append(ResourceType.toString(categorized.get(DepositType.DEPOSIT))).append("```\n");
            }
            if (categorized.containsKey(DepositType.TAX)) {
                response.append("**`#TAX`** worth $" + MathMan.format(ResourceType.convertedTotal(categorized.get(DepositType.TAX))));
                response.append("\n```").append(ResourceType.toString(categorized.get(DepositType.TAX))).append("```\n");
            } else if (nationOrAllianceOrGuild.isNation()) {
                footers.add("No tax records are added to deposits");
            }
            if (categorized.containsKey(DepositType.LOAN)) {
                response.append("**`#LOAN/#GRANT`** worth $" + MathMan.format(ResourceType.convertedTotal(categorized.get(DepositType.LOAN))));
                response.append("\n```").append(ResourceType.toString(categorized.get(DepositType.LOAN))).append("```\n");
            }
            if (categorized.containsKey(DepositType.GRANT)) {
                response.append("**`#EXPIRE`** worth $" + MathMan.format(ResourceType.convertedTotal(categorized.get(DepositType.GRANT))));
                response.append("\n```").append(ResourceType.toString(categorized.get(DepositType.GRANT))).append("```\n");
            }
            if (hasEscrowed) {
                response.append("**" + CM.escrow.withdraw.cmd.toSlashMention() + ":** worth: $" + MathMan.format(ResourceType.convertedTotal(escrowed)));
                if (escrowExpire > 0) {
                    response.append(" expires: " + DiscordUtil.timestamp(escrowExpire, null));
                }
                response.append("\n```").append(ResourceType.toString(escrowed)).append("``` ");
            }
            if (categorized.size() > 1) {
                response.append("**Balance:** (`" + StringMan.join(balanceNotes, "`|`") + "`) worth: $" + MathMan.format(ResourceType.convertedTotal(balance)) + ")");
                response.append("\n```").append(ResourceType.toString(balance)).append("``` ");
            } else if (categorized.isEmpty()){
                response.append("**No balance found**\n");
            }
        } else {
            String prefix = condenseFormat ? "**" : "## ";
            String suffix = condenseFormat ? "**" : "";
            response.append(prefix + "Balance:" + suffix);
            if (condenseFormat) {
                response.append(" worth: `$" + MathMan.format(ResourceType.convertedTotal(balance)) + "`\n");
                response.append("```" + ResourceType.toString(balance) + "``` ");
            } else {
                response.append("\n").append(ResourceType.resourcesToFancyString(balance)).append("\n");
            }
            response.append("**Includes:** `" + StringMan.join(balanceNotes, "`, `")).append("`\n");
            response.append("**Excludes:** `" + StringMan.join(excluded, "`, `")).append("`\n");

            if (hasEscrowed) {
                response.append("\n" + prefix + CM.escrow.withdraw.cmd.toSlashMention() + ":" + suffix);
                if (condenseFormat) {
                    response.append(" worth: `$" + MathMan.format(ResourceType.convertedTotal(escrowed)) + "`\n");
                    response.append("```" + ResourceType.toString(escrowed) + "``` ");
                } else {
                    response.append("\n").append(ResourceType.resourcesToFancyString(escrowed)).append("\n");
                }
                if (escrowExpire > 0) {
                    response.append("- expires: " + DiscordUtil.timestamp(escrowExpire, null) + "\n");
                }
            }

            if (!ResourceType.isZero(nonBalance)) {
                response.append("\n" + prefix + "Expiring Debt:" + suffix + "\n");
                response.append("In addition to your balance, you owe the following:\n");
                response.append("```\n" + ResourceType.toString(nonBalance)).append("```\n- worth: $" + MathMan.format(ResourceType.convertedTotal(nonBalance)) + "\n");
            }
        }
        return KeyValue.of(balance, response.toString());
    }

    public static Set<DBNation> getNationsSnapshot(Collection<DBNation> nations, String filterStr, Long snapshotDate, GuildDB db) {
        return getNationsSnapshot(nations, filterStr, snapshotDate, db == null ? null : db.getGuild());
    }

    public static Set<DBNation> getNationsSnapshot(Collection<DBNation> nations, String filterStr, Long snapshotDate, Guild guild) {
        return getNationsSnapshot(nations, filterStr, snapshotDate, guild, false);
    }

    public static Set<DBNation> getNationsSnapshot(Collection<DBNation> nations, String filterStr, Long snapshotDate, Guild guild, boolean loadVm) {
        if (snapshotDate == null) return nations instanceof Set<DBNation> ? (Set<DBNation>) nations : new ObjectOpenHashSet<>(nations);
        NationPlaceholders ph = Locutus.cmd().getV2().getNationPlaceholders();

        DataDumpParser dumper = Locutus.imp().getDataDumper(true);
        long day = TimeUtil.getDay(snapshotDate);
        try {
            ValueStore store = ph.createLocals(guild, null, null);
            NationsFileSnapshot snapshot = dumper.getSnapshotDelegate(day, true, loadVm);
            Set<DBNation> result = ph.parseSet(store, filterStr, snapshot, true);
            return result;
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static Integer parseAllianceId(String arg) {
        if (arg.length() == 1) return null;
        String lower = arg.toLowerCase();
        if (lower.startsWith("aa:")) arg = arg.substring(3);
        else if (lower.startsWith("alliance:")) arg = arg.substring(9);
        if (arg.charAt(0) == '"' && arg.charAt(arg.length() - 1) == '"') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.equalsIgnoreCase("none")) {
            return 0;
        }
        if (arg.startsWith(Settings.PNW_URL() + "/alliance/id=") || arg.startsWith(Settings.PNW_URL() + "//alliance/id=") || arg.startsWith(Settings.PNW_URL() + "/alliance/id=")) {
            String[] split = arg.split("=");
            if (split.length == 2) {
                arg = split[1].replaceAll("/", "");
            }
        }
        if (MathMan.isInteger(arg)) {
            try {
                return Integer.parseInt(arg);
            } catch (NumberFormatException e) {}
        }
        {
            DBAlliance alliance = Locutus.imp().getNationDB().getAllianceByName(arg);
            if (alliance != null) {
                return alliance.getAlliance_id();
            }
        }
        if (arg.contains("=HYPERLINK") && arg.contains("alliance/id=")) {
            String regex = "alliance/id=([0-9]+)";
            Matcher m = Pattern.compile(regex).matcher(arg);
            m.find();
            arg = m.group(1);
            return Integer.parseInt(arg);
        }
        return null;
    }

    public static String sharesToString(Map<Exchange, Long> shares) {
        Map<String, String> resultMap = new LinkedHashMap<>();
        for (Map.Entry<Exchange, Long> entry : shares.entrySet()) {
            resultMap.put(entry.getKey().symbol, MathMan.format(entry.getValue() / 100d));
        }
        return StringMan.getString(resultMap);
    }

    public static double[] normalize(double[] resources) {
        return ResourceType.resourcesToArray(PW.normalize(ResourceType.resourcesToMap(resources)));
    }

    public static Map<ResourceType, Double> normalize(Map<ResourceType, Double> resources) {
        resources = new LinkedHashMap<>(resources);
        double total = ResourceType.convertedTotal(resources);
        if (total == 0) return new HashMap<>();
        if (total < 0) {
            return new HashMap<>();
        }

        double negativeTotal = 0;

        Iterator<Map.Entry<ResourceType, Double>> iter = resources.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<ResourceType, Double> entry = iter.next();
            if (entry.getValue() < 0) {
                negativeTotal += Locutus.imp().getTradeManager().getHigh(entry.getKey()) * entry.getValue() * -1;
                iter.remove();
            }
        }
        double postiveTotal = ResourceType.convertedTotal(resources);

        double factor = Math.max(0, Math.min(1, total / postiveTotal));
//            factor = Math.min(factor, postiveTotal / (negativeTotal + postiveTotal));

        for (ResourceType type : ResourceType.values()) {
            Double value = resources.get(type);
            if (value == null || value == 0) continue;

            resources.put(type, value * factor);
        }
        return resources;
    }

    private static double[] LAND_COST_CACHE = null;

    public static Map<ResourceType, Double> adapt(AllianceBankContainer bank) {
        Map<ResourceType, Double> totals = new LinkedHashMap<ResourceType, Double>();
        String json = WebUtil.GSON.toJson(bank);
        JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
        for (ResourceType type : ResourceType.values) {
            JsonElement amt = obj.get(type.name().toLowerCase());
            if (amt != null) {
                totals.put(type, amt.getAsDouble());
            }
        }
        return totals;
    }

    private static final int[] INFRA_COST_FAST_CACHE;

    static {
        int max = 4000;
        int min = 30;
        int minCents = min * 100;
        INFRA_COST_FAST_CACHE = new int[(max - min) * 100 + 1];
        for (int i = minCents; i <= max * 100; i++) {
            double x = (i * 0.01) - 10d;
            int cost = Math.toIntExact(Math.round(100 * (300d + (Math.pow(x, (2.2d))) * 0.00140845070422535211267605633803)));
            INFRA_COST_FAST_CACHE[i - minCents] = cost;
        }
    }

    public static <T> T withLogin(Callable<T> task, Auth auth) {
        synchronized (auth)
        {
            try {
                auth.login(false);
                return task.call();
            } catch (Exception e) {
                AlertUtil.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

    public static double[] multiply(double[] a, double factor) {
        for (int i = 0; i < a.length; i++) {
            a[i] *= factor;
        }
        return a;
    }

    public static <T extends Number> Map<ResourceType, T> multiply(Map<ResourceType, T> a, T value) {
        HashMap<ResourceType, T> copy = new HashMap<>(a);
        for (Map.Entry<ResourceType, T> entry : copy.entrySet()) {
            entry.setValue(MathMan.multiply(entry.getValue(), value));
        }
        return copy;
    }

    public static String parseDom(Element dom, String clazz) {
        for (Element element : dom.getElementsByClass(clazz)) {
            String text = element.text();
            if (text.startsWith("Player Advertisement by ")) {
                continue;
            }
            return element.text();
        }
        return null;
    }

    public static double[] getRevenue(double[] profitBuffer, int turns, DBNation nation, Collection<JavaCity> cities, boolean militaryUpkeep, boolean tradeBonus, boolean bonus, boolean noFood, boolean noPower, double treasureBonus) {
        double rads = nation.getRads();
        boolean atWar = nation.getNumWars() > 0;
        long date = -1L;
        return getRevenue(profitBuffer, turns, date, nation, cities, militaryUpkeep, tradeBonus, bonus, noFood, noPower, rads, atWar, treasureBonus);
    }

    public static double[] getRevenue(double[] profitBuffer, int turns, long date, DBNation nation, Collection<JavaCity> cities, boolean militaryUpkeep, boolean tradeBonus, boolean bonus, boolean noFood, boolean noPower, double rads, boolean atWar, double treasureBonus) {
        if (profitBuffer == null) profitBuffer = new double[ResourceType.values.length];

        Continent continent = nation.getContinent();
        double grossModifier = nation.getGrossModifier(noFood) + treasureBonus;

        // Project revenue
//        if (checkRpc && nation.getCities() <= 15 && nation.hasProject(Projects.ACTIVITY_CENTER)) {
////            for (ResourceType type : ResourceType.values) {
////                if (type.isRaw() && type.getBuilding().canBuild(nation.getContinent())) {
////                    // profitBuffer[type.ordinal()] += turns * (Math.min(nation.getCities(), 10));
////                }
////            }
//        }

        // city revenue
        for (JavaCity build : cities) {
            int numCities = bonus ? nation.getCities() : 21;
            profitBuffer = build.profit(continent, rads, date, nation::hasProject, profitBuffer, numCities, grossModifier, noPower, 12);
        }

        // trade revenue
        if (tradeBonus) {
            profitBuffer[0] += nation.getColor().getTurnBonus() * turns * grossModifier;
        }

        // Add military upkeep
        if (militaryUpkeep && !nation.hasUnsetMil()) {
            double factor = nation.getMilitaryUpkeepFactor() * turns / 12;
            int research = nation.getResearchBits();

            for (MilitaryUnit unit : MilitaryUnit.values) {
                int amt = nation.getUnits(unit);
                if (amt == 0) continue;
                unit.addUpkeep(profitBuffer, amt, atWar, research, -factor);
            }
        }

        return profitBuffer;
    }

    public static String getMarkdownUrl(int nationId, boolean isAA) {
        return MarkupUtil.markdownUrl(PW.getName(nationId, isAA), "<" + PW.getUrl(nationId, isAA) + ">");
    }

    public static int parseTaxId(String url) {
        String regex = "tax_id[=:]([0-9]+)";
        Matcher matcher = Pattern.compile(regex).matcher(url.toLowerCase(Locale.ROOT));
        if (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            return id;
        }
        throw new IllegalArgumentException("Not a valid tax url: `" + url + "`");
    }
    
    public static String getName(long nationOrAllianceId, boolean isAA) {
        if (isAA) {
            String name = Locutus.imp().getNationDB().getAllianceName((int) nationOrAllianceId);
            return name != null ? name : nationOrAllianceId + "";
        } else if (Math.abs(nationOrAllianceId) < Integer.MAX_VALUE) {
            DBNation nation = Locutus.imp().getNationDB().getNationById((int) nationOrAllianceId);
            return nation != null ? nation.getNation() : nationOrAllianceId + "";
        } else {
            Guild guild = Locutus.imp().getDiscordApi().getGuildById(Math.abs(nationOrAllianceId));
            return guild != null ? guild.getName() : nationOrAllianceId + "";
        }
    }

    public static String getUrl(int nationOrAllianceId, boolean isAA) {
        return isAA ? getAllianceUrl(nationOrAllianceId) : getNationUrl(nationOrAllianceId);
    }

    public static String getNationUrl(int nationId) {
        return Settings.PNW_URL() + "/nation/id=" + nationId;
    }

    public static String getAllianceUrl(int cityId) {
        return Settings.PNW_URL() + "/alliance/id=" + cityId;
    }

    public static String getTradeUrl(ResourceType type, boolean isBuy) {
        String url = Settings.PNW_URL() + "/index.php?id=90&display=world&resource1=%s&buysell=" + (isBuy ? "buy" : "sell") + "&ob=price&od=DEF";
        return String.format(url, type.name().toLowerCase());
    }

    public static BiFunction<Integer, Integer, Integer> getIsNationsInCityRange(Collection<DBNation> attackers) {
        int[] cityRange = new int[50];
        for (DBNation attacker : attackers) {
            cityRange[attacker.getCities()]++;
        }
        int total = 0;
        for (int i = 0; i < cityRange.length; i++) {
            total += cityRange[i];
            cityRange[i] = total;
        }
        return new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer min, Integer max) {
                int minVal = min == 0 ? 0 : cityRange[Math.min(cityRange.length - 1, min - 1)];
                int maxVal = cityRange[Math.min(cityRange.length - 1, max)];
                return maxVal - minVal;
            }
        };
    }

    public static double estimateScore(NationDB db, DBNation nation) {
        return estimateScore(db, nation, null, null, null, null, null);
    }

    public enum ScoreType {
        TOTAL(0),
        BASE(1),
        RESEARCH(1),
        PROJECTS(1),
        CITIES(1),
        INFRA(1),
        MILITARY(1),
        SOLDIER(2),
        TANK(2),
        AIRCRAFT(2),
        SHIP(2),
        MISSILE(2),
        NUKE(2);

        private final int tier;

        ScoreType(int tier) {
            this.tier = tier;
        }

        public int getTier() {
            return tier;
        }
    }

    public static Map<ScoreType, Double> scoreBreakdown(NationDB db, DBNation nation, MMRDouble mmr, Double infra, Integer projects, Integer cities, Integer researchBits) {
        Map<ScoreType, Double> result = new EnumMap<>(ScoreType.class);

        if (projects == null) projects = nation.getNumProjects();
        if (researchBits == null) researchBits = nation.getResearchBits();
        if (infra == null) {
            infra = 0d;
            for (DBCity city : db.getCitiesV3(nation.getNation_id()).values()) {
                infra += city.getInfra();
            }
        }
        if (cities == null) cities = nation.getCities();

        double base = 10;
        result.put(ScoreType.BASE, base);

        double researchScore = 0;
        if (researchBits > 0) {
            for (Research rs : Research.values) {
                int level = rs.getLevel(researchBits);
                if (level > 0) {
                    researchScore += rs.getScore() * level;
                }
            }
        }
        result.put(ScoreType.RESEARCH, MathMan.round2(researchScore));

        double projectScore = projects * Projects.getScore();
        result.put(ScoreType.PROJECTS, MathMan.round2(projectScore));

        double cityScore = (cities - 1) * 100;
        result.put(ScoreType.CITIES, MathMan.round2(cityScore));

        double infraScore = infra / 40d;
        result.put(ScoreType.INFRA, MathMan.round2(infraScore));

        MilitaryUnit[] units = {
            MilitaryUnit.SOLDIER,
            MilitaryUnit.TANK,
            MilitaryUnit.AIRCRAFT,
            MilitaryUnit.SHIP,
            MilitaryUnit.MISSILE,
            MilitaryUnit.NUKE
        };
        ScoreType[] unitScoreTypes = {
            ScoreType.SOLDIER,
            ScoreType.TANK,
            ScoreType.AIRCRAFT,
            ScoreType.SHIP,
            ScoreType.MISSILE,
            ScoreType.NUKE
        };
        double militaryScore = 0;
        for (int i = 0; i < units.length; i++) {
            MilitaryUnit unit = units[i];
            ScoreType scoreType = unitScoreTypes[i];
            int amt;
            if (mmr != null && unit.getBuilding() != null) {
                amt = (int) (mmr.getPercent(unit) * unit.getBuilding().getUnitCap() * unit.getBuilding().cap(Predicates.alwaysFalse()) * cities);
            } else {
                amt = nation.getUnits(unit);
            }
            if (amt > 0) {
                double score = MathMan.round2(unit.getScore(amt));
                militaryScore += score;
                result.put(scoreType, score);
            } else {
                result.put(scoreType, 0d);
            }
        }
        result.put(ScoreType.MILITARY, MathMan.round2(militaryScore));

        double total = base + researchScore + projectScore + cityScore + infraScore + militaryScore;
        result.put(ScoreType.TOTAL, MathMan.round2(total));

        return result;
    }

    public static double estimateScore(NationDB db, DBNation nation, MMRDouble mmr, Double infra, Integer projects, Integer cities, Integer researchBits) {
        if (projects == null) projects = nation.getNumProjects();
        if (researchBits == null) researchBits = nation.getResearchBits();
        if (infra == null) {
            infra = 0d;
            for (DBCity city : db.getCitiesV3(nation.getNation_id()).values()) {
                infra += city.getInfra();
            }
        }
        if (cities == null) cities = nation.getCities();

        double base = 10;

        if (researchBits > 0) {
            for (Research rs : Research.values) {
                int level = rs.getLevel(researchBits);
                if (level > 0) {
                    base += rs.getScore() * level;
                }
            }
        }

        base += projects * Projects.getScore();
        base += (cities - 1) * 100;
        base += infra / 40d;
        for (MilitaryUnit unit : MilitaryUnit.values) {
            if (unit == MilitaryUnit.INFRASTRUCTURE) continue;
            int amt;
            if (mmr != null && unit.getBuilding() != null) {
                amt = (int) (mmr.getPercent(unit) * unit.getBuilding().getUnitCap() * unit.getBuilding().cap(Predicates.alwaysFalse()) * cities);
            } else {
                amt = nation.getUnits(unit);
            }
            if (amt > 0) {
                base += unit.getScore(amt);
            }
        }
        return base;
    }

    public static BiFunction<Double, Double, Integer> getIsNationsInScoreRange(Collection<DBNation> attackers) {
        int minScore = Integer.MAX_VALUE;
        int maxScore = 0;
        for (DBNation attacker : attackers) {
            minScore = (int) Math.min(minScore, attacker.getScore() * 0.75);
            maxScore = (int) Math.max(maxScore, attacker.getScore() / 0.75);
        }
        int[] scoreRange = new int[maxScore + 1];
        for (DBNation attacker : attackers) {
            scoreRange[(int) attacker.getScore()]++;
        }
        int total = 0;
        for (int i = 0; i < scoreRange.length; i++) {
            total += scoreRange[i];
            scoreRange[i] = total;
        }
        return new BiFunction<Double, Double, Integer>() {
            @Override
            public Integer apply(Double min, Double max) {
                max = Math.max(0, Math.min(scoreRange.length - 1, max));
                min = Math.max(0, Math.min(scoreRange.length - 1, min));
                return scoreRange[max.intValue()] - scoreRange[min.intValue()];
            }
        };
    }

    public static BiFunction<Double, Double, Double> getXInRange(Collection<DBNation> attackers, Function<DBNation, Double> valueFunc) {
        int minScore = Integer.MAX_VALUE;
        int maxScore = 0;
        for (DBNation attacker : attackers) {
            minScore = (int) Math.min(minScore, attacker.getScore() * 0.75);
            maxScore = (int) Math.max(maxScore, attacker.getScore() / 0.75);
        }
        double[] scoreRange = new double[maxScore + 1];
        for (DBNation attacker : attackers) {
            scoreRange[(int) attacker.getScore()] += valueFunc.apply(attacker);
        }
        double total = 0;
        for (int i = 0; i < scoreRange.length; i++) {
            total += scoreRange[i];
            scoreRange[i] = total;
        }
        return new BiFunction<Double, Double, Double>() {
            @Override
            public Double apply(Double min, Double max) {
                max = Math.min(scoreRange.length - 1, max);
                return scoreRange[(int) Math.ceil(max)] - scoreRange[min.intValue()];
            }
        };
    }

    public static BiFunction<Double, Double, Integer> getIsNationsInSpyRange(Collection<DBNation> attackers) {
        int minScore = Integer.MAX_VALUE;
        int maxScore = 0;
        for (DBNation attacker : attackers) {
            minScore = (int) Math.min(minScore, attacker.getScore() * 0.4);
            maxScore = (int) Math.max(maxScore, attacker.getScore() * 1.5);
        }
        int[] scoreRange = new int[maxScore + 1];
        for (DBNation attacker : attackers) {
            scoreRange[(int) attacker.getScore()]++;
        }
        int total = 0;
        for (int i = 0; i < scoreRange.length; i++) {
            total += scoreRange[i];
            scoreRange[i] = total;
        }
        return new BiFunction<Double, Double, Integer>() {
            @Override
            public Integer apply(Double min, Double max) {
                int minVal = min == 0 ? 0 : scoreRange[Math.min(scoreRange.length - 1, min.intValue() - 1)];
                int maxVal = scoreRange[Math.min(scoreRange.length - 1, max.intValue())];
                return maxVal - minVal;
            }
        };
    }

    public static double getOdds(double attStrength, double defStrength, int success) {
        attStrength = Math.pow(attStrength, 0.75);
        defStrength = Math.pow(defStrength, 0.75);

        double attMin = attStrength * 0.4;
        double attMax = attStrength;
        double defMin = defStrength * 0.4;
        double defMax = defStrength;

        // Skip formula for common cases (for performance)
        if (attStrength <= 0) return success == 0 ? 1 : 0;
        if (defStrength * 2.5 <= attStrength) return success == 3 ? 1 : 0;
        if (attMax <= defMin || defMax <= attMin) return success == 0 ? 1 : 0;

        double sampleSpace = (attMax - attMin) * (defMax - defMin);
        double overlap = Math.min(attMax, defMax) - Math.max(attMin, defMin);
        double p = (overlap * overlap * 0.5) / sampleSpace;
        if (attStrength > defStrength) p = 1 - p;

        if (p <= 0) return 0;
        if (p >= 1) return 1;

        int k = success;
        int n = 3;

        double odds = Math.pow(p, k) * Math.pow(1 - p, n - k);
        double npr = MathMan.factorial(n) / (double) (MathMan.factorial(k) * MathMan.factorial(n - k));
        return odds * npr;
    }


    public static Set<Integer> parseAlliances(GuildDB db, String arg) {
        Set<Integer> aaIds = new IntLinkedOpenHashSet();
        for (String allianceName : arg.split(",")) {
            Set<Integer> coalition = db == null ? Collections.emptySet() : db.getCoalition(allianceName);
            if (!coalition.isEmpty()) aaIds.addAll(coalition);
            else {
                Integer aaId = PW.parseAllianceId(allianceName);
                if (aaId == null) throw new IllegalArgumentException("Unknown alliance: `" + allianceName + "`");
                aaIds.add(aaId);
            }
        }
        return aaIds;
    }

    public static <E extends Enum<E>, V extends Number> Map<E, V> parseEnumMap(String arg, Class<E> enumClass, Class<V> valueClass) {
        if (arg.endsWith("},")) {
            arg = arg.substring(0, arg.length() - 1);
        }
        if (arg.endsWith(",}")) {
            arg = arg.substring(0, arg.length() - 2) + "}";
        } else if (arg.endsWith(",")) {
            arg = arg.substring(0, arg.length() - 1);
        }
        arg = arg.trim();
        if (!arg.contains(":") && !arg.contains("=")) arg = arg.replaceAll("[ ]+", ":");
        arg = arg.replace('=', ':').toUpperCase();
        arg = arg.replaceAll("([A-Z]+:[0-9.]+) ([A-Z]+:[0-9.]+)", "$1,$2");
        arg = arg.replace(" ", "");
        arg = arg.replaceAll("([0-9]),([0-9])", "$1$2").toUpperCase();
        for (E unit : enumClass.getEnumConstants()) {
            String name = unit.name();
            arg = arg.replace(name.toUpperCase() + ":", name + ":");
        }

        double sign = 1;
        if (arg.charAt(0) == '-') {
            sign = -1;
            arg = arg.substring(1);
        }
        int preMultiply = arg.indexOf("*{");
        int postMultiply = arg.indexOf("}*");
        if (preMultiply != -1) {
            String[] split = arg.split("\\*\\{", 2);
            arg = "{" + split[1];
            sign *= Double.parseDouble(split[0]);
        }
        if (postMultiply != -1) {
            String[] split = arg.split("\\}\\*", 2);
            arg = split[0] + "}";
            sign *= Double.parseDouble(split[1]);
        }

        Type type = com.google.gson.reflect.TypeToken.getParameterized(Map.class, enumClass, valueClass).getType();
        if (arg.charAt(0) != '{' && arg.charAt(arg.length() - 1) != '}') {
            arg = "{" + arg + "}";
        }
        Map<E, V> result = WebUtil.GSON.fromJson(arg, type);
        if (result.containsKey(null)) {
            throw new IllegalArgumentException("Invalid type specified in map: `" + arg + "`. Options: " + StringMan.join(enumClass.getEnumConstants(), ", ") + " or `{}`");
        }
        if (sign != 1) {
            for (Map.Entry<E, V> entry : result.entrySet()) {
                entry.setValue(multiply(entry.getValue(), sign, valueClass));
            }
        }
        return result;
    }

    private static <V extends Number> V multiply(V value, double factor, Class<V> valueClass) {
        if (valueClass == Long.class) {
            return valueClass.cast((long) (value.longValue() * factor));
        } else if (valueClass == Double.class) {
            return valueClass.cast(value.doubleValue() * factor);
        } else if (valueClass == Integer.class) {
            return valueClass.cast((int) (value.intValue() * factor));
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + valueClass);
        }
    }

    public static Map<MilitaryUnit, Long> parseUnits(String arg) {
        return parseEnumMap(arg, MilitaryUnit.class, Long.class);
    }

    public static Map<String, String> parseMap(String arg) {
        if (arg.charAt(0) != '{' && arg.charAt(arg.length() - 1) != '}') {
            arg = arg.trim();
            if (!arg.contains(":") && !arg.contains("=")) arg = arg.replaceAll("[ ]+", ":");
            arg = arg.replace(" ", "").replace('=', ':');
            if (arg.charAt(0) != '{' && arg.charAt(arg.length() - 1) != '}') {
                arg = "{" + arg + "}";
            }
        }
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> result = WebUtil.GSON.fromJson(arg, type);
        if (result.containsKey(null)) {
            throw new IllegalArgumentException("Invalid type specified in map: `" + arg + "`");
        }
        return result;
    }

    public static double[] capManuFromRaws(double[] revenue, double[] totalRss) {
        for (ResourceType type : ResourceType.values) {
            double amt = revenue[type.ordinal()];
            if (amt > 0 && type.isManufactured()) {
                double required = amt * type.getBaseInput();
                for (ResourceType input : type.getInputs()) {
                    double inputAmt = totalRss[input.ordinal()];
                    double revenueAmt = revenue[input.ordinal()];
                    if (revenueAmt > -required) {
                        inputAmt += revenueAmt + required;
                    }
                    double cap = inputAmt / type.getBaseInput();
                    if (amt > cap) {
                        revenue[type.ordinal()] = cap;
                    }
                }
            }
        }
        return revenue;
    }

    public static String getTaxUrl(int taxId) {
        return String.format(Settings.PNW_URL() + "/index.php?id=15&tax_id=%s", taxId);
    }

}