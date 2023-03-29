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

    EXPIRE(GRANT, "Will be excluded from deposits after the specified time e.g. `#expire=60d`"),
    ;

    private final String description;
    private DepositType parent;

    DepositType(String description) {
        this(null, null);
    }

    DepositType(DepositType parent, String description) {
        this.parent = parent;
        this.description = description;
    }

    public DepositType getParent() {
        return parent;
    }

    public Info withValue(long value) {
        return new DepositType.Info(this, value);
    }

    public static class Info {
        public final DepositType type;
        public final long value;

        public Info(DepositType type, long value) {
            this.type = type;
            this.value = value;
        }

        public Info(DepositType type) {
            this(type, -1);
        }

        public DepositType getType() {
            return type;
        }

        public long getValue() {
            return value;
        }

        public String toString(long accountId) {
            String result = toString();
            if (accountId != 0) {
                if (type.parent == null) {
                    if (result.contains("=")) {
                        throw new IllegalArgumentException("Deposit type " + type.name() + " does not support a value");
                    }
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
            if (value == -1) {
                note += "=*";
            } else if (value != 0) {
                note += "=" + value;
            }
        }
    }
}