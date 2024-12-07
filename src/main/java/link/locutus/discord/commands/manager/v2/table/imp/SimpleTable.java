package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

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

    public void write(IMessageIO io, boolean attachJson, boolean attachCsv) throws IOException {
        write(io, getTimeFormat(), getNumberFormat(), getGraphType(), getOrigin(), attachJson, attachCsv);
    }

    public IMessageBuilder writeMsg(IMessageBuilder msg, boolean attachJson, boolean attachCsv) throws IOException {
        return writeMsg(msg, getTimeFormat(), getNumberFormat(), getGraphType(), getOrigin(), attachJson, attachCsv);
    }

    public byte[] write() {
        return write(getTimeFormat(), getNumberFormat(), getGraphType(), getOrigin());
    }
}
