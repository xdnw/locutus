package gg.jte.generated.ondemand.guild;
import java.util.*;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv1.enums.city.project.Project;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Guild;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
import java.util.UUID;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.Locutus;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import link.locutus.discord.util.PnwUtil;
import java.util.Collection;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.pnw.BeigeReason;
public final class JtemywarsGenerated {
	public static final String JTE_NAME = "guild/mywars.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,27,27,27,38,38,38,45,45,48,48,50,50,52,52,54,54,56,56,57,57,59,59,61,61,63,63,65,65,66,66,68,68,70,70,72,72,74,74,75,75,77,77,79,79,81,81,83,83,87,87,89,89,90,90,92,92,94,94,96,96,98,98,100,100,101,101,102,102,103,103,104,104,105,105,106,106,107,107,108,108,110,110,111,111,112,112,113,113,115,115,119,119,119,119,27,28,29,30,31,32,33,34,35,36,36,36,36};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, DBNation nation, User author, Collection<JavaCity> cities, boolean isFightingActives, Map<DBWar, DBNation> offensives, Map<DBWar, DBNation> defensives, Map<DBWar, WarCard> warCards, Map<DBWar, AttackType> recommendedAttacks) {
		jteOutput.writeContent("\r\n");
		if (nation.getNumWars() > 0) {
			jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h3 class=\"\">Current Wars</h3>\r\n    <hr>\r\n    <a href=\"https://politicsandwar.com/nation/war/\" class=\"btn btn-primary btn\">War Page <i class=\"bi bi-box-arrow-up-right text-light\"></i></a>\r\n    <hr>\r\n\r\n    ");
			if (isFightingActives) {
				jteOutput.writeContent("\r\n    <h4>Buy units</h4>\r\n    <p class=\"border-left border-2 border-secondary ml-1\">It is recommended to repurchase lost units after each attack</p>\r\n    ");
				if (MilitaryUnit.SOLDIER.getCap(() -> cities, nation::hasProject) > 0) {
					jteOutput.writeContent("\r\n    <a href=\"https://politicsandwar.com/nation/military/soldiers/\" class=\"\r\n    ");
					if (nation.getSoldiers() * 1.05 < MilitaryUnit.SOLDIER.getCap(() -> cities, nation::hasProject)) {
						jteOutput.writeContent("\r\n        btn btn-sm btn-primary\r\n    ");
					} else {
						jteOutput.writeContent("\r\n        btn btn-sm btn-secondary\r\n    ");
					}
					jteOutput.writeContent("\r\n    \">Buy soldiers <i class=\"bi bi-box-arrow-up-right text-light\"></i></a>\r\n    ");
				}
				jteOutput.writeContent("\r\n    ");
				if (MilitaryUnit.TANK.getCap(() -> cities, nation::hasProject) > 0) {
					jteOutput.writeContent("\r\n    <a href=\"https://politicsandwar.com/nation/military/tanks/\" class=\"\r\n    ");
					if (nation.getTanks() * 1.05 < MilitaryUnit.TANK.getCap(() -> cities, nation::hasProject)) {
						jteOutput.writeContent("\r\n        btn btn-sm btn-primary\r\n    ");
					} else {
						jteOutput.writeContent("\r\n        btn btn-sm btn-secondary\r\n    ");
					}
					jteOutput.writeContent("\r\n    \">Buy tanks <i class=\"bi bi-box-arrow-up-right text-light\"></i></a>\r\n    ");
				}
				jteOutput.writeContent("\r\n    ");
				if (MilitaryUnit.AIRCRAFT.getCap(() -> cities, nation::hasProject) > 0) {
					jteOutput.writeContent("\r\n    <a href=\"https://politicsandwar.com/nation/military/aircraft/\" class=\"\r\n    ");
					if (nation.getAircraft() * 1.05 < MilitaryUnit.AIRCRAFT.getCap(() -> cities, nation::hasProject)) {
						jteOutput.writeContent("\r\n        btn btn-sm btn-primary\r\n    ");
					} else {
						jteOutput.writeContent("\r\n        btn btn-sm btn-secondary\r\n    ");
					}
					jteOutput.writeContent("\r\n    \">Buy aircraft <i class=\"bi bi-box-arrow-up-right text-light\"></i></a>\r\n    ");
				}
				jteOutput.writeContent("\r\n    ");
				if (MilitaryUnit.SHIP.getCap(() -> cities, nation::hasProject) > 0) {
					jteOutput.writeContent("\r\n    <a href=\"https://politicsandwar.com/nation/military/navy/\" class=\"\r\n    ");
					if (nation.getShips() * 1.05 < MilitaryUnit.SHIP.getCap(() -> cities, nation::hasProject)) {
						jteOutput.writeContent("\r\n        btn btn-sm btn-primary\r\n    ");
					} else {
						jteOutput.writeContent("\r\n        btn btn-sm btn-secondary\r\n    ");
					}
					jteOutput.writeContent("\r\n    \">Buy navy <i class=\"bi bi-box-arrow-up-right text-light\"></i></a>\r\n    ");
				}
				jteOutput.writeContent("\r\n\r\n    <a href=\"https://politicsandwar.com/nation/military/spies/\" class=\"btn btn-sm btn-primary\">Buy spies <i class=\"bi bi-box-arrow-up-right text-light\"></i></a>\r\n\r\n    ");
				if (nation.getMissiles() == 0 && nation.getMissiles() < MilitaryUnit.MISSILE.getCap(() -> cities, nation::hasProject)) {
					jteOutput.writeContent("\r\n    <a href=\"https://politicsandwar.com/nation/military/missiles/\" class=\"btn btn-sm btn-primary\">Buy missiles <i class=\"bi bi-box-arrow-up-right text-light\"></i></a>\r\n    ");
				}
				jteOutput.writeContent("\r\n    ");
				if (nation.getNukes() == 0 && nation.getNukes() < MilitaryUnit.NUKE.getCap(() -> cities, nation::hasProject)) {
					jteOutput.writeContent("\r\n    <a href=\"https://politicsandwar.com/nation/military/missiles/\" class=\"btn btn-sm btn-primary\">Buy nukes <i class=\"bi bi-box-arrow-up-right text-light\"></i></a>\r\n    ");
				}
				jteOutput.writeContent("\r\n\r\n    ");
			} else {
				jteOutput.writeContent("\r\n    <a href=\"https://politicsandwar.com/nation/military/\" class=\"btn btn-primary btn-sm\">Buy military <i class=\"bi bi-box-arrow-up-right text-light\"></i></a>\r\n    ");
			}
			jteOutput.writeContent("\r\n    <hr>\r\n    ");
			if (!offensives.isEmpty()) {
				jteOutput.writeContent("\r\n    <h4 class=\"\">Offensives</h4>\r\n        ");
				for (Map.Entry<DBWar, DBNation> entry : offensives.entrySet()) {
					jteOutput.writeContent("\r\n            ");
					if (db.getOrNull(GuildKey.ENEMY_BEIGED_ALERT_VIOLATIONS) != null && db.isEnemyAlliance(entry.getKey().getDefender_aa())) {
						jteOutput.writeContent("\r\n                ");
						gg.jte.generated.ondemand.guild.JtemywartrGenerated.render(jteOutput, jteHtmlInterceptor, ws, db, nation, author, entry.getKey(), entry.getValue(), warCards.get(entry.getKey()), recommendedAttacks.get(entry.getKey()), true, BeigeReason.getAllowedBeigeReasons(db, nation, entry.getKey(), null));
						jteOutput.writeContent("\r\n            ");
					} else {
						jteOutput.writeContent("\r\n                ");
						gg.jte.generated.ondemand.guild.JtemywartrGenerated.render(jteOutput, jteHtmlInterceptor, ws, db, nation, author, entry.getKey(), entry.getValue(), warCards.get(entry.getKey()), recommendedAttacks.get(entry.getKey()), true, null);
						jteOutput.writeContent("\r\n            ");
					}
					jteOutput.writeContent("\r\n        ");
				}
				jteOutput.writeContent("\r\n    ");
			}
			jteOutput.writeContent("\r\n    ");
			if (!defensives.isEmpty()) {
				jteOutput.writeContent("\r\n        <h4 class=\"\">Defensives</h4>\r\n        ");
				for (Map.Entry<DBWar, DBNation> entry : defensives.entrySet()) {
					jteOutput.writeContent("\r\n            ");
					gg.jte.generated.ondemand.guild.JtemywartrGenerated.render(jteOutput, jteHtmlInterceptor, ws, db, nation, author, entry.getKey(), entry.getValue(), warCards.get(entry.getKey()), recommendedAttacks.get(entry.getKey()), false, null);
					jteOutput.writeContent("\r\n        ");
				}
				jteOutput.writeContent("\r\n    ");
			}
			jteOutput.writeContent("\r\n</div>\r\n");
		} else {
			jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h2>You currently have no wars</h2>\r\n</div>\r\n");
		}
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		DBNation nation = (DBNation)params.get("nation");
		User author = (User)params.get("author");
		Collection<JavaCity> cities = (Collection<JavaCity>)params.get("cities");
		boolean isFightingActives = (boolean)params.get("isFightingActives");
		Map<DBWar, DBNation> offensives = (Map<DBWar, DBNation>)params.get("offensives");
		Map<DBWar, DBNation> defensives = (Map<DBWar, DBNation>)params.get("defensives");
		Map<DBWar, WarCard> warCards = (Map<DBWar, WarCard>)params.get("warCards");
		Map<DBWar, AttackType> recommendedAttacks = (Map<DBWar, AttackType>)params.get("recommendedAttacks");
		render(jteOutput, jteHtmlInterceptor, ws, db, nation, author, cities, isFightingActives, offensives, defensives, warCards, recommendedAttacks);
	}
}
