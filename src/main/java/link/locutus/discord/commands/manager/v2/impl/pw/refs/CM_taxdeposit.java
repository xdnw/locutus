package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class CM_taxdeposit {
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getAlliance")
        public static class getAlliance extends CommandRef {
            public static final getAlliance cmd = new getAlliance();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getAllianceId")
        public static class getAllianceId extends CommandRef {
            public static final getAllianceId cmd = new getAllianceId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getAllianceInfo")
        public static class getAllianceInfo extends CommandRef {
            public static final getAllianceInfo cmd = new getAllianceInfo();
        public getAllianceInfo allianceFunction(String value) {
            return set("allianceFunction", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getAmount")
        public static class getAmount extends CommandRef {
            public static final getAmount cmd = new getAmount();
        public getAmount type(String value) {
            return set("type", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getDateMs")
        public static class getDateMs extends CommandRef {
            public static final getDateMs cmd = new getDateMs();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getDateStr")
        public static class getDateStr extends CommandRef {
            public static final getDateStr cmd = new getDateStr();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getInternalMoneyRate")
        public static class getInternalMoneyRate extends CommandRef {
            public static final getInternalMoneyRate cmd = new getInternalMoneyRate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getInternalResourceRate")
        public static class getInternalResourceRate extends CommandRef {
            public static final getInternalResourceRate cmd = new getInternalResourceRate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getMarketValue")
        public static class getMarketValue extends CommandRef {
            public static final getMarketValue cmd = new getMarketValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getMoneyRate")
        public static class getMoneyRate extends CommandRef {
            public static final getMoneyRate cmd = new getMoneyRate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getNation")
        public static class getNation extends CommandRef {
            public static final getNation cmd = new getNation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getNationId")
        public static class getNationId extends CommandRef {
            public static final getNationId cmd = new getNationId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getNationInfo")
        public static class getNationInfo extends CommandRef {
            public static final getNationInfo cmd = new getNationInfo();
        public getNationInfo nationFunction(String value) {
            return set("nationFunction", value);
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
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getResourceRate")
        public static class getResourceRate extends CommandRef {
            public static final getResourceRate cmd = new getResourceRate();

        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders.class,method="getResourceValue")
        public static class getResourceValue extends CommandRef {
            public static final getResourceValue cmd = new getResourceValue();
        public getResourceValue resources(String value) {
            return set("resources", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getResourcesArray")
        public static class getResourcesArray extends CommandRef {
            public static final getResourcesArray cmd = new getResourcesArray();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getResourcesJson")
        public static class getResourcesJson extends CommandRef {
            public static final getResourcesJson cmd = new getResourcesJson();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getResourcesMap")
        public static class getResourcesMap extends CommandRef {
            public static final getResourcesMap cmd = new getResourcesMap();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getTaxId")
        public static class getTaxId extends CommandRef {
            public static final getTaxId cmd = new getTaxId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.TaxDeposit.class,method="getTurnsOld")
        public static class getTurnsOld extends CommandRef {
            public static final getTurnsOld cmd = new getTurnsOld();

        }

}
