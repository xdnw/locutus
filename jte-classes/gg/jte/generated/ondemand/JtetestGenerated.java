package gg.jte.generated.ondemand;
public final class JtetestGenerated {
	public static final String JTE_NAME = "test.jte";
	public static final int[] JTE_LINE_INFO = {0,0,0,0,2,2,2,3,3,3,0,1,1,1,1};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, link.locutus.discord.commands.manager.v2.binding.WebStore ws, String title) {
		jteOutput.writeContent("<h1>");
		jteOutput.writeUserContent(title);
		jteOutput.writeContent("</h1>\r\n<p>Hello World</p>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		link.locutus.discord.commands.manager.v2.binding.WebStore ws = (link.locutus.discord.commands.manager.v2.binding.WebStore)params.get("ws");
		String title = (String)params.get("title");
		render(jteOutput, jteHtmlInterceptor, ws, title);
	}
}
