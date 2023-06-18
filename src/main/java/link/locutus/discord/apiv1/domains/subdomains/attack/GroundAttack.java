package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.util.MathMan;

import java.util.function.Supplier;

public abstract class GroundAttack extends AbstractAttack {
    public GroundAttack(int id, long date, boolean isAttackerIdGreater) {
        super(id, date, isAttackerIdGreater);
    }

    private static class GroundIT_AC_0_NoMoneyNoImp extends GroundAttack {
        private final long data;
        private final long data2;

        public GroundIT_AC_0_NoMoneyNoImp(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attcas1_s, int attCas2, double attGas, double attMuni) {
            super(id, date, isAttackerIdGreater);
            this.data = (long) (city_infra_before * 100) << 39 | (long) attcas1_s << 17 | (long) attCas2;
            this.data2 = (long) (infra_destroyed * 100) << 42 | (long) (attGas * 100) << 21 | (long) (attMuni * 100);
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return ((data >> 39) & 33554431) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data2 >> 42) & 4194303) * 0.01;
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 17) & 4194303);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) (data & 131071);
        }

        @Override
        public double getAtt_gas_used() {
            // signed
            return ((data2 >> 21) & 2097151) * 0.01;
        }

        @Override
        public double getAtt_mun_used() {
            // signed
            return ((data2) & 2097151) * 0.01;
        }


        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
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
        public double getDef_gas_used() {
            return 0;
        }

        @Override
        public double getDef_mun_used() {
            return 0;
        }
    }

    private static class GroundIT_A_0_NoMoneyNoImp extends GroundAttack {
        private final long data;

        public GroundIT_A_0_NoMoneyNoImp(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attcas1_s, int attCas2) {
            super(id, date, isAttackerIdGreater);
            this.data = (long) (city_infra_before * 100) << 41 | (long) (infra_destroyed * 100) << 22 | (long) attcas1_s << 9 | (long) (attCas2);
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return ((data >> 41) & 8388607) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 22) & 524287) * 0.01;
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 9) & 8191);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) (data & 511);
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
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
        public double getAtt_gas_used() {
            // signed
            return 0;
        }

        @Override
        public double getAtt_mun_used() {
            // signed
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

    private static class GroundIT_A_0_NoImp extends GroundAttack {
        private final long data;
        private final long data2;

        public GroundIT_A_0_NoImp(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attcas1_s, int attCas2, double moneyLooted) {
            super(id, date, isAttackerIdGreater);
            this.data = (long) (city_infra_before * 100) << 42 | (long) (attcas1_s) << 22 | (long) (infra_destroyed * 100);
            this.data2 = (long) (moneyLooted * 100) << 14 | (long) (attCas2);
        }

        @Override
        public double getMoney_looted() {
            return ((data2 >> 14) & 1125899906842623L) * 0.01;
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return ((data >> 42) & 4194303) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data) & 4194303) * 0.01;
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 22) & 1048575);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) (data2 & 16383);
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
        }

        @Override
        public int getImprovements_destroyed() {
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
        public double getAtt_gas_used() {
            // signed
            return 0;
        }

        @Override
        public double getAtt_mun_used() {
            // signed
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

    private static class GroundIT_SM_0_NoImp extends GroundAttack {
        private final long data;
        private final long data2;

        public GroundIT_SM_0_NoImp(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attcas1_s, double attMuni, double moneyLooted) {
            super(id, date, isAttackerIdGreater);
            // 39 + 32 + 30
            this.data = (long) (infra_destroyed * 100) << 39 | (long) (city_infra_before * 100) << 18 | attcas1_s;
            this.data2 = (long) (moneyLooted * 100) << 17 | (int) (attMuni * 100);
        }

        @Override
        public double getMoney_looted() {
            return ((data2 >> 17) & 140737488355327L) * 0.01;
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return ((data >> 18) & 2097151L) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 39) & 33554431) * 0.01;
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) (data & 262143);
        }

        @Override
        public double getAtt_mun_used() {
            // signed
            return ((data2) & 131071) * 0.01;
        }

        @Override
        public double getAtt_gas_used() {
            return 0;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
        }

        @Override
        public int getImprovements_destroyed() {
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
        public double getDef_gas_used() {
            return 0;
        }

        @Override
        public double getDef_mun_used() {
            return 0;
        }
    }

    private static class GroundIT_SM_0_NoMoneyNoImp extends GroundAttack {
        private final long data;
        private final char data2;

        public GroundIT_SM_0_NoMoneyNoImp(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attcas1_s, double attMuni) {
            super(id, date, isAttackerIdGreater);
            long attMuniCents = (long) (attMuni * 100);
            this.data = (attMuniCents & 31) << 59 | (long) (city_infra_before * 100) << 37 | (long) (infra_destroyed * 100) << 19 | attcas1_s;
            this.data2 = (char) (attMuniCents >> 5);
        }

        @Override
        public double getAtt_mun_used() {
            // signed
            return (((data >> 59) & 31) + ((int) data2 << 5)) * 0.01;
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return ((data >> 37) & 4194303) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 19) & 262143) * 0.01;
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data) & 524287);
        }


        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
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
        public double getAtt_gas_used() {
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
        public double getDef_gas_used() {
            return 0;
        }

        @Override
        public double getDef_mun_used() {
            return 0;
        }

    }

    private static class GroundIT_AC_0_NoMoney extends GroundAttack {
        private final long data;
        private final long data2;

        public GroundIT_AC_0_NoMoney(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attcas1_s, int attCas2, double attGas, double attMuni) {
            super(id, date, isAttackerIdGreater);

            // city_infra_before 2 << 21
            // infra_destroyed 2 << 17
            // attcas1 2 << 18
            // attcas2 2 << 13
            // att_gas 70000 17
            // att_mun 86800 17
            // moneyCents = 2 << 20

//            data
            // infra_destroyed = 21
            // attMuni = 21
            // attGas = 21

//            data2
            // attCas2 = 17
            // attcas1_s = 22
            // city_infra_before = 25

            this.data = (long) (infra_destroyed * 100) << 42 | (long) (attMuni * 100) << 21 | (long) (attGas * 100);
            this.data2 = (long) attCas2 << 47 | (long) attcas1_s << 25 | (long) (city_infra_before * 100);
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return ((data2) & 33554431) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 42) & 4194303) * 0.01;
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data2 >> 25) & 4194303);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) ((data2 >> 47) & 131071);
        }

        @Override
        public double getAtt_gas_used() {
            // signed
            return (data & 2097151) * 0.01;
        }

        @Override
        public double getAtt_mun_used() {
            // signed
            return ((data >> 21) & 2097151) * 0.01;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }

        @Override
        public double getMoney_looted() {
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
        public double getDef_gas_used() {
            return 0;
        }

        @Override
        public double getDef_mun_used() {
            return 0;
        }
    }

    private static class GroundIT_A_0_NoMoney extends GroundAttack {
        private final long data;

        public GroundIT_A_0_NoMoney(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attcas1_s, int attCas2) {
            super(id, date, isAttackerIdGreater);
            this.data = (long) (city_infra_before * 100) << 41 | (long) (infra_destroyed * 100) << 22 | (long) attcas1_s << 9 | (long) (attCas2);
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return ((data >> 41) & 8388607) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 22) & 524287) * 0.01;
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 9) & 8191);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) (data & 511);
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }

        @Override
        public double getMoney_looted() {
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
        public double getAtt_gas_used() {
            // signed
            return 0;
        }

        @Override
        public double getAtt_mun_used() {
            // signed
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

    private static class GroundIT_A_0_ extends GroundAttack {
        private final long data;
        private final int moneyCents;

        public GroundIT_A_0_(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attcas1_s, int attCas2, double moneyLooted) {
            super(id, date, isAttackerIdGreater);
            this.moneyCents = (int) (moneyLooted * 100d);
            this.data = (long) (city_infra_before * 100) << 41 | (long) (infra_destroyed * 100) << 22 | (long) attcas1_s << 9 | (long) (attCas2);
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return ((data >> 41) & 8388607) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 22) & 524287) * 0.01;
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 9) & 8191);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) (data & 511);
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }

        @Override
        public double getMoney_looted() {
            return moneyCents / 100d;
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
        public double getAtt_gas_used() {
            // signed
            return 0;
        }

        @Override
        public double getAtt_mun_used() {
            // signed
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

    private static class GroundIT_SM_0_ extends GroundAttack {
        private final long data;
        private final long data2;

        public GroundIT_SM_0_(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attcas1_s, double attMuni, double moneyLooted) {
            super(id, date, isAttackerIdGreater);
            this.data = ((long) (attcas1_s) << 38) | (long) (attMuni * 100) << 21 | (long) (city_infra_before * 100);
            this.data2 = (long) (moneyLooted * 100) << 17 | (int) (infra_destroyed * 100);
        }
        @Override
        public double getMoney_looted() {
            return ((data2 >> 17) & 140737488355327L) * 0.01;
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return (data & 2097151) * 0.01;
        }
        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data2) & 131071) * 0.01;
        }

        @Override
        public int getAttcas1() {
            return (int) (((data >> 38) & 67108863));
        }

        @Override
        public double getAtt_mun_used() {
            // signed
            return ((data >> 21) & 131071) * 0.01;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }

        @Override
        public double getAtt_gas_used() {
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
        public double getDef_gas_used() {
            return 0;
        }

        @Override
        public double getDef_mun_used() {
            return 0;
        }
    }

    private static class GroundIT_SM_0_NoMoney extends GroundAttack {
        private final long data;
        private final char data2;


        public GroundIT_SM_0_NoMoney(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attcas1_s, double attMuni) {
            super(id, date, isAttackerIdGreater);
            // city_infra_before 2 << 21
            // infra_destroyed 2 << 17
            // attcas1 2 << 18
            // attcas2 2 << 13
            // att_gas 70000 17
            // att_mun 86800 17
            // moneyCents = 2 << 20

            long attMuniCents = (long) (attMuni * 100);
            this.data = (attMuniCents & 31) << 59 | (long) (city_infra_before * 100) << 37 | (long) (infra_destroyed * 100) << 19 | attcas1_s;
            this.data2 = (char) (attMuniCents >> 5);
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return ((data >> 37) & 4194303) * 0.01;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 19) & 262143) * 0.01;
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data) & 524287);
        }

        @Override
        public double getAtt_mun_used() {
            // signed
            return (((data >> 59) & 31) + (data2 << 5)) * 0.01;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.IMMENSE_TRIUMPH;
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }

        @Override
        public double getMoney_looted() {
            return 0;
        }


        @Override
        public double getAtt_gas_used() {
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
        public double getDef_gas_used() {
            return 0;
        }

        @Override
        public double getDef_mun_used() {
            return 0;
        }

    }

    public static GroundAttack create(int id, long date, boolean isAttackerIdGreater,
                                      SuccessType success,
                                      int attcas1_s,
                                      Supplier<Integer> attcas2_s,

                                      int defcas1_s,
                                      int defcas2_s,
                                      Supplier<Integer> defcas3_s,

                                      Supplier<Double> city_infra_before_s,
                                      Supplier<Double> infra_destroyed_s,
                                      Supplier<Integer> improvements_destroyed_s,

                                      Supplier<Double> money_looted_s,
                                      double att_gas_used,
                                      double att_mun_used,
                                      double def_gas_used,
                                      double def_mun_used) {
        switch (success) {
            case UTTER_FAILURE: {
                if (att_gas_used == 0 && att_mun_used == 0) {
                    int attcas2 = attcas2_s.get();
                    if (def_gas_used == 0 && def_mun_used == 0) {
                        if (defcas1_s == 0 && defcas2_s == 0) {
                            return new GroundUF_A(id, date, isAttackerIdGreater, attcas1_s, attcas2); // att_pair ->
                        } else {
                            return new GroundUF_A_A(id, date, isAttackerIdGreater, attcas1_s, attcas2, defcas1_s, defcas2_s); // att_pair -> def_pair
                        }
                    } else {
                        // att_pair -> def_pair, def_consume_pair
                        return new GroundUF_A_AC(id, date, isAttackerIdGreater, attcas1_s, attcas2, defcas1_s, defcas2_s, (int) (def_gas_used * 100), (int) (def_mun_used * 100));
                    }
                } else {
                    int attcas2 = attcas2_s.get();
                    if (def_gas_used == 0 && def_mun_used == 0) {
                        if (defcas1_s == 0 && defcas2_s == 0) {
                            return new GroundUF_AC(id, date, isAttackerIdGreater, attcas1_s, attcas2, (int) (att_gas_used * 100), (int) (att_mun_used * 100)); // att_pair ->
                        } else {
                            return new GroundUF_AC_A(id, date, isAttackerIdGreater, attcas1_s, attcas2, (int) (att_gas_used * 100), (int) (att_mun_used * 100), defcas1_s, defcas2_s); // att_pair -> def_pair
                        }
                    } else {
                        // att_pair -> def_pair, def_consume_pair
                        return new GroundUF_AC_AC(id, date, isAttackerIdGreater, attcas1_s, attcas2, (int) (att_gas_used * 100), (int) (att_mun_used * 100), defcas1_s, defcas2_s, (int) (def_gas_used * 100), (int) (def_mun_used * 100));
                    }
                }
            }
            default: {
                int improvements_destroyed = improvements_destroyed_s.get();
                double money_looted = money_looted_s.get();
                double city_infra_before = city_infra_before_s.get();
                double infra_destroyed = infra_destroyed_s.get();
                int attcas2 = attcas2_s.get();
                int defcas3 = att_gas_used == 0 ? 0 : defcas3_s.get();

                if (success == SuccessType.IMMENSE_TRIUMPH) {
                    {
                        if (improvements_destroyed == 0) {
                            if (def_mun_used == 0 && defcas1_s == 0 && defcas2_s == 0 && defcas3 == 0) {
                                if (money_looted == 0) {
                                    if (att_mun_used > 0) {
                                        if (att_gas_used > 0) {
                                            return new GroundIT_AC_0_NoMoneyNoImp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1_s, attcas2, att_gas_used, att_mun_used);
                                        } else if (attcas2 == 0) {
                                            return new GroundIT_SM_0_NoMoneyNoImp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1_s, att_mun_used);
                                        }
                                    } else if (att_gas_used == 0) {
                                        return new GroundIT_A_0_NoMoneyNoImp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1_s, attcas2);
                                    }
                                } else {
                                    if (att_gas_used == 0) {
                                        if (att_mun_used == 0) {
                                            return new GroundIT_A_0_NoImp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1_s, attcas2, money_looted);
                                        } else if (attcas2 == 0) {
                                            return new GroundIT_SM_0_NoImp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1_s, att_mun_used, money_looted);
                                        }
                                    }
                                }
                            }
                        } else if (improvements_destroyed == 1) {
                            if (def_mun_used == 0 && defcas1_s == 0 && defcas2_s == 0 && defcas3 == 0) {
                                if (money_looted == 0) {
                                    if (att_mun_used > 0) {
                                        if (att_gas_used > 0) {
                                            return new GroundIT_AC_0_NoMoney(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1_s, attcas2, att_gas_used, att_mun_used);
                                        } else if (attcas2 == 0){
                                            return new GroundIT_SM_0_NoMoney(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1_s, att_mun_used);
                                        }
                                    } else if (att_gas_used == 0) {
                                        return new GroundIT_A_0_NoMoney(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1_s, attcas2);
                                    }
                                } else {
                                    if (att_gas_used == 0) {
                                        if (att_mun_used == 0) {
                                            return new GroundIT_A_0_(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1_s, attcas2, money_looted);
                                        } else if (attcas2 == 0) {
                                            return new GroundIT_SM_0_(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1_s, att_mun_used, money_looted);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (att_gas_used == 0 && att_mun_used == 0) {
                    if (def_mun_used == 0) {
                        if (defcas1_s == 0 && defcas2_s == 0) {
                            return new GroundN_A(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1_s, attcas2); // att_pair ->
                        } else {
                            return new GroundN_A_A(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1_s, attcas2, defcas1_s, defcas2_s, defcas3); // att_pair -> def_pair
                        }
                    } else {
                        // att_pair -> def_pair, def_consume_pair
                        return new GroundN_A_AC(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1_s, attcas2, defcas1_s, defcas2_s, defcas3, (int) (def_gas_used * 100), (int) (def_mun_used * 100));
                    }
                } else {
                    if (def_mun_used == 0) {
                        if (defcas1_s == 0 && defcas2_s == 0 && defcas3 == 0) {
                            return new GroundN_AC(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1_s, attcas2, (int) (att_gas_used * 100), (int) (att_mun_used * 100)); // att_pair ->
                        } else {
                            return new GroundN_AC_A(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1_s, attcas2, (int) (att_gas_used * 100), (int) (att_mun_used * 100), defcas1_s, defcas2_s, defcas3); // att_pair -> def_pair
                        }
                    } else {
                        // att_pair -> def_pair, def_consume_pair
                        return new GroundN_AC_AC(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1_s, attcas2, (int) (att_gas_used * 100), (int) (att_mun_used * 100), defcas1_s, defcas2_s, defcas3, (int) (def_gas_used * 100), (int) (def_mun_used * 100));
                    }
                }
            }
        }
    }

    @Override
    public AttackType getAttack_type() {
        return AttackType.GROUND;
    }

    @Override
    public double[] getLoot() {
        return null;
    }

    @Override
    public double getLootPercent() {
        return 0;
    }

    private static abstract class GroundUF extends GroundAttack {
        public GroundUF(int id, long date, boolean isAttackerIdGreater) {
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
        public double getMoney_looted() {
            return 0;
        }

        @Override
        public double getCity_infra_before() {
            return 0;
        }
    }

    private static class GroundUF_A extends GroundUF {

        public GroundUF_A(int id, long date, boolean isAttackerIdGreater, int attSoldier, int attTanks) {
            super(id, date, isAttackerIdGreater);
            this.data = attTanks << 19 | attSoldier;
        }

        private final int data;

        @Override
        public int getAttcas1() {
            return data & 0x7FFFF;
        }

        @Override
        public int getAttcas2() {
            return (data >> 19) & 0x1FFF;
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

    private static class GroundUF_A_A extends GroundUF_A {

        public GroundUF_A_A(int id, long date, boolean isAttackerIdGreater, int soldiers, int tanks, int defSoldier, int defTanks) {
            super(id, date, isAttackerIdGreater, soldiers, tanks);
            this.data = defTanks << 19 | defSoldier;
        }

        private final int data;

        @Override
        public int getDefcas1() {
            return data & 0x7FFFF;
        }

        @Override
        public int getDefcas2() {
            return (data >> 19) & 0x1FFF;
        }
    }

    private static class GroundUF_A_AC extends GroundUF_A_A {

        private final long defConsume;

        public GroundUF_A_AC(int id, long date, boolean isAttackerIdGreater, int soldiers, int tanks, int defSoldier, int defTanks, int defGas, int defMuni) {
            super(id, date, isAttackerIdGreater, soldiers, tanks, defSoldier, defTanks);
            this.defConsume = MathMan.pairInt(defGas, defMuni);
        }

        @Override
        public double getDef_gas_used() {
            return (MathMan.unpairIntX(defConsume)) / 100d;
        }

        @Override
        public double getDef_mun_used() {
            return (MathMan.unpairIntY(defConsume)) / 100d;
        }

    }

    private static class GroundUF_AC extends GroundUF_A {

        private final long attConsume;

        public GroundUF_AC(int id, long date, boolean isAttackerIdGreater, int soldiers, int tanks, int attGas, int attMuni) {
            super(id, date, isAttackerIdGreater, soldiers, tanks);
            this.attConsume = MathMan.pairInt(attGas, attMuni);
        }

        @Override
        public double getAtt_gas_used() {
            return (MathMan.unpairIntX(attConsume)) / 100d;
        }

        @Override
        public double getAtt_mun_used() {
            return (MathMan.unpairIntY(attConsume)) / 100d;
        }
    }

    private static class GroundUF_AC_A extends GroundUF_AC {

        public GroundUF_AC_A(int id, long date, boolean isAttackerIdGreater, int soldiers, int tanks, int attGas, int attMuni, int defSoldier, int defTanks) {
            super(id, date, isAttackerIdGreater, soldiers, tanks, attGas, attMuni);
            this.data = defTanks << 19 | defSoldier;
        }

        private final int data;

        @Override
        public int getDefcas1() {
            return data & 524287;
        }

        @Override
        public int getDefcas2() {
            return (data >> 19) & 8191;
        }
    }

    private static class GroundUF_AC_AC extends GroundUF_AC_A {

        private final long defConsume;

        public GroundUF_AC_AC(int id, long date, boolean isAttackerIdGreater, int soldiers, int tanks, int attGas, int attMuni, int defSoldier, int defTanks, int defGas, int defMuni) {
            super(id, date, isAttackerIdGreater, soldiers, tanks, attGas, attMuni, defSoldier, defTanks);
            this.defConsume = MathMan.pairInt(defGas, defMuni);
        }

        @Override
        public double getDef_gas_used() {
            return (MathMan.unpairIntX(defConsume)) / 100d;
        }

        @Override
        public double getDef_mun_used() {
            return (MathMan.unpairIntY(defConsume)) / 100d;
        }
    }

    //-----------------------

    private static abstract class GroundSuccess extends GroundAttack {
        private final long data;
        private final byte data2;

        public GroundSuccess(int id, long date, boolean isAttackerIdGreater, SuccessType successType, double city_infra_before, double infra_destroyed, int improvementDestroyed, double money_looted) {
            super(id, date, isAttackerIdGreater);
            long city_infra_beforeCents = (long) (city_infra_before * 100);
            this.data = ((city_infra_beforeCents & 8191)) << 51 | (long) (100 * infra_destroyed) << 34 | (long) Math.max(money_looted * 100, 0) << 3 | (long) successType.ordinal() << 1 | improvementDestroyed;
            this.data2 = (byte) (city_infra_beforeCents >> 13);
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.values()[(int) ((data >> 1) & 0x3)];
        }

        @Override
        public double getInfra_destroyed() {
            return (((data >> 34) & 131071)) * 0.01;
        }

        @Override
        public int getImprovements_destroyed() {
            return (int) (data & 0x1);
        }

        @Override
        public double getMoney_looted() {
            return ((data >> 3) & 0x7FFFFFFF) * 0.01;
        }

        @Override
        public double getCity_infra_before() {
            return (((data >> 51) & 8191) | ((((int) data2) & 255) << 13)) * 0.01;
        }
    }

    private static class GroundN_A extends GroundSuccess {

        public GroundN_A(int id, long date, boolean isAttackerIdGreater, SuccessType successType, double city_infra_before, double infra_destroyed, int improvementDestroyed, double money_looted, int attSoldier, int attTanks) {
            super(id, date, isAttackerIdGreater, successType, city_infra_before, infra_destroyed, improvementDestroyed, money_looted);
            this.data3 = attTanks << 19 | attSoldier;
        }

        private final int data3;

        @Override
        public int getAttcas1() {
            return data3 & 0x7FFFF;
        }

        @Override
        public int getAttcas2() {
            return (data3 >> 19) & 0x1FFF;
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

    private static class GroundN_A_A extends GroundN_A {

        public GroundN_A_A(int id, long date, boolean isAttackerIdGreater, SuccessType successType, double city_infra_before, double infra_destroyed, int improvementDestroyed, double money_looted, int soldiers, int tanks, int defSoldier, int defTanks, int defPlanes) {
            super(id, date, isAttackerIdGreater, successType, city_infra_before, infra_destroyed, improvementDestroyed, money_looted, soldiers, tanks);
            this.data = (long) defSoldier << 40 | (long) defTanks << 20 | defPlanes;
        }

        private final long data;

        @Override
        public int getDefcas1() {
            // defSoldier
            return (int) (data >> 40) & 0xFFFFF;
        }

        @Override
        public int getDefcas2() {
            // defTanks
            return (int) (data >> 20) & 0xFFFFF;
        }

        @Override
        public int getDefcas3() {
            // defplanes
            return (int) (data & 0xFFFFF);
        }
    }

    private static class GroundN_A_AC extends GroundN_A_A {

        private final long defConsume;

        public GroundN_A_AC(int id, long date, boolean isAttackerIdGreater, SuccessType successType, double city_infra_before, double infra_destroyed, int improvementDestroyed, double money_looted, int soldiers, int tanks, int defSoldier, int defTanks, int defPlanes, int defGas, int defMuni) {
            super(id, date, isAttackerIdGreater, successType, city_infra_before, infra_destroyed, improvementDestroyed, money_looted, soldiers, tanks, defSoldier, defTanks, defPlanes);
            this.defConsume = MathMan.pairInt(defGas, defMuni);
        }

        @Override
        public double getDef_gas_used() {
            return (MathMan.unpairIntX(defConsume)) / 100d;
        }

        @Override
        public double getDef_mun_used() {
            return (MathMan.unpairIntY(defConsume)) / 100d;
        }

    }

    private static class GroundN_AC extends GroundN_A {

        private final long attConsume;

        public GroundN_AC(int id, long date, boolean isAttackerIdGreater, SuccessType successType, double city_infra_before, double infra_destroyed, int improvementDestroyed, double money_looted, int soldiers, int tanks, int attGas, int attMuni) {
            super(id, date, isAttackerIdGreater, successType, city_infra_before, infra_destroyed, improvementDestroyed, money_looted, soldiers, tanks);
            this.attConsume = MathMan.pairInt(attGas, attMuni);
        }

        @Override
        public double getAtt_gas_used() {
            return (MathMan.unpairIntX(attConsume)) / 100d;
        }

        @Override
        public double getAtt_mun_used() {
            return (MathMan.unpairIntY(attConsume)) / 100d;
        }
    }

    private static class GroundN_AC_A extends GroundN_AC {


        public GroundN_AC_A(int id, long date, boolean isAttackerIdGreater, SuccessType successType, double city_infra_before, double infra_destroyed, int improvementDestroyed, double money_looted, int soldiers, int tanks, int attGas, int attMuni, int defSoldier, int defTanks, int defPlanes) {
            super(id, date, isAttackerIdGreater, successType, city_infra_before, infra_destroyed, improvementDestroyed, money_looted, soldiers, tanks, attGas, attMuni);
            this.data = (long) defSoldier << 40 | (long) defTanks << 20 | defPlanes;
        }

        private final long data;

        @Override
        public int getDefcas1() {
            // defSoldier
            return (int) (data >> 40) & 0xFFFFF;
        }

        @Override
        public int getDefcas2() {
            // defTanks
            return (int) (data >> 20) & 0xFFFFF;
        }

        @Override
        public int getDefcas3() {
            // defplanes
            return (int) (data & 0xFFFFF);
        }
    }

    private static class GroundN_AC_AC extends GroundN_AC_A {

        private final long defConsume;

        public GroundN_AC_AC(int id, long date, boolean isAttackerIdGreater, SuccessType successType, double city_infra_before, double infra_destroyed, int improvementDestroyed, double money_looted, int soldiers, int tanks, int attGas, int attMuni, int defSoldier, int defTanks, int defPlanes, int defGas, int defMuni) {
            super(id, date, isAttackerIdGreater, successType, city_infra_before, infra_destroyed, improvementDestroyed, money_looted, soldiers, tanks, attGas, attMuni, defSoldier, defTanks, defPlanes);
            this.defConsume = MathMan.pairInt(defGas, defMuni);
        }

        @Override
        public double getDef_gas_used() {
            return (MathMan.unpairIntX(defConsume)) / 100d;
        }

        @Override
        public double getDef_mun_used() {
            return (MathMan.unpairIntY(defConsume)) / 100d;
        }
    }
}
