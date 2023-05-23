package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;

public class FortifyAttack extends AbstractAttack{
    protected FortifyAttack(int id, long date, boolean isAttackerIdGreater) {
        super(id, date, isAttackerIdGreater);
    }

    @Override
    public AttackType getAttack_type() {
        return AttackType.FORTIFY;
    }

    @Override
    public SuccessType getSuccess() {
        return SuccessType.PYRRHIC_VICTORY;
    }

    @Override
    public int getAttcas1() {
        return 0;
    }

    @Override
    public int getAttcas2() {
        return 0;
    }

    @Override
    public int getDefcas1() {
        return 0;
    }

    @Override
    public int getDefcas2() {
        return 0;
    }

    @Override
    public int getDefcas3() {
        return 0;
    }

    @Override
    public double getInfra_destroyed() {
        return 0;
    }

    @Override
    public int getImprovements_destroyed() {
        return 0;
    }

    @Override
    public double getMoney_looted() {
        return 0;
    }

    @Override
    public double[] getLoot() {
        return null;
    }

    @Override
    public double getLootPercent() {
        return 0;
    }

    @Override
    public double getCity_infra_before() {
        return 0;
    }

    @Override
    public double getAtt_gas_used() {
        return 0;
    }

    @Override
    public double getAtt_mun_used() {
        return 0;
    }

    @Override
    public double getDef_gas_used() {
        return 0;
    }

    @Override
    public double getDef_mun_used() {
        return 0;
    }
}
