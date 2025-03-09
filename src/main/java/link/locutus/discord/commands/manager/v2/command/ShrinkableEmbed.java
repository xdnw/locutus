package link.locutus.discord.commands.manager.v2.command;

public class ShrinkableEmbed {
    protected final Shrinkable title;
    protected final Shrinkable description;
    protected final Shrinkable footer;

    public ShrinkableEmbed() {

    }

    public void shrink(int totalSize, int titleSize, int descSize, int footerSize) {
        Shrinkable.shrink(totalSize, title, description, footer);
        title.shrink(titleSize);
        description.shrink(descSize);
        footer.shrink(footerSize);
    }
}
