package link.locutus.discord.apiv3.csv.header;

import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv3.csv.column.DoubleColumn;
import link.locutus.discord.apiv3.csv.column.EnumColumn;
import link.locutus.discord.apiv3.csv.column.IntColumn;
import link.locutus.discord.apiv3.csv.column.LongColumn;
import link.locutus.discord.apiv3.csv.column.StringColumn;
import link.locutus.discord.apiv3.csv.file.Dictionary;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.util.TimeUtil;

import java.util.Locale;

public class AllianceHeader extends DataHeader<DBAlliance> {
    public final IntColumn<DBAlliance> alliance_id = new IntColumn<>(this, null);
    public final LongColumn<DBAlliance> date_created = new LongColumn<>(this, null) {
        @Override
        public Long read(String string) {
            return TimeUtil.parseDate(TimeUtil.YYYY_MM_DD_HH_MM_SS, string);
        }
    };
    public final StringColumn<DBAlliance> name = new StringColumn<>(this, null);
    public final StringColumn<DBAlliance> acronym = new StringColumn<>(this, null);
    public final EnumColumn<DBAlliance, NationColor> color = new EnumColumn<>(this, NationColor.class, null,
            value -> NationColor.valueOf(value.toUpperCase(Locale.ROOT)));
    public final DoubleColumn<DBAlliance> score = new DoubleColumn<>(this, null);
    public final StringColumn<DBAlliance> flag_url = new StringColumn<>(this, null);

    public AllianceHeader(Dictionary dict) {
        super(dict);
    }

    @Override
    public boolean isIgnoredColumn(String columnName) {
        return "continent".equals(columnName) || "discord_server".equals(columnName);
    }
}