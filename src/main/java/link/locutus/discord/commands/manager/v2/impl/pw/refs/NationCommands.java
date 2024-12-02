package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class NationCommands {
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="allianceSeniority")
        public static class allianceSeniority extends CommandRef {
            public static final allianceSeniority cmd = new allianceSeniority();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="allianceSeniorityApplicant")
        public static class allianceSeniorityApplicant extends CommandRef {
            public static final allianceSeniorityApplicant cmd = new allianceSeniorityApplicant();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="allianceSeniorityApplicantMs")
        public static class allianceSeniorityApplicantMs extends CommandRef {
            public static final allianceSeniorityApplicantMs cmd = new allianceSeniorityApplicantMs();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="allianceSeniorityMs")
        public static class allianceSeniorityMs extends CommandRef {
            public static final allianceSeniorityMs cmd = new allianceSeniorityMs();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="allianceSeniorityNoneMs")
        public static class allianceSeniorityNoneMs extends CommandRef {
            public static final allianceSeniorityNoneMs cmd = new allianceSeniorityNoneMs();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="attritionBountyValue")
        public static class attritionBountyValue extends CommandRef {
            public static final attritionBountyValue cmd = new attritionBountyValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="avg_daily_login")
        public static class avg_daily_login extends CommandRef {
            public static final avg_daily_login cmd = new avg_daily_login();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="avg_daily_login_turns")
        public static class avg_daily_login_turns extends CommandRef {
            public static final avg_daily_login_turns cmd = new avg_daily_login_turns();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="avg_daily_login_week")
        public static class avg_daily_login_week extends CommandRef {
            public static final avg_daily_login_week cmd = new avg_daily_login_week();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="buildingValue")
        public static class buildingValue extends CommandRef {
            public static final buildingValue cmd = new buildingValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="canBeDeclaredOnByScore")
        public static class canBeDeclaredOnByScore extends CommandRef {
            public static final canBeDeclaredOnByScore cmd = new canBeDeclaredOnByScore();
        public canBeDeclaredOnByScore score(String value) {
            return set("score", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="canBeSpiedByScore")
        public static class canBeSpiedByScore extends CommandRef {
            public static final canBeSpiedByScore cmd = new canBeSpiedByScore();
        public canBeSpiedByScore score(String value) {
            return set("score", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="canBuildProject")
        public static class canBuildProject extends CommandRef {
            public static final canBuildProject cmd = new canBuildProject();
        public canBuildProject project(String value) {
            return set("project", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="canDeclareOnScore")
        public static class canDeclareOnScore extends CommandRef {
            public static final canDeclareOnScore cmd = new canDeclareOnScore();
        public canDeclareOnScore score(String value) {
            return set("score", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="canSpyOnScore")
        public static class canSpyOnScore extends CommandRef {
            public static final canSpyOnScore cmd = new canSpyOnScore();
        public canSpyOnScore score(String value) {
            return set("score", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="cellLookup")
        public static class cellLookup extends CommandRef {
            public static final cellLookup cmd = new cellLookup();
        public cellLookup sheet(String value) {
            return set("sheet", value);
        }

        public cellLookup tabName(String value) {
            return set("tabName", value);
        }

        public cellLookup columnSearch(String value) {
            return set("columnSearch", value);
        }

        public cellLookup columnOutput(String value) {
            return set("columnOutput", value);
        }

        public cellLookup search(String value) {
            return set("search", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="city")
        public static class city extends CommandRef {
            public static final city cmd = new city();
        public city index(String value) {
            return set("index", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="cityUrl")
        public static class cityUrl extends CommandRef {
            public static final cityUrl cmd = new cityUrl();
        public cityUrl index(String value) {
            return set("index", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="cityValue")
        public static class cityValue extends CommandRef {
            public static final cityValue cmd = new cityValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="correctAllianceMMR")
        public static class correctAllianceMMR extends CommandRef {
            public static final correctAllianceMMR cmd = new correctAllianceMMR();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="countWars")
        public static class countWars extends CommandRef {
            public static final countWars cmd = new countWars();
        public countWars warFilter(String value) {
            return set("warFilter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSince3ConsecutiveLogins")
        public static class daysSince3ConsecutiveLogins extends CommandRef {
            public static final daysSince3ConsecutiveLogins cmd = new daysSince3ConsecutiveLogins();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSince4ConsecutiveLogins")
        public static class daysSince4ConsecutiveLogins extends CommandRef {
            public static final daysSince4ConsecutiveLogins cmd = new daysSince4ConsecutiveLogins();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSince5ConsecutiveLogins")
        public static class daysSince5ConsecutiveLogins extends CommandRef {
            public static final daysSince5ConsecutiveLogins cmd = new daysSince5ConsecutiveLogins();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSince6ConsecutiveLogins")
        public static class daysSince6ConsecutiveLogins extends CommandRef {
            public static final daysSince6ConsecutiveLogins cmd = new daysSince6ConsecutiveLogins();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSince7ConsecutiveLogins")
        public static class daysSince7ConsecutiveLogins extends CommandRef {
            public static final daysSince7ConsecutiveLogins cmd = new daysSince7ConsecutiveLogins();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceConsecutiveLogins")
        public static class daysSinceConsecutiveLogins extends CommandRef {
            public static final daysSinceConsecutiveLogins cmd = new daysSinceConsecutiveLogins();
        public daysSinceConsecutiveLogins checkPastXDays(String value) {
            return set("checkPastXDays", value);
        }

        public daysSinceConsecutiveLogins sequentialDays(String value) {
            return set("sequentialDays", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastAircraftBuy")
        public static class daysSinceLastAircraftBuy extends CommandRef {
            public static final daysSinceLastAircraftBuy cmd = new daysSinceLastAircraftBuy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastBankDeposit")
        public static class daysSinceLastBankDeposit extends CommandRef {
            public static final daysSinceLastBankDeposit cmd = new daysSinceLastBankDeposit();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastDefensiveWarLoss")
        public static class daysSinceLastDefensiveWarLoss extends CommandRef {
            public static final daysSinceLastDefensiveWarLoss cmd = new daysSinceLastDefensiveWarLoss();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastMissileBuy")
        public static class daysSinceLastMissileBuy extends CommandRef {
            public static final daysSinceLastMissileBuy cmd = new daysSinceLastMissileBuy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastNukeBuy")
        public static class daysSinceLastNukeBuy extends CommandRef {
            public static final daysSinceLastNukeBuy cmd = new daysSinceLastNukeBuy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastOffensive")
        public static class daysSinceLastOffensive extends CommandRef {
            public static final daysSinceLastOffensive cmd = new daysSinceLastOffensive();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastSelfWithdrawal")
        public static class daysSinceLastSelfWithdrawal extends CommandRef {
            public static final daysSinceLastSelfWithdrawal cmd = new daysSinceLastSelfWithdrawal();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastShipBuy")
        public static class daysSinceLastShipBuy extends CommandRef {
            public static final daysSinceLastShipBuy cmd = new daysSinceLastShipBuy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastSoldierBuy")
        public static class daysSinceLastSoldierBuy extends CommandRef {
            public static final daysSinceLastSoldierBuy cmd = new daysSinceLastSoldierBuy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastSpyBuy")
        public static class daysSinceLastSpyBuy extends CommandRef {
            public static final daysSinceLastSpyBuy cmd = new daysSinceLastSpyBuy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastTankBuy")
        public static class daysSinceLastTankBuy extends CommandRef {
            public static final daysSinceLastTankBuy cmd = new daysSinceLastTankBuy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastWar")
        public static class daysSinceLastWar extends CommandRef {
            public static final daysSinceLastWar cmd = new daysSinceLastWar();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="equilibriumTaxRate")
        public static class equilibriumTaxRate extends CommandRef {
            public static final equilibriumTaxRate cmd = new equilibriumTaxRate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="estimateGNI")
        public static class estimateGNI extends CommandRef {
            public static final estimateGNI cmd = new estimateGNI();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getActiveWarsWith")
        public static class getActiveWarsWith extends CommandRef {
            public static final getActiveWarsWith cmd = new getActiveWarsWith();
        public getActiveWarsWith filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getActive_m")
        public static class getActive_m extends CommandRef {
            public static final getActive_m cmd = new getActive_m();
        public getActive_m time(String value) {
            return set("time", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAgeDays")
        public static class getAgeDays extends CommandRef {
            public static final getAgeDays cmd = new getAgeDays();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAircraft")
        public static class getAircraft extends CommandRef {
            public static final getAircraft cmd = new getAircraft();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAircraftPct")
        public static class getAircraftPct extends CommandRef {
            public static final getAircraftPct cmd = new getAircraftPct();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllTimeDefensiveWars")
        public static class getAllTimeDefensiveWars extends CommandRef {
            public static final getAllTimeDefensiveWars cmd = new getAllTimeDefensiveWars();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllTimeOffDefWars")
        public static class getAllTimeOffDefWars extends CommandRef {
            public static final getAllTimeOffDefWars cmd = new getAllTimeOffDefWars();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllTimeOffensiveWars")
        public static class getAllTimeOffensiveWars extends CommandRef {
            public static final getAllTimeOffensiveWars cmd = new getAllTimeOffensiveWars();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllTimeWars")
        public static class getAllTimeWars extends CommandRef {
            public static final getAllTimeWars cmd = new getAllTimeWars();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAlliance")
        public static class getAlliance extends CommandRef {
            public static final getAlliance cmd = new getAlliance();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllianceName")
        public static class getAllianceName extends CommandRef {
            public static final getAllianceName cmd = new getAllianceName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAlliancePositionId")
        public static class getAlliancePositionId extends CommandRef {
            public static final getAlliancePositionId cmd = new getAlliancePositionId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllianceRank")
        public static class getAllianceRank extends CommandRef {
            public static final getAllianceRank cmd = new getAllianceRank();
        public getAllianceRank filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllianceUrl")
        public static class getAllianceUrl extends CommandRef {
            public static final getAllianceUrl cmd = new getAllianceUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllianceUrlMarkup")
        public static class getAllianceUrlMarkup extends CommandRef {
            public static final getAllianceUrlMarkup cmd = new getAllianceUrlMarkup();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAlliance_id")
        public static class getAlliance_id extends CommandRef {
            public static final getAlliance_id cmd = new getAlliance_id();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAttacking")
        public static class getAttacking extends CommandRef {
            public static final getAttacking cmd = new getAttacking();
        public getAttacking nations(String value) {
            return set("nations", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAuditResult")
        public static class getAuditResult extends CommandRef {
            public static final getAuditResult cmd = new getAuditResult();
        public getAuditResult audit(String value) {
            return set("audit", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAuditResultString")
        public static class getAuditResultString extends CommandRef {
            public static final getAuditResultString cmd = new getAuditResultString();
        public getAuditResultString audit(String value) {
            return set("audit", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgBarracks")
        public static class getAvgBarracks extends CommandRef {
            public static final getAvgBarracks cmd = new getAvgBarracks();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgBuilding")
        public static class getAvgBuilding extends CommandRef {
            public static final getAvgBuilding cmd = new getAvgBuilding();
        public getAvgBuilding building(String value) {
            return set("building", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgBuildings")
        public static class getAvgBuildings extends CommandRef {
            public static final getAvgBuildings cmd = new getAvgBuildings();
        public getAvgBuildings buildings(String value) {
            return set("buildings", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgDrydocks")
        public static class getAvgDrydocks extends CommandRef {
            public static final getAvgDrydocks cmd = new getAvgDrydocks();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgFactories")
        public static class getAvgFactories extends CommandRef {
            public static final getAvgFactories cmd = new getAvgFactories();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgHangars")
        public static class getAvgHangars extends CommandRef {
            public static final getAvgHangars cmd = new getAvgHangars();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgLand")
        public static class getAvgLand extends CommandRef {
            public static final getAvgLand cmd = new getAvgLand();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvg_infra")
        public static class getAvg_infra extends CommandRef {
            public static final getAvg_infra cmd = new getAvg_infra();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBeigeAbsoluteTurn")
        public static class getBeigeAbsoluteTurn extends CommandRef {
            public static final getBeigeAbsoluteTurn cmd = new getBeigeAbsoluteTurn();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBeigeLootTotal")
        public static class getBeigeLootTotal extends CommandRef {
            public static final getBeigeLootTotal cmd = new getBeigeLootTotal();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBeigeTurns")
        public static class getBeigeTurns extends CommandRef {
            public static final getBeigeTurns cmd = new getBeigeTurns();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBlockadedBy")
        public static class getBlockadedBy extends CommandRef {
            public static final getBlockadedBy cmd = new getBlockadedBy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBlockading")
        public static class getBlockading extends CommandRef {
            public static final getBlockading cmd = new getBlockading();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBountySums")
        public static class getBountySums extends CommandRef {
            public static final getBountySums cmd = new getBountySums();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBuildings")
        public static class getBuildings extends CommandRef {
            public static final getBuildings cmd = new getBuildings();
        public getBuildings buildings(String value) {
            return set("buildings", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBuyInfraCost")
        public static class getBuyInfraCost extends CommandRef {
            public static final getBuyInfraCost cmd = new getBuyInfraCost();
        public getBuyInfraCost toInfra(String value) {
            return set("toInfra", value);
        }

        public getBuyInfraCost forceUrbanization(String value) {
            return set("forceUrbanization", value);
        }

        public getBuyInfraCost forceAEC(String value) {
            return set("forceAEC", value);
        }

        public getBuyInfraCost forceCFCE(String value) {
            return set("forceCFCE", value);
        }

        public getBuyInfraCost forceGSA(String value) {
            return set("forceGSA", value);
        }

        public getBuyInfraCost forceBDA(String value) {
            return set("forceBDA", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBuyLandCost")
        public static class getBuyLandCost extends CommandRef {
            public static final getBuyLandCost cmd = new getBuyLandCost();
        public getBuyLandCost toLand(String value) {
            return set("toLand", value);
        }

        public getBuyLandCost forceRAPolicy(String value) {
            return set("forceRAPolicy", value);
        }

        public getBuyLandCost forceAEC(String value) {
            return set("forceAEC", value);
        }

        public getBuyLandCost forceALA(String value) {
            return set("forceALA", value);
        }

        public getBuyLandCost forceGSA(String value) {
            return set("forceGSA", value);
        }

        public getBuyLandCost forceBDA(String value) {
            return set("forceBDA", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCities")
        public static class getCities extends CommandRef {
            public static final getCities cmd = new getCities();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCitiesAt")
        public static class getCitiesAt extends CommandRef {
            public static final getCitiesAt cmd = new getCitiesAt();
        public getCitiesAt time(String value) {
            return set("time", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCitiesSince")
        public static class getCitiesSince extends CommandRef {
            public static final getCitiesSince cmd = new getCitiesSince();
        public getCitiesSince time(String value) {
            return set("time", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityAvg")
        public static class getCityAvg extends CommandRef {
            public static final getCityAvg cmd = new getCityAvg();
        public getCityAvg attribute(String value) {
            return set("attribute", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityCostPerCitySince")
        public static class getCityCostPerCitySince extends CommandRef {
            public static final getCityCostPerCitySince cmd = new getCityCostPerCitySince();
        public getCityCostPerCitySince time(String value) {
            return set("time", value);
        }

        public getCityCostPerCitySince allowProjects(String value) {
            return set("allowProjects", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityCostSince")
        public static class getCityCostSince extends CommandRef {
            public static final getCityCostSince cmd = new getCityCostSince();
        public getCityCostSince time(String value) {
            return set("time", value);
        }

        public getCityCostSince allowProjects(String value) {
            return set("allowProjects", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityGroup")
        public static class getCityGroup extends CommandRef {
            public static final getCityGroup cmd = new getCityGroup();
        public getCityGroup ranges(String value) {
            return set("ranges", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityMax")
        public static class getCityMax extends CommandRef {
            public static final getCityMax cmd = new getCityMax();
        public getCityMax attribute(String value) {
            return set("attribute", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityMin")
        public static class getCityMin extends CommandRef {
            public static final getCityMin cmd = new getCityMin();
        public getCityMin attribute(String value) {
            return set("attribute", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityTimerAbsoluteTurn")
        public static class getCityTimerAbsoluteTurn extends CommandRef {
            public static final getCityTimerAbsoluteTurn cmd = new getCityTimerAbsoluteTurn();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityTotal")
        public static class getCityTotal extends CommandRef {
            public static final getCityTotal cmd = new getCityTotal();
        public getCityTotal attribute(String value) {
            return set("attribute", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityTurns")
        public static class getCityTurns extends CommandRef {
            public static final getCityTurns cmd = new getCityTurns();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getColor")
        public static class getColor extends CommandRef {
            public static final getColor cmd = new getColor();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getColorAbsoluteTurn")
        public static class getColorAbsoluteTurn extends CommandRef {
            public static final getColorAbsoluteTurn cmd = new getColorAbsoluteTurn();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getColorTurns")
        public static class getColorTurns extends CommandRef {
            public static final getColorTurns cmd = new getColorTurns();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getContinent")
        public static class getContinent extends CommandRef {
            public static final getContinent cmd = new getContinent();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDate")
        public static class getDate extends CommandRef {
            public static final getDate cmd = new getDate();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDaysSinceLastCity")
        public static class getDaysSinceLastCity extends CommandRef {
            public static final getDaysSinceLastCity cmd = new getDaysSinceLastCity();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDaysSinceLastSpyReport")
        public static class getDaysSinceLastSpyReport extends CommandRef {
            public static final getDaysSinceLastSpyReport cmd = new getDaysSinceLastSpyReport();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDc_turn")
        public static class getDc_turn extends CommandRef {
            public static final getDc_turn cmd = new getDc_turn();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDef")
        public static class getDef extends CommandRef {
            public static final getDef cmd = new getDef();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDefending")
        public static class getDefending extends CommandRef {
            public static final getDefending cmd = new getDefending();
        public getDefending nations(String value) {
            return set("nations", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDepositValuePerCity")
        public static class getDepositValuePerCity extends CommandRef {
            public static final getDepositValuePerCity cmd = new getDepositValuePerCity();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDeposits")
        public static class getDeposits extends CommandRef {
            public static final getDeposits cmd = new getDeposits();
        public getDeposits start(String value) {
            return set("start", value);
        }

        public getDeposits end(String value) {
            return set("end", value);
        }

        public getDeposits filter(String value) {
            return set("filter", value);
        }

        public getDeposits ignoreBaseTaxrate(String value) {
            return set("ignoreBaseTaxrate", value);
        }

        public getDeposits ignoreOffsets(String value) {
            return set("ignoreOffsets", value);
        }

        public getDeposits includeExpired(String value) {
            return set("includeExpired", value);
        }

        public getDeposits includeIgnored(String value) {
            return set("includeIgnored", value);
        }

        public getDeposits excludeTypes(String value) {
            return set("excludeTypes", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDiscordString")
        public static class getDiscordString extends CommandRef {
            public static final getDiscordString cmd = new getDiscordString();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDiscordUser")
        public static class getDiscordUser extends CommandRef {
            public static final getDiscordUser cmd = new getDiscordUser();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDomesticPolicy")
        public static class getDomesticPolicy extends CommandRef {
            public static final getDomesticPolicy cmd = new getDomesticPolicy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDomesticPolicyAbsoluteTurn")
        public static class getDomesticPolicyAbsoluteTurn extends CommandRef {
            public static final getDomesticPolicyAbsoluteTurn cmd = new getDomesticPolicyAbsoluteTurn();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDomesticPolicyTurns")
        public static class getDomesticPolicyTurns extends CommandRef {
            public static final getDomesticPolicyTurns cmd = new getDomesticPolicyTurns();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getEnemies")
        public static class getEnemies extends CommandRef {
            public static final getEnemies cmd = new getEnemies();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getEnemyStrength")
        public static class getEnemyStrength extends CommandRef {
            public static final getEnemyStrength cmd = new getEnemyStrength();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getEntered_vm")
        public static class getEntered_vm extends CommandRef {
            public static final getEntered_vm cmd = new getEntered_vm();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getEspionageFullTurn")
        public static class getEspionageFullTurn extends CommandRef {
            public static final getEspionageFullTurn cmd = new getEspionageFullTurn();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getFighting")
        public static class getFighting extends CommandRef {
            public static final getFighting cmd = new getFighting();
        public getFighting nations(String value) {
            return set("nations", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getFlagUrl")
        public static class getFlagUrl extends CommandRef {
            public static final getFlagUrl cmd = new getFlagUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getFreeBuildings")
        public static class getFreeBuildings extends CommandRef {
            public static final getFreeBuildings cmd = new getFreeBuildings();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getFreeOffSpyOps")
        public static class getFreeOffSpyOps extends CommandRef {
            public static final getFreeOffSpyOps cmd = new getFreeOffSpyOps();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getFreeOffensiveSlots")
        public static class getFreeOffensiveSlots extends CommandRef {
            public static final getFreeOffensiveSlots cmd = new getFreeOffensiveSlots();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getFreeProjectSlots")
        public static class getFreeProjectSlots extends CommandRef {
            public static final getFreeProjectSlots cmd = new getFreeProjectSlots();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getGNI")
        public static class getGNI extends CommandRef {
            public static final getGNI cmd = new getGNI();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getGroundStrength")
        public static class getGroundStrength extends CommandRef {
            public static final getGroundStrength cmd = new getGroundStrength();
        public getGroundStrength munitions(String value) {
            return set("munitions", value);
        }

        public getGroundStrength enemyAc(String value) {
            return set("enemyAc", value);
        }

        public getGroundStrength includeRebuy(String value) {
            return set("includeRebuy", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getInfra")
        public static class getInfra extends CommandRef {
            public static final getInfra cmd = new getInfra();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getLastUnitBuy")
        public static class getLastUnitBuy extends CommandRef {
            public static final getLastUnitBuy cmd = new getLastUnitBuy();
        public getLastUnitBuy unit(String value) {
            return set("unit", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getLatestUid")
        public static class getLatestUid extends CommandRef {
            public static final getLatestUid cmd = new getLatestUid();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getLeader")
        public static class getLeader extends CommandRef {
            public static final getLeader cmd = new getLeader();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getLeaving_vm")
        public static class getLeaving_vm extends CommandRef {
            public static final getLeaving_vm cmd = new getLeaving_vm();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getLootRevenueTotal")
        public static class getLootRevenueTotal extends CommandRef {
            public static final getLootRevenueTotal cmd = new getLootRevenueTotal();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMMR")
        public static class getMMR extends CommandRef {
            public static final getMMR cmd = new getMMR();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMMRBuildingDecimal")
        public static class getMMRBuildingDecimal extends CommandRef {
            public static final getMMRBuildingDecimal cmd = new getMMRBuildingDecimal();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMMRBuildingStr")
        public static class getMMRBuildingStr extends CommandRef {
            public static final getMMRBuildingStr cmd = new getMMRBuildingStr();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMarkdownUrl")
        public static class getMarkdownUrl extends CommandRef {
            public static final getMarkdownUrl cmd = new getMarkdownUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMaxOff")
        public static class getMaxOff extends CommandRef {
            public static final getMaxOff cmd = new getMaxOff();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMissiles")
        public static class getMissiles extends CommandRef {
            public static final getMissiles cmd = new getMissiles();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMoneyLooted")
        public static class getMoneyLooted extends CommandRef {
            public static final getMoneyLooted cmd = new getMoneyLooted();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getName")
        public static class getName extends CommandRef {
            public static final getName cmd = new getName();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNation")
        public static class getNation extends CommandRef {
            public static final getNation cmd = new getNation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNation_id")
        public static class getNation_id extends CommandRef {
            public static final getNation_id cmd = new getNation_id();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNations")
        public static class getNations extends CommandRef {
            public static final getNations cmd = new getNations();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNetDepositsConverted")
        public static class getNetDepositsConverted extends CommandRef {
            public static final getNetDepositsConverted cmd = new getNetDepositsConverted();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNukes")
        public static class getNukes extends CommandRef {
            public static final getNukes cmd = new getNukes();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumDefWarsSince")
        public static class getNumDefWarsSince extends CommandRef {
            public static final getNumDefWarsSince cmd = new getNumDefWarsSince();
        public getNumDefWarsSince date(String value) {
            return set("date", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumOffWarsSince")
        public static class getNumOffWarsSince extends CommandRef {
            public static final getNumOffWarsSince cmd = new getNumOffWarsSince();
        public getNumOffWarsSince date(String value) {
            return set("date", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumProjects")
        public static class getNumProjects extends CommandRef {
            public static final getNumProjects cmd = new getNumProjects();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumReports")
        public static class getNumReports extends CommandRef {
            public static final getNumReports cmd = new getNumReports();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumWars")
        public static class getNumWars extends CommandRef {
            public static final getNumWars cmd = new getNumWars();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumWarsAgainstActives")
        public static class getNumWarsAgainstActives extends CommandRef {
            public static final getNumWarsAgainstActives cmd = new getNumWarsAgainstActives();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumWarsSince")
        public static class getNumWarsSince extends CommandRef {
            public static final getNumWarsSince cmd = new getNumWarsSince();
        public getNumWarsSince date(String value) {
            return set("date", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getOff")
        public static class getOff extends CommandRef {
            public static final getOff cmd = new getOff();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getOffSpySlots")
        public static class getOffSpySlots extends CommandRef {
            public static final getOffSpySlots cmd = new getOffSpySlots();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getOnlineStatus")
        public static class getOnlineStatus extends CommandRef {
            public static final getOnlineStatus cmd = new getOnlineStatus();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getPopulation")
        public static class getPopulation extends CommandRef {
            public static final getPopulation cmd = new getPopulation();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getPosition")
        public static class getPosition extends CommandRef {
            public static final getPosition cmd = new getPosition();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getPositionEnum")
        public static class getPositionEnum extends CommandRef {
            public static final getPositionEnum cmd = new getPositionEnum();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getPositionLevel")
        public static class getPositionLevel extends CommandRef {
            public static final getPositionLevel cmd = new getPositionLevel();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getProjectAbsoluteTurn")
        public static class getProjectAbsoluteTurn extends CommandRef {
            public static final getProjectAbsoluteTurn cmd = new getProjectAbsoluteTurn();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getProjectBitMask")
        public static class getProjectBitMask extends CommandRef {
            public static final getProjectBitMask cmd = new getProjectBitMask();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getProjectTurns")
        public static class getProjectTurns extends CommandRef {
            public static final getProjectTurns cmd = new getProjectTurns();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getProjects")
        public static class getProjects extends CommandRef {
            public static final getProjects cmd = new getProjects();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getRads")
        public static class getRads extends CommandRef {
            public static final getRads cmd = new getRads();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getRelativeStrength")
        public static class getRelativeStrength extends CommandRef {
            public static final getRelativeStrength cmd = new getRelativeStrength();

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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getRevenueConverted")
        public static class getRevenueConverted extends CommandRef {
            public static final getRevenueConverted cmd = new getRevenueConverted();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getRevenuePerCityConverted")
        public static class getRevenuePerCityConverted extends CommandRef {
            public static final getRevenuePerCityConverted cmd = new getRevenuePerCityConverted();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getScore")
        public static class getScore extends CommandRef {
            public static final getScore cmd = new getScore();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getShipPct")
        public static class getShipPct extends CommandRef {
            public static final getShipPct cmd = new getShipPct();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getShips")
        public static class getShips extends CommandRef {
            public static final getShips cmd = new getShips();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getSoldierPct")
        public static class getSoldierPct extends CommandRef {
            public static final getSoldierPct cmd = new getSoldierPct();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getSoldiers")
        public static class getSoldiers extends CommandRef {
            public static final getSoldiers cmd = new getSoldiers();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getSpies")
        public static class getSpies extends CommandRef {
            public static final getSpies cmd = new getSpies();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getSpyCap")
        public static class getSpyCap extends CommandRef {
            public static final getSpyCap cmd = new getSpyCap();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getSpyCapLeft")
        public static class getSpyCapLeft extends CommandRef {
            public static final getSpyCapLeft cmd = new getSpyCapLeft();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getSpyReportsToday")
        public static class getSpyReportsToday extends CommandRef {
            public static final getSpyReportsToday cmd = new getSpyReportsToday();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStockpile")
        public static class getStockpile extends CommandRef {
            public static final getStockpile cmd = new getStockpile();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStrength")
        public static class getStrength extends CommandRef {
            public static final getStrength cmd = new getStrength();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStrengthMMR")
        public static class getStrengthMMR extends CommandRef {
            public static final getStrengthMMR cmd = new getStrengthMMR();
        public getStrengthMMR mmr(String value) {
            return set("mmr", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStrongestEnemy")
        public static class getStrongestEnemy extends CommandRef {
            public static final getStrongestEnemy cmd = new getStrongestEnemy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStrongestEnemyOfScore")
        public static class getStrongestEnemyOfScore extends CommandRef {
            public static final getStrongestEnemyOfScore cmd = new getStrongestEnemyOfScore();
        public getStrongestEnemyOfScore minScore(String value) {
            return set("minScore", value);
        }

        public getStrongestEnemyOfScore maxScore(String value) {
            return set("maxScore", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStrongestEnemyRelative")
        public static class getStrongestEnemyRelative extends CommandRef {
            public static final getStrongestEnemyRelative cmd = new getStrongestEnemyRelative();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStrongestOffEnemyOfScore")
        public static class getStrongestOffEnemyOfScore extends CommandRef {
            public static final getStrongestOffEnemyOfScore cmd = new getStrongestOffEnemyOfScore();
        public getStrongestOffEnemyOfScore minScore(String value) {
            return set("minScore", value);
        }

        public getStrongestOffEnemyOfScore maxScore(String value) {
            return set("maxScore", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTankPct")
        public static class getTankPct extends CommandRef {
            public static final getTankPct cmd = new getTankPct();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTanks")
        public static class getTanks extends CommandRef {
            public static final getTanks cmd = new getTanks();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTax_id")
        public static class getTax_id extends CommandRef {
            public static final getTax_id cmd = new getTax_id();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTotalLand")
        public static class getTotalLand extends CommandRef {
            public static final getTotalLand cmd = new getTotalLand();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTradeAvgPpu")
        public static class getTradeAvgPpu extends CommandRef {
            public static final getTradeAvgPpu cmd = new getTradeAvgPpu();
        public getTradeAvgPpu dateStart(String value) {
            return set("dateStart", value);
        }

        public getTradeAvgPpu dateEnd(String value) {
            return set("dateEnd", value);
        }

        public getTradeAvgPpu types(String value) {
            return set("types", value);
        }

        public getTradeAvgPpu filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTradeQuantity")
        public static class getTradeQuantity extends CommandRef {
            public static final getTradeQuantity cmd = new getTradeQuantity();
        public getTradeQuantity dateStart(String value) {
            return set("dateStart", value);
        }

        public getTradeQuantity dateEnd(String value) {
            return set("dateEnd", value);
        }

        public getTradeQuantity types(String value) {
            return set("types", value);
        }

        public getTradeQuantity filter(String value) {
            return set("filter", value);
        }

        public getTradeQuantity net(String value) {
            return set("net", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTradeValue")
        public static class getTradeValue extends CommandRef {
            public static final getTradeValue cmd = new getTradeValue();
        public getTradeValue dateStart(String value) {
            return set("dateStart", value);
        }

        public getTradeValue dateEnd(String value) {
            return set("dateEnd", value);
        }

        public getTradeValue types(String value) {
            return set("types", value);
        }

        public getTradeValue filter(String value) {
            return set("filter", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTreasureBonusPct")
        public static class getTreasureBonusPct extends CommandRef {
            public static final getTreasureBonusPct cmd = new getTreasureBonusPct();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTurnsFromDC")
        public static class getTurnsFromDC extends CommandRef {
            public static final getTurnsFromDC cmd = new getTurnsFromDC();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTurnsTillDC")
        public static class getTurnsTillDC extends CommandRef {
            public static final getTurnsTillDC cmd = new getTurnsTillDC();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUnits")
        public static class getUnits extends CommandRef {
            public static final getUnits cmd = new getUnits();
        public getUnits unit(String value) {
            return set("unit", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUnitsAt")
        public static class getUnitsAt extends CommandRef {
            public static final getUnitsAt cmd = new getUnitsAt();
        public getUnitsAt unit(String value) {
            return set("unit", value);
        }

        public getUnitsAt date(String value) {
            return set("date", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUpdateTZ")
        public static class getUpdateTZ extends CommandRef {
            public static final getUpdateTZ cmd = new getUpdateTZ();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUrl")
        public static class getUrl extends CommandRef {
            public static final getUrl cmd = new getUrl();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUserAgeDays")
        public static class getUserAgeDays extends CommandRef {
            public static final getUserAgeDays cmd = new getUserAgeDays();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUserAgeMs")
        public static class getUserAgeMs extends CommandRef {
            public static final getUserAgeMs cmd = new getUserAgeMs();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUserDiscriminator")
        public static class getUserDiscriminator extends CommandRef {
            public static final getUserDiscriminator cmd = new getUserDiscriminator();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUserId")
        public static class getUserId extends CommandRef {
            public static final getUserId cmd = new getUserId();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUserMention")
        public static class getUserMention extends CommandRef {
            public static final getUserMention cmd = new getUserMention();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getVacationTurnsElapsed")
        public static class getVacationTurnsElapsed extends CommandRef {
            public static final getVacationTurnsElapsed cmd = new getVacationTurnsElapsed();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getVm_turns")
        public static class getVm_turns extends CommandRef {
            public static final getVm_turns cmd = new getVm_turns();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getWarPolicy")
        public static class getWarPolicy extends CommandRef {
            public static final getWarPolicy cmd = new getWarPolicy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getWarPolicyAbsoluteTurn")
        public static class getWarPolicyAbsoluteTurn extends CommandRef {
            public static final getWarPolicyAbsoluteTurn cmd = new getWarPolicyAbsoluteTurn();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getWarPolicyTurns")
        public static class getWarPolicyTurns extends CommandRef {
            public static final getWarPolicyTurns cmd = new getWarPolicyTurns();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getWars_lost")
        public static class getWars_lost extends CommandRef {
            public static final getWars_lost cmd = new getWars_lost();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getWars_won")
        public static class getWars_won extends CommandRef {
            public static final getWars_won cmd = new getWars_won();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasAllPermission")
        public static class hasAllPermission extends CommandRef {
            public static final hasAllPermission cmd = new hasAllPermission();
        public hasAllPermission permissions(String value) {
            return set("permissions", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasAnyPermission")
        public static class hasAnyPermission extends CommandRef {
            public static final hasAnyPermission cmd = new hasAnyPermission();
        public hasAnyPermission permissions(String value) {
            return set("permissions", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtAircraftToday")
        public static class hasBoughtAircraftToday extends CommandRef {
            public static final hasBoughtAircraftToday cmd = new hasBoughtAircraftToday();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtMissileToday")
        public static class hasBoughtMissileToday extends CommandRef {
            public static final hasBoughtMissileToday cmd = new hasBoughtMissileToday();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtNukeToday")
        public static class hasBoughtNukeToday extends CommandRef {
            public static final hasBoughtNukeToday cmd = new hasBoughtNukeToday();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtShipsToday")
        public static class hasBoughtShipsToday extends CommandRef {
            public static final hasBoughtShipsToday cmd = new hasBoughtShipsToday();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtSoldiersToday")
        public static class hasBoughtSoldiersToday extends CommandRef {
            public static final hasBoughtSoldiersToday cmd = new hasBoughtSoldiersToday();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtSpiesToday")
        public static class hasBoughtSpiesToday extends CommandRef {
            public static final hasBoughtSpiesToday cmd = new hasBoughtSpiesToday();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtTanksToday")
        public static class hasBoughtTanksToday extends CommandRef {
            public static final hasBoughtTanksToday cmd = new hasBoughtTanksToday();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBounty")
        public static class hasBounty extends CommandRef {
            public static final hasBounty cmd = new hasBounty();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasNukeBounty")
        public static class hasNukeBounty extends CommandRef {
            public static final hasNukeBounty cmd = new hasNukeBounty();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasPermission")
        public static class hasPermission extends CommandRef {
            public static final hasPermission cmd = new hasPermission();
        public hasPermission permission(String value) {
            return set("permission", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasPriorBan")
        public static class hasPriorBan extends CommandRef {
            public static final hasPriorBan cmd = new hasPriorBan();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasProject")
        public static class hasProject extends CommandRef {
            public static final hasProject cmd = new hasProject();
        public hasProject project(String value) {
            return set("project", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasProjects")
        public static class hasProjects extends CommandRef {
            public static final hasProjects cmd = new hasProjects();
        public hasProjects projects(String value) {
            return set("projects", value);
        }

        public hasProjects any(String value) {
            return set("any", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasTreasure")
        public static class hasTreasure extends CommandRef {
            public static final hasTreasure cmd = new hasTreasure();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasUnitBuyToday")
        public static class hasUnitBuyToday extends CommandRef {
            public static final hasUnitBuyToday cmd = new hasUnitBuyToday();
        public hasUnitBuyToday unit(String value) {
            return set("unit", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasUnsetMil")
        public static class hasUnsetMil extends CommandRef {
            public static final hasUnsetMil cmd = new hasUnsetMil();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasWarBounty")
        public static class hasWarBounty extends CommandRef {
            public static final hasWarBounty cmd = new hasWarBounty();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="inactivity_streak")
        public static class inactivity_streak extends CommandRef {
            public static final inactivity_streak cmd = new inactivity_streak();
        public inactivity_streak daysInactive(String value) {
            return set("daysInactive", value);
        }

        public inactivity_streak checkPastXDays(String value) {
            return set("checkPastXDays", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="infraAttackModifier")
        public static class infraAttackModifier extends CommandRef {
            public static final infraAttackModifier cmd = new infraAttackModifier();
        public infraAttackModifier type(String value) {
            return set("type", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="infraDefendModifier")
        public static class infraDefendModifier extends CommandRef {
            public static final infraDefendModifier cmd = new infraDefendModifier();
        public infraDefendModifier type(String value) {
            return set("type", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="infraValue")
        public static class infraValue extends CommandRef {
            public static final infraValue cmd = new infraValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isAllianceColor")
        public static class isAllianceColor extends CommandRef {
            public static final isAllianceColor cmd = new isAllianceColor();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isAttackingEnemyOfCities")
        public static class isAttackingEnemyOfCities extends CommandRef {
            public static final isAttackingEnemyOfCities cmd = new isAttackingEnemyOfCities();
        public isAttackingEnemyOfCities minCities(String value) {
            return set("minCities", value);
        }

        public isAttackingEnemyOfCities maxCities(String value) {
            return set("maxCities", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isAttackingEnemyOfScore")
        public static class isAttackingEnemyOfScore extends CommandRef {
            public static final isAttackingEnemyOfScore cmd = new isAttackingEnemyOfScore();
        public isAttackingEnemyOfScore minScore(String value) {
            return set("minScore", value);
        }

        public isAttackingEnemyOfScore maxScore(String value) {
            return set("maxScore", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isBanEvading")
        public static class isBanEvading extends CommandRef {
            public static final isBanEvading cmd = new isBanEvading();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isBeige")
        public static class isBeige extends CommandRef {
            public static final isBeige cmd = new isBeige();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isBlitzkrieg")
        public static class isBlitzkrieg extends CommandRef {
            public static final isBlitzkrieg cmd = new isBlitzkrieg();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isBlockaded")
        public static class isBlockaded extends CommandRef {
            public static final isBlockaded cmd = new isBlockaded();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isBlockader")
        public static class isBlockader extends CommandRef {
            public static final isBlockader cmd = new isBlockader();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isDefendingEnemyOfCities")
        public static class isDefendingEnemyOfCities extends CommandRef {
            public static final isDefendingEnemyOfCities cmd = new isDefendingEnemyOfCities();
        public isDefendingEnemyOfCities minCities(String value) {
            return set("minCities", value);
        }

        public isDefendingEnemyOfCities maxCities(String value) {
            return set("maxCities", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isEnemy")
        public static class isEnemy extends CommandRef {
            public static final isEnemy cmd = new isEnemy();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isEspionageAvailable")
        public static class isEspionageAvailable extends CommandRef {
            public static final isEspionageAvailable cmd = new isEspionageAvailable();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isEspionageFull")
        public static class isEspionageFull extends CommandRef {
            public static final isEspionageFull cmd = new isEspionageFull();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isFightingActive")
        public static class isFightingActive extends CommandRef {
            public static final isFightingActive cmd = new isFightingActive();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isFightingEnemyOfCities")
        public static class isFightingEnemyOfCities extends CommandRef {
            public static final isFightingEnemyOfCities cmd = new isFightingEnemyOfCities();
        public isFightingEnemyOfCities minCities(String value) {
            return set("minCities", value);
        }

        public isFightingEnemyOfCities maxCities(String value) {
            return set("maxCities", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isFightingEnemyOfScore")
        public static class isFightingEnemyOfScore extends CommandRef {
            public static final isFightingEnemyOfScore cmd = new isFightingEnemyOfScore();
        public isFightingEnemyOfScore minScore(String value) {
            return set("minScore", value);
        }

        public isFightingEnemyOfScore maxScore(String value) {
            return set("maxScore", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isGray")
        public static class isGray extends CommandRef {
            public static final isGray cmd = new isGray();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isIn")
        public static class isIn extends CommandRef {
            public static final isIn cmd = new isIn();
        public isIn nations(String value) {
            return set("nations", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isInAllianceGuild")
        public static class isInAllianceGuild extends CommandRef {
            public static final isInAllianceGuild cmd = new isInAllianceGuild();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isInMilcomGuild")
        public static class isInMilcomGuild extends CommandRef {
            public static final isInMilcomGuild cmd = new isInMilcomGuild();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isInSpyRange")
        public static class isInSpyRange extends CommandRef {
            public static final isInSpyRange cmd = new isInSpyRange();
        public isInSpyRange other(String value) {
            return set("other", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isInWarRange")
        public static class isInWarRange extends CommandRef {
            public static final isInWarRange cmd = new isInWarRange();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isOnline")
        public static class isOnline extends CommandRef {
            public static final isOnline cmd = new isOnline();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isPowered")
        public static class isPowered extends CommandRef {
            public static final isPowered cmd = new isPowered();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isReroll")
        public static class isReroll extends CommandRef {
            public static final isReroll cmd = new isReroll();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isTaxable")
        public static class isTaxable extends CommandRef {
            public static final isTaxable cmd = new isTaxable();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isVerified")
        public static class isVerified extends CommandRef {
            public static final isVerified cmd = new isVerified();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="landValue")
        public static class landValue extends CommandRef {
            public static final landValue cmd = new landValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="lastActiveMs")
        public static class lastActiveMs extends CommandRef {
            public static final lastActiveMs cmd = new lastActiveMs();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="lastBankDeposit")
        public static class lastBankDeposit extends CommandRef {
            public static final lastBankDeposit cmd = new lastBankDeposit();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="lastSelfWithdrawal")
        public static class lastSelfWithdrawal extends CommandRef {
            public static final lastSelfWithdrawal cmd = new lastSelfWithdrawal();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="login_daychange")
        public static class login_daychange extends CommandRef {
            public static final login_daychange cmd = new login_daychange();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="lootModifier")
        public static class lootModifier extends CommandRef {
            public static final lootModifier cmd = new lootModifier();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="lootTotal")
        public static class lootTotal extends CommandRef {
            public static final lootTotal cmd = new lootTotal();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="looterModifier")
        public static class looterModifier extends CommandRef {
            public static final looterModifier cmd = new looterModifier();
        public looterModifier isGround(String value) {
            return set("isGround", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="lostInactiveWar")
        public static class lostInactiveWar extends CommandRef {
            public static final lostInactiveWar cmd = new lostInactiveWar();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="maxBountyValue")
        public static class maxBountyValue extends CommandRef {
            public static final maxBountyValue cmd = new maxBountyValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="maxCityInfra")
        public static class maxCityInfra extends CommandRef {
            public static final maxCityInfra cmd = new maxCityInfra();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="maxCityLand")
        public static class maxCityLand extends CommandRef {
            public static final maxCityLand cmd = new maxCityLand();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="maxWarBountyValue")
        public static class maxWarBountyValue extends CommandRef {
            public static final maxWarBountyValue cmd = new maxWarBountyValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="militaryValue")
        public static class militaryValue extends CommandRef {
            public static final militaryValue cmd = new militaryValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="minWarResistance")
        public static class minWarResistance extends CommandRef {
            public static final minWarResistance cmd = new minWarResistance();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="minWarResistancePlusMap")
        public static class minWarResistancePlusMap extends CommandRef {
            public static final minWarResistancePlusMap cmd = new minWarResistancePlusMap();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="nukeBountyValue")
        public static class nukeBountyValue extends CommandRef {
            public static final nukeBountyValue cmd = new nukeBountyValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="ordinaryBountyValue")
        public static class ordinaryBountyValue extends CommandRef {
            public static final ordinaryBountyValue cmd = new ordinaryBountyValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="passesAudit")
        public static class passesAudit extends CommandRef {
            public static final passesAudit cmd = new passesAudit();
        public passesAudit audit(String value) {
            return set("audit", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="projectSlots")
        public static class projectSlots extends CommandRef {
            public static final projectSlots cmd = new projectSlots();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="projectValue")
        public static class projectValue extends CommandRef {
            public static final projectValue cmd = new projectValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="raidBountyValue")
        public static class raidBountyValue extends CommandRef {
            public static final raidBountyValue cmd = new raidBountyValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="revenue")
        public static class revenue extends CommandRef {
            public static final revenue cmd = new revenue();
        public revenue turns(String value) {
            return set("turns", value);
        }

        public revenue no_cities(String value) {
            return set("no_cities", value);
        }

        public revenue no_military(String value) {
            return set("no_military", value);
        }

        public revenue no_trade_bonus(String value) {
            return set("no_trade_bonus", value);
        }

        public revenue no_new_bonus(String value) {
            return set("no_new_bonus", value);
        }

        public revenue no_food(String value) {
            return set("no_food", value);
        }

        public revenue no_power(String value) {
            return set("no_power", value);
        }

        public revenue treasure_bonus(String value) {
            return set("treasure_bonus", value);
        }

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="totalBountyValue")
        public static class totalBountyValue extends CommandRef {
            public static final totalBountyValue cmd = new totalBountyValue();

        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="treasureDays")
        public static class treasureDays extends CommandRef {
            public static final treasureDays cmd = new treasureDays();

        }

}
