package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_guild {
        @AutoRegister(clazz=link.locutus.discord.db.GuildDB.class,method="getAlliance_id")
        public static class getAlliance_id extends CommandRef {
            public static final getAlliance_id cmd = new getAlliance_id();

        }
        @AutoRegister(clazz=link.locutus.discord.db.GuildDB.class,method="getDelegateServer")
        public static class getDelegateServer extends CommandRef {
            public static final getDelegateServer cmd = new getDelegateServer();

        }
        @AutoRegister(clazz=link.locutus.discord.db.GuildDB.class,method="getIdLong")
        public static class getIdLong extends CommandRef {
            public static final getIdLong cmd = new getIdLong();

        }
        @AutoRegister(clazz=link.locutus.discord.db.GuildDB.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.GuildDB.class,method="getOffshoredBalance")
        public static class getOffshoredBalance extends CommandRef {
            public static final getOffshoredBalance cmd = new getOffshoredBalance();
        public getOffshoredBalance update(String value) {
            return set("update", value);
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
        @AutoRegister(clazz=link.locutus.discord.db.GuildDB.class,method="hasAlliance")
        public static class hasAlliance extends CommandRef {
            public static final hasAlliance cmd = new hasAlliance();

        }
        @AutoRegister(clazz=link.locutus.discord.db.GuildDB.class,method="isAlliance")
        public static class isAlliance extends CommandRef {
            public static final isAlliance cmd = new isAlliance();

        }
        @AutoRegister(clazz=link.locutus.discord.db.GuildDB.class,method="isDelegateServer")
        public static class isDelegateServer extends CommandRef {
            public static final isDelegateServer cmd = new isDelegateServer();

        }
        @AutoRegister(clazz=link.locutus.discord.db.GuildDB.class,method="isOffshore")
        public static class isOffshore extends CommandRef {
            public static final isOffshore cmd = new isOffshore();

        }
        @AutoRegister(clazz=link.locutus.discord.db.GuildDB.class,method="isOwnerActive")
        public static class isOwnerActive extends CommandRef {
            public static final isOwnerActive cmd = new isOwnerActive();

        }
        @AutoRegister(clazz=link.locutus.discord.db.GuildDB.class,method="isValidAlliance")
        public static class isValidAlliance extends CommandRef {
            public static final isValidAlliance cmd = new isValidAlliance();

        }

}
