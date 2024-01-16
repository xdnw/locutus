package gg.jte.generated.ondemand.grant;
import java.util.*;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv1.enums.city.project.Project;
import net.dv8tion.jda.api.entities.User;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
import java.util.UUID;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.util.TimeUtil;
public final class JtecitiesGenerated {
	public static final String JTE_NAME = "grant/cities.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,12,12,12,20,20,20,20,23,23,33,33,33,33,34,34,35,35,36,36,36,36,39,39,44,44,49,49,50,50,62,62,63,63,75,75,76,76,86,86,93,93,94,94,95,95,98,98,98,98,98,12,13,14,15,16,17,18,18,18,18};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Collection<Grant> grants, User user, DBNation nation, Map<Grant, List<String>> failedRequirements, Map<Grant, List<String>> overrideRequirements, Map<Grant, UUID> grantTokens) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.grant.JtegrantsGenerated.render(jteOutput, jteHtmlInterceptor, ws, "City", new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<div class=\"row\">\r\n    <div class=\"col-lg-3\">\r\n        ");
				gg.jte.generated.ondemand.grant.JtenationGenerated.render(jteOutput, jteHtmlInterceptor, ws, nation);
				jteOutput.writeContent("\r\n    </div>\r\n    <div class=\"col-lg-9\">\r\n        <div class=\"alert p-1 my-1 alert-info border border-info alert-dismissible fade show\" role=\"alert\">\r\n            To purchase a city <a href=\"https://politicsandwar.com/nation/cities/\" class=\"btn btn-secondary btn-sm\">Visit\r\n                the Cities Page 🡕</a><br>\r\n            After city 10, there is a 10 day timer before you are able to purchase another city.\r\n            <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n        </div>\r\n        <div class=\"alert p-1 my-1 alert-info border border-info alert-dismissible fade show\" role=\"alert\">\r\n            <b>City timer: </b>");
				jteOutput.writeUserContent(nation.getCityTurns());
				jteOutput.writeContent(" turns (");
				jteOutput.writeUserContent(TimeUtil.turnsToTime(nation.getCityTurns()));
				jteOutput.writeContent(")<br>\r\n            <b>Project timer: </b>");
				jteOutput.writeUserContent(nation.getProjectTurns());
				jteOutput.writeContent(" turns\r\n            (");
				jteOutput.writeUserContent(TimeUtil.turnsToTime(nation.getProjectTurns()));
				jteOutput.writeContent(")<br>\r\n            <b>Project Slots: </b>");
				jteOutput.writeUserContent(nation.getNumProjects());
				jteOutput.writeContent("/");
				jteOutput.writeUserContent(nation.projectSlots());
				jteOutput.writeContent("<br>\r\n            <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n        </div>\r\n        ");
				if (nation.getCities() < 10 && nation.getCities()> 2) {
					jteOutput.writeContent("\r\n            <div class=\"alert p-1 my-1 alert-warning border border-warning alert-dismissible fade show\"\r\n                 role=\"alert\">\r\n                <p>\r\n                    Raiding is the best way to make profit. Targets become sparser at higher city counts.\r\n                    It is recommended to raid at city ");
					jteOutput.writeUserContent(nation.getCities());
					jteOutput.writeContent(" until you have saved up enough to build\r\n                    to city 10 with 2k infrastructure\r\n                </p>\r\n                <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n            </div>\r\n        ");
				}
				jteOutput.writeContent("\r\n        ");
				if (nation.getCities() == 11 && !nation.hasProject(Projects.URBAN_PLANNING)) {
					jteOutput.writeContent("\r\n            <div class=\"alert p-1 my-1 alert-warning border border-warning alert-dismissible fade show\"\r\n                 role=\"alert\">\r\n                <p>\r\n                    The <kbd>Urban Planning</kbd> project can be bought at city 11 and reduces future city costs by\r\n                    $50m\r\n                    <a href=\"https://politicsandwar.com/nation/projects/\" class=\"btn btn-secondary btn-sm\">Visit\r\n                        Projects Page 🡕</a>\r\n                    <a href=\"../projects/\" class=\"btn btn-secondary btn-sm\">Visit Projects Grants</a>\r\n                </p>\r\n                <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n            </div>\r\n        ");
				}
				jteOutput.writeContent("\r\n        ");
				if (nation.getCities() == 16 && !nation.hasProject(Projects.ADVANCED_URBAN_PLANNING)) {
					jteOutput.writeContent("\r\n            <div class=\"alert p-1 my-1 alert-warning border border-warning alert-dismissible fade show\"\r\n                 role=\"alert\">\r\n                <p>\r\n                    The <kbd>Advanced Urban Planning</kbd> project can be bought at city 16 and reduces future city\r\n                    costs by $100m\r\n                    <a href=\"https://politicsandwar.com/nation/projects/\" class=\"btn btn-secondary btn-sm\">Visit\r\n                        Projects Page 🡕</a>\r\n                    <a href=\"../projects/\" class=\"btn btn-secondary btn-sm\">Visit Projects Grants</a>\r\n                </p>\r\n                <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n            </div>\r\n        ");
				}
				jteOutput.writeContent("\r\n        ");
				if (nation.getDomesticPolicy() != DomesticPolicy.MANIFEST_DESTINY) {
					jteOutput.writeContent("\r\n            <div class=\"alert p-1 my-1 alert-warning border border-warning alert-dismissible fade show\"\r\n                 role=\"alert\">\r\n                <p>\r\n                    Go to the <a href=\"https://politicsandwar.com/nation/edit/\" class=\"btn btn-secondary btn-sm\">edit\r\n                        nation 🡕</a> page to set your domestic policy to <kbd>Manifest Destiny</kbd> to save 5% on\r\n                    projects\r\n                </p>\r\n                <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n            </div>\r\n        ");
				}
				jteOutput.writeContent("\r\n    </div>\r\n</div>\r\n<div class=\"row\">\r\n    <div class=\"col-12\">\r\n        <h2>Search</h2>\r\n        <input class=\"form-control form-control-sm\" id=\"myInput\" type=\"text\" placeholder=\"Search..\">\r\n        ");
				for (Grant grant : grants) {
					jteOutput.writeContent("\r\n            ");
					gg.jte.generated.ondemand.grant.JtecityGenerated.render(jteOutput, jteHtmlInterceptor, ws, grant, user, nation, failedRequirements.get(grant), overrideRequirements.get(grant), grantTokens.get(grant));
					jteOutput.writeContent("\r\n        ");
				}
				jteOutput.writeContent("\r\n    </div>\r\n</div>\r\n");
			}
		});
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Collection<Grant> grants = (Collection<Grant>)params.get("grants");
		User user = (User)params.get("user");
		DBNation nation = (DBNation)params.get("nation");
		Map<Grant, List<String>> failedRequirements = (Map<Grant, List<String>>)params.get("failedRequirements");
		Map<Grant, List<String>> overrideRequirements = (Map<Grant, List<String>>)params.get("overrideRequirements");
		Map<Grant, UUID> grantTokens = (Map<Grant, UUID>)params.get("grantTokens");
		render(jteOutput, jteHtmlInterceptor, ws, grants, user, nation, failedRequirements, overrideRequirements, grantTokens);
	}
}
