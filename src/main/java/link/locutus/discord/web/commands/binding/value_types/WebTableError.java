package link.locutus.discord.web.commands.binding.value_types;

import org.checkerframework.checker.nullness.qual.Nullable;

public class WebTableError {
    public @Nullable Integer col;
    public @Nullable Integer row;
    public String msg;

    public WebTableError(Integer col, Integer row, String msg) {
        this.col = col;
        this.row = row;
        this.msg = msg;
    }
}
