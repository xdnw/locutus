package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.entities.DBWar;

public record WarAttackWrapper(DBWar war, IAttack attack) implements IAttack {

    public DBWar getWar() {
        return war;
    }

    public IAttack getAttack() {
        return attack;
    }

    public int getLooted() {
        switch (attack.getAttack_type()) {
            case A_LOOT -> {
                return getDefenderAA();
            }
            case VICTORY -> {
                return getDefenderId();
            }
            default -> {
                return 0;
            }
        }
    }

    public int getWarId() {
        return war.warId;
    }
    public int getAttackerId() {
        if (attack.isAttackerIdGreater()) {
            return Math.max(war.attacker_id, war.defender_id);
        } else {
            return Math.min(war.attacker_id, war.defender_id);
        }
    }

    public int getDefenderId() {
        if (!attack.isAttackerIdGreater()) {
            return Math.max(war.attacker_id, war.defender_id);
        } else {
            return Math.min(war.attacker_id, war.defender_id);
        }
    }

    public int getAttackerAA() {
        if (attack.isAttackerIdGreater()) {
            return war.attacker_id > war.defender_id ? war.attacker_aa : war.defender_aa;
        } else {
            return war.attacker_id > war.defender_id ? war.defender_aa : war.attacker_aa;
        }
    }

    public int getDefenderAA() {
        if (!attack.isAttackerIdGreater()) {
            return war.attacker_id > war.defender_id ? war.attacker_aa : war.defender_aa;
        } else {
            return war.attacker_id > war.defender_id ? war.defender_aa : war.attacker_aa;
        }
    }

    @Override
    public boolean isAttackerIdGreater() {
        return attack.isAttackerIdGreater();
    }

    @Override
    public int getWar_attack_id() {
        return attack.getWar_attack_id();
    }

    @Override
    public long getDate() {
        return attack.getDate();
    }

    @Override
    public AttackType getAttack_type() {
        return attack.getAttack_type();
    }

    @Override
    public SuccessType getSuccess() {
        return attack.getSuccess();
    }

    @Override
    public int getAttcas1() {
        return attack.getAttcas1();
    }

    @Override
    public int getAttcas2() {
        return attack.getAttcas2();
    }

    @Override
    public int getDefcas1() {
        return attack.getDefcas1();
    }

    @Override
    public int getDefcas2() {
        return attack.getDefcas2();
    }

    @Override
    public int getDefcas3() {
        return attack.getDefcas3();
    }

    @Override
    public double getInfra_destroyed() {
        return attack.getInfra_destroyed();
    }

    @Override
    public int getImprovements_destroyed() {
        return attack.getImprovements_destroyed();
    }

    @Override
    public double getMoney_looted() {
        return attack.getMoney_looted();
    }

    @Override
    public double[] getLoot() {
        return attack.getLoot();
    }

    @Override
    public double getLootPercent() {
        return attack.getLootPercent();
    }

    @Override
    public double getCity_infra_before() {
        return attack.getCity_infra_before();
    }

    @Override
    public double getInfra_destroyed_value() {
        return attack.getInfra_destroyed_value();
    }

    @Override
    public double getAtt_gas_used() {
        return attack.getAtt_gas_used();
    }

    @Override
    public double getAtt_mun_used() {
        return attack.getAtt_mun_used();
    }

    @Override
    public double getDef_gas_used() {
        return attack.getDef_gas_used();
    }

    @Override
    public double getDef_mun_used() {
        return attack.getDef_mun_used();
    }
}
