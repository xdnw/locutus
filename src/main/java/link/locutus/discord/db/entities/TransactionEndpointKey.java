package link.locutus.discord.db.entities;

import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import org.jooq.Condition;
import org.jooq.Field;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class TransactionEndpointKey {
    public static final int NONE_TYPE = 0;
    public static final int NATION_TYPE = 1;
    public static final int ALLIANCE_TYPE = 2;
    public static final int GUILD_TYPE = 3;
    public static final int TAX_TYPE = 4;

    public static final int TYPE_BITS = 3;
    public static final long TYPE_MASK = (1L << TYPE_BITS) - 1;
    public static final long NONE = 0L;
    public static final long MAX_ID = -1L >>> TYPE_BITS;

    private TransactionEndpointKey() {
    }

    public static long encode(NationOrAllianceOrGuildOrTaxid account) {
        if (account == null) {
            return NONE;
        }
        return encode(account.getIdLong(), account.getReceiverType());
    }

    public static long encode(long id, int type) {
        validate(id, type);
        if (type == NONE_TYPE) {
            return NONE;
        }
        return (id << TYPE_BITS) | (type & TYPE_MASK);
    }

    public static long idFromKey(long key) {
        if (key == NONE) {
            return 0L;
        }
        return key >>> TYPE_BITS;
    }

    public static int typeFromKey(long key) {
        return (int) (key & TYPE_MASK);
    }

    public static boolean matches(long key, long id, int type) {
        return key == encode(id, type);
    }

    public static boolean matches(long endpointId, int endpointType, long id, int type) {
        return endpointId == id && endpointType == type;
    }

    public static int direction(long senderId, int senderType, long receiverId, int receiverType, long endpointId,
            int endpointType) {
        boolean senderMatches = matches(senderId, senderType, endpointId, endpointType);
        boolean receiverMatches = matches(receiverId, receiverType, endpointId, endpointType);
        if (senderMatches == receiverMatches) {
            return 0;
        }
        return senderMatches ? 1 : -1;
    }

    public static boolean hasType(long key, int type) {
        return typeFromKey(key) == type;
    }

    public static String sqlEncode(String idExpression, int type) {
        validateType(type);
        if (type == NONE_TYPE) {
            return Long.toString(NONE);
        }
        return "((CAST(" + idExpression + " AS BIGINT) << " + TYPE_BITS + ") | " + (type & TYPE_MASK) + ")";
    }

    public static Condition eq(Field<Long> field, long id, int type) {
        return field.eq(encode(id, type));
    }

    public static Condition eq(Field<Long> field, long key) {
        return field.eq(key);
    }

    public static Condition hasType(Field<Long> field, int type) {
        return field.bitAnd(TYPE_MASK).eq((long) type);
    }

    public static List<Long> expand(Collection<Long> ids, int... types) {
        List<Long> keys = new ArrayList<>(ids.size() * Math.max(types.length, 1));
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            for (int type : types) {
                keys.add(encode(id, type));
            }
        }
        return keys;
    }

    public static void validate(long id, int type) {
        validateType(type);
        if (type == NONE_TYPE) {
            if (id != 0L) {
                throw new IllegalArgumentException("Transaction endpoint NONE type requires id 0, got: " + id);
            }
            return;
        }
        if (id <= 0L) {
            throw new IllegalArgumentException("Transaction endpoint ids must be positive, got: " + id + " for type " + type);
        }
        if (id > MAX_ID) {
            throw new IllegalArgumentException("Transaction endpoint id exceeds encodable range: " + id);
        }
    }

    private static void validateType(int type) {
        if (type < NONE_TYPE || type > TAX_TYPE) {
            throw new IllegalArgumentException("Unsupported transaction endpoint type: " + type);
        }
    }
}
