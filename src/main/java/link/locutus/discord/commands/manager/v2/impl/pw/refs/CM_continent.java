package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_continent {
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="canBuild")
        public static class canBuild extends CommandRef {
            public static final canBuild cmd = new canBuild();
        public canBuild building(String value) {
            return set("building", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getAverage")
        public static class getAverage extends CommandRef {
            public static final getAverage cmd = new getAverage();
        public getAverage attribute(String value) {
            return set("attribute", value);
        }

        public getAverage filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getAveragePer")
        public static class getAveragePer extends CommandRef {
            public static final getAveragePer cmd = new getAveragePer();
        public getAveragePer attribute(String value) {
            return set("attribute", value);
        }

        public getAveragePer per(String value) {
            return set("per", value);
        }

        public getAveragePer filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getBuildings")
        public static class getBuildings extends CommandRef {
            public static final getBuildings cmd = new getBuildings();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getFoodRatio")
        public static class getFoodRatio extends CommandRef {
            public static final getFoodRatio cmd = new getFoodRatio();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getNumNations")
        public static class getNumNations extends CommandRef {
            public static final getNumNations cmd = new getNumNations();
        public getNumNations filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getOrdinal")
        public static class getOrdinal extends CommandRef {
            public static final getOrdinal cmd = new getOrdinal();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getRadIndex")
        public static class getRadIndex extends CommandRef {
            public static final getRadIndex cmd = new getRadIndex();

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
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getResources")
        public static class getResources extends CommandRef {
            public static final getResources cmd = new getResources();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getSeasonModifier")
        public static class getSeasonModifier extends CommandRef {
            public static final getSeasonModifier cmd = new getSeasonModifier();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getSeasonModifierDate")
        public static class getSeasonModifierDate extends CommandRef {
            public static final getSeasonModifierDate cmd = new getSeasonModifierDate();
        public getSeasonModifierDate instant(String value) {
            return set("instant", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="getTotal")
        public static class getTotal extends CommandRef {
            public static final getTotal cmd = new getTotal();
        public getTotal attribute(String value) {
            return set("attribute", value);
        }

        public getTotal filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="hasResource")
        public static class hasResource extends CommandRef {
            public static final hasResource cmd = new hasResource();
        public hasResource type(String value) {
            return set("type", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.Continent.class,method="isNorth")
        public static class isNorth extends CommandRef {
            public static final isNorth cmd = new isNorth();

        }

}
