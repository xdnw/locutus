package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_guildsetting {
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="getCategory")
        public static class getCategory extends CommandRef {
            public static final getCategory cmd = new getCategory();

        }
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="getCommandMention")
        public static class getCommandMention extends CommandRef {
            public static final getCommandMention cmd = new getCommandMention();

        }
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="getKeyName")
        public static class getKeyName extends CommandRef {
            public static final getKeyName cmd = new getKeyName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="getName")
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
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="getTypeName")
        public static class getTypeName extends CommandRef {
            public static final getTypeName cmd = new getTypeName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="getValueString")
        public static class getValueString extends CommandRef {
            public static final getValueString cmd = new getValueString();

        }
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="hasInvalidValue")
        public static class hasInvalidValue extends CommandRef {
            public static final hasInvalidValue cmd = new hasInvalidValue();
        public hasInvalidValue checkDelegate(String value) {
            return set("checkDelegate", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="hasValue")
        public static class hasValue extends CommandRef {
            public static final hasValue cmd = new hasValue();
        public hasValue checkDelegate(String value) {
            return set("checkDelegate", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="help")
        public static class help extends CommandRef {
            public static final help cmd = new help();

        }
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="isChannelType")
        public static class isChannelType extends CommandRef {
            public static final isChannelType cmd = new isChannelType();

        }
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="name")
        public static class name extends CommandRef {
            public static final name cmd = new name();

        }
        @AutoRegister(clazz=link.locutus.discord.db.guild.GuildSetting.class,method="toString")
        public static class toString extends CommandRef {
            public static final toString cmd = new toString();

        }

}
