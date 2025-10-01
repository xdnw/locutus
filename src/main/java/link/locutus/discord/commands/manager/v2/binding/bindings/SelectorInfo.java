package link.locutus.discord.commands.manager.v2.binding.bindings;

import org.jetbrains.annotations.NotNull;

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

    @Override
    public @NotNull String toString() {
        String response = format() + " - " + desc();
        if (example() != null && !example().isEmpty()) {
            response += " (" + example() + ")";
        }
        return response;
    }
}
