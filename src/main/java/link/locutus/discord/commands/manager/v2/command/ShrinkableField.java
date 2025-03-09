package link.locutus.discord.commands.manager.v2.command;

public class ShrinkableField {
    public final Shrinkable name;
    public final Shrinkable value;
    public final boolean inline;

    public ShrinkableField(Shrinkable name, Shrinkable value, boolean inline) {
        this.name = name;
        this.value = value;
        this.inline = inline;
    }
}
