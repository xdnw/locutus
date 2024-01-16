package gg.jte.generated.ondemand.data;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import java.util.*;
public final class Jtetable_dataGenerated {
	public static final String JTE_NAME = "data/table_data.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,3,3,3,7,7,7,8,8,9,9,10,10,10,10,10,3,4,5,5,5,5};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String title, String data) {
		jteOutput.writeContent("\r\n");
		if (title != null) {
			jteOutput.writeContent("\r\n    <p class=\"h3\">");
			jteOutput.writeUserContent(title);
			jteOutput.writeContent("</p>\r\n");
		}
		jteOutput.writeContent("\r\n<div class=\"locutus-table-container\" data-src=\"");
		jteOutput.writeUnsafeContent(data);
		jteOutput.writeContent("\"></div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String title = (String)params.get("title");
		String data = (String)params.get("data");
		render(jteOutput, jteHtmlInterceptor, ws, title, data);
	}
}
