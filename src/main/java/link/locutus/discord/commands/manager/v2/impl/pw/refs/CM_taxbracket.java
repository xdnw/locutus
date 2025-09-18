package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_taxbracket {
        @AutoRegister(clazz=link.locutus.discord.db.entities.TaxBracket.class,method="countNations")
        public static class countNations extends CommandRef {
            public static final countNations cmd = new countNations();
        public countNations filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TaxBracket.class,method="getAlliance")
        public static class getAlliance extends CommandRef {
            public static final getAlliance cmd = new getAlliance();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TaxBracket.class,method="getAlliance_id")
        public static class getAlliance_id extends CommandRef {
            public static final getAlliance_id cmd = new getAlliance_id();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TaxBracket.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TaxBracket.class,method="getMoneyRate")
        public static class getMoneyRate extends CommandRef {
            public static final getMoneyRate cmd = new getMoneyRate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TaxBracket.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TaxBracket.class,method="getNationList")
        public static class getNationList extends CommandRef {
            public static final getNationList cmd = new getNationList();
        public getNationList filter(String value) {
            return set("filter", value);
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
        @AutoRegister(clazz=link.locutus.discord.db.entities.TaxBracket.class,method="getRssRate")
        public static class getRssRate extends CommandRef {
            public static final getRssRate cmd = new getRssRate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.TaxBracket.class,method="getUrl")
        public static class getUrl extends CommandRef {
            public static final getUrl cmd = new getUrl();

        }

}
