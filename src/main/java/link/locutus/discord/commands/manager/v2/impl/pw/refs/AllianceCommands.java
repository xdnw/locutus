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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getCitySpending")
        public static class getCitySpending extends CommandRef {
            public static final getCitySpending cmd = new getCitySpending();
        public getCitySpending start(String value) {
            return set("start", value);
        }

        public getCitySpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getCitySpendingValue")
        public static class getCitySpendingValue extends CommandRef {
            public static final getCitySpendingValue cmd = new getCitySpendingValue();
        public getCitySpendingValue start(String value) {
            return set("start", value);
        }

        public getCitySpendingValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getColor")
        public static class getColor extends CommandRef {
            public static final getColor cmd = new getColor();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getDeleted")
        public static class getDeleted extends CommandRef {
            public static final getDeleted cmd = new getDeleted();
        public getDeleted start(String value) {
            return set("start", value);
        }

        public getDeleted end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getDeletedAssetCount")
        public static class getDeletedAssetCount extends CommandRef {
            public static final getDeletedAssetCount cmd = new getDeletedAssetCount();
        public getDeletedAssetCount assets(String value) {
            return set("assets", value);
        }

        public getDeletedAssetCount start(String value) {
            return set("start", value);
        }

        public getDeletedAssetCount end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getDeletedCost")
        public static class getDeletedCost extends CommandRef {
            public static final getDeletedCost cmd = new getDeletedCost();
        public getDeletedCost assets(String value) {
            return set("assets", value);
        }

        public getDeletedCost start(String value) {
            return set("start", value);
        }

        public getDeletedCost end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getDeletedCostValue")
        public static class getDeletedCostValue extends CommandRef {
            public static final getDeletedCostValue cmd = new getDeletedCostValue();
        public getDeletedCostValue assets(String value) {
            return set("assets", value);
        }

        public getDeletedCostValue start(String value) {
            return set("start", value);
        }

        public getDeletedCostValue end(String value) {
            return set("end", value);
        }

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveCitySpending")
        public static class getEffectiveCitySpending extends CommandRef {
            public static final getEffectiveCitySpending cmd = new getEffectiveCitySpending();
        public getEffectiveCitySpending start(String value) {
            return set("start", value);
        }

        public getEffectiveCitySpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveCitySpendingValue")
        public static class getEffectiveCitySpendingValue extends CommandRef {
            public static final getEffectiveCitySpendingValue cmd = new getEffectiveCitySpendingValue();
        public getEffectiveCitySpendingValue start(String value) {
            return set("start", value);
        }

        public getEffectiveCitySpendingValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveGrowthSpending")
        public static class getEffectiveGrowthSpending extends CommandRef {
            public static final getEffectiveGrowthSpending cmd = new getEffectiveGrowthSpending();
        public getEffectiveGrowthSpending start(String value) {
            return set("start", value);
        }

        public getEffectiveGrowthSpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveGrowthSpendingValue")
        public static class getEffectiveGrowthSpendingValue extends CommandRef {
            public static final getEffectiveGrowthSpendingValue cmd = new getEffectiveGrowthSpendingValue();
        public getEffectiveGrowthSpendingValue start(String value) {
            return set("start", value);
        }

        public getEffectiveGrowthSpendingValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveInfraSpending")
        public static class getEffectiveInfraSpending extends CommandRef {
            public static final getEffectiveInfraSpending cmd = new getEffectiveInfraSpending();
        public getEffectiveInfraSpending start(String value) {
            return set("start", value);
        }

        public getEffectiveInfraSpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveInfraSpendingValue")
        public static class getEffectiveInfraSpendingValue extends CommandRef {
            public static final getEffectiveInfraSpendingValue cmd = new getEffectiveInfraSpendingValue();
        public getEffectiveInfraSpendingValue start(String value) {
            return set("start", value);
        }

        public getEffectiveInfraSpendingValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveLandSpending")
        public static class getEffectiveLandSpending extends CommandRef {
            public static final getEffectiveLandSpending cmd = new getEffectiveLandSpending();
        public getEffectiveLandSpending start(String value) {
            return set("start", value);
        }

        public getEffectiveLandSpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveLandSpendingValue")
        public static class getEffectiveLandSpendingValue extends CommandRef {
            public static final getEffectiveLandSpendingValue cmd = new getEffectiveLandSpendingValue();
        public getEffectiveLandSpendingValue start(String value) {
            return set("start", value);
        }

        public getEffectiveLandSpendingValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveProjectSpending")
        public static class getEffectiveProjectSpending extends CommandRef {
            public static final getEffectiveProjectSpending cmd = new getEffectiveProjectSpending();
        public getEffectiveProjectSpending start(String value) {
            return set("start", value);
        }

        public getEffectiveProjectSpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getEffectiveProjectSpendingValue")
        public static class getEffectiveProjectSpendingValue extends CommandRef {
            public static final getEffectiveProjectSpendingValue cmd = new getEffectiveProjectSpendingValue();
        public getEffectiveProjectSpendingValue start(String value) {
            return set("start", value);
        }

        public getEffectiveProjectSpendingValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getFlag")
        public static class getFlag extends CommandRef {
            public static final getFlag cmd = new getFlag();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getForum_link")
        public static class getForum_link extends CommandRef {
            public static final getForum_link cmd = new getForum_link();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getGrowthSpending")
        public static class getGrowthSpending extends CommandRef {
            public static final getGrowthSpending cmd = new getGrowthSpending();
        public getGrowthSpending start(String value) {
            return set("start", value);
        }

        public getGrowthSpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getGrowthSpendingValue")
        public static class getGrowthSpendingValue extends CommandRef {
            public static final getGrowthSpendingValue cmd = new getGrowthSpendingValue();
        public getGrowthSpendingValue start(String value) {
            return set("start", value);
        }

        public getGrowthSpendingValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getInfraSpending")
        public static class getInfraSpending extends CommandRef {
            public static final getInfraSpending cmd = new getInfraSpending();
        public getInfraSpending start(String value) {
            return set("start", value);
        }

        public getInfraSpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getInfraSpendingValue")
        public static class getInfraSpendingValue extends CommandRef {
            public static final getInfraSpendingValue cmd = new getInfraSpendingValue();
        public getInfraSpendingValue start(String value) {
            return set("start", value);
        }

        public getInfraSpendingValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getJoins")
        public static class getJoins extends CommandRef {
            public static final getJoins cmd = new getJoins();
        public getJoins start(String value) {
            return set("start", value);
        }

        public getJoins end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getJoinsAssetCount")
        public static class getJoinsAssetCount extends CommandRef {
            public static final getJoinsAssetCount cmd = new getJoinsAssetCount();
        public getJoinsAssetCount assets(String value) {
            return set("assets", value);
        }

        public getJoinsAssetCount start(String value) {
            return set("start", value);
        }

        public getJoinsAssetCount end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getJoinsCost")
        public static class getJoinsCost extends CommandRef {
            public static final getJoinsCost cmd = new getJoinsCost();
        public getJoinsCost assets(String value) {
            return set("assets", value);
        }

        public getJoinsCost start(String value) {
            return set("start", value);
        }

        public getJoinsCost end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getJoinsCostValue")
        public static class getJoinsCostValue extends CommandRef {
            public static final getJoinsCostValue cmd = new getJoinsCostValue();
        public getJoinsCostValue assets(String value) {
            return set("assets", value);
        }

        public getJoinsCostValue start(String value) {
            return set("start", value);
        }

        public getJoinsCostValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getLandSpending")
        public static class getLandSpending extends CommandRef {
            public static final getLandSpending cmd = new getLandSpending();
        public getLandSpending start(String value) {
            return set("start", value);
        }

        public getLandSpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getLandSpendingValue")
        public static class getLandSpendingValue extends CommandRef {
            public static final getLandSpendingValue cmd = new getLandSpendingValue();
        public getLandSpendingValue start(String value) {
            return set("start", value);
        }

        public getLandSpendingValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getLeft")
        public static class getLeft extends CommandRef {
            public static final getLeft cmd = new getLeft();
        public getLeft start(String value) {
            return set("start", value);
        }

        public getLeft end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getLeftAssetCount")
        public static class getLeftAssetCount extends CommandRef {
            public static final getLeftAssetCount cmd = new getLeftAssetCount();
        public getLeftAssetCount assets(String value) {
            return set("assets", value);
        }

        public getLeftAssetCount start(String value) {
            return set("start", value);
        }

        public getLeftAssetCount end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getLeftCost")
        public static class getLeftCost extends CommandRef {
            public static final getLeftCost cmd = new getLeftCost();
        public getLeftCost assets(String value) {
            return set("assets", value);
        }

        public getLeftCost start(String value) {
            return set("start", value);
        }

        public getLeftCost end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getLeftCostValue")
        public static class getLeftCostValue extends CommandRef {
            public static final getLeftCostValue cmd = new getLeftCostValue();
        public getLeftCostValue assets(String value) {
            return set("assets", value);
        }

        public getLeftCostValue start(String value) {
            return set("start", value);
        }

        public getLeftCostValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getMarkdownUrl")
        public static class getMarkdownUrl extends CommandRef {
            public static final getMarkdownUrl cmd = new getMarkdownUrl();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getNetAssetChange")
        public static class getNetAssetChange extends CommandRef {
            public static final getNetAssetChange cmd = new getNetAssetChange();
        public getNetAssetChange asset(String value) {
            return set("asset", value);
        }

        public getNetAssetChange start(String value) {
            return set("start", value);
        }

        public getNetAssetChange end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getNetAssetValueChange")
        public static class getNetAssetValueChange extends CommandRef {
            public static final getNetAssetValueChange cmd = new getNetAssetValueChange();
        public getNetAssetValueChange asset(String value) {
            return set("asset", value);
        }

        public getNetAssetValueChange start(String value) {
            return set("start", value);
        }

        public getNetAssetValueChange end(String value) {
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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getPoached")
        public static class getPoached extends CommandRef {
            public static final getPoached cmd = new getPoached();
        public getPoached start(String value) {
            return set("start", value);
        }

        public getPoached end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getPoachedAssetCount")
        public static class getPoachedAssetCount extends CommandRef {
            public static final getPoachedAssetCount cmd = new getPoachedAssetCount();
        public getPoachedAssetCount assets(String value) {
            return set("assets", value);
        }

        public getPoachedAssetCount start(String value) {
            return set("start", value);
        }

        public getPoachedAssetCount end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getPoachedCost")
        public static class getPoachedCost extends CommandRef {
            public static final getPoachedCost cmd = new getPoachedCost();
        public getPoachedCost assets(String value) {
            return set("assets", value);
        }

        public getPoachedCost start(String value) {
            return set("start", value);
        }

        public getPoachedCost end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getPoachedCostValue")
        public static class getPoachedCostValue extends CommandRef {
            public static final getPoachedCostValue cmd = new getPoachedCostValue();
        public getPoachedCostValue assets(String value) {
            return set("assets", value);
        }

        public getPoachedCostValue start(String value) {
            return set("start", value);
        }

        public getPoachedCostValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getProjectSpending")
        public static class getProjectSpending extends CommandRef {
            public static final getProjectSpending cmd = new getProjectSpending();
        public getProjectSpending start(String value) {
            return set("start", value);
        }

        public getProjectSpending end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getProjectSpendingValue")
        public static class getProjectSpendingValue extends CommandRef {
            public static final getProjectSpendingValue cmd = new getProjectSpendingValue();
        public getProjectSpendingValue start(String value) {
            return set("start", value);
        }

        public getProjectSpendingValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getRank")
        public static class getRank extends CommandRef {
            public static final getRank cmd = new getRank();
        public getRank filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getRecruited")
        public static class getRecruited extends CommandRef {
            public static final getRecruited cmd = new getRecruited();
        public getRecruited start(String value) {
            return set("start", value);
        }

        public getRecruited end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getRecruitedAssetCount")
        public static class getRecruitedAssetCount extends CommandRef {
            public static final getRecruitedAssetCount cmd = new getRecruitedAssetCount();
        public getRecruitedAssetCount assets(String value) {
            return set("assets", value);
        }

        public getRecruitedAssetCount start(String value) {
            return set("start", value);
        }

        public getRecruitedAssetCount end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getRecruitedCost")
        public static class getRecruitedCost extends CommandRef {
            public static final getRecruitedCost cmd = new getRecruitedCost();
        public getRecruitedCost assets(String value) {
            return set("assets", value);
        }

        public getRecruitedCost start(String value) {
            return set("start", value);
        }

        public getRecruitedCost end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getRecruitedCostValue")
        public static class getRecruitedCostValue extends CommandRef {
            public static final getRecruitedCostValue cmd = new getRecruitedCostValue();
        public getRecruitedCostValue assets(String value) {
            return set("assets", value);
        }

        public getRecruitedCostValue start(String value) {
            return set("start", value);
        }

        public getRecruitedCostValue end(String value) {
            return set("end", value);
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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getVmEnded")
        public static class getVmEnded extends CommandRef {
            public static final getVmEnded cmd = new getVmEnded();
        public getVmEnded start(String value) {
            return set("start", value);
        }

        public getVmEnded end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getVm_EndedAssetCount")
        public static class getVm_EndedAssetCount extends CommandRef {
            public static final getVm_EndedAssetCount cmd = new getVm_EndedAssetCount();
        public getVm_EndedAssetCount assets(String value) {
            return set("assets", value);
        }

        public getVm_EndedAssetCount start(String value) {
            return set("start", value);
        }

        public getVm_EndedAssetCount end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getVm_EndedCost")
        public static class getVm_EndedCost extends CommandRef {
            public static final getVm_EndedCost cmd = new getVm_EndedCost();
        public getVm_EndedCost assets(String value) {
            return set("assets", value);
        }

        public getVm_EndedCost start(String value) {
            return set("start", value);
        }

        public getVm_EndedCost end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getVm_EndedCostValue")
        public static class getVm_EndedCostValue extends CommandRef {
            public static final getVm_EndedCostValue cmd = new getVm_EndedCostValue();
        public getVm_EndedCostValue assets(String value) {
            return set("assets", value);
        }

        public getVm_EndedCostValue start(String value) {
            return set("start", value);
        }

        public getVm_EndedCostValue end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getVm_Entered")
        public static class getVm_Entered extends CommandRef {
            public static final getVm_Entered cmd = new getVm_Entered();
        public getVm_Entered start(String value) {
            return set("start", value);
        }

        public getVm_Entered end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getVm_EnteredAssetCount")
        public static class getVm_EnteredAssetCount extends CommandRef {
            public static final getVm_EnteredAssetCount cmd = new getVm_EnteredAssetCount();
        public getVm_EnteredAssetCount assets(String value) {
            return set("assets", value);
        }

        public getVm_EnteredAssetCount start(String value) {
            return set("start", value);
        }

        public getVm_EnteredAssetCount end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getVm_EnteredCost")
        public static class getVm_EnteredCost extends CommandRef {
            public static final getVm_EnteredCost cmd = new getVm_EnteredCost();
        public getVm_EnteredCost assets(String value) {
            return set("assets", value);
        }

        public getVm_EnteredCost start(String value) {
            return set("start", value);
        }

        public getVm_EnteredCost end(String value) {
            return set("end", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBAlliance.class,method="getVm_EnteredCostValue")
        public static class getVm_EnteredCostValue extends CommandRef {
            public static final getVm_EnteredCostValue cmd = new getVm_EnteredCostValue();
        public getVm_EnteredCostValue assets(String value) {
            return set("assets", value);
        }

        public getVm_EnteredCostValue start(String value) {
            return set("start", value);
        }

        public getVm_EnteredCostValue end(String value) {
            return set("end", value);
        }

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
