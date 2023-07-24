package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors.*;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

import java.nio.ByteBuffer;

public class AttackCursor {
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

    public AttackCursor() {
        this.buffer = new BitBuffer(ByteBuffer.wrap(new byte[SIZE]));
    }

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

    public IAttack2 load(DBWar war, byte[] data) {
        buffer.setBytes(data);
        AttackType type = AttackType.values[(int) buffer.readBits(4)];
        AbstractCursor cursor = getCursor(type);
        if (cursor == null) {
            throw new UnsupportedOperationException("Attack type not supported: " + type);
        }
        cursor.load(war, buffer);
        return cursor;
    }
}
