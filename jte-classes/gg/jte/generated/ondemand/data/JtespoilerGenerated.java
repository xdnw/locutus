package gg.jte.generated.ondemand.data;
import gg.jte.Content;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
public final class JtespoilerGenerated {
	public static final String JTE_NAME = "data/spoiler.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,3,3,3,8,8,8,8,8,9,9,11,11,12,12,15,15,15,3,4,5,6,6,6,6};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String title, Content content, String id) {
		jteOutput.writeContent("<br>\r\n<button class=\"btn btn-primary\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#");
		jteOutput.writeUserContent(id);
		jteOutput.writeContent("\" aria-expanded=\"false\" aria-controls=\"");
		jteOutput.writeUserContent(id);
		jteOutput.writeContent("\">\r\n    ");
		jteOutput.writeUserContent(title);
		jteOutput.writeContent("\r\n</button>\r\n<div class=\"collapse\" id=\"");
		jteOutput.writeUserContent(id);
		jteOutput.writeContent("\">\r\n");
		jteOutput.writeUserContent(content);
		jteOutput.writeContent("\r\n</div>\r\n<br>\r\n<hr/>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String title = (String)params.get("title");
		Content content = (Content)params.get("content");
		String id = (String)params.get("id");
		render(jteOutput, jteHtmlInterceptor, ws, title, content, id);
	}
}
