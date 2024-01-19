package gg.jte.generated.ondemand.guild;
import java.util.*;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Guild;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
import java.util.UUID;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.util.MathMan;
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
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.pnw.BeigeReason;
public final class JtemywartrGenerated {
	public static final String JTE_NAME = "guild/mywartr.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,28,28,28,57,57,57,57,57,58,58,59,59,60,60,62,62,62,62,63,63,64,64,64,64,65,65,65,65,66,66,66,66,67,67,67,67,68,68,68,69,69,69,70,70,71,71,73,73,73,73,73,73,73,73,76,76,76,76,77,77,77,77,78,78,79,79,79,79,80,80,80,80,81,81,81,81,82,82,82,82,83,83,83,83,84,84,84,84,85,85,87,87,88,88,88,88,88,88,88,88,93,93,95,95,96,96,98,98,98,98,104,104,106,106,108,108,109,109,111,111,112,112,114,114,115,115,117,117,118,118,120,120,121,121,123,123,124,124,126,126,127,127,129,129,130,130,132,132,133,133,135,135,138,138,139,139,139,139,143,143,143,143,143,143,149,149,150,150,151,151,152,152,153,153,154,154,155,155,156,156,157,157,158,158,159,159,160,160,161,161,162,162,163,163,169,169,170,170,172,172,174,174,177,177,180,180,181,181,181,181,182,182,184,184,187,187,188,188,188,188,189,189,191,191,192,192,194,194,195,195,195,195,199,199,199,199,199,199,230,230,234,234,234,28,29,30,31,32,33,34,35,36,37,37,37,37};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, DBNation nation, User author, DBWar war, DBNation enemy, WarCard warCard, AttackType recommendedAttack, boolean isAttacker, Set<BeigeReason> permitted) {
		jteOutput.writeContent("\r\n<table class=\"table bg-light border-2 border-secondary\">\r\n    <thead>\r\n    <tr>\r\n        <th scope=\"col\">Nation</th>\r\n        <th scope=\"col\">Alliance</th>\r\n        <th scope=\"col\">&#127961;&#65039;</th>\r\n        <th scope=\"col\">&#128130;</th>\r\n        <th scope=\"col\">&#9881;&#65039;</th>\r\n        <th scope=\"col\">&#9992;&#65039;</th>\r\n        <th scope=\"col\">&#128674;</th>\r\n        <th scope=\"col\">&#128640;/&#9762;&#65039;</th>\r\n        <th scope=\"col\">Off/Def</th>\r\n        <th scope=\"col\">MAP</th>\r\n        <th scope=\"col\">Resist</th>\r\n    </tr>\r\n    </thead>\r\n    <tbody>\r\n<tr class=\"border-top border-1 border-secondary\">\r\n    <td><a href=\"");
		jteOutput.writeUserContent(enemy.getNationUrl());
		jteOutput.writeContent("\">");
		jteOutput.writeUserContent(enemy.getNation());
		jteOutput.writeContent("</a>\r\n    ");
		if (enemy.getActive_m() > 2440) {
			jteOutput.writeContent("\r\n        <span class=\"badge bg-secondary\">inactive ");
			jteOutput.writeUserContent(TimeUtil.minutesToTime(enemy.getActive_m()));
			jteOutput.writeContent("</span>\r\n    ");
		}
		jteOutput.writeContent("\r\n    </td>\r\n    <td><a href=\"");
		jteOutput.writeUserContent(enemy.getAllianceUrl());
		jteOutput.writeContent("\">");
		jteOutput.writeUserContent(enemy.getAllianceName());
		jteOutput.writeContent("</a></td>\r\n    <td>");
		jteOutput.writeUserContent(enemy.getCities());
		jteOutput.writeContent("</td>\r\n    <td style=\"color:rgb(");
		jteOutput.writeUserContent(Math.min(255, Math.max(0, 255 * enemy.getGroundStrength(false, false) / ((double) 4 * nation.getGroundStrength(false, false)))));
		jteOutput.writeContent(", 0, 0)\">");
		jteOutput.writeUserContent(enemy.getSoldiers());
		jteOutput.writeContent("</td>\r\n    <td style=\"color:rgb(");
		jteOutput.writeUserContent(Math.min(255, Math.max(0, 255 * enemy.getGroundStrength(false, false) / ((double) 4 * nation.getGroundStrength(false, false)))));
		jteOutput.writeContent(", 0, 0)\">");
		jteOutput.writeUserContent(enemy.getTanks());
		jteOutput.writeContent("</td>\r\n    <td style=\"color:rgb(");
		jteOutput.writeUserContent(Math.min(255, Math.max(0, 255 * enemy.getAircraft() / ((double) 5 * nation.getAircraft()))));
		jteOutput.writeContent(", 0, 0)\">");
		jteOutput.writeUserContent(enemy.getAircraft());
		jteOutput.writeContent("</td>\r\n    <td style=\"color:rgb(");
		jteOutput.writeUserContent(Math.min(255, Math.max(0, 255 * enemy.getShips() / ((double) 5 * nation.getShips()))));
		jteOutput.writeContent(", 0, 0)\">");
		jteOutput.writeUserContent(enemy.getShips());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(enemy.getMissiles());
		jteOutput.writeUserContent(enemy.getNukes());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(enemy.getOff());
		jteOutput.writeUserContent(enemy.getDef());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(isAttacker?warCard.defenderMAP:warCard.attackerMAP);
		jteOutput.writeContent("</td>\r\n    <td><div class=\"progress-bar\" role=\"progressbar\" aria-valuenow=\"");
		jteOutput.writeUserContent((isAttacker?warCard.defenderResistance:warCard.attackerResistance));
		jteOutput.writeContent("\"\r\n             aria-valuemin=\"0\" aria-valuemax=\"100\"\r\n             style=\"width:");
		jteOutput.writeUserContent(Math.max(10,(isAttacker?warCard.defenderResistance:warCard.attackerResistance)));
		jteOutput.writeContent("%;background-color:rgb(");
		jteOutput.writeUserContent(MathMan.clamp(255 - (isAttacker?warCard.defenderResistance:warCard.attackerResistance) * 255 / 100, 0, 255));
		jteOutput.writeContent(", ");
		jteOutput.writeUserContent(MathMan.clamp((isAttacker?warCard.defenderResistance:warCard.attackerResistance) * 255 / 100, 0, 255));
		jteOutput.writeContent(", 0)!important\">");
		jteOutput.writeUserContent((isAttacker?warCard.defenderResistance:warCard.attackerResistance));
		jteOutput.writeContent("</div></td>\r\n</tr>\r\n<tr class=\"border-top border-1 border-secondary\">\r\n    <td><a href=\"");
		jteOutput.writeUserContent(enemy.getNationUrl());
		jteOutput.writeContent("\">");
		jteOutput.writeUserContent(nation.getNation());
		jteOutput.writeContent("</a></td>\r\n    <td><a href=\"");
		jteOutput.writeUserContent(enemy.getAllianceUrl());
		jteOutput.writeContent("\">");
		jteOutput.writeUserContent(nation.getAllianceName());
		jteOutput.writeContent("</a></td>\r\n    <td>");
		jteOutput.writeUserContent(nation.getCities());
		jteOutput.writeContent("</td>\r\n    <td style=\"color:rgb(0, ");
		jteOutput.writeUserContent(Math.min(255, Math.max(0, 255 * nation.getGroundStrength(false, false) / ((double) 4 * enemy.getGroundStrength(false, false)))));
		jteOutput.writeContent(", 0)\">");
		jteOutput.writeUserContent(nation.getSoldiers());
		jteOutput.writeContent("</td>\r\n    <td style=\"color:rgb(0, ");
		jteOutput.writeUserContent(Math.min(255, Math.max(0, 255 * nation.getGroundStrength(false, false) / ((double) 4 * enemy.getGroundStrength(false, false)))));
		jteOutput.writeContent(", 0)\">");
		jteOutput.writeUserContent(nation.getTanks());
		jteOutput.writeContent("</td>\r\n    <td style=\"color:rgb(0, ");
		jteOutput.writeUserContent(Math.min(255, Math.max(0, 255 * nation.getAircraft() / ((double) 5 * enemy.getAircraft()))));
		jteOutput.writeContent(", 0)\">");
		jteOutput.writeUserContent(nation.getAircraft());
		jteOutput.writeContent("</td>\r\n    <td style=\"color:rgb(0, ");
		jteOutput.writeUserContent(Math.min(255, Math.max(0, 255 * nation.getShips() / ((double) 5 * enemy.getShips()))));
		jteOutput.writeContent(", 0)\">");
		jteOutput.writeUserContent(nation.getShips());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(nation.getMissiles());
		jteOutput.writeContent("/");
		jteOutput.writeUserContent(nation.getNukes());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(nation.getOff());
		jteOutput.writeContent("/");
		jteOutput.writeUserContent(nation.getDef());
		jteOutput.writeContent("</td>\r\n    <td>");
		jteOutput.writeUserContent(!isAttacker?warCard.defenderMAP:warCard.attackerMAP);
		jteOutput.writeContent("</td>\r\n    <td><div class=\"progress-bar\" role=\"progressbar\"\r\n             aria-valuenow=\"");
		jteOutput.writeUserContent((!isAttacker?warCard.defenderResistance:warCard.attackerResistance));
		jteOutput.writeContent("\" aria-valuemin=\"0\" aria-valuemax=\"100\"\r\n             style=\"width:");
		jteOutput.writeUserContent(Math.max(10,(!isAttacker?warCard.defenderResistance:warCard.attackerResistance)));
		jteOutput.writeContent("%;background-color:rgb(");
		jteOutput.writeUserContent(MathMan.clamp(255 - (!isAttacker?warCard.defenderResistance:warCard.attackerResistance) * 255 / 100, 0, 255));
		jteOutput.writeContent(", ");
		jteOutput.writeUserContent(MathMan.clamp((!isAttacker?warCard.defenderResistance:warCard.attackerResistance) * 255 / 100, 0, 255));
		jteOutput.writeContent(", 0)!important\">");
		jteOutput.writeUserContent((!isAttacker?warCard.defenderResistance:warCard.attackerResistance));
		jteOutput.writeContent("</div></td>\r\n</tr>\r\n<tr class=\"border-top border-1 border-secondary\">\r\n    <td colspan=\"3\"></td>\r\n    <td colspan=\"2\">\r\n        <a class=\"btn btn-secondary btn-sm d-flex p-0 m-0 justify-content-center\" href=\"https://politicsandwar.com/nation/war/groundbattle/war=");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\">&#128130; ground attack</a>\r\n    </td>\r\n    <td><a class=\"btn btn-secondary btn-sm d-flex p-0 m-0 justify-content-center\" href=\"https://politicsandwar.com/nation/war/airstrike/war=");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\">&#9992;&#65039; airstrike</a></td>\r\n    <td><a class=\"btn btn-secondary btn-sm d-flex p-0 m-0 justify-content-center\" href=\"https://politicsandwar.com/nation/war/navalbattle/war=");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\">&#128674; naval</a></td>\r\n    <td class=\"d-flex justify-content-center\">\r\n        <a class=\"btn btn-secondary w-50 btn-sm p-0 m-0 justify-content-center\" href=\"https://politicsandwar.com/nation/war/missile/war=");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\">&#128640;</a>|<a class=\"btn btn-secondary w-50 btn-sm p-0 m-0 justify-content-center\" href=\"https://politicsandwar.com/nation/war/nuke/war=");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\">&#9762;&#65039;</a>\r\n    </td>\r\n\r\n</tr>\r\n<tr class=\"border-bottom border-1 border-secondary mb-2\">\r\n    <td colspan=\"100\">\r\n        <a href=\"https://politicsandwar.com/nation/war/timeline/war=");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\">War link</a><br>\r\n        <hr>\r\n        ");
		if (nation.hasProject(Projects.MISSILE_LAUNCH_PAD) && enemy.hasProject(Projects.IRON_DOME)) {
			jteOutput.writeContent("\r\n            <p>IRON_DOME (50% chance to thwart missiles)</p>\r\n        ");
		}
		jteOutput.writeContent("\r\n        ");
		if (nation.hasProject(Projects.NUCLEAR_RESEARCH_FACILITY) && enemy.hasProject(Projects.VITAL_DEFENSE_SYSTEM)) {
			jteOutput.writeContent("\r\n            <p>VITAL_DEFENSE_SYSTEM (20% chance to thwart nukes)</p>\r\n        ");
		}
		jteOutput.writeContent("\r\n        ");
		if (warCard.attackerFortified) {
			jteOutput.writeContent("\r\n\r\n        ");
		}
		jteOutput.writeContent("\r\n        ");
		if (warCard.defenderFortified) {
			jteOutput.writeContent("\r\n\r\n        ");
		}
		jteOutput.writeContent("\r\n        ");
		if (warCard.blockaded == nation.getNation_id()) {
			jteOutput.writeContent("\r\n\r\n        ");
		}
		jteOutput.writeContent("\r\n        ");
		if (warCard.blockaded == enemy.getNation_id()) {
			jteOutput.writeContent("\r\n\r\n        ");
		}
		jteOutput.writeContent("\r\n        ");
		if (warCard.groundControl == nation.getNation_id()) {
			jteOutput.writeContent("\r\n\r\n        ");
		}
		jteOutput.writeContent("\r\n        ");
		if (warCard.groundControl == enemy.getNation_id()) {
			jteOutput.writeContent("\r\n\r\n        ");
		}
		jteOutput.writeContent("\r\n        ");
		if (warCard.airSuperiority == nation.getNation_id()) {
			jteOutput.writeContent("\r\n\r\n        ");
		}
		jteOutput.writeContent("\r\n        ");
		if (warCard.airSuperiority == enemy.getNation_id()) {
			jteOutput.writeContent("\r\n\r\n        ");
		}
		jteOutput.writeContent("\r\n        <div class=\"accordion\" id=\"oddsAccordion\">\r\n            <div class=\"accordion-item m-1 bg-primary bg-gradient\">\r\n                <h2 class=\"accordion-header\" id=\"headingodds");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\">\r\n                    <button class=\"accordion-button collapsed p-1 text-white btn-sm bg-primary bg-gradient\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#collapseodds");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\" aria-expanded=\"false\" aria-controls=\"collapseodds");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\">\r\n                        Show odds\r\n                    </button>\r\n                </h2>\r\n                <div id=\"collapseodds");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\" class=\"accordion-collapse collapse\" aria-labelledby=\"headingodds");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\" data-bs-parent=\"#oddsAccordion");
		jteOutput.writeUserContent(war.warId);
		jteOutput.writeContent("\">\r\n                    <div class=\"accordion-body  bg-light\">\r\n                        <div><div class=\"bg-danger align-middle d-inline-block m-1 border border-2 border-dark\" style=\"width:1.5em;height:1.5em\"></div>Utter Failure</div>\r\n                        <div><div class=\"bg-warning align-middle d-inline-block m-1 border border-2 border-dark\" style=\"width:1.5em;height:1.5em\"></div>Pyrrhic Victory</div>\r\n                        <div><div class=\"bg-info align-middle d-inline-block m-1 border border-2 border-dark\" style=\"width:1.5em;height:1.5em\"></div>Moderate Success</div>\r\n                        <div><div class=\"bg-primary align-middle d-inline-block m-1 border border-2 border-dark\" style=\"width:1.5em;height:1.5em\"></div>Immense Triumph</div>\r\n                        ");
		if (nation.getSoldiers() > enemy.getGroundStrength(true, false) * 0.3 && enemy.getGroundStrength(true, false) > 0) {
			jteOutput.writeContent("\r\n                            ");
			gg.jte.generated.ondemand.guild.JteoddsGenerated.render(jteOutput, jteHtmlInterceptor, ws, "Soldiers (unarmed) v Enemy", nation.getSoldiers(), enemy.getGroundStrength(true, false));
			jteOutput.writeContent("\r\n                        ");
		}
		jteOutput.writeContent("\r\n                        ");
		if (nation.getSoldiers() * 1.7_5 > enemy.getGroundStrength(true, false) * 0.3 && enemy.getGroundStrength(true, false) > 0) {
			jteOutput.writeContent("\r\n                            ");
			gg.jte.generated.ondemand.guild.JteoddsGenerated.render(jteOutput, jteHtmlInterceptor, ws, "Soldiers (munitions) v Enemy", nation.getSoldiers() * 1.7_5, enemy.getGroundStrength(true, false));
			jteOutput.writeContent("\r\n                        ");
		}
		jteOutput.writeContent("\r\n                        ");
		if (nation.getGroundStrength(true, false) > 0 && enemy.getGroundStrength(true, false) > 0) {
			jteOutput.writeContent("\r\n                            ");
			gg.jte.generated.ondemand.guild.JteoddsGenerated.render(jteOutput, jteHtmlInterceptor, ws, "Ground", nation.getGroundStrength(true, false), enemy.getGroundStrength(true, false));
			jteOutput.writeContent("\r\n                        ");
		}
		jteOutput.writeContent("\r\n                        ");
		if (nation.getAircraft() > 0 && enemy.getAircraft() > 0) {
			jteOutput.writeContent("\r\n                            ");
			gg.jte.generated.ondemand.guild.JteoddsGenerated.render(jteOutput, jteHtmlInterceptor, ws, "Airstrike", nation.getAircraft(), enemy.getAircraft());
			jteOutput.writeContent("\r\n                        ");
		}
		jteOutput.writeContent("\r\n                        ");
		if (nation.getShips() > 0 && enemy.getShips() > 0) {
			jteOutput.writeContent("\r\n                            ");
			gg.jte.generated.ondemand.guild.JteoddsGenerated.render(jteOutput, jteHtmlInterceptor, ws, "Naval", nation.getShips(), enemy.getShips());
			jteOutput.writeContent("\r\n                        ");
		}
		jteOutput.writeContent("\r\n                    </div>\r\n                </div>\r\n            </div>\r\n        </div>\r\n<!--        <hr>-->\r\n        ");
		if (permitted != null && isAttacker && (db.isEnemyAlliance(war.getDefender_aa()))) {
			jteOutput.writeContent("\r\n        <div class=\"alert ");
			if (permitted.isEmpty()) {
				jteOutput.writeContent("\r\n                alert-danger\r\n            ");
			} else {
				jteOutput.writeContent("\r\n                alert-success\r\n            ");
			}
			jteOutput.writeContent("\r\n            \">\r\n            <p class=\"lead\">This is an enemy nation</p>\r\n            ");
			if (permitted.isEmpty()) {
				jteOutput.writeContent("\r\n                <p><b>Please avoid defeating this enemy. None of the following allowed beige reasons are met</b></p>\r\n                <ul>\r\n                ");
				for (BeigeReason reason : db.getAllowedBeigeReasons(enemy)) {
					jteOutput.writeContent("\r\n                    <li><u>");
					jteOutput.writeUserContent(reason);
					jteOutput.writeContent("</u><br>");
					jteOutput.writeUserContent(reason.getDescription());
					jteOutput.writeContent("</li>\r\n                ");
				}
				jteOutput.writeContent("\r\n                </ul>\r\n            ");
			} else {
				jteOutput.writeContent("\r\n                <p><b>You can defeat this enemy for the following reasons</b></p>\r\n                <ul>\r\n                    ");
				for (BeigeReason reason : permitted) {
					jteOutput.writeContent("\r\n                    <li><u>");
					jteOutput.writeUserContent(reason);
					jteOutput.writeContent("</u><br>");
					jteOutput.writeUserContent(reason.getDescription());
					jteOutput.writeContent("</li>\r\n                    ");
				}
				jteOutput.writeContent("\r\n                </ul>\r\n            ");
			}
			jteOutput.writeContent("\r\n            <div class=\"accordion\" id=\"beigeAccordion");
			jteOutput.writeUserContent(war.warId);
			jteOutput.writeContent("\">\r\n                <div class=\"accordion-item bg-primary bg-gradient\">\r\n                    <h2 class=\"accordion-header\" id=\"headingBeige");
			jteOutput.writeUserContent(war.warId);
			jteOutput.writeContent("\">\r\n                        <button class=\"accordion-button collapsed p-1 text-white btn-sm bg-primary bg-gradient\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#collapseBeige");
			jteOutput.writeUserContent(war.warId);
			jteOutput.writeContent("\" aria-expanded=\"false\" aria-controls=\"collapseBeige");
			jteOutput.writeUserContent(war.warId);
			jteOutput.writeContent("\">\r\n                            Beige Cycling Info\r\n                        </button>\r\n                    </h2>\r\n                    <div id=\"collapseBeige");
			jteOutput.writeUserContent(war.warId);
			jteOutput.writeContent("\" class=\"accordion-collapse collapse\" aria-labelledby=\"headingBeige");
			jteOutput.writeUserContent(war.warId);
			jteOutput.writeContent("\" data-bs-parent=\"#beigeAccordion");
			jteOutput.writeUserContent(war.warId);
			jteOutput.writeContent("\">\r\n                        <div class=\"accordion-body bg-light\">\r\n                <h5>What is beige?</h5>\r\n                <p>A nation defeated gets 2 more days of being on the beige color. Beige protects from new war declarations. We want to have active enemies always in war, so they don&apos;t have the opportunity to build back up.</p>\r\n                <h5>Tips for avoiding unnecessary attacks:</h5>\r\n                <ol>\r\n                    <li>Don&apos;t open with navals if they have units which are a threat. Ships can&apos;t attack planes, tanks or soldiers.</li>\r\n                    <li>Dont naval if you already have them blockaded</li>\r\n                    <li>Never airstrike infra, cash, or small amounts of units - wait for them to build more units</li>\r\n                    <li>If they just have some soldiers and can&apos;t get a victory against you, don&apos;t spam ground attacks.</li>\r\n                    <li>If the enemy only has soldiers (no tanks) and you have max planes. Airstriking soldiers kills more soldiers than a ground attack will.</li>\r\n                    <li>Missiles/Nukes do NOT kill any units</li>\r\n                </ol>\r\n                <p>note: You can do some unnecessary attacks if the war is going to expire, or you need to beige them as part of a beige cycle</p>\r\n\r\n                <h5>What is beige cycling?</h5>\r\n                <p>Beige cycling is when we have a weakened enemy, and 3 strong nations declared on that enemy - then 1 nation defeats them, whilst the other two sit on them whilst they are on beige. <br>\r\n                    When their 2 days of beige from the defeat ends, another nation declares on the enemies free slot and the next nation defeats the enemy.</p>\r\n                <h5>Beige cycling checklist:</h5>\r\n                <ol>\r\n                    <li>Is the enemy military mostly weakened/gone?</li>\r\n                    <li>Is the enemy not currently on beige?</li>\r\n                    <li>Do they have 3 defensive wars, with the other two attackers having enough military?</li>\r\n                    <li>Are you the first person to have declared?</li>\r\n                </ol>\r\n                <p>Tip: Save your MAP. Avoid going below 40 resistance until you are GO for beiging them</p>\r\n                        </div>\r\n                    </div>\r\n                </div>\r\n            </div>\r\n        </div>\r\n        ");
		}
		jteOutput.writeContent("\r\n    </td>\r\n</tr>\r\n    </tbody>\r\n</table>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		DBNation nation = (DBNation)params.get("nation");
		User author = (User)params.get("author");
		DBWar war = (DBWar)params.get("war");
		DBNation enemy = (DBNation)params.get("enemy");
		WarCard warCard = (WarCard)params.get("warCard");
		AttackType recommendedAttack = (AttackType)params.get("recommendedAttack");
		boolean isAttacker = (boolean)params.get("isAttacker");
		Set<BeigeReason> permitted = (Set<BeigeReason>)params.get("permitted");
		render(jteOutput, jteHtmlInterceptor, ws, db, nation, author, war, enemy, warCard, recommendedAttack, isAttacker, permitted);
	}
}
