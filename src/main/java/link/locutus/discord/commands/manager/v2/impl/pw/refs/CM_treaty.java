package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_treaty {
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getDate")
        public static class getDate extends CommandRef {
            public static final getDate cmd = new getDate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getEndTime")
        public static class getEndTime extends CommandRef {
            public static final getEndTime cmd = new getEndTime();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getExpiresDiscordString")
        public static class getExpiresDiscordString extends CommandRef {
            public static final getExpiresDiscordString cmd = new getExpiresDiscordString();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getExpiresString")
        public static class getExpiresString extends CommandRef {
            public static final getExpiresString cmd = new getExpiresString();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getFrom")
        public static class getFrom extends CommandRef {
            public static final getFrom cmd = new getFrom();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getFromId")
        public static class getFromId extends CommandRef {
            public static final getFromId cmd = new getFromId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getTo")
        public static class getTo extends CommandRef {
            public static final getTo cmd = new getTo();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getToId")
        public static class getToId extends CommandRef {
            public static final getToId cmd = new getToId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getTurnEnds")
        public static class getTurnEnds extends CommandRef {
            public static final getTurnEnds cmd = new getTurnEnds();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getTurnsRemaining")
        public static class getTurnsRemaining extends CommandRef {
            public static final getTurnsRemaining cmd = new getTurnsRemaining();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="getType")
        public static class getType extends CommandRef {
            public static final getType cmd = new getType();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="isAlliance")
        public static class isAlliance extends CommandRef {
            public static final isAlliance cmd = new isAlliance();
        public isAlliance fromOrTo(String value) {
            return set("fromOrTo", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="isPending")
        public static class isPending extends CommandRef {
            public static final isPending cmd = new isPending();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.Treaty.class,method="toLineString")
        public static class toLineString extends CommandRef {
            public static final toLineString cmd = new toLineString();

        }

}
