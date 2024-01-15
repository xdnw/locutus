package gg.jte.generated.ondemand;
public final class JtetestGenerated {
	public static final String JTE_NAME = "test.jte";
	public static final int[] JTE_LINE_INFO = {0,0,0,0,1,1,1,1,2,2,2,0,0,0,0};
	public static void render(gg.jte.html.HtmlTemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, String title) {
		jteOutput.writeContent("<h1>");
		jteOutput.setContext("h1", null);
		jteOutput.writeUserContent(title);
		jteOutput.writeContent("</h1>\r\n<p>Hello World</p>");
	}
	public static void renderMap(gg.jte.html.HtmlTemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		String title = (String)params.get("title");
		render(jteOutput, jteHtmlInterceptor, title);
	}
}
