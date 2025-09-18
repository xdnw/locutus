package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_resourcetype {
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="canProduceInAny")
        public static class canProduceInAny extends CommandRef {
            public static final canProduceInAny cmd = new canProduceInAny();
        public canProduceInAny continents(String value) {
            return set("continents", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getAverageMargin")
        public static class getAverageMargin extends CommandRef {
            public static final getAverageMargin cmd = new getAverageMargin();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getBaseInput")
        public static class getBaseInput extends CommandRef {
            public static final getBaseInput cmd = new getBaseInput();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getBoostFactor")
        public static class getBoostFactor extends CommandRef {
            public static final getBoostFactor cmd = new getBoostFactor();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getBuilding")
        public static class getBuilding extends CommandRef {
            public static final getBuilding cmd = new getBuilding();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getCap")
        public static class getCap extends CommandRef {
            public static final getCap cmd = new getCap();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getContinents")
        public static class getContinents extends CommandRef {
            public static final getContinents cmd = new getContinents();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getGraphId")
        public static class getGraphId extends CommandRef {
            public static final getGraphId cmd = new getGraphId();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getHigh")
        public static class getHigh extends CommandRef {
            public static final getHigh cmd = new getHigh();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getHighAverage")
        public static class getHighAverage extends CommandRef {
            public static final getHighAverage cmd = new getHighAverage();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getInputList")
        public static class getInputList extends CommandRef {
            public static final getInputList cmd = new getInputList();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getLow")
        public static class getLow extends CommandRef {
            public static final getLow cmd = new getLow();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getLowAverage")
        public static class getLowAverage extends CommandRef {
            public static final getLowAverage cmd = new getLowAverage();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getManufacturingMultiplier")
        public static class getManufacturingMultiplier extends CommandRef {
            public static final getManufacturingMultiplier cmd = new getManufacturingMultiplier();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getMargin")
        public static class getMargin extends CommandRef {
            public static final getMargin cmd = new getMargin();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getMarketValue")
        public static class getMarketValue extends CommandRef {
            public static final getMarketValue cmd = new getMarketValue();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getPollution")
        public static class getPollution extends CommandRef {
            public static final getPollution cmd = new getPollution();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getProduction")
        public static class getProduction extends CommandRef {
            public static final getProduction cmd = new getProduction();
        public getProduction nations(String value) {
            return set("nations", value);
        }

        public getProduction includeNegatives(String value) {
            return set("includeNegatives", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getProject")
        public static class getProject extends CommandRef {
            public static final getProject cmd = new getProject();

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
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="getUpkeep")
        public static class getUpkeep extends CommandRef {
            public static final getUpkeep cmd = new getUpkeep();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="hasBuilding")
        public static class hasBuilding extends CommandRef {
            public static final hasBuilding cmd = new hasBuilding();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="isManufactured")
        public static class isManufactured extends CommandRef {
            public static final isManufactured cmd = new isManufactured();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.ResourceType.class,method="isRaw")
        public static class isRaw extends CommandRef {
            public static final isRaw cmd = new isRaw();

        }

}
