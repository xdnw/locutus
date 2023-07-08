package link.locutus.discord.apiv1.domains.subdomains.attack;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;

import java.util.function.Supplier;

public abstract class AirstrikeInfra extends AbstractAttack{
    protected AirstrikeInfra(int id, long date, boolean isAttackerIdGreater) {
        super(id, date, isAttackerIdGreater);
    }

    public static AirstrikeInfra create(int id, long date, boolean isAttackerIdGreater,
                                           SuccessType success,
                                           Supplier<Integer> attcas1_s,
                                           int defcas1,
                                           Supplier<Double> city_infra_before_s,
                                           Supplier<Double> infra_destroyed_s,
                                           Supplier<Integer> improvements_destroyed_s,
                                           double att_gas_used,
                                           double att_mun_used,
                                           double def_gas_used,
                                           Supplier<Double> def_mun_used_s) {
        int improvements_destroyed = success == SuccessType.UTTER_FAILURE ? 0 : improvements_destroyed_s.get();
        if (improvements_destroyed == 0) {
            if (success == SuccessType.IMMENSE_TRIUMPH && defcas1 == 0 && def_gas_used == 0) {
                return new AirstrikeInfra.AirstrikeInfraIt_0_NoImp(id, date, isAttackerIdGreater, city_infra_before_s.get(), infra_destroyed_s.get(), att_gas_used, att_mun_used);
            }
            double def_mun_used = def_gas_used == 0 ? 0 : def_mun_used_s.get();
            double city_infra_before = success == SuccessType.UTTER_FAILURE ? 0 : city_infra_before_s.get();
            double infra_destroyed = success == SuccessType.UTTER_FAILURE ? 0 : infra_destroyed_s.get();
            int attcas1 = def_gas_used == 0 ? 0 : attcas1_s.get();
            return new AirstrikeInfra.AirstrikeInfraAny_Any_NoImp(id, date, isAttackerIdGreater, success, attcas1, defcas1, city_infra_before, infra_destroyed, att_gas_used, att_mun_used, def_gas_used, def_mun_used);
        } else {
            if (success == SuccessType.IMMENSE_TRIUMPH && defcas1 == 0 && def_gas_used == 0) {
                return new AirstrikeInfra.AirstrikeInfraIt_0_Imp(id, date, isAttackerIdGreater, city_infra_before_s.get(), infra_destroyed_s.get(), att_gas_used, att_mun_used);
            }
            double def_mun_used = def_gas_used == 0 ? 0 : def_mun_used_s.get();
            int attcas1 = def_gas_used == 0 ? 0 : attcas1_s.get();
            return new AirstrikeInfra.AirstrikeInfraAny_Any_Imp(id, date, isAttackerIdGreater, success, attcas1, defcas1, city_infra_before_s.get(), infra_destroyed_s.get(), att_gas_used, att_mun_used, def_gas_used, def_mun_used);
        }
    }

    public static class AirstrikeInfraIt_0_NoImp extends AirstrikeInfra {
        private final long data;
        private final byte data2;

        public AirstrikeInfraIt_0_NoImp(int id, long date, boolean isAttackerIdGreater, double city_infra_before, double infra_destroyed, double att_gas_used, double att_mun_used) {
            super(id, date, isAttackerIdGreater);
            // att_gas_used = 17
            // city_infra_before = 21
            // infra_destroyed = 17
            // att_mun_used = 17
            long attGasCents = (long) (att_gas_used * 100);
            this.data = (long) (attGasCents & 511) << 55 | (long) (city_infra_before * 100) << 34 | (long) (infra_destroyed * 100) << 17 | (long) (att_mun_used * 100);
            this.data2 = (byte) (attGasCents >> 9);
        }

        @Override
        public double getCity_infra_before() {
            return ((data >> 34) & 2097151) * 0.01;
        }
        @Override
        public double getInfra_destroyed() {
            return ((data >> 17) & 131071) * 0.01;
        }
        @Override
        public double getAtt_gas_used() {
            return (((data >> 55) & 511) | (data2 << 9)) * 0.01;
        }

        @Override
        public double getAtt_mun_used() {
            return ((data) & 131071) * 0.01;
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

    public static class AirstrikeInfraIt_0_Imp extends AirstrikeInfraIt_0_NoImp {
        public AirstrikeInfraIt_0_Imp(int id, long date, boolean isAttackerIdGreater, double cityInfraBefore, double infraDestroyed, double att_gas_used, double att_mun_used) {
            super(id, date, isAttackerIdGreater, cityInfraBefore, infraDestroyed, att_gas_used, att_mun_used);
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }
    }

    public static class AirstrikeInfraAny_Any_NoImp extends AirstrikeInfra {
        private final long data;
        private final long data2;

        public AirstrikeInfraAny_Any_NoImp(int id, long date, boolean isAttackerIdGreater, SuccessType success,
                                     int attcas1, int defcas1,
                                     double city_infra_before, double infra_destroyed,
                                     double att_gas_used, double att_mun_used, double def_gas_used, double def_mun_used) {
            super(id, date, isAttackerIdGreater);
            this.data = (long) success.ordinal() << 62 | (long) attcas1 << 49 | (long) defcas1 << 36 | (long) att_gas_used << 18 | (long) att_mun_used;
            this.data2 = (long) def_gas_used << 46 | (long) def_mun_used << 28 | (long) city_infra_before << 14 | (long) infra_destroyed;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.values()[(int) (data >> 62) & 0x3];
        }

        @Override
        public int getAttcas1() {
            return (int) (data >> 49) & 0x1FFF;
        }

        @Override
        public int getDefcas1() {
            return (int) (data >> 36) & 0x1FFF;
        }

        @Override
        public double getAtt_gas_used() {
            return (data >> 18) & 0x3FFFF;
        }

        @Override
        public double getAtt_mun_used() {
            return data & 0x3FFFF;
        }

        @Override
        public double getDef_gas_used() {
            return (data2 >> 46) & 0x3FFFF;
        }

        @Override
        public double getDef_mun_used() {
            return (data2 >> 28) & 0x3FFFF;
        }

        @Override
        public double getCity_infra_before() {
            return (data2 >> 14) & 0x3FFF;
        }

        @Override
        public double getInfra_destroyed() {
            return data2 & 0x3FFF;
        }

        @Override
        public int getImprovements_destroyed() {
            return 0;
        }
    }

    public static class AirstrikeInfraAny_Any_Imp extends AirstrikeInfraAny_Any_NoImp {

        public AirstrikeInfraAny_Any_Imp(int id, long date, boolean isAttackerIdGreater, SuccessType success, int attcas1, int defcas1, double city_infra_before, double infra_destroyed, double att_gas_used, double att_mun_used, double def_gas_used, double def_mun_used) {
            super(id, date, isAttackerIdGreater, success, attcas1, defcas1, city_infra_before, infra_destroyed, att_gas_used, att_mun_used, def_gas_used, def_mun_used);
        }

        @Override
        public int getImprovements_destroyed() {
            return 1;
        }
    }

    @Override
    public AttackType getAttack_type() {
        return AttackType.AIRSTRIKE_INFRA;
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
}