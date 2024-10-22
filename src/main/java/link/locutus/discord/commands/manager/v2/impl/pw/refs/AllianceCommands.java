package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class AllianceCommands {
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="countNations")
        public static class countNations extends CommandRef {
            public static final countNations cmd = new countNations();
        public countNations filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="exponentialCityStrength")
        public static class exponentialCityStrength extends CommandRef {
            public static final exponentialCityStrength cmd = new exponentialCityStrength();
        public exponentialCityStrength power(String value) {
            return set("power", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getAcronym")
        public static class getAcronym extends CommandRef {
            public static final getAcronym cmd = new getAcronym();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getAlliance_id")
        public static class getAlliance_id extends CommandRef {
            public static final getAlliance_id cmd = new getAlliance_id();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getAverage")
        public static class getAverage extends CommandRef {
            public static final getAverage cmd = new getAverage();
        public getAverage attribute(String value) {
            return set("attribute", value);
        }

        public getAverage filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getAveragePer")
        public static class getAveragePer extends CommandRef {
            public static final getAveragePer cmd = new getAveragePer();
        public getAveragePer attribute(String value) {
            return set("attribute", value);
        }

        public getAveragePer per(String value) {
            return set("per", value);
        }

        public getAveragePer filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getColor")
        public static class getColor extends CommandRef {
            public static final getColor cmd = new getColor();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getDateCreated")
        public static class getDateCreated extends CommandRef {
            public static final getDateCreated cmd = new getDateCreated();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getDiscord_link")
        public static class getDiscord_link extends CommandRef {
            public static final getDiscord_link cmd = new getDiscord_link();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getFlag")
        public static class getFlag extends CommandRef {
            public static final getFlag cmd = new getFlag();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getForum_link")
        public static class getForum_link extends CommandRef {
            public static final getForum_link cmd = new getForum_link();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getNumTreasures")
        public static class getNumTreasures extends CommandRef {
            public static final getNumTreasures cmd = new getNumTreasures();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getNumWarsSince")
        public static class getNumWarsSince extends CommandRef {
            public static final getNumWarsSince cmd = new getNumWarsSince();
        public getNumWarsSince date(String value) {
            return set("date", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getRank")
        public static class getRank extends CommandRef {
            public static final getRank cmd = new getRank();
        public getRank filter(String value) {
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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getRevenue")
        public static class getRevenue extends CommandRef {
            public static final getRevenue cmd = new getRevenue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getRevenueConverted")
        public static class getRevenueConverted extends CommandRef {
            public static final getRevenueConverted cmd = new getRevenueConverted();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getScore")
        public static class getScore extends CommandRef {
            public static final getScore cmd = new getScore();
        public getScore filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getTotal")
        public static class getTotal extends CommandRef {
            public static final getTotal cmd = new getTotal();
        public getTotal attribute(String value) {
            return set("attribute", value);
        }

        public getTotal filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getTreasureBonus")
        public static class getTreasureBonus extends CommandRef {
            public static final getTreasureBonus cmd = new getTreasureBonus();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getTreatiedAllies")
        public static class getTreatiedAllies extends CommandRef {
            public static final getTreatiedAllies cmd = new getTreatiedAllies();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getTreatyOrdinal")
        public static class getTreatyOrdinal extends CommandRef {
            public static final getTreatyOrdinal cmd = new getTreatyOrdinal();
        public getTreatyOrdinal alliance(String value) {
            return set("alliance", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getTreatyType")
        public static class getTreatyType extends CommandRef {
            public static final getTreatyType cmd = new getTreatyType();
        public getTreatyType alliance(String value) {
            return set("alliance", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getUrl")
        public static class getUrl extends CommandRef {
            public static final getUrl cmd = new getUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getWiki_link")
        public static class getWiki_link extends CommandRef {
            public static final getWiki_link cmd = new getWiki_link();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="hasDefensiveTreaty")
        public static class hasDefensiveTreaty extends CommandRef {
            public static final hasDefensiveTreaty cmd = new hasDefensiveTreaty();
        public hasDefensiveTreaty alliances(String value) {
            return set("alliances", value);
        }

        }

}
