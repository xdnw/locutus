package link.locutus.discord.web.commands.binding.value_types;

public class WebAnnouncement {
    public int id;
    public int type;
    public boolean active;
    public String title;
    public String content;

    public WebAnnouncement(int id, int type, boolean active, String title, String content) {
        this.id = id;
        this.type = type;
        this.active = active;
        this.title = title;
        this.content = content;
    }
}
