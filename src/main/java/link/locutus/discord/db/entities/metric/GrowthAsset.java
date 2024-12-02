package link.locutus.discord.db.entities.metric;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBNation;

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

    public abstract double[] value(double[] buffer, DBNation nation);

    public abstract double[] value(double[] buffer, DBNation from, DBNation to);
}