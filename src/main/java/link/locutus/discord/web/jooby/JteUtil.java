package link.locutus.discord.web.jooby;

import gg.jte.TemplateOutput;
import gg.jte.html.OwaspHtmlTemplateOutput;
import gg.jte.output.StringOutput;

import java.util.function.Consumer;

public class JteUtil {
    public static String render(Consumer<OwaspHtmlTemplateOutput> task) {
        TemplateOutput output = new StringOutput();
        OwaspHtmlTemplateOutput htmlOutput = new OwaspHtmlTemplateOutput(output);
        task.accept(htmlOutput);
        return output.toString();
    }
}
