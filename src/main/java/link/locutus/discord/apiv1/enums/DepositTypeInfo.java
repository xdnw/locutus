package link.locutus.discord.apiv1.enums;

import java.util.Locale;
import java.util.Map;

public class DepositTypeInfo {
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
            if (type.getParent() == null) {
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
                result = "#" + type.getParent().name().toLowerCase(Locale.ROOT) + "=" + accountId + " " + result;
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
        if (ignore && type != DepositType.IGNORE) {
            note += " #ignore";
        }
        return note.trim();
    }

    public boolean isDeposits() {
        return (type == DepositType.DEPOSIT || type == DepositType.TRADE) && !isIgnored();
    }

    public boolean isIgnored() {
        return ignore || type == DepositType.IGNORE;
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
