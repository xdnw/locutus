package gg.jte.generated.ondemand.trade;
public final class JtetradepriceGenerated {
	public static final String JTE_NAME = "trade/tradeprice.jte";
	public static final int[] JTE_LINE_INFO = {0,0,0,0,3,3,3,4,4,5,5,5,5,5,0,1,2,2,2,2};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, link.locutus.discord.commands.manager.v2.binding.WebStore ws, String title, String endpoint) {
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n    ");
				gg.jte.generated.ondemand.data.JtetimechartendpointGenerated.render(jteOutput, jteHtmlInterceptor, ws, title, endpoint);
				jteOutput.writeContent("\r\n");
			}
		}, title, null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		link.locutus.discord.commands.manager.v2.binding.WebStore ws = (link.locutus.discord.commands.manager.v2.binding.WebStore)params.get("ws");
		String title = (String)params.get("title");
		String endpoint = (String)params.get("endpoint");
		render(jteOutput, jteHtmlInterceptor, ws, title, endpoint);
	}
}
