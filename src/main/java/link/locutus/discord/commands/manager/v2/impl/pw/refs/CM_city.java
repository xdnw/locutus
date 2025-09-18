package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_city {
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getAgeDays")
        public static class getAgeDays extends CommandRef {
            public static final getAgeDays cmd = new getAgeDays();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getAgeMillis")
        public static class getAgeMillis extends CommandRef {
            public static final getAgeMillis cmd = new getAgeMillis();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getBuilding")
        public static class getBuilding extends CommandRef {
            public static final getBuilding cmd = new getBuilding();
        public getBuilding building(String value) {
            return set("building", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getBuildingCost")
        public static class getBuildingCost extends CommandRef {
            public static final getBuildingCost cmd = new getBuildingCost();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getBuildingMarketCost")
        public static class getBuildingMarketCost extends CommandRef {
            public static final getBuildingMarketCost cmd = new getBuildingMarketCost();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getCommerce")
        public static class getCommerce extends CommandRef {
            public static final getCommerce cmd = new getCommerce();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getCreatedMillis")
        public static class getCreatedMillis extends CommandRef {
            public static final getCreatedMillis cmd = new getCreatedMillis();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getCrime")
        public static class getCrime extends CommandRef {
            public static final getCrime cmd = new getCrime();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getDisease")
        public static class getDisease extends CommandRef {
            public static final getDisease cmd = new getDisease();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getFreeInfra")
        public static class getFreeInfra extends CommandRef {
            public static final getFreeInfra cmd = new getFreeInfra();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getFreeSlots")
        public static class getFreeSlots extends CommandRef {
            public static final getFreeSlots cmd = new getFreeSlots();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getInfra")
        public static class getInfra extends CommandRef {
            public static final getInfra cmd = new getInfra();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getLand")
        public static class getLand extends CommandRef {
            public static final getLand cmd = new getLand();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getMMR")
        public static class getMMR extends CommandRef {
            public static final getMMR cmd = new getMMR();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getMarkdownUrl")
        public static class getMarkdownUrl extends CommandRef {
            public static final getMarkdownUrl cmd = new getMarkdownUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getNation")
        public static class getNation extends CommandRef {
            public static final getNation cmd = new getNation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getNationId")
        public static class getNationId extends CommandRef {
            public static final getNationId cmd = new getNationId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getNukeTurn")
        public static class getNukeTurn extends CommandRef {
            public static final getNukeTurn cmd = new getNukeTurn();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getNukeTurnEpoch")
        public static class getNukeTurnEpoch extends CommandRef {
            public static final getNukeTurnEpoch cmd = new getNukeTurnEpoch();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getNumBuildings")
        public static class getNumBuildings extends CommandRef {
            public static final getNumBuildings cmd = new getNumBuildings();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getPollution")
        public static class getPollution extends CommandRef {
            public static final getPollution cmd = new getPollution();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getPopulation")
        public static class getPopulation extends CommandRef {
            public static final getPopulation cmd = new getPopulation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getPowered")
        public static class getPowered extends CommandRef {
            public static final getPowered cmd = new getPowered();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getPoweredInfra")
        public static class getPoweredInfra extends CommandRef {
            public static final getPoweredInfra cmd = new getPoweredInfra();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getRequiredInfra")
        public static class getRequiredInfra extends CommandRef {
            public static final getRequiredInfra cmd = new getRequiredInfra();

        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders.class,method="getResource")
        public static class getResource extends CommandRef {
            public static final getResource cmd = new getResource();
        public getResource resources(String value) {
            return set("resources", value);
        }

        public getResource resource(String value) {
            return set("resource", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders.class,method="getResourceValue")
        public static class getResourceValue extends CommandRef {
            public static final getResourceValue cmd = new getResourceValue();
        public getResourceValue resources(String value) {
            return set("resources", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getRevenue")
        public static class getRevenue extends CommandRef {
            public static final getRevenue cmd = new getRevenue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getRevenueValue")
        public static class getRevenueValue extends CommandRef {
            public static final getRevenueValue cmd = new getRevenueValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getSheetUrl")
        public static class getSheetUrl extends CommandRef {
            public static final getSheetUrl cmd = new getSheetUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBCity.class,method="getUrl")
        public static class getUrl extends CommandRef {
            public static final getUrl cmd = new getUrl();

        }

}
