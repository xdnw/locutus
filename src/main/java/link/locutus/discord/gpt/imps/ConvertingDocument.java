package link.locutus.discord.gpt.imps;

public class ConvertingDocument {
    // document_queue: source_id int, prompt string, converted bool, use_global_context bool, provider_type: int, user: long, error: string, date: long
    public int source_id;
    public String prompt;
    public boolean converted;
    public boolean use_global_context;
    public int provider_type;
    public long user;
    public String error;
    public long date;
    public long hash;

    public ProviderType getProviderType() {
        return ProviderType.values()[provider_type];
    }
}
