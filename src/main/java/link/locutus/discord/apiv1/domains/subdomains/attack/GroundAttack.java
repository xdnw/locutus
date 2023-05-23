package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.util.MathMan;

public abstract class GroundAttack extends AbstractAttack {
    public GroundAttack(int id, long date, boolean isAttackerIdGreater) {
        super(id, date, isAttackerIdGreater);
    }

    private static class GroundIT_AC_0_NoMoneyNoImp extends GroundAttack {
        private final long data;
        private final int data2;

        public GroundIT_AC_0_NoMoneyNoImp(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attCas1, int attCas2, double attGas, double attMuni) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            // data = cityInfraData << ? | infraDestroyedData << ? | attCas1 << ? | attCas2 << ?
            data = (long) cityInfraData << 48 | (long) infraDestroyedData << 32 | (long) attCas1 << 16 | attCas2;
            // pack signed
            data2 = (int) (attGas * 10) << 16 | (int) (attMuni * 10);
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
        public double getCity_infra_before() {
            // signed
            return (data >> 48) & 0xFFFF;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 32) & 0xFFFF);
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 16) & 0xFFFF);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) (data & 0xFFFF);
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
            return ((data2 >> 16) & 0xFFFF) / 10.0;
        }

        @Override
        public double getAtt_mun_used() {
            // signed
            return (data2 & 0xFFFF) / 10.0;
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

        public GroundIT_A_0_NoMoneyNoImp(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attCas1, int attCas2) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            this.data = (long) cityInfraData << 48 | (long) infraDestroyedData << 32 | (long) attCas1 << 16 | attCas2;
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
        public double getCity_infra_before() {
            // signed
            return (data >> 48) & 0xFFFF;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 32) & 0xFFFF);
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 16) & 0xFFFF);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) (data & 0xFFFF);
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
        private final int moneyCents;

        public GroundIT_A_0_NoImp(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attCas1, int attCas2, double moneyLooted) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            this.moneyCents = (int) Math.round(moneyLooted * 100d);
            this.data = (long) cityInfraData << 48 | (long) infraDestroyedData << 32 | (long) attCas1 << 16 | attCas2;
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
            return moneyCents / 100d;
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return (data >> 48) & 0xFFFF;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 32) & 0xFFFF);
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 16) & 0xFFFF);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) (data & 0xFFFF);
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
        private final int moneyCents;

        public GroundIT_SM_0_NoImp(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attCas1, double attMuni, double moneyLooted) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            this.moneyCents = (int) Math.round(moneyLooted * 100d);
            this.data = (long) cityInfraData << 48 | (long) infraDestroyedData << 32 | (long) attCas1 << 19 | (long) (attMuni * 100d);
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
            return moneyCents / 100d;
        }

        @Override
        public double getCity_infra_before() {
            // signed
            return (data >> 48) & 0xFFFF;
        }

        @Override
        public double getAtt_gas_used() {
            return 0;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 32) & 0xFFFF);
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 19) & 0x1FFF);
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
        public double getAtt_mun_used() {
            // signed
            return ((data) & 0x7FFFF) / 100d;
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

        public GroundIT_SM_0_NoMoneyNoImp(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attCas1, double attMuni) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            this.data = (long) cityInfraData << 48 | (long) infraDestroyedData << 32 | (long) attCas1 << 19 | (long) (attMuni * 100d);
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
        public double getCity_infra_before() {
            // signed
            return (data >> 48) & 0xFFFF;
        }

        @Override
        public double getAtt_gas_used() {
            return 0;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 32) & 0xFFFF);
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 19) & 0x1FFF);
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
        public double getAtt_mun_used() {
            // signed
            return ((data) & 0x7FFFF) / 100d;
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
        private final int data2;

        public GroundIT_AC_0_NoMoney(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attCas1, int attCas2, double attGas, double attMuni) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            // data = cityInfraData << ? | infraDestroyedData << ? | attCas1 << ? | attCas2 << ?
            data = (long) cityInfraData << 48 | (long) infraDestroyedData << 32 | (long) attCas1 << 16 | attCas2;
            // pack signed
            data2 = (int) (attGas * 10) << 16 | (int) (attMuni * 10);
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
        public double getCity_infra_before() {
            // signed
            return (data >> 48) & 0xFFFF;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 32) & 0xFFFF);
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 16) & 0xFFFF);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) (data & 0xFFFF);
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
            return ((data2 >> 16) & 0xFFFF) / 10.0;
        }

        @Override
        public double getAtt_mun_used() {
            // signed
            return (data2 & 0xFFFF) / 10.0;
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

        public GroundIT_A_0_NoMoney(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attCas1, int attCas2) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            this.data = (long) cityInfraData << 48 | (long) infraDestroyedData << 32 | (long) attCas1 << 16 | attCas2;
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
        public double getCity_infra_before() {
            // signed
            return (data >> 48) & 0xFFFF;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 32) & 0xFFFF);
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 16) & 0xFFFF);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) (data & 0xFFFF);
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

        public GroundIT_A_0_(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attCas1, int attCas2, double moneyLooted) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            this.moneyCents = (int) Math.round(moneyLooted * 100d);
            this.data = (long) cityInfraData << 48 | (long) infraDestroyedData << 32 | (long) attCas1 << 16 | attCas2;
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
        public double getCity_infra_before() {
            // signed
            return (data >> 48) & 0xFFFF;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 32) & 0xFFFF);
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 16) & 0xFFFF);
        }

        @Override
        public int getAttcas2() {
            // signed
            return (int) (data & 0xFFFF);
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
        private final int moneyCents;

        public GroundIT_SM_0_(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attCas1, double attMuni, double moneyLooted) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            this.moneyCents = (int) Math.round(moneyLooted * 100d);
            this.data = (long) cityInfraData << 48 | (long) infraDestroyedData << 32 | (long) attCas1 << 19 | (long) (attMuni * 100d);
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
        public double getCity_infra_before() {
            // signed
            return (data >> 48) & 0xFFFF;
        }

        @Override
        public double getAtt_gas_used() {
            return 0;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 32) & 0xFFFF);
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 19) & 0x1FFF);
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
        public double getAtt_mun_used() {
            // signed
            return ((data) & 0x7FFFF) / 100d;
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

        public GroundIT_SM_0_NoMoney(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, int attCas1, double attMuni) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            this.data = (long) cityInfraData << 48 | (long) infraDestroyedData << 32 | (long) attCas1 << 19 | (long) (attMuni * 100d);
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
        public double getCity_infra_before() {
            // signed
            return (data >> 48) & 0xFFFF;
        }

        @Override
        public double getAtt_gas_used() {
            return 0;
        }

        @Override
        public double getInfra_destroyed() {
            // signed
            return ((data >> 32) & 0xFFFF);
        }

        @Override
        public int getAttcas1() {
            // signed
            return (int) ((data >> 19) & 0x1FFF);
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
        public double getAtt_mun_used() {
            // signed
            return ((data) & 0x7FFFF) / 100d;
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
                                      int attcas1,
                                      int attcas2,

                                      int defcas1,
                                      int defcas2,
                                      int defcas3,

                                      double city_infra_before,
                                      double infra_destroyed,
                                      int improvements_destroyed,

                                      double money_looted,
                                      double att_gas_used,
                                      double att_mun_used,
                                      double def_gas_used,
                                      double def_mun_used) {
        switch (success) {
            case UTTER_FAILURE: {
                if (att_gas_used == 0 && att_mun_used == 0) {
                    if (def_gas_used == 0 && def_mun_used == 0) {
                        if (defcas1 == 0 && defcas2 == 0 && defcas3 == 0) {
                            return new GroundUF_A(id, date, isAttackerIdGreater, attcas1, attcas2); // att_pair ->
                        } else {
                            return new GroundUF_A_A(id, date, isAttackerIdGreater, attcas1, attcas2, defcas1, defcas2, defcas3); // att_pair -> def_pair
                        }
                    } else {
                        // att_pair -> def_pair, def_consume_pair
                        return new GroundUF_A_AC(id, date, isAttackerIdGreater, attcas1, attcas2, defcas1, defcas2, defcas3, (int) Math.round(def_gas_used * 100), (int) Math.round(def_mun_used * 100));
                    }
                } else {
                    if (def_gas_used == 0 && def_mun_used == 0) {
                        if (defcas1 == 0 && defcas2 == 0 && defcas3 == 0) {
                            return new GroundUF_AC(id, date, isAttackerIdGreater, attcas1, attcas2, (int) Math.round(att_gas_used * 100), (int) Math.round(att_mun_used * 100)); // att_pair ->
                        } else {
                            return new GroundUF_AC_A(id, date, isAttackerIdGreater, attcas1, attcas2, (int) Math.round(att_gas_used * 100), (int) Math.round(att_mun_used * 100), defcas1, defcas2, defcas3); // att_pair -> def_pair
                        }
                    } else {
                        // att_pair -> def_pair, def_consume_pair
                        return new GroundUF_AC_AC(id, date, isAttackerIdGreater, attcas1, attcas2, (int) Math.round(att_gas_used * 100), (int) Math.round(att_mun_used * 100), defcas1, defcas2, defcas3, (int) Math.round(def_gas_used * 100), (int) Math.round(def_mun_used * 100));
                    }
                }
            }
            case IMMENSE_TRIUMPH: {
                if (improvements_destroyed == 0) {
                    if (def_gas_used == 0 && def_mun_used == 0 && defcas1 == 0 && defcas2 == 0 && defcas3 == 0) {
                        if (money_looted == 0) {
                            if (att_mun_used > 0) {
                                if (att_gas_used > 0) {
                                    return new GroundIT_AC_0_NoMoneyNoImp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1, attcas2, att_gas_used, att_mun_used);
                                } else {
                                    return new GroundIT_SM_0_NoMoneyNoImp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1, att_mun_used);
                                }
                            } else if (att_gas_used == 0) {
                               return new GroundIT_A_0_NoMoneyNoImp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1, attcas2);
                            }
                        } else {
                            if (att_gas_used == 0) {
                                if (att_mun_used == 0) {
                                    return new GroundIT_A_0_NoImp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1, attcas2, money_looted);
                                } else {
                                    return new GroundIT_SM_0_NoImp(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1, att_mun_used, money_looted);
                                }
                            }
                        }
                    }
                } else if (improvements_destroyed == 1) {
                    if (def_gas_used == 0 && def_mun_used == 0 && defcas1 == 0 && defcas2 == 0 && defcas3 == 0) {
                        if (money_looted == 0) {
                            if (att_mun_used > 0) {
                                if (att_gas_used > 0) {
                                    return new GroundIT_AC_0_NoMoney(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1, attcas2, att_gas_used, att_mun_used);
                                } else {
                                    return new GroundIT_SM_0_NoMoney(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1, att_mun_used);
                                }
                            } else if (att_gas_used == 0) {
                                return new GroundIT_A_0_NoMoney(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1, attcas2);
                            }
                        } else {
                            if (att_gas_used == 0) {
                                if (att_mun_used == 0) {
                                    return new GroundIT_A_0_(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1, attcas2, money_looted);
                                } else {
                                    return new GroundIT_SM_0_(id, date, isAttackerIdGreater, city_infra_before, infra_destroyed, attcas1, att_mun_used, money_looted);
                                }
                            }
                        }
                    }
                }
            }
            default: {
                if (att_gas_used == 0 && att_mun_used == 0) {
                    if (def_gas_used == 0 && def_mun_used == 0) {
                        if (defcas1 == 0 && defcas2 == 0 && defcas3 == 0) {
                            return new GroundN_A(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1, attcas2); // att_pair ->
                        } else {
                            return new GroundN_A_A(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1, attcas2, defcas1, defcas2, defcas3); // att_pair -> def_pair
                        }
                    } else {
                        // att_pair -> def_pair, def_consume_pair
                        return new GroundN_A_AC(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1, attcas2, defcas1, defcas2, defcas3, (int) Math.round(def_gas_used * 100), (int) Math.round(def_mun_used * 100));
                    }
                } else {
                    if (def_gas_used == 0 && def_mun_used == 0) {
                        if (defcas1 == 0 && defcas2 == 0 && defcas3 == 0) {
                            return new GroundN_AC(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1, attcas2, (int) Math.round(att_gas_used * 100), (int) Math.round(att_mun_used * 100)); // att_pair ->
                        } else {
                            return new GroundN_AC_A(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1, attcas2, (int) Math.round(att_gas_used * 100), (int) Math.round(att_mun_used * 100), defcas1, defcas2, defcas3); // att_pair -> def_pair
                        }
                    } else {
                        // att_pair -> def_pair, def_consume_pair
                        return new GroundN_AC_AC(id, date, isAttackerIdGreater, success, city_infra_before, infra_destroyed, improvements_destroyed, money_looted, attcas1, attcas2, (int) Math.round(att_gas_used * 100), (int) Math.round(att_mun_used * 100), defcas1, defcas2, defcas3, (int) Math.round(def_gas_used * 100), (int) Math.round(def_mun_used * 100));
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
        private final long attPair;

        public GroundUF_A(int id, long date, boolean isAttackerIdGreater, int soldiers, int tanks) {
            super(id, date, isAttackerIdGreater);
            this.attPair = MathMan.pairInt(soldiers, tanks);
        }

        @Override
        public int getAttcas1() {
            return MathMan.unpairIntX(attPair);
        }

        @Override
        public int getAttcas2() {
            return MathMan.unpairIntY(attPair);
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

        private final long defPair;

        public GroundUF_A_A(int id, long date, boolean isAttackerIdGreater, int soldiers, int tanks, int defSoldier, int defTanks, int defPlanes) {
            super(id, date, isAttackerIdGreater, soldiers, tanks);
            this.defPair = MathMan.pairInt(defSoldier, MathMan.pair((short) defTanks, (short) defPlanes));
        }

        @Override
        public int getDefcas1() {
            return MathMan.unpairIntX(defPair);
        }

        @Override
        public int getDefcas2() {
            return MathMan.unpairX(MathMan.unpairIntY(defPair)) & 0xFFFF;
        }

        @Override
        public int getDefcas3() {
            return MathMan.unpairY(MathMan.unpairIntY(defPair)) & 0xFFFF;
        }
    }

    private static class GroundUF_A_AC extends GroundUF_A_A {

        private final long defConsume;

        public GroundUF_A_AC(int id, long date, boolean isAttackerIdGreater, int soldiers, int tanks, int defSoldier, int defTanks, int defPlanes, int defGas, int defMuni) {
            super(id, date, isAttackerIdGreater, soldiers, tanks, defSoldier, defTanks, defPlanes);
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

        private final long defPair;

        public GroundUF_AC_A(int id, long date, boolean isAttackerIdGreater, int soldiers, int tanks, int attGas, int attMuni, int defSoldier, int defTanks, int defPlanes) {
            super(id, date, isAttackerIdGreater, soldiers, tanks, attGas, attMuni);
            this.defPair = MathMan.pairInt(defSoldier, MathMan.pair((short) defTanks, (short) defPlanes));
        }

        @Override
        public int getDefcas1() {
            return MathMan.unpairIntX(defPair);
        }

        @Override
        public int getDefcas2() {
            return MathMan.unpairX(MathMan.unpairIntY(defPair)) & 0xFFFF;
        }

        @Override
        public int getDefcas3() {
            return MathMan.unpairY(MathMan.unpairIntY(defPair)) & 0xFFFF;
        }
    }

    private static class GroundUF_AC_AC extends GroundUF_AC_A {

        private final long defConsume;

        public GroundUF_AC_AC(int id, long date, boolean isAttackerIdGreater, int soldiers, int tanks, int attGas, int attMuni, int defSoldier, int defTanks, int defPlanes, int defGas, int defMuni) {
            super(id, date, isAttackerIdGreater, soldiers, tanks, attGas, attMuni, defSoldier, defTanks, defPlanes);
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

        public GroundSuccess(int id, long date, boolean isAttackerIdGreater, SuccessType successType, double city_infra_before, double infra_destroyed, int improvementDestroyed, double money_looted) {
            super(id, date, isAttackerIdGreater);
            char cityInfraData = (char) (int) city_infra_before;
            char infraDestroyedData = (char) (int) infra_destroyed;
            int looted = (int) money_looted; // 3 bytes
            byte improveMentSuccess = MathMan.pair16((byte) improvementDestroyed, (byte) successType.ordinal());
            this.data = (long) cityInfraData << 48 | (long) infraDestroyedData << 32 | (long) looted << 8 | improveMentSuccess;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.values()[MathMan.unpair16y((byte) (data & 0xFF))];
        }

        @Override
        public double getInfra_destroyed() {
            return ((data >> 32) & 0xFFFF);
        }

        @Override
        public int getImprovements_destroyed() {
            return MathMan.unpair16x((byte) (data & 0xFF));
        }

        @Override
        public double getMoney_looted() {
            return (data >> 8) & 0xFFFFFF;
        }

        @Override
        public double getCity_infra_before() {
            return (data >> 48) & 0xFFFF;
        }
    }

    private static class GroundN_A extends GroundSuccess {
        private final long attPair;

        public GroundN_A(int id, long date, boolean isAttackerIdGreater, SuccessType successType, double city_infra_before, double infra_destroyed, int improvementDestroyed, double money_looted, int soldiers, int tanks) {
            super(id, date, isAttackerIdGreater, successType, city_infra_before, infra_destroyed, improvementDestroyed, money_looted);
            this.attPair = MathMan.pairInt(soldiers, tanks);
        }

        @Override
        public int getAttcas1() {
            return MathMan.unpairIntX(attPair);
        }

        @Override
        public int getAttcas2() {
            return MathMan.unpairIntY(attPair);
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

        private final long defPair;

        public GroundN_A_A(int id, long date, boolean isAttackerIdGreater, SuccessType successType, double city_infra_before, double infra_destroyed, int improvementDestroyed, double money_looted, int soldiers, int tanks, int defSoldier, int defTanks, int defPlanes) {
            super(id, date, isAttackerIdGreater, successType, city_infra_before, infra_destroyed, improvementDestroyed, money_looted, soldiers, tanks);
            this.defPair = MathMan.pairInt(defSoldier, MathMan.pair((short) defTanks, (short) defPlanes));
        }

        @Override
        public int getDefcas1() {
            return MathMan.unpairIntX(defPair);
        }

        @Override
        public int getDefcas2() {
            return MathMan.unpairX(MathMan.unpairIntY(defPair)) & 0xFFFF;
        }

        @Override
        public int getDefcas3() {
            return MathMan.unpairY(MathMan.unpairIntY(defPair)) & 0xFFFF;
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

        private final long defPair;

        public GroundN_AC_A(int id, long date, boolean isAttackerIdGreater, SuccessType successType, double city_infra_before, double infra_destroyed, int improvementDestroyed, double money_looted, int soldiers, int tanks, int attGas, int attMuni, int defSoldier, int defTanks, int defPlanes) {
            super(id, date, isAttackerIdGreater, successType, city_infra_before, infra_destroyed, improvementDestroyed, money_looted, soldiers, tanks, attGas, attMuni);
            this.defPair = MathMan.pairInt(defSoldier, MathMan.pair((short) defTanks, (short) defPlanes));
        }

        @Override
        public int getDefcas1() {
            return MathMan.unpairIntX(defPair);
        }

        @Override
        public int getDefcas2() {
            return MathMan.unpairX(MathMan.unpairIntY(defPair)) & 0xFFFF;
        }

        @Override
        public int getDefcas3() {
            return MathMan.unpairY(MathMan.unpairIntY(defPair)) & 0xFFFF;
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
