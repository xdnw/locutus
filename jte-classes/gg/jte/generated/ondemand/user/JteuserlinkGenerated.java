package gg.jte.generated.ondemand.user;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.User;
public final class JteuserlinkGenerated {
	public static final String JTE_NAME = "user/userlink.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,4,4,4,8,8,9,9,9,9,9,9,10,10,14,14,14,14,19,19,25,25,25,25,25,25,27,27,30,30,30,30,34,34,34,34,38,38,40,40,51,51,52,52,52,52,53,53,54,54,54,54,55,55,55,55,4,5,6,7,7,7,7};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, long userId, User user, DBNation nation) {
		if (user != null) {
			jteOutput.writeContent("\r\n<a href=\"javascript:void(0)\" type=\"button\" data-bs-toggle=\"modal\" data-bs-target=\"#modal-");
			jteOutput.writeUserContent(userId);
			jteOutput.writeContent("\">");
			jteOutput.writeUserContent(user.getName());
			jteOutput.writeContent("#");
			jteOutput.writeUserContent(user.getDiscriminator());
			jteOutput.writeContent("</a>\r\n<div class=\"modal fade\" id=\"modal-");
			jteOutput.writeUserContent(userId);
			jteOutput.writeContent("\" tabindex=\"-1\" aria-labelledby=\"exampleModalLabel\" aria-hidden=\"true\">\r\n    <div class=\"modal-dialog\">\r\n        <div class=\"modal-content\">\r\n            <div class=\"modal-header\">\r\n                <h5 class=\"modal-title\" id=\"exampleModalLabel\">");
			jteOutput.writeUserContent(user.getName());
			jteOutput.writeContent("#");
			jteOutput.writeUserContent(user.getDiscriminator());
			jteOutput.writeContent("</h5>\r\n                <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"modal\" aria-label=\"Close\"></button>\r\n            </div>\r\n            <div class=\"modal-body row\">\r\n                <div class=\"col-auto\">\r\n                    <img src=\"");
			jteOutput.writeUserContent(user.getAvatarUrl());
			jteOutput.writeContent("\" class=\"rounded\" alt=\"Discord Avatar\">\r\n                </div>\r\n                    <div class=\"col-auto\">\r\n                    <table class=\"table\">\r\n                        <tr>\r\n                            <td>Discord</td>\r\n                            <td><a href=\"discord://discordapp.com/users/");
			jteOutput.writeUserContent(userId);
			jteOutput.writeContent("\">");
			jteOutput.writeUserContent(user.getName());
			jteOutput.writeContent("#");
			jteOutput.writeUserContent(user.getDiscriminator());
			jteOutput.writeContent("</a></td>\r\n                        </tr>\r\n                        ");
			if (nation != null) {
				jteOutput.writeContent("\r\n                        <tr>\r\n                            <td>Nation</td>\r\n                            <td><a href=\"");
				jteOutput.writeUserContent(nation.getNationUrl());
				jteOutput.writeContent("\">");
				jteOutput.writeUserContent(nation.getNation());
				jteOutput.writeContent("</a></td>\r\n                        </tr>\r\n                        <tr>\r\n                            <td>Alliance</td>\r\n                            <td><a href=\"");
				jteOutput.writeUserContent(nation.getAllianceUrl());
				jteOutput.writeContent("\">");
				jteOutput.writeUserContent(nation.getAllianceName());
				jteOutput.writeContent("</a><br></td>\r\n                        </tr>\r\n                        <tr>\r\n                            <td>Cities</td>\r\n                            <td>");
				jteOutput.writeUserContent(nation.getCities());
				jteOutput.writeContent("</td>\r\n                        </tr>\r\n                        ");
			}
			jteOutput.writeContent("\r\n                    </table>\r\n                </div>\r\n            </div>\r\n            <div class=\"modal-footer\">\r\n                <button type=\"button\" class=\"btn btn-secondary\" data-bs-dismiss=\"modal\">Close</button>\r\n            </div>\r\n        </div>\r\n    </div>\r\n</div>\r\n\r\n");
		} else if (nation != null) {
			jteOutput.writeContent("\r\n<a href=\"");
			jteOutput.writeUserContent(nation.getNationUrl());
			jteOutput.writeContent("\">");
			jteOutput.writeUserContent(nation.getName());
			jteOutput.writeContent("</a>\r\n");
		} else {
			jteOutput.writeContent("\r\n<a href=\"https://discordapp.com/users/");
			jteOutput.writeUserContent(userId);
			jteOutput.writeContent("\">&lt;@");
			jteOutput.writeUserContent(userId);
			jteOutput.writeContent("&gt; (unknown user)</a>\r\n");
		}
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		long userId = (long)params.get("userId");
		User user = (User)params.get("user");
		DBNation nation = (DBNation)params.get("nation");
		render(jteOutput, jteHtmlInterceptor, ws, userId, user, nation);
	}
}
