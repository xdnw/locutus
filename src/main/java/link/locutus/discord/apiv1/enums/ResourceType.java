package link.locutus.discord.apiv1.enums;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.politicsandwar.graphql.model.Bankrec;
import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.config.Settings;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.IOUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.CachedSupplier;
import link.locutus.discord.util.scheduler.KeyValue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum ResourceType {
    MONEY("money", "withmoney", 16),
    CREDITS("credits", "withcredits", -1),

    FOOD("food", "withfood", 11, 400, 2, 0, 20, 1.25d, () -> Projects.MASS_IRRIGATION, 0) {
            @Override
            public double getBaseProduction(Continent continent, double rads, Predicate<Project> hasProject, double land, long date) {
                int factor = 500;
                if (hasProject.test(getProject())) {
                    factor = 400;
                }
                if (hasProject.test(Projects.FALLOUT_SHELTER)) {
                    rads = Math.max(0, Math.min(1, 0.15 + 0.85 * rads));
                } else {
                    rads = Math.max(0, rads);
                }

                double season = date <= 0 ? continent.getSeasonModifier() : continent.getSeasonModifierDate(date);

                return Math.max(0, (land / factor) * 12 * season * rads);
            }

        @Override
        public double getProduction(Continent continent, double rads, Predicate<Project> hasProject, double land, int improvements, long date) {
            return improvements * (1 + ((0.5 * (improvements - 1)) / (20 - 1))) * getBaseProduction(continent, rads, hasProject, land, date);
        }
    },
    COAL("coal", "withcoal", 10, 600, 12, 3, 10),
    OIL("oil", "withoil", 21, 600, 12, 3, 10),
    URANIUM("uranium", "withuranium", 24, 5000, 20, 3, 5, 2, () -> Projects.URANIUM_ENRICHMENT_PROGRAM, 0),
    LEAD("lead", "withlead", 15, 1500, 12, 3, 10),
    IRON("iron", "withiron", 14, 1600, 12, 3, 10),
    BAUXITE("bauxite", "withbauxite", 9, 1600, 12, 3, 10),
    GASOLINE("gasoline", "withgasoline", 13, 4000, 32, 6, 5, 2, () -> Projects.EMERGENCY_GASOLINE_RESERVE, 3, OIL),
    MUNITIONS("munitions", "withmunitions", 19, 3500 , 32, 18, 5, 1.2, () -> Projects.ARMS_STOCKPILE, 6, LEAD),
    STEEL("steel", "withsteel", 23, 4000, 40, 9, 5, 1.36, () -> Projects.IRON_WORKS, 3, IRON, COAL),
    ALUMINUM("aluminum", "withaluminum", 8, 2500, 40, 9, 5, 1.36, () -> Projects.BAUXITEWORKS, 3, BAUXITE);

    public static Supplier<Double> convertedCostLazy(double[] amount) {
        return new CachedSupplier<>(() -> convertedTotal(amount));
    }

    public static Supplier<Double> convertedCostLazy(ResourceType type, double amt) {
        return new CachedSupplier<>(() -> convertedTotal(type, amt));
    }

    private static final Type RESOURCE_TYPE = new TypeToken<Map<ResourceType, Double>>() {}.getType();
    private static final Gson RESOURCE_GSON = new GsonBuilder()
            .registerTypeAdapter(RESOURCE_TYPE, new DoubleDeserializer())
            .create();

    public static ResourceType[] getTypes(double[] costReduction) {
        List<ResourceType> types = new LinkedList<>();
        for (ResourceType type : values) {
            if (costReduction[type.ordinal()] != 0) {
                types.add(type);
            }
        }
        return types.toArray(new ResourceType[0]);
    }

    private static class DoubleDeserializer implements JsonDeserializer<Map<ResourceType, Double>> {
        @Override
        public Map<ResourceType, Double> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Map<ResourceType, Double> map = new LinkedHashMap<>();
            json.getAsJsonObject().entrySet().forEach(entry -> {
                ResourceType key = ResourceType.valueOf(entry.getKey());
                Double value = PrimitiveBindings.Double(entry.getValue().getAsString());
                map.put(key, value);
            });
            return map;
        }
    }
    private static final Pattern RSS_PATTERN;;

    static {
        String regex = "\\$([0-9|,.]+), ([0-9|,.]+) coal, ([0-9|,.]+) oil, " +
                "([0-9|,.]+) uranium, ([0-9|,.]+) lead, ([0-9|,.]+) iron, ([0-9|,.]+) bauxite, ([0-9|,.]+) " +
                "gasoline, ([0-9|,.]+) munitions, ([0-9|,.]+) steel, ([0-9|,.]+) aluminum[,]{0,1} and " +
                "([0-9|,.]+) food";
        RSS_PATTERN = Pattern.compile(regex);
    }

    public static ResourceType parseChar(Character s) {
        if (s == '$') return MONEY;
        // ignore MONEY and CREDITS
        for (ResourceType type : values) {
            if (type == MONEY || type == CREDITS) continue;
            if (Character.toLowerCase(type.getName().charAt(0)) == Character.toLowerCase(s)) {
                return type;
            }
        }
        return null;
    }

    public static Map<ResourceType, Double> roundResources(Map<ResourceType, Double> resources) {
        Map<ResourceType, Double> copy = new Object2DoubleOpenHashMap<>(resources);
        for (Map.Entry<ResourceType, Double> entry : copy.entrySet()) {
            entry.setValue(Math.round(entry.getValue() * 100.0) / 100.0);
        }
        return copy;
    }

    public static Map<String, String> resourcesToJson(NationOrAlliance receiver, Map<ResourceType, Double> rss, String note) {
        Map<String, String> post = new LinkedHashMap<>();
        if (receiver.isNation()) {
            post.put("withrecipient", receiver.getName());
            post.put("withtype", "Nation");
        } else {
            post.put("withrecipient", receiver.getName());
            post.put("withtype", "Alliance");
        }
        for (ResourceType type : values) {
            if (type == CREDITS) continue;
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
        return post;
    }

    public static Map<ResourceType, Double> parseResources(String arg) {
        if (arg.endsWith("},")) {
            arg = arg.substring(0, arg.length() - 1);
        }
        if (arg.endsWith(",}")) {
            arg = arg.substring(0, arg.length() - 2) + "}";
        } else if (arg.endsWith(",")) {
            arg = arg.substring(0, arg.length() - 1);
        }
        boolean allowBodmas = arg.contains("{") && StringMan.containsAny("+-*/^%", arg.replaceAll("\\{[^}]+}", ""));
        return parseResources(arg, allowBodmas);
    }

    public static Map<ResourceType, Double> parseResources(String arg, boolean allowBodmas) {
        if (MathMan.isInteger(arg)) {
            throw new IllegalArgumentException("Please use `$" + arg + "` or `money=" + arg + "` for money, not `" + arg + "`");
        }
        if (arg.contains(" AM ") || arg.contains(" PM ")) {
            arg = arg.replaceAll("([0-9]{1,2}:[0-9]{2})[ ](AM|PM)", "")
                    .replace("\n", " ")
                    .replaceAll("[ ]+", " ");
        }
        if (arg.contains("---+") && arg.contains("-+-")) {
            arg = arg.replace("-+-", "---");
            int start = arg.indexOf("---+");
            arg = arg.substring(start + 4).trim();
            arg = arg.replaceAll("([0-9.]+)[ ]+", "$1,");
            arg = arg.replace("\n", "");
            arg = arg.replaceAll("[ ]+", " ");
            arg = "{" + arg.replace(" | ", ":") + "}";
        }
        if (arg.contains("\t") || arg.contains("    ")) {
            String[] split = arg.split("[\t]");
            if (split.length == 1) split = arg.split("[ ]{4}");
            boolean credits = (split.length == values.length);
            if (credits || split.length == values.length - 1) {
                ArrayList<ResourceType> types = new ArrayList<>(Arrays.asList(values));
                if (!credits) types.remove(CREDITS);
                Map<ResourceType, Double> result = new LinkedHashMap<>();
                for (int i = 0; i < types.size(); i++) {
                    result.put(types.get(i), MathMan.parseDouble(split[i].trim()));
                }
                return result;
            }
        } else if (arg.contains(" and ")) {
            arg = arg.replace(" and ", ", ");
            if (arg.contains(" taking:")) {
                arg = arg.split(" taking:")[1].trim();
            } else if (arg.contains(" looted ")) {
                arg = arg.split(" looted ")[1].trim();
            } else if (arg.contains(" spies, ")) {
                arg = arg.split(" spies, ")[1].trim();
            }
            if (arg.contains(". ")) {
                arg = arg.substring(0, arg.indexOf(". "));
            }
            arg = arg.replace(",,", ",");
        }
        arg = arg.trim();
        String original = arg;
        if (!arg.contains(":") && !arg.contains("=")) {
            arg = arg.replaceAll("([-0-9])[ ]([a-zA-Z])", "$1:$2");
            arg = arg.replaceAll("([a-zA-Z])[ ]([-0-9])", "$1:$2");
        }
        arg = arg.replace('=', ':').replaceAll("([0-9]),([0-9])", "$1$2").toUpperCase();
        arg = arg.replaceAll("([0-9.]+):([a-zA-Z]{3,})", "$2:$1");
        arg = arg.replaceAll("([A-Z]+:[0-9.]+) ([A-Z]+:[0-9.]+)", "$1,$2");
        arg = arg.replace(" ", "");
        if (arg.startsWith("$") || arg.startsWith("-$")) {
            if (!arg.contains(",")) {
                int sign = 1;
                if (arg.startsWith("-")) {
                    sign = -1;
                    arg = arg.substring(1);
                }
                Map<ResourceType, Double> result = new LinkedHashMap<>();
                result.put(MONEY, MathMan.parseDouble(arg) * sign);
                return result;
            }
            arg = arg.replace("$", MONEY +":");
        }

        arg = arg.replace("GAS:", "GASOLINE:");
        arg = arg.replace("URA:", "URANIUM:");
        arg = arg.replace("BAUX:", "BAUXITE:");
        arg = arg.replace("MUNI:", "MUNITIONS:");
        arg = arg.replace("ALU:", "ALUMINUM:");
        arg = arg.replace("ALUMINIUM:", "ALUMINUM:");
        arg = arg.replace("CASH:", "MONEY:");

        if (!arg.contains("{") && !arg.contains("}")) {
            arg = "{" + arg + "}";
        }
        if (arg.startsWith("-{")) {
            arg = "{}" + arg;
        }
        arg = arg.replace("(-{", "({}-{");

        Map<ResourceType, Double> result;
        try {
            Function<String, Map<ResourceType, Double>> parse = f -> {
                if (f.contains("TRANSACTION_COUNT")) {
                    f = f.replaceAll("\"TRANSACTION_COUNT\":[0-9]+,", "");
                    f = f.replaceAll(",\"TRANSACTION_COUNT\":[0-9]+", "");
                }
                return RESOURCE_GSON.fromJson(f, RESOURCE_TYPE);
            };
            if (allowBodmas) {
                List<ArrayUtil.DoubleArray> resources = (ArrayUtil.calculate(arg, arg1 -> {
                    if (!arg1.contains("{")) {
                        return new ArrayUtil.DoubleArray(PrimitiveBindings.Double(arg1));
                    }
                    Map<ResourceType, Double> map = parse.apply(arg1);
                    double[] arr = resourcesToArray(map);
                    return new ArrayUtil.DoubleArray(arr);
                }));
                result = resourcesToMap(resources.get(0).toArray());
            } else {
                result = parse.apply(arg);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (original.toUpperCase(Locale.ROOT).matches("[0-9]+[ASMGBILUOCF$]([ ][0-9]+[ASMGBILUOCF$])*")) {
                String[] split = original.split(" ");
                result = new LinkedHashMap<>();
                for (String s : split) {
                    Character typeChar = s.charAt(s.length() - 1);
                    ResourceType type1 = parseChar(typeChar);
                    double amount = MathMan.parseDouble(s.substring(0, s.length() - 1));
                    result.put(type1, amount);
                }
            } else {
                return handleResourceError(arg, e);
            }
        }
        if (result.containsKey(null)) {
            return handleResourceError(arg, null);
        }
        return result;
    }

    private static Map<ResourceType, Double> handleResourceError(String arg, Exception e) {
        StringBuilder response = new StringBuilder("Invalid resource amounts: `" + arg + "`\n");
        if (e != null) {
            String msg = e.getMessage();
            if (msg.startsWith("No enum constant")) {
                String rssInput = msg.substring(msg.lastIndexOf(".") + 1);
                List<ResourceType> closest = StringMan.getClosest(rssInput, valuesList, false);

                response.append("You entered `" + rssInput + "` which is not a valid resource.");
                if (closest.size() > 0) {
                    response.append(" Did you mean: `").append(closest.get(0)).append("`");
                }
                response.append("\n");
            } else {
                response.append("Error: `").append(e.getMessage()).append("`\n");
            }
        }
        response.append("Valid resources are: `").append(StringMan.join(values, ", ")).append("`").append("\n");
        response.append("""
                You can enter a single resource like this:
                `food=10`
                `$15`
                Use commas for multiple resources:
                `food=10,gas=20,money=30`
                Use k,m,b,t for thousands, millions, billions, trillions:
                `food=10k,gas=20m,money=30b`
                Use curly braces for operations:
                `{food=3*(2+1),coal=-3}*{food=112,coal=2513}*1.5+{coal=1k}^0.5`""");
        throw new IllegalArgumentException(response.toString());
    }

    public static String resourcesToFancyString(double[] resources) {
        return resourcesToFancyString(resourcesToMap(resources));
    }

    public static String resourcesToFancyString(double[] resources, String totalName) {
        return resourcesToFancyString(resourcesToMap(resources), totalName);
    }

    public static String resourcesToFancyString(Map<ResourceType, Double> resources) {
        return resourcesToFancyString(resources, null);
    }

    public static String resourcesToFancyString(Map<ResourceType, Double> resources, String totalName) {
        StringBuilder out = new StringBuilder();
        String leftAlignFormat = "%-10s | %-17s\n";
        out.append("```");
        out.append("Resource   | Amount   \n");
        out.append("-----------+-----------------+\n");
        for (ResourceType type : values) {
            Double amt = resources.get(type);
            if (amt != null) out.append(String.format(leftAlignFormat, type.name(), MathMan.format(amt)));
        }
        out.append("```\n");
        out.append("**Total" + (totalName != null && !totalName.isEmpty() ? " " + totalName : "") + "**: worth ~$" + MathMan.format(convertedTotal(resources)) + "\n```" + toString(resources) + "``` ");
        return out.toString();
    }

    public static <T extends Number> Map<ResourceType, T> addResourcesToA(Map<ResourceType, T> a, Map<ResourceType, T> b) {
        if (b.isEmpty()) {
            return a;
        }
        for (ResourceType type : values) {
            T v1 = a.get(type);
            T v2 = b.get(type);
            T total = v1 == null ? v2 : (v2 == null ? v1 : (T) MathMan.add(v1, v2));
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
        for (ResourceType type : values) {
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
        for (ResourceType type : values) {
            double value = resources[type.ordinal()];
            if (value != 0) {
                map.put(type, value);
            }
        }
        return map;
    }

    public static double[] resourcesToArray(Map<ResourceType, Double> resources) {
        double[] result = new double[values.length];
        for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
            result[entry.getKey().ordinal()] += entry.getValue();
        }
        return result;
    }

    public static String toString(double[] values) {
        return toString(resourcesToMap(values));
    }

    public static String toString(Map<ResourceType, ? extends Number> resources) {
        Map<ResourceType, String> newMap = new LinkedHashMap<>();
        for (ResourceType resourceType : values()) {
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
                total += -convertedTotal(values[i], -amt);
            }
        }
        return total;
    }

    public static double convertedTotal(double[] resources) {
        double total = 0;
        for (int i = 0; i < resources.length; i++) {
            double amt = resources[i];
            if (amt != 0) {
                total += convertedTotal(values[i], amt);
            }
        }
        return total;
    }

    public static Map.Entry<DBNation, double[]> parseIntelRss(String input, double[] resourceOutput) {
        if (resourceOutput == null) {
            resourceOutput = new double[values.length];
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

        resourceOutput[MONEY.ordinal()] = money;
        resourceOutput[COAL.ordinal()] = coal;
        resourceOutput[OIL.ordinal()] = oil;
        resourceOutput[URANIUM.ordinal()] = uranium;
        resourceOutput[IRON.ordinal()] = iron;
        resourceOutput[BAUXITE.ordinal()] = bauxite;
        resourceOutput[LEAD.ordinal()] = lead;
        resourceOutput[GASOLINE.ordinal()] = gasoline;
        resourceOutput[MUNITIONS.ordinal()] = munitions;
        resourceOutput[STEEL.ordinal()] = steel;
        resourceOutput[ALUMINUM.ordinal()] = aluminum;
        resourceOutput[FOOD.ordinal()] = food;
        for (int i = 0; i < resourceOutput.length; i++) {
            if (resourceOutput[i] < 0) resourceOutput[i] = 0;
        }

        String name = input.split("You successfully gathered intelligence about ")[1].split("\\. Your spies discovered that")[0];
        Locutus lc = Locutus.imp();
        DBNation nation = lc == null ? null : lc.getNationDB().getNationByNameOrLeader(name);

        return new KeyValue<>(nation, resourceOutput);
    }

    public static double convertedTotal(Map<ResourceType, ? extends Number> resources) {
        double total = 0;
        for (Map.Entry<ResourceType, ? extends Number> entry : resources.entrySet()) {
            total += convertedTotal(entry.getKey(), entry.getValue().doubleValue());
        }
        return total;
    }

    public static double convertedTotalPositive(ResourceType type, double amt) {
        return Locutus.imp().getTradeManager().getHighAvg(type) * amt;
    }

    public static double convertedTotalNegative(ResourceType type, double amt) {
        return Locutus.imp().getTradeManager().getLowAvg(type) * amt;
    }

    public static double convertedTotal(ResourceType type, double amt) {
        if (amt != 0) {
            try {
                Locutus locutus = Locutus.imp();
//                if (amt < 0) {
//                    return locutus.getTradeManager().getLowAvg(type) * amt;
//                } else
//                {
                return locutus.getTradeManager().getHighAvg(type) * amt;
//                }
            } catch (NullPointerException ignore) {}
            return type == MONEY ? amt : 0;
        }
        return 0;
    }

    public static double[] max(double[] rss1, double[] rss2) {
        for (int i = 0; i < rss1.length; i++) {
            rss1[i] = Math.max(rss1[i], rss2[i]);
        }
        return rss1;
    }

    public static double[] min(double[] rss1, double[] rss2) {
        for (int i = 0; i < rss1.length; i++) {
            rss1[i] = Math.min(rss1[i], rss2[i]);
        }
        return rss1;
    }

    public String getShorthand() {
        return switch (this) {
            case MONEY -> "$";
            case CREDITS -> "CRED";
            case FOOD -> name();
            case COAL -> name();
            case OIL -> name();
            case URANIUM -> "URA";
            case LEAD -> "PB";
            case IRON -> "FE";
            case BAUXITE -> "BAUX";
            case GASOLINE -> "GAS";
            case MUNITIONS -> "MUNI";
            case STEEL -> "STEEL";
            case ALUMINUM -> "ALU";
        };
    }

    public static ResourceType parse(String input) {
        String upper = input.toUpperCase();
        switch (upper) {
            case "$":
            case "MO":
            case "MON":
            case "MONE":
                return MONEY;
            case "CR":
            case "CRE":
            case "CRED":
            case "CREDI":
            case "CREDIT":
                return CREDITS;
            case "F":
            case "FO":
            case "FOO":
                return FOOD;
            case "CO":
            case "COA":
                return COAL;
            case "O":
            case "OI":
                return OIL;
            case "U":
            case "UR":
            case "URA":
            case "URAN":
            case "URANI":
            case "URANIU":
                return URANIUM;
            case "L":
            case "LE":
            case "LEA":
            case "PB":
                return LEAD;
            case "FE":
            case "I":
            case "IR":
            case "IRO":
                return IRON;
            case "B":
            case "BA":
            case "BAU":
            case "BAUX":
            case "BAUXI":
            case "BAUXIT":
                return BAUXITE;
            case "G":
            case "GA":
            case "GAS":
            case "GASO":
            case "GASOL":
            case "GASOLI":
            case "GASOLIN":
                return GASOLINE;
            case "MU":
            case "MUN":
            case "MUNI":
            case "MUNIT":
            case "MUNITI":
            case "MUNITIO":
            case "MUNITION":
                return MUNITIONS;
            case "S":
            case "ST":
            case "STE":
            case "STEE":
                return STEEL;
            case "AL":
            case "ALU":
            case "ALUM":
            case "ALUMI":
            case "ALUMIN":
            case "ALUMINU":
            case "ALUMINI":
            case "ALUMINIU":
            case "ALUMINIUM":
                return ALUMINUM;
        }
        return valueOf(upper);
    }

    public static final ResourceType[] values = values();
    public static final List<ResourceType> valuesList = Arrays.asList(values);

    public static boolean isZero(double[] resources) {
        for (double i : resources) {
            if (i != 0 && (Math.abs(i) >= 0.005)) return false;
        }
        return true;
    }

    public static boolean isZero(Map<ResourceType, Double> amount) {
        for (Map.Entry<ResourceType, Double> entry : amount.entrySet()) {
            if (entry.getValue() != null && (Math.abs(entry.getValue()) >= 0.005)) {
                return false;
            }
        }
        return true;
    }


    public static double[] floor(double[] resources, double min) {
        for (int i = 0; i < resources.length; i++) {
            if (resources[i] < min) resources[i] = min;
        }
        return resources;
    }

    public static double[] ceil(double[] resources, double max) {
        for (int i = 0; i < resources.length; i++) {
            if (resources[i] > max) resources[i] = max;
        }
        return resources;
    }

    public static double[] set(double[] resources, double[] values) {
        for (int i = 0; i < values.length; i++) {
            resources[i] = values[i];
        }
        return resources;
    }

    public static double[] subtract(double[] resources, double[] values) {
        for (int i = 0; i < values.length; i++) {
            double amt = values[i];
            double curr = resources[i];
            resources[i] = (Math.round(curr * 100) - Math.round(amt * 100)) * 0.01;
        }
        return resources;
    }
    public static double[] add(double[] resources, double[] values) {
        for (int i = 0; i < values.length; i++) {
            double amt = values[i];
            double curr = resources[i];
            resources[i] = Math.round(amt * 100) * 0.01 + Math.round(curr * 100) * 0.01;
        }
        return resources;
    }
    public static double[] add(Collection<double[]> values) {
        double[] result = getBuffer();
        for (double[] value : values) {
            add(result, value);
        }
        return result;
    }


    public static double[] round(double[] resources) {
        for (int i = 0; i < resources.length; i++) {
            double amt = resources[i];
            if (amt != 0) {
                resources[i] = Math.round(amt * 100) * 0.01;
            }
        }
        return resources;
    }

    public static double[] negative(double[] resources) {
        for (int i = 0; i < resources.length; i++) {
            resources[i] = -resources[i];
        }
        return resources;
    }

    public static boolean equals(Map<ResourceType, Double> amtA, Map<ResourceType, Double> amtB) {
        for (ResourceType type : ResourceType.values) {
            if (Math.round(100 * (amtA.getOrDefault(type, 0d) - amtB.getOrDefault(type, 0d))) != 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean equals(double[] rss1, double[] rss2) {
        for (ResourceType type : ResourceType.values) {
            if (Math.round(100 * (rss1[type.ordinal()] - rss2[type.ordinal()])) != 0) {
                return false;
            }
        }
        return true;
    }

    public static String toString(double[] resources, boolean fancy) {
        return fancy ? resourcesToFancyString(resources) : toString(resources);
    }

    public double[] toArray(double amt) {
        double[] result = getBuffer();
        result[ordinal()] = amt;
        return result;
    }

    public static double getEquilibrium(double[] total) {
        double consumeCost = 0;
        double taxable = 0;
        for (ResourceType type : values) {
            double amt = total[type.ordinal()];
            if (amt < 0) {
                consumeCost += Math.abs(convertedTotal(type, -amt));
            } else if (amt > 0){
                taxable += Math.abs(convertedTotal(type, amt));
            }
        }
        if (taxable > consumeCost) {
            double requiredTax = consumeCost / taxable;
            return requiredTax;
        }
        return -1;
    }

    @Command(desc = "The building corresponding to this resource (if any)")
    public Building getBuilding() {
        return Buildings.RESOURCE_BUILDING.get(this);
    }

    public static double[] read(ByteBuffer buf, double[] output) {
        if (output == null) output = getBuffer();
        for (int i = 0; i < output.length; i++) {
            if (!buf.hasRemaining()) break;
            output[i] += buf.getDouble();
        }
        return output;
    }

    public static double[] getBuffer() {
        return new double[ResourceType.values.length];
    }

    public static ResourcesBuilder builder() {
        return new ResourcesBuilder();
    }

    public static ResourcesBuilder builder(double[] amount) {
        return builder().add(amount);
    }

    public static ResourcesBuilder builder(Map<ResourceType, Double> amount) {
        return builder().add(amount);
    }

    public ResourcesBuilder builder(double amt) {
        return builder().add(this, amt);
    }

    public static class ResourcesBuilder {
        private double[] resources = null;

        public String toString() {
            return ResourceType.toString(build());
        }

        public double convertedTotal() {
            return ResourceType.convertedTotal(build());
        }

        public String convertedStr() {
            return "~$" + MathMan.format(convertedTotal());
        }

        private double[] getResources() {
            if (resources == null) resources = getBuffer();
            return resources;
        }


        public <T> ResourcesBuilder forEach(Iterable<T> iterable, BiConsumer<ResourcesBuilder, T> consumer) {
            for (T t : iterable) {
                consumer.accept(this, t);
            }
            return this;
        }
        public ResourcesBuilder add(ResourceType type, double amt) {
            if (amt != 0) {
                getResources()[type.ordinal()] += amt;
            }
            return this;
        }

        public ResourcesBuilder add(double[] amt) {
            for (ResourceType type : ResourceType.values) {
                add(type, amt[type.ordinal()]);
            }
            return this;
        }

        public ResourcesBuilder add(Map<ResourceType, Double> amt) {
            for (Map.Entry<ResourceType, Double> entry : amt.entrySet()) {
                add(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public ResourcesBuilder addMoney(double amt) {
            return add(ResourceType.MONEY, amt);
        }

        public boolean isEmpty() {
            return resources == null || ResourceType.isZero(resources);
        }

        public double[] build() {
            return getResources();
        }

        public ResourcesBuilder subtract(Map<ResourceType, Double> amt) {
            for (Map.Entry<ResourceType, Double> entry : amt.entrySet()) {
                add(entry.getKey(), -entry.getValue());
            }
            return this;
        }

        public ResourcesBuilder subtract(double[] resources) {
            for (int i = 0; i < resources.length; i++) {
                add(ResourceType.values[i], -resources[i]);
            }
            return this;
        }

        public Map<ResourceType, Double> buildMap() {
            return resourcesToMap(build());
        }

        public ResourcesBuilder max(double[] amounts) {
            for (int i = 0; i < amounts.length; i++) {
                this.resources[i] = Math.max(this.resources[i], amounts[i]);
            }
            return this;
        }

        public ResourcesBuilder maxZero() {
            for (int i = 0; i < resources.length; i++) {
                this.resources[i] = Math.max(this.resources[i], 0);
            }
            return this;
        }

        public ResourcesBuilder min(double[] amounts) {
            for (int i = 0; i < amounts.length; i++) {
                this.resources[i] = Math.min(this.resources[i], amounts[i]);
            }
            return this;
        }

        public ResourcesBuilder negative() {
            for (int i = 0; i < resources.length; i++) {
                this.resources[i] = -this.resources[i];
            }
            return this;
        }
    }

    public static double[] fromApiV3(Bankrec rec, double[] buffer) {
        double[] resources = buffer == null ? getBuffer() : buffer;
        resources[ResourceType.MONEY.ordinal()] = rec.getMoney();
        resources[ResourceType.COAL.ordinal()] = rec.getCoal();
        resources[ResourceType.OIL.ordinal()] = rec.getOil();
        resources[ResourceType.URANIUM.ordinal()] = rec.getUranium();
        resources[ResourceType.IRON.ordinal()] = rec.getIron();
        resources[ResourceType.BAUXITE.ordinal()] = rec.getBauxite();
        resources[ResourceType.LEAD.ordinal()] = rec.getLead();
        resources[ResourceType.GASOLINE.ordinal()] = rec.getGasoline();
        resources[ResourceType.MUNITIONS.ordinal()] = rec.getMunitions();
        resources[ResourceType.STEEL.ordinal()] = rec.getSteel();
        resources[ResourceType.ALUMINUM.ordinal()] = rec.getAluminum();
        resources[ResourceType.FOOD.ordinal()] = rec.getFood();
        return resources;
    }

    private final double baseProduction;
    private final double baseProductionInverse;
    private final int cap;
    private final double capInverse;
    private final double boostFactor;
    private final Supplier<Project> getProject;
    private final Predicate<Predicate<Project>> hasProject;
    private final ResourceType[] inputs;
    private final int baseInput;
    private final int pollution;
    private final int upkeep;

    private final String name;
    private final String bankString;
    private final int graphId;

    ResourceType(String name, String bankString, int graphId) {
        this(name, bankString, graphId, 0, 0, 0, 0);
    }

    ResourceType(String name, String bankString, int graphId, int upkeep, int pollution, double baseProduction, int cap) {
        this(name, bankString, graphId, upkeep, pollution, baseProduction, cap, 1, null, 0);
    }

    ResourceType(String name, String bankString, int graphId, int upkeep, int pollution, double baseProduction, int cap, double boostFactor, Supplier<Project> project, int baseInput, ResourceType... inputs) {
        this.name = name;
        this.bankString = bankString;
        this.baseProduction = baseProduction;
        this.baseProductionInverse = 1d / baseProduction;
        this.cap = cap;
        this.capInverse = 1d / (cap - 1d);
        this.boostFactor = boostFactor;
        this.baseInput = baseInput;
        this.pollution = pollution;
        this.upkeep = upkeep;
        this.getProject = project == null ? () -> null : project;
        this.hasProject = project == null ? p -> false : p -> p.test(project.get());
        this.inputs = inputs;
        this.graphId = graphId;
    }

    @Command(desc = "The id of this resource on the graph")
    public int getGraphId() {
        return graphId;
    }

    public String url(Boolean isBuy, boolean shorten) {
        String url;
        if (shorten) {
            if (isBuy == Boolean.TRUE) {
                url = "https://tinyurl.com/qmm5ue7?resource1=%s";
            } else if (isBuy == Boolean.FALSE){
                url = "https://tinyurl.com/s2n7xp9?resource1=%s";
            } else {
                url = "https://tinyurl.com/26sxb2xb?resource1=%s";
            }
            url = String.format(url, name().toLowerCase());
        } else {
            url = Settings.PNW_URL() + "/index.php?id=90&display=world&resource1=%s&buysell=" + (isBuy ? "buy" : "sell") + "&ob=price&od=DEF";
            url = String.format(url, name().toLowerCase());
        }
        return url;
    }

    @Command(desc = "If this is a raw resource")
    public boolean isRaw() {
        return inputs.length == 0 && cap > 0;
    }

    @Command(desc = "If this is a manufactured resource")
    public boolean isManufactured() {
        return inputs.length > 0;
    }

    @Command(desc = "The pollution modifier for this resource's production")
    public int getPollution() {
        return pollution;
    }

    @Command(desc = "The upkeep modifier for this resource's production")
    public int getUpkeep() {
        return upkeep;
    }

    @Command(desc = "The base input for this resource (if manufactured)")
    public int getBaseInput() {
        return baseInput;
    }

    @Command(desc = "The project boost factor for this resource's production (if any)")
    public double getBoostFactor() {
        return boostFactor;
    }

    public double getInput(Continent continent, double rads, Predicate<Project> hasProject, ICity city, int improvements) {
        if (inputs.length == 0) return 0;

        double base = getBaseProduction(continent, rads, hasProject, city.getLand(), -1);
        base = (base * baseProductionInverse) * baseInput;

        return base * (1+0.5*((improvements - 1d) * capInverse)) * improvements;
    }

    public double getBaseProduction(Continent continent, double rads, Predicate<Project> hasProject, double land, long date) {
        double factor = 1;
        if (this.hasProject.test(hasProject)) {
            factor = boostFactor;
        }
        return baseProduction * factor;
    }

    @Command(desc = "The input output ratio for this resource (if manufactured)")
    public double getManufacturingMultiplier() {
        return baseProduction / baseInput;
    }

    @Command(desc = "The project required to boost this resource's production (if any)")
    public Project getProject() {
        return getProject.get();
    }

    @Command(desc = "The building cap for this resource's production")
    public int getCap() {
        return cap;
    }

    public double getProduction(Continent continent, double rads, Predicate<Project> hasProject, ICity city, int improvements, long date) {
        return getProduction(continent, rads, hasProject, city.getLand(), improvements, date);
    }

    public double getProduction(Continent continent, double rads, Predicate<Project> hasProject, double land, int improvements, long date) {
        double base = getBaseProduction(continent, rads, hasProject, land, date);
        return base * (1+0.5*((improvements - 1) * capInverse)) *improvements;
    }

    @Command(desc = "The market value of this resource (weekly average)")
    public double getMarketValue() {
        return Locutus.imp().getTradeManager().getLowAvg(this);
    }

    @Command(desc = "The average weekly sell price of this resource")
    public double getLowAverage() {
        return Locutus.imp().getTradeManager().getLowAvg(this);
    }

    @Command(desc = "The average weekly buy price of this resource")
    public double getHighAverage() {
        return Locutus.imp().getTradeManager().getHighAvg(this);
    }

    @Command(desc = "The margin between the current top buy and sell price on the market")
    public int getMargin() {
        return (int) (getHigh() - getLow());
    }

    @Command(desc = "The average margin between buy and sell in the past week for completed trades")
    public double getAverageMargin() {
        return (getHighAverage() - getLowAverage());
    }

    @Command(desc = "The current top buy price on the market")
    public int getHigh() {
        return Locutus.imp().getTradeManager().getPrice(this, true);
    }

    @Command(desc = "The current top sell price on the market")
    public int getLow() {
        return Locutus.imp().getTradeManager().getPrice(this, false);
    }

    public ResourceType[] getInputs() {
        return inputs;
    }

    @Command(desc = "The input resources for this resource (if manufactured)")
    public List<ResourceType> getInputList() {
        return Arrays.asList(inputs);
    }

    @Command(desc = "The name of this resource")
    public String getName() {
        return name;
    }

    @Command(desc = "If this resource has a corresponding building")
    public boolean hasBuilding() {
        return getBuilding() != null;
    }

    @Command(desc = "Continents of this resource (if any)")
    public Set<Continent> getContinents() {
        Building building = getBuilding();
        if (building == null) return Collections.emptySet();
        return building.getContinents();
    }

    @Command(desc = "If this resource is on the given continent")
    public boolean canProduceInAny(@NoFormat Set<Continent> continents) {
        Building building = getBuilding();
        if (building == null) return false;
        for (Continent continent : continents) {
            if (building.canBuild(continent)) return true;
        }
        return false;
    }

    @Command(desc = "The total production of resources for nations")
    public Map<ResourceType, Double> getProduction(@NoFormat Set<DBNation> nations, boolean includeNegatives) {
        double[] total = ResourceType.getBuffer();
        for (DBNation nation : nations) {
            double[] revenue = nation.getRevenue();
            if (includeNegatives) {
                ResourceType.add(total, revenue);
            } else {
                for (int i = 0; i < revenue.length; i++) {
                    if (revenue[i] > 0) {
                        total[i] += revenue[i];
                    }
                }
            }
        }
        return resourcesToMap(total);
    }

    public String getBankString() {
        return bankString;
    }

    public static interface IResourceArray {
        public double[] get();

        public static IResourceArray create(double[] resources) {
            int numResources = 0;
            boolean noCredits = resources[CREDITS.ordinal()] == 0;
            ResourceType latestType = null;
            for (ResourceType type : link.locutus.discord.apiv1.enums.ResourceType.values) {
                double amt = resources[type.ordinal()];
                if (amt > 0) {
                    numResources++;
                    latestType = type;
                }
            }
            switch (numResources) {
                case 0:
                    return new EmptyResources();
                case 1:
                    return new ResourceAmtCents(latestType, resources[latestType.ordinal()]);
                case 2, 3, 4, 5, 6, 7, 8, 9, 10:
                    return new VarArray(resources);
                default:
                    if (noCredits) {
                        return new ArrayNoCredits(resources);
                    } else {
                        return new VarArray(resources);
                    }
            }
        }
    }

    public static class EmptyResources implements IResourceArray{

        @Override
        public double[] get() {
            return ResourceType.getBuffer();
        }
    }

    public static class ArrayNoCredits implements IResourceArray {
        private final byte[] data;

        public ArrayNoCredits(double[] loot) {
            FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
            for (ResourceType type : link.locutus.discord.apiv1.enums.ResourceType.values) {
                if (type == link.locutus.discord.apiv1.enums.ResourceType.CREDITS) continue;
                try {
                    IOUtil.writeVarLong(baos, (long) (loot[type.ordinal()] * 100));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            baos.trim();
            this.data = baos.array;
        }
        public double[] get() {
            double[] data = ResourceType.getBuffer();
            FastByteArrayInputStream in = new FastByteArrayInputStream(this.data);
            for (ResourceType type : link.locutus.discord.apiv1.enums.ResourceType.values) {
                if (type == link.locutus.discord.apiv1.enums.ResourceType.CREDITS) continue;
                try {
                    data[type.ordinal()] = IOUtil.readVarLong(in) / 100d;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return data;
        }
    }

    public static class VarArray implements IResourceArray {
        private final byte[] data;

        public VarArray(double[] loot) {
            FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
            for (ResourceType type : link.locutus.discord.apiv1.enums.ResourceType.values) {
                long amtCents = (long) (loot[type.ordinal()] * 100);
                if (amtCents == 0) continue;
                try {
                    long pair = type.ordinal() + (amtCents << 4);
                    IOUtil.writeVarLong(baos, pair);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            baos.trim();
            this.data = baos.array;
        }

        public double[] get() {
            double[] data = ResourceType.getBuffer();
            ByteArrayInputStream in = new ByteArrayInputStream(this.data);
            while (in.available() > 0) {
                try {
                    long pair = IOUtil.readVarLong(in);
                    int type = (int) (pair & 0xF);
                    long amtCents = pair >> 4;
                    data[type] = amtCents / 100d;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return data;
        }
    }

    public static class ResourceAmtCents implements IResourceArray {
        private final long data;

        public ResourceAmtCents(ResourceType type, double amount) {
            this.data = type.ordinal() + ((int)(amount * 100d) << 4);
        }

        public ResourceType getType() {
            return link.locutus.discord.apiv1.enums.ResourceType.values[(int) (data & 0xF)];
        }
        public long getAmountCents() {
            return data >> 4;
        }

        @Override
        public double[] get() {
            return getType().toArray(getAmountCents() / 100d);
        }
    }
}
