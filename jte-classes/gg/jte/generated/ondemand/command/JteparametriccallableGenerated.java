package gg.jte.generated.ondemand.command;
import gg.jte.Content;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import java.util.*;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.util.MarkupUtil;
public final class JteparametriccallableGenerated {
	public static final String JTE_NAME = "command/parametriccallable.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,8,8,8,11,11,11,13,13,14,14,15,15,16,16,18,18,19,19,20,20,22,22,23,23,25,25,28,28,28,28,28,8,9,10,10,10,10};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, ParametricCallable command, Content form) {
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<div class=\"container-fluid rounded shadow bg-white p-1\">\r\n");
				if (command.getParent() != null) {
					jteOutput.writeContent("\r\n<a href=\"\">Go back ");
					jteOutput.writeUserContent(command.getParent().getPrimaryCommandId());
					jteOutput.writeContent("</a>\r\n");
				}
				jteOutput.writeContent("\r\n");
				if (command.help(ws.store()) != null) {
					jteOutput.writeContent("\r\n<h2>Help</h2>\r\n<p>");
					jteOutput.writeUnsafeContent(MarkupUtil.markdownToHTML(command.help(ws.store())));
					jteOutput.writeContent("</p>\r\n");
				}
				jteOutput.writeContent("\r\n");
				if (command.simpleDesc() != null && !command.simpleDesc().isEmpty()) {
					jteOutput.writeContent("\r\n<h2>Description</h2>\r\n<p>");
					jteOutput.writeUnsafeContent(MarkupUtil.markdownToHTML(command.simpleDesc()));
					jteOutput.writeContent("</p>\r\n");
				}
				jteOutput.writeContent("\r\n<h2>Execute</h2>\r\n");
				jteOutput.writeUserContent(form);
				jteOutput.writeContent("\r\n<div style=\"width:100%;height:80%;margin-top:10px\" id=\"output\" class=\"\"></div>\r\n</div>\r\n");
			}
		}, "Command: " + command.getPrimaryCommandId(), null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		ParametricCallable command = (ParametricCallable)params.get("command");
		Content form = (Content)params.get("form");
		render(jteOutput, jteHtmlInterceptor, ws, command, form);
	}
}
