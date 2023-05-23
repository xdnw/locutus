package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.util.MathMan;

public abstract class NavalAttack extends AbstractAttack {
    public NavalAttack(int id, long date, boolean isAttackerIdGreater) {
        super(id, date, isAttackerIdGreater);
    }

    public static NavalAttack create(int id, long date, boolean isAttackerGreater, SuccessType success, int attcas1, double  attgas, double  attmuni, int  defcas1, double  defgas, double defmuni, double city_infra_before, double infra_destroyed, int improvements) {

        if (improvements == 0) {
            switch (success) {
                case UTTER_FAILURE: {
                    return new NavalUF_ANY_NoImp(id, date, isAttackerGreater, attcas1, attgas, attmuni, defcas1, defgas, defmuni);
                }
                case IMMENSE_TRIUMPH: {
                    if (attcas1 == 0 && defcas1 == 0 && defgas == 0) {
                        return new NavalIT_0_0_NoImpNoGas(id, date, isAttackerGreater, city_infra_before, infra_destroyed, attgas, attmuni);
                    }
                }
                default: {
                    return new Naval_ANY_NoImp(id, date, isAttackerGreater, success, attcas1, attgas, attmuni, defcas1, defgas, defmuni, city_infra_before, infra_destroyed);
                }
            }
        } else {
            switch (success) {
                case UTTER_FAILURE: {
                    return new NavalUF_ANY_Imp(id, date, isAttackerGreater, attcas1, attgas, attmuni, defcas1, defgas, defmuni);
                }
                case IMMENSE_TRIUMPH: {
                    if (attcas1 == 0 && defcas1 == 0 && defgas == 0) {
                        return new NavalIT_0_0_ImpNoGas(id, date, isAttackerGreater, city_infra_before, infra_destroyed, attgas, attmuni);
                    }
                }
                default: {
                    return new Naval_ANY_Imp(id, date, isAttackerGreater, success, attcas1, attgas, attmuni, defcas1, defgas, defmuni, city_infra_before, infra_destroyed);
                }
            }
        }
    }

    @Override
    public AttackType getAttack_type() {
        return AttackType.NAVAL;
    }

    @Override
    public int getAttcas2() {
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

    public static class NavalIT_0_0_NoImpNoGas extends NavalAttack {
        private final long data;
        public NavalIT_0_0_NoImpNoGas(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, double att_gas_used, double att_mun_used) {
            super(id, date, isAttackerIdGreater);
            data = MathMan.pairInt(
                    MathMan.pair((short) city_infra_before, (short) infra_destroyed),
                    MathMan.pair((short) att_gas_used, (short) att_mun_used)
            );
        }

        @Override
        public double getInfra_destroyed() {
            return MathMan.unpairY(MathMan.unpairIntX(data));
        }

        @Override
        public double getCity_infra_before() {
            return MathMan.unpairX(MathMan.unpairIntX(data));
        }

        @Override
        public double getAtt_gas_used() {
            return MathMan.unpairX(MathMan.unpairIntY(data));
        }

        @Override
        public double getAtt_mun_used() {
            return MathMan.unpairY(MathMan.unpairIntY(data));
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
        }

        @Override
        public int getAttcas1() {
            return 0;
        }

        @Override
        public int getDefcas1() {
            return 0;
        }

        @Override
        public int getImprovements_destroyed() {
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

    public static class NavalIT_0_0_ImpNoGas extends NavalIT_0_0_NoImpNoGas {
        public NavalIT_0_0_ImpNoGas(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, double att_gas_used, double att_mun_used) {
            super(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, att_gas_used, att_mun_used);
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }
    }

    public static class NavalUF_ANY_NoImp extends NavalAttack{

        private final long data;

        public NavalUF_ANY_NoImp(int id, long date, boolean isAttackerIdGreater, int attCas1, double attGas, double attMuni, int defCas1, double defGas, double defMuni) {
            super(id, date, isAttackerIdGreater);
            this.data = (long) defMuni << 52 | (long) defGas << 40 | (long) attMuni << 28 | (long) attGas << 16 | (long) attCas1 << 8 | (long) defCas1;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.UTTER_FAILURE;
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 8) & 0xFF);
        }

        @Override
        public int getDefcas1() {
            return (int) (data & 0xFF);
        }

        @Override
        public double getAtt_gas_used() {
            return (int) ((data >> 16) & 0xFF);
        }

        @Override
        public double getAtt_mun_used() {
            return (int) ((data >> 28) & 0xFF);
        }

        @Override
        public double getDef_gas_used() {
            return (int) ((data >> 40) & 0xFF);
        }

        @Override
        public double getDef_mun_used() {
            return (int) ((data >> 52) & 0xFF);
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

    public static class NavalUF_ANY_Imp extends NavalUF_ANY_NoImp{

        public NavalUF_ANY_Imp(int id, long date, boolean isAttackerIdGreater, int attCas1, double attGas, double attMuni, int defCas1, double defGas, double defMuni) {
            super(id, date, isAttackerIdGreater, attCas1, attGas, attMuni, defCas1, defGas, defMuni);
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }
    }

    public static class Naval_ANY_NoImp extends NavalUF_ANY_NoImp {
        private final int data2;

        public Naval_ANY_NoImp(int id, long date, boolean isAttackerIdGreater, SuccessType success, int attCas1, double att_gas_used, double att_mun_used, int defCas1, double def_gas_used, double def_mun_used, double city_infra_before, double infra_destroyed) {
            super(id, date, isAttackerIdGreater, attCas1, att_gas_used, att_mun_used, defCas1, def_gas_used, def_mun_used);
            this.data2 = ((int) city_infra_before << 17) | ((int) infra_destroyed << 2) | success.ordinal();
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.values()[data2 & 0x3];
        }

        @Override
        public double getCity_infra_before() {
            return (data2 >> 17) & 0x1FFFF;
        }

        @Override
        public double getInfra_destroyed() {
            return (data2 >> 2) & 0x1FFFF;
        }
    }

    public static class Naval_ANY_Imp extends Naval_ANY_NoImp {

        public Naval_ANY_Imp(int id, long date, boolean isAttackerIdGreater, SuccessType success, int attCas1, double att_gas_used, double att_mun_used, int defCas1, double def_gas_used, double def_mun_used, double city_infra_before, double infra_destroyed) {
            super(id, date, isAttackerIdGreater, success, attCas1, att_gas_used, att_mun_used, defCas1, def_gas_used, def_mun_used, city_infra_before, infra_destroyed);
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }
    }
}
