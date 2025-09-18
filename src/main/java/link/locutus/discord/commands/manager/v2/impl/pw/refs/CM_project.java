package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_project {
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="canBuild")
        public static class canBuild extends CommandRef {
            public static final canBuild cmd = new canBuild();
        public canBuild nation(String value) {
            return set("nation", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="cost")
        public static class cost extends CommandRef {
            public static final cost cmd = new cost();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="getApiName")
        public static class getApiName extends CommandRef {
            public static final getApiName cmd = new getApiName();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="getAvg")
        public static class getAvg extends CommandRef {
            public static final getAvg cmd = new getAvg();
        public getAvg attribute(String value) {
            return set("attribute", value);
        }

        public getAvg nations(String value) {
            return set("nations", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="getCount")
        public static class getCount extends CommandRef {
            public static final getCount cmd = new getCount();
        public getCount nations(String value) {
            return set("nations", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="getImageName")
        public static class getImageName extends CommandRef {
            public static final getImageName cmd = new getImageName();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="getImageUrl")
        public static class getImageUrl extends CommandRef {
            public static final getImageUrl cmd = new getImageUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="getMarketValue")
        public static class getMarketValue extends CommandRef {
            public static final getMarketValue cmd = new getMarketValue();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="getOutput")
        public static class getOutput extends CommandRef {
            public static final getOutput cmd = new getOutput();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="getRequiredProject")
        public static class getRequiredProject extends CommandRef {
            public static final getRequiredProject cmd = new getRequiredProject();
        public getRequiredProject index(String value) {
            return set("index", value);
        }

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
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="getResourceCost")
        public static class getResourceCost extends CommandRef {
            public static final getResourceCost cmd = new getResourceCost();
        public getResourceCost type(String value) {
            return set("type", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders.class,method="getResourceValue")
        public static class getResourceValue extends CommandRef {
            public static final getResourceValue cmd = new getResourceValue();
        public getResourceValue resources(String value) {
            return set("resources", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="getTotal")
        public static class getTotal extends CommandRef {
            public static final getTotal cmd = new getTotal();
        public getTotal attribute(String value) {
            return set("attribute", value);
        }

        public getTotal nations(String value) {
            return set("nations", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="has")
        public static class has extends CommandRef {
            public static final has cmd = new has();
        public has nation(String value) {
            return set("nation", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="hasBit")
        public static class hasBit extends CommandRef {
            public static final hasBit cmd = new hasBit();
        public hasBit bitMask(String value) {
            return set("bitMask", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="hasProjectRequirements")
        public static class hasProjectRequirements extends CommandRef {
            public static final hasProjectRequirements cmd = new hasProjectRequirements();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="isRequiredProject")
        public static class isRequiredProject extends CommandRef {
            public static final isRequiredProject cmd = new isRequiredProject();
        public isRequiredProject project(String value) {
            return set("project", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="maxCities")
        public static class maxCities extends CommandRef {
            public static final maxCities cmd = new maxCities();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="name")
        public static class name extends CommandRef {
            public static final name cmd = new name();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="ordinal")
        public static class ordinal extends CommandRef {
            public static final ordinal cmd = new ordinal();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="requiredCities")
        public static class requiredCities extends CommandRef {
            public static final requiredCities cmd = new requiredCities();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.city.project.Project.class,method="requiredProjects")
        public static class requiredProjects extends CommandRef {
            public static final requiredProjects cmd = new requiredProjects();

        }

}
