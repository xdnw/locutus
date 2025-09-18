package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_ban {
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBan.class,method="getDate")
        public static class getDate extends CommandRef {
            public static final getDate cmd = new getDate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBan.class,method="getDiscordId")
        public static class getDiscordId extends CommandRef {
            public static final getDiscordId cmd = new getDiscordId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBan.class,method="getEndDate")
        public static class getEndDate extends CommandRef {
            public static final getEndDate cmd = new getEndDate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBan.class,method="getExistingNation")
        public static class getExistingNation extends CommandRef {
            public static final getExistingNation cmd = new getExistingNation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBan.class,method="getNation_id")
        public static class getNation_id extends CommandRef {
            public static final getNation_id cmd = new getNation_id();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBan.class,method="getReason")
        public static class getReason extends CommandRef {
            public static final getReason cmd = new getReason();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBan.class,method="getTimeRemaining")
        public static class getTimeRemaining extends CommandRef {
            public static final getTimeRemaining cmd = new getTimeRemaining();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBan.class,method="hasExistingNation")
        public static class hasExistingNation extends CommandRef {
            public static final hasExistingNation cmd = new hasExistingNation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBan.class,method="isExpired")
        public static class isExpired extends CommandRef {
            public static final isExpired cmd = new isExpired();

        }

}
