package gg.jte.generated.ondemand;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import java.util.*;
public final class JtebasictableGenerated {
	public static final String JTE_NAME = "basictable.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,3,3,3,8,8,8,8,12,12,13,13,14,14,18,18,20,20,21,21,22,22,24,24,27,27,27,27,27,3,4,5,6,6,6,6};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String title, List<String> header, List<List<gg.jte.Content>> rows) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<table class=\"table\">\r\n    <thead>\r\n    <tr>\r\n        ");
				for (String item : header) {
					jteOutput.writeContent("\r\n        <th scope=\"col\">");
					jteOutput.writeUserContent(item);
					jteOutput.writeContent("</th>\r\n        ");
				}
				jteOutput.writeContent("\r\n    </tr>\r\n    </thead>\r\n    <tbody>\r\n    ");
				for (List<gg.jte.Content> row : rows) {
					jteOutput.writeContent("\r\n        <tr>\r\n            ");
					for (gg.jte.Content item : row) {
						jteOutput.writeContent("\r\n            <th>");
						jteOutput.writeUserContent(item);
						jteOutput.writeContent("</th>\r\n            ");
					}
					jteOutput.writeContent("\r\n        </tr>\r\n        ");
				}
				jteOutput.writeContent("\r\n    </tbody>\r\n</table>\r\n");
			}
		}, title, null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String title = (String)params.get("title");
		List<String> header = (List<String>)params.get("header");
		List<List<gg.jte.Content>> rows = (List<List<gg.jte.Content>>)params.get("rows");
		render(jteOutput, jteHtmlInterceptor, ws, title, header, rows);
	}
}
