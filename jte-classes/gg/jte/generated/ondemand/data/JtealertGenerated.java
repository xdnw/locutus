package gg.jte.generated.ondemand.data;
import gg.jte.Content;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
public final class JtealertGenerated {
	public static final String JTE_NAME = "data/alert.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,3,3,3,6,6,6,7,7,8,8,8,3,4,5,5,5,5};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String type, Content content) {
		jteOutput.writeContent("<div class=\"alert p-1 my-1 alert-");
		jteOutput.writeUserContent(type);
		jteOutput.writeContent("\" role=\"alert\">\r\n    ");
		jteOutput.writeUserContent(content);
		jteOutput.writeContent("\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String type = (String)params.get("type");
		Content content = (Content)params.get("content");
		render(jteOutput, jteHtmlInterceptor, ws, type, content);
	}
}
