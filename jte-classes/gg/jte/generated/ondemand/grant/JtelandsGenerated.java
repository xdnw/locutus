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
public final class JtelandsGenerated {
	public static final String JTE_NAME = "grant/lands.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,12,12,12,20,20,20,20,23,23,31,31,41,41,48,48,49,49,50,50,53,53,53,53,53,12,13,14,15,16,17,18,18,18,18};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Collection<Grant> grants, User user, DBNation nation, Map<Grant, List<String>> failedRequirements, Map<Grant, List<String>> overrideRequirements, Map<Grant, UUID> grantTokens) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.grant.JtegrantsGenerated.render(jteOutput, jteHtmlInterceptor, ws, "Land", new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n    <div class=\"row\">\r\n        <div class=\"col-lg-3\">\r\n            ");
				gg.jte.generated.ondemand.grant.JtenationGenerated.render(jteOutput, jteHtmlInterceptor, ws, nation);
				jteOutput.writeContent("\r\n        </div>\r\n        <div class=\"col-lg-9\">\r\n            <div class=\"alert p-1 my-1 alert-info border border-info alert-dismissible fade show\" role=\"alert\">\r\n                Land increases the output of your farms, as well as lowers the population density (which at low land\r\n                levels can contribute to disease)\r\n                <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n            </div>\r\n            ");
				if (nation.getDomesticPolicy() != DomesticPolicy.RAPID_EXPANSION) {
					jteOutput.writeContent("\r\n                <div class=\"alert p-1 my-1 alert-warning border border-warning alert-dismissible fade show\"\r\n                     role=\"alert\">\r\n                    <p>\r\n                        You can go to the <a href=\"https://politicsandwar.com/nation/edit/\"\r\n                                             class=\"btn btn-secondary btn-sm\">edit nation 🡕</a> page to set your\r\n                        domestic policy to <kbd>Rapid Expansion</kbd> to save 5% on land\r\n                    </p>\r\n                    <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n                </div>\r\n            ");
				}
				jteOutput.writeContent("\r\n        </div>\r\n    </div>\r\n    <div class=\"row\">\r\n        <div class=\"col-12\">\r\n            <h2>Search</h2>\r\n            <input class=\"form-control form-control-sm\" id=\"myInput\" type=\"text\" placeholder=\"Search..\">\r\n            ");
				for (Grant grant : grants) {
					jteOutput.writeContent("\r\n                ");
					gg.jte.generated.ondemand.grant.JtelandGenerated.render(jteOutput, jteHtmlInterceptor, ws, grant, user, nation, failedRequirements.get(grant), overrideRequirements.get(grant), grantTokens.get(grant));
					jteOutput.writeContent("\r\n            ");
				}
				jteOutput.writeContent("\r\n        </div>\r\n    </div>\r\n");
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
