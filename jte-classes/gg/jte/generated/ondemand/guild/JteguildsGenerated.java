package gg.jte.generated.ondemand.guild;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.db.GuildDB;
import net.dv8tion.jda.api.entities.Guild;
import java.util.*;
public final class JteguildsGenerated {
	public static final String JTE_NAME = "guild/guilds.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,5,5,5,13,13,13,13,27,27,36,36,45,45,46,46,49,49,57,57,58,58,61,61,66,66,74,74,79,79,87,87,91,91,92,92,93,93,94,94,95,95,96,96,97,97,98,98,100,100,100,100,100,5,6,7,8,9,10,11,11,11,11};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Set<GuildDB> guildDbs, GuildDB current, GuildDB allianceGuild, String registerLink, String locutusInvite, String joinLink) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<script>\r\n$(document).ready(function(){\r\n  $(\"#myInput\").on(\"keyup\", function() {\r\n    var value = $(this).val().toLowerCase();\r\n    $(\".guild-entry\").filter(function() {\r\n      $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)\r\n    });\r\n  });\r\n});\r\n</script>\r\n<div class=\"container\">\r\n    <div class=\"row\">\r\n        <div class=\"col-sm\">\r\n    ");
				if (registerLink != null) {
					jteOutput.writeContent("\r\n    <div class=\"p-1 my-1 alert alert-warning alert-dismissible fade show\" role=\"alert\">\r\n        <strong>Discord is a chat application.</strong> Register your discord account to access alliance discord functionality\r\n        <a href=\"https://discord.com\" class=\"btn btn-primary btn-sm\" role=\"button\" aria-pressed=\"true\">\r\n            <svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" fill=\"currentColor\" class=\"bi bi-discord\" viewBox=\"0 0 16 16\">\r\n                <path d=\"M13.545 2.907a13.227 13.227 0 0 0-3.257-1.011.05.05 0 0 0-.052.025c-.141.25-.297.577-.406.833a12.19 12.19 0 0 0-3.658 0 8.258 8.258 0 0 0-.412-.833.051.051 0 0 0-.052-.025c-1.125.194-2.22.534-3.257 1.011a.041.041 0 0 0-.021.018C.356 6.024-.213 9.047.066 12.032c.001.014.01.028.021.037a13.276 13.276 0 0 0 3.995 2.02.05.05 0 0 0 .056-.019c.308-.42.582-.863.818-1.329a.05.05 0 0 0-.01-.059.051.051 0 0 0-.018-.011 8.875 8.875 0 0 1-1.248-.595.05.05 0 0 1-.02-.066.051.051 0 0 1 .015-.019c.084-.063.168-.129.248-.195a.05.05 0 0 1 .051-.007c2.619 1.196 5.454 1.196 8.041 0a.052.052 0 0 1 .053.007c.08.066.164.132.248.195a.051.051 0 0 1-.004.085 8.254 8.254 0 0 1-1.249.594.05.05 0 0 0-.03.03.052.052 0 0 0 .003.041c.24.465.515.909.817 1.329a.05.05 0 0 0 .056.019 13.235 13.235 0 0 0 4.001-2.02.049.049 0 0 0 .021-.037c.334-3.451-.559-6.449-2.366-9.106a.034.034 0 0 0-.02-.019Zm-8.198 7.307c-.789 0-1.438-.724-1.438-1.612 0-.889.637-1.613 1.438-1.613.807 0 1.45.73 1.438 1.613 0 .888-.637 1.612-1.438 1.612Zm5.316 0c-.788 0-1.438-.724-1.438-1.612 0-.889.637-1.613 1.438-1.613.807 0 1.451.73 1.438 1.613 0 .888-.631 1.612-1.438 1.612Z\"></path>\r\n            </svg>\r\n            Create a Discord Account\r\n        </a>\r\n        <a href=\"");
					jteOutput.writeUserContent(registerLink);
					jteOutput.writeContent("\" class=\"btn btn-secondary btn-sm\" role=\"button\" aria-pressed=\"true\">\r\n            <svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" fill=\"currentColor\" class=\"bi bi-link\" viewBox=\"0 0 16 16\">\r\n                <path d=\"M6.354 5.5H4a3 3 0 0 0 0 6h3a3 3 0 0 0 2.83-4H9c-.086 0-.17.01-.25.031A2 2 0 0 1 7 10.5H4a2 2 0 1 1 0-4h1.535c.218-.376.495-.714.82-1z\"/>\r\n                <path d=\"M9 5.5a3 3 0 0 0-2.83 4h1.098A2 2 0 0 1 9 6.5h3a2 2 0 1 1 0 4h-1.535a4.02 4.02 0 0 1-.82 1H12a3 3 0 1 0 0-6H9z\"/>\r\n            </svg>\r\n            Link your Discord with Locutus\r\n        </a>\r\n        <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n    </div>\r\n    ");
				}
				jteOutput.writeContent("\r\n    ");
				if (locutusInvite != null) {
					jteOutput.writeContent("\r\n    <div class=\"p-1 my-1 alert alert-warning alert-dismissible fade show\" role=\"alert\">\r\n        <strong>Locutus is not added setup for your alliance.</strong> Add Locutus to your alliance discord server to access alliance discord functionality\r\n        <a href=\"");
					jteOutput.writeUserContent(locutusInvite);
					jteOutput.writeContent("\">\r\n            Invite Locutus\r\n        </a>\r\n        <a href=\"https://github.com/xdnw/locutus/wiki\">\r\n            Wiki\r\n        </a>\r\n        <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n    </div>\r\n    ");
				}
				jteOutput.writeContent("\r\n    ");
				if (joinLink != null) {
					jteOutput.writeContent("\r\n    <div class=\"p-1 my-1 alert alert-warning alert-dismissible fade show\" role=\"alert\">\r\n        <strong>You are not a member of an alliance.</strong> Find and join an alliance to gain access to alliance functions\r\n        <a href=\"");
					jteOutput.writeUserContent(joinLink);
					jteOutput.writeContent("\">\r\n            Join Alliance\r\n        </a>\r\n        <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n    </div>\r\n    ");
				}
				jteOutput.writeContent("\r\n        </div>\r\n    </div>\r\n    <div class=\"row\">\r\n        <div class=\"col-sm\">\r\n    <input class=\"form-control form-control-sm\" id=\"myInput\" type=\"text\" placeholder=\"Search..\">\r\n            </div>\r\n    </div>\r\n    ");
				if (guildDbs.isEmpty()) {
					jteOutput.writeContent("\r\n    <div class=\"p-1 my-1 alert alert-error alert-dismissible fade show\" role=\"alert\">\r\n        <strong>No guilds found</strong>\r\n        <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n    </div>\r\n    ");
				}
				jteOutput.writeContent("\r\n</div>\r\n    </div>\r\n    <div class=\"row\">\r\n        <div class=\"col-sm\">\r\n            <input class=\"form-control form-control-sm\" id=\"myInput\" type=\"text\" placeholder=\"Search..\">\r\n        </div>\r\n    </div>\r\n    ");
				for (GuildDB db : guildDbs) {
					jteOutput.writeContent("\r\n        <!--  Add argument to the guildentry template which sets the highlight flag and additional note -->\r\n        <!-- if current is not null and matches db, set the highlight to \"primary\" and note to \"Currently Selected\"  -->\r\n        <!-- if allianceGuild is not null and matches db, set the highlight to \"secondary\" and note to \"Your Alliance\"  -->\r\n        ");
					if (current != null && db.getIdLong() == current.getIdLong()) {
						jteOutput.writeContent("\r\n            ");
						gg.jte.generated.ondemand.guild.JteguildentryGenerated.render(jteOutput, jteHtmlInterceptor, ws, db.getGuild(), db, true, "Currently Selected");
						jteOutput.writeContent("\r\n        ");
					} else if (allianceGuild != null && db.getIdLong() == allianceGuild.getIdLong()) {
						jteOutput.writeContent("\r\n            ");
						gg.jte.generated.ondemand.guild.JteguildentryGenerated.render(jteOutput, jteHtmlInterceptor, ws, db.getGuild(), db, true, "Your Alliance");
						jteOutput.writeContent("\r\n        ");
					} else {
						jteOutput.writeContent("\r\n            ");
						gg.jte.generated.ondemand.guild.JteguildentryGenerated.render(jteOutput, jteHtmlInterceptor, ws, db.getGuild(), db, false, null);
						jteOutput.writeContent("\r\n        ");
					}
					jteOutput.writeContent("\r\n    ");
				}
				jteOutput.writeContent("\r\n</div>\r\n");
			}
		}, "Guilds", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Set<GuildDB> guildDbs = (Set<GuildDB>)params.get("guildDbs");
		GuildDB current = (GuildDB)params.get("current");
		GuildDB allianceGuild = (GuildDB)params.get("allianceGuild");
		String registerLink = (String)params.get("registerLink");
		String locutusInvite = (String)params.get("locutusInvite");
		String joinLink = (String)params.get("joinLink");
		render(jteOutput, jteHtmlInterceptor, ws, guildDbs, current, allianceGuild, registerLink, locutusInvite, joinLink);
	}
}
