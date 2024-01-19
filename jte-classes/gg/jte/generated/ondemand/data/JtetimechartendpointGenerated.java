package gg.jte.generated.ondemand.data;
public final class JtetimechartendpointGenerated {
	public static final String JTE_NAME = "data/timechartendpoint.jte";
	public static final int[] JTE_LINE_INFO = {0,0,0,0,3,3,3,3,3,3,3,3,0,1,2,2,2,2};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, link.locutus.discord.commands.manager.v2.binding.WebStore ws, String title, String endpoint) {
		jteOutput.writeContent("<div class=\"col-sm locutus-chart\" src=\"");
		jteOutput.writeUserContent(endpoint);
		jteOutput.writeContent("\" title=\"");
		jteOutput.writeUserContent(title);
		jteOutput.writeContent("\"></div>>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		link.locutus.discord.commands.manager.v2.binding.WebStore ws = (link.locutus.discord.commands.manager.v2.binding.WebStore)params.get("ws");
		String title = (String)params.get("title");
		String endpoint = (String)params.get("endpoint");
		render(jteOutput, jteHtmlInterceptor, ws, title, endpoint);
	}
}
