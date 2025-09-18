package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_nationcolor {
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.NationColor.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.NationColor.class,method="getNumNations")
        public static class getNumNations extends CommandRef {
            public static final getNumNations cmd = new getNumNations();
        public getNumNations filter(String value) {
            return set("filter", value);
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
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders.class,method="getResourceValue")
        public static class getResourceValue extends CommandRef {
            public static final getResourceValue cmd = new getResourceValue();
        public getResourceValue resources(String value) {
            return set("resources", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.NationColor.class,method="getTurnBonus")
        public static class getTurnBonus extends CommandRef {
            public static final getTurnBonus cmd = new getTurnBonus();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.NationColor.class,method="getVotedName")
        public static class getVotedName extends CommandRef {
            public static final getVotedName cmd = new getVotedName();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.NationColor.class,method="isTaxable")
        public static class isTaxable extends CommandRef {
            public static final isTaxable cmd = new isTaxable();

        }

}
