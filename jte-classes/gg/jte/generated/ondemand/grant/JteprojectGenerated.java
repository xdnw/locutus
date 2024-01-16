package gg.jte.generated.ondemand.grant;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.util.PnwUtil;
import net.dv8tion.jda.api.entities.User;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import java.util.List;
import java.util.UUID;
import link.locutus.discord.util.MarkupUtil;
public final class JteprojectGenerated {
	public static final String JTE_NAME = "grant/project.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,12,12,12,21,21,21,22,22,26,26,29,29,30,30,31,31,32,32,32,32,33,33,34,34,35,35,36,36,37,37,38,38,39,39,40,40,41,41,42,42,43,43,45,45,48,48,48,48,49,49,51,51,53,53,57,57,59,59,59,12,13,14,15,16,17,18,19,19,19,19};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Project project, Grant grant, User user, DBNation nation, List<String> failed, List<String> override, UUID grantToken) {
		jteOutput.writeContent("\r\n");
		var costFull = PnwUtil.resourcesToString(grant.cost());
		jteOutput.writeContent("\r\n");
		var costWorth = MathMan.format(PnwUtil.convertedTotal(grant.cost()));
		jteOutput.writeContent("\r\n\r\n<div class=\"row p-2 m-3 guild-icon guild-entry bg-light\" style=\"border-radius:10px\">\r\n    <div class=\"col-md-2\">\r\n        <img alt=\"guild-icon\" class=\"img-fluid guild-icon\" src=\"");
		jteOutput.writeUserContent(project.getImageUrl());
		jteOutput.writeContent("\" onerror=\"this.style.display='none'\">\r\n    </div>\r\n    <div class=\"col-md-10 p-2\">\r\n        <div id=\"body");
		jteOutput.writeUserContent(grantToken + "");
		jteOutput.writeContent("\">\r\n            <h4>");
		jteOutput.writeUserContent(project.name());
		jteOutput.writeContent("</h4>\r\n            <p class=\"lead\">");
		jteOutput.writeUnsafeContent(MarkupUtil.transformURLIntoLinks(grant.getInstructions()));
		jteOutput.writeContent("</p>\r\n            <p class=\"\">");
		jteOutput.writeUserContent(costFull);
		jteOutput.writeContent(" worth: ~$");
		jteOutput.writeUserContent(costWorth);
		jteOutput.writeContent("</p>\r\n            <p class=\"\">");
		jteOutput.writeUserContent(grant.getNote());
		jteOutput.writeContent("</p>\r\n            ");
		if (failed != null) {
			jteOutput.writeContent("\r\n                ");
			for (String message : failed) {
				jteOutput.writeContent("\r\n                    <div class=\"alert p-1 my-1 alert-danger border border-danger\" role=\"alert\">");
				jteOutput.writeUserContent(message);
				jteOutput.writeContent("</div>\r\n                ");
			}
			jteOutput.writeContent("\r\n            ");
		}
		jteOutput.writeContent("\r\n            ");
		if (override != null) {
			jteOutput.writeContent("\r\n                ");
			for (String message : override) {
				jteOutput.writeContent("\r\n                    <div class=\"alert p-1 my-1 alert-warning border border-warning\" role=\"alert\">");
				jteOutput.writeUserContent(message);
				jteOutput.writeContent("</div>\r\n                ");
			}
			jteOutput.writeContent("\r\n            ");
		}
		jteOutput.writeContent("\r\n        </div>\r\n        ");
		if (failed == null) {
			jteOutput.writeContent("\r\n            <div class=\"row\">\r\n                <div class=\"col\">\r\n                    <button onclick=\"promptGrant('Confirm Grant', document.getElementById('body");
			jteOutput.writeUserContent(grantToken + "");
			jteOutput.writeContent("').innerHTML, '");
			jteOutput.writeUserContent(grantToken + "");
			jteOutput.writeContent("', true)\" class=\"m-1 btn-lg btn\r\n                    ");
			if (override == null) {
				jteOutput.writeContent("\r\n                        btn-primary\">Send Grant\r\n                    ");
			} else {
				jteOutput.writeContent("\r\n                        btn-warning\">Send Grant (Admin Override)\r\n                    ");
			}
			jteOutput.writeContent("\r\n                </button>\r\n                </div>\r\n            </div>\r\n        ");
		}
		jteOutput.writeContent("\r\n    </div>\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Project project = (Project)params.get("project");
		Grant grant = (Grant)params.get("grant");
		User user = (User)params.get("user");
		DBNation nation = (DBNation)params.get("nation");
		List<String> failed = (List<String>)params.get("failed");
		List<String> override = (List<String>)params.get("override");
		UUID grantToken = (UUID)params.get("grantToken");
		render(jteOutput, jteHtmlInterceptor, ws, project, grant, user, nation, failed, override, grantToken);
	}
}
