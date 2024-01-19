package gg.jte.generated.ondemand.command;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import java.util.*;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.util.StringMan;
public final class JtecommandgroupGenerated {
	public static final String JTE_NAME = "command/commandgroup.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,7,7,7,12,12,12,14,14,16,16,18,18,20,20,21,21,23,23,25,25,26,26,29,29,31,31,31,31,31,33,33,36,36,39,39,41,41,41,7,8,9,10,10,10,10};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, CommandGroup command, Map<String, CommandCallable> subcommands, String endpoint) {
		jteOutput.writeContent("\r\n");
		if (command.getParent() != null) {
			jteOutput.writeContent("\r\n    <div class=\"alert alert-light\">\r\n    <a href=\"..\">Go back ");
			jteOutput.writeUserContent(command.getParent().getPrimaryCommandId());
			jteOutput.writeContent("</a>\r\n    </div>\r\n");
		}
		jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n");
		if (command.help(ws.store()) != null) {
			jteOutput.writeContent("\r\n<h2>Help</h2>\r\n<p>");
			jteOutput.writeUserContent(command.help(ws.store()));
			jteOutput.writeContent("</p>\r\n");
		}
		jteOutput.writeContent("\r\n\r\n");
		if (command.desc(ws.store()) != null) {
			jteOutput.writeContent("\r\n<h2>Description</h2>\r\n<p>");
			jteOutput.writeUserContent(command.desc(ws.store()));
			jteOutput.writeContent("</p>\r\n");
		}
		jteOutput.writeContent("\r\n<h2>Subcommands</h2>\r\n<ul class=\"list-group\">\r\n    ");
		for (Map.Entry<String, CommandCallable> entry : subcommands.entrySet()) {
			jteOutput.writeContent("\r\n        <li class=\"command list-group-item\">\r\n            <b><a href=\"");
			jteOutput.writeUserContent(endpoint);
			jteOutput.writeUserContent(entry.getKey());
			jteOutput.writeContent("\">");
			jteOutput.writeUserContent(entry.getKey());
			jteOutput.writeContent("</a></b><br>\r\n            <p>\r\n                Aliases: ");
			jteOutput.writeUserContent(StringMan.getString(entry.getValue().aliases()));
			jteOutput.writeContent("\r\n            </p>\r\n            <p>\r\n                Help: ");
			jteOutput.writeUserContent(entry.getValue().simpleDesc());
			jteOutput.writeContent("\r\n            </p>\r\n        </li>\r\n    ");
		}
		jteOutput.writeContent("\r\n</ul>\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		CommandGroup command = (CommandGroup)params.get("command");
		Map<String, CommandCallable> subcommands = (Map<String, CommandCallable>)params.get("subcommands");
		String endpoint = (String)params.get("endpoint");
		render(jteOutput, jteHtmlInterceptor, ws, command, subcommands, endpoint);
	}
}
