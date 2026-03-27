package link.locutus.discord.apiv1.enums;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.BitBuffer;

import java.util.Collections;
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

    CITY(GRANT, "Go to <https://{test}politicsandwar.com/city/create/> and purchase a new city",
            "A city grant with a value either the number of cities, -1 for all cities, or the city id\n" +
                    "Can be applied alongside another modifier, e.g. `#land=2000 #city=-1` would be 2000 land for all cities",
            true) {
        @Override
        public Object resolve(String value, long timestamp) {
            Object result = super.resolve(value, timestamp);
            if (result == null) {
                if (value == null || value.isEmpty())
                    return null;
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

        @Override
        public void write(BitBuffer out, Object value) {
            if (value instanceof Number n) {
                int id = n.intValue();
                out.writeVarInt(1);
                out.writeVarInt(id);
            } else if (value instanceof Set s) {
                Set<Integer> ids = s;
                out.writeVarInt(ids.size());
                for (Integer id : ids) {
                    out.writeVarInt(id);
                }
            }
        }

        @Override
        public Object read(BitBuffer input) {
            int size = input.readVarInt();
            if (size == 1) {
                return input.readVarInt();
            } else if (size > 1) {
                IntOpenHashSet ids = new IntOpenHashSet(size);
                for (int i = 0; i < size; i++) {
                    ids.add(input.readVarInt());
                }
                return ids;
            }
            return null;
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
            if (value == null || value.isEmpty())
                return null;
            Project project = Projects.get(value);
            return project;
        }

        @Override
        public void write(BitBuffer out, Object value) {
            out.writeByte(((Project) value).ordinal());
        }

        @Override
        public Object read(BitBuffer input) {
            return Projects.get(input.readByte());
        }
    },
    INFRA(GRANT, "Go to your city <https://{test}politicsandwar.com/cities/> and purchase the desired infrastructure",
            "A grant for infra level. Can be added with `#city`", true) {
        @Override
        public Object read(BitBuffer input) {
            return input.readDouble();
        }

        @Override
        public void write(BitBuffer out, Object value) {
            out.writeDouble(((Number) value).doubleValue());
        }
    },
    LAND(GRANT, "Go to your city <https://{test}politicsandwar.com/cities/> and purchase the desired land",
            "A grant for a land level. Can be added with `#city`", true) {
        @Override
        public Object read(BitBuffer input) {
            return input.readDouble();
        }

        @Override
        public void write(BitBuffer out, Object value) {
            out.writeDouble(((Number) value).doubleValue());
        }
    },
    BUILD(GRANT, "Go to <https://{test}politicsandwar.com/city/improvements/bulk-import/> and import the desired build",
            "A grant for a city build. The value is optional and is equal to `infra << 32 | land` (see: <https://bit-calculator.com/bit-shift-calculator> ). Can be added with `#city` / `#infra` / `#land`",
            true),
    WARCHEST(GRANT, "Go to <https://{test}politicsandwar.com/nation/military/> and purchase the desired units",
            "A grant for war resources", true),
    RESEARCH(GRANT,
            "Go to <https://{test}politicsandwar.com/nation/military/research/> and purchase the desired research",
            "A grant for research", true),
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

        @Override
        public void write(BitBuffer out, Object value) {
            out.writeVarLong((long) value);
        }

        @Override
        public Object read(BitBuffer input) {
            return input.readVarLong();
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

        @Override
        public void write(BitBuffer out, Object value) {
            out.writeVarLong((long) value);
        }

        @Override
        public Object read(BitBuffer input) {
            return input.readVarLong();
        }
    },

    AMOUNT(GRANT, "Meta note for recording an amount of a tag", "Meta note for recording an amount of a tag", true),
    INCENTIVE(DEPOSIT, "Reward for government activity", "Reward for government activity", true) {
        @Override
        public Object resolve(String value, long timestamp) {
            return value;
        }

        @Override
        public void write(BitBuffer out, Object value) {
            out.writeByte(((NationMeta) value).ordinal());
        }

        @Override
        public Object read(BitBuffer input) {
            return NationMeta.values[input.readByte()];
        }
    },

    GUILD("The guild id this transfer belongs to"),
    ALLIANCE("The guild id this transfer belongs to"),
    NATION("Reserved. DO NOT USE"),
    ACCOUNT("Reserved. DO NOT USE"),
    CASH("The value of this transfer in nation balance is converted to cash") {
        @Override
        public Object read(BitBuffer input) {
            return input.readDouble();
        }

        @Override
        public void write(BitBuffer out, Object value) {
            out.writeDouble(((Number) value).doubleValue());
        }
    },
    RSS("The bits representing the resources in the transfer. Resource ordinals aare the same as appear in the game's bank records table"),
    BANKER("The nation initiating the transfer"),
    RECEIVER_ID("The receiving nation or alliance id"),
    RECEIVER_TYPE("The receiver type (0 = nation, 1 = alliance, 2 == guild, 3 == tax account)"),

    ;

    public static final DepositType[] values = values();

    public static void serialize(Map<DepositType, Object> data, BitBuffer buffer) {
        if (data == null || data.isEmpty()) {
            buffer.writeBit(false);
            return;
        }
        buffer.writeBit(true);
        buffer.writeBits(data.size(), 5);
        for (Map.Entry<DepositType, Object> entry : data.entrySet()) {
            DepositType type = entry.getKey();
            buffer.writeBits(type.ordinal(), 5);
            Object value = entry.getValue();
            if (value == null) {
                buffer.writeBit(false);
            } else {
                buffer.writeBit(true);
                type.write(buffer, value);
            }
        }
    }

    public static Map<DepositType, Object> readMap(BitBuffer buffer) {
        if (!buffer.readBit()) {
            return Collections.emptyMap();
        }
        int size = (int) buffer.readBits(5);
        Map<DepositType, Object> data = new Object2ObjectOpenHashMap<>(size);
        for (int i = 0; i < size; i++) {
            DepositType type = DepositType.values[(int) buffer.readBits(5)];
            if (buffer.readBit()) {
                data.put(type, type.read(buffer));
            } else {
                data.put(type, null);
            }
        }
        return data;
    }

    public static Map<DepositType, Object> readMap(byte[] bytes, BitBuffer buffer) {
        buffer.setBytes(bytes);
        return readMap(buffer);
    }

    public void write(BitBuffer out, Object value) {
        out.writeVarLong(((Number) value).longValue());
    };

    public Object read(BitBuffer input) {
        return input.readVarLong();
    }

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
    private final DepositType parent;
    private final boolean isClassifier;
    private final String wikiDesc;

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

    public Map<DepositType, Object> toParsedNote() {
        return Collections.singletonMap(this, null);
    }

    public boolean isReserved() {
        return this == GUILD || this == ALLIANCE || this == NATION || this == ACCOUNT || this == CASH || this == RSS
                || this == BANKER || this == RECEIVER_TYPE || this == RECEIVER_ID;
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

    public static boolean hasLegacyRootAccountTag(DepositType type) {
        return switch (type) {
            case DEPOSIT, TAX, LOAN, GRANT, IGNORE, TRADE -> true;
            default -> false;
        };
    }

}