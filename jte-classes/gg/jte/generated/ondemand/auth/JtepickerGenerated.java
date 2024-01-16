package gg.jte.generated.ondemand.auth;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.user.Roles;
import link.locutus.discord.db.GuildDB;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import link.locutus.discord.db.entities.DBAlliance;
public final class JtepickerGenerated {
	public static final String JTE_NAME = "auth/picker.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,7,7,7,10,10,10,13,13,19,19,33,33,33,33,33,7,8,9,9,9,9};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String discordUrl, String mailUrl) {
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<div class=\"container rounded shadow bg-white p-1\">\r\n    <h3>How would you like to login?</h3>\r\n    <a class=\"btn btn-primary\" href=\"");
				jteOutput.writeUserContent(discordUrl);
				jteOutput.writeContent("\" role=\"button\">\r\n        <svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" fill=\"currentColor\" class=\"bi bi-discord\" viewBox=\"0 0 16 16\">\r\n            <path d=\"M13.545 2.907a13.227 13.227 0 0 0-3.257-1.011.05.05 0 0 0-.052.025c-.141.25-.297.577-.406.833a12.19 12.19 0 0 0-3.658 0 8.258 8.258 0 0 0-.412-.833.051.051 0 0 0-.052-.025c-1.125.194-2.22.534-3.257 1.011a.041.041 0 0 0-.021.018C.356 6.024-.213 9.047.066 12.032c.001.014.01.028.021.037a13.276 13.276 0 0 0 3.995 2.02.05.05 0 0 0 .056-.019c.308-.42.582-.863.818-1.329a.05.05 0 0 0-.01-.059.051.051 0 0 0-.018-.011 8.875 8.875 0 0 1-1.248-.595.05.05 0 0 1-.02-.066.051.051 0 0 1 .015-.019c.084-.063.168-.129.248-.195a.05.05 0 0 1 .051-.007c2.619 1.196 5.454 1.196 8.041 0a.052.052 0 0 1 .053.007c.08.066.164.132.248.195a.051.051 0 0 1-.004.085 8.254 8.254 0 0 1-1.249.594.05.05 0 0 0-.03.03.052.052 0 0 0 .003.041c.24.465.515.909.817 1.329a.05.05 0 0 0 .056.019 13.235 13.235 0 0 0 4.001-2.02.049.049 0 0 0 .021-.037c.334-3.451-.559-6.449-2.366-9.106a.034.034 0 0 0-.02-.019Zm-8.198 7.307c-.789 0-1.438-.724-1.438-1.612 0-.889.637-1.613 1.438-1.613.807 0 1.45.73 1.438 1.613 0 .888-.637 1.612-1.438 1.612Zm5.316 0c-.788 0-1.438-.724-1.438-1.612 0-.889.637-1.613 1.438-1.613.807 0 1.451.73 1.438 1.613 0 .888-.631 1.612-1.438 1.612Z\"></path>\r\n        </svg>\r\n        Discord OAuth\r\n    </a>\r\n    <a class=\"btn btn-success\" href=\"");
				jteOutput.writeUserContent(mailUrl);
				jteOutput.writeContent("\" role=\"button\">\r\n        <svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" fill=\"currentColor\" class=\"bi bi-envelope\" viewBox=\"0 0 16 16\">\r\n            <path d=\"M0 4a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H2a2 2 0 0 1-2-2V4Zm2-1a1 1 0 0 0-1 1v.217l7 4.2 7-4.2V4a1 1 0 0 0-1-1H2Zm13 2.383-4.708 2.825L15 11.105V5.383Zm-.034 6.876-5.64-3.471L8 9.583l-1.326-.795-5.64 3.47A1 1 0 0 0 2 13h12a1 1 0 0 0 .966-.741ZM1 11.105l4.708-2.897L1 5.383v5.722Z\"></path>\r\n        </svg>\r\n        Politics & War Mail\r\n    </a>\r\n    <div class=\"bd-callout bd-callout-warning\">\r\n        <h5>What is discord?</h5>\r\n        <p>Discord is a voice, video, and text chat app that's used to communicate and hang out with communities and friends.<br>\r\n            Discord is a can be opened in browser or installed on your computer and mobile device<br>\r\n            <a href=\"https://discord.com/download\">Download Discord</a>\r\n        </p>\r\n    </div>\r\n</div>\r\n");
			}
		}, "Pick a login method", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String discordUrl = (String)params.get("discordUrl");
		String mailUrl = (String)params.get("mailUrl");
		render(jteOutput, jteHtmlInterceptor, ws, discordUrl, mailUrl);
	}
}
