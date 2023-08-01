package link.locutus.discord.gpt.imps;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import link.locutus.discord.util.StringMan;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ProcessText2Text implements IText2Text{
    private final File file;
    private final File venvExe;

    public ProcessText2Text(File venvExe, File file) {
        this.venvExe = venvExe;
        this.file = file;
    }

    @Override
    public String generate(String text) {
        String encodedString = Base64.getEncoder().encodeToString(text.getBytes());
        List<String> lines = new ArrayList<>();
        String command = venvExe == null ? "python" : venvExe.getAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder(command, file.getAbsolutePath(), encodedString).redirectErrorStream(true);
        try {
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String result = StringMan.join(lines, "\n");
        if (result.contains("result:")) {
            result = result.substring(result.indexOf("result:") + 7);
            result = result.replace("\\n", "\n");
            return result;
        } else {
            System.err.println(result);
            throw new IllegalArgumentException("Unknown process result (see console)");
        }
    }
}
