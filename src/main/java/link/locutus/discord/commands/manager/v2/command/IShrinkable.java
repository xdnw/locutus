package link.locutus.discord.commands.manager.v2.command;

public interface IShrinkable {
    public IShrinkable append(String s);
    public IShrinkable prepend(String s);
    public IShrinkable append(Shrinkable s);
    public IShrinkable prepend(Shrinkable s);
    public IShrinkable clone();
    public int getSize();
    public int shrink(int totalSize);
    public IShrinkable shrink();
    public boolean isIdentical();

    public String get();
    public boolean isEmpty();
}
