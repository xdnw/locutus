package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_textchannel {
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="canBotTalk")
        public static class canBotTalk extends CommandRef {
            public static final canBotTalk cmd = new canBotTalk();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="canTalk")
        public static class canTalk extends CommandRef {
            public static final canTalk cmd = new canTalk();
        public canTalk user(String value) {
            return set("user", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="getCategoryPosition")
        public static class getCategoryPosition extends CommandRef {
            public static final getCategoryPosition cmd = new getCategoryPosition();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="getGuild")
        public static class getGuild extends CommandRef {
            public static final getGuild cmd = new getGuild();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="getJumpUrl")
        public static class getJumpUrl extends CommandRef {
            public static final getJumpUrl cmd = new getJumpUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="getMembers")
        public static class getMembers extends CommandRef {
            public static final getMembers cmd = new getMembers();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="getMention")
        public static class getMention extends CommandRef {
            public static final getMention cmd = new getMention();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="getPermission")
        public static class getPermission extends CommandRef {
            public static final getPermission cmd = new getPermission();
        public getPermission role(String value) {
            return set("role", value);
        }

        public getPermission permission(String value) {
            return set("permission", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="getPosition")
        public static class getPosition extends CommandRef {
            public static final getPosition cmd = new getPosition();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="getRawPosition")
        public static class getRawPosition extends CommandRef {
            public static final getRawPosition cmd = new getRawPosition();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="getTopic")
        public static class getTopic extends CommandRef {
            public static final getTopic cmd = new getTopic();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TextChannelWrapper.class,method="isNSFW")
        public static class isNSFW extends CommandRef {
            public static final isNSFW cmd = new isNSFW();

        }

}
