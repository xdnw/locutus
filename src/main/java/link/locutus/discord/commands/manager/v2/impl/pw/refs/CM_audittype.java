package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_audittype {
        @AutoRegister(clazz=link.locutus.discord.util.task.ia.AuditType.class,method="getEmoji")
        public static class getEmoji extends CommandRef {
            public static final getEmoji cmd = new getEmoji();

        }
        @AutoRegister(clazz=link.locutus.discord.util.task.ia.AuditType.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.util.task.ia.AuditType.class,method="getRequired")
        public static class getRequired extends CommandRef {
            public static final getRequired cmd = new getRequired();

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
        @AutoRegister(clazz=link.locutus.discord.util.task.ia.AuditType.class,method="getSeverity")
        public static class getSeverity extends CommandRef {
            public static final getSeverity cmd = new getSeverity();

        }
        @AutoRegister(clazz=link.locutus.discord.util.task.ia.AuditType.class,method="requiresApi")
        public static class requiresApi extends CommandRef {
            public static final requiresApi cmd = new requiresApi();

        }
        @AutoRegister(clazz=link.locutus.discord.util.task.ia.AuditType.class,method="requiresDiscord")
        public static class requiresDiscord extends CommandRef {
            public static final requiresDiscord cmd = new requiresDiscord();

        }

}
