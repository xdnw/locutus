package link.locutus.discord.apiv1.enums;

import java.util.Locale;

public enum DepositType {
    DEPOSIT("For funds directly deposited or withdrawn"),
    TAX("For city raw consumption or taxes"),
    LOAN("For funds members are expected to repay at some date in the future"),
    GRANT("Can be excluded from withdrawal limit, considered a loan if no time is specified e.g. `#expire=60d`"),
    IGNORE("Excluded from deposits"),

    CITY(GRANT, "Go to <https://politicsandwar.com/city/create/> and purchase a new city"),
    PROJECT(GRANT, "Go to <https://politicsandwar.com/nation/projects/> and purchase the desired project"),
    INFRA(GRANT, "Go to your city <https://politicsandwar.com/cities/> and purchase the desired infrastructure"),
    LAND(GRANT, "Go to your city <https://politicsandwar.com/cities/> and purchase the desired land"),
    BUILD(GRANT, "Go to <https://politicsandwar.com/city/improvements/bulk-import/> and import the desired build"),
    WARCHEST(GRANT, "Go to <https://politicsandwar.com/nation/military/> and purchase the desired units"),

    RAWS(GRANT, "Raw resources for city consumption"),

    EXPIRE(GRANT, "Will be excluded from deposits after the specified time e.g. `#expire=60d`"),

    ;

    private final String description;
    private DepositType parent;

    DepositType(String description) {
        this(null, description);
    }

    DepositType(DepositType parent, String description) {
        this.parent = parent;
        this.description = description;
    }

    public String getDescription() {
        return description;
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

        public boolean isIgnored() {
            return ignore || type == IGNORE;
        }
    }
}