package gg.jte.generated.ondemand.command;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
public final class JteguildindexGenerated {
	public static final String JTE_NAME = "command/guildindex.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,5,5,5,11,11,11,25,25,27,27,28,28,28,31,31,31,5,6,7,8,9,10,10,10,10};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, PermissionHandler permHandler, String commandEndpoint, CommandGroup commands, String pageEndpoint, CommandGroup pages) {
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<script>\r\n    $(document).ready(function(){\r\n      $(\"#myInput\").on(\"keyup\", function() {\r\n        var value = $(this).val().toLowerCase();\r\n        $(\".command\").filter(function() {\r\n          $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)\r\n        });\r\n      });\r\n    });\r\n</script>\r\n<div class=\"container input-group input-group-lg\">\r\n    <input class=\"form-control form-control-lg\" id=\"myInput\" type=\"text\" placeholder=\"Filter..\">\r\n</div>\r\n");
				gg.jte.generated.ondemand.command.JtecommandgroupGenerated.render(jteOutput, jteHtmlInterceptor, ws, pages, pages.getAllowedCommands(ws.store(), permHandler), pageEndpoint);
				jteOutput.writeContent("\r\n\r\n");
				gg.jte.generated.ondemand.command.JtecommandgroupGenerated.render(jteOutput, jteHtmlInterceptor, ws, commands, commands.getAllowedCommands(ws.store(), permHandler), commandEndpoint);
				jteOutput.writeContent("\r\n");
			}
		}, "Commands/Pages", null);
		jteOutput.writeContent("\r\n\r\n\r\n");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		PermissionHandler permHandler = (PermissionHandler)params.get("permHandler");
		String commandEndpoint = (String)params.get("commandEndpoint");
		CommandGroup commands = (CommandGroup)params.get("commands");
		String pageEndpoint = (String)params.get("pageEndpoint");
		CommandGroup pages = (CommandGroup)params.get("pages");
		render(jteOutput, jteHtmlInterceptor, ws, permHandler, commandEndpoint, commands, pageEndpoint, pages);
	}
}
