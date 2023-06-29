package link.locutus.discord.gpt.copilot;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CopilotChoice {
    /// <summary>
    /// Text Result.
    /// </summary>
    @JsonProperty("text")
    public String Text;

    /// <summary>
    /// Unknown Index value.
    /// </summary>
    @JsonProperty("index")
    public int Index;

    /// <summary>
    /// Unknown value.
    /// </summary>
    @JsonProperty("finish_reason")
    public Object FinishReason;

    /// <summary>
    /// Unknown values.
    /// </summary>
        @JsonProperty("logprobs")
    public CopilotLogProbs LogProbs;
}
