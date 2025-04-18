package link.locutus.discord.apiv1.enums;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum DepositType {
    DEPOSIT("For funds directly deposited or withdrawn"),
    TAX("For city raw consumption or taxes"),
    LOAN("For funds members are expected to repay at some date in the future"),
    GRANT("Can be excluded from withdrawal limit, considered a loan if no time is specified e.g. `#expire=60d` or `#decay=3w`"),
    IGNORE("Excluded from deposits"),
    TRADE("Sub type of deposits, earmarked as trading funds"),

    CITY(GRANT, "Go to <https://{test}politicsandwar.com/city/create/> and purchase a new city", "A city grant with a value either the number of cities, -1 for all cities, or the city id\n" +
            "Can be applied alongside another modifier, e.g. `#land=2000 #city=-1` would be 2000 land for all cities",
            true) {
        @Override
        public Object resolve(String value, long timestamp) {
            Object result = super.resolve(value, timestamp);
            if (result == null) {
                if (value == null || value.isEmpty()) return null;
                if (value.contains(",")) {
                    try {
                        Set<Integer> ids = new IntOpenHashSet();
                        for (String elem : value.split(",")) {
                            ids.add(Integer.parseInt(elem));
                        }
                        return ids;
                    } catch (NumberFormatException e) {
                    }
                }
            }
            return result;
        }
    },
    PROJECT(GRANT, "Go to <https://{test}politicsandwar.com/nation/projects/> and purchase the desired project",
            "A project grant with the id or name for a value. `#project=BAUXITEWORKS`", true) {

        @Override
        public Object resolve(String value, long timestamp) {
            Object parsed = super.resolve(value, timestamp);
            if (parsed != null) {
                return parsed;
            }
            if (value == null || value.isEmpty()) return null;
            Project project = Projects.get(value);
            return project != null ? project : null;
        }
    },
    INFRA(GRANT, "Go to your city <https://{test}politicsandwar.com/cities/> and purchase the desired infrastructure",
            "A grant for infra level. Can be added with `#city`", true),
    LAND(GRANT, "Go to your city <https://{test}politicsandwar.com/cities/> and purchase the desired land", "A grant for a land level. Can be added with `#city`", true),
    BUILD(GRANT, "Go to <https://{test}politicsandwar.com/city/improvements/bulk-import/> and import the desired build", "A grant for a city build. The value is optional and is equal to `infra << 32 | land` (see: <https://bit-calculator.com/bit-shift-calculator> ). Can be added with `#city` / `#infra` / `#land`", true),
    WARCHEST(GRANT, "Go to <https://{test}politicsandwar.com/nation/military/> and purchase the desired units", "A grant for war resources", true),
    RESEARCH(GRANT, "Go to <https://{test}politicsandwar.com/nation/military/research/> and purchase the desired research", "A grant for research", true),
    RAWS(GRANT, "Raw resources for city consumption", "", true),

    EXPIRE(GRANT, "Will be excluded from deposits after the specified time e.g. `#expire=60d`", "", false) {
        @Override
        public Object resolve(String value, long timestamp) {
            try {
                if (value == null || value.isEmpty()) {
                    return null;
                }
                return timestamp + TimeUtil.timeToSec_BugFix1(value, timestamp) * 1000L;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    },
    DECAY(GRANT, "Expires by reducing linearly over time until 0 e.g. `#decay=60d`", "", false) {
        @Override
        public Object resolve(String value, long timestamp) {
            try {
                if (value == null || value.isEmpty()) {
                    return null;
                }
                return timestamp + TimeUtil.timeToSec_BugFix1(value, timestamp) * 1000L;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    },

    AMOUNT(GRANT, "Meta note for recording an amount of a tag", "Meta note for recording an amount of a tag", true),
    INCENTIVE(DEPOSIT, "Reward for government activity", "Reward for government activity", true) {
        @Override
        public Object resolve(String value, long timestamp) {
            return value;
        }
    },

    GUILD("The guild id this transfer belongs to"),
    ALLIANCE("The guild id this transfer belongs to"),
    NATION("Reserved. DO NOT USE"),
    ACCOUNT("Reserved. DO NOT USE"),
    CASH("The value of this transfer in nation balance is converted to cash"),
    RSS("The bits representing the resources in the transfer. Resource ordinals aare the same as appear in the game's bank records table"),
    BANKER("The nation initiating the transfer"),

    ;

    public static DepositType parse(String note) {
        if (note == null || note.isEmpty()) {
            return null;
        }
        if (note.charAt(0) == '#') {
            note = note.substring(1);
        }
        note = note.toUpperCase(Locale.ROOT);
        try {
            return DepositType.valueOf(note);
        } catch (IllegalArgumentException e) {
            switch (note) {
                case "RAW":
                case "DISPERSE":
                case "DISBURSE":
                    return RAWS;
                case "TAXES":
                    return TAX;
            }
            return null;
        }
    }

    private final String description;
    private DepositType parent;
    private boolean isClassifier;
    private String wikiDesc;

    DepositType(String description) {
        this(null, description, "", false);
    }

    DepositType(DepositType parent, String description, String wikiDesc, boolean isClassifier) {
        this.parent = parent;
        this.description = description;
        this.isClassifier = isClassifier;
        this.wikiDesc = wikiDesc.isEmpty() ? description : wikiDesc;
    }

    public String getWikiDesc() {
        return wikiDesc;
    }

    public boolean isClassifier() {
        return isClassifier;
    }

    public String getDescription() {
        return description.replace("{test}", Settings.INSTANCE.TEST ? "test." : "");
    }

    public DepositType getParent() {
        return parent;
    }

    public DepositTypeInfo withValue() {
        return withValue(0, 0);
    }

    public DepositTypeInfo withAmount(long amount) {
        return withValue(amount, 0);
    }

    public DepositTypeInfo withCity(long city) {
        return withValue(0, city);
    }

    public DepositTypeInfo withValue(long amount, long city) {
        return new DepositTypeInfo(this, amount, city, false);
    }

    public DepositTypeInfo withValue(long amount, long city, boolean ignore) {
        return new DepositTypeInfo(this, amount, city, ignore);
    }

    public boolean isReserved() {
        return this == GUILD || this == ALLIANCE || this == NATION || this == ACCOUNT || this == CASH || this == RSS;
    }

    public Object resolve(String value, long timestamp) {
        if (value == null || value.isEmpty()) {
             return null;
        }
        if (value.equals("*")) {
            return -1L;
        }
        if (value.endsWith(".0")) {
            value = value.substring(0, value.length() - 2);
        }
        try {
            if (MathMan.isInteger(value)) {
                return Long.parseLong(value);
            }
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static class DepositTypeInfo {
        public final DepositType type;
        public final long amount;
        public final long city;
        public boolean ignore;

        public DepositTypeInfo(DepositType type, long amount, long city, boolean ignore) {
            this.type = type;
            this.amount = amount;
            this.city = city;
            this.ignore = ignore;
        }

        public DepositTypeInfo clone() {
            return new DepositTypeInfo(type, amount, city, ignore);
        }

        public DepositTypeInfo(DepositType type) {
            this(type, 0, 0, false);
        }

        public DepositTypeInfo ignore(boolean value) {
            this.ignore = value;
            return this;
        }

        public DepositType getType() {
            return type;
        }

        public long getAmount() {
            return amount;
        }

        public long getCity() {
            return city;
        }

        public String toString(long accountId) {
            String result = toString();
            if (accountId != 0) {
                if (type.parent == null) {
                    if (result.contains("=")) {
                        throw new IllegalArgumentException("Deposit type " + type.name() + " already has a value");
                    }
                    if (result.contains("#ignore")) {
                        String typeName = type.name().toLowerCase(Locale.ROOT);
                        result = result.replace(typeName, typeName + "=" + accountId);
                    } else {
                        result += "=" + accountId;
                    }
                } else if (result.contains("#ignore")) {
                    result += "=" + accountId;
                } else {
                    result = "#" + type.parent.name().toLowerCase(Locale.ROOT) + "=" + accountId + " " + result;
                }
            }
            return result;
        }

        @Override
        public String toString() {
            String note = "#" + type.name().toLowerCase(Locale.ROOT);
            if (amount != 0) {
                note += "=" + amount;
            }
            if (city != 0) {
                note += " #city=" + city;
            }
            if (ignore && type != IGNORE) {
                note += " #ignore";
            }
            return note.trim();
        }

        public boolean isDeposits() {
            return (type == DEPOSIT || type == TRADE) && !isIgnored();
        }

        public boolean isIgnored() {
            return ignore || type == IGNORE;
        }

        public boolean isReservedOrIgnored() {
            return type.isReserved() || isIgnored();
        }

        public DepositTypeInfo applyClassifiers(Map<DepositType, Object> parsed2) {
            for (Map.Entry<DepositType, Object> noteEntry2 : parsed2.entrySet()) {
                DepositType type = noteEntry2.getKey();
                boolean ignore = isIgnored();
                if (!type.isClassifier()) {
                    throw new IllegalArgumentException();
                }
                long amount = getAmount();
                Object value = noteEntry2.getValue();
                if (value instanceof Number n) {
                    amount = n.longValue();
                }
                return new DepositTypeInfo(type, amount, 0, ignore);
            }
            return this;
        }
    }
}