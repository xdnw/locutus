package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_nationlist {
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="getAverageMMR")
        public static class getAverageMMR extends CommandRef {
            public static final getAverageMMR cmd = new getAverageMMR();
        public getAverageMMR update(String value) {
            return set("update", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.pnw.NationList.class,method="getAverageMMRUnit")
        public static class getAverageMMRUnit extends CommandRef {
            public static final getAverageMMRUnit cmd = new getAverageMMRUnit();

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

}
