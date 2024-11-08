package link.locutus.discord.web.commands.binding.value_types;

public class SetGuild extends WebSuccess {
    public String id;
    public String name;
    public String icon;

    public SetGuild(String id, String name, String icon) {
        super(true, null);
        this.id = id;
        this.name = name;
        this.icon = icon;
    }
}
