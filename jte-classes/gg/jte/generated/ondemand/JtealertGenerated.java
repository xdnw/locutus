package gg.jte.generated.ondemand;
public final class JtealertGenerated {
	public static final String JTE_NAME = "alert.jte";
	public static final int[] JTE_LINE_INFO = {0,0,0,0,4,4,4,5,5,6,6,6,0,1,2,2,2,2};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, link.locutus.discord.commands.manager.v2.binding.WebStore ws, String title, String message) {
		jteOutput.writeContent("\r\n<h1 class=\"text-primary\">");
		jteOutput.writeUserContent(title);
		jteOutput.writeContent("</h1>\r\n<p>");
		jteOutput.writeUserContent(message);
		jteOutput.writeContent("</p>\r\n");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		link.locutus.discord.commands.manager.v2.binding.WebStore ws = (link.locutus.discord.commands.manager.v2.binding.WebStore)params.get("ws");
		String title = (String)params.get("title");
		String message = (String)params.get("message");
		render(jteOutput, jteHtmlInterceptor, ws, title, message);
	}
}
