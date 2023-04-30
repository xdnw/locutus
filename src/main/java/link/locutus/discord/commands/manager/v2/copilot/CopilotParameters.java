package link.locutus.discord.commands.manager.v2.copilot;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CopilotParameters {
    /// <summary>
    /// Text context for the returned completions.
    /// </summary>
        @JsonProperty("prompt")
    public String Prompt;

    /// <summary>
    /// Amount of tokens, which should be returned by copilot.
    /// </summary>
        @JsonProperty("max_tokens")
    public int MaxTokens = 30;

    /// <summary>
    /// Higher values means the model will take more risks.
    /// 0.9 for more creative applications, and 0 for ones with a well-defined answer.
    /// </summary>
        @JsonProperty("temperature")
    public float Temperature = 1;

    /// <summary>
    /// An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p probability mass.
    /// So 0.1 means only the tokens comprising the top 10% probability mass are considered.
    /// </summary>
        @JsonProperty("top_p")
    public float TopP = 1F;

    /// <summary>
    /// How many completions to generate for each prompt.
    /// You can group the related completions using the index property. e.g. completions.GroupBy(e => e.Choices@0.Index)
    /// </summary>
        @JsonProperty("n")
    public int N = 1;

    /// <summary>
    ///  Include the log probabilities on the logprobs most likely tokens, as well the chosen tokens.
    /// For example, if logprobs is 5, the API will return a list of the 5 most likely tokens. The maximum value for logprobs is 5
    /// </summary>
        @JsonProperty("logprobs")
    public Integer LogProbs = null;

    /// <summary>
    /// Should the response be streamed. Only true is allowed.
    /// </summary>
        @JsonProperty("stream")
    public boolean IsStreamEnabled = true;

    /// <summary>
    /// Up to 4 sequences where the API will stop generating further tokens. The returned text will not contain the stop sequence.
    /// Use "\n" for one line completions. Use "\n\n" for multi line completions. 
    /// </summary>
        @JsonProperty("stop")
    public String[] Stop = {"\n"};

    /// <summary>
    /// Unknown effects. One known experimental feature is 'nextLineIndent'.
    /// </summary>
        @JsonProperty("experimentalFeatures")
    public String[] ExperimentalFeatures = { };

    /// <summary>
    /// Extra data (may be used for experimental features) which is not known. Serialized, it is a new json object.
    /// </summary>
        @JsonProperty("extra")
    public Object Extra;

    /// <summary>
    /// Header value with unknown effects. Known values are 'copilot-ghost' or 'copilot-panel'.
    /// </summary>
    public String OpenAiIntent = "copilot-ghost";
}
