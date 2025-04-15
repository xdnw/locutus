package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class AllianceCommands {
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="countMembers")
        public static class countMembers extends CommandRef {
            public static final countMembers cmd = new countMembers();

        }
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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getAgeDays")
        public static class getAgeDays extends CommandRef {
            public static final getAgeDays cmd = new getAgeDays();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getBoughtAssetCount")
        public static class getBoughtAssetCount extends CommandRef {
            public static final getBoughtAssetCount cmd = new getBoughtAssetCount();
        public getBoughtAssetCount assets(String value) {
            return set("assets", value);
        }

        public getBoughtAssetCount start(String value) {
            return set("start", value);
        }

        public getBoughtAssetCount end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getCities")
        public static class getCities extends CommandRef {
            public static final getCities cmd = new getCities();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getColor")
        public static class getColor extends CommandRef {
            public static final getColor cmd = new getColor();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getCostConverted")
        public static class getCostConverted extends CommandRef {
            public static final getCostConverted cmd = new getCostConverted();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getCumulativeRevenue")
        public static class getCumulativeRevenue extends CommandRef {
            public static final getCumulativeRevenue cmd = new getCumulativeRevenue();
        public getCumulativeRevenue start(String value) {
            return set("start", value);
        }

        public getCumulativeRevenue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getCumulativeRevenueValue")
        public static class getCumulativeRevenueValue extends CommandRef {
            public static final getCumulativeRevenueValue cmd = new getCumulativeRevenueValue();
        public getCumulativeRevenueValue start(String value) {
            return set("start", value);
        }

        public getCumulativeRevenueValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getDateCreated")
        public static class getDateCreated extends CommandRef {
            public static final getDateCreated cmd = new getDateCreated();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getDiscord_link")
        public static class getDiscord_link extends CommandRef {
            public static final getDiscord_link cmd = new getDiscord_link();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveBoughtAssetCount")
        public static class getEffectiveBoughtAssetCount extends CommandRef {
            public static final getEffectiveBoughtAssetCount cmd = new getEffectiveBoughtAssetCount();
        public getEffectiveBoughtAssetCount assets(String value) {
            return set("assets", value);
        }

        public getEffectiveBoughtAssetCount start(String value) {
            return set("start", value);
        }

        public getEffectiveBoughtAssetCount end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveSpending")
        public static class getEffectiveSpending extends CommandRef {
            public static final getEffectiveSpending cmd = new getEffectiveSpending();
        public getEffectiveSpending assets(String value) {
            return set("assets", value);
        }

        public getEffectiveSpending start(String value) {
            return set("start", value);
        }

        public getEffectiveSpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveSpendingValue")
        public static class getEffectiveSpendingValue extends CommandRef {
            public static final getEffectiveSpendingValue cmd = new getEffectiveSpendingValue();
        public getEffectiveSpendingValue assets(String value) {
            return set("assets", value);
        }

        public getEffectiveSpendingValue start(String value) {
            return set("start", value);
        }

        public getEffectiveSpendingValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEstimatedStockpile")
        public static class getEstimatedStockpile extends CommandRef {
            public static final getEstimatedStockpile cmd = new getEstimatedStockpile();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEstimatedStockpileValue")
        public static class getEstimatedStockpileValue extends CommandRef {
            public static final getEstimatedStockpileValue cmd = new getEstimatedStockpileValue();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getLoot")
        public static class getLoot extends CommandRef {
            public static final getLoot cmd = new getLoot();
        public getLoot score(String value) {
            return set("score", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getLootValue")
        public static class getLootValue extends CommandRef {
            public static final getLootValue cmd = new getLootValue();
        public getLootValue score(String value) {
            return set("score", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getMarkdownUrl")
        public static class getMarkdownUrl extends CommandRef {
            public static final getMarkdownUrl cmd = new getMarkdownUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getMembershipChangeAssetCount")
        public static class getMembershipChangeAssetCount extends CommandRef {
            public static final getMembershipChangeAssetCount cmd = new getMembershipChangeAssetCount();
        public getMembershipChangeAssetCount reasons(String value) {
            return set("reasons", value);
        }

        public getMembershipChangeAssetCount assets(String value) {
            return set("assets", value);
        }

        public getMembershipChangeAssetCount start(String value) {
            return set("start", value);
        }

        public getMembershipChangeAssetCount end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getMembershipChangeAssetRss")
        public static class getMembershipChangeAssetRss extends CommandRef {
            public static final getMembershipChangeAssetRss cmd = new getMembershipChangeAssetRss();
        public getMembershipChangeAssetRss reasons(String value) {
            return set("reasons", value);
        }

        public getMembershipChangeAssetRss assets(String value) {
            return set("assets", value);
        }

        public getMembershipChangeAssetRss start(String value) {
            return set("start", value);
        }

        public getMembershipChangeAssetRss end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getMembershipChangeAssetValue")
        public static class getMembershipChangeAssetValue extends CommandRef {
            public static final getMembershipChangeAssetValue cmd = new getMembershipChangeAssetValue();
        public getMembershipChangeAssetValue reasons(String value) {
            return set("reasons", value);
        }

        public getMembershipChangeAssetValue assets(String value) {
            return set("assets", value);
        }

        public getMembershipChangeAssetValue start(String value) {
            return set("start", value);
        }

        public getMembershipChangeAssetValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getMembershipChangesByReason")
        public static class getMembershipChangesByReason extends CommandRef {
            public static final getMembershipChangesByReason cmd = new getMembershipChangesByReason();
        public getMembershipChangesByReason reasons(String value) {
            return set("reasons", value);
        }

        public getMembershipChangesByReason start(String value) {
            return set("start", value);
        }

        public getMembershipChangesByReason end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getMetricAt")
        public static class getMetricAt extends CommandRef {
            public static final getMetricAt cmd = new getMetricAt();
        public getMetricAt metric(String value) {
            return set("metric", value);
        }

        public getMetricAt date(String value) {
            return set("date", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getMetricsGraph")
        public static class getMetricsGraph extends CommandRef {
            public static final getMetricsGraph cmd = new getMetricsGraph();
        public getMetricsGraph metrics(String value) {
            return set("metrics", value);
        }

        public getMetricsGraph start(String value) {
            return set("start", value);
        }

        public getMetricsGraph end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getMilitarizationGraph")
        public static class getMilitarizationGraph extends CommandRef {
            public static final getMilitarizationGraph cmd = new getMilitarizationGraph();
        public getMilitarizationGraph start(String value) {
            return set("start", value);
        }

        public getMilitarizationGraph end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getNetAsset")
        public static class getNetAsset extends CommandRef {
            public static final getNetAsset cmd = new getNetAsset();
        public getNetAsset asset(String value) {
            return set("asset", value);
        }

        public getNetAsset start(String value) {
            return set("start", value);
        }

        public getNetAsset end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getNetAssetValue")
        public static class getNetAssetValue extends CommandRef {
            public static final getNetAssetValue cmd = new getNetAssetValue();
        public getNetAssetValue asset(String value) {
            return set("asset", value);
        }

        public getNetAssetValue start(String value) {
            return set("start", value);
        }

        public getNetAssetValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getNetMembersAcquired")
        public static class getNetMembersAcquired extends CommandRef {
            public static final getNetMembersAcquired cmd = new getNetMembersAcquired();
        public getNetMembersAcquired start(String value) {
            return set("start", value);
        }

        public getNetMembersAcquired end(String value) {
            return set("end", value);
        }

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getSheetUrl")
        public static class getSheetUrl extends CommandRef {
            public static final getSheetUrl cmd = new getSheetUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getSpending")
        public static class getSpending extends CommandRef {
            public static final getSpending cmd = new getSpending();
        public getSpending assets(String value) {
            return set("assets", value);
        }

        public getSpending start(String value) {
            return set("start", value);
        }

        public getSpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getSpendingValue")
        public static class getSpendingValue extends CommandRef {
            public static final getSpendingValue cmd = new getSpendingValue();
        public getSpendingValue assets(String value) {
            return set("assets", value);
        }

        public getSpendingValue start(String value) {
            return set("start", value);
        }

        public getSpendingValue end(String value) {
            return set("end", value);
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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getWebUrl")
        public static class getWebUrl extends CommandRef {
            public static final getWebUrl cmd = new getWebUrl();

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
