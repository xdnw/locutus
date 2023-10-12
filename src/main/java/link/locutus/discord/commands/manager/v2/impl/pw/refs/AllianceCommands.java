package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class AllianceCommands {
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="countNations")
        public static class countNations extends CommandRef {
            public static final countNations cmd = new countNations();
            public countNations create(String filter) {
                return createArgs("filter", filter);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="exponentialCityStrength")
        public static class exponentialCityStrength extends CommandRef {
            public static final exponentialCityStrength cmd = new exponentialCityStrength();
            public exponentialCityStrength create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getAcronym")
        public static class getAcronym extends CommandRef {
            public static final getAcronym cmd = new getAcronym();
            public getAcronym create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getAlliance_id")
        public static class getAlliance_id extends CommandRef {
            public static final getAlliance_id cmd = new getAlliance_id();
            public getAlliance_id create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getAverage")
        public static class getAverage extends CommandRef {
            public static final getAverage cmd = new getAverage();
            public getAverage create(String attribute, String filter) {
                return createArgs("attribute", attribute, "filter", filter);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getAveragePer")
        public static class getAveragePer extends CommandRef {
            public static final getAveragePer cmd = new getAveragePer();
            public getAveragePer create(String attribute, String per, String filter) {
                return createArgs("attribute", attribute, "per", per, "filter", filter);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getColor")
        public static class getColor extends CommandRef {
            public static final getColor cmd = new getColor();
            public getColor create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getDateCreated")
        public static class getDateCreated extends CommandRef {
            public static final getDateCreated cmd = new getDateCreated();
            public getDateCreated create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getDiscord_link")
        public static class getDiscord_link extends CommandRef {
            public static final getDiscord_link cmd = new getDiscord_link();
            public getDiscord_link create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getFlag")
        public static class getFlag extends CommandRef {
            public static final getFlag cmd = new getFlag();
            public getFlag create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getForum_link")
        public static class getForum_link extends CommandRef {
            public static final getForum_link cmd = new getForum_link();
            public getForum_link create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();
            public getId create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();
            public getName create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getNumWarsSince")
        public static class getNumWarsSince extends CommandRef {
            public static final getNumWarsSince cmd = new getNumWarsSince();
            public getNumWarsSince create(String date) {
                return createArgs("date", date);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getRank")
        public static class getRank extends CommandRef {
            public static final getRank cmd = new getRank();
            public getRank create(String filter) {
                return createArgs("filter", filter);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders.class,method="getResource")
        public static class getResource extends CommandRef {
            public static final getResource cmd = new getResource();
            public getResource create(String resources, String resource) {
                return createArgs("resources", resources, "resource", resource);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders.class,method="getResourceValue")
        public static class getResourceValue extends CommandRef {
            public static final getResourceValue cmd = new getResourceValue();
            public getResourceValue create(String resources) {
                return createArgs("resources", resources);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getRevenue")
        public static class getRevenue extends CommandRef {
            public static final getRevenue cmd = new getRevenue();
            public getRevenue create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getScore")
        public static class getScore extends CommandRef {
            public static final getScore cmd = new getScore();
            public getScore create(String filter) {
                return createArgs("filter", filter);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getTotal")
        public static class getTotal extends CommandRef {
            public static final getTotal cmd = new getTotal();
            public getTotal create(String attribute, String filter) {
                return createArgs("attribute", attribute, "filter", filter);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getTreatiedAllies")
        public static class getTreatiedAllies extends CommandRef {
            public static final getTreatiedAllies cmd = new getTreatiedAllies();
            public getTreatiedAllies create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getTreatyOrdinal")
        public static class getTreatyOrdinal extends CommandRef {
            public static final getTreatyOrdinal cmd = new getTreatyOrdinal();
            public getTreatyOrdinal create(String alliance) {
                return createArgs("alliance", alliance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getTreatyType")
        public static class getTreatyType extends CommandRef {
            public static final getTreatyType cmd = new getTreatyType();
            public getTreatyType create(String alliance) {
                return createArgs("alliance", alliance);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getWiki_link")
        public static class getWiki_link extends CommandRef {
            public static final getWiki_link cmd = new getWiki_link();
            public getWiki_link create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="hasDefensiveTreaty")
        public static class hasDefensiveTreaty extends CommandRef {
            public static final hasDefensiveTreaty cmd = new hasDefensiveTreaty();
            public hasDefensiveTreaty create(String alliances) {
                return createArgs("alliances", alliances);
            }
        }

}
