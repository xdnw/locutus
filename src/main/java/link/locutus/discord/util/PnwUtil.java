package link.locutus.discord.util;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.stock.Exchange;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.trade.Offer;
import com.google.common.hash.Hashing;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.locutus.discord.apiv1.domains.subdomains.AllianceBankContainer;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PnwUtil {
    public static Map<ResourceType, Double> roundResources(Map<ResourceType, Double> resources) {
        HashMap<ResourceType, Double> copy = new HashMap<>(resources);
        for (Map.Entry<ResourceType, Double> entry : copy.entrySet()) {
            entry.setValue(Math.round(entry.getValue() * 100.0) / 100.0);
        }
        return copy;
    }

//    @Deprecated
//    public static ApiRecord apiKeyStats(String key, boolean cached) {
//        Type type = new com.google.gson.reflect.TypeToken<ApiRecord>() {}.getType();
//        Gson gson = new Gson();
//        if (cached) {
//            long id = new BigInteger(key, 16).longValue();
//            ByteBuffer jsonBytes = Locutus.imp().getDiscordDB().getInfo(DiscordMeta.API_KEY, id);
//            if (jsonBytes != null) {
//                String json = new String(jsonBytes.array(), StandardCharsets.UTF_8);
//                ApiRecord record = gson.fromJson(json, type);
//                return record;
//            }
//        }
//        PoliticsAndWarV2 api = new PoliticsAndWarV2(key, Settings.INSTANCE.TEST, false);
//        ApiKeyDetails stats = api.getV3().getApiKeyStats();
//        JsonObject obj = new JsonObject();
//        obj.addProperty("api_key", stats.getKey());
//        obj.addProperty("nation_id", stats.getNation().getId());
//        obj.addProperty("daily_requests_maximum", stats.getMax_requests());
//        obj.addProperty("daily_requests_used", stats.getRequests());
//
////        String json = api.read(QueryURLV2.BANK_RECORDS, Settings.INSTANCE.NATION_ID + "", null, false);
////        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
////        JsonObject request = obj.getAsJsonObject("api_request");
////        JsonObject details = request.getAsJsonObject("api_key_details");
//
//        String json = obj.toString();
//        long id = new BigInteger(key, 16).longValue();
//        ApiRecord record = gson.fromJson(obj, type);
//
//        Locutus.imp().getDiscordDB().setInfo(DiscordMeta.API_KEY, id, json.getBytes(StandardCharsets.UTF_8));
//        return record;
//    }

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
        Set<Long> extra = new HashSet<>(coalition);
        for (Long id : coalition) {
            GuildDB other;
            if (id > Integer.MAX_VALUE) {
                other = Locutus.imp().getGuildDB(id);
            } else {
                other = Locutus.imp().getGuildDBByAA(id.intValue());
            }
            if (other != null) {
                Integer allianceId = other.getOrNull(GuildDB.Key.ALLIANCE_ID);
                if (allianceId != null) extra.add(allianceId.longValue());
                extra.add(other.getGuild().getIdLong());
            }
        }
        return extra;
    }

    public static String getSphereName(int sphereId) {
        GuildDB db = Locutus.imp().getRootCoalitionServer();
        if (db != null) {
            for (Map.Entry<String, Set<Integer>> entry : db.getCoalitions().entrySet()) {
                Coalition namedCoal = Coalition.getOrNull(entry.getKey());
                if (namedCoal != null) continue;
                if (entry.getValue().contains(sphereId)) {
                    return entry.getKey();
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
    public static Map<DepositType, double[]> sumNationTransactions(GuildDB guildDB, Set<Long> tracked, List<Map.Entry<Integer, Transaction2>> transactionsEntries) {
        Map<DepositType, double[]> result = new HashMap<>();

        boolean allowExpiryDefault = (guildDB.getOrNull(GuildDB.Key.RESOURCE_CONVERSION) == Boolean.TRUE) || guildDB.getIdLong() == 790253684537688086L;
        long allowExpiryCutoff = 1635910300000L;
        Predicate<Transaction2> allowExpiry = transaction2 ->
                allowExpiryDefault || transaction2.tx_datetime > allowExpiryCutoff;

        if (tracked == null) {
            tracked = new HashSet<>();
            tracked.addAll(guildDB.getCoalitionRaw(Coalition.TRACK_DEPOSITS));
            tracked.add(guildDB.getGuild().getIdLong());
            Integer aaId = guildDB.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId != null) tracked.add(aaId.longValue());
            tracked = expandCoalition(tracked);
        }

        for (Map.Entry<Integer, Transaction2> entry : transactionsEntries) {
            int sign = entry.getKey();
            Transaction2 record = entry.getValue();

            boolean isOffshoreSender = (record.sender_type == 2 || record.sender_type == 3) && record.receiver_type == 1;

            boolean allowConversion = record.tx_id != -1 && isOffshoreSender;
            boolean allowArbitraryConversion = record.tx_id != -1 && isOffshoreSender;

            PnwUtil.processDeposit(record, guildDB, tracked, sign, result, record.resources, record.note, record.tx_datetime, allowExpiry, allowConversion, allowArbitraryConversion, true);
        }
        return result;
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

    public static Map<String, String> parseTransferHashNotes(String note) {
        if (note == null || note.isEmpty()) return Collections.emptyMap();

        Map<String, String> result = new LinkedHashMap<>();

        String[] split = note.split("(?=#)");
        for (String filter : split) {
            if (filter.charAt(0) != '#') continue;

            String[] tagSplit = filter.split("[=| ]", 2);
            String tag = tagSplit[0].toLowerCase();
            String value = tagSplit.length == 2 && !tagSplit[1].trim().isEmpty() ? tagSplit[1].split(" ")[0].trim() : null;

            result.put(tag.toLowerCase(), value);
        }
        return result;
    }

    public static boolean isNoteFromDeposits(String note, long id, long date) {
        // TODO also update processDeposit if this is updated
        Map<String, String> notes = PnwUtil.parseTransferHashNotes(note);
        for (Map.Entry<String, String> entry : notes.entrySet()) {
            String tag = entry.getKey();
            String value = entry.getValue();
            switch (tag) {
                case "#nation":
                case "#alliance":
                case "#guild":
                case "#account":
                case "#cash":
                case "#expire":
                    return false;
                case "#ignore":
                    return false;
                case "#deposit":
                case "#deposits":
                case "#trade":
                case "#trades":
                case "#trading":
                case "#credits":
                case "#buy":
                case "#sell":
                case "#warchest":
                case "#raws":
                case "#raw":
                case "#tax":
                case "#taxes":
                case "#disperse":
                case "#disburse":
                case "#loan":
                case "#grant":
                    if (value != null && !value.isEmpty() && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && MathMan.isInteger(value) && id != Long.parseLong(value)) {
                        return false;
                    }
                default:
                    return true;


            }
        }
        return true;
    }

    public static void processDeposit(Transaction2 record, GuildDB guildDB, Set<Long> tracked, int sign, Map<DepositType, double[]> result, double[] amount, String note, long date, Predicate<Transaction2> allowExpiry, boolean allowConversion, boolean allowArbitraryConversion, boolean ignoreMarkedDeposits) {
        /*
        allowConversion sender is nation and alliance has conversion enabled
         */
        if (tracked == null) {
            tracked = new HashSet<>();
            tracked.addAll(guildDB.getCoalitionRaw(Coalition.TRACK_DEPOSITS));
            tracked.add(guildDB.getGuild().getIdLong());
            Integer aaId = guildDB.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId != null) tracked.add(aaId.longValue());
            tracked = expandCoalition(tracked);
        }
        // TODO also update Grant.isNoteFromDeposits if this code is updated

        Map<String, String> notes = parseTransferHashNotes(note);
        DepositType type = DepositType.DEPOSITS;

        for (Map.Entry<String, String> entry : notes.entrySet()) {
            String tag = entry.getKey();
            String value = entry.getValue();

            switch (tag) {
                case "#nation":
                case "#alliance":
                case "#guild":
                case "#account":
                    return;
                case "#ignore":
                    return;
                case "#deposit":
                case "#deposits":
                case "#trade":
                case "#trades":
                case "#trading":
                case "#credits":
                case "#buy":
                case "#sell":
                case "#warchest":
                    if (value != null && !value.isEmpty() && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && ignoreMarkedDeposits && MathMan.isInteger(value) && !tracked.contains(Long.parseLong(value))) {
                        return;
                    }
                    type = DepositType.DEPOSITS;
                    continue;
                case "#raws":
                case "#raw":
                case "#tax":
                case "#taxes":
                case "#disperse":
                case "#disburse":
                    type = DepositType.TAX;
                    if (value != null && !value.isEmpty() && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && ignoreMarkedDeposits && MathMan.isInteger(value) && !tracked.contains(Long.parseLong(value))) {
                        return;
                    }
                    continue;
                default:
                    if (!tag.startsWith("#")) continue;
                    continue;
                case "#loan":
                case "#grant":
                    if (value != null && !value.isEmpty() && date > Settings.INSTANCE.LEGACY_SETTINGS.MARKED_DEPOSITS_DATE && ignoreMarkedDeposits && MathMan.isInteger(value) && !tracked.contains(Long.parseLong(value))) {
                        return;
                    }
                    if (type == DepositType.DEPOSITS) {
                        type = DepositType.LOAN;
                    }
                    continue;
                case "#expire": {
                    if (allowExpiry.test(record) && value != null && !value.isEmpty()) {
                        try {
                            long now = System.currentTimeMillis();
                            long expire = TimeUtil.timeToSec_BugFix1(value, record.tx_datetime) * 1000L;
                            if (now > date + expire) {
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
                case "#cash":
                    if (allowConversion) {
                        Double cashValue = null;
                        if (value != null) {
                            if (allowArbitraryConversion) {
                                cashValue = MathMan.parseDouble(value);
                            }
                        }
                        if (cashValue == null) {
                            if (value != null) {
                                String hash = Hashing.md5()
                                        .hashString(CONVERSION_SECRET + record.tx_id, StandardCharsets.UTF_8)
                                        .toString();

                                if (record.note.contains(hash)) {
                                    cashValue = MathMan.parseDouble(value);
                                }
                            }

                            if (cashValue == null) {
                                long oneWeek = TimeUnit.DAYS.toMillis(7);
                                long start = date - oneWeek;
                                long end = date;
                                TradeDB tradeDb = Locutus.imp().getTradeManager().getTradeDb();

                                cashValue = 0d;
                                for (int i = 0; i < amount.length; i++) {
                                    ResourceType resource = ResourceType.values[i];
                                    double amt = amount[i];
                                    if (resource == ResourceType.MONEY) {
                                        cashValue += amt;
                                    } else {
                                        if (amt < 1) continue;

                                        List<Offer> trades = tradeDb.getOffers(resource, start, end);

                                        Double avg = Locutus.imp().getTradeManager().getAverage(trades).getKey().get(resource);
                                        if (avg != null) {
                                            cashValue += amt * avg;
                                        }
                                    }
                                }

                                {
                                    // set hash
                                    String hash = Hashing.md5()
                                            .hashString(CONVERSION_SECRET + record.tx_id, StandardCharsets.UTF_8)
                                            .toString();
                                    note = note.replaceAll(entry.getKey() + "[^ ]+", "#cash=" + MathMan.format(cashValue));
                                    note += " #" + hash;
                                    record.note = note;
                                    Locutus.imp().getBankDB().addTransaction(record);
                                }
                            }
                        }
                        Arrays.fill(amount, 0);
                        amount[0] = cashValue;
                    }
                    continue;
            }
        }
        double[] rss = result.computeIfAbsent(type, f -> ResourceType.getBuffer());
        if (sign == 1) {
            for (int i = 0; i < rss.length; i++) {
                rss[i] += amount[i];
            }
        } else if (sign == -1) {
            for (int i = 0; i < rss.length; i++) {
                rss[i] -= amount[i];
            }
        } else {
            for (int i = 0; i < rss.length; i++) {
                rss[i] += amount[i] * sign;
            }
        }
    }

    private static String CONVERSION_SECRET = "fe51a236d437901bc1650b0187ac3e46";

    public static String resourcesToJson(String receiver, boolean isNation, Map<ResourceType, Double> rss, String note) {
        Map<String, String> post = new LinkedHashMap<>();
        if (isNation) {
            post.put("withrecipient", receiver);
            post.put("withtype", "Nation");
        } else {
            post.put("withrecipient", "" + receiver);
            post.put("withtype", "Alliance");
        }
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            double amt = rss.getOrDefault(type, 0d);
            if (amt == 0) continue;
            String key = "with" + type.name().toLowerCase();
            post.put(key, String.format("%.2f", amt));
        }
        post.put("withnote", note == null ? "" : note);
        post.put("withsubmit", "Withdraw");

//        for (Map.Entry<String, String> entry : post.entrySet()) {
//            entry.setValue("\"" + entry.getValue() + "\"");
//        }
        return new Gson().toJson(post);
    }

    public static void main(String[] args) {
        String m = "2*{steel=3}";
        System.out.println(StringMan.getString(parseResources(m)));
    }

    public static Map<ResourceType, Double> parseResources(String arg) {
        if (arg.contains("\t") || arg.contains("    ")) {
            String[] split = arg.split("[\t]");
            if (split.length == 1) split = arg.split("[ ]{4}");
            boolean credits = (split.length == ResourceType.values.length);
            if (credits || split.length == ResourceType.values.length - 1) {
                ArrayList<ResourceType> types = new ArrayList<>(Arrays.asList(ResourceType.values));
                if (!credits) types.remove(ResourceType.CREDITS);
                Map<ResourceType, Double> result = new LinkedHashMap<>();
                for (int i = 0; i < types.size(); i++) {
                    result.put(types.get(i), MathMan.parseDouble(split[i].trim()));
                }
                return result;
            }
        }
        arg = arg.replace(" ", "").replace('=', ':').replaceAll("([0-9]),([0-9])", "$1$2").toUpperCase();
        double sign = 1;
        if (arg.charAt(0) == '-') {
            sign = -1;
            arg = arg.substring(1);
        }
        if (arg.charAt(0) == '$') {
            Map<ResourceType, Double> result = new LinkedHashMap<>();
            result.put(ResourceType.MONEY, MathMan.parseDouble(arg) * sign);
            return result;
        }

        arg = arg.replace("GAS:", "GASOLINE:");
        arg = arg.replace("URA:", "URANIUM:");
        arg = arg.replace("BAUX:", "BAUXITE:");
        arg = arg.replace("MUNI:", "MUNITIONS:");
        arg = arg.replace("ALU:", "ALUMINUM:");
        arg = arg.replace("ALUMINIUM:", "ALUMINUM:");
        arg = arg.replace("CASH:", "MONEY:");

        Type type = new TypeToken<Map<ResourceType, Double>>() {}.getType();
        if (arg.charAt(0) != '{' && arg.charAt(arg.length() - 1) != '}') {
            arg = "{" + arg + "}";
        }

        int preMultiply = arg.indexOf("*{");
        int postMultiply = arg.indexOf("}*");
        System.out.println("Pre multiply " + preMultiply);
        if (preMultiply != -1) {
            String[] split = arg.split("\\*\\{", 2);
            arg = "{" + split[1];
            sign *= MathMan.parseDouble(split[0]);
        }
        if (postMultiply != -1) {
            String[] split = arg.split("\\}\\*", 2);
            arg = split[0] + "}";
            sign *= MathMan.parseDouble(split[1]);
        }

        Map<ResourceType, Double> transfer = new Gson().fromJson(arg, type);
        if (sign != 1) {
            for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
                entry.setValue(entry.getValue() * sign);
            }
        }
        return transfer;
    }


    public static Integer parseAllianceId(String arg) {
        if (arg.toLowerCase().startsWith("aa:")) arg = arg.substring(3);
        if (arg.charAt(0) == '"' && arg.charAt(arg.length() - 1) == '"') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.equalsIgnoreCase("none")) {
            return 0;
        }
        if (arg.startsWith(Settings.INSTANCE.PNW_URL() + "/alliance/id=") || arg.startsWith("" + Settings.INSTANCE.PNW_URL() + "/alliance/id=")) {
            String[] split = arg.split("=");
            if (split.length == 2) {
                arg = split[1].replaceAll("/", "");
            }
        } else {
            Integer allianceId = Locutus.imp().getNationDB().getAllianceId(arg);
            if (allianceId != null) {
                return allianceId;
            }
        }
        if (MathMan.isInteger(arg)) {
            try {
                return Integer.parseInt(arg);
            } catch (NumberFormatException e) {}
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
        return PnwUtil.resourcesToArray(PnwUtil.normalize(PnwUtil.resourcesToMap(resources)));
    }

    public static Map<ResourceType, Double> normalize(Map<ResourceType, Double> resources) {
        resources = new LinkedHashMap<>(resources);
        double total = PnwUtil.convertedTotal(resources);
        if (total == 0) return new HashMap<>();
        if (total < 0) {
            return new HashMap<>();
        }

        double negativeTotal = 0;

        Iterator<Map.Entry<ResourceType, Double>> iter = resources.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<ResourceType, Double> entry = iter.next();
            if (entry.getValue() < 0) {
                negativeTotal += Locutus.imp().getTradeManager().getHigh(entry.getKey()) * entry.getValue().doubleValue() * -1;
                iter.remove();
            }
        }
        double postiveTotal = PnwUtil.convertedTotal(resources);


        double factor = total / postiveTotal;
//            factor = Math.min(factor, postiveTotal / (negativeTotal + postiveTotal));

        for (ResourceType type : ResourceType.values()) {
            Double value = resources.get(type);
            if (value == null || value == 0) continue;

            resources.put(type, value * factor);
        }
        return resources;
    }

    public static String resourcesToFancyString(double[] resources) {
        return resourcesToFancyString(resourcesToMap(resources));
    }

    public static String resourcesToFancyString(Map<ResourceType, Double> resources) {
        StringBuilder out = new StringBuilder();
        String leftAlignFormat = "%-10s | %-17s\n";
        out.append("```");
        out.append("Resource   | Amount   \n");
        out.append("-----------+-----------------+\n");
        for (ResourceType type : ResourceType.values) {
            Double amt = resources.get(type);
            if (amt != null) out.append(String.format(leftAlignFormat, type.name(), MathMan.format(amt)));
        }
        out.append("```\n");
        out.append("**Total**: worth ~$" + MathMan.format(PnwUtil.convertedTotal(resources)) + "\n`" + PnwUtil.resourcesToString(resources) + "`");
        return out.toString();
    }

    private static double[] LAND_COST_CACHE = null;

    public static double calculateLand(double from, double to) {
        if (to <= from) return 0;
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

    public static double nextCityCost(DBNation nation, int amount) {
        int current = nation.getCities();
        return cityCost(nation, current, current + amount);
    }

    public static double cityCost(DBNation nation, int from, int to) {
        double total = 0;
        for (int city = Math.max(1, from); city < to; city++) {
            total += nextCityCost(city,
                nation.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY,
                nation.hasProject(Projects.URBAN_PLANNING),
                nation.hasProject(Projects.ADVANCED_URBAN_PLANNING));
        }
        return total;
    }

    public static double nextCityCost(int currentCity, boolean manifestDestiny, boolean cityPlanning, boolean advCityPlanning) {
        double cost = 50000*Math.pow(currentCity - 1, 3) + 150000 * (currentCity) + 75000;
        if (cityPlanning) {
            cost -= 50000000;
        }
        if (advCityPlanning) {
            cost -= 100000000;
        }
        if (manifestDestiny) {
            cost *= 0.95;
        }
        return cost;
    }

    public static Map<ResourceType, Double> adapt(AllianceBankContainer bank) {
        Map<ResourceType, Double> totals = new LinkedHashMap<ResourceType, Double>();
        String json = new Gson().toJson(bank);
        JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
        for (ResourceType type : ResourceType.values) {
            JsonElement amt = obj.get(type.name().toLowerCase());
            if (amt != null) {
                totals.put(type, amt.getAsDouble());
            }
        }
        return totals;
    }

    private static double[] INFRA_COST_CACHE = null;

    public static double calculateInfra(double from, double to) {
        if (to <= from) return 0;

        double[] tmp = INFRA_COST_CACHE;
        if (tmp != null && from == tmp[0] && to == tmp[1]) {
            return tmp[2];
        }

        double total = 0;
        for (double i = Math.max(0, from); i < to; i += 100) {
            double cost = 300d + (Math.pow(Math.max(i - 10d, 20), (2.2d))) / 710d;
            double amt = Math.min(100, to - i);
            total += cost * amt;
        }
        INFRA_COST_CACHE = new double[]{from, to, total};

        return total;
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

    public static <T extends Number> Map<ResourceType, T> addResourcesToA(Map<ResourceType, T> a, Map<ResourceType, T> b) {
        if (b.isEmpty()) {
            return a;
        }
        for (ResourceType type : ResourceType.values) {
            Number v1 = a.get(type);
            Number v2 = b.get(type);
            Number total = v1 == null ? v2 : (v2 == null ? v1 : MathMan.add(v1, v2));
            if (total != null && total.doubleValue() != 0) {
                a.put(type, (T) total);
            } else {
                a.remove(type);
            }
        }
        return a;
    }

    public static <T extends Number> Map<ResourceType, T> negate(Map<ResourceType, T> b) {
        return subResourcesToA(new LinkedHashMap<>(), b);
    }

    public static <T extends Number> Map<ResourceType, T> subResourcesToA(Map<ResourceType, T> a, Map<ResourceType, T> b) {
        for (ResourceType type : ResourceType.values) {
            Number v1 = a.get(type);
            Number v2 = b.get(type);
            if (v2 == null) continue;
            Number total = MathMan.subtract(v1, v2);
            if (total != null && total.doubleValue() != 0) {
                a.put(type, (T) total);
            }
        }
        return a;
    }

    public static <K, T extends Number> Map<K, T> add(Map<K, T> a, Map<K, T> b) {
        if (a.isEmpty()) {
            return b;
        } else if (b.isEmpty()) {
            return a;
        }
        LinkedHashMap<K, T> copy = new LinkedHashMap<>();
        Set<K> keys = new HashSet<>(a.keySet());
        keys.addAll(b.keySet());
        for (K type : keys) {
            Number v1 = a.get(type);
            Number v2 = b.get(type);
            Number total = v1 == null ? v2 : (v2 == null ? v1 : MathMan.add(v1, v2));
            if (total != null && total.doubleValue() != 0) {
                copy.put(type, (T) total);
            }
        }
        return copy;
    }

    public static Map<ResourceType, Double> resourcesToMap(double[] resources) {
        Map<ResourceType, Double> map = new LinkedHashMap<>();
        for (ResourceType type : ResourceType.values) {
            double value = resources[type.ordinal()];
            if (value != 0) {
                map.put(type, value);
            }
        }
        return map;
    }

    public static double[] resourcesToArray(Map<ResourceType, Double> resources) {
        double[] result = new double[ResourceType.values.length];
        for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
            result[entry.getKey().ordinal()] += entry.getValue();
        }
        return result;
    }

    public static String resourcesToString(double[] values) {
        return resourcesToString(resourcesToMap(values));
    }

    public static String resourcesToString(Map<ResourceType, ? extends Number> resources) {
        Map<ResourceType, String> newMap = new LinkedHashMap<>();
        for (ResourceType resourceType : ResourceType.values()) {
            if (resources.containsKey(resourceType)) {
                Number value = resources.get(resourceType);
                if (value.doubleValue() == 0) continue;
                if (value.doubleValue() == value.longValue()) {
                    newMap.put(resourceType, MathMan.format(value.longValue()));
                } else {
                    newMap.put(resourceType, MathMan.format(value.doubleValue()));
                }
            }
        }
        return StringMan.getString(newMap);
    }

    public static double convertedTotal(double[] resources, boolean max) {
        if (max) return convertedTotal(resources);
        double total = 0;
        for (int i = 0; i < resources.length; i++) {
            double amt = resources[i];
            if (amt != 0) {
                total += -convertedTotal(ResourceType.values[i], -amt);
            }
        }
        return total;
    }


    public static double convertedTotal(double[] resources) {
        double total = 0;
        for (int i = 0; i < resources.length; i++) {
            double amt = resources[i];
            if (amt != 0) {
                total += convertedTotal(ResourceType.values[i], amt);
            }
        }
        return total;
    }

    private static Pattern RSS_PATTERN;;

    static {
        String regex = "\\$([0-9|,.]+), ([0-9|,.]+) coal, ([0-9|,.]+) oil, " +
                "([0-9|,.]+) uranium, ([0-9|,.]+) lead, ([0-9|,.]+) iron, ([0-9|,.]+) bauxite, ([0-9|,.]+) " +
                "gasoline, ([0-9|,.]+) munitions, ([0-9|,.]+) steel, ([0-9|,.]+) aluminum, and " +
                "([0-9|,.]+) food";
        RSS_PATTERN = Pattern.compile(regex);
    }

    public static Map.Entry<DBNation, double[]> parseIntelRss(String input, double[] resourceOutput) {
        if (resourceOutput == null) {
            resourceOutput = new double[ResourceType.values.length];
        }

        Matcher matcher = RSS_PATTERN.matcher(input.toLowerCase());
        matcher.matches();
        matcher.groupCount();
        matcher.find();
        String moneyStr;
        try {
            moneyStr = matcher.group(1);
        } catch (IllegalStateException | IndexOutOfBoundsException e) {
            return null;
        }
        double money = MathMan.parseDouble(moneyStr.substring(0, moneyStr.length() - 1));
        double coal = MathMan.parseDouble(matcher.group(2));
        double oil = MathMan.parseDouble(matcher.group(3));
        double uranium = MathMan.parseDouble(matcher.group(4));
        double iron = MathMan.parseDouble(matcher.group(5));
        double bauxite = MathMan.parseDouble(matcher.group(6));
        double lead = MathMan.parseDouble(matcher.group(7));
        double gasoline = MathMan.parseDouble(matcher.group(8));
        double munitions = MathMan.parseDouble(matcher.group(9));
        double steel = MathMan.parseDouble(matcher.group(10));
        double aluminum = MathMan.parseDouble(matcher.group(11));
        double food = MathMan.parseDouble(matcher.group(12));

        resourceOutput[ResourceType.MONEY.ordinal()] = money;
        resourceOutput[ResourceType.COAL.ordinal()] = coal;
        resourceOutput[ResourceType.OIL.ordinal()] = oil;
        resourceOutput[ResourceType.URANIUM.ordinal()] = uranium;
        resourceOutput[ResourceType.IRON.ordinal()] = iron;
        resourceOutput[ResourceType.BAUXITE.ordinal()] = bauxite;
        resourceOutput[ResourceType.LEAD.ordinal()] = lead;
        resourceOutput[ResourceType.GASOLINE.ordinal()] = gasoline;
        resourceOutput[ResourceType.MUNITIONS.ordinal()] = munitions;
        resourceOutput[ResourceType.STEEL.ordinal()] = steel;
        resourceOutput[ResourceType.ALUMINUM.ordinal()] = aluminum;
        resourceOutput[ResourceType.FOOD.ordinal()] = food;
        for (int i = 0; i < resourceOutput.length; i++) {
            if (resourceOutput[i] < 0) resourceOutput[i] = 0;
        }

        String name = input.split("You successfully gathered intelligence about ")[1].split("\\. Your spies discovered that")[0];
        DBNation nation = Locutus.imp().getNationDB().getNation(name);

        return new AbstractMap.SimpleEntry<>(nation, resourceOutput);
    }

    public static double convertedTotal(Map<ResourceType, ? extends Number> resources) {
        double total = 0;
        for (Map.Entry<ResourceType, ? extends Number> entry : resources.entrySet()) {
            total += convertedTotal(entry.getKey(), entry.getValue().doubleValue());
        }
        return total;
    }

    public static double[] getRevenue(double[] profitBuffer, DBNation nation, Map<Integer, JavaCity> cityMap, boolean militaryUpkeep, boolean tradeBonus, boolean bonus) {
        if (profitBuffer == null) profitBuffer = new double[ResourceType.values.length];

        Predicate<Project> hasProject = project -> project != null && nation.hasProject(project);

        double rads = nation.getRads();

        int numCities = bonus ? nation.getCities() : 10;

        Collection<JavaCity> cityList = cityMap.values();

        for (JavaCity build : cityList) {
            profitBuffer = build.profit(rads, hasProject, profitBuffer, numCities);
        }

        if (tradeBonus) {
            profitBuffer[0] += Locutus.imp().getTradeManager().getTradeBonus(nation.getColor()) * 12;
        }

        if (!nation.hasUnsetMil() && militaryUpkeep) {
            boolean war = nation.getOff() > 0 || nation.getDef() > 0;

            for (MilitaryUnit unit : MilitaryUnit.values) {
                int amt = nation.getUnits(unit);
                if (amt == 0) continue;

                double[] upkeep = unit.getUpkeep(war);
                for (int i = 0; i < upkeep.length; i++) {
                    double value = upkeep[i];
                    if (value != 0) {
                        profitBuffer[i] -= value * amt;
                    }
                }
            }
        }
        return profitBuffer;
    }

    public static double convertedTotalPositive(ResourceType type, double amt) {
        return Locutus.imp().getTradeManager().getHighAvg(type) * amt;
    }

    public static double convertedTotalNegative(ResourceType type, double amt) {
        return Locutus.imp().getTradeManager().getLowAvg(type) * amt;
    }

    public static double convertedTotal(ResourceType type, double amt) {
        if (amt != 0) {
            Locutus locutus = Locutus.imp();
            if (locutus != null) {
                if (amt < 0) {
                    return locutus.getTradeManager().getLowAvg(type) * amt;
                } else {
                    return locutus.getTradeManager().getHighAvg(type) * amt;
                }
            }
        }
        return 0;
    }

    public static String getMarkdownUrl(int nationId, boolean isAA) {
        return MarkupUtil.markdownUrl(PnwUtil.getName(nationId, isAA), PnwUtil.getUrl(nationId, isAA));
    }

    public static String getName(long nationOrAllianceId, boolean isAA) {
        if (isAA) {
            String name = Locutus.imp().getNationDB().getAllianceName((int) nationOrAllianceId);
            return name != null ? name : nationOrAllianceId + "";
        } else if (Math.abs(nationOrAllianceId) < Integer.MAX_VALUE) {
            DBNation nation = Locutus.imp().getNationDB().getNation((int) nationOrAllianceId);
            return nation != null ? nation.getNation() : nationOrAllianceId + "";
        } else {
            Guild guild = Locutus.imp().getDiscordApi().getGuildById(Math.abs(nationOrAllianceId));
            return guild != null ? guild.getName() : nationOrAllianceId + "";
        }
    }

    public static String getBBUrl(int nationOrAllianceId, boolean isAA) {
        String type;
        String name;
        if (isAA) {
            type = "alliance";
            name = Locutus.imp().getNationDB().getAllianceName(nationOrAllianceId);
            name = name != null ? name : nationOrAllianceId + "";
        } else {
            type = "nation";
            DBNation nation = Locutus.imp().getNationDB().getNation(nationOrAllianceId);
            name = nation != null ? nation.getNation() : nationOrAllianceId + "";
        }
        String url = "" + Settings.INSTANCE.PNW_URL() + "/" + type + "/id=" + nationOrAllianceId;
        return String.format("[%s](%s)", name, url);
    }

    public static String getUrl(int nationOrAllianceId, boolean isAA) {
        String type;
        String name;
        if (isAA) {
            type = "alliance";
            name = Locutus.imp().getNationDB().getAllianceName(nationOrAllianceId);
            name = name != null ? name : nationOrAllianceId + "";
        } else {
            type = "nation";
            DBNation nation = Locutus.imp().getNationDB().getNation(nationOrAllianceId);
            name = nation != null ? nation.getNation() : nationOrAllianceId + "";
        }
        return "" + Settings.INSTANCE.PNW_URL() + "/" + type + "/id=" + nationOrAllianceId;
    }

    public static String getCityUrl(int cityId) {
        return "" + Settings.INSTANCE.PNW_URL() + "/city/id=" + cityId;
    }

    public static String getNationUrl(int cityId) {
        return "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + cityId;
    }

    public static String getAllianceUrl(int cityId) {
        return "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + cityId;
    }

    public static String getTradeUrl(ResourceType type, boolean isBuy) {
        String url = "https://politicsandwar.com/index.php?id=90&display=world&resource1=%s&buysell=" + (isBuy ? "buy" : "sell") + "&ob=price&od=DEF";
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
                max = Math.min(scoreRange.length - 1, max);
                min = Math.min(scoreRange.length - 1, min);
                return scoreRange[(int) Math.ceil(max)] - scoreRange[min.intValue()];
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
        int total = 0;
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

    public static String getPostScript(String name, boolean nation, Map<ResourceType, Double> rss, String note) {
        return resourcesToJson(name, nation, rss, note);
    }

    public static double getOdds(double attStrength, double defStrength, int success) {
        double a1 = attStrength * 0.4;
        double a2 = attStrength;
        double b1 = defStrength * 0.4;
        double b2 = defStrength;

        if (attStrength <= 0) return 0;
        if (defStrength * 2.5 <= attStrength) return success == 3 ? 1 : 0;
        if (a2 <= b1 || b2 <= a1) return 0;

        double sampleSpace = (a2 - a1) * (b2 - b1);
        double overlap = Math.min(a2, b2) - Math.max(a1, b1);
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
        Set<Integer> aaIds = new LinkedHashSet<>();
        for (String allianceName : arg.split(",")) {
            Set<Integer> coalition = db == null ? Collections.emptySet() : db.getCoalition(allianceName);
            if (!coalition.isEmpty()) aaIds.addAll(coalition);
            else {
                Integer aaId = PnwUtil.parseAllianceId(allianceName);
                if (aaId == null) throw new IllegalArgumentException("Unknown alliance: `" + allianceName + "`");
                aaIds.add(aaId);
            }
        }
        return aaIds;
    }

    public static double[] add(double[] origin, double[] toAdd) {
        for (int i = 0; i < toAdd.length; i++) {
            origin[i] += toAdd[i];
        }
        return origin;
    }

    public static Map<MilitaryUnit, Long> parseUnits(String arg) {
        arg = arg.replace(" ", "").replace('=', ':').replaceAll("([0-9]),([0-9])", "$1$2").toUpperCase();

        for (MilitaryUnit unit : MilitaryUnit.values()) {
            String name = unit.getName();
            if (name == null || name.equalsIgnoreCase(unit.name())) continue;
            arg = arg.replace(name.toUpperCase() + ":", unit.name() + ":");
        }

        Type type = new TypeToken<Map<MilitaryUnit, Long>>() {}.getType();
        if (arg.charAt(0) != '{' && arg.charAt(arg.length() - 1) != '}') {
            arg = "{" + arg + "}";
        }
        Map<MilitaryUnit, Long> result = new Gson().fromJson(arg, type);
        return result;
    }
}