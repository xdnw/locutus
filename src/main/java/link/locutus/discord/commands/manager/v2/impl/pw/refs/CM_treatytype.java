package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_treatytype {
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.TreatyType.class,method="getColor")
        public static class getColor extends CommandRef {
            public static final getColor cmd = new getColor();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.TreatyType.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

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
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.TreatyType.class,method="getStrength")
        public static class getStrength extends CommandRef {
            public static final getStrength cmd = new getStrength();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.TreatyType.class,method="isDefensive")
        public static class isDefensive extends CommandRef {
            public static final isDefensive cmd = new isDefensive();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.TreatyType.class,method="isMandatoryDefensive")
        public static class isMandatoryDefensive extends CommandRef {
            public static final isMandatoryDefensive cmd = new isMandatoryDefensive();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.TreatyType.class,method="isOffensive")
        public static class isOffensive extends CommandRef {
            public static final isOffensive cmd = new isOffensive();

        }

}
