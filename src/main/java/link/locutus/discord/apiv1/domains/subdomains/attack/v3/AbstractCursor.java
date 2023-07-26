package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

public abstract class AbstractCursor implements IAttack2 {
    protected DBWar war_cached;
    protected int war_attack_id;
    protected long date;
    protected int war_id;
    protected int attacker_id;
    protected int defender_id;

    public void load(DBAttack legacy) {
        war_attack_id = legacy.getWar_attack_id();
        date = legacy.getDate();
        war_id = legacy.getWar_id();
        attacker_id = legacy.getAttacker_nation_id();
        defender_id = legacy.getDefender_nation_id();
    }

    @Override
    public abstract AttackType getAttack_type();
    @Override
    public abstract SuccessType getSuccess();
    public void load(WarAttack attack) {
        war_cached = null;
        war_attack_id = attack.getId();
        date = attack.getDate().toEpochMilli();
        war_id = attack.getWar_id();
        attacker_id = attack.getAtt_id();
        defender_id = attack.getDef_id();
    }

    public void serialze(BitBuffer output) {
        output.writeInt(war_attack_id);
        output.writeLong(date);
        output.writeInt(war_id);
        output.writeBit(attacker_id > defender_id);
    }

    public void load(DBWar war, BitBuffer input) {
        war_cached = war;
        war_attack_id = input.readInt();
        date = input.readLong();
        war_id = input.readInt();
        boolean isAttackerGreater = input.readBit();
        if (war.attacker_id > war.defender_id) {
            if (isAttackerGreater) {
                attacker_id = war.attacker_id;
                defender_id = war.defender_id;
            } else {
                attacker_id = war.defender_id;
                defender_id = war.attacker_id;
            }
        } else {
            if (isAttackerGreater) {
                attacker_id = war.defender_id;
                defender_id = war.attacker_id;
            } else {
                attacker_id = war.attacker_id;
                defender_id = war.defender_id;
            }
        }
    }


    @Override
    public boolean isAttackerIdGreater() {
        return attacker_id > defender_id;
    }

    @Override
    public int getAttacker_id() {
        return attacker_id;
    }

    @Override
    public int getDefender_id() {
        return defender_id;
    }

    public int getWar_attack_id() {
        return war_attack_id;
    }

    public long getDate() {
        return date;
    }

    public int getWar_id() {
        return war_id;
    }

    public DBWar getWar() {
        if (war_cached == null) {
            Locutus lc = Locutus.imp();
            if (lc != null) {
                war_cached = lc.getWarDb().getWar(war_id);
            }
        }
        return war_cached;
    }
}
