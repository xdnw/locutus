package link.locutus.discord.db.entities.metric;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.util.PW;

import java.util.Map;

public enum GrowthAsset {
    CITIES(AllianceMetric.CITY, AllianceMetric.CITY_VALUE) {
        @Override
        public int get(DBNation nation) {
            return nation.getCities();
        }

        @Override
        public double[] value(double[] buffer, DBNation nation) {
            buffer[ResourceType.MONEY.ordinal()] += nation.cityValue();
            return buffer;
        }

        @Override
        public double[] value(double[] buffer, DBNation from, DBNation to) {
            buffer[ResourceType.MONEY.ordinal()] += to.cityValue() - from.cityValue();
            return buffer;
        }
    },
    PROJECTS(AllianceMetric.PROJECTS, AllianceMetric.PROJECT_VALUE) {
        @Override
        public int get(DBNation nation) {
            return nation.getNumProjects();
        }

        @Override
        public double[] value(double[] buffer, DBNation nation) {
            for (Project project : nation.getProjects()) {
                buffer = ResourceType.add(buffer, project.costArr());
            }
            return buffer;
        }

        @Override
        public double[] value(double[] buffer, DBNation from, DBNation to) {
            for (Project project : to.getProjects()) {
                if (!from.hasProject(project)) {
                    buffer = ResourceType.add(buffer, project.costArr());
                }
            }
            return buffer;
        }
    },
    INFRA(AllianceMetric.INFRA, AllianceMetric.INFRA_VALUE) {
        @Override
        public int get(DBNation nation) {
            return (int) nation.getInfra();
        }

        @Override
        public int getAndValue(DBNation nation, double[] valueOut) {
            double infra = 0;
            double value = 0;
            for (Map.Entry<Integer, DBCity> entry : nation._getCitiesV3().entrySet()) {
                DBCity city = entry.getValue();
                double cityInfra = city.getInfra();
                infra += cityInfra;
                value += PW.City.Infra.calculateInfra(0, cityInfra);
            }
            valueOut[ResourceType.MONEY.ordinal()] += value;
            return (int) infra;
        }

        @Override
        public int getDeltaAndValue(DBNation from, DBNation to, double[] valueOut) {
            double fromInfra = 0;
            double toInfra = 0;
            double fromValue = 0;
            double toValue = 0;

            for (Map.Entry<Integer, DBCity> entry : from._getCitiesV3().entrySet()) {
                DBCity city = entry.getValue();
                double cityInfra = city.getInfra();
                fromInfra += cityInfra;
                fromValue += PW.City.Infra.calculateInfra(0, cityInfra);
            }
            for (Map.Entry<Integer, DBCity> entry : to._getCitiesV3().entrySet()) {
                DBCity city = entry.getValue();
                double cityInfra = city.getInfra();
                toInfra += cityInfra;
                toValue += PW.City.Infra.calculateInfra(0, cityInfra);
            }

            valueOut[ResourceType.MONEY.ordinal()] += (toValue - fromValue);
            int fromAmt = (int) fromInfra;
            int toAmt = (int) toInfra;
            return toAmt - fromAmt;
        }

        @Override
        public double[] value(double[] buffer, DBNation nation) {
            buffer[ResourceType.MONEY.ordinal()] += nation.infraValue();
            return buffer;
        }

        @Override
        public double[] value(double[] buffer, DBNation from, DBNation to) {
            buffer[ResourceType.MONEY.ordinal()] += to.infraValue() - from.infraValue();
            return buffer;
        }
    },
    LAND(AllianceMetric.LAND, AllianceMetric.LAND_VALUE) {
        @Override
        public int get(DBNation nation) {
            return (int) nation.getTotalLand();
        }

        @Override
        public int getAndValue(DBNation nation, double[] valueOut) {
            double land = 0;
            double value = 0;
            for (Map.Entry<Integer, DBCity> entry : nation._getCitiesV3().entrySet()) {
                DBCity city = entry.getValue();
                double cityLand = city.getLand();
                land += cityLand;
                value += PW.City.Land.calculateLand(0, cityLand);
            }
            valueOut[ResourceType.MONEY.ordinal()] += value;
            return (int) land;
        }

        @Override
        public int getDeltaAndValue(DBNation from, DBNation to, double[] valueOut) {
            double fromLand = 0;
            double toLand = 0;
            double fromValue = 0;
            double toValue = 0;

            for (Map.Entry<Integer, DBCity> entry : from._getCitiesV3().entrySet()) {
                DBCity city = entry.getValue();
                double cityLand = city.getLand();
                fromLand += cityLand;
                fromValue += PW.City.Land.calculateLand(0, cityLand);
            }
            for (Map.Entry<Integer, DBCity> entry : to._getCitiesV3().entrySet()) {
                DBCity city = entry.getValue();
                double cityLand = city.getLand();
                toLand += cityLand;
                toValue += PW.City.Land.calculateLand(0, cityLand);
            }

            valueOut[ResourceType.MONEY.ordinal()] += (toValue - fromValue);
            int fromAmt = (int) fromLand;
            int toAmt = (int) toLand;
            return toAmt - fromAmt;
        }

        @Override
        public double[] value(double[] buffer, DBNation nation) {
            buffer[ResourceType.MONEY.ordinal()] += nation.landValue();
            return buffer;
        }

        @Override
        public double[] value(double[] buffer, DBNation from, DBNation to) {
            buffer[ResourceType.MONEY.ordinal()] += to.landValue() - from.landValue();
            return buffer;
        }
    };

    public final AllianceMetric count;
    public final AllianceMetric value;

    GrowthAsset(AllianceMetric count, AllianceMetric value) {
        this.count = count;
        this.value = value;
    }

    public abstract int get(DBNation nation);

    public int getAndValue(DBNation nation, double[] valueOut) {
        value(valueOut, nation);
        return get(nation);
    }

    public int getDeltaAndValue(DBNation from, DBNation to, double[] valueOut) {
        value(valueOut, from, to);
        return get(to) - get(from);
    }

    public abstract double[] value(double[] buffer, DBNation nation);

    public abstract double[] value(double[] buffer, DBNation from, DBNation to);
}