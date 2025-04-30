package link.locutus.discord.commands.manager.v2.binding.bindings;

public record SelectorInfo(String format, String example, String desc) {
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SelectorInfo that = (SelectorInfo) obj;
        return format.equals(that.format);
    }

    @Override
    public int hashCode() {
        return format.hashCode();
    }
}
