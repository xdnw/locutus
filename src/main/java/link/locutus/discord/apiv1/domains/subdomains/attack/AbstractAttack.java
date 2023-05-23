package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.util.TimeUtil;

public abstract class AbstractAttack implements IAttack {
    long date_id_isattacker;
//    private int id;
//    private long date;
//    private final boolean isAttacker;
    protected AbstractAttack(int id, long date, boolean isAttackerIdGreater) {
        int dateRelative = (int) (date - TimeUtil.getOrigin());
        // date << shift ? | id << 1 | isAttackerIdGreater
        this.date_id_isattacker = (long) date << 32 | (long) id << 1 | (isAttackerIdGreater ? 1 : 0);
    }

    @Override
    public int getWar_attack_id() {
        return (int) ((date_id_isattacker & 0xFFFFFFFFL) >> 1);
    }

    @Override
    public long getDate() {
        return (date_id_isattacker >> 32) + TimeUtil.getOrigin();
    }

    @Override
    public boolean isAttackerIdGreater() {
        return (date_id_isattacker & 1) == 1;
    }

    public static AbstractAttack create2(int war_attack_id,
                                         long date,
                                         boolean isAttackerIdGreater,
                                         AttackType attack_type,
                                         int success,
                                         int attcas1,
                                         int attcas2,
                                         int defcas1,
                                         int defcas2,
                                         int defcas3,
                                         double infra_destroyed,
                                         int improvements_destroyed,
                                         double money_looted,
                                         double[] loot,
                                         double lootPct,
                                         double city_infra_before,
                                         double infra_destroyed_value,
                                         double att_gas_used,
                                         double att_mun_used,
                                         double def_gas_used,
                                         double def_mun_used) {
        switch (attack_type) {
            case GROUND -> {
                return GroundAttack.create(war_attack_id, date, isAttackerIdGreater,
                        SuccessType.values[success], attcas1, attcas2, defcas1, defcas2, defcas3,
                        city_infra_before, infra_destroyed, improvements_destroyed,
                        money_looted, att_gas_used, att_mun_used, def_gas_used, def_mun_used);
            }
            case VICTORY -> {
                return VictoryAttack.create(war_attack_id, date, isAttackerIdGreater,
                        loot, lootPct, infra_destroyed_value);
            }
            case A_LOOT -> {
                return ALootAttack.create(war_attack_id, date, isAttackerIdGreater,
                        loot, lootPct);
            }
            case FORTIFY -> {
                return new FortifyAttack(war_attack_id, date, isAttackerIdGreater);
            }
            case MISSILE -> {
                return MissileAttack.create(war_attack_id, date, isAttackerIdGreater, SuccessType.values[success], improvements_destroyed, city_infra_before, infra_destroyed);
            }
            case NUKE -> {
                return NukeAttack.create(war_attack_id, date, isAttackerIdGreater, SuccessType.values[success], improvements_destroyed, city_infra_before, infra_destroyed);
            }
            case AIRSTRIKE_INFRA -> {
                // most attcas1 = 0, defcas1 = 0, defcas2 = 0, def mun = 0, defgas = 0
                return null;
            }
            case AIRSTRIKE_SOLDIER -> {
                return null;
            }
            case AIRSTRIKE_TANK -> {
                return null;
            }
            case AIRSTRIKE_MONEY -> {
                return null;
            }
            case AIRSTRIKE_SHIP -> {
                return null;
            }
            case AIRSTRIKE_AIRCRAFT -> {
                return null;
            }
            case NAVAL -> {
                return NavalAttack.create(war_attack_id, date, isAttackerIdGreater, SuccessType.values[success], attcas1, att_gas_used, att_mun_used, defcas1, def_gas_used, def_mun_used, city_infra_before, infra_destroyed, improvements_destroyed);
            }
            case PEACE -> {
                throw new IllegalStateException("Unexpected attack_type: " + attack_type + " for AbstractAttack");
            }
            default -> {
                throw new IllegalStateException("Unexpected attack_type: " + attack_type);
            }
        }
    }
}
