package link.locutus.discord.commands.manager.v2.impl.pw.refs;
import link.locutus.discord.commands.manager.v2.command.AutoRegister;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
public class NationCommands {
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="allianceSeniority")
        public static class allianceSeniority extends CommandRef {
            public static final allianceSeniority cmd = new allianceSeniority();
            public allianceSeniority create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="allianceSeniorityMs")
        public static class allianceSeniorityMs extends CommandRef {
            public static final allianceSeniorityMs cmd = new allianceSeniorityMs();
            public allianceSeniorityMs create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="attritionBountyValue")
        public static class attritionBountyValue extends CommandRef {
            public static final attritionBountyValue cmd = new attritionBountyValue();
            public attritionBountyValue create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="avg_daily_login")
        public static class avg_daily_login extends CommandRef {
            public static final avg_daily_login cmd = new avg_daily_login();
            public avg_daily_login create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="avg_daily_login_turns")
        public static class avg_daily_login_turns extends CommandRef {
            public static final avg_daily_login_turns cmd = new avg_daily_login_turns();
            public avg_daily_login_turns create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="avg_daily_login_week")
        public static class avg_daily_login_week extends CommandRef {
            public static final avg_daily_login_week cmd = new avg_daily_login_week();
            public avg_daily_login_week create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="canBeDeclaredOnByScore")
        public static class canBeDeclaredOnByScore extends CommandRef {
            public static final canBeDeclaredOnByScore cmd = new canBeDeclaredOnByScore();
            public canBeDeclaredOnByScore create(String score) {
                return createArgs("score", score);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="canBeSpiedByScore")
        public static class canBeSpiedByScore extends CommandRef {
            public static final canBeSpiedByScore cmd = new canBeSpiedByScore();
            public canBeSpiedByScore create(String score) {
                return createArgs("score", score);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="canDeclareOnScore")
        public static class canDeclareOnScore extends CommandRef {
            public static final canDeclareOnScore cmd = new canDeclareOnScore();
            public canDeclareOnScore create(String score) {
                return createArgs("score", score);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="canSpyOnScore")
        public static class canSpyOnScore extends CommandRef {
            public static final canSpyOnScore cmd = new canSpyOnScore();
            public canSpyOnScore create(String score) {
                return createArgs("score", score);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="cellLookup")
        public static class cellLookup extends CommandRef {
            public static final cellLookup cmd = new cellLookup();
            public cellLookup create(String sheet, String tabName, String columnSearch, String columnOutput, String search) {
                return createArgs("sheet", sheet, "tabName", tabName, "columnSearch", columnSearch, "columnOutput", columnOutput, "search", search);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="city")
        public static class city extends CommandRef {
            public static final city cmd = new city();
            public city create(String index) {
                return createArgs("index", index);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="cityUrl")
        public static class cityUrl extends CommandRef {
            public static final cityUrl cmd = new cityUrl();
            public cityUrl create(String index) {
                return createArgs("index", index);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="correctAllianceMMR")
        public static class correctAllianceMMR extends CommandRef {
            public static final correctAllianceMMR cmd = new correctAllianceMMR();
            public correctAllianceMMR create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="countWars")
        public static class countWars extends CommandRef {
            public static final countWars cmd = new countWars();
            public countWars create(String warFilter) {
                return createArgs("warFilter", warFilter);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSince3ConsecutiveLogins")
        public static class daysSince3ConsecutiveLogins extends CommandRef {
            public static final daysSince3ConsecutiveLogins cmd = new daysSince3ConsecutiveLogins();
            public daysSince3ConsecutiveLogins create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSince4ConsecutiveLogins")
        public static class daysSince4ConsecutiveLogins extends CommandRef {
            public static final daysSince4ConsecutiveLogins cmd = new daysSince4ConsecutiveLogins();
            public daysSince4ConsecutiveLogins create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSince5ConsecutiveLogins")
        public static class daysSince5ConsecutiveLogins extends CommandRef {
            public static final daysSince5ConsecutiveLogins cmd = new daysSince5ConsecutiveLogins();
            public daysSince5ConsecutiveLogins create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSince6ConsecutiveLogins")
        public static class daysSince6ConsecutiveLogins extends CommandRef {
            public static final daysSince6ConsecutiveLogins cmd = new daysSince6ConsecutiveLogins();
            public daysSince6ConsecutiveLogins create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSince7ConsecutiveLogins")
        public static class daysSince7ConsecutiveLogins extends CommandRef {
            public static final daysSince7ConsecutiveLogins cmd = new daysSince7ConsecutiveLogins();
            public daysSince7ConsecutiveLogins create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceConsecutiveLogins")
        public static class daysSinceConsecutiveLogins extends CommandRef {
            public static final daysSinceConsecutiveLogins cmd = new daysSinceConsecutiveLogins();
            public daysSinceConsecutiveLogins create(String checkPastXDays, String sequentialDays) {
                return createArgs("checkPastXDays", checkPastXDays, "sequentialDays", sequentialDays);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastAircraftBuy")
        public static class daysSinceLastAircraftBuy extends CommandRef {
            public static final daysSinceLastAircraftBuy cmd = new daysSinceLastAircraftBuy();
            public daysSinceLastAircraftBuy create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastBankDeposit")
        public static class daysSinceLastBankDeposit extends CommandRef {
            public static final daysSinceLastBankDeposit cmd = new daysSinceLastBankDeposit();
            public daysSinceLastBankDeposit create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastDefensiveWarLoss")
        public static class daysSinceLastDefensiveWarLoss extends CommandRef {
            public static final daysSinceLastDefensiveWarLoss cmd = new daysSinceLastDefensiveWarLoss();
            public daysSinceLastDefensiveWarLoss create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastOffensive")
        public static class daysSinceLastOffensive extends CommandRef {
            public static final daysSinceLastOffensive cmd = new daysSinceLastOffensive();
            public daysSinceLastOffensive create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastShipBuy")
        public static class daysSinceLastShipBuy extends CommandRef {
            public static final daysSinceLastShipBuy cmd = new daysSinceLastShipBuy();
            public daysSinceLastShipBuy create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastSoldierBuy")
        public static class daysSinceLastSoldierBuy extends CommandRef {
            public static final daysSinceLastSoldierBuy cmd = new daysSinceLastSoldierBuy();
            public daysSinceLastSoldierBuy create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastSpyBuy")
        public static class daysSinceLastSpyBuy extends CommandRef {
            public static final daysSinceLastSpyBuy cmd = new daysSinceLastSpyBuy();
            public daysSinceLastSpyBuy create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastTankBuy")
        public static class daysSinceLastTankBuy extends CommandRef {
            public static final daysSinceLastTankBuy cmd = new daysSinceLastTankBuy();
            public daysSinceLastTankBuy create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="daysSinceLastWar")
        public static class daysSinceLastWar extends CommandRef {
            public static final daysSinceLastWar cmd = new daysSinceLastWar();
            public daysSinceLastWar create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="equilibriumTaxRate")
        public static class equilibriumTaxRate extends CommandRef {
            public static final equilibriumTaxRate cmd = new equilibriumTaxRate();
            public equilibriumTaxRate create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="estimateGNI")
        public static class estimateGNI extends CommandRef {
            public static final estimateGNI cmd = new estimateGNI();
            public estimateGNI create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getActive_m")
        public static class getActive_m extends CommandRef {
            public static final getActive_m cmd = new getActive_m();
            public getActive_m create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAgeDays")
        public static class getAgeDays extends CommandRef {
            public static final getAgeDays cmd = new getAgeDays();
            public getAgeDays create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAircraft")
        public static class getAircraft extends CommandRef {
            public static final getAircraft cmd = new getAircraft();
            public getAircraft create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAircraftPct")
        public static class getAircraftPct extends CommandRef {
            public static final getAircraftPct cmd = new getAircraftPct();
            public getAircraftPct create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllTimeDefensiveWars")
        public static class getAllTimeDefensiveWars extends CommandRef {
            public static final getAllTimeDefensiveWars cmd = new getAllTimeDefensiveWars();
            public getAllTimeDefensiveWars create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllTimeOffDefWars")
        public static class getAllTimeOffDefWars extends CommandRef {
            public static final getAllTimeOffDefWars cmd = new getAllTimeOffDefWars();
            public getAllTimeOffDefWars create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllTimeOffensiveWars")
        public static class getAllTimeOffensiveWars extends CommandRef {
            public static final getAllTimeOffensiveWars cmd = new getAllTimeOffensiveWars();
            public getAllTimeOffensiveWars create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllTimeWars")
        public static class getAllTimeWars extends CommandRef {
            public static final getAllTimeWars cmd = new getAllTimeWars();
            public getAllTimeWars create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllianceDepositValue")
        public static class getAllianceDepositValue extends CommandRef {
            public static final getAllianceDepositValue cmd = new getAllianceDepositValue();
            public getAllianceDepositValue create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllianceDepositValuePerCity")
        public static class getAllianceDepositValuePerCity extends CommandRef {
            public static final getAllianceDepositValuePerCity cmd = new getAllianceDepositValuePerCity();
            public getAllianceDepositValuePerCity create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllianceName")
        public static class getAllianceName extends CommandRef {
            public static final getAllianceName cmd = new getAllianceName();
            public getAllianceName create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAlliancePositionId")
        public static class getAlliancePositionId extends CommandRef {
            public static final getAlliancePositionId cmd = new getAlliancePositionId();
            public getAlliancePositionId create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllianceRank")
        public static class getAllianceRank extends CommandRef {
            public static final getAllianceRank cmd = new getAllianceRank();
            public getAllianceRank create(String filter) {
                return createArgs("filter", filter);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAllianceUrl")
        public static class getAllianceUrl extends CommandRef {
            public static final getAllianceUrl cmd = new getAllianceUrl();
            public getAllianceUrl create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAlliance_id")
        public static class getAlliance_id extends CommandRef {
            public static final getAlliance_id cmd = new getAlliance_id();
            public getAlliance_id create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAttacking")
        public static class getAttacking extends CommandRef {
            public static final getAttacking cmd = new getAttacking();
            public getAttacking create(String nations) {
                return createArgs("nations", nations);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgBarracks")
        public static class getAvgBarracks extends CommandRef {
            public static final getAvgBarracks cmd = new getAvgBarracks();
            public getAvgBarracks create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgBuilding")
        public static class getAvgBuilding extends CommandRef {
            public static final getAvgBuilding cmd = new getAvgBuilding();
            public getAvgBuilding create(String building) {
                return createArgs("building", building);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgBuildings")
        public static class getAvgBuildings extends CommandRef {
            public static final getAvgBuildings cmd = new getAvgBuildings();
            public getAvgBuildings create(String buildings) {
                return createArgs("buildings", buildings);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgDrydocks")
        public static class getAvgDrydocks extends CommandRef {
            public static final getAvgDrydocks cmd = new getAvgDrydocks();
            public getAvgDrydocks create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgFactories")
        public static class getAvgFactories extends CommandRef {
            public static final getAvgFactories cmd = new getAvgFactories();
            public getAvgFactories create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgHangars")
        public static class getAvgHangars extends CommandRef {
            public static final getAvgHangars cmd = new getAvgHangars();
            public getAvgHangars create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvgLand")
        public static class getAvgLand extends CommandRef {
            public static final getAvgLand cmd = new getAvgLand();
            public getAvgLand create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getAvg_infra")
        public static class getAvg_infra extends CommandRef {
            public static final getAvg_infra cmd = new getAvg_infra();
            public getAvg_infra create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBeigeAbsoluteTurn")
        public static class getBeigeAbsoluteTurn extends CommandRef {
            public static final getBeigeAbsoluteTurn cmd = new getBeigeAbsoluteTurn();
            public getBeigeAbsoluteTurn create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBeigeLootTotal")
        public static class getBeigeLootTotal extends CommandRef {
            public static final getBeigeLootTotal cmd = new getBeigeLootTotal();
            public getBeigeLootTotal create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBeigeTurns")
        public static class getBeigeTurns extends CommandRef {
            public static final getBeigeTurns cmd = new getBeigeTurns();
            public getBeigeTurns create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBlockadedBy")
        public static class getBlockadedBy extends CommandRef {
            public static final getBlockadedBy cmd = new getBlockadedBy();
            public getBlockadedBy create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBlockading")
        public static class getBlockading extends CommandRef {
            public static final getBlockading cmd = new getBlockading();
            public getBlockading create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBountySums")
        public static class getBountySums extends CommandRef {
            public static final getBountySums cmd = new getBountySums();
            public getBountySums create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getBuildings")
        public static class getBuildings extends CommandRef {
            public static final getBuildings cmd = new getBuildings();
            public getBuildings create(String buildings) {
                return createArgs("buildings", buildings);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCities")
        public static class getCities extends CommandRef {
            public static final getCities cmd = new getCities();
            public getCities create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCitiesAt")
        public static class getCitiesAt extends CommandRef {
            public static final getCitiesAt cmd = new getCitiesAt();
            public getCitiesAt create(String time) {
                return createArgs("time", time);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCitiesSince")
        public static class getCitiesSince extends CommandRef {
            public static final getCitiesSince cmd = new getCitiesSince();
            public getCitiesSince create(String time) {
                return createArgs("time", time);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityCostPerCitySince")
        public static class getCityCostPerCitySince extends CommandRef {
            public static final getCityCostPerCitySince cmd = new getCityCostPerCitySince();
            public getCityCostPerCitySince create(String time, String allowProjects) {
                return createArgs("time", time, "allowProjects", allowProjects);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityCostSince")
        public static class getCityCostSince extends CommandRef {
            public static final getCityCostSince cmd = new getCityCostSince();
            public getCityCostSince create(String time, String allowProjects) {
                return createArgs("time", time, "allowProjects", allowProjects);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityGroup")
        public static class getCityGroup extends CommandRef {
            public static final getCityGroup cmd = new getCityGroup();
            public getCityGroup create(String ranges) {
                return createArgs("ranges", ranges);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityTimerAbsoluteTurn")
        public static class getCityTimerAbsoluteTurn extends CommandRef {
            public static final getCityTimerAbsoluteTurn cmd = new getCityTimerAbsoluteTurn();
            public getCityTimerAbsoluteTurn create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getCityTurns")
        public static class getCityTurns extends CommandRef {
            public static final getCityTurns cmd = new getCityTurns();
            public getCityTurns create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getColor")
        public static class getColor extends CommandRef {
            public static final getColor cmd = new getColor();
            public getColor create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getColorAbsoluteTurn")
        public static class getColorAbsoluteTurn extends CommandRef {
            public static final getColorAbsoluteTurn cmd = new getColorAbsoluteTurn();
            public getColorAbsoluteTurn create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getColorTurns")
        public static class getColorTurns extends CommandRef {
            public static final getColorTurns cmd = new getColorTurns();
            public getColorTurns create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getContinent")
        public static class getContinent extends CommandRef {
            public static final getContinent cmd = new getContinent();
            public getContinent create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDate")
        public static class getDate extends CommandRef {
            public static final getDate cmd = new getDate();
            public getDate create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDaysSinceLastSpyReport")
        public static class getDaysSinceLastSpyReport extends CommandRef {
            public static final getDaysSinceLastSpyReport cmd = new getDaysSinceLastSpyReport();
            public getDaysSinceLastSpyReport create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDc_turn")
        public static class getDc_turn extends CommandRef {
            public static final getDc_turn cmd = new getDc_turn();
            public getDc_turn create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDef")
        public static class getDef extends CommandRef {
            public static final getDef cmd = new getDef();
            public getDef create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDefending")
        public static class getDefending extends CommandRef {
            public static final getDefending cmd = new getDefending();
            public getDefending create(String nations) {
                return createArgs("nations", nations);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDomesticPolicy")
        public static class getDomesticPolicy extends CommandRef {
            public static final getDomesticPolicy cmd = new getDomesticPolicy();
            public getDomesticPolicy create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDomesticPolicyAbsoluteTurn")
        public static class getDomesticPolicyAbsoluteTurn extends CommandRef {
            public static final getDomesticPolicyAbsoluteTurn cmd = new getDomesticPolicyAbsoluteTurn();
            public getDomesticPolicyAbsoluteTurn create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getDomesticPolicyTurns")
        public static class getDomesticPolicyTurns extends CommandRef {
            public static final getDomesticPolicyTurns cmd = new getDomesticPolicyTurns();
            public getDomesticPolicyTurns create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getEnemies")
        public static class getEnemies extends CommandRef {
            public static final getEnemies cmd = new getEnemies();
            public getEnemies create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getEnemyStrength")
        public static class getEnemyStrength extends CommandRef {
            public static final getEnemyStrength cmd = new getEnemyStrength();
            public getEnemyStrength create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getEntered_vm")
        public static class getEntered_vm extends CommandRef {
            public static final getEntered_vm cmd = new getEntered_vm();
            public getEntered_vm create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getEspionageFullTurn")
        public static class getEspionageFullTurn extends CommandRef {
            public static final getEspionageFullTurn cmd = new getEspionageFullTurn();
            public getEspionageFullTurn create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getFighting")
        public static class getFighting extends CommandRef {
            public static final getFighting cmd = new getFighting();
            public getFighting create(String nations) {
                return createArgs("nations", nations);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getFreeBuildings")
        public static class getFreeBuildings extends CommandRef {
            public static final getFreeBuildings cmd = new getFreeBuildings();
            public getFreeBuildings create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getFreeOffensiveSlots")
        public static class getFreeOffensiveSlots extends CommandRef {
            public static final getFreeOffensiveSlots cmd = new getFreeOffensiveSlots();
            public getFreeOffensiveSlots create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getFreeProjectSlots")
        public static class getFreeProjectSlots extends CommandRef {
            public static final getFreeProjectSlots cmd = new getFreeProjectSlots();
            public getFreeProjectSlots create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getGNI")
        public static class getGNI extends CommandRef {
            public static final getGNI cmd = new getGNI();
            public getGNI create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getGroundStrength")
        public static class getGroundStrength extends CommandRef {
            public static final getGroundStrength cmd = new getGroundStrength();
            public getGroundStrength create(String munitions, String enemyAc, String includeRebuy) {
                return createArgs("munitions", munitions, "enemyAc", enemyAc, "includeRebuy", includeRebuy);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getId")
        public static class getId extends CommandRef {
            public static final getId cmd = new getId();
            public getId create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getInfra")
        public static class getInfra extends CommandRef {
            public static final getInfra cmd = new getInfra();
            public getInfra create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getLastUnitBuy")
        public static class getLastUnitBuy extends CommandRef {
            public static final getLastUnitBuy cmd = new getLastUnitBuy();
            public getLastUnitBuy create(String unit) {
                return createArgs("unit", unit);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getLeader")
        public static class getLeader extends CommandRef {
            public static final getLeader cmd = new getLeader();
            public getLeader create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getLeaving_vm")
        public static class getLeaving_vm extends CommandRef {
            public static final getLeaving_vm cmd = new getLeaving_vm();
            public getLeaving_vm create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getLootRevenueTotal")
        public static class getLootRevenueTotal extends CommandRef {
            public static final getLootRevenueTotal cmd = new getLootRevenueTotal();
            public getLootRevenueTotal create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMMR")
        public static class getMMR extends CommandRef {
            public static final getMMR cmd = new getMMR();
            public getMMR create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMMRBuildingArr")
        public static class getMMRBuildingArr extends CommandRef {
            public static final getMMRBuildingArr cmd = new getMMRBuildingArr();
            public getMMRBuildingArr create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMMRBuildingStr")
        public static class getMMRBuildingStr extends CommandRef {
            public static final getMMRBuildingStr cmd = new getMMRBuildingStr();
            public getMMRBuildingStr create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMaxOff")
        public static class getMaxOff extends CommandRef {
            public static final getMaxOff cmd = new getMaxOff();
            public getMaxOff create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMissiles")
        public static class getMissiles extends CommandRef {
            public static final getMissiles cmd = new getMissiles();
            public getMissiles create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getMoneyLooted")
        public static class getMoneyLooted extends CommandRef {
            public static final getMoneyLooted cmd = new getMoneyLooted();
            public getMoneyLooted create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNation")
        public static class getNation extends CommandRef {
            public static final getNation cmd = new getNation();
            public getNation create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNationUrl")
        public static class getNationUrl extends CommandRef {
            public static final getNationUrl cmd = new getNationUrl();
            public getNationUrl create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNation_id")
        public static class getNation_id extends CommandRef {
            public static final getNation_id cmd = new getNation_id();
            public getNation_id create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNations")
        public static class getNations extends CommandRef {
            public static final getNations cmd = new getNations();
            public getNations create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNukes")
        public static class getNukes extends CommandRef {
            public static final getNukes cmd = new getNukes();
            public getNukes create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumDefWarsSince")
        public static class getNumDefWarsSince extends CommandRef {
            public static final getNumDefWarsSince cmd = new getNumDefWarsSince();
            public getNumDefWarsSince create(String date) {
                return createArgs("date", date);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumOffWarsSince")
        public static class getNumOffWarsSince extends CommandRef {
            public static final getNumOffWarsSince cmd = new getNumOffWarsSince();
            public getNumOffWarsSince create(String date) {
                return createArgs("date", date);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumProjects")
        public static class getNumProjects extends CommandRef {
            public static final getNumProjects cmd = new getNumProjects();
            public getNumProjects create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumReports")
        public static class getNumReports extends CommandRef {
            public static final getNumReports cmd = new getNumReports();
            public getNumReports create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumWars")
        public static class getNumWars extends CommandRef {
            public static final getNumWars cmd = new getNumWars();
            public getNumWars create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumWarsAgainstActives")
        public static class getNumWarsAgainstActives extends CommandRef {
            public static final getNumWarsAgainstActives cmd = new getNumWarsAgainstActives();
            public getNumWarsAgainstActives create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getNumWarsSince")
        public static class getNumWarsSince extends CommandRef {
            public static final getNumWarsSince cmd = new getNumWarsSince();
            public getNumWarsSince create(String date) {
                return createArgs("date", date);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getOff")
        public static class getOff extends CommandRef {
            public static final getOff cmd = new getOff();
            public getOff create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getOffSpySlots")
        public static class getOffSpySlots extends CommandRef {
            public static final getOffSpySlots cmd = new getOffSpySlots();
            public getOffSpySlots create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getOnlineStatus")
        public static class getOnlineStatus extends CommandRef {
            public static final getOnlineStatus cmd = new getOnlineStatus();
            public getOnlineStatus create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getPopulation")
        public static class getPopulation extends CommandRef {
            public static final getPopulation cmd = new getPopulation();
            public getPopulation create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getPosition")
        public static class getPosition extends CommandRef {
            public static final getPosition cmd = new getPosition();
            public getPosition create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getPositionEnum")
        public static class getPositionEnum extends CommandRef {
            public static final getPositionEnum cmd = new getPositionEnum();
            public getPositionEnum create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getPositionLevel")
        public static class getPositionLevel extends CommandRef {
            public static final getPositionLevel cmd = new getPositionLevel();
            public getPositionLevel create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getProjectAbsoluteTurn")
        public static class getProjectAbsoluteTurn extends CommandRef {
            public static final getProjectAbsoluteTurn cmd = new getProjectAbsoluteTurn();
            public getProjectAbsoluteTurn create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getProjectBitMask")
        public static class getProjectBitMask extends CommandRef {
            public static final getProjectBitMask cmd = new getProjectBitMask();
            public getProjectBitMask create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getProjectTurns")
        public static class getProjectTurns extends CommandRef {
            public static final getProjectTurns cmd = new getProjectTurns();
            public getProjectTurns create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getProjects")
        public static class getProjects extends CommandRef {
            public static final getProjects cmd = new getProjects();
            public getProjects create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getRads")
        public static class getRads extends CommandRef {
            public static final getRads cmd = new getRads();
            public getRads create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getRelativeStrength")
        public static class getRelativeStrength extends CommandRef {
            public static final getRelativeStrength cmd = new getRelativeStrength();
            public getRelativeStrength create() {
                return createArgs();
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
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getRevenueConverted")
        public static class getRevenueConverted extends CommandRef {
            public static final getRevenueConverted cmd = new getRevenueConverted();
            public getRevenueConverted create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getScore")
        public static class getScore extends CommandRef {
            public static final getScore cmd = new getScore();
            public getScore create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getShipPct")
        public static class getShipPct extends CommandRef {
            public static final getShipPct cmd = new getShipPct();
            public getShipPct create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getShips")
        public static class getShips extends CommandRef {
            public static final getShips cmd = new getShips();
            public getShips create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getSoldierPct")
        public static class getSoldierPct extends CommandRef {
            public static final getSoldierPct cmd = new getSoldierPct();
            public getSoldierPct create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getSoldiers")
        public static class getSoldiers extends CommandRef {
            public static final getSoldiers cmd = new getSoldiers();
            public getSoldiers create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getSpies")
        public static class getSpies extends CommandRef {
            public static final getSpies cmd = new getSpies();
            public getSpies create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getSpyCap")
        public static class getSpyCap extends CommandRef {
            public static final getSpyCap cmd = new getSpyCap();
            public getSpyCap create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getSpyReportsToday")
        public static class getSpyReportsToday extends CommandRef {
            public static final getSpyReportsToday cmd = new getSpyReportsToday();
            public getSpyReportsToday create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStrength")
        public static class getStrength extends CommandRef {
            public static final getStrength cmd = new getStrength();
            public getStrength create(String mmr) {
                return createArgs("mmr", mmr);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStrongestEnemy")
        public static class getStrongestEnemy extends CommandRef {
            public static final getStrongestEnemy cmd = new getStrongestEnemy();
            public getStrongestEnemy create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStrongestEnemyOfScore")
        public static class getStrongestEnemyOfScore extends CommandRef {
            public static final getStrongestEnemyOfScore cmd = new getStrongestEnemyOfScore();
            public getStrongestEnemyOfScore create(String minScore, String maxScore) {
                return createArgs("minScore", minScore, "maxScore", maxScore);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStrongestEnemyRelative")
        public static class getStrongestEnemyRelative extends CommandRef {
            public static final getStrongestEnemyRelative cmd = new getStrongestEnemyRelative();
            public getStrongestEnemyRelative create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getStrongestOffEnemyOfScore")
        public static class getStrongestOffEnemyOfScore extends CommandRef {
            public static final getStrongestOffEnemyOfScore cmd = new getStrongestOffEnemyOfScore();
            public getStrongestOffEnemyOfScore create(String minScore, String maxScore) {
                return createArgs("minScore", minScore, "maxScore", maxScore);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTankPct")
        public static class getTankPct extends CommandRef {
            public static final getTankPct cmd = new getTankPct();
            public getTankPct create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTanks")
        public static class getTanks extends CommandRef {
            public static final getTanks cmd = new getTanks();
            public getTanks create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTax_id")
        public static class getTax_id extends CommandRef {
            public static final getTax_id cmd = new getTax_id();
            public getTax_id create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTotalLand")
        public static class getTotalLand extends CommandRef {
            public static final getTotalLand cmd = new getTotalLand();
            public getTotalLand create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTreasureBonusPct")
        public static class getTreasureBonusPct extends CommandRef {
            public static final getTreasureBonusPct cmd = new getTreasureBonusPct();
            public getTreasureBonusPct create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTurnsFromDC")
        public static class getTurnsFromDC extends CommandRef {
            public static final getTurnsFromDC cmd = new getTurnsFromDC();
            public getTurnsFromDC create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getTurnsTillDC")
        public static class getTurnsTillDC extends CommandRef {
            public static final getTurnsTillDC cmd = new getTurnsTillDC();
            public getTurnsTillDC create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUnits")
        public static class getUnits extends CommandRef {
            public static final getUnits cmd = new getUnits();
            public getUnits create(String unit) {
                return createArgs("unit", unit);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUserAgeDays")
        public static class getUserAgeDays extends CommandRef {
            public static final getUserAgeDays cmd = new getUserAgeDays();
            public getUserAgeDays create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUserAgeMs")
        public static class getUserAgeMs extends CommandRef {
            public static final getUserAgeMs cmd = new getUserAgeMs();
            public getUserAgeMs create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUserDiscriminator")
        public static class getUserDiscriminator extends CommandRef {
            public static final getUserDiscriminator cmd = new getUserDiscriminator();
            public getUserDiscriminator create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUserId")
        public static class getUserId extends CommandRef {
            public static final getUserId cmd = new getUserId();
            public getUserId create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getUserMention")
        public static class getUserMention extends CommandRef {
            public static final getUserMention cmd = new getUserMention();
            public getUserMention create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getVacationTurnsElapsed")
        public static class getVacationTurnsElapsed extends CommandRef {
            public static final getVacationTurnsElapsed cmd = new getVacationTurnsElapsed();
            public getVacationTurnsElapsed create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getVm_turns")
        public static class getVm_turns extends CommandRef {
            public static final getVm_turns cmd = new getVm_turns();
            public getVm_turns create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getWarPolicy")
        public static class getWarPolicy extends CommandRef {
            public static final getWarPolicy cmd = new getWarPolicy();
            public getWarPolicy create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getWarPolicyAbsoluteTurn")
        public static class getWarPolicyAbsoluteTurn extends CommandRef {
            public static final getWarPolicyAbsoluteTurn cmd = new getWarPolicyAbsoluteTurn();
            public getWarPolicyAbsoluteTurn create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getWarPolicyTurns")
        public static class getWarPolicyTurns extends CommandRef {
            public static final getWarPolicyTurns cmd = new getWarPolicyTurns();
            public getWarPolicyTurns create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getWars_lost")
        public static class getWars_lost extends CommandRef {
            public static final getWars_lost cmd = new getWars_lost();
            public getWars_lost create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="getWars_won")
        public static class getWars_won extends CommandRef {
            public static final getWars_won cmd = new getWars_won();
            public getWars_won create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasAllPermission")
        public static class hasAllPermission extends CommandRef {
            public static final hasAllPermission cmd = new hasAllPermission();
            public hasAllPermission create(String permissions) {
                return createArgs("permissions", permissions);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasAnyPermission")
        public static class hasAnyPermission extends CommandRef {
            public static final hasAnyPermission cmd = new hasAnyPermission();
            public hasAnyPermission create(String permissions) {
                return createArgs("permissions", permissions);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtAircraftToday")
        public static class hasBoughtAircraftToday extends CommandRef {
            public static final hasBoughtAircraftToday cmd = new hasBoughtAircraftToday();
            public hasBoughtAircraftToday create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtMissileToday")
        public static class hasBoughtMissileToday extends CommandRef {
            public static final hasBoughtMissileToday cmd = new hasBoughtMissileToday();
            public hasBoughtMissileToday create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtNukeToday")
        public static class hasBoughtNukeToday extends CommandRef {
            public static final hasBoughtNukeToday cmd = new hasBoughtNukeToday();
            public hasBoughtNukeToday create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtShipsToday")
        public static class hasBoughtShipsToday extends CommandRef {
            public static final hasBoughtShipsToday cmd = new hasBoughtShipsToday();
            public hasBoughtShipsToday create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtSoldiersToday")
        public static class hasBoughtSoldiersToday extends CommandRef {
            public static final hasBoughtSoldiersToday cmd = new hasBoughtSoldiersToday();
            public hasBoughtSoldiersToday create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtSpiesToday")
        public static class hasBoughtSpiesToday extends CommandRef {
            public static final hasBoughtSpiesToday cmd = new hasBoughtSpiesToday();
            public hasBoughtSpiesToday create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBoughtTanksToday")
        public static class hasBoughtTanksToday extends CommandRef {
            public static final hasBoughtTanksToday cmd = new hasBoughtTanksToday();
            public hasBoughtTanksToday create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasBounty")
        public static class hasBounty extends CommandRef {
            public static final hasBounty cmd = new hasBounty();
            public hasBounty create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasNukeBounty")
        public static class hasNukeBounty extends CommandRef {
            public static final hasNukeBounty cmd = new hasNukeBounty();
            public hasNukeBounty create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasPermission")
        public static class hasPermission extends CommandRef {
            public static final hasPermission cmd = new hasPermission();
            public hasPermission create(String permission) {
                return createArgs("permission", permission);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasPriorBan")
        public static class hasPriorBan extends CommandRef {
            public static final hasPriorBan cmd = new hasPriorBan();
            public hasPriorBan create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasProject")
        public static class hasProject extends CommandRef {
            public static final hasProject cmd = new hasProject();
            public hasProject create(String project) {
                return createArgs("project", project);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasProjects")
        public static class hasProjects extends CommandRef {
            public static final hasProjects cmd = new hasProjects();
            public hasProjects create(String projects, String any) {
                return createArgs("projects", projects, "any", any);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasTreasure")
        public static class hasTreasure extends CommandRef {
            public static final hasTreasure cmd = new hasTreasure();
            public hasTreasure create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasUnitBuyToday")
        public static class hasUnitBuyToday extends CommandRef {
            public static final hasUnitBuyToday cmd = new hasUnitBuyToday();
            public hasUnitBuyToday create(String unit) {
                return createArgs("unit", unit);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasUnsetMil")
        public static class hasUnsetMil extends CommandRef {
            public static final hasUnsetMil cmd = new hasUnsetMil();
            public hasUnsetMil create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="hasWarBounty")
        public static class hasWarBounty extends CommandRef {
            public static final hasWarBounty cmd = new hasWarBounty();
            public hasWarBounty create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="infraAttackModifier")
        public static class infraAttackModifier extends CommandRef {
            public static final infraAttackModifier cmd = new infraAttackModifier();
            public infraAttackModifier create(String type) {
                return createArgs("type", type);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="infraDefendModifier")
        public static class infraDefendModifier extends CommandRef {
            public static final infraDefendModifier cmd = new infraDefendModifier();
            public infraDefendModifier create(String type) {
                return createArgs("type", type);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isAllianceColor")
        public static class isAllianceColor extends CommandRef {
            public static final isAllianceColor cmd = new isAllianceColor();
            public isAllianceColor create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isAttackingEnemyOfCities")
        public static class isAttackingEnemyOfCities extends CommandRef {
            public static final isAttackingEnemyOfCities cmd = new isAttackingEnemyOfCities();
            public isAttackingEnemyOfCities create(String minCities, String maxCities) {
                return createArgs("minCities", minCities, "maxCities", maxCities);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isAttackingEnemyOfScore")
        public static class isAttackingEnemyOfScore extends CommandRef {
            public static final isAttackingEnemyOfScore cmd = new isAttackingEnemyOfScore();
            public isAttackingEnemyOfScore create(String minScore, String maxScore) {
                return createArgs("minScore", minScore, "maxScore", maxScore);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isBanEvading")
        public static class isBanEvading extends CommandRef {
            public static final isBanEvading cmd = new isBanEvading();
            public isBanEvading create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isBeige")
        public static class isBeige extends CommandRef {
            public static final isBeige cmd = new isBeige();
            public isBeige create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isBlitzkrieg")
        public static class isBlitzkrieg extends CommandRef {
            public static final isBlitzkrieg cmd = new isBlitzkrieg();
            public isBlitzkrieg create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isBlockaded")
        public static class isBlockaded extends CommandRef {
            public static final isBlockaded cmd = new isBlockaded();
            public isBlockaded create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isBlockader")
        public static class isBlockader extends CommandRef {
            public static final isBlockader cmd = new isBlockader();
            public isBlockader create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isDefendingEnemyOfCities")
        public static class isDefendingEnemyOfCities extends CommandRef {
            public static final isDefendingEnemyOfCities cmd = new isDefendingEnemyOfCities();
            public isDefendingEnemyOfCities create(String minCities, String maxCities) {
                return createArgs("minCities", minCities, "maxCities", maxCities);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isEnemy")
        public static class isEnemy extends CommandRef {
            public static final isEnemy cmd = new isEnemy();
            public isEnemy create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isEspionageAvailable")
        public static class isEspionageAvailable extends CommandRef {
            public static final isEspionageAvailable cmd = new isEspionageAvailable();
            public isEspionageAvailable create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isEspionageFull")
        public static class isEspionageFull extends CommandRef {
            public static final isEspionageFull cmd = new isEspionageFull();
            public isEspionageFull create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isFightingActive")
        public static class isFightingActive extends CommandRef {
            public static final isFightingActive cmd = new isFightingActive();
            public isFightingActive create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isFightingEnemyOfCities")
        public static class isFightingEnemyOfCities extends CommandRef {
            public static final isFightingEnemyOfCities cmd = new isFightingEnemyOfCities();
            public isFightingEnemyOfCities create(String minCities, String maxCities) {
                return createArgs("minCities", minCities, "maxCities", maxCities);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isFightingEnemyOfScore")
        public static class isFightingEnemyOfScore extends CommandRef {
            public static final isFightingEnemyOfScore cmd = new isFightingEnemyOfScore();
            public isFightingEnemyOfScore create(String minScore, String maxScore) {
                return createArgs("minScore", minScore, "maxScore", maxScore);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isGray")
        public static class isGray extends CommandRef {
            public static final isGray cmd = new isGray();
            public isGray create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isIn")
        public static class isIn extends CommandRef {
            public static final isIn cmd = new isIn();
            public isIn create(String nations) {
                return createArgs("nations", nations);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isInAllianceGuild")
        public static class isInAllianceGuild extends CommandRef {
            public static final isInAllianceGuild cmd = new isInAllianceGuild();
            public isInAllianceGuild create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isInMilcomGuild")
        public static class isInMilcomGuild extends CommandRef {
            public static final isInMilcomGuild cmd = new isInMilcomGuild();
            public isInMilcomGuild create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isInSpyRange")
        public static class isInSpyRange extends CommandRef {
            public static final isInSpyRange cmd = new isInSpyRange();
            public isInSpyRange create(String other) {
                return createArgs("other", other);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isInWarRange")
        public static class isInWarRange extends CommandRef {
            public static final isInWarRange cmd = new isInWarRange();
            public isInWarRange create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isOnline")
        public static class isOnline extends CommandRef {
            public static final isOnline cmd = new isOnline();
            public isOnline create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isPowered")
        public static class isPowered extends CommandRef {
            public static final isPowered cmd = new isPowered();
            public isPowered create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isReroll")
        public static class isReroll extends CommandRef {
            public static final isReroll cmd = new isReroll();
            public isReroll create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isTaxable")
        public static class isTaxable extends CommandRef {
            public static final isTaxable cmd = new isTaxable();
            public isTaxable create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="isVerified")
        public static class isVerified extends CommandRef {
            public static final isVerified cmd = new isVerified();
            public isVerified create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="lastBankDeposit")
        public static class lastBankDeposit extends CommandRef {
            public static final lastBankDeposit cmd = new lastBankDeposit();
            public lastBankDeposit create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="login_daychange")
        public static class login_daychange extends CommandRef {
            public static final login_daychange cmd = new login_daychange();
            public login_daychange create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="lootModifier")
        public static class lootModifier extends CommandRef {
            public static final lootModifier cmd = new lootModifier();
            public lootModifier create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="lootTotal")
        public static class lootTotal extends CommandRef {
            public static final lootTotal cmd = new lootTotal();
            public lootTotal create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="looterModifier")
        public static class looterModifier extends CommandRef {
            public static final looterModifier cmd = new looterModifier();
            public looterModifier create(String isGround) {
                return createArgs("isGround", isGround);
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="lostInactiveWar")
        public static class lostInactiveWar extends CommandRef {
            public static final lostInactiveWar cmd = new lostInactiveWar();
            public lostInactiveWar create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="maxBountyValue")
        public static class maxBountyValue extends CommandRef {
            public static final maxBountyValue cmd = new maxBountyValue();
            public maxBountyValue create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="maxCityInfra")
        public static class maxCityInfra extends CommandRef {
            public static final maxCityInfra cmd = new maxCityInfra();
            public maxCityInfra create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="maxWarBountyValue")
        public static class maxWarBountyValue extends CommandRef {
            public static final maxWarBountyValue cmd = new maxWarBountyValue();
            public maxWarBountyValue create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="militaryValue")
        public static class militaryValue extends CommandRef {
            public static final militaryValue cmd = new militaryValue();
            public militaryValue create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="minWarResistance")
        public static class minWarResistance extends CommandRef {
            public static final minWarResistance cmd = new minWarResistance();
            public minWarResistance create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="minWarResistancePlusMap")
        public static class minWarResistancePlusMap extends CommandRef {
            public static final minWarResistancePlusMap cmd = new minWarResistancePlusMap();
            public minWarResistancePlusMap create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="nukeBountyValue")
        public static class nukeBountyValue extends CommandRef {
            public static final nukeBountyValue cmd = new nukeBountyValue();
            public nukeBountyValue create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="ordinaryBountyValue")
        public static class ordinaryBountyValue extends CommandRef {
            public static final ordinaryBountyValue cmd = new ordinaryBountyValue();
            public ordinaryBountyValue create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="projectSlots")
        public static class projectSlots extends CommandRef {
            public static final projectSlots cmd = new projectSlots();
            public projectSlots create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="raidBountyValue")
        public static class raidBountyValue extends CommandRef {
            public static final raidBountyValue cmd = new raidBountyValue();
            public raidBountyValue create() {
                return createArgs();
            }
        }
        @AutoRegister(clazz=link.locutus.discord.db.entities.DBNation.class,method="treasureDays")
        public static class treasureDays extends CommandRef {
            public static final treasureDays cmd = new treasureDays();
            public treasureDays create() {
                return createArgs();
            }
        }

}
