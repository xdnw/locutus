package link.locutus.discord.commands.manager.v2.copilot;

public class CopilotConfiguration {
    /// <summary>
    /// The AppId identifying this application. Retrieved using reverse engineering techniques but it is not
    /// be considered a secret in OAuth application flows, so it is fine to put it here.
    /// </summary>
    public String GithubAppId = "Iv1.b507a08c87ecfe98";

    /// <summary>
    /// Github GrantType for the OAuth application flow.
    /// </summary>
    public String GithubGrantType = "urn:ietf:params:oauth:grant-type:device_code";

    /// <summary>
    /// User Agent identifier for the http client.
    /// </summary>
    public String UserAgent = "Mozilla/5.0 (Windows NT x.y; rv:10.0) Gecko/20100101 Firefox/10.0";
}
