package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_bounty {
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBounty.class,method="getAmount")
        public static class getAmount extends CommandRef {
            public static final getAmount cmd = new getAmount();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBounty.class,method="getDate")
        public static class getDate extends CommandRef {
            public static final getDate cmd = new getDate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBounty.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBounty.class,method="getNation")
        public static class getNation extends CommandRef {
            public static final getNation cmd = new getNation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBounty.class,method="getNationId")
        public static class getNationId extends CommandRef {
            public static final getNationId cmd = new getNationId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBounty.class,method="getPostedBy")
        public static class getPostedBy extends CommandRef {
            public static final getPostedBy cmd = new getPostedBy();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBounty.class,method="getType")
        public static class getType extends CommandRef {
            public static final getType cmd = new getType();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBBounty.class,method="toLineString")
        public static class toLineString extends CommandRef {
            public static final toLineString cmd = new toLineString();

        }

}
