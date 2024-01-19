package gg.jte.generated.ondemand.grant;
public final class JtegrantsGenerated {
	public static final String JTE_NAME = "grant/grants.jte";
	public static final int[] JTE_LINE_INFO = {0,0,0,0,4,4,4,4,22,22,23,23,23,23,23,0,1,2,2,2,2};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, link.locutus.discord.commands.manager.v2.binding.WebStore ws, String title, gg.jte.Content content) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<script>\r\n    function promptGrant(title, body, uuid, isOverride) {\r\n        var body = '<div class=\"container\">' + body + '</div>';\r\n    var cmd = \"$approveGrant \" + uuid + \" -f\";\r\n    var footer = '<button type=\"button\" class=\"btn btn-primary\" cmd=\"' + cmd + '\" >Confirm Grant</button>'\r\n    footer += '<button type=\"button\" class=\"btn btn-secondary\" data-dismiss=\"modal\">Close</button>';\r\n        modal(title, body, footer);\r\n    }\r\n    $(document).ready(function(){\r\n      $(\"#myInput\").on(\"keyup\", function() {\r\n    var value = $(this).val().toLowerCase();\r\n        $(\".guild-entry\").filter(function() {\r\n    $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)\r\n    });\r\n    });\r\n    });\r\n</script>\r\n");
				jteOutput.writeUserContent(content);
				jteOutput.writeContent("\r\n");
			}
		}, title + " Grants", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		link.locutus.discord.commands.manager.v2.binding.WebStore ws = (link.locutus.discord.commands.manager.v2.binding.WebStore)params.get("ws");
		String title = (String)params.get("title");
		gg.jte.Content content = (gg.jte.Content)params.get("content");
		render(jteOutput, jteHtmlInterceptor, ws, title, content);
	}
}
