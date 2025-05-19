package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors.*;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.AttackEntry;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.BitBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;

public class AttackCursorFactory {
    private static final int SIZE = 1024;
    private final BitBuffer buffer;
    private final AirInfraCursor airInfraCursor = new AirInfraCursor();
    private final AirMoneyCursor airMoneyCursor = new AirMoneyCursor();
    private final AirShipCursor airShipCursor = new AirShipCursor();
    private final AirSoldierCursor airSoldierCursor = new AirSoldierCursor();
    private final AirTankCursor airTankCursor = new AirTankCursor();
    private final ALootCursor aLootCursor = new ALootCursor();
    private final DogfightCursor dogfightCursor = new DogfightCursor();
    private final FortifyCursor fortifyCursor = new FortifyCursor();
    private final GroundCursor groundCursor = new GroundCursor();
    private final MissileCursor missileCursor = new MissileCursor();
    private final NavalCursor navalCursor = new NavalCursor();
    private final NavalAirCursor navalAirCursor = new NavalAirCursor();
    private final NavalGroundCursor navalGroundCursor = new NavalGroundCursor();
    private final NavalAirCursor navalInfraCursor = new NavalAirCursor();
    private final NukeCursor nukeCursor = new NukeCursor();
    private final PeaceCursor peaceCursor = new PeaceCursor();
    private final VictoryCursor victoryCursor = new VictoryCursor();
    private final WarDB db;

    public AttackCursorFactory(WarDB db) {
        this.db = db;
        this.buffer = new BitBuffer(ByteBuffer.wrap(new byte[SIZE]).order(ByteOrder.LITTLE_ENDIAN));
    }

    private AbstractCursor create(AttackType type) {
        switch (type) {
            case GROUND -> {
                return new GroundCursor();
            }
            case NAVAL -> {
                return new NavalCursor();
            }
            case NAVAL_AIR -> {
                return new NavalAirCursor();
            }
            case NAVAL_INFRA -> {
                return new NavalAirCursor();
            }
            case NAVAL_GROUND -> {
                return new NavalGroundCursor();
            }
            case AIRSTRIKE_INFRA -> {
                return new AirInfraCursor();
            }
            case AIRSTRIKE_SOLDIER -> {
                return new AirSoldierCursor();
            }
            case AIRSTRIKE_TANK -> {
                return new AirTankCursor();
            }
            case AIRSTRIKE_MONEY -> {
                return new AirMoneyCursor();
            }
            case AIRSTRIKE_SHIP -> {
                return new AirShipCursor();
            }
            case AIRSTRIKE_AIRCRAFT -> {
                return new DogfightCursor();
            }
            case FORTIFY -> {
                return new FortifyCursor();
            }
            case MISSILE -> {
                return new MissileCursor();
            }
            case NUKE -> {
                return new NukeCursor();
            }
            case PEACE -> {
                return new PeaceCursor();
            }
            case VICTORY -> {
                return new VictoryCursor();
            }
            case A_LOOT -> {
                return new ALootCursor();
            }
            default -> {
                throw new UnsupportedOperationException("Attack type not supported: " + type);
            }
        }
    }

    private Object lock = new Object();

    private AbstractCursor getCursor(AttackType type) {
        switch (type) {
            case GROUND -> {
                return groundCursor;
            }
            case NAVAL -> {
                return navalCursor;
            }
            case NAVAL_AIR -> {
                return navalAirCursor;
            }
            case NAVAL_GROUND -> {
                return navalGroundCursor;
            }
            case NAVAL_INFRA -> {
                return navalInfraCursor;
            }
            case AIRSTRIKE_INFRA -> {
                return airInfraCursor;
            }
            case AIRSTRIKE_SOLDIER -> {
                return airSoldierCursor;
            }
            case AIRSTRIKE_TANK -> {
                return airTankCursor;
            }
            case AIRSTRIKE_MONEY -> {
                return airMoneyCursor;
            }
            case AIRSTRIKE_SHIP -> {
                return airShipCursor;
            }
            case AIRSTRIKE_AIRCRAFT -> {
                return dogfightCursor;
            }
            case FORTIFY -> {
                return fortifyCursor;
            }
            case MISSILE -> {
                return missileCursor;
            }
            case NUKE -> {
                return nukeCursor;
            }
            case PEACE -> {
                return peaceCursor;
            }
            case VICTORY -> {
                return victoryCursor;
            }
            case A_LOOT -> {
                return aLootCursor;
            }
            default -> {
                return null;
            }
        }
    }

    public AbstractCursor load(DBAttack legacy, boolean create) {
        AttackType type = legacy.getAttack_type();
        AbstractCursor cursor = create ? create(type) : getCursor(type);
        if (cursor == null) {
            throw new UnsupportedOperationException("Attack type not supported: " + type);
        }

        // correct the values instead if possible
        // infra_destroyed -> 0 if negative
        if (legacy.getInfra_destroyed() < 0) {
            legacy.setInfra_destroyed(0);
        }
        // date -> war date if negative
        if (legacy.getDate() < 0) {
            DBWar war = legacy.getWar();
            if (war != null) {
                legacy.setDate(war.getDate());
            } else {
                legacy.setDate(0);
            }
        }
        // money_looted -> assume 0 if negative
        if (legacy.getMoney_looted() < 0) {
            legacy.setMoney_looted(0);
        }
        // att/def gas/mun assume 0 if negative
        if (legacy.getAtt_gas_used() < 0) {
            legacy.setAtt_gas_used(0);
        }
        if (legacy.getAtt_mun_used() < 0) {
            legacy.setAtt_mun_used(0);
        }
        if (legacy.getDef_gas_used() < 0) {
            legacy.setDef_gas_used(0);
        }
        if (legacy.getDef_mun_used() < 0) {
            legacy.setDef_mun_used(0);
        }
        // defcas1, defcas2 -> assume 0 if negative
        if (legacy.getDefcas1() < 0) {
            legacy.setDefcas1(0);
        }
        if (legacy.getDefcas2() < 0) {
            legacy.setDefcas2(0);
        }
        // attcas1, attcas2 -> assume 0 if negative
        if (legacy.getAttcas1() < 0) {
            legacy.setAttcas1(0);
        }
        if (legacy.getAttcas2() < 0) {
            legacy.setAttcas2(0);
        }

        checkArgument(legacy.getWar_attack_id() >= 0, "war_attack_id");
        checkArgument(legacy.getDate() >= 0, "date " + legacy.getDate());
        checkArgument(legacy.getWar_id() >= 0, "war_id");
        checkArgument(legacy.getAttacker_id() >= 0, "attacker_nation_id");
        checkArgument(legacy.getDefender_id() >= 0, "defender_nation_id");
        // skip victor
        checkArgument(legacy.getSuccess() >= 0, "success");
        checkArgument(legacy.getAttcas1() >= 0, "attcas1");
        checkArgument(legacy.getAttcas2() >= 0, "attcas2");
        checkArgument(legacy.getDefcas1() >= 0, "defcas1 " + legacy.getDefcas1());
        checkArgument(legacy.getDefcas2() >= 0, "defcas2");
        checkArgument(legacy.getDefcas3() >= 0, "defcas3");
        checkArgument(legacy.getInfra_destroyed() >= 0, "infra_destroyed " + legacy.getInfra_destroyed());
        checkArgument(legacy.getImprovements_destroyed() >= 0, "improvements_destroyed");
        checkArgument(legacy.getMoney_looted() >= 0, "money_looted " + legacy.getMoney_looted());
        // ensure looted is either null or all values are not negative
        if (legacy.loot != null) {
            for (ResourceType r : ResourceType.values) {
                double amt = legacy.loot[r.ordinal()];
                checkArgument(amt >= 0, "loot[" + r + "] = " + amt);
            }
        }
        checkArgument(legacy.getLooted() >= 0, "looted");
        checkArgument(legacy.getLootPercent() >= 0, "lootPercent");
        checkArgument(legacy.getCity_infra_before() >= 0, "city_infra_before");
        checkArgument(legacy.getInfra_destroyed_value() >= 0, "infra_destroyed_value");
        checkArgument(legacy.getAtt_gas_used() >= 0, "att_gas_used");
        checkArgument(legacy.getAtt_mun_used() >= 0, "att_mun_used");
        checkArgument(legacy.getDef_gas_used() >= 0, "def_gas_used");
        checkArgument(legacy.getDef_mun_used() >= 0, "def_mun_used");

        cursor.load(legacy);
        return cursor;
    }

    public synchronized byte[] toBytes(AbstractCursor cursor) {
        buffer.reset();
        buffer.writeBits(cursor.getAttack_type().ordinal(), 4);
        cursor.serialize(buffer);
        return buffer.getWrittenBytes();
    }

    public synchronized AbstractCursor load(WarAttack attack, boolean create) {
        AttackType type = AttackType.fromV3(attack.getType());
        AbstractCursor cursor = create ? create(type) : getCursor(type);
        if (cursor == null) {
            throw new UnsupportedOperationException("Attack type not supported: " + type);
        }
        cursor.load(attack, db);
        return cursor;
    }

    public synchronized AbstractCursor load(DBWar war, byte[] data, boolean create) {
        buffer.setBytes(data);
        AttackType type = AttackType.values[(int) buffer.readBits(4)];
        AbstractCursor cursor = create ? create(type) : getCursor(type);
        if (cursor == null) {
            throw new UnsupportedOperationException("Attack type not supported: " + type);
        }
        cursor.initialize(war, buffer);
        cursor.load(war, buffer);
        return cursor;
    }

    public synchronized AbstractCursor loadWithType(DBWar war, byte[] data, boolean create, Predicate<AttackType> testType) {
        buffer.setBytes(data);
        AttackType type = AttackType.values[(int) buffer.readBits(4)];
        if (!testType.test(type)) {
            return null;
        }
        AbstractCursor cursor = create ? create(type) : getCursor(type);
        if (cursor == null) {
            throw new UnsupportedOperationException("Attack type not supported: " + type);
        }
        cursor.initialize(war, buffer);
        cursor.load(war, buffer);
        return cursor;
    }

    public synchronized VictoryCursor loadWithTypeVictoryLegacy(DBWar war, byte[] data, boolean create) {
        buffer.setBytes(data);
        AttackType type = AttackType.values[(int) buffer.readBits(4)];
        if (type != AttackType.VICTORY) {
            return null;
        }
        VictoryCursor cursor = (VictoryCursor) (create ? create(type) : getCursor(type));
        if (cursor == null) {
            throw new UnsupportedOperationException("Attack type not supported: " + type);
        }
        cursor.initialize(war, buffer);
        cursor.loadLegacy(war, buffer);
        return cursor;
    }

    public synchronized int getId(byte[] data) {
        buffer.setBytes(data);
        buffer.readBits(4);
        return buffer.readInt();
    }

    public synchronized AttackEntry shouldReEncode(DBWar war, byte[] data) {
        buffer.setBytes(data);
        AttackType type = AttackType.values[(int) buffer.readBits(4)];
        AbstractCursor cursor = getCursor(type);
        if (cursor == null) {
            throw new UnsupportedOperationException("Attack type not supported: " + type);
        }
        cursor.initialize(war, buffer);
        cursor.load(war, buffer);
        byte[] out = toBytes(cursor);
        if (out.length != data.length) {
            buffer.clear();
            buffer.setBytes(data);
            buffer.readBits(4);
            cursor.initialize(war, buffer);
            cursor.load(war, buffer);
            return AttackEntry.of(cursor, this);
        }
        return null;
    }

    public synchronized AbstractCursor loadWithPretest(DBWar war, byte[] data, boolean create, Predicate<AbstractCursor> testInitial) {
        buffer.setBytes(data);

        AttackType type = AttackType.values[(int) buffer.readBits(4)];
        AbstractCursor cursor = create ? create(type) : getCursor(type);
        if (cursor == null) {
            throw new UnsupportedOperationException("Attack type not supported: " + type);
        }
        cursor.initialize(war, buffer);
        if (!testInitial.test(cursor)) {
            return null;
        }
        cursor.load(war, buffer);
        return cursor;
    }

    public synchronized AbstractCursor loadWithTypePretest(DBWar war, byte[] data, boolean create, Predicate<AttackType> testType, Predicate<AbstractCursor> testInitial) {
        buffer.setBytes(data);
        AttackType type = AttackType.values[(int) buffer.readBits(4)];
        if (!testType.test(type)) {
            return null;
        }
        AbstractCursor cursor = create ? create(type) : getCursor(type);
        if (cursor == null) {
            throw new UnsupportedOperationException("Attack type not supported: " + type);
        }
        cursor.initialize(war, buffer);
        if (!testInitial.test(cursor)) {
            return null;
        }
        cursor.load(war, buffer);
        return cursor;
    }
}
