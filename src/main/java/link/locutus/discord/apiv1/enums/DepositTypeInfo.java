package link.locutus.discord.apiv1.enums;

import link.locutus.discord.db.entities.TransactionNote;

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

    public String toLegacyString(long accountId) {
        String legacy = toTransactionNote(accountId).toLegacyString();
        return legacy == null ? "" : legacy;
    }

    public String toString(long accountId) {
        return toLegacyString(accountId);
    }

    public TransactionNote toTransactionNote() {
        return toTransactionNote(0);
    }

    public TransactionNote toTransactionNote(long accountId) {
        TransactionNote.Builder builder = TransactionNote.builder();

        Object typeValue = amount == 0 ? null : amount;
        if (accountId != 0) {
            if (type.getParent() == null) {
                if (typeValue != null) {
                    throw new IllegalArgumentException("Deposit type " + type.name() + " already has a value");
                }
                typeValue = accountId;
            } else if (ignore && type != DepositType.IGNORE) {
                builder.put(DepositType.IGNORE, accountId);
            } else {
                builder.put(type.getParent(), accountId);
            }
        }

        builder.put(type, typeValue);
        if (city != 0) {
            builder.put(DepositType.CITY, city);
        }
        if (ignore && type != DepositType.IGNORE && (accountId == 0 || type.getParent() == null)) {
            builder.put(DepositType.IGNORE);
        }
        return builder.build();
    }

    public Map<DepositType, Object> toParsedNote() {
        return toTransactionNote().asMap();
    }

    public Map<DepositType, Object> toParsedNote(long accountId) {
        return toTransactionNote(accountId).asMap();
    }

    public String toLegacyString() {
        String legacy = toTransactionNote().toLegacyString();
        return legacy == null ? "" : legacy;
    }

    @Override
    public String toString() {
        return toLegacyString();
    }

    public boolean isIncludedInDeposits() {
        return (type == DepositType.DEPOSIT || type == DepositType.TRADE || type == DepositType.TAX) && !isIgnored();
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
