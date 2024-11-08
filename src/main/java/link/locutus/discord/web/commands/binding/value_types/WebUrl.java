package link.locutus.discord.web.commands.binding.value_types;

public class WebUrl extends WebSuccess {
    public String url;

    public WebUrl(String url) {
        super(true, null);
        this.url = url;
    }
}
