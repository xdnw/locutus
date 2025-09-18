package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_conflict {
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getActiveWars")
        public static class getActiveWars extends CommandRef {
            public static final getActiveWars cmd = new getActiveWars();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getAllianceList")
        public static class getAllianceList extends CommandRef {
            public static final getAllianceList cmd = new getAllianceList();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getCasusBelli")
        public static class getCasusBelli extends CommandRef {
            public static final getCasusBelli cmd = new getCasusBelli();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getCategory")
        public static class getCategory extends CommandRef {
            public static final getCategory cmd = new getCategory();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getCol1List")
        public static class getCol1List extends CommandRef {
            public static final getCol1List cmd = new getCol1List();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getCol2List")
        public static class getCol2List extends CommandRef {
            public static final getCol2List cmd = new getCol2List();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getDamageConverted")
        public static class getDamageConverted extends CommandRef {
            public static final getDamageConverted cmd = new getDamageConverted();
        public getDamageConverted isPrimary(String value) {
            return set("isPrimary", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getEndMS")
        public static class getEndMS extends CommandRef {
            public static final getEndMS cmd = new getEndMS();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getEndTurn")
        public static class getEndTurn extends CommandRef {
            public static final getEndTurn cmd = new getEndTurn();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getGuild")
        public static class getGuild extends CommandRef {
            public static final getGuild cmd = new getGuild();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getGuildId")
        public static class getGuildId extends CommandRef {
            public static final getGuildId cmd = new getGuildId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getOrdinal")
        public static class getOrdinal extends CommandRef {
            public static final getOrdinal cmd = new getOrdinal();

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
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getSide")
        public static class getSide extends CommandRef {
            public static final getSide cmd = new getSide();
        public getSide alliance(String value) {
            return set("alliance", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getStartMS")
        public static class getStartMS extends CommandRef {
            public static final getStartMS cmd = new getStartMS();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getStartTurn")
        public static class getStartTurn extends CommandRef {
            public static final getStartTurn cmd = new getStartTurn();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getStatusDesc")
        public static class getStatusDesc extends CommandRef {
            public static final getStatusDesc cmd = new getStatusDesc();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getTotalWars")
        public static class getTotalWars extends CommandRef {
            public static final getTotalWars cmd = new getTotalWars();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getUrl")
        public static class getUrl extends CommandRef {
            public static final getUrl cmd = new getUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="getWiki")
        public static class getWiki extends CommandRef {
            public static final getWiki cmd = new getWiki();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="isDirty")
        public static class isDirty extends CommandRef {
            public static final isDirty cmd = new isDirty();

        }
        @AutoRegister(clazz=link.locutus.discord.db.conflict.Conflict.class,method="isParticipant")
        public static class isParticipant extends CommandRef {
            public static final isParticipant cmd = new isParticipant();
        public isParticipant alliance(String value) {
            return set("alliance", value);
        }

        }

}
