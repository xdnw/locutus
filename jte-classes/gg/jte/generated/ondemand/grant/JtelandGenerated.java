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
import link.locutus.discord.util.MathMan;
public final class JtelandGenerated {
	public static final String JTE_NAME = "grant/land.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,13,13,13,21,21,21,22,22,31,31,32,32,33,33,35,35,38,38,39,39,39,39,40,40,42,42,44,44,45,45,45,45,46,46,47,47,48,48,49,49,50,50,51,51,52,52,53,53,54,54,55,55,56,56,60,60,61,61,61,61,63,63,65,65,67,67,69,69,74,74,78,78,78,13,14,15,16,17,18,19,19,19,19};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Grant grant, User user, DBNation nation, List<String> failed, List<String> override, UUID grantToken) {
		jteOutput.writeContent("\r\n");
		var costFull = PnwUtil.resourcesToString(grant.cost());
		jteOutput.writeContent("\r\n");
		var costWorth = MathMan.format(PnwUtil.convertedTotal(grant.cost()));
		jteOutput.writeContent("\r\n\r\n<div class=\"row p-2 m-3 guild-icon guild-entry bg-light\" style=\"border-radius:10px\">\r\n    <div class=\"col-md-2\">\r\n        <img alt=\"guild-icon\" class=\"img-fluid guild-icon\"\r\n             src=\"https://upload.wikimedia.org/wikipedia/commons/thumb/a/ac/Buchansrigs.jpg/300px-Buchansrigs.jpg\"\r\n             onerror=\"this.style.display='none'\">\r\n    </div>\r\n    <div class=\"col-md-10 p-2\">\r\n        <div id=\"body");
		jteOutput.writeUserContent(grantToken + "");
		jteOutput.writeContent("\">\r\n            <h4 class=\"\">Land <kbd>@");
		jteOutput.writeUserContent(grant.getAmount());
		jteOutput.writeContent("</kbd></h4>\r\n            ");
		if (grant.isAllCities()) {
			jteOutput.writeContent("\r\n                <h4 class=\"row\">All cities</h4>\r\n            ");
		} else {
			jteOutput.writeContent("\r\n                <b class=\"row\">Cities:</b>\r\n                <ul class=\"list-group\">\r\n                    ");
			for (int cityId : grant.getCities()) {
				jteOutput.writeContent("\r\n                        <li class=\"list-group-item\"><a href=\"");
				jteOutput.writeUserContent(PnwUtil.getCityUrl(cityId));
				jteOutput.writeContent("\">");
				jteOutput.writeUserContent(cityId);
				jteOutput.writeContent("</a></li>\r\n                    ");
			}
			jteOutput.writeContent("\r\n                </ul>\r\n            ");
		}
		jteOutput.writeContent("\r\n\r\n            <p class=\"lead\">");
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
		jteOutput.writeContent("\r\n        </div>\r\n        <div class=\"row\">\r\n            <div class=\"col\">\r\n                ");
		if (failed == null) {
			jteOutput.writeContent("\r\n                    <button onclick=\"promptGrant('Confirm Grant', document.getElementById('body");
			jteOutput.writeUserContent(grantToken + "");
			jteOutput.writeContent("').innerHTML, '");
			jteOutput.writeUserContent(grantToken + "");
			jteOutput.writeContent("', true)\"\r\n                            class=\"m-1 btn-lg btn\r\n                    ");
			if (override == null) {
				jteOutput.writeContent("\r\n                        btn-primary\">Send Grant\r\n                        ");
			} else {
				jteOutput.writeContent("\r\n                            btn-warning\">Send Grant (Admin Override)\r\n                        ");
			}
			jteOutput.writeContent("\r\n                    </button>\r\n                ");
		} else {
			jteOutput.writeContent("\r\n                    <div class=\"alert p-1 my-1 alert-danger border border-danger\" role=\"alert\">\r\n                        Unable to automatically approve this grant (open a grant channel and discuss with econ for\r\n                        assistance)\r\n                    </div>\r\n                ");
		}
		jteOutput.writeContent("\r\n            </div>\r\n        </div>\r\n    </div>\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Grant grant = (Grant)params.get("grant");
		User user = (User)params.get("user");
		DBNation nation = (DBNation)params.get("nation");
		List<String> failed = (List<String>)params.get("failed");
		List<String> override = (List<String>)params.get("override");
		UUID grantToken = (UUID)params.get("grantToken");
		render(jteOutput, jteHtmlInterceptor, ws, grant, user, nation, failed, override, grantToken);
	}
}
