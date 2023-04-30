package link.locutus.discord.commands.manager.v2.copilot;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class CopilotLogProbs {
    @JsonProperty("tokens")
    public String[] Tokens;

    @JsonProperty("token_logprobs")
    public double[] TokenLogProbs;

    @JsonProperty("top_logprobs")
    public Map<String, Object>[] TopLogProbs;

    @JsonProperty("text_offset")
    public int[] TextOffSet;
}
