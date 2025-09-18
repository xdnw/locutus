package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_building {
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="canBuild")
        public static class canBuild extends CommandRef {
            public static final canBuild cmd = new canBuild();
        public canBuild continent(String value) {
            return set("continent", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="cost")
        public static class cost extends CommandRef {
            public static final cost cmd = new cost();
        public cost type(String value) {
            return set("type", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="countCanBuild")
        public static class countCanBuild extends CommandRef {
            public static final countCanBuild cmd = new countCanBuild();
        public countCanBuild nations(String value) {
            return set("nations", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getAverage")
        public static class getAverage extends CommandRef {
            public static final getAverage cmd = new getAverage();
        public getAverage nations(String value) {
            return set("nations", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getBaseProduction")
        public static class getBaseProduction extends CommandRef {
            public static final getBaseProduction cmd = new getBaseProduction();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getCap")
        public static class getCap extends CommandRef {
            public static final getCap cmd = new getCap();
        public getCap hasProject(String value) {
            return set("hasProject", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getCommerce")
        public static class getCommerce extends CommandRef {
            public static final getCommerce cmd = new getCommerce();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getContinents")
        public static class getContinents extends CommandRef {
            public static final getContinents cmd = new getContinents();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getCostMap")
        public static class getCostMap extends CommandRef {
            public static final getCostMap cmd = new getCostMap();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getInfraBase")
        public static class getInfraBase extends CommandRef {
            public static final getInfraBase cmd = new getInfraBase();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getInfraMax")
        public static class getInfraMax extends CommandRef {
            public static final getInfraMax cmd = new getInfraMax();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getMarketCost")
        public static class getMarketCost extends CommandRef {
            public static final getMarketCost cmd = new getMarketCost();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getMilitaryUnit")
        public static class getMilitaryUnit extends CommandRef {
            public static final getMilitaryUnit cmd = new getMilitaryUnit();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getNMarketCost")
        public static class getNMarketCost extends CommandRef {
            public static final getNMarketCost cmd = new getNMarketCost();
        public getNMarketCost num(String value) {
            return set("num", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getPollution")
        public static class getPollution extends CommandRef {
            public static final getPollution cmd = new getPollution();
        public getPollution hasProject(String value) {
            return set("hasProject", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getPowerResource")
        public static class getPowerResource extends CommandRef {
            public static final getPowerResource cmd = new getPowerResource();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getPowerResourceConsumed")
        public static class getPowerResourceConsumed extends CommandRef {
            public static final getPowerResourceConsumed cmd = new getPowerResourceConsumed();
        public getPowerResourceConsumed infra(String value) {
            return set("infra", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getRequiredCitizens")
        public static class getRequiredCitizens extends CommandRef {
            public static final getRequiredCitizens cmd = new getRequiredCitizens();

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
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getResourceProduced")
        public static class getResourceProduced extends CommandRef {
            public static final getResourceProduced cmd = new getResourceProduced();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getResourceTypesConsumed")
        public static class getResourceTypesConsumed extends CommandRef {
            public static final getResourceTypesConsumed cmd = new getResourceTypesConsumed();

        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders.class,method="getResourceValue")
        public static class getResourceValue extends CommandRef {
            public static final getResourceValue cmd = new getResourceValue();
        public getResourceValue resources(String value) {
            return set("resources", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getTotal")
        public static class getTotal extends CommandRef {
            public static final getTotal cmd = new getTotal();
        public getTotal nations(String value) {
            return set("nations", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getType")
        public static class getType extends CommandRef {
            public static final getType cmd = new getType();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getUnitCap")
        public static class getUnitCap extends CommandRef {
            public static final getUnitCap cmd = new getUnitCap();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getUnitDailyBuy")
        public static class getUnitDailyBuy extends CommandRef {
            public static final getUnitDailyBuy cmd = new getUnitDailyBuy();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getUpkeep")
        public static class getUpkeep extends CommandRef {
            public static final getUpkeep cmd = new getUpkeep();
        public getUpkeep type(String value) {
            return set("type", value);
        }

        public getUpkeep hasProject(String value) {
            return set("hasProject", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="getUpkeepMap")
        public static class getUpkeepMap extends CommandRef {
            public static final getUpkeepMap cmd = new getUpkeepMap();
        public getUpkeepMap hasProject(String value) {
            return set("hasProject", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="name")
        public static class name extends CommandRef {
            public static final name cmd = new name();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="nameSnakeCase")
        public static class nameSnakeCase extends CommandRef {
            public static final nameSnakeCase cmd = new nameSnakeCase();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.building.Building.class,method="ordinal")
        public static class ordinal extends CommandRef {
            public static final ordinal cmd = new ordinal();

        }

}
