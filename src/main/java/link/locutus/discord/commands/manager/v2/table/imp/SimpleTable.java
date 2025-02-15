package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.web.commands.binding.value_types.GraphType;
import link.locutus.discord.web.commands.binding.value_types.WebGraph;

import java.io.IOException;

public abstract class SimpleTable<T> extends TimeNumericTable<T> {
    public SimpleTable() {
        super(null, null, null, null, null);
    }

    protected abstract SimpleTable<T> writeData();

    public abstract TimeFormat getTimeFormat();

    public abstract TableNumberFormat getNumberFormat();

    public abstract GraphType getGraphType();

    public abstract long getOrigin();

    public void write(IMessageIO io, boolean attachJson, boolean attachCsv, GuildDB db, SheetKey sheetKey) throws IOException {
        write(io, getTimeFormat(), getNumberFormat(), getGraphType(), getOrigin(), attachJson, attachCsv, db, sheetKey);
    }

    public IMessageBuilder writeMsg(IMessageBuilder msg, boolean attachJson, boolean attachCsv, GuildDB db, SheetKey sheetKey) throws IOException {
        return writeMsg(msg, getTimeFormat(), getNumberFormat(), getGraphType(), getOrigin(), attachJson, attachCsv, db, sheetKey);
    }

    public byte[] write() throws IOException {
        return write(getTimeFormat(), getNumberFormat(), getGraphType(), getOrigin());
    }

    public WebGraph toHtmlJson() {
        return toHtmlJson(getTimeFormat(), getNumberFormat(), getGraphType(), getOrigin());
    }
}
