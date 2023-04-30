package link.locutus.discord.commands.manager.v2.copilot;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CopilotResult {

// // {"id":"cmpl-7AycOKU5WKUIngC82vzEslIBTQuIx","model":"cushman-ml","created":1682850652,"choices":[{"text":"ail","index":0,"finish_reason":null,"logprobs":null}]}
    /// <summary>
    /// Request and response id.
    /// </summary>
    @JsonProperty("id")
    public String Id;

    /// <summary>
    /// Name of the machine learning model being used.
    /// </summary>
    @JsonProperty("model")
    public String Model;

    /// <summary>
    /// LocalDate when this result was created.
    /// </summary>
    @JsonProperty("created")
    public long CreationTimestampSeconds;

    /// <summary>
    /// Available result choices.
    /// </summary>
    @JsonProperty("choices")
    public CopilotChoice[] choices;
}
