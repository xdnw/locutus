package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.util.MathMan;

public abstract class MissileAttack extends AbstractAttack{
    protected MissileAttack(int id, long date, boolean isAttackerIdGreater) {
        super(id, date, isAttackerIdGreater);
    }

    public static MissileAttack create(int id, long date, boolean isAttackerIdGreater, SuccessType success, int improvements, double city_infra_before, double infra_destroyed) {
        switch (success) {
            case UTTER_FAILURE -> {
                return new MissileAttackUF(id, date, isAttackerIdGreater);
            }
            case PYRRHIC_VICTORY -> {
                switch (improvements) {
                    case 0:
                        return new MissileAttackPV0(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed);
                    case 1:
                        return new MissileAttackPV1(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed);
                    case 2:
                        return new MissileAttackPV2(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed);
                    default:
                        throw new IllegalStateException("Unexpected improvements: " + improvements + " for MissileAttack");
                }
            }
            default -> {
                throw new IllegalStateException("Unexpected success: " + success + " for MissileAttack");
            }
        }
    }

    public static class MissileAttackUF extends MissileAttack {

        protected MissileAttackUF(int id, long date, boolean isAttackerIdGreater) {
            super(id, date, isAttackerIdGreater);
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.UTTER_FAILURE;
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
        public double getCity_infra_before() {
            return 0;
        }
    }

    public static abstract class MissileAttackPV extends MissileAttack {
        private final int data;

        public MissileAttackPV(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            this.data = MathMan.pair((short) cityInfraData, (short) infraDestroyedData);
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.PYRRHIC_VICTORY;
        }

        @Override
        public double getCity_infra_before() {
            return MathMan.unpairX(data) & 0xFFFF;
        }

        @Override
        public double getInfra_destroyed() {
            return MathMan.unpairY(data) & 0xFFFF;
        }
    }

    public static class MissileAttackPV0 extends MissileAttackPV {
        public MissileAttackPV0(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed) {
            super(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed);
        }

        @Override
        public int getImprovements_destroyed() {
            return 0;
        }
    }

    public static class MissileAttackPV1 extends MissileAttackPV {
        public MissileAttackPV1(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed) {
            super(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed);
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }
    }

    public static class MissileAttackPV2 extends MissileAttackPV {
        public MissileAttackPV2(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed) {
            super(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed);
        }

        @Override
        public int getImprovements_destroyed() {
            return 2;
        }
    }

    @Override
    public AttackType getAttack_type() {
        return AttackType.MISSILE;
    }

    @Override
    public int getAttcas1() {
        return 1;
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
