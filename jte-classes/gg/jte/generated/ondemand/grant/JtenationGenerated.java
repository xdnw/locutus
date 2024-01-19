package gg.jte.generated.ondemand.grant;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import java.util.*;
public final class JtenationGenerated {
	public static final String JTE_NAME = "grant/nation.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,5,5,5,9,9,9,10,10,10,10,10,10,11,11,13,13,19,19,23,23,27,27,27,27,31,31,31,31,31,31,31,35,35,39,39,43,43,43,43,47,47,53,53,53,5,6,6,6,6};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, DBNation nation) {
		jteOutput.writeContent("<div class=\"card alert p-1 my-1 alert-dismissible\">\r\n    <div class=\"card-body\">\r\n        <h5 class=\"card-title\"><a href=\"");
		jteOutput.writeUserContent(nation.getNationUrl());
		jteOutput.writeContent("\">\r\n            ");
		jteOutput.writeUserContent(nation.getNation());
		jteOutput.writeContent("</a> | <a href=\"");
		jteOutput.writeUserContent(nation.getAllianceUrl());
		jteOutput.writeContent("\">");
		jteOutput.writeUserContent(nation.getAllianceName());
		jteOutput.writeContent("</a>\r\n            ");
		if (nation.getPosition() == 1) {
			jteOutput.writeContent("\r\n            <span class=\"badge badge-secondary\">APP</span>\r\n            ");
		}
		jteOutput.writeContent("\r\n        </h5>\r\n        <table class=\"table\">\r\n            <tbody>\r\n            <tr>\r\n                <th scope=\"row\">Cities</th>\r\n                <td>");
		jteOutput.writeUserContent(nation.getCities());
		jteOutput.writeContent("</td>\r\n            </tr>\r\n            <tr>\r\n                <th scope=\"row\">avg_infra</th>\r\n                <td>");
		jteOutput.writeUserContent(nation.getAvg_infra());
		jteOutput.writeContent("</td>\r\n            </tr>\r\n            <tr>\r\n                <th scope=\"row\">MMR (build/units)</th>\r\n                <td>");
		jteOutput.writeUserContent(nation.getMMRBuildingStr());
		jteOutput.writeContent("/");
		jteOutput.writeUserContent(nation.getMMR());
		jteOutput.writeContent("</td>\r\n            </tr>\r\n            <tr>\r\n                <th scope=\"row\">Last login</th>\r\n                <td>");
		if (nation.getActive_m() <= 3) {
			jteOutput.writeContent("Online ");
		} else {
			jteOutput.writeContent(" ");
			jteOutput.writeUserContent(TimeUtil.minutesToTime(nation.getActive_m()));
		}
		jteOutput.writeContent("</td>\r\n            </tr>\r\n            <tr>\r\n                <th scope=\"row\">Daily Login</th>\r\n                <td>");
		jteOutput.writeUserContent(100 * nation.avg_daily_login());
		jteOutput.writeContent("%</td>\r\n            </tr>\r\n            <tr>\r\n                <th scope=\"row\">Seniority</th>\r\n                <td>");
		jteOutput.writeUserContent(nation.allianceSeniority());
		jteOutput.writeContent(" days</td>\r\n            </tr>\r\n            <tr>\r\n                <th scope=\"row\">Wars (off/def)</th>\r\n                <td>");
		jteOutput.writeUserContent(nation.getOff());
		jteOutput.writeContent("/");
		jteOutput.writeUserContent(nation.getDef());
		jteOutput.writeContent("</td>\r\n            </tr>\r\n            <tr>\r\n                <th scope=\"row\">Domestic Policy</th>\r\n                <td>");
		jteOutput.writeUserContent(nation.getDomesticPolicy());
		jteOutput.writeContent("</td>\r\n            </tr>\r\n            </tbody>\r\n        </table>\r\n    </div>\r\n    <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		DBNation nation = (DBNation)params.get("nation");
		render(jteOutput, jteHtmlInterceptor, ws, nation);
	}
}
