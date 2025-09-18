package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_militaryunit {
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.MilitaryUnit.class,method="getBaseCost")
        public static class getBaseCost extends CommandRef {
            public static final getBaseCost cmd = new getBaseCost();
        public getBaseCost amount(String value) {
            return set("amount", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.MilitaryUnit.class,method="getBaseMonetaryValue")
        public static class getBaseMonetaryValue extends CommandRef {
            public static final getBaseMonetaryValue cmd = new getBaseMonetaryValue();
        public getBaseMonetaryValue amount(String value) {
            return set("amount", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.MilitaryUnit.class,method="getBuffer")
        public static class getBuffer extends CommandRef {
            public static final getBuffer cmd = new getBuffer();

        }
        @AutoRegister(clazz=link.locutus.discord.apiv1.enums.MilitaryUnit.class,method="getMaxPerDay")
        public static class getMaxPerDay extends CommandRef {
            public static final getMaxPerDay cmd = new getMaxPerDay();
        public getMaxPerDay cities(String value) {
            return set("cities", value);
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

}
