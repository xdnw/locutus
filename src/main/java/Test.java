import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.generated.precompiled.JtetestGenerated;
import gg.jte.html.HtmlInterceptor;
import gg.jte.html.OwaspHtmlTemplateOutput;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import gg.jte.resolve.ResourceCodeResolver;
import link.locutus.discord.web.jooby.JteUtil;

import java.nio.file.Path;

public class Test {
    public static void main(String[] args) {
        CodeResolver codeResolver = new DirectoryCodeResolver(Path.of("src/main/jte")); // This is the directory where your .jte files are located.
//        ResourceCodeResolver rssResolver = new ResourceCodeResolver("src/main/jte");
//        TemplateEngine templateEngine = TemplateEngine.create(codeResolver, ContentType.Plain); // Two choices: Plain or Html
//        templateEngine.precompileAll();

//        templateEngine.render("test.jte", "Testing", output);
//        System.out.println(output);

        HtmlInterceptor interceptor = null;
        String output = JteUtil.render(f -> JtetestGenerated.render(f, null, null, "TestingTest"));
        System.out.println(output);

    }
}
