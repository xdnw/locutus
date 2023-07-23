package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.Locutus;
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

    public abstract AttackType getAttackType();
    public abstract SuccessType getSuccess();
    public abstract void load(WarAttack attack);

    public abstract void load(DBWar war, BitBuffer input);
    public abstract void serialze(BitBuffer output);

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
            war_cached = Locutus.imp().getWarDb().getWar(war_id);
        }
        return war_cached;
    }
}
