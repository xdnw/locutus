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

    public ShrinkableField(String name, String value, boolean inline) {
        this(new Shrinkable(name), new Shrinkable(value), inline);
    }

    public ShrinkableField(ShrinkableField field) {
        this.name = field.name.clone();
        this.value = field.value.clone();
        this.inline = field.inline;
    }

    public ShrinkableField clone() {
        return new ShrinkableField(this);
    }
}
