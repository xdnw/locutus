package gg.jte.generated.ondemand;
public final class JteindexGenerated {
	public static final String JTE_NAME = "index.jte";
	public static final int[] JTE_LINE_INFO = {0,0,0,0,3,3,3,3,4,4,5,5,5,5,5,0,1,1,1,1};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, link.locutus.discord.commands.manager.v2.binding.WebStore ws, String message) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<h1 class=\"text-primary\">Hello ");
				jteOutput.writeUserContent(message);
				jteOutput.writeContent("!</h1>\r\n");
			}
		}, "Home", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		link.locutus.discord.commands.manager.v2.binding.WebStore ws = (link.locutus.discord.commands.manager.v2.binding.WebStore)params.get("ws");
		String message = (String)params.get("message");
		render(jteOutput, jteHtmlInterceptor, ws, message);
	}
}
