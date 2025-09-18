package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_user {
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getAgeMs")
        public static class getAgeMs extends CommandRef {
            public static final getAgeMs cmd = new getAgeMs();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getAvatarUrl")
        public static class getAvatarUrl extends CommandRef {
            public static final getAvatarUrl cmd = new getAvatarUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getColor")
        public static class getColor extends CommandRef {
            public static final getColor cmd = new getColor();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getColorRaw")
        public static class getColorRaw extends CommandRef {
            public static final getColorRaw cmd = new getColorRaw();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getCreatedMs")
        public static class getCreatedMs extends CommandRef {
            public static final getCreatedMs cmd = new getCreatedMs();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getEffectiveAvatarUrl")
        public static class getEffectiveAvatarUrl extends CommandRef {
            public static final getEffectiveAvatarUrl cmd = new getEffectiveAvatarUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getEffectiveName")
        public static class getEffectiveName extends CommandRef {
            public static final getEffectiveName cmd = new getEffectiveName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getMention")
        public static class getMention extends CommandRef {
            public static final getMention cmd = new getMention();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getNation")
        public static class getNation extends CommandRef {
            public static final getNation cmd = new getNation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getNickname")
        public static class getNickname extends CommandRef {
            public static final getNickname cmd = new getNickname();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getOnlineStatus")
        public static class getOnlineStatus extends CommandRef {
            public static final getOnlineStatus cmd = new getOnlineStatus();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getRoles")
        public static class getRoles extends CommandRef {
            public static final getRoles cmd = new getRoles();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getServerAgeMs")
        public static class getServerAgeMs extends CommandRef {
            public static final getServerAgeMs cmd = new getServerAgeMs();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getTimeJoinedMs")
        public static class getTimeJoinedMs extends CommandRef {
            public static final getTimeJoinedMs cmd = new getTimeJoinedMs();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getUrl")
        public static class getUrl extends CommandRef {
            public static final getUrl cmd = new getUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getUserId")
        public static class getUserId extends CommandRef {
            public static final getUserId cmd = new getUserId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="getUserName")
        public static class getUserName extends CommandRef {
            public static final getUserName cmd = new getUserName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="hasAccess")
        public static class hasAccess extends CommandRef {
            public static final hasAccess cmd = new hasAccess();
        public hasAccess channel(String value) {
            return set("channel", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="hasAllRoles")
        public static class hasAllRoles extends CommandRef {
            public static final hasAllRoles cmd = new hasAllRoles();
        public hasAllRoles roles(String value) {
            return set("roles", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="hasAnyRoles")
        public static class hasAnyRoles extends CommandRef {
            public static final hasAnyRoles cmd = new hasAnyRoles();
        public hasAnyRoles roles(String value) {
            return set("roles", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="hasPermission")
        public static class hasPermission extends CommandRef {
            public static final hasPermission cmd = new hasPermission();
        public hasPermission permission(String value) {
            return set("permission", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="hasPermissionChannel")
        public static class hasPermissionChannel extends CommandRef {
            public static final hasPermissionChannel cmd = new hasPermissionChannel();
        public hasPermissionChannel channel(String value) {
            return set("channel", value);
        }

        public hasPermissionChannel permission(String value) {
            return set("permission", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="hasRole")
        public static class hasRole extends CommandRef {
            public static final hasRole cmd = new hasRole();
        public hasRole role(String value) {
            return set("role", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.UserWrapper.class,method="matches")
        public static class matches extends CommandRef {
            public static final matches cmd = new matches();
        public matches filter(String value) {
            return set("filter", value);
        }

        }

}
