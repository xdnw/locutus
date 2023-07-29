package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors.*;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.google.common.base.Preconditions.checkArgument;

public class AttackCursorFactory {
    private static final int SIZE = 256;
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
    private final NukeCursor nukeCursor = new NukeCursor();
    private final PeaceCursor peaceCursor = new PeaceCursor();
    private final VictoryCursor victoryCursor = new VictoryCursor();

    public AttackCursorFactory() {
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
                return null;
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

    public AbstractCursor load(AbstractCursor legacy, boolean create) {
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
        checkArgument(legacy.getAttacker_nation_id() >= 0, "attacker_nation_id");
        checkArgument(legacy.getDefender_nation_id() >= 0, "defender_nation_id");
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
        cursor.serialze(buffer);
        return buffer.getWrittenBytes();
    }

    public synchronized AbstractCursor load(WarAttack attack, boolean create) {
        AttackType type = AttackType.fromV3(attack.getType());

        // validate all the non deprecated fields

        //     private Integer id;
        //    private java.time.Instant date;
        //    private Integer att_id;
        //    private Nation attacker;
        //    private Integer def_id;
        //    private Nation defender;
        //    private AttackType type;
        //    private Integer war_id;
        //    private War war;
        //    private Integer victor;
        //    private Integer success;
        //    private Integer attcas1;
        //    private Integer defcas1;
        //    private Integer attcas2;
        //    private Integer defcas2;
        //    private Integer city_id;
        //    private Double infra_destroyed;
        //    private Integer improvements_lost;
        //    private Double money_stolen;
        //    private String loot_info;
        //    private Integer resistance_lost;
        //    private Double city_infra_before;
        //    private Double infra_destroyed_value;
        //    private Double att_mun_used;
        //    private Double def_mun_used;
        //    private Double att_gas_used;
        //    private Double def_gas_used;
        //    private Integer aircraft_killed_by_tanks;
        //    private Double money_destroyed;
        //    private Double military_salvage_aluminum;
        //    private Double military_salvage_steel;
        //    private Integer att_soldiers_used;
        //    private Integer att_soldiers_lost;
        //    private Integer def_soldiers_used;
        //    private Integer def_soldiers_lost;
        //    private Integer att_tanks_used;
        //    private Integer att_tanks_lost;
        //    private Integer def_tanks_used;
        //    private Integer def_tanks_lost;
        //    private Integer att_aircraft_used;
        //    private Integer att_aircraft_lost;
        //    private Integer def_aircraft_used;
        //    private Integer def_aircraft_lost;
        //    private Integer att_ships_used;
        //    private Integer att_ships_lost;
        //    private Integer def_ships_used;
        //    private Integer def_ships_lost;
        //    private Integer att_missiles_used;
        //    private Integer att_missiles_lost;
        //    private Integer def_missiles_used;
        //    private Integer def_missiles_lost;
        //    private Integer att_nukes_used;
        //    private Integer att_nukes_lost;
        //    private Integer def_nukes_used;
        //    private Integer def_nukes_lost;
        //    private java.util.List<String> improvements_destroyed;
        //    private Double infra_destroyed_percentage;
        //    private java.util.List<CityInfraDamage> cities_infra_before;
        //    private Double money_looted;
        //    private Integer coal_looted;
        //    private Integer oil_looted;
        //    private Integer uranium_looted;
        //    private Integer iron_looted;
        //    private Integer bauxite_looted;
        //    private Integer lead_looted;
        //    private Integer gasoline_looted;
        //    private Integer munitions_looted;
        //    private Integer steel_looted;
        //    private Integer aluminum_looted;
        //    private Integer food_looted;

        // allow either null or negative

        AbstractCursor cursor = create ? create(type) : getCursor(type);
        if (cursor == null) {
            throw new UnsupportedOperationException("Attack type not supported: " + type);
        }
        cursor.load(attack);
        return cursor;
    }

    public synchronized AbstractCursor load(DBWar war, byte[] data, boolean create) {
        buffer.setBytes(data);
        AttackType type = AttackType.values[(int) buffer.readBits(4)];
        AbstractCursor cursor = create ? create(type) : getCursor(type);
        if (cursor == null) {
            throw new UnsupportedOperationException("Attack type not supported: " + type);
        }
        cursor.load(war, buffer);
        return cursor;
    }
}
