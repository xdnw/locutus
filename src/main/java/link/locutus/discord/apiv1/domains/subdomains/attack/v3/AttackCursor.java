package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors.*;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

import java.nio.ByteBuffer;

public class AttackCursor {
    private static final int SIZE = 256;
    private final BitBuffer buffer;
    private DBWar war;

    private GroundCursor groundCursor = new GroundCursor();
    private AirSoldierCursor airSoldierCursor = new AirSoldierCursor();
    private AirTankCursor airTankCursor = new AirTankCursor();
    private DogfightCursor dogfightCursor = new DogfightCursor();
    private NavalCursor navalCursor = new NavalCursor();

    public AttackCursor() {
        this.buffer = new BitBuffer(ByteBuffer.wrap(new byte[SIZE]));
    }

    public IAttack2 load(DBWar war, byte[] data) {
        buffer.setBytes(data);
        AttackType type = AttackType.values[(int) buffer.readBits(4)];

        this.war = war;
        switch (type) {
            case GROUND -> {
                groundCursor.load(war, buffer);
                return groundCursor;
            }
            case NAVAL -> {
                this.navalCursor.load(war, buffer);
                return navalCursor;
            }
            case AIRSTRIKE_INFRA -> {
                this
            }
            case AIRSTRIKE_SOLDIER -> {
            }
            case AIRSTRIKE_TANK -> {
            }
            case AIRSTRIKE_MONEY -> {
            }
            case AIRSTRIKE_SHIP -> {
            }
            case AIRSTRIKE_AIRCRAFT -> {
            }
            case VICTORY -> {
            }
            case FORTIFY -> {
            }
            case A_LOOT -> {
            }
            case PEACE -> {
            }
            case MISSILE -> {
            }
            case NUKE -> {
            }
        }
        /*
    GROUND(3, 10, MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT),
    VICTORY(0, 0),
    FORTIFY(3, 0),
    A_LOOT("Alliance Loot", 0, 0),
    AIRSTRIKE_INFRA("Airstrike Infrastructure", 4, 12, MilitaryUnit.AIRCRAFT), // infra
    AIRSTRIKE_SOLDIER("Airstrike Soldiers", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.SOLDIER),
    AIRSTRIKE_TANK("Airstrike Tanks", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.TANK),
    AIRSTRIKE_MONEY("Airstrike Money", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.MONEY),
    AIRSTRIKE_SHIP("Airstrike Ships", 4, 12, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP),
    AIRSTRIKE_AIRCRAFT("Dogfight", 4, 12, MilitaryUnit.AIRCRAFT), // airstrike aircraft
    NAVAL(4, 14, MilitaryUnit.SHIP),
    PEACE(0, 0),
    MISSILE(8, 18, MilitaryUnit.MISSILE),
    NUKE(12, 25, MilitaryUnit.NUKE),
         */


        // WarAttack -> byte[]
        // byte[] -> GroundCursor
        // Serializer for each attack type
    }
}
