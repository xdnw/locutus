import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;

import java.nio.file.Path;

public class Test {
    public static void main(String[] args) {
        CodeResolver codeResolver = new DirectoryCodeResolver(Path.of("src/main/java/jte")); // This is the directory where your .jte files are located.
        TemplateEngine templateEngine = TemplateEngine.create(codeResolver, ContentType.Html); // Two choices: Plain or Html
        templateEngine.precompileAll();

        TemplateOutput output = new StringOutput();
        templateEngine.render("example.jte", "Testing", output);
        System.out.println(output);

    }
}
