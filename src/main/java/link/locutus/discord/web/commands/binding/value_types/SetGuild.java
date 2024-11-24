package link.locutus.discord.web.commands.binding.value_types;

public class SetGuild {
    public String id;
    public String name;
    public String icon;

    public SetGuild(String id, String name, String icon) {
        this.id = id;
        this.name = name;
        this.icon = icon;
    }
}
