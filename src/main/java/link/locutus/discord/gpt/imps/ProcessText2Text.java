package link.locutus.discord.gpt.imps;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import link.locutus.discord.gpt.ISummarizer;
import link.locutus.discord.util.StringMan;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ProcessText2Text implements IText2Text{
    private final String scriptPath;
    private final File venvExe;
    private final File workingDirectory;

    public ProcessText2Text(File venvExe, String scriptPath, File workingDirectory) {
        this.venvExe = venvExe;
        this.scriptPath = scriptPath;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String getId() {
        return "process";
    }

    @Override
    public String generate(Map<String, String> options, String text) {
        setOptions(options);
        String encodedString = Base64.getEncoder().encodeToString(text.getBytes());
        List<String> lines = new ArrayList<>();
        String command = venvExe == null ? "python" : venvExe.getAbsolutePath();
        ProcessBuilder pb = new ProcessBuilder(command, "-m", scriptPath, encodedString).redirectErrorStream(true).directory(workingDirectory);
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
            result = result.replace("\\n", "\n").trim();
            // remove starting b' and ending '
            if (result.startsWith("b'")) {
                result = result.substring(2);
            }
            if (result.endsWith("'")) {
                result = result.substring(0, result.length() - 1);
            }
            return result;
        } else {
            System.err.println(result);
            throw new IllegalArgumentException("Unknown process result (see console)");
        }
    }

    @Override
    public int getSize(String text) {
        return GPTUtil.getTokens(text, ModelType.GPT_3_5_TURBO);
    }

    @Override
    public int getSizeCap() {
        return 4096;
    }

    @Override
    public Map<String, String> getOptions() {
        return Collections.emptyMap();
    }

    public void setOptions(Map<String, String> options) {
        if (options != null && !options.isEmpty()) {
            throw new IllegalArgumentException("Options not supported");
        }
    }
}
