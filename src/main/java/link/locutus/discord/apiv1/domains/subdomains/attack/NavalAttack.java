package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.util.MathMan;

import java.util.function.Supplier;

public abstract class NavalAttack extends AbstractAttack {
    public NavalAttack(int id, long date, boolean isAttackerIdGreater) {
        super(id, date, isAttackerIdGreater);
    }

    public static NavalAttack create(int id, long date, boolean isAttackerGreater,
                                     SuccessType success,
                                     Supplier<Integer> attcas1_s,
                                     double  attgas,
                                     double  attmuni,
                                     Integer defcas1,
                                     double  defgas,
                                     Supplier<Double> defmuni_s,
                                     Supplier<Double> city_infra_before_s,
                                     Supplier<Double> infra_destroyed_s,
                                     Supplier<Integer> improvements_s) {

        int improvements = success == SuccessType.UTTER_FAILURE ? 0 : improvements_s.get();
        if (improvements == 0) {
            switch (success) {
                case UTTER_FAILURE: {
                    double defmuni = defgas == 0 ? 0 : defmuni_s.get();
                    return new NavalUF_ANY_NoImp(id, date, isAttackerGreater, attcas1_s.get(), attgas, attmuni, defcas1, defgas, defmuni);
                }
                default: {
                    if (success == SuccessType.IMMENSE_TRIUMPH && defcas1 == 0 && defgas == 0) {
                        return new NavalIT_0_0_NoImpNoGas(id, date, isAttackerGreater, city_infra_before_s.get(), infra_destroyed_s.get(), attgas, attmuni);
                    }
                    return new Naval_ANY_NoImp(id, date, isAttackerGreater, success, attcas1_s.get(), attgas, attmuni, defcas1, defgas, defmuni_s.get(), city_infra_before_s.get(), infra_destroyed_s.get());
                }
            }
        } else {
            switch (success) {
                default: {
                    if (success == SuccessType.IMMENSE_TRIUMPH && defcas1 == 0 && defgas == 0) {
                        return new NavalIT_0_0_ImpNoGas(id, date, isAttackerGreater, city_infra_before_s.get(), infra_destroyed_s.get(), attgas, attmuni);
                    }
                    return new Naval_ANY_Imp(id, date, isAttackerGreater, success, attcas1_s.get(), attgas, attmuni, defcas1, defgas, defmuni_s.get(), city_infra_before_s.get(), infra_destroyed_s.get());
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
        private final long data1;
        private final char data2;
        public NavalIT_0_0_NoImpNoGas(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, double att_gas_used, double att_mun_used) {
            super(id, date, isAttackerIdGreater);
            // att_mun_used 146000 18
            // city_infra_before 596476 21
            // infra_destroyed 198776 18
            // att_gas_used 146000 18
            // 4
            // 14
            long att_mun_usedCents = (long) (att_mun_used * 100);
            data1 = ((long) (att_mun_usedCents & 15)) << 60 | (long) (city_infra_before * 100) << 38 | ((long) (infra_destroyed * 100) << 19) | ((long) (att_gas_used * 100));
            data2 = (char) (att_mun_usedCents >> 4);
        }

        @Override
        public double getInfra_destroyed() {
            return ((data1 >> 19) & 524287) * 0.01;
        }

        @Override
        public double getCity_infra_before() {
            return ((data1 >> 38) & 4194303) * 0.01;
        }

        @Override
        public double getAtt_gas_used() {
            return (data1 & 524287) * 0.01;
        }

        @Override
        public double getAtt_mun_used() {
            return (((data1 >> 60) & 15) | (data2 << 4)) * 0.01;
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

        private final long data1;
        private final int data2;

        public NavalUF_ANY_NoImp(int id, long date, boolean isAttackerIdGreater, int attCas1, double att_gas_used, double att_mun_used, int defCas1, double def_gas_used, double def_mun_used) {
            super(id, date, isAttackerIdGreater);
            // att_mun_used 50175   2 << 16 | 19
            // attcas1 308          2 << 9 | 12


            // att_gas_used 50175   2 << 16 | 18
            // def_mun_used 29250   2 << 15 | 18
            // def_gas_used 29250   2 << 15 | 18
            // defcas1 94           2 << 7 | 10

            this.data1 = (long) (att_gas_used * 100) << 46 | (long) (def_mun_used * 100) << 28 | (long) (def_gas_used * 100) << 10 | (long) defCas1;
            this.data2 = (int) (att_mun_used * 100) << 12 | attCas1;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.UTTER_FAILURE;
        }

        @Override
        public int getAttcas1() {
            return (int) ((data2) & 4095);
        }

        @Override
        public int getDefcas1() {
            return (int) (data1 & 1023);
        }

        @Override
        public double getAtt_gas_used() {
            return (int) ((data1 >> 46) & 262143) * 0.01;
        }

        @Override
        public double getAtt_mun_used() {
            return (int) ((data2 >> 12) & 1048575) * 0.01;
        }

        @Override
        public double getDef_gas_used() {
            return (int) ((data1 >> 10) & 262143) * 0.01;
        }

        @Override
        public double getDef_mun_used() {
            return (int) ((data2 >> 28) & 262143) * 0.01;
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
        private final long data1;

        public Naval_ANY_NoImp(int id, long date, boolean isAttackerIdGreater, SuccessType success, int attCas1, double att_gas_used, double att_mun_used, int defCas1, double def_gas_used, double def_mun_used, double city_infra_before, double infra_destroyed) {
            super(id, date, isAttackerIdGreater, attCas1, att_gas_used, att_mun_used, defCas1, def_gas_used, def_mun_used);
            this.data1 = (long) (city_infra_before * 100) << 20 | (long) (infra_destroyed * 100) << 2 | success.ordinal();
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.values()[(int) (data1 & 0x3)];
        }

        @Override
        public double getCity_infra_before() {
            return ((data1 >> 20) & 17592186044415L) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            return ((data1 >> 2) & 262143) * 0.01;
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
